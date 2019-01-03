package com.fenbi.android.ytkjsbridge

/**
 * Created by yangjw on 2019/1/2.
 */
interface JsCallback<in T> {
    fun onReceiveValue(ret: T?)
}