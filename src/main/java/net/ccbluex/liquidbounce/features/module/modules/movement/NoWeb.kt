/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.aac.AAC
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.aac.LAAC
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.intave.IntaveNew
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.intave.IntaveOld
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other.None
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other.OldGrim
import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.other.Rewi
import net.ccbluex.liquidbounce.value.choices

object NoWeb : Module("NoWeb", Category.MOVEMENT, hideModule = false) {

    private val noWebModes = arrayOf(
        // Vanilla
        None,

        // AAC
        AAC, LAAC,

        // Intave
        IntaveOld,
        IntaveNew,

         // Other
        Rewi,
        OldGrim
    )

    private val modes = noWebModes.map { it.modeName }.toTypedArray()

    val mode by choices(
        "Mode", modes, "None"
    )

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        modeModule.onUpdate()
    }

    override val tag
        get() = mode

    private val modeModule
        get() = noWebModes.find { it.modeName == mode }!!
}
