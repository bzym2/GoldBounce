package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.hud.HUD.addNotification
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.init.Items
import net.minecraft.item.ItemAppleGold
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.S09PacketHeldItemChange
import net.minecraft.potion.Potion
import net.minecraft.util.MathHelper
import java.util.*

object Gapple : Module("Gapple", Category.PLAYER) {
    private val modeValue = ListValue("Mode", arrayOf("Auto", "LegitAuto", "Legit", "Head"), "Auto")
    private val percent = FloatValue("HealthPercent", 75.0f, 1.0f..100.0f) { modeValue.get() != "Grim" }
    private val min = IntegerValue("MinDelay", 75, 1..5000) { modeValue.get() != "Grim" }
    private val max = IntegerValue("MaxDelay", 125, 1..5000) { modeValue.get() != "Grim" }
    private val regenSec = FloatValue("MinRegenSec", 4.6f, 0.0f..10.0f) { modeValue.get() != "Grim" }
    private val groundCheck = BoolValue("OnlyOnGround", false) { modeValue.get() != "Grim" }
    private val waitRegen = BoolValue("WaitRegen", true) { modeValue.get() != "Grim" }
    private val invCheck = BoolValue("InvCheck", false) { modeValue.get() != "Grim" }
    private val absorpCheck = BoolValue("NoAbsorption", true) { modeValue.get() != "Grim" }
    private val AlertValue = BoolValue("Notify", false) { modeValue.get() != "Grim" }
    val timer = MSTimer()
    var eating = -1
    var delay = 0
    var isDisable = false
    var tryHeal = false
    var prevSlot = -1
    var switchBack = false
    var i: Int = 0
    private var needSkip = false
    var eating2: Boolean = false
    private val packets = LinkedList<Packet<*>?>()
    override fun onEnable() {
        needSkip = false
        eating2 = false
        i = 0
        packets.clear()
        eating = -1
        prevSlot = -1
        switchBack = false
        timer.reset()
        isDisable = false
        tryHeal = false
        delay = MathHelper.getRandomIntegerInRange(Random(), min.get(), max.get())
    }

    override fun onDisable() {
        eating2 = false
        release()
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        isDisable = true
        tryHeal = false
    }


    @EventTarget
    fun onPacket(event: PacketEvent) {
            val packet = event.packet
            if (eating != -1 && packet is C03PacketPlayer) {
                eating++
            } else if (packet is S09PacketHeldItemChange || packet is C09PacketHeldItemChange) {
                eating = -1
            }
    }

    private fun run() {
        while (!packets.isEmpty()) {
            val packet = packets.poll()

            if (packet is C01PacketChatMessage) continue

            packet?.let { sendPacket(it) }
        }
    }

    private fun release() {
        if (mc.netHandler == null) return

        while (!packets.isEmpty()) {
            val packet = packets.poll()

            if (packet is C01PacketChatMessage || packet is C08PacketPlayerBlockPlacement || packet is C07PacketPlayerDigging) continue

            packet?.let { sendPacket(it) }
        }

        i = 0
    }

    private fun getAppleGold(): Int {
        for (i in 0..8) {
            if (!mc.thePlayer.inventoryContainer.getSlot(i + 36)
                    .hasStack || mc.thePlayer.inventoryContainer.getSlot(i + 36).stack
                    .item !is ItemAppleGold
            ) continue
            return i
        }
        return -1
    }

    @EventTarget
    fun onMotion(event: MotionEvent) {
        if (modeValue.get() != "Grim") return
        if (event.eventState.stateName == "PRE") {
            if (mc.thePlayer.ticksExisted % 5 === 0) {
                while (!packets.isEmpty()) {
                    val packet = packets.poll()

                    if (packet is C03PacketPlayer) {
                        i--
                    }

                    if (packet is C01PacketChatMessage) break

                    packet?.let { sendPacket(it) }
                }
            }
        } else if (event.eventState.stateName == "POST") {
            packets.add(C01PacketChatMessage("post"))
        }
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (tryHeal) {
            when (modeValue.get().lowercase()) {
                "auto" -> {
                    val gappleInHotbar = InventoryUtils.findItem(36, 45, Items.golden_apple)
                    if (gappleInHotbar != -1) {
                        gappleInHotbar?.let { mc.netHandler.addToSendQueue(C09PacketHeldItemChange(it - 36)) }
                        mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
                        repeat(35) {
                            mc.netHandler.addToSendQueue(C03PacketPlayer(mc.thePlayer.onGround))
                        }
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                        if (AlertValue.get()) addNotification(Notification("好吃。", 3000F, "金苹果口服液。"))
                        tryHeal = false
                        timer.reset()
                        delay = MathHelper.getRandomIntegerInRange(Random(), min.get(), max.get())
                    } else {
                        tryHeal = false
                    }
                }

                "legitauto" -> {
                    if (eating == -1) {
                        val gappleInHotbar = InventoryUtils.findItem(36, 45, Items.golden_apple)
                        if (gappleInHotbar == -1) {
                            tryHeal = false
                            return
                        }
                        gappleInHotbar?.let { mc.netHandler.addToSendQueue(C09PacketHeldItemChange(it - 36)) }
                        mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
                        eating = 0
                    } else if (eating > 35) {
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                        timer.reset()
                        tryHeal = false
                        delay = MathHelper.getRandomIntegerInRange(Random(), min.get(), max.get())
                    }
                }

                "legit" -> {
                    if (eating == -1) {
                        val gappleInHotbar = InventoryUtils.findItem(36, 45, Items.golden_apple)
                        if (gappleInHotbar == -1) {
                            tryHeal = false
                            return
                        }
                        if (prevSlot == -1)
                            prevSlot = mc.thePlayer.inventory.currentItem

                        gappleInHotbar?.let { mc.thePlayer.inventory.currentItem = it - 36 }
                        mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
                        eating = 0
                    } else if (eating > 35) {
                        timer.reset()
                        tryHeal = false
                        delay = MathHelper.getRandomIntegerInRange(Random(), min.get(), max.get())
                    }
                }

                "head" -> {
                    val headInHotbar = InventoryUtils.findItem(36, 45, Items.skull)
                    if (headInHotbar != -1) {
                        headInHotbar?.let { mc.netHandler.addToSendQueue(C09PacketHeldItemChange(it - 36)) }
                        mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                        timer.reset()
                        tryHeal = false
                        delay = MathHelper.getRandomIntegerInRange(Random(), min.get(), max.get())
                    } else {
                        tryHeal = false
                    }
                }
            }
        }
        if (mc.thePlayer.ticksExisted <= 10 && isDisable) {
            isDisable = false
        }
        val absorp = MathHelper.ceiling_double_int(mc.thePlayer.absorptionAmount.toDouble())


        if (!tryHeal && prevSlot != -1) {
            if (!switchBack) {
                switchBack = true
                return
            }
            mc.thePlayer.inventory.currentItem = prevSlot
            prevSlot = -1
            switchBack = false
        }

        if ((groundCheck.get() && !mc.thePlayer.onGround) || (invCheck.get() && mc.currentScreen is GuiContainer) || (absorp > 0 && absorpCheck.get()))
            return
        if (waitRegen.get() && mc.thePlayer.isPotionActive(Potion.regeneration) && mc.thePlayer.getActivePotionEffect(
                Potion.regeneration
            ).duration > regenSec.get() * 20.0f
        )
            return
        if (!isDisable && (mc.thePlayer.health <= (percent.get() / 100.0f) * mc.thePlayer.maxHealth) && timer.hasTimePassed(
                delay.toLong()
            )
        ) {
            if (tryHeal)
                return
            tryHeal = true
        }
    }

    override val tag: String
        get() = modeValue.get()

}