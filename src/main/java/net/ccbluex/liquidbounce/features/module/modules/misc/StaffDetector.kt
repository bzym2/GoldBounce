/*
* LiquidBounce Hacked Client
* A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
* https://github.com/CCBlueX/LiquidBounce/
*/
package net.ccbluex.liquidbounce.features.module.modules.misc

import kotlinx.coroutines.*
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_CLOUD
import net.ccbluex.liquidbounce.LiquidBounce.hud
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.WorldEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.utils.chat
import net.ccbluex.liquidbounce.utils.misc.HttpUtils
import net.ccbluex.liquidbounce.value.ListValue
import net.ccbluex.liquidbounce.value.boolean
import net.ccbluex.liquidbounce.value.choices
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Items
import net.minecraft.network.Packet
import net.minecraft.network.play.server.*
import java.util.concurrent.ConcurrentHashMap

object StaffDetector : Module("StaffDetector", Category.MISC, gameDetecting = false, hideModule = false) {

    private val staffMode by object : ListValue(
        "StaffMode", arrayOf(
            "BlocksMC", "CubeCraft", "Gamster",
            "AgeraPvP", "HypeMC", "Hypixel",
            "SuperCraft", "PikaNetwork", "GommeHD",
            "CoralMC", "LibreCraft"
        ), "BlocksMC"
    ) {
        override fun onUpdate(value: String) {
            loadStaffData()
        }
    }

    private val tab by boolean("TAB", true)
    private val packet by boolean("Packet", true)
    private val velocity by boolean("Velocity", true)

    private val autoLeave by choices("AutoLeave", arrayOf("Off", "Leave", "Lobby", "Quit"), "Off") { tab || packet }

    private val spectator by boolean("StaffSpectator", false) { tab || packet }
    private val otherSpectator by boolean("OtherSpectator", false) { tab || packet }

    private val inGame by boolean("InGame", true) { autoLeave != "Off" }
    private val warn by choices("Warn", arrayOf("Chat", "Notification"), "Chat")

    private val checkedStaff = ConcurrentHashMap.newKeySet<String>()
    private val checkedSpectator = ConcurrentHashMap.newKeySet<String>()
    private val playersInSpectatorMode = ConcurrentHashMap.newKeySet<String>()

    private var attemptLeave = false

    private var staffList = mapOf<String, Set<String>?>()
    private var serverIp = ""

    private val moduleJob = SupervisorJob()
    private val moduleScope = CoroutineScope(Dispatchers.IO + moduleJob)

    override fun onDisable() {
        serverIp = ""
        moduleJob.cancel()
        checkedStaff.clear()
        checkedSpectator.clear()
        playersInSpectatorMode.clear()
        attemptLeave = false
    }

    /**
     * Reset on World Change
     */
    @EventTarget
    fun onWorld(event: WorldEvent) {
        checkedStaff.clear()
        checkedSpectator.clear()
        playersInSpectatorMode.clear()
    }

    private fun loadStaffData() {
        val serverIpMap = mapOf(
            "blocksmc" to "blocksmc.com",
            "cubecraft" to "cubecraft.net",
            "gamster" to "gamster.org",
            "agerapvp" to "agerapvp.club",
            "hypemc" to "hypemc.pro",
            "hypixel" to "hypixel.net",
            "supercraft" to "supercraft.es",
            "pikanetwork" to "pika-network.net",
            "gommehd" to "gommehd.net",
            "coralmc" to "coralmc.it",
            "librecraft" to "librecraft.com"
        )

        serverIp = serverIpMap[staffMode.lowercase()] ?: return

        moduleScope.launch {
            staffList = loadStaffList("$CLIENT_CLOUD/staffs/$serverIp")
        }
    }

    private fun checkedStaffRemoved() {
        mc.netHandler?.playerInfoMap?.mapNotNullTo(hashSetOf()) { it?.gameProfile?.name }?.let(checkedStaff::retainAll)
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return
        }

        val packet = event.packet

        /**
         * OLD BlocksMC Staff Spectator Check
         * Credit: @HU & Modified by @EclipsesDev
         *
         * NOTE: Doesn't detect staff spectator all the time.
         */
        if (spectator) {
            if (packet is S3EPacketTeams) {
                val teamName = packet.name

                if (teamName.equals("Z_Spectator", true)) {
                    val players = packet.players ?: return

                    val staffSpectateList = players.filter { it in staffList.keys } - checkedSpectator
                    val nonStaffSpectateList = players.filter { it !in staffList.keys } - checkedSpectator

                    // Check for players who are using spectator menu
                    val miscSpectatorList = playersInSpectatorMode - players.toSet()

                    staffSpectateList.forEach { player ->
                        notifySpectators(player!!)
                    }

                    nonStaffSpectateList.forEach { player ->
                        if (otherSpectator) {
                            notifySpectators(player!!)
                        }
                    }

                    miscSpectatorList.forEach { player ->
                        val isStaff = player in staffList

                        if (isStaff && spectator) {
                            chat("§c[STAFF] §d${player} §3is using the spectator menu §e(compass/left)")
                        }

                        if (!isStaff && otherSpectator) {
                            chat("§d${player} §3is using the spectator menu §e(compass/left)")
                        }
                        checkedSpectator.remove(player)
                    }

                    // Update the set of players in spectator mode
                    playersInSpectatorMode.clear()
                    playersInSpectatorMode.addAll(players)
                }
            }

            // Handle other packets
            handleOtherChecks(packet)
        }

        /**
         * Velocity Check
         * Credit: @azureskylines / Nextgen
         *
         * Check if this is a regular velocity update
         */
        if (velocity) {
            if (packet is S12PacketEntityVelocity && packet.entityID == mc.thePlayer?.entityId) {
                if (packet.motionX == 0 && packet.motionZ == 0 && packet.motionY / 8000.0 > 0.075) {
                    attemptLeave = false
                    autoLeave()

                    if (warn == "Chat") {
                        chat("§3Staff is Watching")
                    } else {
                        hud.addNotification(Notification("§3Staff is Watching", 3000F))
                    }
                }
            }
        }
    }

    private fun notifySpectators(player: String) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return
        }

        val isStaff = staffList.any { entry ->
            entry.value?.any { staffName -> player.contains(staffName) } == true
        }

        if (isStaff && spectator) {
            if (warn == "Chat") {
                chat("§c[STAFF] §d${player} §3is a spectators")
            } else {
                hud.addNotification(Notification("§c[STAFF] §d${player} §3is a spectators", 3000F))
            }
        }

        if (!isStaff && otherSpectator) {
            if (warn == "Chat") {
                chat("§d${player} §3is a spectators")
            } else {
                hud.addNotification(Notification("§d${player} §3is a spectators", 3000F))
            }
        }

        attemptLeave = false
        checkedSpectator.add(player)

        if (isStaff) {
            autoLeave()
        }
    }

    /**
     * Check staff using TAB
     */
    private fun notifyStaff() {
        if (!tab)
            return

        if (mc.thePlayer == null || mc.theWorld == null) {
            return
        }

        val playerInfoMap = mc.netHandler?.playerInfoMap ?: return

        val playerInfos = synchronized(playerInfoMap) {
            playerInfoMap.mapNotNull { playerInfo ->
                playerInfo?.gameProfile?.name?.let { playerName ->
                    playerName to playerInfo.responseTime
                }
            }
        }

        playerInfos.forEach { (player, responseTime) ->
            val isStaff = staffList.any { entry ->
                entry.value?.any { staffName -> player.contains(staffName) } == true
            }

            val condition = when {
                responseTime > 0 -> "§e(${responseTime}ms)"
                responseTime == 0 -> "§a(Joined)"
                else -> "§c(Ping error)"
            }

            val warnings = "§c[STAFF] §d${player} §3is a staff §b(TAB) $condition"

            if (isStaff && player !in checkedStaff) {
                if (warn == "Chat") {
                    chat(warnings)
                } else {
                    hud.addNotification(Notification(warnings, 3000F))
                }

                attemptLeave = false
                checkedStaff.add(player)

                autoLeave()
            }
        }
    }

    /**
     * Check staff using Packet
     */
    private fun notifyStaffPacket(staff: Entity) {
        if (!packet)
            return

        if (mc.thePlayer == null || mc.theWorld == null) {
            return
        }

        val isStaff = if (staff is EntityPlayer) {
            val playerName = staff.gameProfile.name

            staffList.any { entry ->
                entry.value?.any { staffName -> playerName.contains(staffName) } == true
            }
        } else {
            false
        }

        val condition = when (staff) {
            is EntityPlayer -> {
                val responseTime = mc.netHandler?.getPlayerInfo(staff.uniqueID)?.responseTime ?: 0
                when {
                    responseTime > 0 -> "§e(${responseTime}ms)"
                    responseTime == 0 -> "§a(Joined)"
                    else -> "§c(Ping error)"
                }
            }

            else -> ""
        }

        val playerName = if (staff is EntityPlayer) staff.gameProfile.name else ""

        val warnings = "§c[STAFF] §d${playerName} §3is a staff §b(Packet) $condition"

        if (isStaff && playerName !in checkedStaff) {
            if (warn == "Chat") {
                chat(warnings)
            } else {
                hud.addNotification(Notification(warnings, 3000F))
            }

            attemptLeave = false
            checkedStaff.add(playerName)

            autoLeave()
        }
    }

    private fun autoLeave() {
        val firstSlotItemStack = mc.thePlayer.inventory.mainInventory[0] ?: return

        if (inGame && (firstSlotItemStack.item == Items.compass || firstSlotItemStack.item == Items.bow)) {
            return
        }

        if (!attemptLeave && autoLeave != "Off") {
            when (autoLeave.lowercase()) {
                "leave" -> mc.thePlayer.sendChatMessage("/leave")
                "lobby" -> mc.thePlayer.sendChatMessage("/lobby")
                "quit" -> mc.theWorld.sendQuittingDisconnectingPacket()
            }
            attemptLeave = true
        }
    }

    private fun handleOtherChecks(packet: Packet<*>?) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return
        }

        when (packet) {
            is S01PacketJoinGame -> handleStaff(mc.theWorld.getEntityByID(packet.entityId) ?: null)
            is S0CPacketSpawnPlayer -> handleStaff(mc.theWorld.getEntityByID(packet.entityID) ?: null)
            is S18PacketEntityTeleport -> handleStaff(mc.theWorld.getEntityByID(packet.entityId) ?: null)
            is S1CPacketEntityMetadata -> handleStaff(mc.theWorld.getEntityByID(packet.entityId) ?: null)
            is S1DPacketEntityEffect -> handleStaff(mc.theWorld.getEntityByID(packet.entityId) ?: null)
            is S1EPacketRemoveEntityEffect -> handleStaff(mc.theWorld.getEntityByID(packet.entityId) ?: null)
            is S19PacketEntityStatus -> handleStaff(mc.theWorld.getEntityByID(packet.entityId) ?: null)
            is S19PacketEntityHeadLook -> handleStaff(packet.getEntity(mc.theWorld) ?: null)
            is S49PacketUpdateEntityNBT -> handleStaff(packet.getEntity(mc.theWorld) ?: null)
            is S1BPacketEntityAttach -> handleStaff(mc.theWorld.getEntityByID(packet.entityId) ?: null)
            is S04PacketEntityEquipment -> handleStaff(mc.theWorld.getEntityByID(packet.entityID) ?: null)
        }
    }

    private fun handleStaff(staff: Entity?) {
        if (mc.thePlayer == null || mc.theWorld == null || staff == null) {
            return
        }

        checkedStaffRemoved()

        notifyStaff()
        notifyStaffPacket(staff)
    }

    private suspend fun loadStaffList(url: String): Map<String, Set<String>> {
        return try {
            val (response, code) = fetchDataAsync(url)

            when (code) {
                200 -> {
                    val staffList = response.lineSequence()
                        .filter { it.isNotBlank() }
                        .map { it.trim() }
                        .toSet()

                    chat("§aSuccessfully loaded §e${staffList.size} §astaff names.")
                    mapOf(url to staffList)
                }

                404 -> {
                    chat("§cFailed to load staff list. §e(§3Doesn't exist in LiquidCloud§e)")
                    emptyMap()
                }

                else -> {
                    chat("§cFailed to load staff list. §e(§3ERROR CODE: $code§e)")
                    emptyMap()
                }
            }
        } catch (e: Exception) {
            chat("§cFailed to load staff list. §e(${e.message})")
            e.printStackTrace()
            emptyMap()
        }
    }

    private suspend fun fetchDataAsync(url: String): Pair<String, Int> {
        return withContext(Dispatchers.IO) {
            HttpUtils.request(url, "GET").let { Pair(it.first, it.second) }
        }
    }

    /**
     * HUD TAG
     */
    override val tag
        get() = staffMode
}
