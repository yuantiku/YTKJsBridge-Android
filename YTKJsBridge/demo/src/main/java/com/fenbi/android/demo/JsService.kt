package com.fenbi.android.demo

import com.fenbi.android.ytkjsbridge.JsCallback

/**
 * Created by yangjw on 2019/1/2.
 */
interface JsService {

    fun showMessage(str: String?, callback: JsCallback<Int>?)

    fun showMessage2(str1: String?, str2: String?, int1: Int?, callback: JsCallback<Int>?)

    suspend fun testSync(arg1: String, arg2: Int): String
}