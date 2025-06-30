/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.value._boolean

object SilentHotbarModule : Module("SilentHotbar", Category.RENDER) {
    val keepHighlightedName by _boolean("KeepHighlightedName", false)
    val keepHotbarSlot by _boolean("KeepHotbarSlot", false)
    val keepItemInHandInFirstPerson by _boolean("KeepItemInHandInFirstPerson", false)
    val keepItemInHandInThirdPerson by _boolean("KeepItemInHandInThirdPerson", false)
}