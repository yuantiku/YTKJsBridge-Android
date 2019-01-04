package com.fenbi.android.demo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import com.fenbi.android.ytkjsbridge.JsCallback
import com.fenbi.android.ytkjsbridge.addYTKJavascriptInterface
import com.fenbi.android.ytkjsbridge.getJsInterface
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val mWebView by lazy { findViewById<WebView>(R.id.web_view) }
    private val mCallJsBt by lazy { findViewById<Button>(R.id.call_js_bt) }
    private val mCallJsBt2 by lazy { findViewById<Button>(R.id.call_js_bt_2) }
    private val mCallJsBt3 by lazy { findViewById<Button>(R.id.call_js_bt_3) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        with(mWebView) {
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/test-native-call-js.html")
        }

        val jsInterface = mWebView.getJsInterface<JsService>()
        mCallJsBt.setOnClickListener {
            jsInterface.showMessage("hello world", object : JsCallback<Int> {
                override fun onReceiveValue(ret: Int?) {
                    Toast.makeText(this@MainActivity, "js call return value $ret", Toast.LENGTH_SHORT).show()
                }
            })
        }
        mCallJsBt2.setOnClickListener {
            jsInterface.showMessage2("hello", "world", 2019, object : JsCallback<Int> {
                override fun onReceiveValue(ret: Int?) {
                    Toast.makeText(this@MainActivity, "js call return value $ret", Toast.LENGTH_SHORT).show()
                }
            })
        }
        mCallJsBt3.setOnClickListener {
            GlobalScope.launch {
                val ret = jsInterface.testSync("suspend call ", 111)
                Toast.makeText(this@MainActivity, ret, Toast.LENGTH_SHORT).show()
            }
        }

        mWebView.addYTKJavascriptInterface(object {
            @JavascriptInterface
            fun toastSync(msg: String?): Int {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "synchronous call with param: $msg", Toast.LENGTH_SHORT).show()
                }
                return 666
            }
        }, "com.fenbi.android")

        mWebView.addYTKJavascriptInterface(object {
            @JavascriptInterface
            fun toastAsync(msg: String?, callback: JsCallback<Int>): Int {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "asynchronous call with param: $msg", Toast.LENGTH_SHORT).show()
                }
                callback.onReceiveValue(233)
                return 0
            }
        })
    }
}
