/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.SilentHotbar
import net.ccbluex.liquidbounce.utils.chat
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.text
import net.minecraft.init.Items
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.world.WorldSettings
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

object KeyPearl : Module("KeyPearl", Category.PLAYER, subjective = true, gameDetecting = false, hideModule = false) {

    private val delayedSlotSwitch by _boolean("DelayedSlotSwitch", true)
    private val mouse by _boolean("Mouse", false)
    private val mouseButtonValue = choices(
        "MouseButton",
        arrayOf("Left", "Right", "Middle", "MouseButton4", "MouseButton5"), "Middle"
    ) { mouse }

    private val keyName by text("KeyName", "X") { !mouse }

    private val noEnderPearlsMessage by _boolean("NoEnderPearlsMessage", true)

    private var wasMouseDown = false
    private var wasKeyDown = false
    private var hasThrown = false

    private fun throwEnderPearl() {
        val pearlInHotbar = InventoryUtils.findItem(36, 44, Items.ender_pearl)

        if (pearlInHotbar == null) {
            if (noEnderPearlsMessage) {
                chat("§6§lWarning: §aThere are no ender pearls in your hotbar.")
            }
            return
        }

        // don't wait before and after throwing if the player is already holding an ender pearl
        if (!delayedSlotSwitch || SilentHotbar.currentSlot == pearlInHotbar) {
            SilentHotbar.selectSlotSilently(this,
                pearlInHotbar,
                immediate = true,
                render = false,
                resetManually = true
            )
            sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
            SilentHotbar.resetSlot(this)
            return
        }

        SilentHotbar.selectSlotSilently(this,
            pearlInHotbar,
            immediate = true,
            render = false,
            resetManually = true
        )
        sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
        hasThrown = true
    }

    @EventTarget
    fun onTick(event: GameTickEvent) {
        if (hasThrown) {
            SilentHotbar.resetSlot(this)
            hasThrown = false
        }

        if (mc.currentScreen != null || mc.playerController.currentGameType == WorldSettings.GameType.SPECTATOR
            || mc.playerController.currentGameType == WorldSettings.GameType.CREATIVE
        ) return

        val isMouseDown = Mouse.isButtonDown(mouseButtonValue.values.indexOf(mouseButtonValue.get()))
        val isKeyDown = Keyboard.isKeyDown(Keyboard.getKeyIndex(keyName.uppercase()))

        if (mouse && !wasMouseDown && isMouseDown) {
            throwEnderPearl()
        } else if (!mouse && !wasKeyDown && isKeyDown) {
            throwEnderPearl()
        }

        wasMouseDown = isMouseDown
        wasKeyDown = isKeyDown
    }

    override fun onEnable() {
        hasThrown = false
    }
}