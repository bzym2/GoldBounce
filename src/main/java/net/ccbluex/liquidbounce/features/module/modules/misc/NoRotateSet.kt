/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.Rotation
import net.ccbluex.liquidbounce.utils.RotationSettings
import net.ccbluex.liquidbounce.utils.RotationUtils
import net.ccbluex.liquidbounce.utils.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.extensions.rotation
import net.ccbluex.liquidbounce.utils.timing.WaitTickUtils
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.intRange
import net.minecraft.entity.player.EntityPlayer

object NoRotateSet : Module("NoRotateSet", Category.MISC, gameDetecting = false, hideModule = false) {
    var savedRotation = Rotation.ZERO

    private val ignoreOnSpawn by _boolean("IgnoreOnSpawn", false)
    val affectRotation by _boolean("AffectRotation", true)

    private val ticksUntilStart = intRange("TicksUntilStart", 0..0, 0..20) { affectRotation }
    private val options = RotationSettings(this) { affectRotation }.apply {
        rotationsValue.excludeWithState(true)
        applyServerSideValue.excludeWithState(true)
        resetTicksValue.excludeWithState(1)

        withoutKeepRotation()
    }

    fun shouldModify(player: EntityPlayer) = handleEvents() && (!ignoreOnSpawn || player.ticksExisted != 0)

    fun rotateBackToPlayerRotation() {
        val player = mc.thePlayer ?: return

        currentRotation = player.rotation

        // This connects with the SimulateShortStop code, [performAngleChange] function.
        WaitTickUtils.schedule(ticksUntilStart.random, RotationUtils)

        setTargetRotation(savedRotation, options = options)
    }
}