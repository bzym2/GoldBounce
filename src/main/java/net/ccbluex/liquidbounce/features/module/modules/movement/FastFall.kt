package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.value.*
import net.minecraft.network.play.client.C03PacketPlayer

object FastFall : Module("FastFall", category = Category.MOVEMENT, hideModule = false) {
    private var donnotclickme = 0
    private var freeze = false

    private val listValue = ListValue("Mode", arrayOf("Normal", "Polar", "Intave", "Vulcan"), "Normal")
    private val maxFallDistance = float("MaxFallDistance", 3f, 0F.. 10F)
    private var freezeValue = float("FreezeTick", 10f, 6F.. 50F) {listValue.equals("Polar")}
    private val timerSpeed = float("TimerSpeed", 1F, 0F..5F) {listValue.equals("Vulcan") || listValue.equals("Intave")}
    override fun onEnable() {
        donnotclickme = 0
        freeze = false
        super.onEnable()
        mc.timer.timerSpeed = 1F
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (listValue.equals("Normal")) {
            if (mc.thePlayer.fallDistance >= maxFallDistance.get()) {
                mc.thePlayer.motionY -= 5.0
            }
        }else if(listValue.equals("Intave") || listValue.equals("Vulcan")){
            // If player is on the ground, slow down the timer
            if (mc.thePlayer.onGround) {
                mc.timer.timerSpeed = 0.2F
            } else {
                // If player is in the air, increase the timer speed
                mc.timer.timerSpeed = 5F
            }
        } else {
            if (mc.thePlayer.fallDistance >= maxFallDistance.get()) {
                if (donnotclickme == 0) {
                    donnotclickme = freezeValue.get().toInt()
                }
            }
            if (donnotclickme > 0){
                donnotclickme -= 1
                freeze = donnotclickme > 5
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet
        if (listValue.equals("Polar")) {
            if (freeze) {
                if (packet is C03PacketPlayer) {
                    packet.y += mc.thePlayer.posY + 0.01
                }
            }
        }
    }
    @EventTarget
    fun onMove(event: MoveEvent) {
        if (freeze) {
            event.cancelEvent()
        }
    }

    override fun onDisable() {
        super.onDisable()
        // Reset timer speed to normal when module is disabled
        mc.timer.timerSpeed = 1F
    }
}