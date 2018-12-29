# YTKJsBridge-Android

一个基于kotlin的javascript bridge

## 特性

* 双向调用
* 同步、异步调用 \*
* 动态、静态调用
* 不需要继承定制的WebView，代码侵入性小

\* Android端仅支持suspend function形式的同步调用

## 使用

### Android调用JS

Javascript:

```javascript
//注册 javascript API
ytkBridge.def('functionName', function(arg){
    return arg + "ok";
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
@YTKJsInerface
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
@YTKJsInerface
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
@YTKJsApi
class JsApi {
    //同步API
    @JavascriptInterface
    fun testSync(msg: Any): String {
        return msg + "［syn call］"
    }

    //异步API
    @JavascriptInterface
    fun testAsync(msg: Any, onComplete: (String) -> Unit) {
        // do some work
        onComplete(msg+" [ asyn call]")
    }
}

webView.addYTKJavascriptInterface(JsApi())
```

可以添加多个JsApi，可省略注入变量名，在javascript中统一用ytkBridge调用。

Javascript:

```javascript
//同步调用
var str = ytkBridge.call("testSync", "some msg");

//异步调用
ytkBridge.call("testAsync", "some msg", function (v) {
  alert(v);
})
```
