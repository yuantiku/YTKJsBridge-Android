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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

/**
 * Created by yangjw on 2019/1/2.
 */

class YTKJsBridge {

    companion object {
        const val BRIDGE_NAME = "YTKJsBridge"
    }

    private val callId by lazy { AtomicInteger() }
    private val callMap by lazy { mutableMapOf<Int, JsCallback<Any>?>() }
    private val interfaceMap by lazy { mutableMapOf<String, Any>() }
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
                    val callId = jsonObject.optInt("callId", -1)
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
                    val param = jsonObject.optString("param")
                    val callId = jsonObject.optInt("callId")
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
            }
    }

    /**
     * asynchronous call javascript function [methodName] with [args] as params.
     */
    fun <T> call(methodName: String, vararg args: Any?, callback: JsCallback<T>?) {
        val jsonArray = JSONArray(args)
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
            val isAsync = paramTypes.isNotEmpty() && JsCallback::class.java.isAssignableFrom(paramTypes.last())
            val lastArg = args?.lastOrNull()
            return@InvocationHandler when {
                isAsync -> callWithCallback(method, args)
                lastArg is Continuation<*> -> callSuspended<T>(method, args)
                else -> call<T>(method.name, *args.orEmpty()) {}
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

    private fun dispatchJsCall(namespace: String, param: String?, callId: Int): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("ret", "")
            jsonObject.put("message", "")
            jsonObject.put("code", -1)
            val obj = interfaceMap[namespace]
            val index = namespace.indexOfLast { it == '.' }
            val methodName = namespace.substring(if (index < 0) 0 else index + 1)
            if (obj == null) {
                //log
                jsonObject.put("message", "native interface:$namespace not found.")
                return jsonObject.toString()
            }
            var isAsync = false
            var method: Method? = null
            try {
                method = obj.javaClass.getMethod(methodName, String::class.java, JsCallback::class.java)
                isAsync = true
            } catch (e: Exception) {

            }
            if (method == null) {
                try {
                    method = obj.javaClass.getMethod(methodName, String::class.java)
                } catch (e: Exception) {

                }
            }
            if (method == null) {
                //log
                jsonObject.put("message", "native method:$namespace not found.")
                return jsonObject.toString()
            }
            if (isAsync) {
                method.invoke(obj, param, object : JsCallback<Any> {
                    override fun onReceiveValue(ret: Any?) {
                        jsonObject.put("ret", ret)
                        jsonObject.put("code", 0)
                        jsonObject.put("callId", callId)
                        makeJsCallback(jsonObject)
                    }
                })
                jsonObject.put("code", 0)
                return jsonObject.toString()
            } else {
                jsonObject.put("ret", method.invoke(obj, param))
                jsonObject.put("code", 0)
                return jsonObject.toString()
            }
        } catch (e: JSONException) {
            try {
                jsonObject.put("message", "native occurs JSONException:$e.")
            } catch (e: JSONException) {

            }
        }
        return jsonObject.toString()
    }

    private fun dispatchJsCallback(callId: Int, ret: Any?) {
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

    private class CallInfo(val methodName: String, val args: String?, val callId: Int) {

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
}
