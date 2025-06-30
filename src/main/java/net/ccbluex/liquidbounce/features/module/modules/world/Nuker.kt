/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.RotationSettings
import net.ccbluex.liquidbounce.utils.RotationUtils.faceBlock
import net.ccbluex.liquidbounce.utils.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.block.BlockUtils.getBlock
import net.ccbluex.liquidbounce.utils.block.BlockUtils.getBlockName
import net.ccbluex.liquidbounce.utils.block.BlockUtils.getCenterDistance
import net.ccbluex.liquidbounce.utils.block.BlockUtils.searchBlocks
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.render.RenderUtils.disableGlCap
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawBlockBox
import net.ccbluex.liquidbounce.utils.render.RenderUtils.enableGlCap
import net.ccbluex.liquidbounce.utils.render.RenderUtils.resetCaps
import net.ccbluex.liquidbounce.utils.timing.TickTimer
import net.ccbluex.liquidbounce.value.*
import net.minecraft.block.Block
import net.minecraft.block.BlockLiquid
import net.minecraft.init.Blocks.air
import net.minecraft.init.Blocks.bedrock
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.roundToInt

object Nuker : Module("Nuker", Category.WORLD, gameDetecting = false, hideModule = false) {

    /**
     * OPTIONS
     */

    private val allBlocks by _boolean("AllBlocks", true)
    private val blocks by block("Block", 1) { !allBlocks }

    private val radius by floatValue("Radius", 5.2F, 1F..6F)
    private val throughWalls by _boolean("ThroughWalls", false)
    private val priority by choices("Priority", arrayOf("Distance", "Hardness", "LightOpacity"), "Distance")

    private val options = RotationSettings(this).apply {
        immediate = true

        resetTicksValue.hideWithState()
        withoutKeepRotation()
    }

    private val layer by _boolean("Layer", false)
    private val hitDelay by intValue("HitDelay", 4, 0..20)
    private val nuke by intValue("Nuke", 1, 1..20)
    private val nukeDelay by intValue("NukeDelay", 1, 1..20)

    private val blockProgress by _boolean("BlockProgress", true)

    private val scale by floatValue("Scale", 2F, 1F..6F) { blockProgress }
    private val font by font("Font", Fonts.font40) { blockProgress }
    private val fontShadow by _boolean("Shadow", true) { blockProgress }

    private val colorRed by intValue("R", 200, 0..255) { blockProgress }
    private val colorGreen by intValue("G", 100, 0..255) { blockProgress }
    private val colorBlue by intValue("B", 0, 0..255) { blockProgress }

    /**
     * VALUES
     */

    private val attackedBlocks = arrayListOf<BlockPos>()
    private var currentBlock: BlockPos? = null
    private var blockHitDelay = 0

    private val nukeTimer = TickTimer()
    private var nukedCount = 0

    var currentDamage = 0F

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        // Block hit delay
        if (blockHitDelay > 0 && !handleEvents()) {
            blockHitDelay--
            return
        }

        // Reset bps
        nukeTimer.update()
        if (nukeTimer.hasTimePassed(nukeDelay)) {
            nukedCount = 0
            nukeTimer.reset()
        }

        // Clear blocks
        attackedBlocks.clear()

        val thePlayer = mc.thePlayer

        if (!mc.playerController.isInCreativeMode) {
            // Default nuker

            val eyesPos = thePlayer.eyes
            val validBlocks = searchBlocks(radius.roundToInt() + 1, null).filter { (pos, block) ->
                if (getCenterDistance(pos) <= radius && validBlock(block)) {
                    if (!allBlocks && Block.getIdFromBlock(block) != blocks) {
                        return@filter false
                    }

                    if (layer && pos.y < thePlayer.posY) { // Layer: Break all blocks above you
                        return@filter false
                    }

                    if (!throughWalls) { // ThroughWalls: Just break blocks in your sight
                        // Raytrace player eyes to block position (through walls check)
                        val blockVec = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                        val rayTrace = mc.theWorld.rayTraceBlocks(
                            eyesPos, blockVec,
                            false, true, false
                        )

                        // Check if block is visible
                        rayTrace != null && rayTrace.blockPos == pos
                    } else true // Done
                } else false // Bad block
            }.toMutableMap()

            while (nukedCount < nuke) {
                val (blockPos, block) = when (priority) {
                    "Distance" -> validBlocks.minByOrNull { (pos) ->
                        val distance = getCenterDistance(pos)
                        val safePos = BlockPos(thePlayer).down()

                        if (pos.x == safePos.x && safePos.y <= pos.y && pos.z == safePos.z)
                            Double.MAX_VALUE - distance // Last block
                        else
                            distance
                    }

                    "Hardness" -> validBlocks.maxByOrNull { (pos, block) ->
                        val hardness = block.getPlayerRelativeBlockHardness(thePlayer, mc.theWorld, pos).toDouble()

                        val safePos = BlockPos(thePlayer).down()
                        if (pos.x == safePos.x && safePos.y <= pos.y && pos.z == safePos.z)
                            Double.MIN_VALUE + hardness // Last block
                        else
                            hardness
                    }

                    "LightOpacity" -> validBlocks.maxByOrNull { (pos, block) ->
                        val opacity = block.getLightOpacity(mc.theWorld, pos).toDouble()

                        val safePos = BlockPos(thePlayer).down()
                        if (pos.x == safePos.x && safePos.y <= pos.y && pos.z == safePos.z)
                            Double.MIN_VALUE + opacity // Last block
                        else
                            opacity
                    }

                    else -> return // what? why?
                } ?: return // well I guess there is no block to break :(

                // Reset current damage in case of block switch
                if (blockPos != currentBlock)
                    currentDamage = 0F

                // Change head rotations to next block
                if (options.rotationsActive) {
                    val rotation = faceBlock(blockPos) ?: return // In case of a mistake. Prevent flag.

                    setTargetRotation(rotation.rotation, options = options)
                }

                // Set next target block
                currentBlock = blockPos
                attackedBlocks += blockPos

                EventManager.callEvent(ClickBlockEvent(blockPos, EnumFacing.DOWN))

                // Start block breaking
                if (currentDamage == 0F) {
                    sendPacket(C07PacketPlayerDigging(START_DESTROY_BLOCK, blockPos, EnumFacing.DOWN))

                    // End block break if able to break instant
                    if (block.getPlayerRelativeBlockHardness(thePlayer, mc.theWorld, blockPos) >= 1F) {
                        currentDamage = 0F
                        thePlayer.swingItem()
                        mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.DOWN)
                        blockHitDelay = hitDelay
                        validBlocks -= blockPos
                        nukedCount++
                        continue // Next break
                    }
                }

                // Break block
                thePlayer.swingItem()
                currentDamage += block.getPlayerRelativeBlockHardness(thePlayer, mc.theWorld, blockPos)
                mc.theWorld.sendBlockBreakProgress(thePlayer.entityId, blockPos, (currentDamage * 10F).toInt() - 1)

                // End of breaking block
                if (currentDamage >= 1F) {
                    sendPacket(C07PacketPlayerDigging(STOP_DESTROY_BLOCK, blockPos, EnumFacing.DOWN))
                    mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.DOWN)
                    blockHitDelay = hitDelay
                    currentDamage = 0F
                }
                return // Break out
            }
        } else {
            // Fast creative mode nuker (CreativeStorm option)

            // Unable to break with swords in creative mode
            if (thePlayer.heldItem?.item is ItemSword)
                return

            // Search for new blocks to break
            searchBlocks(radius.roundToInt() + 1, null)
                .filter { (pos, block) ->
                    if (getCenterDistance(pos) <= radius && validBlock(block)) {
                        if (layer && pos.y < thePlayer.posY) { // Layer: Break all blocks above you
                            return@filter false
                        }

                        if (!throughWalls) { // ThroughWalls: Just break blocks in your sight
                            // Raytrace player eyes to block position (through walls check)
                            val eyesPos = thePlayer.eyes
                            val blockVec = Vec3(thePlayer.position)
                            val rayTrace = mc.theWorld.rayTraceBlocks(
                                eyesPos, blockVec,
                                false, true, false
                            )

                            // Check if block is visible
                            rayTrace != null && rayTrace.blockPos == pos
                        } else true // Done
                    } else false // Bad block
                }
                .forEach { (pos, _) ->
                    // Instant break block
                    sendPacket(C07PacketPlayerDigging(START_DESTROY_BLOCK, pos, EnumFacing.DOWN))
                    thePlayer.swingItem()
                    sendPacket(C07PacketPlayerDigging(STOP_DESTROY_BLOCK, pos, EnumFacing.DOWN))
                    attackedBlocks += pos
                }
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        val player = mc.thePlayer ?: return
        val renderManager = mc.renderManager

        for (pos in attackedBlocks) {
            if (blockProgress) {
                if (getBlockName(blocks) == "Air") return

                val progress = (currentDamage * 100).coerceIn(0f, 100f).toInt()
                val progressText = "%d%%".format(progress)

                glPushAttrib(GL_ENABLE_BIT)
                glPushMatrix()

                val (x, y, z) = pos.getVec() - renderManager.renderPos

                // Translate to block position
                glTranslated(x, y, z)

                glRotatef(-renderManager.playerViewY, 0F, 1F, 0F)
                glRotatef(renderManager.playerViewX, 1F, 0F, 0F)

                disableGlCap(GL_LIGHTING, GL_DEPTH_TEST)
                enableGlCap(GL_BLEND)
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

                val fontRenderer = font
                val color = ((colorRed and 0xFF) shl 16) or ((colorGreen and 0xFF) shl 8) or (colorBlue and 0xFF)

                // Scale
                val scale = ((player.getDistanceSq(pos) / 8F).coerceAtLeast(1.5) / 150F) * scale
                glScaled(-scale, -scale, scale)

                // Draw text
                val width = fontRenderer.getStringWidth(progressText) * 0.5f
                fontRenderer.drawString(
                    progressText, -width, if (fontRenderer == Fonts.minecraftFont) 1F else 1.5F, color, fontShadow
                )

                resetCaps()
                glPopMatrix()
                glPopAttrib()
            }

            // Just draw all blocks
            drawBlockBox(pos, Color.RED, true)
        }

        // Safe block
        if (!layer) {
            val safePos = BlockPos(player).down()
            val safeBlock = getBlock(safePos)
            if (safeBlock != null && validBlock(safeBlock))
                drawBlockBox(safePos, Color.GREEN, true)
        }
    }

    /**
     * Check if [block] is a valid block to break
     */
    private fun validBlock(block: Block) = block != air && block !is BlockLiquid && block != bedrock

}