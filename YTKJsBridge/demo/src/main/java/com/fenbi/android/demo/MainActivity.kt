package com.fenbi.android.demo

import android.os.Bundle
import android.support.annotation.Keep
import android.support.v7.app.AppCompatActivity
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import com.fenbi.android.ytkjsbridge.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val mWebView by lazy { findViewById<WebView>(R.id.web_view) }
    private val mCallJsBt by lazy { findViewById<Button>(R.id.call_js_bt) }
    private val mCallJsBt2 by lazy { findViewById<Button>(R.id.call_js_bt_2) }
    private val mCallJsBt3 by lazy { findViewById<Button>(R.id.call_js_bt_3) }
    private val mCallJsBt4 by lazy { findViewById<Button>(R.id.call_js_bt_4) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        with(mWebView) {
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            initYTKJsBridge()
            loadUrl("file:///android_asset/test-native-call-js.html")
        }

        val jsInterface = mWebView.getJsInterface<JsService>()
        mCallJsBt.setOnClickListener {
            mWebView.call("showMessage","hello world",callback = object:JsCallback<Int>{
                override fun onReceiveValue(ret: Int?) {
                    Toast.makeText(this@MainActivity, "js call return value $ret", Toast.LENGTH_SHORT).show()
                }
            })
        }
        mCallJsBt2.setOnClickListener {
            jsInterface.showMessage2("hello", "world", 2019) { ret ->
                Toast.makeText(this@MainActivity, "js call return value $ret", Toast.LENGTH_SHORT).show()
            }
        }
        mCallJsBt3.setOnClickListener {
            GlobalScope.launch {
                val ret = jsInterface.testSync("suspend call ", 111)
                Toast.makeText(this@MainActivity, ret, Toast.LENGTH_SHORT).show()
            }
        }
        mCallJsBt4.setOnClickListener {
            mWebView.emit("onClick", "emit from bt4")
        }
        mWebView.addYTKJavascriptInterface(@Keep object {
            @JavascriptInterface
            fun toastSync(msg: String?): Int {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "toastSync call with param: $msg", Toast.LENGTH_SHORT).show()
                }
                return 666
            }

            @JavascriptInterface
            fun toastAsync(msg: String?, msg2: Int?, callback: JsCallback<Int>): Int {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "toastAsync call with param: $msg $msg2", Toast.LENGTH_SHORT).show()
                }
                callback.onReceiveValue(233)
                return 0
            }
        })
        mWebView.addEventListener<String>("onClick"){
            Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
        }
    }
}
