/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.RotationUpdateEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.RotationSettings
import net.ccbluex.liquidbounce.utils.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.isRotationFaced
import net.ccbluex.liquidbounce.utils.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.toRotation
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.floatValue
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.EntityFireball
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.world.WorldSettings

object AntiFireball : Module("AntiFireball", Category.PLAYER, hideModule = false) {
    private val range by floatValue("Range", 4.5f, 3f..8f)
    private val swing by choices("Swing", arrayOf("Normal", "Packet", "None"), "Normal")

    private val options = RotationSettings(this).withoutKeepRotation()

    private val fireballTickCheck by _boolean("FireballTickCheck", true)
    private val minFireballTick by intValue("MinFireballTick", 10, 1..20) { fireballTickCheck }

    private var target: Entity? = null

    @EventTarget
    fun onRotationUpdate(event: RotationUpdateEvent) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        target = null

        for (entity in world.loadedEntityList.filterIsInstance<EntityFireball>()
            .sortedBy { player.getDistanceToBox(it.hitBox) }) {
            val nearestPoint = getNearestPointBB(player.eyes, entity.hitBox)

            val entityPrediction = entity.currPos - entity.prevPos

            val normalDistance = player.getDistanceToBox(entity.hitBox)

            val predictedDistance = player.getDistanceToBox(
                entity.hitBox.offset(
                    entityPrediction.xCoord,
                    entityPrediction.yCoord,
                    entityPrediction.zCoord
                )
            )

            // Skip if the predicted distance is (further than/same as) the normal distance or the predicted distance is out of reach
            if (predictedDistance >= normalDistance || predictedDistance > range) {
                continue
            }

            // Skip if the fireball entity tick exist is lower than minFireballTick
            if (fireballTickCheck && entity.ticksExisted <= minFireballTick) {
                continue
            }

            if (options.rotationsActive) {
                setTargetRotation(toRotation(nearestPoint, true), options = options)
            }

            target = entity
            break
        }
    }

    @EventTarget
    fun onTick(event: GameTickEvent) {
        val player = mc.thePlayer ?: return
        val entity = target ?: return

        val rotation = currentRotation ?: player.rotation

        if (!options.rotationsActive && player.getDistanceToBox(entity.hitBox) <= range
            || isRotationFaced(entity, range.toDouble(), rotation)
        ) {
            when (swing) {
                "Normal" -> mc.thePlayer.swingItem()
                "Packet" -> sendPacket(C0APacketAnimation())
            }

            sendPacket(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))

            if (mc.playerController.currentGameType != WorldSettings.GameType.SPECTATOR) {
                player.attackTargetEntityWithCurrentItem(entity)
            }

            target = null
        }
    }
}