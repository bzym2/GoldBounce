/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.ui.client.altmanager.menus

import me.liuli.elixir.account.MinecraftAccount
import net.ccbluex.liquidbounce.ui.client.altmanager.GuiAltManager.Companion.login
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawLoadingCircle
import net.minecraft.client.gui.GuiScreen

class GuiLoginProgress(minecraftAccount: MinecraftAccount, success: () -> Unit, error: (Exception) -> Unit, done: () -> Unit) : GuiScreen() {

    init {
        login(minecraftAccount, success, error, done)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile = true

        drawDefaultBackground()
        drawLoadingCircle(width / 2f, height / 4f + 70)
        drawCenteredString(fontRendererObj, "Logging into account...", width / 2, height / 2 - 60, 16777215)

        assumeNonVolatile = false

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

}