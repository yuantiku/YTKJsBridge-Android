package com.fenbi.android.demo

import com.fenbi.android.annotation.YTKJsInterface
import com.fenbi.android.ytkjsbridge.JsCallback

/**
 * Created by yangjw on 2019/1/2.
 */
@YTKJsInterface
interface JsService {

    fun test(str:String,callback:JsCallback<Int>)
}