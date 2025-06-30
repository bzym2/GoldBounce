/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.attackDamage
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.item.ItemSword
import net.minecraft.item.ItemTool
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK

object AutoWeapon : Module("AutoWeapon", Category.COMBAT, subjective = true, hideModule = false) {

    private val onlySword by _boolean("OnlySword", false)

    private val spoof by _boolean("SpoofItem", false)
    private val spoofTicks by intValue("SpoofTicks", 10, 1..20) { spoof }

    private var attackEnemy = false

    @EventTarget
    fun onAttack(event: AttackEvent) {
        attackEnemy = true
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return

        if (event.packet is C02PacketUseEntity && event.packet.action == ATTACK && attackEnemy) {
            attackEnemy = false

            // Find the best weapon in hotbar (#Kotlin Style)
            val (slot, _) = (0..8)
                .map { it to mc.thePlayer.inventory.getStackInSlot(it) }
                .filter {
                    it.second != null && ((onlySword && it.second.item is ItemSword)
                            || (!onlySword && (it.second.item is ItemSword || it.second.item is ItemTool)))
                }
                .maxByOrNull { it.second.attackDamage } ?: return

            if (slot == mc.thePlayer.inventory.currentItem) // If in hand no need to swap
                return

            // Switch to best weapon
            SilentHotbar.selectSlotSilently(this, slot, spoofTicks, true, !spoof, spoof)

            if (!spoof) {
                player.inventory.currentItem = slot
                SilentHotbar.resetSlot(this)
            }

            // Resend attack packet
            sendPacket(event.packet)
            event.cancelEvent()
        }
    }
}