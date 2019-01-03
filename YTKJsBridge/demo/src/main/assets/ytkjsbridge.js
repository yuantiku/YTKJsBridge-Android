function handleNativeCall(call){
    var method=call.methodName
    var args=call.args
    var result=window[method](args)
    var json={
       "callId":call.callId,
       "ret":result
    }
    window.YTKJsBridge.makeCallback(JSON.stringify(json))
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