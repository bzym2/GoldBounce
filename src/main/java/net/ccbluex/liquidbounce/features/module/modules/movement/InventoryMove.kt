/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.utils.JumpUtils
import net.ccbluex.liquidbounce.utils.PacketUtils
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.canClickInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.hasScheduledInLastLoop
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenContainer
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenInventory
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.floatValue
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.settings.GameSettings
import net.minecraft.client.settings.KeyBinding
import net.minecraft.network.play.client.C0DPacketCloseWindow
import net.minecraft.network.play.client.C0EPacketClickWindow
import org.lwjgl.input.Mouse

object InventoryMove : Module("InventoryMove", Category.MOVEMENT, gameDetecting = false, hideModule = false) {

    private val notInChests by _boolean("NotInChests", false)
    val aacAdditionPro by _boolean("AACAdditionPro", false)
    private val intave by _boolean("Intave", false)
    private val saveC0E by _boolean("SaveC0E",true)
    private val ncpJump by _boolean("NCPJump", false)
    private val noSprintWhenClosed by _boolean("NoSprintWhenClosed",false) { saveC0E }
    private val isIntave = (mc.currentScreen is GuiInventory || mc.currentScreen is GuiChest) && intave
    private val clickWindowList = mutableListOf<C0EPacketClickWindow>()

    private val noMove by InventoryManager.noMoveValue
    private val noMoveAir by InventoryManager.noMoveAirValue
    private val noMoveGround by InventoryManager.noMoveGroundValue
    private val undetectable by InventoryManager.undetectableValue

    // If player violates nomove check and inventory is open, close inventory and reopen it when still
    private val silentlyCloseAndReopen by _boolean("SilentlyCloseAndReopen", false)
    { noMove && (noMoveAir || noMoveGround) }

    // Reopen closed inventory just before a click (could flag for clicking too fast after opening inventory)
    private val reopenOnClick by _boolean("ReopenOnClick", false)
    { silentlyCloseAndReopen && noMove && (noMoveAir || noMoveGround) }

    private val inventoryMotion by floatValue("InventoryMotion", 1F, 0F..2F)

    private val affectedBindings = arrayOf(
        mc.gameSettings.keyBindForward,
        mc.gameSettings.keyBindBack,
        mc.gameSettings.keyBindRight,
        mc.gameSettings.keyBindLeft,
        mc.gameSettings.keyBindJump,
        mc.gameSettings.keyBindSprint
    )

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val player = mc.thePlayer ?: return
        val screen = mc.currentScreen

        // Don't make player move when chat or ESC menu are open
        if (screen is GuiChat || screen is GuiIngameMenu)
            return

        if (undetectable && (screen != null && screen !is GuiHudDesigner && screen !is ClickGui))
            return

        if (notInChests && screen is GuiChest)
            return

        if (screen is GuiInventory || screen is GuiChest) {
            player.motionX *= inventoryMotion
            player.motionZ *= inventoryMotion
        }

        if (silentlyCloseAndReopen && screen is GuiInventory) {
            if (canClickInventory(closeWhenViolating = true) && !reopenOnClick)
                serverOpenInventory = true
        }

        for (affectedBinding in affectedBindings)
            affectedBinding.pressed =
                isButtonPressed(affectedBinding) || (affectedBinding == mc.gameSettings.keyBindSprint && handleEvents() && Sprint.mode == "Legit" && (!Sprint.onlyOnSprintPress || mc.thePlayer.isSprinting))
    }

    @EventTarget
    fun onStrafe(event: StrafeEvent) {
        if (isIntave) {
            mc.gameSettings.keyBindSneak.pressed = true
        }
    }
    @EventTarget
    fun onPacket(event:PacketEvent){
        val packet = event.packet
        val player = mc.thePlayer ?: return
        if (ncpJump && player.isMoving && packet is C0EPacketClickWindow) {
            JumpUtils().jump()
            event.cancelEvent()
            mc.addScheduledTask {
                PacketUtils.sendPacket(packet, false)
            }
        }
        if (!saveC0E)
            return

        if (noSprintWhenClosed) {
            if (clickWindowList.isNotEmpty() && !(serverOpenInventory || serverOpenContainer))
                mc.thePlayer.isSprinting = false

            if (packet is C0DPacketCloseWindow) {
                event.cancelEvent()
                player.isSprinting = false
                if (!player.serverSprintState)
                    PacketUtils.sendPacket(C0DPacketCloseWindow(),false)
            }
        }

        if (serverOpenInventory || serverOpenContainer) {
            if (packet is C0EPacketClickWindow) {
                clickWindowList.add(packet)
                event.cancelEvent()
            }
        } else if (clickWindowList.isNotEmpty()) {
            clickWindowList.forEach {
                PacketUtils.sendPacket(it,false)
            }
            clickWindowList.clear()
        }
    }
    @EventTarget
    fun onJump(event: JumpEvent) {
        if (isIntave) event.cancelEvent()
    }

    @EventTarget
    fun onClick(event: ClickWindowEvent) {
        if (!canClickInventory()) event.cancelEvent()
        else if (reopenOnClick) {
            hasScheduledInLastLoop = false
            serverOpenInventory = true
        }
    }

    override fun onDisable() {
        for (affectedBinding in affectedBindings)
            affectedBinding.pressed = isButtonPressed(affectedBinding)
    }

    private fun isButtonPressed(keyBinding: KeyBinding): Boolean {
        return if (keyBinding.keyCode < 0) {
            Mouse.isButtonDown(keyBinding.keyCode + 100)
        } else {
            GameSettings.isKeyDown(keyBinding)
        }
    }

    override val tag
        get() = when {
            aacAdditionPro -> "AACAdditionPro"
            inventoryMotion != 1F -> inventoryMotion.toString()
            else -> null
        }
}
