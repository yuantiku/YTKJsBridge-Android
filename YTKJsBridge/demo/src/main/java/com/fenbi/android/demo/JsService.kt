package com.fenbi.android.demo

import androidx.annotation.Keep
import com.fenbi.android.ytkjsbridge.JsCallback

/**
 * Created by yangjw on 2019/1/2.
 */
@Keep
interface JsService {

    fun showMessage(str: String?, callback: JsCallback<Int>?)

    fun showMessage2(str1: String?, str2: String?, int1: Int?, callback: (Int?)->Unit)

    suspend fun testSync(arg1: String, arg2: Int): String
}