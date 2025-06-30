/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.WorldEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot.isBot
import net.ccbluex.liquidbounce.utils.chat
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.block.BlockTNT
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemFireball
import net.minecraft.item.ItemTool
import net.minecraft.network.play.server.S38PacketPlayerListItem
import net.minecraft.network.play.server.S38PacketPlayerListItem.Action.ADD_PLAYER
import net.minecraft.network.play.server.S38PacketPlayerListItem.Action.REMOVE_PLAYER
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object Notifier : Module("Notifier", Category.MISC, hideModule = false) {

    private val onPlayerJoin by _boolean("Join", true)
    private val onPlayerLeft by _boolean("Left", true)
    private val onPlayerDeath by _boolean("Death", true)
    private val onHeldExplosive by _boolean("HeldExplosive", true)
    private val onPlayerTool by _boolean("HeldTools", false)
    
    private val warnDelay by intValue("WarnDelay", 5000, 1000..50000)
    { onPlayerDeath || onHeldExplosive || onPlayerTool }

    private val recentlyWarned = ConcurrentHashMap<String, Long>()

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        val currentTime = System.currentTimeMillis()
        for (entity in world.playerEntities) {
            if (entity.gameProfile.id == player.uniqueID || isBot(entity)) continue
            val entityDistance = player.getDistanceToEntity(entity).roundToInt()

            val lastNotified = recentlyWarned[entity.uniqueID.toString()] ?: 0L
            if (currentTime - lastNotified < warnDelay) continue

            val heldItem = entity.heldItem?.item ?: continue

            when {
                onPlayerDeath && (entity.isDead || !entity.isEntityAlive) -> {
                    chat("§7${entity.name} has §cdied §a(${entityDistance}m)")
                    recentlyWarned[entity.uniqueID.toString()] = currentTime
                }

                onHeldExplosive && (heldItem is ItemFireball || heldItem is ItemBlock && heldItem.block is BlockTNT) -> {
                    chat("§7${entity.name} is holding a §eFireball §a(${entityDistance}m)")
                    recentlyWarned[entity.uniqueID.toString()] = currentTime
                }

                onPlayerTool && heldItem is ItemTool -> {
                    chat("§7${entity.name} is holding a §b${entity.heldItem?.displayName} §a(${entityDistance}m)")
                    recentlyWarned[entity.uniqueID.toString()] = currentTime
                }
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (player.ticksExisted < 50) return

        when (val packet = event.packet) {
            is S38PacketPlayerListItem -> {
                if (onPlayerJoin && packet.action == ADD_PLAYER) {
                    for (playerData in packet.entries) {
                        val players = playerData.profile ?: continue
                        if (players.id == player.uniqueID || players.id in AntiBot.botList) continue

                        chat("§7${players.name} §ajoined the game.")
                    }
                }
                if (onPlayerLeft && packet.action == REMOVE_PLAYER) {
                    for (playerData in packet.entries) {
                        val players = world.getPlayerEntityByUUID(playerData?.profile?.id)?.gameProfile ?: continue
                        if (players.id == player.uniqueID || players.id in AntiBot.botList) continue

                        chat("§7${players.name} §cleft the game.")
                    }
                }
            }
        }
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        recentlyWarned.clear()
    }
}
