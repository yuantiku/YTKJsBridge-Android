package com.fenbi.android.ytkjsbridgex5

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.fenbi.android.ytkjsbridge.JsCallback
import com.tencent.smtt.sdk.WebChromeClient
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by yangjw on 2019/1/9.
 */
@RunWith(AndroidJUnit4::class)
class TestDynamicCallJs {

    private fun initWebView() = WebView(InstrumentationRegistry.getTargetContext()).apply {
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
        initYTKJsBridge()
        loadUrl("file:///android_asset/test-call-js.html")
    }

    @Test
    fun testCallSync() {
        val countDownLatch = CountDownLatch(1)
        var ret: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = initWebView()
            GlobalScope.launch {
                delay(5000)         //wait js script to be load
                ret = webView.call<Int>("testSync", 1, 2)
                countDownLatch.countDown()
            }
        }
        countDownLatch.await(60, TimeUnit.SECONDS)
        assertEquals(3, ret)
    }

    @Test
    fun testCallAsyncWithJsCall() {
        val countDownLatch = CountDownLatch(1)
        var ret: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = initWebView()
            GlobalScope.launch {
                delay(5000)
                webView.call("testAsyncWithJsCall", "tom", callback = object : JsCallback<String> {
                    override fun onReceiveValue(msg: String?) {
                        ret = msg
                        countDownLatch.countDown()
                    }
                })
            }
        }
        countDownLatch.await(60, TimeUnit.SECONDS)
        assertEquals("hello tom", ret)
    }

    @Test
    fun testCallAsyncWithLambda() {
        val countDownLatch = CountDownLatch(1)
        var ret: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = initWebView()
            GlobalScope.launch {
                delay(5000)
                webView.call<String>("testAsyncWithLambda","lisa"){
                    ret = it
                    countDownLatch.countDown()
                }
            }
        }
        countDownLatch.await(60, TimeUnit.SECONDS)
        assertEquals("hello lisa", ret)
    }
}