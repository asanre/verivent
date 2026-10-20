package io.github.asanre.verivent.baseline

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

class FlowScope internal constructor() {

    fun <T> step(name: String, action: () -> T): T = action()

    fun <T> bound(value: T): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> =
        PropertyDelegateProvider { _, _ -> ReadOnlyProperty { _, _ -> value } }
}
