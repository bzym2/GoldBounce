/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.LiquidBounce.background
import net.ccbluex.liquidbounce.bzym.GlobalFeatures
import net.ccbluex.liquidbounce.file.FileManager.backgroundImageFile
import net.ccbluex.liquidbounce.file.FileManager.backgroundShaderFile
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.file.FileManager.valuesConfig
import net.ccbluex.liquidbounce.lang.LanguageManager
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.Background
import net.ccbluex.liquidbounce.utils.MinecraftInstance.Companion.mc
import net.ccbluex.liquidbounce.utils.misc.MiscUtils
import net.ccbluex.liquidbounce.utils.render.IconUtils
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraftforge.fml.client.config.GuiSlider
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.Display
import java.nio.file.Files

class GuiClientConfiguration(val prevGui: GuiScreen) : GuiScreen() {

    companion object {
        var enabledClientTitle = true
        var enabledCustomBackground = true
        var particles = false
        var stylisedAlts = true
        var unformattedAlts = false
        var altsLength = 8

        fun updateClientWindow() {
            if (enabledClientTitle) {
                // Set LiquidBounce title
                Display.setTitle("欢迎您。${GlobalFeatures().getIpLocation()} 本客户端永久开源免费 严禁售卖 官方群聊718366220")
                IconUtils.getFavicon()?.let { icons ->
                    Display.setIcon(icons)
                }
            } else {
                // Set original title
                Display.setTitle("Minecraft 1.8.9")
                // Update favicon
                mc.setWindowIcon()
            }
        }

    }

    private lateinit var languageButton: GuiButton

    private lateinit var backgroundButton: GuiButton
    private lateinit var particlesButton: GuiButton
    private lateinit var altsModeButton: GuiButton
    private lateinit var unformattedAltsButton: GuiButton
    private lateinit var altsSlider: GuiSlider

    private lateinit var titleButton: GuiButton

    override fun initGui() {
        buttonList.run {
            clear()

            // Title button
            // Location > 1st row
            add(GuiButton(
                4, width / 2 - 100, height / 4 + 25, "Client title (${if (enabledClientTitle) "On" else "Off"})"
            ).also { titleButton = it })
            add(GuiButton(
                7,
                width / 2 - 100,
                height / 4 + 50,
                "Language (${LanguageManager.overrideLanguage.ifBlank { "Game" }})"
            ).also { languageButton = it })

            // Background configuration buttons
            // Button location > 2nd row
            add(GuiButton(
                0,
                width / 2 - 100,
                height / 4 + 25 + 75,
                "Enabled (${if (enabledCustomBackground) "On" else "Off"})"
            ).also { backgroundButton = it })
            add(GuiButton(
                1, width / 2 - 100, height / 4 + 25 + 75 + 25, "Particles (${if (particles) "On" else "Off"})"
            ).also { particlesButton = it })
            add(GuiButton(2, width / 2 - 100, height / 4 + 25 + 75 + 25 * 2, 98, 20, "Change wallpaper"))
            add(GuiButton(3, width / 2 + 2, height / 4 + 25 + 75 + 25 * 2, 98, 20, "Reset wallpaper"))

            // AltManager configuration buttons
            // Location > 3rd row
            add(GuiButton(
                6,
                width / 2 - 100,
                height / 4 + 25 + 185,
                "Random alts mode (${if (stylisedAlts) "Stylised" else "Legacy"})"
            ).also { altsModeButton = it })
            add(GuiSlider(
                -1,
                width / 2 - 100,
                height / 4 + 210 + 25,
                200,
                20,
                "${if (stylisedAlts && unformattedAlts) "Random alt max" else "Random alt"} length (",
                ")",
                6.0,
                16.0,
                altsLength.toDouble(),
                false,
                true
            ) {
                altsLength = it.valueInt
            }.also { altsSlider = it })
            add(GuiButton(
                5,
                width / 2 - 100,
                height / 4 + 235 + 25,
                "Unformatted alt names (${if (unformattedAlts) "On" else "Off"})"
            ).also {
                it.enabled = stylisedAlts
                unformattedAltsButton = it
            })

            // Back button
            add(GuiButton(8, width / 2 - 100, height / 4 + 25 + 25 * 11, "Back"))
        }
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> {
                enabledCustomBackground = !enabledCustomBackground
                backgroundButton.displayString = "Enabled (${if (enabledCustomBackground) "On" else "Off"})"
            }

            1 -> {
                particles = !particles
                particlesButton.displayString = "Particles (${if (particles) "On" else "Off"})"
            }

            4 -> {
                enabledClientTitle = !enabledClientTitle
                titleButton.displayString = "Client title (${if (enabledClientTitle) "On" else "Off"})"
                updateClientWindow()
            }

            5 -> {
                unformattedAlts = !unformattedAlts
                unformattedAltsButton.displayString = "Unformatted alt names (${if (unformattedAlts) "On" else "Off"})"
                altsSlider.dispString = "${if (unformattedAlts) "Max random alt" else "Random alt"} length ("
                altsSlider.updateSlider()
            }

            6 -> {
                stylisedAlts = !stylisedAlts
                altsModeButton.displayString = "Random alts mode (${if (stylisedAlts) "Stylised" else "Legacy"})"
                altsSlider.dispString =
                    "${if (stylisedAlts && unformattedAlts) "Max random alt" else "Random alt"} length ("
                altsSlider.updateSlider()
                unformattedAltsButton.enabled = stylisedAlts
            }

            2 -> {
                val file = MiscUtils.openFileChooser() ?: return

                if (file.isDirectory) return

                // Delete old files
                background = null
                if (backgroundImageFile.exists()) backgroundImageFile.deleteRecursively()
                if (backgroundShaderFile.exists()) backgroundShaderFile.deleteRecursively()

                // Copy new file
                val fileExtension = file.extension

                try {
                    val destFile = when (fileExtension) {
                        "png" -> backgroundImageFile
                        "frag", "glsl", "shader" -> backgroundShaderFile
                        else -> {
                            MiscUtils.showErrorPopup("Error", "Invalid file extension: $fileExtension")
                            return
                        }
                    }

                    Files.copy(file.toPath(), destFile.outputStream())

                    // Load new background
                    try {
                        background = Background.createBackground(destFile)
                    } catch (_: IllegalArgumentException) {
                        background = null
                        if (backgroundImageFile.exists()) backgroundImageFile.deleteRecursively()
                        if (backgroundShaderFile.exists()) backgroundShaderFile.deleteRecursively()

                        MiscUtils.showErrorPopup("Error", "Invalid file extension: $fileExtension")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    MiscUtils.showErrorPopup(
                        "Error", "Exception class: " + e.javaClass.name + "\nMessage: " + e.message
                    )

                    background = null
                    if (backgroundImageFile.exists()) backgroundImageFile.deleteRecursively()
                    if (backgroundShaderFile.exists()) backgroundShaderFile.deleteRecursively()
                }
            }

            3 -> {
                background = null
                if (backgroundImageFile.exists()) backgroundImageFile.deleteRecursively()
                if (backgroundShaderFile.exists()) backgroundShaderFile.deleteRecursively()
            }

            7 -> {
                val languageIndex = LanguageManager.knownLanguages.indexOf(LanguageManager.overrideLanguage)

                // If the language is not found, set it to the first language
                if (languageIndex == -1) {
                    LanguageManager.overrideLanguage = LanguageManager.knownLanguages.first()
                } else {
                    // If the language is the last one, set it to blank
                    if (languageIndex == LanguageManager.knownLanguages.size - 1) {
                        LanguageManager.overrideLanguage = ""
                    } else {
                        // Otherwise, set it to the next language
                        LanguageManager.overrideLanguage = LanguageManager.knownLanguages[languageIndex + 1]
                    }
                }

                initGui()
            }

            8 -> mc.displayGuiScreen(prevGui)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)
        Fonts.fontBold180.drawCenteredString(
            translationMenu("configuration"), width / 2F, height / 8F + 5F, 4673984, true
        )

        Fonts.font40.drawString(
            "Window", width / 2F - 98F, height / 4F + 15F, 0xFFFFFF, true
        )

        Fonts.font40.drawString(
            "Background", width / 2F - 98F, height / 4F + 90F, 0xFFFFFF, true
        )
        Fonts.font35.drawString(
            "Supported background types: (.png, .frag, .glsl)",
            width / 2F - 98F,
            height / 4F + 100 + 25 * 3,
            0xFFFFFF,
            true
        )

        Fonts.font40.drawString(
            translationMenu("altManager"), width / 2F - 98F, height / 4F + 200F, 0xFFFFFF, true
        )
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        saveConfig(valuesConfig)
        super.onGuiClosed()
    }
}