package com.fenbi.android.ytkjsbridge

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture

/**
 * Created by yangjw on 2019/1/9.
 */
@RunWith(AndroidJUnit4::class)
class TestCallNative {

    private fun initWebView() = WebView(InstrumentationRegistry.getTargetContext()).apply {
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
        initYTKJsBridge()
    }

    @Test
    fun testCallNative() {
        val future = CompletableFuture<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = initWebView()
            webView.addYTKJavascriptInterface(object {
                @JavascriptInterface
                fun testNative(msg: String?) {
                    future.complete(msg)
                }
            })
            webView.loadUrl("file:///android_asset/test-call-native.html")
        }
        assertEquals("hello world", future.get())
    }

}