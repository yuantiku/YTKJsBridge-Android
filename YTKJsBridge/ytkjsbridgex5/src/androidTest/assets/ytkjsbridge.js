function dispatchNativeCall(call){
    var method=call.methodName
    var args=call.args
    var result=window[method](JSON.parse(args))
    var json={
       "callId":call.callId,
       "ret":result
    }
    window.YTKJsBridge.makeCallback(JSON.stringify(json))
}

function dispatchCallbackFromNative(call){
    var callId=call.callId
    var result=call.ret
    alert("return value from native call "+result)
}

function testSync(args) {
    return args[0]+args[1]
}

function testAsyncWithJsCall(args){
    return 'hello '+args[0]
}

function testAsyncWithLambda(args){
    return 'hello '+args[0]
}

function testCallNative(){
        var json={
            "methodName":"testNative",
            "args":"hello world",
            "callId":-1
        };
        window.YTKJsBridge.callNative(JSON.stringify(json));
}
