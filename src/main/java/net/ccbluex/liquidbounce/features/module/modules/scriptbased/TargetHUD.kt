package net.ccbluex.liquidbounce.features.module.modules.scriptbased

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Target
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.RaycastUtils.raycastEntity
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.skid.moonlight.render.ColorUtils
import net.ccbluex.liquidbounce.value.ListValue
import net.ccbluex.liquidbounce.value.int
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EntityPlayer
import java.awt.Color

object TargetHUD : Module("TargetHUD", Category.SCRIPT, hideModule = false) {

    // 配置系统
    private val hudStyle by ListValue(
        "Style",
        arrayOf(
            "Health",
            "Novoline",
            "Smoke",
            "Moon",
            "0x01a4",
        ),
        "Health"
    )
    private val posX by int("PosX", 0, -400..400)
    private val posY by int("PosY", 0, -400..400)
    private val textColor = Color.WHITE
    private val bgColor = Color(0, 0, 0, 120)

    // 状态跟踪
    private var target: EntityPlayer? = null
    private const val HUD_WIDTH = 160
    private const val HUD_HEIGHT = 60

    @EventTarget
    fun onRender2D(event: Render2DEvent) {
        if (target == null) return
        val sr = ScaledResolution(mc)
        target =
            KillAura.target as EntityPlayer?

        // 抗机器人检测
        if (state && AntiBot.isBot(target!!)) return

        when (hudStyle.lowercase()) {
            "health" -> renderHealthHUD(sr)
            "novoline" -> renderNovolineHUD(sr)
            "smoke" -> renderSmokeHUD(sr)
            "moon" -> renderMoonHUD(sr)
            "0x01a4" -> render0x01a4HUD(sr)
        }
    }

    private fun renderHealthHUD(sr: ScaledResolution) {
        val centerX = sr.scaledWidth / 2 + posX
        val centerY = sr.scaledHeight / 2 + posY

        // 背景
        RenderUtils.drawRect(centerX - 60, centerY - 30, centerX + 60, centerY + 30, bgColor.rgb)

        // 名称
        Fonts.font35.drawCenteredString(target!!.name, centerX.toFloat(), (centerY - 25).toFloat(), textColor.rgb)

        // 生命条
        val healthPercent = target!!.health / target!!.maxHealth
        RenderUtils.drawRect(centerX - 50, centerY + 10, centerX + 50, centerY + 15, Color.DARK_GRAY.rgb)
        RenderUtils.drawRect(
            centerX - 50F, centerY + 10F,
            (centerX - 50F) + (100 * healthPercent).toFloat(), centerY + 15F,
            ColorUtils.healthColor(target!!.health, target!!.maxHealth)
        )
    }


    private fun renderNovolineHUD(sr: ScaledResolution) {
        val centerX = sr.scaledWidth / 2 + posX
        val centerY = sr.scaledHeight / 2 + posY
        RenderUtils.drawRect(centerX - 50, centerY - 15, centerX + 50, centerY + 15, Color(40, 40, 40).rgb)
        Fonts.font35.drawCenteredString(target!!.name, centerX.toFloat(), centerY - 10F, Color.WHITE.rgb)

        val healthPercent = (target!!.health / target!!.maxHealth).coerceIn(0f..1f)
        RenderUtils.drawRect(centerX - 45, centerY + 5, centerX + 45, centerY + 8, Color(30, 30, 30).rgb)
        RenderUtils.drawRect(
            centerX - 45, centerY + 5,
            (centerX - 45) + (90 * healthPercent).toInt(), centerY + 8,
            Color(129, 95, 149).rgb
        )
    }


    private fun render0x01a4HUD(sr: ScaledResolution) {
        val posX = sr.scaledWidth / 2 + this.posX
        val posY = sr.scaledHeight / 2 + this.posY

        // 高级渐变背景
        RenderUtils.drawRect(
            posX + 11F, posY + 5F, posX + 130F, posY + 53F,
            Color(30, 30, 30, 200).rgb
        )

        // 动态数据展示
        Fonts.font35.drawString("HP: ${target!!.health.toInt()}", posX + 15, posY + 15, Color.WHITE.rgb)
        Fonts.font35.drawString("Armor: ${target!!.totalArmorValue}", posX + 15, posY + 30, Color.WHITE.rgb)
    }


    // Moon样式实现
    private fun renderMoonHUD(sr: ScaledResolution) {
        val posX = sr.scaledWidth / 2 + this.posX
        val posY = sr.scaledHeight / 2 + this.posY


        // 数据面板
        RenderUtils.drawRect(posX + 11, posY + 5, posX + 116, posY + 34, Color(0, 0, 0, 100).rgb)
        Fonts.font35.drawString(target!!.name, posX + 41, posY + 8, Color.WHITE.rgb)

        // 动态生命条
        val healthPercent = target!!.health / target!!.maxHealth
        RenderUtils.drawRect(
            posX + 42F, posY + 26F,
            (posX + 42F) + (72 * healthPercent), posY + 27F,
            ColorUtils.healthColor(target!!.health, target!!.maxHealth)
        )
    }

    // Smoke样式实现
    private fun renderSmokeHUD(sr: ScaledResolution) {
        val posX = sr.scaledWidth / 2 + this.posX
        val posY = sr.scaledHeight / 2 + this.posY

        // 主面板
        RenderUtils.drawRect(posX, posY + 5, posX + 158, posY + 52, Color(20, 20, 20).rgb)

        // 动态生命指示器
        val healthPercent = target!!.health / target!!.maxHealth
        RenderUtils.drawRect(
            posX + 3F, posY + 47F,
            (posX + 3F) + (152 * healthPercent), posY + 40F,
            ColorUtils.healthColor(target!!.health, target!!.maxHealth)
        )
        val color = if (target!!.hurtTime > 0) Color(
            255,
            (255 + -(target!!.hurtTime * 20)).coerceAtMost(255),
            (255 + -(target!!.hurtTime * 20)).coerceAtMost(255)
        ) else Color.WHITE
        // 实体头像
        mc.netHandler.getPlayerInfo(target!!.uniqueID)?.let {
            Target().drawHead(it.locationSkin, posX + 3, posY + 9, 28, 28, color)
        }
    }

    override fun onDisable() {
        // 清理渲染状态
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.disableBlend()
    }
}