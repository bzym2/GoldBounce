/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.entity.player.EnumPlayerModelParts
import kotlin.random.Random.Default.nextBoolean

object SkinDerp : Module("SkinDerp", Category.FUN, subjective = true, hideModule = false) {

    private val delay by intValue("Delay", 0, 0..1000)
    private val hat by _boolean("Hat", true)
    private val jacket by _boolean("Jacket", true)
    private val leftPants by _boolean("LeftPants", true)
    private val rightPants by _boolean("RightPants", true)
    private val leftSleeve by _boolean("LeftSleeve", true)
    private val rightSleeve by _boolean("RightSleeve", true)

    private var prevModelParts = emptySet<EnumPlayerModelParts>()

    private val timer = MSTimer()

    override fun onEnable() {
        prevModelParts = mc.gameSettings.modelParts

        super.onEnable()
    }

    override fun onDisable() {
        // Disable all current model parts

        for (modelPart in mc.gameSettings.modelParts)
            mc.gameSettings.setModelPartEnabled(modelPart, false)

        // Enable all old model parts
        for (modelPart in prevModelParts)
            mc.gameSettings.setModelPartEnabled(modelPart, true)

        super.onDisable()
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (timer.hasTimePassed(delay)) {
            if (hat)
                mc.gameSettings.setModelPartEnabled(EnumPlayerModelParts.HAT, nextBoolean())
            if (jacket)
                mc.gameSettings.setModelPartEnabled(EnumPlayerModelParts.JACKET, nextBoolean())
            if (leftPants)
                mc.gameSettings.setModelPartEnabled(EnumPlayerModelParts.LEFT_PANTS_LEG, nextBoolean())
            if (rightPants)
                mc.gameSettings.setModelPartEnabled(EnumPlayerModelParts.RIGHT_PANTS_LEG, nextBoolean())
            if (leftSleeve)
                mc.gameSettings.setModelPartEnabled(EnumPlayerModelParts.LEFT_SLEEVE, nextBoolean())
            if (rightSleeve)
                mc.gameSettings.setModelPartEnabled(EnumPlayerModelParts.RIGHT_SLEEVE, nextBoolean())
            timer.reset()
        }
    }

}
