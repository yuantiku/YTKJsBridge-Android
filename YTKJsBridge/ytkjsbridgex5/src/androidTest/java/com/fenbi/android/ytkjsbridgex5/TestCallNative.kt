package com.fenbi.android.ytkjsbridgex5

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.webkit.JavascriptInterface
import com.tencent.smtt.sdk.WebChromeClient
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by yangjw on 2019/1/9.
 */
@RunWith(AndroidJUnit4::class)
class TestCallNative {

    private fun initWebView() = WebView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
        initYTKJsBridge()
    }

    @Test
    fun testCallNative() {
        val countDownLatch = CountDownLatch(1)
        var ret: String? = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = initWebView()
            webView.addYTKJavascriptInterface(object {
                @JavascriptInterface
                fun testNative(msg: String?) {
                    ret = msg
                    countDownLatch.countDown()
                }
            })
            webView.loadUrl("file:///android_asset/test-call-native.html")
        }
        countDownLatch.await(60, TimeUnit.SECONDS)
        assertEquals("hello world", ret)
    }

}