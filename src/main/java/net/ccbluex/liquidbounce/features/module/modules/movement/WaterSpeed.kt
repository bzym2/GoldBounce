/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.block.BlockUtils.getBlock
import net.ccbluex.liquidbounce.value.floatValue
import net.minecraft.block.BlockLiquid

object WaterSpeed : Module("WaterSpeed", Category.MOVEMENT, gameDetecting = false) {
    private val speed by floatValue("Speed", 1.2f, 1.1f..1.5f)

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val thePlayer = mc.thePlayer ?: return

        if (thePlayer.isInWater && getBlock(thePlayer.position) is BlockLiquid) {
            val speed = speed

            thePlayer.motionX *= speed
            thePlayer.motionZ *= speed
        }
    }
}