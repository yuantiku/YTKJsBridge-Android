function dispatchNativeCall(call){
    var method=call.methodName
    var args=call.args
    var result=window[method](args)
    var json={
       "callId":call.callId,
       "ret":result
    }
    window.YTKJsBridge.makeCallback(JSON.stringify(json))
}

function dispatchNativeEvent(args){
    var event=args.event
    var param=args.arg
    alert("receive event from native:"+event+"  parameter:"+param)
}

function dispatchCallbackFromNative(call){
    var callId=call.callId
    var result=call.ret
    alert("return value from native call "+result)
}

function showMessage(args){
    var json=JSON.parse(args)
    alert(json[0])
    return 233
}

function showMessage2(args){
    var json=JSON.parse(args)
    var message=json[0]+" "+json[1]+" "+json[2]
    alert(message)
    return 666
}

function testSync(args) {
    var json=JSON.parse(args)
    var message=json[0]+" "+json[1]
    alert(message)
    return "suspended"
}

function sendEvent() {
    var event = {
        "event": "onClick",
        "arg": "click event from js"
    }
    window.YTKJsBridge.sendEvent(JSON.stringify(event))
}

function callNativeSync(param){
    var json={
        "methodName":"toastSync",
        "args":param,
        "callId":-1
    }
    var result=window.YTKJsBridge.callNative(JSON.stringify(json))
    alert(result)
    return result.ret
}

function callNativeAsync(param,f){
      var json={
           "methodName":"toastAsync",
           "args":param,
           "callId":1
       }
    var result=window.YTKJsBridge.callNative(JSON.stringify(json))
    return result.ret
}
