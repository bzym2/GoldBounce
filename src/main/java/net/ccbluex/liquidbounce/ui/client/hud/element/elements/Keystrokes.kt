/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.ui.font.GameFontRenderer
import net.ccbluex.liquidbounce.utils.render.ColorSettingsInteger
import net.ccbluex.liquidbounce.utils.render.ColorUtils.rainbow
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.floatValue
import net.ccbluex.liquidbounce.value.font
import java.awt.Color

@ElementInfo(name = "Keystrokes")
class Keystrokes : Element(2.0, 123.0) {
    private val radius by floatValue("RectangleRound-Radius", 3F, 0F..10F)
    private val textRainbow by _boolean("Text-Rainbow", false)
    private val textColors = ColorSettingsInteger(this, "Text", zeroAlphaCheck = true, applyMax = true)
    private val rectRainbow by _boolean("Rectangle-Rainbow", false)
    private val rectColors = ColorSettingsInteger(this, "Rectangle", zeroAlphaCheck = true).with(a = 150)
    private val pressRainbow by _boolean("Press-Rainbow", false)
    private val pressColors = ColorSettingsInteger(this, "Press", zeroAlphaCheck = true).with(Color.YELLOW)

    private var shadow by _boolean("Text-Shadow", true)
    private val font by font("Font", Fonts.font40)

    // row -> column -> key
    private val gridLayout = listOf(
        Triple(1, 1, "W"),
        Triple(2, 0, "A"),
        Triple(2, 1, "S"),
        Triple(2, 2, "D"),
        Triple(3, 1, "Space")
    )

    private val textColor
        get() = if (textRainbow) rainbow() else textColors.color()

    private val rectColor
        get() = if (rectRainbow) rainbow() else rectColors.color()

    private val pressColor
        get() = if (pressRainbow) rainbow() else pressColors.color()

    override fun drawElement(): Border {
        val options = mc.gameSettings

        val movementKeys = mapOf(
            "Space" to options.keyBindJump,
            "W" to options.keyBindForward,
            "A" to options.keyBindLeft,
            "S" to options.keyBindBack,
            "D" to options.keyBindRight
        )

        val padding = 3F

        val fontHeight = (font as? GameFontRenderer)?.height ?: font.FONT_HEIGHT
        val maxCharWidth = gridLayout.maxOf { (_, _, key) -> font.getStringWidth(key) }

        val boxSize = maxOf(fontHeight, maxCharWidth)

        gridLayout.forEach { (row, col, key) ->
            val currentX = col * (boxSize + padding)
            val currentY = row * (boxSize + padding)

            val (startX, endX) = if (row == 3) {
                // Fill from the first row until the last (Space button)
                0F to 2 * (boxSize + padding) + boxSize
            } else currentX to currentX + boxSize

            val color = if (movementKeys[key]?.isKeyDown == true) pressColor else rectColor

            RenderUtils.drawRoundedRect(startX, currentY, endX, currentY + boxSize, color.rgb, radius)

            val textX = (startX + endX) / 2 - (font.getStringWidth(key) / 2)
            val textY = currentY + (boxSize / 2) - (fontHeight / 2)

            font.drawString(key, textX, textY, textColor.rgb, shadow)
        }

        return Border(0F, boxSize + padding, boxSize * 3 + padding * 2, boxSize * 4 + padding * 3)
    }


}

