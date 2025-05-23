/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.player.Blink
import net.ccbluex.liquidbounce.features.module.modules.settings.Sounds.playKillSound
import net.ccbluex.liquidbounce.features.module.modules.world.ChestAura
import net.ccbluex.liquidbounce.features.module.modules.world.Fucker
import net.ccbluex.liquidbounce.features.module.modules.world.Nuker
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Tower
import net.ccbluex.liquidbounce.utils.*
import net.ccbluex.liquidbounce.utils.ClientUtils.runTimeTicks
import net.ccbluex.liquidbounce.utils.CooldownHelper.getAttackCooldownProgress
import net.ccbluex.liquidbounce.utils.CooldownHelper.resetLastAttackedTicks
import net.ccbluex.liquidbounce.utils.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.RaycastUtils.raycastEntity
import net.ccbluex.liquidbounce.utils.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.getVectorForRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.isRotationFaced
import net.ccbluex.liquidbounce.utils.RotationUtils.isVisible
import net.ccbluex.liquidbounce.utils.RotationUtils.rotationDifference
import net.ccbluex.liquidbounce.utils.RotationUtils.searchCenter
import net.ccbluex.liquidbounce.utils.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.toRotation
import net.ccbluex.liquidbounce.utils.attack.EntityUtils
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenInventory
import net.ccbluex.liquidbounce.utils.inventory.ItemUtils.isConsumingItem
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.packet.sendOffHandUseItem.sendOffHandUseItem
import net.ccbluex.liquidbounce.utils.render.ColorSettingsInteger
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawEntityBox
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawPlatform
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.utils.timing.TimeUtils.randomClickDelay
import net.ccbluex.liquidbounce.value.*
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C02PacketUseEntity.Action.INTERACT
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.potion.Potion
import net.minecraft.util.*
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object KillAura : Module("KillAura", Category.COMBAT, hideModule = false) {
    /**
     * OPTIONS
     */

    private val simulateCooldown by boolean("SimulateCooldown", false)
    private val simulateDoubleClicking by boolean("SimulateDoubleClicking", false) { !simulateCooldown }

    // CPS - Attack speed
    private val maxCPSValue = object : IntegerValue("MaxCPS", 8, 1..20) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minCPS)

        override fun onChanged(oldValue: Int, newValue: Int) {
            attackDelay = randomClickDelay(minCPS, newValue)
        }

        override fun isSupported() = !simulateCooldown
    }

    private val maxCPS by maxCPSValue

    private val minCPS: Int by object : IntegerValue("MinCPS", 5, 1..20) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxCPS)

        override fun onChanged(oldValue: Int, newValue: Int) {
            attackDelay = randomClickDelay(newValue, maxCPS)
        }

        override fun isSupported() = !maxCPSValue.isMinimal() && !simulateCooldown
    }

    private val hurtTime by int("HurtTime", 10, 0..10) { !simulateCooldown }

    private val clickOnly by boolean("ClickOnly", false)

    // Range
    // TODO: Make block range independent from attack range
    val range: Float by object : FloatValue("Range", 3.7f, 1f..8f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            blockRange = blockRange.coerceAtMost(newValue)
        }
    }
    private val scanRange by float("ScanRange", 2f, 0f..10f)
    private val throughWallsRange by float("ThroughWallsRange", 3f, 0f..8f)
    private val rangeSprintReduction by float("RangeSprintReduction", 0f, 0f..0.4f)

    // Modes
    val priority by choices(
        "Priority", arrayOf(
            "Health",
            "Distance",
            "Direction",
            "LivingTime",
            "Armor",
            "HurtResistance",
            "HurtTime",
            "HealthAbsorption",
            "RegenAmplifier",
            "OnLadder",
            "InLiquid",
            "InWeb"
        ), "Distance"
    )
    private val targetMode by choices("TargetMode", arrayOf("Single", "Switch", "Multi"), "Switch")
    private val limitedMultiTargets by int("LimitedMultiTargets", 0, 0..50) { targetMode == "Multi" }
    private val maxSwitchFOV by float("MaxSwitchFOV", 90f, 30f..180f) { targetMode == "Switch" }

    // Delay
    private val switchDelay by int("SwitchDelay", 15, 1..1000) { targetMode == "Switch" }

    // Bypass
    private val swing by boolean("Swing", true)
    private val keepSprint by boolean("KeepSprint", true)

    // Settings
    private val autoF5 by boolean("AutoF5", false, subjective = true)
    private val onScaffold by boolean("OnScaffold", false)
    private val onDestroyBlock by boolean("OnDestroyBlock", false)

    // AutoBlock
    val autoBlock by choices(
        "AutoBlock",
        arrayOf("Off", "Packet", "Fake", "QuickMarco", "BlocksMC", "BlocksMC_A", "BlocksMC_B", "HypixelFull", "NCP"),
        "Packet"
    )

    // Block$MC
    private var blocksmcJohnState = false
    private var blocksmcClickCounter = 0
    private val blocksmcAttackInterval by int("BlocksMCAttackInterval", 2, 1..5) { autoBlock == "BlocksMC" }
    private val blocksmcBlockRate by int("BlocksMCBlockRate", 50, 1..100) { autoBlock == "BlocksMC" }
    private var hypixelBlinking = false
    private var hypixelBlockTicks = 0
    private val maxBlinkPackets by int("MaxBlinkPackets", 20, 5..100) { autoBlock == "HypixelFull" }

    private val blockMaxRange by float(
        "BlockMaxRange",
        3f,
        0f..8f
    ) { autoBlock == "Packet" || autoBlock == "QuickMarco" }
    private val unblockMode by choices(
        "UnblockMode",
        arrayOf("Stop", "Switch", "Empty"),
        "Stop"
    ) { (autoBlock == "Packet") || (autoBlock == "QuickMarco") }
    private val releaseAutoBlock by boolean("ReleaseAutoBlock", true)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") }
    val forceBlockRender by boolean("ForceBlockRender", true)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") && releaseAutoBlock }
    private val ignoreTickRule by boolean("IgnoreTickRule", false)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") && releaseAutoBlock }
    private val blockRate by int("BlockRate", 100, 1..100)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") && releaseAutoBlock }

    private val uncpAutoBlock by boolean("UpdatedNCPAutoBlock", false)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") && !releaseAutoBlock }

    private val switchStartBlock by boolean("SwitchStartBlock", false)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") }

    private val interactAutoBlock by boolean("InteractAutoBlock", true)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") }

    val blinkAutoBlock by boolean("BlinkAutoBlock", false)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") }

    private val blinkBlockTicks by int("BlinkBlockTicks", 3, 2..5)
    { autoBlock !in arrayOf("Off", "Fake", "BlocksMC", "HypixelFull") && blinkAutoBlock }

    // AutoBlock conditions
    private val smartAutoBlock by boolean("SmartAutoBlock", false) { autoBlock == "Packet" }

    // Ignore all blocking conditions, except for block rate, when standing still
    private val forceBlock by boolean("ForceBlockWhenStill", true)
    { smartAutoBlock }

    // Don't block if target isn't holding a sword or an axe
    private val checkWeapon by boolean("CheckEnemyWeapon", true)
    { smartAutoBlock }

    // TODO: Make block range independent from attack range
    private var blockRange by object : FloatValue("BlockRange", range, 1f..8f) {
        override fun isSupported() = smartAutoBlock

        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(this@KillAura.range)
    }

    // Don't block when you can't get damaged
    private val maxOwnHurtTime by int("MaxOwnHurtTime", 3, 0..10)
    { smartAutoBlock }

    // Don't block if target isn't looking at you
    private val maxDirectionDiff by float("MaxOpponentDirectionDiff", 60f, 30f..180f)
    { smartAutoBlock }

    // Don't block if target is swinging an item and therefore cannot attack
    private val maxSwingProgress by int("MaxOpponentSwingProgress", 1, 0..5)
    { smartAutoBlock }

    // Rotations
    private val options = RotationSettings(this).withoutKeepRotation()

    // Raycast
    private val raycastValue = boolean("RayCast", true) { options.rotationsActive }
    private val raycast by raycastValue
    private val raycastIgnored by boolean(
        "RayCastIgnored",
        false
    ) { raycastValue.isActive() && options.rotationsActive }
    private val livingRaycast by boolean("LivingRayCast", true) { raycastValue.isActive() && options.rotationsActive }

    // Hit delay
    private val useHitDelay by boolean("UseHitDelay", false)
    private val hitDelayTicks by int("HitDelayTicks", 1, 1..5) { useHitDelay }

    private val randomization = RandomizationSettings(this) { options.rotationsActive }
    private val outborder by boolean("Outborder", false) { options.rotationsActive }

    private val highestBodyPointToTargetValue: ListValue = object : ListValue(
        "HighestBodyPointToTarget",
        arrayOf("Head", "Body", "Feet"),
        "Head"
    ) {
        override fun isSupported() = options.rotationsActive

        override fun onChange(oldValue: String, newValue: String): String {
            val newPoint = RotationUtils.BodyPoint.fromString(newValue)
            val lowestPoint = RotationUtils.BodyPoint.fromString(lowestBodyPointToTarget)
            val coercedPoint = RotationUtils.coerceBodyPoint(newPoint, lowestPoint, RotationUtils.BodyPoint.HEAD)
            return coercedPoint.name
        }
    }
    private val highestBodyPointToTarget by highestBodyPointToTargetValue
    private val lowestBodyPointToTargetValue: ListValue = object : ListValue(
        "LowestBodyPointToTarget",
        arrayOf("Head", "Body", "Feet"),
        "Feet"
    ) {
        override fun isSupported() = options.rotationsActive

        override fun onChange(oldValue: String, newValue: String): String {
            val newPoint = RotationUtils.BodyPoint.fromString(newValue)
            val highestPoint = RotationUtils.BodyPoint.fromString(highestBodyPointToTarget)
            val coercedPoint = RotationUtils.coerceBodyPoint(newPoint, RotationUtils.BodyPoint.FEET, highestPoint)
            return coercedPoint.name
        }
    }

    private val lowestBodyPointToTarget by lowestBodyPointToTargetValue

    private val maxHorizontalBodySearch: FloatValue = object : FloatValue("MaxHorizontalBodySearch", 1f, 0f..1f) {
        override fun isSupported() = options.rotationsActive

        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtLeast(minHorizontalBodySearch.get())
    }

    private val minHorizontalBodySearch: FloatValue = object : FloatValue("MinHorizontalBodySearch", 0f, 0f..1f) {
        override fun isSupported() = options.rotationsActive

        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(maxHorizontalBodySearch.get())
    }

    private val fov by float("FOV", 180f, 0f..180f)

    // Prediction
    private val predictClientMovement by int("PredictClientMovement", 2, 0..5)
    private val predictOnlyWhenOutOfRange by boolean(
        "PredictOnlyWhenOutOfRange",
        false
    ) { predictClientMovement != 0 }
    private val predictEnemyPosition by float("PredictEnemyPosition", 1.5f, -1f..2f)

    // Extra swing
    private val failSwing by boolean("FailSwing", true) { swing && options.rotationsActive }
    private val respectMissCooldown by boolean("RespectMissCooldown", false)
    { swing && failSwing && options.rotationsActive }
    private val swingOnlyInAir by boolean("SwingOnlyInAir", true) { swing && failSwing && options.rotationsActive }
    private val maxRotationDifferenceToSwing by float("MaxRotationDifferenceToSwing", 180f, 0f..180f)
    { swing && failSwing && options.rotationsActive }
    private val swingWhenTicksLate = object : BoolValue("SwingWhenTicksLate", false) {
        override fun isSupported() =
            swing && failSwing && maxRotationDifferenceToSwing != 180f && options.rotationsActive
    }
    private val ticksLateToSwing by int("TicksLateToSwing", 4, 0..20)
    { swing && failSwing && swingWhenTicksLate.isActive() && options.rotationsActive }
    private val renderBoxOnSwingFail by boolean("RenderBoxOnSwingFail", false) { failSwing }
    private val renderBoxColor = ColorSettingsInteger(this, "RenderBoxColor") { renderBoxOnSwingFail }.with(0, 255, 255)
    private val renderBoxFadeSeconds by float("RenderBoxFadeSeconds", 1f, 0f..5f) { renderBoxOnSwingFail }

    // Inventory
    private val simulateClosingInventory by boolean("SimulateClosingInventory", false) { !noInventoryAttack }
    private val noInventoryAttack by boolean("NoInvAttack", false)
    private val noInventoryDelay by int("NoInvDelay", 200, 0..500) { noInventoryAttack }
    private val noConsumeAttack by choices(
        "NoConsumeAttack",
        arrayOf("Off", "NoHits", "NoRotation"),
        "Off",
        subjective = true
    )

    // Visuals
    private val mark by choices("Mark", arrayOf("None", "Platform", "Box"), "Platform", subjective = true)
    private val boxOutline by boolean("Outline", true, subjective = true) { mark == "Box" }
    private val fakeSharp by boolean("FakeSharp", true, subjective = true)
    private val circle by BoolValue("Circle", false)
    private val circleAccuracy by IntegerValue("Accuracy", 59, 0..59) { circle }
    private val circleThickness by FloatValue("Thickness", 2f, 0f..20f) { circle }
    private val circleRed by IntegerValue("Red", 255, 0..255) { circle }
    private val circleGreen by IntegerValue("Green", 255, 0..255) { circle }
    private val circleBlue by IntegerValue("Blue", 255, 0..255) { circle }
    private val circleAlpha by IntegerValue("Alpha", 255, 0..255) { circle }

    /**
     * MODULE
     */
    private var blockTick: Int = 0
    var attack: Int = 0
    var blinking: Boolean = false
    private var b1 = false
    private var b2 = false
    val blinkedPackets: ArrayList<Packet<*>?> = ArrayList<Packet<*>?>()
    private var lastAttackTime: Long = 0
    // Target
    var target: EntityLivingBase? = null
    private var hittable = false
    private val prevTargetEntities = mutableListOf<Int>()

    // Attack delay
    private val attackTimer = MSTimer()
    private var attackDelay = 0
    private var clicks = 0
    private var attackTickTimes = mutableListOf<Pair<MovingObjectPosition, Int>>()
    private var blinkTicks = 0
    // Container Delay
    private var containerOpen = -1L

    // Block status
    var renderBlocking = false
    var blockStatus = false
    private var blockStopInDead = false

    // Switch Delay
    private val switchTimer = MSTimer()

    // Blink AutoBlock
    private var blinked = false
    private lateinit var combatPacket : PacketEvent
    // Swing fails
    private val swingFails = mutableListOf<SwingFailData>()
    var slotChangeAutoBlock = false
    private var asw = 0 // 用于BlocksMC A模式的状态控制
    private var blockTickB = 0 // 用于BlocksMC B模式的状态控制
    /**
     * Disable kill aura module
     */
    override fun onToggle(state: Boolean) {
        target = null
        hittable = false
        prevTargetEntities.clear()
        attackTickTimes.clear()
        attackTimer.reset()
        clicks = 0
        if (!state && autoBlock == "HypixelFull") {
            BlinkUtils.unblink()
            hypixelBlinking = false
        }
        if (blinkAutoBlock) {
            BlinkUtils.unblink()
            blinked = false
        }

        if (autoF5)
            mc.gameSettings.thirdPersonView = 0

        stopBlocking(true)

        synchronized(swingFails) {
            swingFails.clear()
        }
    }

    @EventTarget
    fun onRotationUpdate(event: RotationUpdateEvent) {
        update()
    }

    fun update() {
        if (cancelRun || (noInventoryAttack && (mc.currentScreen is GuiContainer || System.currentTimeMillis() - containerOpen < noInventoryDelay))) return

        // Update target
        updateTarget()

        if (autoF5) {
            if (mc.gameSettings.thirdPersonView != 1 && target != null) {
                mc.gameSettings.thirdPersonView = 1
            }
        }
    }

    @EventTarget
    fun onWorldChange(event: WorldEvent) {
        attackTickTimes.clear()

        if (blinkAutoBlock && BlinkUtils.isBlinking)
            BlinkUtils.unblink()

        synchronized(swingFails) {
            swingFails.clear()
        }
    }
    private fun releaseBlinkPackets() {
        synchronized(blinkedPackets) {
            blinkedPackets.forEach { packet ->
                packet?.let { sendPacket(it) }
            }
            blinkedPackets.clear()
        }

        // 补偿客户端状态
        if (blockStatus) {
            sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
        }
    }
    /**
     * Tick event
     */
    @EventTarget
    fun onTick(event: GameTickEvent) {
        val player = mc.thePlayer ?: return
        target?.let { if (it.isDead) EventManager.callEvent(EntityKilledEvent(target!!)) }
        if (blinking) {
            if (++blinkTicks >= blinkBlockTicks) {
                releaseBlinkPackets()
                blinking = false
                blinkTicks = 0
            }
        }
        if (shouldPrioritize()) {
            target = null
            renderBlocking = false
            return
        }

        if (clickOnly && !mc.gameSettings.keyBindAttack.isKeyDown)
            return

        if (blockStatus && autoBlock == "Packet" && releaseAutoBlock && !ignoreTickRule) {
            clicks = 0
            stopBlocking()
            return
        }

        if (cancelRun) {
            target = null
            hittable = false
            stopBlocking()
            return
        }

        if (noInventoryAttack && (mc.currentScreen is GuiContainer || System.currentTimeMillis() - containerOpen < noInventoryDelay)) {
            target = null
            hittable = false
            if (mc.currentScreen is GuiContainer) containerOpen = System.currentTimeMillis()
            return
        }

        if (simulateCooldown && getAttackCooldownProgress() < 1f) {
            return
        }

        if (target == null && !blockStopInDead) {
            blockStopInDead = true
            stopBlocking()
            return
        }

        if (blinkAutoBlock) {
            when (player.ticksExisted % (blinkBlockTicks + 1)) {
                0 -> {
                    if (blockStatus && !blinked && !BlinkUtils.isBlinking) {
                        blinked = true
                    }
                }

                1 -> {
                    if (blockStatus && blinked && BlinkUtils.isBlinking) {
                        stopBlocking()
                    }
                }

                blinkBlockTicks -> {
                    if (!blockStatus && blinked && BlinkUtils.isBlinking) {
                        BlinkUtils.unblink()
                        blinked = false

                        startBlocking(target!!, interactAutoBlock, autoBlock == "Fake") // block again
                    }
                }
            }
        }

        if (target != null) {
            if (player.getDistanceToEntityBox(target!!) > blockMaxRange && blockStatus) {
                stopBlocking(true)
                return
            } else {
                if (autoBlock != "Off" && !releaseAutoBlock) {
                    renderBlocking = true
                }
            }

            // Usually when you butterfly click, you end up clicking two (and possibly more) times in a single tick.
            // Sometimes you also do not click. The positives outweigh the negatives, however.
            val extraClicks = if (simulateDoubleClicking && !simulateCooldown) nextInt(-1, 1) else 0

            val maxClicks = clicks + extraClicks

            repeat(maxClicks) {
                val wasBlocking = blockStatus

                runAttack(it == 0, it + 1 == maxClicks)
                clicks--

                if (wasBlocking && !blockStatus && (releaseAutoBlock && !ignoreTickRule || autoBlock == "Off")) {
                    return
                }
            }
        } else {
            renderBlocking = false
        }
    }

    /**
     * Render event
     */
    private val hittableColor = Color(37, 126, 255, 70)
    private val notHittableColor = Color(255, 0, 0, 70)

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (circle) {
            GL11.glPushMatrix()
            GL11.glTranslated(
                mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * mc.timer.renderPartialTicks - mc.renderManager.renderPosX,
                mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * mc.timer.renderPartialTicks - mc.renderManager.renderPosY,
                mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * mc.timer.renderPartialTicks - mc.renderManager.renderPosZ
            )
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            GL11.glLineWidth(circleThickness)
            GL11.glColor4f(
                circleRed.toFloat() / 255.0F,
                circleGreen.toFloat() / 255.0F,
                circleBlue.toFloat() / 255.0F,
                circleAlpha.toFloat() / 255.0F
            )
            GL11.glRotatef(90F, 1F, 0F, 0F)
            GL11.glBegin(GL11.GL_LINE_STRIP)

            for (i in 0..360 step 60 - circleAccuracy) { // You can change circle accuracy  (60 - accuracy)
                GL11.glVertex2f(
                    cos(i * Math.PI / 180.0).toFloat() * range,
                    (sin(i * Math.PI / 180.0).toFloat() * range)
                )
            }

            GL11.glEnd()

            GL11.glDisable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)

            GL11.glPopMatrix()
        }
        handleFailedSwings()
        if (cancelRun) {
            target = null
            hittable = false
            return
        }

        if (noInventoryAttack && (mc.currentScreen is GuiContainer || System.currentTimeMillis() - containerOpen < noInventoryDelay)) {
            target = null
            hittable = false
            if (mc.currentScreen is GuiContainer) containerOpen = System.currentTimeMillis()
            return
        }

        target ?: return

        if (attackTimer.hasTimePassed(attackDelay)) {
            if (maxCPS > 0)
                clicks++
            attackTimer.reset()
            attackDelay = randomClickDelay(minCPS, maxCPS)
        }

        val color = if (hittable) hittableColor else notHittableColor

        if (targetMode != "Multi") {
            when (mark.lowercase()) {
                "none" -> return
                "platform" -> drawPlatform(target!!, color)
                "box" -> drawEntityBox(target!!, color, boxOutline)
            }
        }
    }


    /**
     * Attack enemy
     */
    private fun runAttack(isFirstClick: Boolean, isLastClick: Boolean) {
        var currentTarget = this.target ?: return

        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (noConsumeAttack == "NoHits" && isConsumingItem()) {
            return
        }

        val multi = targetMode == "Multi"
        val manipulateInventory = simulateClosingInventory && !noInventoryAttack && serverOpenInventory

        updateHittable()

        currentTarget = this.target ?: return

        if (hittable && currentTarget.hurtTime > hurtTime) {
            return
        }
        when (autoBlock.lowercase()) {
            "blocksmc_a" -> handleBlocksMC_A(currentTarget)
            "blocksmc_b" -> handleBlocksMC_B(currentTarget)
            "ncp" -> handleNCP(currentTarget)
        }

        if (manipulateInventory && isFirstClick) serverOpenInventory = false

        if (!multi) {
            attackEntity(currentTarget, isLastClick)
        } else {
            var targets = 0
            for (entity in world.loadedEntityList) {
                if (entity is EntityLivingBase && isEnemy(entity) && player.getDistanceToEntityBox(entity) <= getRange(
                        entity
                    )
                ) {
                    attackEntity(entity, isLastClick)

                    targets += 1
                    if (limitedMultiTargets != 0 && limitedMultiTargets <= targets) break

                }
            }
        }
        if (!isLastClick)
            return

        val switchMode = targetMode == "Switch"

        if (!switchMode || switchTimer.hasTimePassed(switchDelay)) {
            prevTargetEntities += currentTarget.entityId

            if (switchMode) {
                switchTimer.reset()
            }
        }

        if (manipulateInventory) serverOpenInventory = true
    }


    /**
     * Update current target
     */
    private fun updateTarget() {
        if (KillAura.shouldPrioritize()) return
        // Reset fixed target to null
        target = null

        val switchMode = targetMode == "Switch"

        val theWorld = mc.theWorld ?: return
        val thePlayer = mc.thePlayer ?: return

        var bestTarget: EntityLivingBase? = null
        var bestValue: Double? = null

        for (entity in theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase || !EntityUtils.isSelected(
                    entity, true
                ) || switchMode && entity.entityId in prevTargetEntities
            ) continue

            var distance = 0.0
            Backtrack.runWithNearestTrackedDistance(entity) {
                distance = thePlayer.getDistanceToEntityBox(entity)
            }
            if (switchMode && distance > range && prevTargetEntities.isNotEmpty()) continue

            val entityFov = rotationDifference(entity)

            if (distance > KillAura.maxRange || fov != 180F && entityFov > fov) continue

            if (switchMode && !EntityUtils.isLookingOnEntities(entity, maxSwitchFOV.toDouble())) continue

            val currentValue = when (priority.lowercase()) {
                "distance" -> distance
                "direction" -> entityFov.toDouble()
                "health" -> entity.health.toDouble()
                "livingtime" -> -entity.ticksExisted.toDouble()
                "armor" -> entity.totalArmorValue.toDouble()
                "hurtresistance" -> entity.hurtResistantTime.toDouble()
                "hurttime" -> entity.hurtTime.toDouble()
                "healthabsorption" -> (entity.health + entity.absorptionAmount).toDouble()
                "regenamplifier" -> if (entity.isPotionActive(Potion.regeneration)) {
                    entity.getActivePotionEffect(Potion.regeneration).amplifier.toDouble()
                } else -1.0

                "inweb" -> if (entity.isInWeb) -1.0 else Double.MAX_VALUE
                "onladder" -> if (entity.isOnLadder) -1.0 else Double.MAX_VALUE
                "inliquid" -> if (entity.isInWater || entity.isInLava) -1.0 else Double.MAX_VALUE
                else -> null
            } ?: continue

            if (bestValue == null || currentValue < bestValue) {
                bestValue = currentValue as Double?
                bestTarget = entity
            }
        }



        if (bestTarget != null) {
            var success = false

            Backtrack.runWithNearestTrackedDistance(bestTarget) {
                success = updateRotations(bestTarget)
            }

            if (success) {
                target = bestTarget
                return
            }
        }

        if (prevTargetEntities.isNotEmpty()) {
            prevTargetEntities.clear()
            updateTarget()
        }
    }

    /**
     * Check if [entity] is selected as enemy with current target options and other modules
     */
    private fun isEnemy(entity: Entity?): Boolean {
        return isSelected(entity, true)
    }

    /**
     * Attack [entity]
     */

    private fun attackEntity(entity: EntityLivingBase, isLastClick: Boolean) {
        val thePlayer = mc.thePlayer

        if (shouldPrioritize())
            return

        if (thePlayer.isBlocking && (autoBlock == "Off" && blockStatus || autoBlock == "Packet" && releaseAutoBlock)) {
            stopBlocking()

            if (!ignoreTickRule || autoBlock == "Off") {
                return
            }
        }

        // The function is only called when we are facing an entity
        if (shouldDelayClick(MovingObjectPosition.MovingObjectType.ENTITY)) {
            return
        }

        if (!blinkAutoBlock || !BlinkUtils.isBlinking) {
            val affectSprint = false.takeIf { KeepSprint.handleEvents() || keepSprint }

            thePlayer.attackEntityWithModifiedSprint(entity, affectSprint) { if (swing) thePlayer.swingItem() }

            // Apply enchantment critical effect if FakeSharp is enabled
            if (EnchantmentHelper.getModifierForCreature(
                    thePlayer.heldItem,
                    entity.creatureAttribute
                ) <= 0F && fakeSharp
            ) {
                thePlayer.onEnchantmentCritical(entity)
            }
        }

        if (autoBlock == "BlocksMC" && (!blinked || !BlinkUtils.isBlinking)) {
            if (blocksmcJohnState) {
                // 发送攻击包
                sendPacket(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))

                blocksmcClickCounter++
                if (blocksmcClickCounter > blocksmcAttackInterval && Math.random() > 0.5) {
                    blocksmcClickCounter = 0
                }
            } else {
                if (blocksmcBlockRate > 0 && nextInt(100) <= blocksmcBlockRate) {
                    sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
                }
            }
            blocksmcJohnState = !blocksmcJohnState
        }
        // Start blocking after attack
        if (autoBlock != "Off" && (thePlayer.isBlocking || canBlock) && (!blinkAutoBlock && isLastClick || blinkAutoBlock && (!blinked || !BlinkUtils.isBlinking))) {
            startBlocking(entity, interactAutoBlock, autoBlock == "Fake")
        }
        if (autoBlock != "Off" && slotChangeAutoBlock && (!blinked || !BlinkUtils.isBlinking)) {
            if (autoBlock == "QuickMarco") {
                sendOffHandUseItem()
            } else if (autoBlock == "Packet") {
                sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
            }
            slotChangeAutoBlock = false
//            chat("发送防砍包")
        }
        resetLastAttackedTicks()
    }

    /**
     * Update rotations to enemy
     */
    private fun updateRotations(entity: Entity): Boolean {
        val player = mc.thePlayer ?: return false

        if (shouldPrioritize())
            return false

        if (!options.rotationsActive) {
            return player.getDistanceToEntityBox(entity) <= range
        }

        val (predictX, predictY, predictZ) = entity.currPos.subtract(entity.prevPos)
            .times(2 + predictEnemyPosition.toDouble())

        val boundingBox = entity.hitBox.offset(predictX, predictY, predictZ)
        val (currPos, oldPos) = player.currPos to player.prevPos

        val simPlayer = SimulatedPlayer.fromClientPlayer(player.movementInput)

        var pos = currPos

        (0..predictClientMovement + 1).forEach { i ->
            val previousPos = simPlayer.pos

            simPlayer.tick()

            if (predictOnlyWhenOutOfRange) {
                player.setPosAndPrevPos(simPlayer.pos)

                val currDist = player.getDistanceToEntityBox(entity)

                player.setPosAndPrevPos(previousPos)

                val prevDist = player.getDistanceToEntityBox(entity)

                player.setPosAndPrevPos(currPos, oldPos)
                pos = simPlayer.pos

                if (currDist <= range && currDist <= prevDist) {
                    return@forEach
                }
            }

            pos = previousPos
        }

        player.setPosAndPrevPos(pos)

        val rotation = searchCenter(
            boundingBox,
            outborder && !attackTimer.hasTimePassed(attackDelay / 2),
            randomization,
            predict = false,
            lookRange = range + scanRange,
            attackRange = range,
            throughWallsRange = throughWallsRange,
            bodyPoints = listOf(highestBodyPointToTarget, lowestBodyPointToTarget),
            horizontalSearch = minHorizontalBodySearch.get()..maxHorizontalBodySearch.get()
        )

        if (rotation == null) {
            player.setPosAndPrevPos(currPos, oldPos)

            return false
        }

        setTargetRotation(rotation, options = options)

        player.setPosAndPrevPos(currPos, oldPos)

        return true
    }

    private fun ticksSinceClick() = runTimeTicks - (attackTickTimes.lastOrNull()?.second ?: 0)

    /**
     * Check if enemy is hittable with current rotations
     */
    private fun updateHittable() {
        val eyes = mc.thePlayer.eyes

        val currentRotation = currentRotation ?: mc.thePlayer.rotation
        val target = this.target ?: return

        if (shouldPrioritize())
            return

        if (!options.rotationsActive) {
            hittable = mc.thePlayer.getDistanceToEntityBox(target) <= range
            return
        }

        var chosenEntity: Entity? = null

        if (raycast) {
            chosenEntity = raycastEntity(
                range.toDouble(),
                currentRotation.yaw,
                currentRotation.pitch
            ) { entity -> !livingRaycast || entity is EntityLivingBase && entity !is EntityArmorStand }

            if (chosenEntity != null && chosenEntity is EntityLivingBase && (NoFriends.handleEvents() || !(chosenEntity is EntityPlayer && chosenEntity.isClientFriend()))) {
                if (raycastIgnored && target != chosenEntity) {
                    this.target = chosenEntity
                }
            }

            hittable = this.target == chosenEntity
        } else {
            hittable = isRotationFaced(target, range.toDouble(), currentRotation)
        }

        var shouldExcept = false

        chosenEntity ?: this.target?.run {
            if (ForwardTrack.handleEvents()) {
                ForwardTrack.includeEntityTruePos(this) {
                    checkIfAimingAtBox(this, currentRotation, eyes, onSuccess = {
                        hittable = true

                        shouldExcept = true
                    })
                }
            }
        }

        if (!hittable || shouldExcept) {
            return
        }

        val targetToCheck = chosenEntity ?: this.target ?: return

        // If player is inside entity, automatic yes because the intercept below cannot check for that
        // Minecraft does the same, see #EntityRenderer line 353
        if (targetToCheck.hitBox.isVecInside(eyes)) {
            return
        }

        var checkNormally = true

        if (Backtrack.handleEvents()) {
            Backtrack.loopThroughBacktrackData(targetToCheck) {
                var result = false

                checkIfAimingAtBox(targetToCheck, currentRotation, eyes, onSuccess = {
                    checkNormally = false

                    result = true
                }, onFail = {
                    result = false
                })

                return@loopThroughBacktrackData result
            }
        } else if (ForwardTrack.handleEvents()) {
            ForwardTrack.includeEntityTruePos(targetToCheck) {
                checkIfAimingAtBox(targetToCheck, currentRotation, eyes, onSuccess = { checkNormally = false })
            }
        }

        if (!checkNormally) {
            return
        }

        // Recreate raycast logic
        val intercept = targetToCheck.hitBox.calculateIntercept(
            eyes,
            eyes + getVectorForRotation(currentRotation) * range.toDouble()
        )

        // Is the entity box raycast vector visible? If not, check through-wall range
        hittable =
            isVisible(intercept.hitVec) || mc.thePlayer.getDistanceToEntityBox(targetToCheck) <= throughWallsRange
    }
    private fun handleBlocksMC_A(target: EntityLivingBase) {
        when (asw) {
            0 -> {
                // 阶段1：释放阻挡并开始blinking
                if (blockStatus) {
                    sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                    sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
                }
                blockStatus = true
                blinking = true
                blinkTicks = 0
                if (attack < 4) attack = 4
                attack++
                blockTick++
                asw = 1
            }
            1 -> {
                // 阶段2：攻击并释放blinking
                if (isTargetInRange(target, range + 2.0) && blockTick < 8) {
                    attackEntity(target, true)
                    lastAttackTime = System.currentTimeMillis()
                }
                blinking = false
                releaseBlinkPackets()
                asw = 2
            }
            2 -> {
                // 阶段3：再次攻击并重置状态
                if (isTargetInRange(target, range.toDouble()) && attack < 16) {
                    attackEntity(target, true)
                    lastAttackTime = System.currentTimeMillis()
                }
                if (blockTick >= 8) blockTick = 0
                if (attack >= 16) attack = 4
                sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
                asw = 0
            }
        }
    }


    private fun handleBlocksMC_B(target: EntityLivingBase) {
        when (blockTickB) {
            0 -> {
                // 阶段1：初始化攻击
                blinking = true
                blinkTicks = 0
                sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                attack++
                blockStatus = false
                blockTickB++
            }
            1 -> {
                // 阶段2：第一次攻击
                if (attack < 7) {
                    if (isTargetInRange(target)) {
                        attackEntity(target, true)
                    } else {
                        sendPacket(C0APacketAnimation())
                    }
                }
                blockTickB++
            }
            2 -> {
                // 阶段3：等待
                blockTickB++
            }
            3 -> {
                // 阶段4：第二次攻击并重置
                if (attack < 7) {
                    if (isTargetInRange(target)) {
                        attackEntity(target, true)
                    } else {
                        sendPacket(C0APacketAnimation())
                    }
                }
                sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
                BlinkUtils.unblink()
                blinking = false
                releaseBlinkPackets()
                if (attack >= 7) attack = 0
                blockStatus = true
                lastAttackTime = System.currentTimeMillis()
                blockTickB = 0
            }
        }
    }


    private fun handleNCP(target: EntityLivingBase) {
        // Pre-motion逻辑
        sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
        blockStatus = false

        // Post-motion逻辑
        if (isTargetInRange(target)) {
            sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer.heldItem))
            blockStatus = true
        }
    }

    private fun isTargetInRange(target: EntityLivingBase, customRange: Double = range.toDouble()): Boolean {
        return mc.thePlayer.getDistanceToEntityBox(target) <= customRange
    }

    /**
     * Start blocking
     */
    private fun startBlocking(interactEntity: Entity, interact: Boolean, fake: Boolean = false) {
        val player = mc.thePlayer ?: return

        if (blockStatus && (!uncpAutoBlock || !blinkAutoBlock) || shouldPrioritize())
            return

        if (mc.thePlayer.isBlocking) {
            blockStatus = true
            renderBlocking = true
            return
        }

        if (!fake) {
            if (!(blockRate > 0 && nextInt(endExclusive = 100) <= blockRate)) return

            if (interact) {
                val positionEye = player.eyes

                val boundingBox = interactEntity.hitBox

                val (yaw, pitch) = currentRotation ?: player.rotation

                val vec = getVectorForRotation(Rotation(yaw, pitch))

                val lookAt = positionEye.add(vec * maxRange.toDouble())

                val movingObject = boundingBox.calculateIntercept(positionEye, lookAt) ?: return
                val hitVec = movingObject.hitVec

                sendPackets(
                    C02PacketUseEntity(interactEntity, hitVec - interactEntity.positionVector),
                    C02PacketUseEntity(interactEntity, INTERACT)
                )

            }

            if (switchStartBlock) {
                switchToSlot((SilentHotbar.currentSlot + 1) % 9)
            }

            sendPacket(C08PacketPlayerBlockPlacement(player.heldItem))
            blockStatus = true
        }

        renderBlocking = true

        CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
    }

    /**
     * Stop blocking
     */
    private fun stopBlocking(forceStop: Boolean = false) {
        val player = mc.thePlayer ?: return
        if (autoBlock == "HypixelFull" && hypixelBlinking) {
            BlinkUtils.unblink()
            hypixelBlinking = false
            sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
        }
        if (!forceStop) {
            if (blockStatus && !mc.thePlayer.isBlocking) {

                when (unblockMode.lowercase()) {
                    "stop" -> {
                        sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                    }

                    "switch" -> {
                        switchToSlot((SilentHotbar.currentSlot + 1) % 9)
                    }

                    "empty" -> {
                        switchToSlot(player.inventory.firstEmptyStack)
                    }
                }

                blockStatus = false
            }
        } else {
            if (blockStatus) {
                sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            }

            blockStatus = false
        }

        renderBlocking = false

        if (autoBlock == "BlocksMC" && (forceStop || !blockStatus)) {
            sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            blocksmcJohnState = false
            blocksmcClickCounter = 0
        }
        if (autoBlock.equals("NCP", true)) {
            sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            blockStatus = false
        }
    }
    private fun release() {
        if (!blinkedPackets.isEmpty() && !b2) {
            val copy: MutableList<Packet<*>?>?
            synchronized(blinkedPackets) {
                copy = ArrayList<Packet<*>?>(blinkedPackets)
                blinkedPackets.clear()
            }
            for (packet in copy!!) {
                packet?.let { sendPacket(it) }
            }
            blinkedPackets.clear()
            b2 = true
        }
    }
    @EventTarget
    fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return
        val packet = event.packet
        if (blinking){
            when (event.packet) {
                is C02PacketUseEntity -> {
                    blinkedPackets.add(event.packet)
                    event.cancelEvent()
                }
                is C03PacketPlayer -> {
                    blinkedPackets.add(event.packet)
                    event.cancelEvent()
                }
                is C08PacketPlayerBlockPlacement -> {
                    blinkedPackets.add(event.packet)
                    event.cancelEvent()
                }
            }
        }
        combatPacket = event
        if (autoBlock == "HypixelFull" && !hypixelBlinking) {
            if (BlinkUtils.isProcessing) {
                return
            }
            BlinkUtils.isProcessing = true // 标记开始处理

            try {
                // 添加队列容量检查
                if (BlinkUtils.queuedPacketsCount < maxBlinkPackets) {
                    BlinkUtils.blink(packet, event, true, false)
                    sendPacket(C08PacketPlayerBlockPlacement(player.heldItem))
                    hypixelBlinking = true
                    hypixelBlockTicks = 0
                } else {
                    BlinkUtils.unblink() // 超过限制立即释放
                    hypixelBlinking = false
                }
            } finally {
                BlinkUtils.isProcessing = false
            }
        }
        if (autoBlock == "Off" || !blinkAutoBlock || !blinked)
            return

        if (player.isDead || player.ticksExisted < 20) {
            BlinkUtils.unblink()
            return
        }

        if (Blink.blinkingSend() || Blink.blinkingReceive()) {
            BlinkUtils.unblink()
            return
        }

        BlinkUtils.blink(packet, event)
    }

    override fun onDisable() {
        asw = 0
        blockTickB = 0
        blockTick = 0
        attack = 0
        if (blinking) {
            releaseBlinkPackets()
            blinking = false
        }
        if (blockStatus) {
            sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            blockStatus = false
        }
        target = null
    }
    /**
     * Checks if raycast landed on a different object
     *
     * The game requires at least 1 tick of cool-down on raycast object type change (miss, block, entity)
     * We are doing the same thing here but allow more cool-down.
     */
    private fun shouldDelayClick(currentType: MovingObjectPosition.MovingObjectType): Boolean {
        if (!useHitDelay) {
            return false
        }

        val lastAttack = attackTickTimes.lastOrNull()

        return lastAttack != null && lastAttack.first.typeOfHit != currentType && runTimeTicks - lastAttack.second <= hitDelayTicks
    }

    private fun checkIfAimingAtBox(
        targetToCheck: Entity, currentRotation: Rotation, eyes: Vec3, onSuccess: () -> Unit,
        onFail: () -> Unit = { },
    ) {
        if (targetToCheck.hitBox.isVecInside(eyes)) {
            onSuccess()
            return
        }

        // Recreate raycast logic
        val intercept = targetToCheck.hitBox.calculateIntercept(
            eyes,
            eyes + getVectorForRotation(currentRotation) * range.toDouble()
        )

        if (intercept != null) {
            // Is the entity box raycast vector visible? If not, check through-wall range
            hittable =
                isVisible(intercept.hitVec) || mc.thePlayer.getDistanceToEntityBox(targetToCheck) <= throughWallsRange

            if (hittable) {
                onSuccess()
                return
            }
        }

        onFail()
    }

    private fun switchToSlot(slot: Int) {
        SilentHotbar.selectSlotSilently(this, slot, immediate = true)
        SilentHotbar.resetSlot(this, true)
    }

    private fun shouldPrioritize(): Boolean = when {
        !onScaffold && (Scaffold.handleEvents() && (Scaffold.placeRotation != null || currentRotation != null) ||
                Tower.handleEvents() && Tower.isTowering) -> true

        !onDestroyBlock && (Fucker.handleEvents() && !Fucker.noHit && Fucker.pos != null || Nuker.handleEvents()) -> true
        else -> false
    }
    object CombatListener : Listenable {
        private var totalPlayed = 0
        var win = 0
        private var syncEntity: EntityLivingBase? = null
        private var startTime = System.currentTimeMillis()
        var killCounts = 0

        @EventTarget
        private fun onAttack(event: AttackEvent) {
            syncEntity = event.targetEntity as? EntityLivingBase
            startTime = System.currentTimeMillis()
            println("onAttack: Target entity set to $syncEntity")
        }

        @EventTarget
        private fun onUpdate(event: UpdateEvent) {
            val e = syncEntity ?: return

            if (e is EntityPlayer && e.health <= 0) {
                println("Player ${e.name} killed.")
                killCounts++
                playKillSound()
                syncEntity = null
                return
            }

            if (e.deathTime > 0) {
                println("Entity ${e.name} killed.")
                killCounts++
                playKillSound()
                syncEntity = null
                return
            }

            if (System.currentTimeMillis() - startTime > 3000) {
                println("Timeout: Entity $syncEntity reset.")
                syncEntity = null
            }
        }

        @EventTarget(ignoreCondition = true)
        private fun onPacket(event: PacketEvent) {
            val packet = event.packet
            if (packet is S45PacketTitle) {
                if (packet.type == S45PacketTitle.Type.TITLE) {
                    packet.message?.formattedText?.let { title ->
                        if (title.contains("Winner")) {
                            win++
                        }
                        if (title.contains("BedWar") || title.contains("SkyWar")) {
                            totalPlayed++
                        }
                    }
                }
            }
        }


        override fun handleEvents() = true

        init {
            LiquidBounce.eventManager.registerListener(this)
        }
    }

    private fun handleFailedSwings() {
        if (!renderBoxOnSwingFail)
            return

        val box = AxisAlignedBB(0.0, 0.0, 0.0, 0.05, 0.05, 0.05)

        synchronized(swingFails) {
            val fadeSeconds = renderBoxFadeSeconds * 1000L
            val colorSettings = renderBoxColor

            val renderManager = mc.renderManager

            swingFails.removeAll {
                val timestamp = System.currentTimeMillis() - it.startTime
                val transparency = (0f..255f).lerpWith(1 - (timestamp / fadeSeconds).coerceAtMost(1.0F))

                val (posX, posY, posZ) = it.vec3
                val (x, y, z) = it.vec3 - renderManager.renderPos

                val offsetBox = box.offset(posX, posY, posZ).offset(-posX, -posY, -posZ).offset(x, y, z)

                RenderUtils.drawAxisAlignedBB(offsetBox, colorSettings.color(a = transparency.roundToInt()))

                timestamp > fadeSeconds
            }
        }
    }

    /**
     * Check if run should be cancelled
     */
    private val cancelRun
        inline get() = mc.thePlayer.isSpectator || !isAlive(mc.thePlayer) || (noConsumeAttack == "NoRotation" && isConsumingItem())

    /**
     * Check if [entity] is alive
     */
    private fun isAlive(entity: EntityLivingBase) = entity.isEntityAlive && entity.health > 0

    /**
     * Check if player is able to block (BlocksMC模式调整)
     */
    private val canBlock: Boolean
        get() {
            val player = mc.thePlayer ?: return false

            if (target != null && player.heldItem?.item is ItemSword) {
                if (smartAutoBlock) {
                    if (!player.isMoving && forceBlock) return true

                    if (checkWeapon && (target!!.heldItem?.item !is ItemSword && target!!.heldItem?.item !is ItemAxe))
                        return false

                    if (player.hurtTime > maxOwnHurtTime) return false

                    val rotationToPlayer = toRotation(player.hitBox.center, true, target!!)

                    if (rotationDifference(rotationToPlayer, target!!.rotation) > maxDirectionDiff)
                        return false

                    if (target!!.swingProgressInt > maxSwingProgress) return false

                    if (target!!.getDistanceToEntityBox(player) > blockRange) return false
                }

                if (player.getDistanceToEntityBox(target!!) > blockMaxRange) return false

                return true
            }

            // BlocksMC模式跳过常规检查
            if (autoBlock == "BlocksMC") return true

            return false
        }
    init {
        CombatListener.handleEvents()
        println("CombatListener registered.")
        CombatListener.killCounts = 0
    }
    /**
     * Range
     */
    private val maxRange
        get() = max(range + scanRange, throughWallsRange)

    private fun getRange(entity: Entity) =
        (if (mc.thePlayer.getDistanceToEntityBox(entity) >= throughWallsRange) range + scanRange else throughWallsRange) - if (mc.thePlayer.isSprinting) rangeSprintReduction else 0F

    /**
     * HUD Tag
     */
    override val tag
        get() = "$targetMode->$autoBlock"

    val isBlockingChestAura
        get() = ChestAura.handleEvents() && target != null
}

data class SwingFailData(val vec3: Vec3, val startTime: Long)

