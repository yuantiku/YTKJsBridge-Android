package com.fenbi.android.ytkjsbridge

import android.annotation.SuppressLint
import android.os.Looper
import android.support.annotation.Keep
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

/**
 * Created by yangjw on 2019/1/2.
 */
const val BRIDGE_NAME = "YTKJsBridge"

private val WebView.callId by lazy { AtomicInteger() }

private val WebView.callMap by lazy { HashMap<Int, JsCallback<Any>?>() }

/**
 * do init work before using YTKJsBridge on a WebView.
 */
@SuppressLint("JavascriptInterface")
fun WebView.initYTK() {

    settings.javaScriptEnabled = true
    addJavascriptInterface(object : Any() {

        /**
         * to get return value from javascript call.
         */
        @Keep
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
    }, BRIDGE_NAME)
}

/**
 * asynchronous call javascript function [methodName] with [args] as params.
 */
fun <T> WebView.call(methodName: String, vararg args: Any?, callback: JsCallback<T>?) {
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
fun <T> WebView.call(methodName: String, vararg arg: Any?, callback: (T?) -> Unit) {
    call(methodName, *arg, callback = object : JsCallback<T> {
        override fun onReceiveValue(ret: T?) {
            callback(ret)
        }
    })
}

/**
 * Suspended call javascript function [methodName] with [args] as params.
 */
suspend fun <T> WebView.call(methodName: String, vararg args: Any?): T? =
    suspendCancellableCoroutine { cont ->
        call<T>(methodName, *args) { ret ->
            cont.resume(ret)
        }
    }

inline fun <reified T> WebView.getJsInterface(): T {
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

fun WebView.callWithCallback(method: Method, args: Array<Any?>?) {
    val argsButLast = if (args?.isNotEmpty() == true) {
        args.take(args.size - 1)
    } else {
        emptyList()
    }
    val callback = args?.lastOrNull() as? JsCallback<Any>
    call(method.name, *argsButLast.toTypedArray(), callback = callback)
}

fun <T> WebView.callSuspended(method: Method, args: Array<Any?>): Any {
    val lastArg = args.last()
    val cont = lastArg as Continuation<T?>
    val argsButLast = args.take(args.size - 1)
    call<T>(method.name, *argsButLast.toTypedArray()) { ret ->
        cont.resume(ret)
    }
    return COROUTINE_SUSPENDED
}

private fun WebView.callInner(callInfo: CallInfo) {
    //todo 前端提供一个接口，本地通过该接口将调用信息通过json格式传给前端
    //evaluatejavascript() or loadUrl()
    val script = "window.handleNativeCall($callInfo)"     //暂定
    uiThread {
        evaluateJavascript(script, null)
    }
}


private fun WebView.dispatchJsCallback(callId: Int, ret: Any?) {
    uiThread {
        callMap[callId]?.onReceiveValue(ret)
        callMap.remove(callId)
    }
}

fun WebView.clearYTK() {
    //log
    callId.set(0)
    callMap.clear()
}

fun WebView.uiThread(call: () -> Unit) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        post(call)
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


