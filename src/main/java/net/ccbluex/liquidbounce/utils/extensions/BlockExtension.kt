/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.utils.extensions

import net.ccbluex.liquidbounce.utils.block.BlockUtils
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3

/**
 * Get block by position
 */
fun BlockPos.getBlock() = BlockUtils.getBlock(this)

/**
 * Get vector of block position
 */
fun BlockPos.getVec() = Vec3(x + 0.5, y + 0.5, z + 0.5)

fun BlockPos.toVec() = Vec3(this)

fun BlockPos.isReplaceable() = BlockUtils.isReplaceable(this)

fun BlockPos.canBeClicked() = BlockUtils.canBeClicked(this)