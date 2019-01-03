function handleNativeCall(call){
    var method=call.methodName
    var param=call.args
    var result=window[method](param)
    var json={
       "callId":call.callId,
       "ret":result
    }
    window.YTKJsBridge.makeCallback(JSON.stringify(json))
}

function test(message){
    alert(message)
    return 233
}