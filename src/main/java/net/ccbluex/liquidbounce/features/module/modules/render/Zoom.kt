package net.ccbluex.liquidbounce.features.module.modules.render

import org.lwjgl.input.Keyboard
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.value.*

object Zoom : Module("SmoothZoom", Category.RENDER) {
    private var smoothMouse by boolean("SmoothMouse", false)

    override fun onEnable() {
        super.onEnable()
        using = true
    }

    override fun onDisable() {
        super.onDisable()
        using = false
    }

    @EventTarget
    fun onUpdate(e: UpdateEvent?) {
        if (mc.currentScreen != null) return
        if (Keyboard.isKeyDown(Keyboard.KEY_C)) {
            zoom = true
            if (smoothMouse) mc.gameSettings.smoothCamera = true
        } else {
            zoom = false
            if (smoothMouse) mc.gameSettings.smoothCamera = false
        }
    }

    @JvmField
    var using = false
    @JvmField
    var zoom = false
}
