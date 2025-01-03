/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.LiquidBounce.hud
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.utils.chat
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.int

object PacketDebugger : Module("PacketDebugger", Category.MISC, gameDetecting = false, hideModule = false) {

    private val notify by choices("Notify", arrayOf("Chat", "Notification"), "Chat")
    val packetType by choices("PacketType", arrayOf("Both", "Server", "Client", "Custom"), "Both")
    private val delay by int("Delay", 100, 0..1000)

    private val timer = MSTimer()
    val selectedPackets = mutableListOf<String>()

    @EventTarget
    fun onPacket(event: PacketEvent) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return
        }

        val packet = event.packet

        val isServerPacket = packet.javaClass.name.startsWith("net.minecraft.network.play.server")
        val isClientPacket = packet.javaClass.name.startsWith("net.minecraft.network.play.client")

        if (timer.hasTimePassed(delay)) {
            when (packetType.lowercase()) {
                "both" -> logPacket(event)
                "server" -> if (isServerPacket) logPacket(event)
                "client" -> if (isClientPacket) logPacket(event)
                "custom" -> if (selectedPackets.contains(packet.javaClass.simpleName)) logPacket(event)
            }
            timer.reset()
        }
    }

    private fun logPacket(event: PacketEvent) {
        val packet = event.packet

        val packetInfo = buildString {
            append("\n")
            append("§aPacket: §b${packet.javaClass.simpleName}\n")
            append("§aEventType: §b${event.eventType}\n")
            packet.javaClass.declaredFields.forEach { field ->
                field.isAccessible = true
                append("§a${field.name}: §b${field.get(packet)}\n")
            }
        }

        if (notify == "Chat") {
            chat(packetInfo)
        } else {
            hud.addNotification(Notification(packetInfo, 3000F))
        }
    }
}