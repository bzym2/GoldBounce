/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.MovementUtils.direction
import net.ccbluex.liquidbounce.utils.MovementUtils.strafe
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.block.BlockUtils
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.utils.timing.WaitTickUtils
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.floatValue
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.init.Blocks.*
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.stats.StatList
import kotlin.math.cos
import kotlin.math.sin

object Step : Module("Step", Category.MOVEMENT, gameDetecting = false, hideModule = false) {

    /**
     * OPTIONS
     */

    private val mode by choices(
        "Mode",
        arrayOf("Vanilla", "Jump", "NCP", "MotionNCP",
            "OldNCP", "AAC", "LAAC", "AAC3.3.4",
            "Spartan", "Rewinside", "BlocksMCTimer"),
        "NCP"
    )

    private val height by floatValue("Height", 1F, 0.6F..10F)
    { mode !in arrayOf("Jump", "MotionNCP", "LAAC", "AAC3.3.4", "BlocksMCTimer") }
    private val jumpHeight by floatValue("JumpHeight", 0.42F, 0.37F..0.42F)
    { mode == "Jump" }

    private val delay by intValue("Delay", 0, 0..500)

    /**
     * VALUES
     */

    private var isStep = false
    private var stepX = 0.0
    private var stepY = 0.0
    private var stepZ = 0.0

    private var ncpNextStep = 0
    private var spartanSwitch = false
    private var isAACStep = false

    private val timer = MSTimer()

    override fun onDisable() {
        val thePlayer = mc.thePlayer ?: return

        // Change step height back to default (0.6 is default)
        thePlayer.stepHeight = 0.6F
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val mode = mode
        val thePlayer = mc.thePlayer ?: return

        if (thePlayer.isOnLadder || thePlayer.isInWater || thePlayer.isInLava || thePlayer.isInWeb) return

        if (!thePlayer.isMoving) return

        // Motion steps
        when (mode) {
            "Jump" ->
                if (thePlayer.isCollidedHorizontally && thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown) {
                    fakeJump()
                    thePlayer.motionY = jumpHeight.toDouble()
                }

            "BlocksMCTimer" ->
                if (thePlayer.onGround && thePlayer.isCollidedHorizontally) {
                    val chest = BlockUtils.searchBlocks(2, setOf(chest, ender_chest, trapped_chest))

                    if (!couldStep() || chest.isNotEmpty()) {
                        mc.timer.timerSpeed = 1f
                        return
                    }

                    fakeJump()
                    thePlayer.tryJump()

                    // TODO: Improve Timer Balancing
                    mc.timer.timerSpeed = 5f
                    WaitTickUtils.schedule(1) {
                        mc.timer.timerSpeed = 0.2f
                    }
                    WaitTickUtils.schedule(2) {
                        mc.timer.timerSpeed = 4f
                    }
                    WaitTickUtils.schedule(3) {
                        strafe(0.27F)
                        mc.timer.timerSpeed = 1f
                    }
                }

            "LAAC" ->
                if (thePlayer.isCollidedHorizontally) {
                    if (thePlayer.onGround && timer.hasTimePassed(delay)) {
                        isStep = true

                        fakeJump()
                        thePlayer.motionY += 0.620000001490116

                        val yaw = direction
                        thePlayer.motionX -= sin(yaw) * 0.2
                        thePlayer.motionZ += cos(yaw) * 0.2
                        timer.reset()
                    }

                    thePlayer.onGround = true
                } else isStep = false

            "AAC3.3.4" ->
                if (thePlayer.isCollidedHorizontally && thePlayer.isMoving) {
                    if (thePlayer.onGround && couldStep()) {
                        thePlayer.motionX *= 1.26
                        thePlayer.motionZ *= 1.26
                        thePlayer.tryJump()
                        isAACStep = true
                    }

                    if (isAACStep) {
                        thePlayer.motionY -= 0.015

                        if (!thePlayer.isUsingItem && thePlayer.movementInput.moveStrafe == 0F)
                            thePlayer.jumpMovementFactor = 0.3F
                    }
                } else isAACStep = false
        }
    }

    @EventTarget
    fun onMove(event: MoveEvent) {
        val thePlayer = mc.thePlayer ?: return

        if (mode != "MotionNCP" || !thePlayer.isCollidedHorizontally || mc.gameSettings.keyBindJump.isKeyDown)
            return

        // Motion steps
        when {
            thePlayer.onGround && couldStep() -> {
                fakeJump()
                thePlayer.motionY = 0.0
                event.y = 0.41999998688698
                ncpNextStep = 1
            }

            ncpNextStep == 1 -> {
                event.y = 0.7531999805212 - 0.41999998688698
                ncpNextStep = 2
            }

            ncpNextStep == 2 -> {
                val yaw = direction

                event.y = 1.001335979112147 - 0.7531999805212
                event.x = -sin(yaw) * 0.7
                event.z = cos(yaw) * 0.7

                ncpNextStep = 0
            }
        }
    }

    @EventTarget
    fun onStep(event: StepEvent) {
        val thePlayer = mc.thePlayer ?: return

        // Phase should disable step
        if (handleEvents()) {
            event.stepHeight = 0F
            return
        }

        // Some fly modes should disable step
        if (handleEvents() && Fly.mode in arrayOf(
                "Hypixel",
                "OtherHypixel",
                "LatestHypixel",
                "Rewinside",
                "Mineplex"
            )
            && thePlayer.inventory.getCurrentItem() == null
        ) {
            event.stepHeight = 0F
            return
        }

        val mode = mode

        // Set step to default in some cases
        if (!thePlayer.onGround || !timer.hasTimePassed(delay) ||
            mode in arrayOf("Jump", "MotionNCP", "LAAC", "AAC3.3.4", "BlocksMCTimer")
        ) {
            thePlayer.stepHeight = 0.6F
            event.stepHeight = 0.6F
            return
        }

        // Set step height
        val height = height
        thePlayer.stepHeight = height
        event.stepHeight = height

        // Detect possible step
        if (event.stepHeight > 0.6F) {
            isStep = true
            stepX = thePlayer.posX
            stepY = thePlayer.posY
            stepZ = thePlayer.posZ
        }
    }

    @EventTarget(ignoreCondition = true)
    fun onStepConfirm(event: StepConfirmEvent) {
        val thePlayer = mc.thePlayer

        if (thePlayer == null || !isStep) // Check if step
            return

        if (thePlayer.entityBoundingBox.minY - stepY > 0.6) { // Check if full block step

            when (mode) {
                "NCP", "AAC" -> {
                    fakeJump()

                    // Half legit step (1 packet missing) [COULD TRIGGER TOO MANY PACKETS]
                    sendPackets(
                        C04PacketPlayerPosition(stepX, stepY + 0.41999998688698, stepZ, false),
                        C04PacketPlayerPosition(stepX, stepY + 0.7531999805212, stepZ, false)
                    )
                    timer.reset()
                }

                "Spartan" -> {
                    fakeJump()

                    if (spartanSwitch) {
                        // Vanilla step (3 packets) [COULD TRIGGER TOO MANY PACKETS]
                        sendPackets(
                            C04PacketPlayerPosition(stepX, stepY + 0.41999998688698, stepZ, false),
                            C04PacketPlayerPosition(stepX, stepY + 0.7531999805212, stepZ, false),
                            C04PacketPlayerPosition(stepX, stepY + 1.001335979112147, stepZ, false)
                        )
                    } else // Force step
                        sendPacket(C04PacketPlayerPosition(stepX, stepY + 0.6, stepZ, false))

                    // Spartan allows one unlegit step so just swap between legit and unlegit
                    spartanSwitch = !spartanSwitch

                    // Reset timer
                    timer.reset()
                }

                "Rewinside" -> {
                    fakeJump()

                    // Vanilla step (3 packets) [COULD TRIGGER TOO MANY PACKETS]
                    sendPackets(
                        C04PacketPlayerPosition(stepX, stepY + 0.41999998688698, stepZ, false),
                        C04PacketPlayerPosition(stepX, stepY + 0.7531999805212, stepZ, false),
                        C04PacketPlayerPosition(stepX, stepY + 1.001335979112147, stepZ, false)
                    )

                    // Reset timer
                    timer.reset()
                }
            }
        }

        isStep = false
        stepX = 0.0
        stepY = 0.0
        stepZ = 0.0
    }

    @EventTarget(ignoreCondition = true)
    fun onPacket(event: PacketEvent) {
        val packet = event.packet

        if (packet is C03PacketPlayer && isStep && mode == "OldNCP") {
            packet.y += 0.07
            isStep = false
        }
    }

    // There could be some anti cheats which tries to detect step by checking for achievements and stuff
    private fun fakeJump() {
        val thePlayer = mc.thePlayer ?: return

        thePlayer.isAirBorne = true
        thePlayer.triggerAchievement(StatList.jumpStat)
    }

    private fun couldStep(): Boolean {
        val player = mc.thePlayer ?: return false

        if (player.isSneaking || mc.gameSettings.keyBindJump.isKeyDown)
            return false

        val yaw = direction
        val heightOffset = 1.001335979112147

        for (i in -10..10) {
            val adjustedYaw = yaw + (i * Math.toRadians(8.0))
            val x = -sin(adjustedYaw) * 0.2
            val z = cos(adjustedYaw) * 0.2

            if (mc.theWorld.getCollisionBoxes(mc.thePlayer.entityBoundingBox.offset(x, heightOffset, z)).isNotEmpty()) {
                return false
            }
        }

        return true
    }

    override val tag
        get() = mode
}