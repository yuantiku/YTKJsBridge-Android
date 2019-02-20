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
class TestStaticCallJs {


    private val webView: WebView by lazy {
        WebView(InstrumentationRegistry.getTargetContext()).apply {
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            initYTKJsBridge()
            loadUrl("file:///android_asset/test-call-js.html")
        }
    }

    @Test
    fun testSyncCall() {
        val countDownLatch = CountDownLatch(1)
        var ret: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val jsService = webView.getJsInterface<TestJsService>()
            GlobalScope.launch {
                delay(5000)         //wait js script to be load
                ret = jsService.testSync(1, 2)
                countDownLatch.countDown()
            }
        }
        countDownLatch.await(60, TimeUnit.SECONDS)
        assertEquals(3, ret)
    }

    @Test
    fun testAsyncCallWithJsCallback() {
        val countDownLatch = CountDownLatch(1)
        var ret: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val jsService = webView.getJsInterface<TestJsService>()
            GlobalScope.launch {
                delay(5000)
                jsService.testAsyncWithJsCall("tom", object : JsCallback<String> {
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
    fun testAsyncCallWithLambda(){
        val countDownLatch = CountDownLatch(1)
        var ret: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val jsService = webView.getJsInterface<TestJsService>()
            GlobalScope.launch {
                delay(5000)
                jsService.testAsyncWithLambda("lisa"){
                    ret = it
                    countDownLatch.countDown()
                }
            }
        }
        countDownLatch.await(60, TimeUnit.SECONDS)
        assertEquals("hello lisa", ret)
    }
}