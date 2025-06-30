/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.MovementUtils
import net.ccbluex.liquidbounce.utils.block.BlockUtils.collideBlock
import net.ccbluex.liquidbounce.utils.block.BlockUtils.getBlock
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.floatValue
import net.minecraft.block.BlockLiquid
import net.minecraft.block.material.Material
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import org.lwjgl.input.Keyboard

object LiquidWalk : Module("LiquidWalk", Category.MOVEMENT, Keyboard.KEY_J) {

    val mode by choices("Mode", arrayOf("Vanilla", "NCP", "AAC", "AAC3.3.11", "AACFly", "Spartan", "Dolphin", "BlocksMC"), "NCP")
    private val aacFly by floatValue("AACFlyMotion", 0.5f, 0.1f..1f) { mode == "AACFly" }

    private val noJump by _boolean("NoJump", false)
    var waterTick = 0;
    private var nextTick = false

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val thePlayer = mc.thePlayer

        if (thePlayer == null || thePlayer.isSneaking) return

        when (mode.lowercase()) {
            "ncp", "vanilla" -> if (collideBlock(thePlayer.entityBoundingBox) { it is BlockLiquid } && thePlayer.isInsideOfMaterial(
                    Material.air
                ) && !thePlayer.isSneaking) thePlayer.motionY = 0.08

            "aac" -> {
                val blockPos = thePlayer.position.down()
                if (!thePlayer.onGround && getBlock(blockPos) == Blocks.water || thePlayer.isInWater) {
                    if (!thePlayer.isSprinting) {
                        thePlayer.motionX *= 0.99999
                        thePlayer.motionY *= 0.0
                        thePlayer.motionZ *= 0.99999
                        if (thePlayer.isCollidedHorizontally) thePlayer.motionY =
                            ((thePlayer.posY - (thePlayer.posY - 1).toInt()).toInt() / 8f).toDouble()
                    } else {
                        thePlayer.motionX *= 0.99999
                        thePlayer.motionY *= 0.0
                        thePlayer.motionZ *= 0.99999
                        if (thePlayer.isCollidedHorizontally) thePlayer.motionY =
                            ((thePlayer.posY - (thePlayer.posY - 1).toInt()).toInt() / 8f).toDouble()
                    }
                    if (thePlayer.fallDistance >= 4) thePlayer.motionY =
                        -0.004 else if (thePlayer.isInWater) thePlayer.motionY = 0.09
                }
                if (thePlayer.hurtTime != 0) thePlayer.onGround = false
            }

            "spartan" -> if (thePlayer.isInWater) {
                if (thePlayer.isCollidedHorizontally) {
                    thePlayer.motionY += 0.15
                    return
                }
                val block = getBlock(BlockPos(thePlayer).up())
                val blockUp = getBlock(BlockPos(thePlayer.posX, thePlayer.posY + 1.1, thePlayer.posZ))

                if (blockUp is BlockLiquid) {
                    thePlayer.motionY = 0.1
                } else if (block is BlockLiquid) {
                    thePlayer.motionY = 0.0
                }

                thePlayer.onGround = true
                thePlayer.motionX *= 1.085
                thePlayer.motionZ *= 1.085
            }

            "aac3.3.11" -> if (thePlayer.isInWater) {
                thePlayer.motionX *= 1.17
                thePlayer.motionZ *= 1.17
                if (thePlayer.isCollidedHorizontally)
                    thePlayer.motionY = 0.24
                else if (getBlock(BlockPos(thePlayer).up()) != Blocks.air)
                    thePlayer.motionY += 0.04
            }

            "dolphin" -> if (thePlayer.isInWater) thePlayer.motionY += 0.03999999910593033
        }
    }

    @EventTarget
    fun onMove(event: MoveEvent) {
        if ("aacfly" == mode.lowercase() && mc.thePlayer.isInWater) {
            event.y = aacFly.toDouble()
            mc.thePlayer.motionY = aacFly.toDouble()
        }
    }

    @EventTarget
    fun onBlockBB(event: BlockBBEvent) {
        if (mc.thePlayer == null)
            return

        if (event.block is BlockLiquid && !collideBlock(mc.thePlayer.entityBoundingBox) { it is BlockLiquid } && !mc.thePlayer.isSneaking) {
            when (mode.lowercase()) {
                "ncp", "vanilla" -> event.boundingBox = AxisAlignedBB.fromBounds(
                    event.x.toDouble(),
                    event.y.toDouble(),
                    event.z.toDouble(),
                    event.x + 1.toDouble(),
                    event.y + 1.toDouble(),
                    event.z + 1.toDouble()
                )
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val thePlayer = mc.thePlayer

        if (thePlayer == null || mode != "NCP")
            return

        if (event.packet is C03PacketPlayer) {
            val packetPlayer = event.packet

            if (collideBlock(
                    AxisAlignedBB.fromBounds(
                        thePlayer.entityBoundingBox.maxX,
                        thePlayer.entityBoundingBox.maxY,
                        thePlayer.entityBoundingBox.maxZ,
                        thePlayer.entityBoundingBox.minX,
                        thePlayer.entityBoundingBox.minY - 0.01,
                        thePlayer.entityBoundingBox.minZ
                    )
                ) { it is BlockLiquid }
            ) {
                nextTick = !nextTick
                if (nextTick) packetPlayer.y -= 0.001
            }
        }
    }

    @EventTarget
    fun onJump(event: JumpEvent) {
        val thePlayer = mc.thePlayer ?: return

        val block = getBlock(BlockPos(thePlayer.posX, thePlayer.posY - 0.01, thePlayer.posZ))

        if (noJump && block is BlockLiquid)
            event.cancelEvent()
    }
    @EventTarget
    fun onMotion(event: MotionEvent){
        if (event.eventState.stateName != "PRE") return
        if (mc.gameSettings.keyBindJump.isKeyDown()) return
        if (mode == "BlocksMC"){
            if (mc.thePlayer.isInWater()) {
                waterTick++
            } else {
                waterTick = 0
            }
            if (mc.theWorld.getBlockState(BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ))
                    .getBlock() is BlockLiquid
            ) {
                if (waterTick >= 1) {
                    mc.thePlayer.onGround = true
                    mc.thePlayer.motionY = 0.1F.toDouble()
                    MovementUtils.strafe(0.1f)
                }
                if (waterTick == 0) {
                    mc.thePlayer.motionY = -0.09800000190734863
                }
            }
        }
    }
    override val tag
        get() = mode
}