package com.fenbi.android.annotationprocessing

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

/**
 * Created by yangjw on 2019/1/2.
 */
class AnnotationProcessor:AbstractProcessor(){
    override fun process(p0: MutableSet<out TypeElement>?, p1: RoundEnvironment?): Boolean {

        return false
    }
}