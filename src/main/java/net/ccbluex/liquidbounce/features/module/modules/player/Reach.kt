/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.value.floatValue
import kotlin.math.max

object Reach : Module("Reach", Category.PLAYER, hideModule = false) {

    val combatReach by floatValue("CombatReach", 3.5f, 3f..7f)
    val buildReach by floatValue("BuildReach", 5f, 4.5f..7f)

    val maxRange
        get() = max(combatReach, buildReach)
}
