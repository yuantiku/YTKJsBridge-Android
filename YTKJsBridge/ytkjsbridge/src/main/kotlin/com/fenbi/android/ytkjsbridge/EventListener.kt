package com.fenbi.android.ytkjsbridge

/**
 * Created by yangjw on 2019/1/15.
 */
interface EventListener<T> {
    fun onEvent(arg: T?)
}