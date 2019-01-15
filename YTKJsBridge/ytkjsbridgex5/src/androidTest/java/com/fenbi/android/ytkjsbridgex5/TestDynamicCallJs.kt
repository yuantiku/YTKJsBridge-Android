package com.fenbi.android.ytkjsbridge

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.fenbi.android.ytkjsbridgex5.call
import com.fenbi.android.ytkjsbridgex5.initYTKJsBridge
import com.tencent.smtt.sdk.WebChromeClient
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture

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
        val future: CompletableFuture<Int> = CompletableFuture()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = initWebView()
            GlobalScope.launch {
                delay(5000)         //wait js script to be load
                val sum = webView.call<Int>("testSync", 1, 2)
                future.complete(sum)
            }
        }
        assertEquals(3, future.get())
    }

    @Test
    fun testCallAsyncWithJsCall() {
        val future: CompletableFuture<String> = CompletableFuture()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = initWebView()
            GlobalScope.launch {
                delay(5000)
                webView.call("testAsyncWithJsCall", "tom", callback = object : JsCallback<String> {
                    override fun onReceiveValue(ret: String?) {
                       future.complete(ret)
                    }
                })
            }
        }
        assertEquals("hello tom", future.get())
    }

    @Test
    fun testCallAsyncWithLambda() {
        val future: CompletableFuture<String> = CompletableFuture()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = initWebView()
            GlobalScope.launch {
                delay(5000)
                webView.call<String>("testAsyncWithLambda","lisa"){
                    future.complete(it)
                }
            }
        }
        assertEquals("hello lisa", future.get())
    }
}