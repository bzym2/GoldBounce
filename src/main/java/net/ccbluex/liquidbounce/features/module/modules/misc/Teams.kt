/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.value._boolean
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemArmor

object Teams : Module("Teams", Category.MISC, gameDetecting = false, hideModule = false) {

    private val scoreboard by _boolean("ScoreboardTeam", true)
    private val nameColor by _boolean("NameColor", true)
    private val armorColor by _boolean("ArmorColor", true)
    private val gommeSW by _boolean("GommeSW", false)

    /**
     * Check if [entity] is in your own team using scoreboard, name/armor color or team prefix
     */
    fun isInYourTeam(entity: EntityLivingBase): Boolean {
        val thePlayer = mc.thePlayer ?: return false

        if (scoreboard && thePlayer.team != null && entity.team != null &&
            thePlayer.team.isSameTeam(entity.team)
        )
            return true

        val displayName = thePlayer.displayName

        if (gommeSW && displayName != null && entity.displayName != null) {
            val targetName = entity.displayName.formattedText.replace("§r", "")
            val clientName = displayName.formattedText.replace("§r", "")
            if (targetName.startsWith("T") && clientName.startsWith("T"))
                if (targetName[1].isDigit() && clientName[1].isDigit())
                    return targetName[1] == clientName[1]
        }

        if (nameColor && displayName != null && entity.displayName != null) {
            val targetName = entity.displayName.formattedText.replace("§r", "")
            val clientName = displayName.formattedText.replace("§r", "")
            return targetName.startsWith("§${clientName[1]}")
        }

        if (armorColor) {
            for (i in 0..3) {
                val playerArmor = thePlayer.getCurrentArmor(i)
                val entityArmor = entity.getCurrentArmor(i)

                if (playerArmor != null && entityArmor != null) {
                    val playerItem = playerArmor.item
                    val entityItem = entityArmor.item

                    if (playerItem is ItemArmor && entityItem is ItemArmor) {
                        val playerArmorColor = playerItem.getColor(playerArmor)
                        val entityArmorColor = entityItem.getColor(entityArmor)

                        if (entityArmorColor == playerArmorColor) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

}
