package com.fenbi.android.demo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import com.fenbi.android.ytkjsbridge.JsCallback
import com.fenbi.android.ytkjsbridge.getJsInterface
import com.fenbi.android.ytkjsbridge.initYTK


class MainActivity : AppCompatActivity() {

    private val mWebView by lazy { findViewById<WebView>(R.id.web_view) }
    private val mCallJsBt by lazy { findViewById<Button>(R.id.call_js_bt) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        with(mWebView){
            initYTK()
            webChromeClient= WebChromeClient()
            webViewClient= WebViewClient()
            loadUrl("file:///android_asset/test-native-call-js.html")
        }

        mCallJsBt.setOnClickListener {
            val jsInterface=mWebView.getJsInterface<JsService>()
            jsInterface.test("hello world",object:JsCallback<Int>{
                override fun onReceiveValue(ret: Int?) {
                    Toast.makeText(this@MainActivity,"js call return value $ret", Toast.LENGTH_SHORT).show()
                }

            })
        }
    }
}
