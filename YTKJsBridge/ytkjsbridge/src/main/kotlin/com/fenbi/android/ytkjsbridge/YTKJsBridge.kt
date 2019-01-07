package com.fenbi.android.ytkjsbridge

import android.os.Handler
import android.os.Looper
import android.support.annotation.Keep
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
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

/**
 * Created by yangjw on 2019/1/2.
 */

class YTKJsBridge {

    private val callId by lazy { AtomicLong() }
    private val callMap by lazy { mutableMapOf<Long, JsCallback<Any>?>() }
    private val interfaceMap by lazy { mutableMapOf<String, Any>() }
    private val methodMap by lazy { mutableMapOf<String, Any>() }
    private val handler = Handler(Looper.getMainLooper())

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
                    val param = jsonObject.get("args")
                    val callId = jsonObject.optLong("callId")
                    return dispatchJsCall(methodName, param, callId)
                } catch (e: JSONException) {

                }
            }
            return null
        }
    }

    fun addYTKJavascriptInterface(obj: Any, namespace: String = "") {
        obj::class.functions
            .filter { it.findAnnotation<JavascriptInterface>() != null }
            .forEach {
                val prefix = if (namespace.isNotEmpty()) "$namespace." else namespace
                interfaceMap[prefix + it.name] = obj
                methodMap[it.name] = obj
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
                lastArg is Continuation<*> -> callSuspended<T>(method, args)
                lastArg is Function1<*, *> -> call<T>(
                    method.name,
                    *args.orEmpty().take(args.size - 1).toTypedArray(),
                    callback = { ret: Any? ->
                        (lastArg as Function1<Any?, Unit>)(ret)
                    })
                else -> throw IllegalStateException("the interface you declared do not satisfy YTKJsBridge requirements.")
            }
        }
        return Proxy.newProxyInstance(proxy.javaClass.classLoader, arrayOf(clazz), proxy) as T
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

    fun <T> callSuspended(method: Method, args: Array<Any?>): Any {
        val lastArg = args.last()
        val cont = lastArg as Continuation<T?>
        val argsButLast = args.take(args.size - 1)
        call<T>(method.name, *argsButLast.toTypedArray()) { ret ->
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

            val obj = if (namespace.contains(".")) interfaceMap[namespace] else methodMap[namespace]
            val index = namespace.indexOfLast { it == '.' }
            val methodName = namespace.substring(if (index < 0) 0 else index + 1)
            if (obj == null) {
                //log
                jsonObject.put("message", "native interface:$namespace not found.")
                return jsonObject.toString()
            }
            var isAsync = false
            var isLambda = false
            var method: Method? = null
            try {
                val paramTypes = if (param.isNull)
                    arrayOf(Function1::class.java)
                else
                    arrayOf(param!!::class.java, Function1::class.java)
                method = obj.javaClass.getMethod(methodName, *paramTypes)
                isLambda = true
            } catch (e: Exception) {

            }
            try {
                val paramTypes = if (param.isNull)
                    arrayOf(JsCallback::class.java)
                else
                    arrayOf(param!!::class.java, JsCallback::class.java)
                method = obj.javaClass.getMethod(methodName, *paramTypes)
                isAsync = true
            } catch (e: Exception) {

            }
            if (method == null) {
                try {
                    val paramTypes = if (param.isNull)
                        emptyArray()
                    else
                        arrayOf(param!!::class.java)
                    method = obj.javaClass.getMethod(methodName, *paramTypes)
                } catch (e: Exception) {

                }
            }
            if (method == null) {
                //log
                jsonObject.put("message", "native method:$namespace not found.")
                return jsonObject.toString()
            }
            when {
                isLambda -> {
                    val callback = { ret: Any ->
                        jsonObject.put("ret", ret)
                        jsonObject.put("code", 0)
                        jsonObject.put("callId", callId)
                        makeJsCallback(jsonObject)
                    }
                    if (param.isNull) {
                        method.invoke(obj, callback)
                    } else {
                        method.invoke(obj, param, callback)
                    }
                    jsonObject.put("code", 0)
                    return jsonObject.toString()
                }
                isAsync -> {
                    val callback = object : JsCallback<Any> {
                        override fun onReceiveValue(ret: Any?) {
                            jsonObject.put("ret", ret)
                            jsonObject.put("code", 0)
                            jsonObject.put("callId", callId)
                            makeJsCallback(jsonObject)
                        }
                    }
                    if (param.isNull) {
                        method.invoke(obj, callback)
                    } else {
                        method.invoke(obj, param, callback)
                    }
                    jsonObject.put("code", 0)
                    return jsonObject.toString()
                }
                else -> {
                    val ret = if (param.isNull) {
                        method.invoke(obj)
                    } else {
                        method.invoke(obj, param)
                    }
                    jsonObject.put("ret", ret)
                    jsonObject.put("code", 0)
                    return jsonObject.toString()
                }
            }
        } catch (e: JSONException) {
            try {
                jsonObject.put("message", "native occurs JSONException:$e.")
            } catch (e: JSONException) {

            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return jsonObject.toString()
    }

    private fun dispatchJsCallback(callId: Long, ret: Any?) {
        uiThread {
            callMap[callId]?.onReceiveValue(ret)
            callMap.remove(callId)
        }
    }

    private fun uiThread(call: () -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(call)
        } else {
            call()
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
                //log
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
