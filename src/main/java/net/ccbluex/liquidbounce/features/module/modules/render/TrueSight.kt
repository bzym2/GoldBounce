/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.value._boolean

object TrueSight : Module("TrueSight", Category.RENDER) {
    val barriers by _boolean("Barriers", true)
    val entities by _boolean("Entities", true)

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (barriers && mc.gameSettings.particleSetting == 2) {
            mc.gameSettings.particleSetting = 1
        }
    }
}