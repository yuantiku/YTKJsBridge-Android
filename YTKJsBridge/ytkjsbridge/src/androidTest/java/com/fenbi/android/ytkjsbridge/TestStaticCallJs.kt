package com.fenbi.android.ytkjsbridge

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
        val future: CompletableFuture<Int> = CompletableFuture()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val jsService = webView.getJsInterface<TestJsService>()
            GlobalScope.launch {
                delay(5000)         //wait js script to be load
                val sum = jsService.testSync(1, 2)
                future.complete(sum)
            }
        }
        assertEquals(3, future.get())
    }

    @Test
    fun testAsyncCallWithJsCallback() {
        val future = CompletableFuture<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val jsService = webView.getJsInterface<TestJsService>()
            GlobalScope.launch {
                delay(5000)
                jsService.testAsyncWithJsCall("tom", object : JsCallback<String> {
                    override fun onReceiveValue(ret: String?) {
                        future.complete(ret)
                    }
                })
            }
        }
        assertEquals("hello tom", future.get())
    }

    @Test
    fun testAsyncCallWithLambda(){
        val future = CompletableFuture<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val jsService = webView.getJsInterface<TestJsService>()
            GlobalScope.launch {
                delay(5000)
                jsService.testAsyncWithLambda("lisa"){
                    future.complete(it)
                }
            }
        }
        assertEquals("hello lisa", future.get())
    }
}