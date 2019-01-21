package com.fenbi.android.ytkjsbridgex5

import com.fenbi.android.ytkjsbridge.JsCallback

/**
 * Created by yangjw on 2019/1/9.
 */
interface TestJsService {
    suspend fun testSync(a: Int, b: Int):Int

    fun testAsyncWithJsCall(msg: String, callback: JsCallback<String>)

    fun testAsyncWithLambda(msg: String, calback: (String) -> Unit)
}