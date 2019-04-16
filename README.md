# YTKJsBridge-Android

A  javascript bridge based on new features of kotlin and written with whole kotlin language.

[中文说明](https://github.com/yuantiku/YTKJsBridge-Android/blob/master/README-CHINESE.md)

## Features

* two-way function call
* synchronized、asynchronous* function call
* dynamic、static function call
* no need to extends specific WebView, less code intrusion
* support tencent x5 WebView

*Only support suspend function type synchronized function call when it comes to Android calling Javascript function.

## Usage

### Initialization

Import [ytkjsbridge.js](https://github.com/yuantiku/YTKJsBridge-Android/blob/master/YTKJsBridge/ytkjsbridge/ytkjsbridge.js) on pages that work with YTKJsBridge, or you can import jsbridge.js by url:

```http
<script type="text/javascript" src="https://conan-online.fbcontent.cn/conan-math/JSBridge/index.js"></script>
```

Remeber to call `initYTKJsBridge()` before you use YTKJsBridge.

```java
mWebView.initYTKJsBridge()    //you should call initYTKJsBridge() before using loadUrl()
```

### Android call Javascript function

Javascript:

```javascript
//register javascript API
JSBridge.bindCall('functionName', function(arg){
    return arg;
})
```

#### asynchronous function call

* dynamic call

```kotlin
webView.call("functionName", "arg1") { result: String ->
    ...
}
```

* static static

```kotlin
interface JsFunctions {
    fun func1(
        arg1: String,
        arg2: Int,
        onComplete: ((result: String) -> Unit)? = null
    )
}

val js = webView.getJsInterface<JsFunction>()
js.func1(...) { result ->
    ...
}
```

#### synchronized function call

Only support suspend function, for more details please visit [kotlin coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html).

* dyamic call

```kotlin
GlobalScope.launch {
    val result = webView.call("functionName", "arg1")
    ...
}
```

The WebView.call here is a suspend function.

* static call

```kotlin
interface JsFunctions {
    suspend fun func1(arg1: String, arg2: Int): String
}

GlobalScope.launch {
    val js = webView.getJsInterface<JsFunction>()
    val result = js.func1("somearg", 123)
    ...
}
```

### Javascript call Android function

Android：

```kotlin
object JsApi {
    //synchronized API
    @JavascriptInterface
    fun testSync(msg1: String, msg2: String): String {
        return "$msg1 $msg2［syn call］"
    }

    //asynchronous API
    @JavascriptInterface
    fun testAsync(msg1: String, msg2: String, onComplete: JsCallback) {
        // do some work
        onComplete.onReceiveValue("$msg1 $msg2 [ asyn call]")
    }
}

webView.addYTKJavascriptInterface(JsApi)
```

You can add multiple JsApi and don't need to specific the name。

Javascript:

```javascript
JSBridge.call(method, args, async)
//method: method name
//args: parameters that should be sent to client, and if you need a callback please put the callback here like: args = { trigger: () => {} }
//async: if it's a asynchronous function call
```

#### namespace

When calling `addYTKJavascriptInterface` you can assign a namespace which is useful when there're methods with the same name in different JsApi.

Android:

```kotlin
object JsApi1 {
    @JavascriptInterface
    fun testFunc(msg: String): String {
        return msg + ", this is JsApi1"
    }
}

object JsApi2 {
    @JavascriptInterface
    fun testFunc(msg: String): String {
        return msg + ", this is JsApi2"
    }
}

webView.addYTKJavascriptInterface(JsApi1, "api1" /*namespace*/)

webView.addYTKJavascriptInterface(JsApi2, "api2")
```

Javascript:

```javascript
var str = JSBridge.call("api1.testFunc", {"some msg"});
```

Same name method in one object is not allowed.

If a WebView has called `addYTKJavascriptInterface`  mutiple times without assigning namespace, the method in the JsApi that is added at last will be invoked when javascript calling a method that exists in more than one JsApi.

### Event transmission

Besides two-way function call, YTKJsBridge also offer you a way to send&listen events between Android and Javasctipt.

Android:

```kotlin
//send event
mWebView.emit("onPause") //no parameter
mWebView.emit("onPause", "hello") //with parameter

//liste event
mWebView.addEventListener("onReady"){ arg ->
 //do something
}
//or
mWebView.addEventListener("onReady",object:EventListener<String>{
     override fun onEvent(arg: String?){
         //do something
     }
})

//remove listeners on "onReady" event
mWebView.removeEventListeners("onReady")
//remove all listeners
mWebView.removeEventListeners(null)
```

Javascript:

```javascript
//send event
JSBridge.emit("onReady") //no parameter
JSBridge.emit("onClick", "hello") //with parameter

//listen event
JSBridge.listen("onPause",function(arg){
     //do something
 })
```

Each event can be observed by mutiple listeners and each event support at most one parameter.

### Debug

Debug mode is enabled by default when building debug package, you can disable it by calling `WebView.debugMode=false`. Please usage "YTKJsBridge" as TAG to filter logs. Any exception caused by YTKJsBridge will be posted on a dialog in WebView when debug mode is enabled.
