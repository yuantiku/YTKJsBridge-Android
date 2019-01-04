package com.fenbi.android.ytkjsbridge

import android.annotation.SuppressLint
import android.webkit.WebView

/**
 * @author zheng on 1/4/19
 */

private const val TAG_KEY_YTK_JS_BRIDGE = -10001

val WebView.ytkJsBridge: YTKJsBridge
    @SuppressLint("JavascriptInterface")
    get() {
        val bridge = getTag(TAG_KEY_YTK_JS_BRIDGE)
        return if (bridge is YTKJsBridge) {
            bridge
        } else {
            settings.javaScriptEnabled = true
            YTKJsBridge().also {
                it.jsEvaluator = { script -> evaluateJavascript(script, null) }
                addJavascriptInterface(it.javascriptInterface, YTKJsBridge.BRIDGE_NAME)
                setTag(TAG_KEY_YTK_JS_BRIDGE, it)
            }
        }
    }

fun WebView.addYTKJavascriptInterface(obj: Any, namespace: String = "") {
    ytkJsBridge.addYTKJavascriptInterface(obj, namespace)
}

fun <T> WebView.call(methodName: String, vararg args: Any?, callback: (T?) -> Unit) {
    ytkJsBridge.call<T>(methodName, *args, callback = callback)
}

fun <T> WebView.call(methodName: String, vararg args: Any?, callback: JsCallback<T>?) {
    ytkJsBridge.call(methodName, *args, callback = callback)
}

suspend fun <T> WebView.call(methodName: String, vararg args: Any?): T? {
    return ytkJsBridge.call(methodName, *args)
}

inline fun <reified T> WebView.getJsInterface(): T {
    return ytkJsBridge.getJsInterface()
}
