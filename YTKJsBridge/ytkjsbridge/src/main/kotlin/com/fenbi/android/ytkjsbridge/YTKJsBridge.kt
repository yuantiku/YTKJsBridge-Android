package com.fenbi.android.ytkjsbridge

import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import android.util.Log
import android.webkit.JavascriptInterface
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.reflect.KClass

/**
 * Created by yangjw on 2019/1/2.
 */

class YTKJsBridge {

    private val TAG = "YTKJsBridge@${hashCode()}"
    private val callId by lazy { AtomicLong() }
    private val callMap by lazy { mutableMapOf<Long, JsCallback<Any>?>() }
    private val interfaceMap by lazy { mutableMapOf<String, Any>() }
    private val methodMap by lazy { mutableMapOf<String, Method>() }
    private val listenerMap by lazy { mutableMapOf<String, MutableList<EventListener<Any?>>>() }
    private val handler = Handler(Looper.getMainLooper())
    var debugMode = BuildConfig.DEBUG

    var jsEvaluator: (script: String) -> Unit = {}

    val javascriptInterface: Any = @Keep object {
        /**
         * to get return value from javascript call.
         */
        @JavascriptInterface
        fun makeCallback(jsonStr: String?) {
            jsonStr?.let {
                try {
                    val jsonObject = JSONObject(jsonStr)
                    val callId = jsonObject.optLong("callId", -1)
                    val ret = jsonObject.opt("ret")
                    dispatchJsCallback(callId, ret)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    logError("makeCallback() with parameter:$jsonStr occurs exception:$e")
                }
            }
        }

        /**
         * pass all call messages from javascript.
         */
        @JavascriptInterface
        fun callNative(jsonStr: String?): String? {
            jsonStr?.let {
                try {
                    val jsonObject = JSONObject(jsonStr)
                    val methodName = jsonObject.optString("methodName")
                    val param = jsonObject.opt("args")
                    val callId = jsonObject.optLong("callId")
                    return dispatchJsCall(methodName, param, callId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    logError("callNative() with parameter:$jsonStr occurs exception:$e")
                }
            }
            return null
        }

        @JavascriptInterface
        fun sendEvent(jsonStr: String?) {
            jsonStr?.let {
                try {
                    val jsonObject = JSONObject(jsonStr)
                    val event = jsonObject.optString("event")
                    val arg = jsonObject.opt("arg")
                    dispatchJsEvent(event, arg)
                } catch (e: Exception) {
                    e.printStackTrace()
                    logError("sendEvent() with parameter:$jsonStr occurs exception:$e")
                }
            }
        }
    }

    fun addYTKJavascriptInterface(obj: Any, namespace: String = "") {
        obj::class.java.methods
            .filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
            .forEach {
                val prefix = if (namespace.isNotEmpty()) "$namespace." else namespace
                interfaceMap[prefix + it.name] = obj
                methodMap[prefix + it.name] = it
            }
    }

    /**
     * asynchronous call javascript function [methodName] with [args] as params.
     */
    fun <T> call(methodName: String, vararg args: Any?, callback: JsCallback<T>?) {
        val jsonArray = JSONArray(args.toList())
        val callInfo = CallInfo(methodName, jsonArray.toString(), callId.getAndIncrement())
        if (callback != null) {
            callMap[callInfo.callId] = callback as JsCallback<Any>
        }
        callInner(callInfo)
    }

    /**
     * function type version of asynchronous [call]
     */
    fun <T> call(methodName: String, vararg arg: Any?, callback: (T?) -> Unit) {
        call(methodName, *arg, callback = object : JsCallback<T> {
            override fun onReceiveValue(ret: T?) {
                callback(ret)
            }
        })
    }

    /**
     * Suspended call javascript function [methodName] with [args] as params.
     */
    suspend fun <T> call(methodName: String, vararg args: Any?): T? =
        suspendCancellableCoroutine { cont ->
            call<T>(methodName, *args) { ret ->
                cont.resume(ret)
            }
        }

    inline fun <reified T> getJsInterface(): T {
        val clazz = T::class.java
        val proxy = InvocationHandler { _, method, args ->
            val paramTypes = method.parameterTypes
            val hasJsCallback = paramTypes.isNotEmpty() && JsCallback::class.java.isAssignableFrom(paramTypes.last())
            val lastArg = args?.lastOrNull()
            return@InvocationHandler when {
                hasJsCallback -> callWithCallback(method, args)
                lastArg is Continuation<*> -> callSuspended(method, args)
                lastArg is Function1<*, *> -> call<Any>(
                    method.name, *args.take(args.size - 1).toTypedArray(),
                    callback = { ret: Any? ->
                        (lastArg as Function1<Any?, Unit>)(ret)
                    })
                else -> call<Any>(method.name, *args, callback = null)
            }
        }
        return Proxy.newProxyInstance(proxy.javaClass.classLoader, arrayOf(clazz), proxy) as T
    }

    fun <T> addEventListener(event: String, listener: EventListener<T>) {
        if (listenerMap[event] == null) {
            listenerMap[event] = mutableListOf()
        }
        listenerMap[event]!!.add(listener as EventListener<Any?>)
    }

    fun <T> addEventListener(event: String, call: (T?) -> Unit) {
        addEventListener(event, object : EventListener<T> {
            override fun onEvent(arg: T?) {
                call(arg)
            }
        })
    }

    fun removeEventListeners(event: String?) {
        if (event != null) {
            listenerMap[event]?.clear()
        } else {
            listenerMap.clear()
        }
    }

    fun emit(event: String, arg: Any? = null) {
        emitInner(Event(event, arg))
    }

    fun callWithCallback(method: Method, args: Array<Any?>?) {
        val argsButLast = if (args?.isNotEmpty() == true) {
            args.take(args.size - 1)
        } else {
            emptyList()
        }
        val callback = args?.lastOrNull() as? JsCallback<Any>
        call(method.name, *argsButLast.toTypedArray(), callback = callback)
    }

    fun callSuspended(method: Method, args: Array<Any?>): Any {
        val lastArg = args.last()
        val cont = lastArg as Continuation<Any?>
        val argsButLast = args.take(args.size - 1)
        call<Any>(method.name, *argsButLast.toTypedArray()) { ret ->
            cont.resume(ret)
        }
        return COROUTINE_SUSPENDED
    }

    private fun callInner(callInfo: CallInfo) {
        val script = "window.dispatchNativeCall($callInfo)"
        uiThread {
            jsEvaluator(script)
        }
    }

    private fun emitInner(event: Event) {
        val script = "window.dispatchNativeEvent($event)"
        uiThread {
            jsEvaluator(script)
        }
    }

    private fun makeJsCallback(ret: JSONObject) {
        val script = "window.dispatchCallbackFromNative($ret)"
        uiThread {
            jsEvaluator(script)
        }
    }

    private fun dispatchJsCall(namespace: String, param: Any?, callId: Long): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("ret", "")
            jsonObject.put("message", "")
            jsonObject.put("code", -1)

            val obj = interfaceMap[namespace]
            val method = methodMap[namespace]
            if (obj == null) {
                val msg = "native YTKJavascriptInterface for $namespace not found."
                logError(msg)
                jsonObject.put("message", msg)
                if (callId != -1L) {
                    jsonObject.put("callId", callId)
                    makeJsCallback(jsonObject)
                }
                return jsonObject.toString()
            }
            if (method == null) {
                val msg = "native method:$namespace not found."
                logError(msg)
                jsonObject.put("message", msg)
                if (callId != -1L) {
                    jsonObject.put("callId", callId)
                    makeJsCallback(jsonObject)
                }
                return jsonObject.toString()
            }
            val realParam = when {
                param.isNull -> emptyArray()
                param is JSONArray -> {
                    Array(param.length()) {
                        val obj = param[it]
                        if (obj == JSONObject.NULL) {
                            null
                        } else {
                            obj
                        }
                    }
                }
                else -> arrayOf(param)
            }
            val lastParam = method.parameterTypes.lastOrNull()
            val isLambda = lastParam != null && Function1::class.java.isAssignableFrom(lastParam)
            val isAsync = lastParam != null && JsCallback::class.java.isAssignableFrom(lastParam)
            when {
                isLambda -> {
                    val callback = { ret: Any ->
                        val jsObj = JSONObject(jsonObject.toString())
                        if (ret is Throwable) {
                            jsObj.put("message", ret.message)
                            jsObj.put("code", -1)
                        } else {
                            jsObj.put("ret", ret)
                            jsObj.put("code", 0)
                        }
                        jsObj.put("callId", callId)
                        makeJsCallback(jsObj)
                    }
                    method.invoke(obj, *realParam, callback)
                    jsonObject.put("code", 0)
                    return jsonObject.toString()
                }
                isAsync -> {
                    val callback = object : JsCallback<Any> {
                        override fun onReceiveValue(ret: Any?) {
                            val jsObj = JSONObject(jsonObject.toString())
                            if (ret is Throwable) {
                                jsObj.put("message", ret.message)
                                jsObj.put("code", -1)
                            } else {
                                jsObj.put("ret", ret)
                                jsObj.put("code", 0)
                            }
                            jsObj.put("callId", callId)
                            makeJsCallback(jsObj)
                        }
                    }
                    method.invoke(obj, *realParam, callback)
                    jsonObject.put("code", 0)
                    return jsonObject.toString()
                }
                else -> {
                    val ret = method.invoke(obj, *realParam)
                    jsonObject.put("ret", ret)
                    jsonObject.put("code", 0)
                    return jsonObject.toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = "dispatchJsCall() occurs Exception:$e"
            logError(msg)
            jsonObject.put("message", msg)
        }
        return jsonObject.toString()
    }

    private fun dispatchJsCallback(callId: Long, ret: Any?) {
        uiThread {
            callMap[callId]?.onReceiveValue(ret)
            callMap.remove(callId)
        }
    }

    private fun dispatchJsEvent(event: String, param: Any?) {
        uiThread {
            listenerMap[event]?.forEach { it.onEvent(param) }
        }
    }

    private fun uiThread(call: () -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(call)
        } else {
            call()
        }
    }

    private fun logError(msg: String) {
        if (debugMode) {
            Log.e(TAG, msg)
            uiThread {
                val script = "alert('YTKJsBridge Error:${msg.replace("'", "\\'")}')"
                jsEvaluator(script)
                Log.e(TAG, script)
            }
        }
    }

    private class CallInfo(val methodName: String, val args: String?, val callId: Long) {
        override fun toString(): String {
            val jsonObject = JSONObject()
            try {
                jsonObject.put("methodName", methodName)
                jsonObject.put("args", args)
                jsonObject.put("callId", callId)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return jsonObject.toString()
        }
    }

    private class Event(val eventName: String, val arg: Any?) {
        override fun toString(): String {
            val jsonObject = JSONObject()
            try {
                jsonObject.put("event", eventName)
                jsonObject.put("arg", arg)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return jsonObject.toString()
        }
    }

    companion object {
        const val BRIDGE_NAME = "YTKJsBridge"

        private val Any?.isNull: Boolean
            get() = this == null || this == JSONObject.NULL
    }
}

fun <T> ((T) -> Unit).error(e: Throwable) =
    (this as ((Any) -> Unit))(e)

fun JsCallback<*>.error(e: Throwable) =
    (this as JsCallback<Any>).onReceiveValue(e)
