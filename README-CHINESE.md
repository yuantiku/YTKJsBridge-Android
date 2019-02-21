# YTKJsBridge-Android

一个基于kotlin的javascript bridge

## 特性

* 双向调用
* 同步、异步调用 \*
* 动态、静态调用
* 不需要继承定制的WebView，代码侵入性小
* 支持腾讯x5内核

\* Android调用JS仅支持suspend function形式的同步调用

## 使用

### 初始化

在需要使用YTKJsBridge的h5页面上引入[ytkjsbridge.js](https://github.com/yuantiku/YTKJsBridge-Android/blob/master/YTKJsBridge/ytkjsbridge/ytkjsbridge.js)文件，或者在h5页面上通过url方式引入。

```http
<script type="text/javascript" src="https://conan-online.fbcontent.cn/conan-math/JSBridge/index.js"></script>
```

同时使用YTKJsBridge前要进行初始化`initYTKJsBridge()`,初始化完成后再进行loadUrl等操作。

```java
mWebView.initYTKJsBridge()    //you should call initYTKJsBridge() before using loadUrl()
```

### Android调用JS

Javascript:

```javascript
//注册 javascript API
JSBridge.bindCall('functionName', function(arg){
    return arg;
})
```

#### 异步调用

* 动态

```kotlin
webView.call("functionName", "arg1") { result: String ->
    ...
}
```

* 静态

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

#### 同步调用

仅支持suspend function的方式，详见
[kotlin coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html)

* 动态：

```kotlin
GlobalScope.launch {
    val result = webView.call("functionName", "arg1")
    ...
}
```

其中WebView.call是一个suspend函数

* 静态：

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

### JS调用Android

Android端：

```kotlin
object JsApi {
    //同步API
    @JavascriptInterface
    fun testSync(msg1: String, msg2: String): String {
        return "$msg1 $msg2［syn call］"
    }

    //异步API
    @JavascriptInterface
    fun testAsync(msg1: String, msg2:String, onComplete: JsCallback) {
        // do some work
        onComplete.onReceiveValue("$msg1 $msg2 [ asyn call]")
    }
}

webView.addYTKJavascriptInterface(JsApi)
```

可以添加多个JsApi，并且无需指定注入变量名，在javascript中统一用ytkBridge调用。

Javascript:

```javascript
JSBridge.call(method, args, async)
//method: 方法名
//args: 传给客户端的参数，如果有回调请在 args 注入。如：args = { trigger: () => {} }
//async: 是否异步调用
```

#### namespace

`addYTKJavascriptInterface`可指定一个namespace参数，用于添加多个JsApi对象时的方
法重名问题。例如

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

前端调用时用

```javascript
var str = JSBridge.call("api1.testFunc", {"some msg"});
```

如果一个WebView同时添加了`JsApi1`和`JsApi2`，并且不指定namespace，前端在调用重名
的方法时会调用最后一个被添加的对象中的方法。

### 事件机制

除了native与javascript的双向方法调用外，YTKJsBridge还提供一套供双方互相发送以及监听事件的机制。

#### native端

```kotlin
//发送event
mWebView.emit("onPause") //无参
mWebView.emit("onPause", "hello") //带参

//监听event
mWebView.addEventListener("onReady"){ arg ->
 //do something
}
//or
mWebView.addEventListener("onReady",object:EventListener<String>{
     override fun onEvent(arg: String?){
         //do something
     }
})

//解除某一事件的监听
mWebView.removeEventListeners("onReady")
//解除所有事件监听
mWebView.removeEventListeners(null)
```

#### javascript端

```javascript
//发送event
JSBridge.emit("onReady") //无参
JSBridge.emit("onClick", "hello") //带参

//监听event
JSBridge.listen("onPause",function(arg){
     //do something
 })
```

native或javascript发送的event只会由对方注册的监听器接收，每一事件可以注册多个listener，每一事件支持一个参数。

### 调试

debug环境下默认启用调试模式，可以通过`WebView.debugMode=false`手动关闭，请使用“YTKJsBridge”作为TAG过滤日志。此外调试模式下客户端YTKJsBridge的内部异常会在WebView中以弹窗形式显示出来。
