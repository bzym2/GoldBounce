/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.RotationUtils.angleDifference
import net.ccbluex.liquidbounce.utils.RotationUtils.serverRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.toRotation
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.render.ColorUtils.stripColor
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.floatValue
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.server.S0BPacketAnimation
import net.minecraft.network.play.server.S13PacketDestroyEntities
import net.minecraft.network.play.server.S14PacketEntity
import net.minecraft.network.play.server.S20PacketEntityProperties
import net.minecraft.potion.Potion
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

object AntiBot : Module("AntiBot", Category.MISC, hideModule = false) {

    private val tab by _boolean("Tab", true)
    private val tabMode by choices("TabMode", arrayOf("Equals", "Contains"), "Contains") { tab }

    private val entityID by _boolean("EntityID", true)
    private val invalidUUID by _boolean("InvalidUUID", true)
    private val color by _boolean("Color", false)

    private val livingTime by _boolean("LivingTime", false)
    private val livingTimeTicks by intValue("LivingTimeTicks", 40, 1..200) { livingTime }

    private val capabilities by _boolean("Capabilities", true)
    private val ground by _boolean("Ground", true)
    private val air by _boolean("Air", false)
    private val invalidGround by _boolean("InvalidGround", true)
    private val invalidSpeed by _boolean("InvalidSpeed", false)
    private val swing by _boolean("Swing", false)
    private val health by _boolean("Health", false)
    private val derp by _boolean("Derp", true)
    private val wasInvisible by _boolean("WasInvisible", false)
    private val armor by _boolean("Armor", false)
    private val ping by _boolean("Ping", false)
    private val needHit by _boolean("NeedHit", false)
    private val duplicateInWorld by _boolean("DuplicateInWorld", false)
    private val duplicateInTab by _boolean("DuplicateInTab", false)
    private val duplicateProfile by _boolean("DuplicateProfile", false)
    private val properties by _boolean("Properties", false)

    private val alwaysInRadius by _boolean("AlwaysInRadius", false)
    private val alwaysRadius by floatValue("AlwaysInRadiusBlocks", 20f, 3f..30f)
    { alwaysInRadius }
    private val alwaysRadiusTick by intValue("AlwaysInRadiusTick", 50, 1..100)
    { alwaysInRadius }

    private val alwaysBehind by _boolean("AlwaysBehind", false)
    private val alwaysBehindRadius by floatValue("AlwaysBehindInRadiusBlocks", 10f, 3f..30f)
    { alwaysBehind }
    private val behindRotDiffToIgnore by floatValue("BehindRotationDiffToIgnore", 90f, 1f..180f)
    { alwaysBehind }

    private val groundList = mutableSetOf<Int>()
    private val airList = mutableSetOf<Int>()
    private val invalidGroundList = mutableMapOf<Int, Int>()
    private val invalidSpeedList = mutableSetOf<Int>()
    private val swingList = mutableSetOf<Int>()
    private val invisibleList = mutableListOf<Int>()
    private val propertiesList = mutableSetOf<Int>()
    private val hitList = mutableSetOf<Int>()
    private val notAlwaysInRadiusList = mutableSetOf<Int>()
    private val alwaysBehindList = mutableSetOf<Int>()
    private val worldPlayerNames = mutableSetOf<String>()
    private val worldDuplicateNames = mutableSetOf<String>()
    private val tabPlayerNames = mutableSetOf<String>()
    private val tabDuplicateNames = mutableSetOf<String>()
    private val entityTickMap = mutableMapOf<Int, Int>()

    val botList = mutableSetOf<UUID>()

    fun isBot(entity: EntityLivingBase): Boolean {
        // Check if entity is a player
        if (entity !is EntityPlayer)
            return false

        // Check if anti bot is enabled
        if (!handleEvents())
            return false

        // Anti Bot checks
        if (color && "§" !in entity.displayName.formattedText.replace("§r", ""))
            return true

        if (livingTime && entity.ticksExisted < livingTimeTicks)
            return true

        if (ground && entity.entityId !in groundList)
            return true

        if (air && entity.entityId !in airList)
            return true

        if (swing && entity.entityId !in swingList)
            return true

        if (health && (entity.health > 20F || entity.health < 0F))
            return true

        if (entityID && (entity.entityId >= 1000000000 || entity.entityId <= 0))
            return true

        if (derp && (entity.rotationPitch > 90F || entity.rotationPitch < -90F))
            return true

        if (wasInvisible && entity.entityId in invisibleList)
            return true

        if (properties && entity.entityId !in propertiesList)
            return true

        if (armor) {
            if (entity.inventory.armorInventory[0] == null && entity.inventory.armorInventory[1] == null &&
                entity.inventory.armorInventory[2] == null && entity.inventory.armorInventory[3] == null
            )
                return true
        }

        if (ping) {
            if (entity.getPing() == 0) return true
        }

        if (invalidUUID && mc.netHandler.getPlayerInfo(entity.uniqueID) == null) {
            return true
        }

        if (capabilities && (entity.isSpectator || entity.capabilities.isFlying || entity.capabilities.allowFlying
                    || entity.capabilities.disableDamage || entity.capabilities.isCreativeMode)
        )
            return true

        if (invalidSpeed && entity.entityId in invalidSpeedList)
            return true

        if (needHit && entity.entityId !in hitList)
            return true

        if (invalidGround && invalidGroundList.getOrDefault(entity.entityId, 0) >= 10)
            return true

        if (alwaysInRadius && entity.entityId !in notAlwaysInRadiusList)
            return true

        if (alwaysBehind && entity.entityId in alwaysBehindList)
            return true

        if (duplicateProfile) {
            return mc.netHandler.playerInfoMap.count {
                it.gameProfile.name == entity.gameProfile.name
                        && it.gameProfile.id != entity.gameProfile.id
            } == 1
        }

        if (duplicateInWorld) {
            for (player in mc.theWorld.playerEntities.filterNotNull()) {
                val playerName = player.name

                if (worldPlayerNames.contains(playerName)) {
                    worldDuplicateNames.add(playerName)
                } else {
                    worldPlayerNames.add(playerName)
                }
            }

            if (worldDuplicateNames.isNotEmpty()) {
                return mc.theWorld.playerEntities.count { it.name in worldDuplicateNames } > 1
            }
        }

        if (duplicateInTab) {
            for (networkPlayerInfo in mc.netHandler.playerInfoMap.filterNotNull()) {
                val playerName = stripColor(networkPlayerInfo.getFullName())

                if (tabPlayerNames.contains(playerName)) {
                    tabDuplicateNames.add(playerName)
                } else {
                    tabPlayerNames.add(playerName)
                }
            }

            if (tabDuplicateNames.isNotEmpty()) {
                return mc.netHandler.playerInfoMap.count { stripColor(it.getFullName()) in tabDuplicateNames } > 1
            }
        }

        if (tab) {
            val equals = tabMode == "Equals"
            val targetName = stripColor(entity.displayName.formattedText)

            val shouldReturn = mc.netHandler.playerInfoMap.any { networkPlayerInfo ->
                val networkName = stripColor(networkPlayerInfo.getFullName())
                if (equals) {
                    targetName == networkName
                } else {
                    networkName in targetName
                }
            }
            return !shouldReturn
        }

        return entity.name.isEmpty() || entity.name == mc.thePlayer.name
    }

    @EventTarget(ignoreCondition = true)
    fun onUpdate(event: UpdateEvent) {
        val world = mc.theWorld ?: return

        world.loadedEntityList.asSequence().forEach { entity ->
            if (entity !is EntityPlayer) return@forEach
            val profile = entity.gameProfile ?: return@forEach

            if (isBot(entity)) {
                if (profile.id !in botList) {
                    botList += profile.id
                }
            } else {
                if (profile.id in botList) {
                    botList -= profile.id
                }
            }
        }
    }

    // Alternative for isBot() check.
    @EventTarget(ignoreCondition = true)
    fun onPacket(event: PacketEvent) {
        if (mc.thePlayer == null || mc.theWorld == null)
            return

        val packet = event.packet

        if (packet is S14PacketEntity) {
            val entity = packet.getEntity(mc.theWorld)

            if (entity is EntityPlayer) {
                if (entity.onGround && entity.entityId !in groundList)
                    groundList += entity.entityId

                if (!entity.onGround && entity.entityId !in airList)
                    airList += entity.entityId

                if (entity.onGround) {
                    if (entity.fallDistance > 0.0 || entity.posY == entity.prevPosY || !entity.isCollidedVertically) {
                        invalidGroundList.putIfAbsent(
                            entity.entityId,
                            invalidGroundList.getOrDefault(entity.entityId, 0) + 1
                        )
                    }
                } else {
                    val currentVL = invalidGroundList.getOrDefault(entity.entityId, 0)

                    if (currentVL > 0) {
                        invalidGroundList.putIfAbsent(entity.entityId, currentVL - 1)
                    } else {
                        invalidGroundList.remove(entity.entityId)
                    }
                }

                if ((entity.isInvisible || entity.isInvisibleToPlayer(mc.thePlayer)) && entity.entityId !in invisibleList)
                    invisibleList += entity.entityId

                if (alwaysInRadius) {
                    val distance = mc.thePlayer.getDistanceToEntity(entity)
                    val currentTicks = entityTickMap.getOrDefault(entity.entityId, 0)

                    if (distance < alwaysRadius) {
                        entityTickMap[entity.entityId] = currentTicks + 1
                    } else {
                        entityTickMap[entity.entityId] = 0
                    }

                    if (entityTickMap[entity.entityId]!! >= alwaysRadiusTick) {
                        notAlwaysInRadiusList -= entity.entityId
                    } else {
                        if (entity.entityId !in notAlwaysInRadiusList) {
                            notAlwaysInRadiusList += entity.entityId
                        }
                    }
                }

                if (alwaysBehind) {
                    val distance = mc.thePlayer.getDistanceToEntity(entity)
                    val rotationToEntity = toRotation(entity.hitBox.center, false, mc.thePlayer).fixedSensitivity().yaw
                    val angleDifferenceToEntity = abs(angleDifference(rotationToEntity, serverRotation.yaw))

                    if (distance < alwaysBehindRadius && angleDifferenceToEntity > behindRotDiffToIgnore) {
                        alwaysBehindList += entity.entityId
                    } else {
                        if (entity.entityId in alwaysBehindList) {
                            alwaysBehindList -= entity.entityId
                        }
                    }
                }

                if (invalidSpeed) {
                    val deltaX = entity.posX - entity.prevPosX
                    val deltaZ = entity.posZ - entity.prevPosZ
                    val speed = sqrt(deltaX * deltaX + deltaZ * deltaZ)


                    if (speed in 0.45..0.46 && (!entity.isSprinting || !entity.isMoving ||
                                entity.getActivePotionEffect(Potion.moveSpeed) == null)
                    ) {
                        invalidSpeedList += entity.entityId
                    }
                }
            }
        }

        if (packet is S0BPacketAnimation) {
            val entity = mc.theWorld.getEntityByID(packet.entityID)

            if (entity != null && entity is EntityLivingBase && packet.animationType == 0
                && entity.entityId !in swingList
            )
                swingList += entity.entityId
        }

        if (packet is S20PacketEntityProperties) {
            propertiesList += packet.entityId
        }

        if (packet is S13PacketDestroyEntities) {
            for (entityID in packet.entityIDs) {
                // Remove [entityID] from every list upon deletion
                groundList -= entityID
                airList -= entityID
                invalidGroundList -= entityID
                swingList -= entityID
                invisibleList -= entityID
                notAlwaysInRadiusList -= entityID
                propertiesList -= entityID
            }
        }
    }

    @EventTarget(ignoreCondition = true)
    fun onAttack(e: AttackEvent) {
        val entity = e.targetEntity

        if (entity != null && entity is EntityLivingBase && entity.entityId !in hitList)
            hitList += entity.entityId
    }

    @EventTarget(ignoreCondition = true)
    fun onWorld(event: WorldEvent) {
        clearAll()
    }

    private fun clearAll() {
        hitList.clear()
        swingList.clear()
        groundList.clear()
        invalidGroundList.clear()
        invalidSpeedList.clear()
        invisibleList.clear()
        notAlwaysInRadiusList.clear()
        worldPlayerNames.clear()
        worldDuplicateNames.clear()
        tabPlayerNames.clear()
        tabDuplicateNames.clear()
        alwaysBehindList.clear()
        entityTickMap.clear()
        botList.clear()
    }

}