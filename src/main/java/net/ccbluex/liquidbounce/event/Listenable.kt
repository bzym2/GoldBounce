/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.event

import java.lang.reflect.Method

interface Listenable {
    fun handleEvents() = true
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class EventTarget(val ignoreCondition: Boolean = false, val priority: Int = 0)

internal class EventHook(val eventClass: Listenable, val method: Method, eventTarget: EventTarget) {
    val ignoreCondition = eventTarget.ignoreCondition
    val priority = eventTarget.priority
}