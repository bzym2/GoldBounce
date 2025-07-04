/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.MovementUtils.serverOnGround
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.inventory.ItemUtils.isConsumingItem
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.floatValue
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.network.play.client.C03PacketPlayer

object FastUse : Module("FastUse", Category.PLAYER) {

    private val mode by choices("Mode", arrayOf("Instant", "NCP", "AAC", "Custom"), "NCP")

    private val delay by intValue("CustomDelay", 0, 0..300) { mode == "Custom" }
    private val customSpeed by intValue("CustomSpeed", 2, 1..35) { mode == "Custom" }
    private val customTimer by floatValue("CustomTimer", 1.1f, 0.5f..2f) { mode == "Custom" }

    private val noMove by _boolean("NoMove", false)

    private val msTimer = MSTimer()
    private var usedTimer = false

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val thePlayer = mc.thePlayer ?: return

        if (usedTimer) {
            mc.timer.timerSpeed = 1F
            usedTimer = false
        }

        if (!isConsumingItem()) {
            msTimer.reset()
            return
        }

        when (mode.lowercase()) {
            "instant" -> {
                repeat(35) {
                    sendPacket(C03PacketPlayer(serverOnGround))
                }

                mc.playerController.onStoppedUsingItem(thePlayer)
            }

            "ncp" -> if (thePlayer.itemInUseDuration > 14) {
                repeat(20) {
                    sendPacket(C03PacketPlayer(serverOnGround))
                }

                mc.playerController.onStoppedUsingItem(thePlayer)
            }

            "aac" -> {
                mc.timer.timerSpeed = 1.22F
                usedTimer = true
            }

            "custom" -> {
                mc.timer.timerSpeed = customTimer
                usedTimer = true

                if (!msTimer.hasTimePassed(delay))
                    return

                repeat(customSpeed) {
                    sendPacket(C03PacketPlayer(serverOnGround))
                }

                msTimer.reset()
            }
        }
    }

    @EventTarget
    fun onMove(event: MoveEvent) {
        mc.thePlayer ?: return

        if (!isConsumingItem() || !noMove)
            return

        event.zero()
    }

    override fun onDisable() {
        if (usedTimer) {
            mc.timer.timerSpeed = 1F
            usedTimer = false
        }
    }

    override val tag
        get() = mode
}
