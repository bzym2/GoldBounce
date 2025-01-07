package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.NetworkManager
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.value.*
import net.ccbluex.liquidbounce.utils.MovementUtils
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Target

object MoreKB : Module(name = "MoreKB", category = Category.COMBAT) {
    private val mode by ListValue(
        "Mode", arrayOf<String>("Legit Fast", "Packet"), "Legit Test",
    )
    private val onlyGround: BoolValue = BoolValue("Only Ground", true)
    var ticks: Int = 0

    var target: EntityLivingBase? = null

    @EventTarget
    fun onAttack(event: AttackEvent) {
        if (Target().getTargetEntity() is EntityLivingBase) {
            target = Target().getTargetEntity() as EntityLivingBase
            ticks = 2
        }
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent?) {
        if (target != null && MovementUtils.isMoving()) {
            if ((onlyGround.get() && mc.thePlayer.onGround || !onlyGround.get())) {
                when (mode) {
                    "Legit Fast" -> mc.thePlayer.sprintingTicksLeft = 0
                    "Packet" -> {
                        sendPacket(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING))
                        sendPacket(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING))
                    }
                }
            }
            target = null
        }
    }
}