/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
@file:Suppress("unused")

package net.ccbluex.liquidbounce.features.module.modules.world

import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.LiquidBounce.hud
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.AutoArmor
import net.ccbluex.liquidbounce.features.module.modules.player.InventoryCleaner.canBeSortedTo
import net.ccbluex.liquidbounce.features.module.modules.player.InventoryCleaner.isStackUseful
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.utils.reflection.ReflectionUtil
import net.ccbluex.liquidbounce.utils.SilentHotbar
import net.ccbluex.liquidbounce.utils.chat
import net.ccbluex.liquidbounce.utils.extensions.component1
import net.ccbluex.liquidbounce.utils.extensions.component2
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.canClickInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.chestStealerCurrentSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.chestStealerLastSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.countSpaceInInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.hasSpaceInInventory
import net.ccbluex.liquidbounce.utils.kotlin.CoroutineUtils.waitUntil
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.timing.TimeUtils.randomDelay
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.int
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EntityLiving.getArmorPosition
import net.minecraft.init.Items
import net.minecraft.item.*
import net.minecraft.network.play.client.C0DPacketCloseWindow
import net.minecraft.network.play.server.S2DPacketOpenWindow
import net.minecraft.network.play.server.S2EPacketCloseWindow
import net.minecraft.network.play.server.S30PacketWindowItems
import net.minecraft.potion.Potion
import net.minecraft.util.BlockPos
import java.awt.Color
import kotlin.math.sqrt

object ChestStealer : Module("ChestStealer", Category.WORLD, hideModule = false) {

    private val smartDelay by boolean("SmartDelay", false)
    private val multiplier by int("DelayMultiplier", 120, 0..500) { smartDelay }
    private val smartOrder by boolean("SmartOrder", true) { smartDelay }

    private val simulateShortStop by boolean("SimulateShortStop", false)

    private val maxDelay: Int by object : IntegerValue("MaxDelay", 50, 0..500) {
        override fun isSupported() = !smartDelay
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minDelay)
    }
    private val minDelay by object : IntegerValue("MinDelay", 50, 0..500) {
        override fun isSupported() = maxDelay > 0 && !smartDelay
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxDelay)
    }

    private val startDelay by int("StartDelay", 50, 0..500)
    private val closeDelay by int("CloseDelay", 50, 0..500)

    private val noMove by InventoryManager.noMoveValue
    private val noMoveAir by InventoryManager.noMoveAirValue
    private val noMoveGround by InventoryManager.noMoveGroundValue

    private val chestTitle by boolean("ChestTitle", true)

    private val randomSlot by boolean("RandomSlot", true)

    private val progressBar by boolean("ProgressBar", true, subjective = true)

    val silentGUI by boolean("SilentGUI", false, subjective = true)

    val highlightSlot by boolean("Highlight-Slot", false, subjective = true) { !silentGUI }

    val backgroundRed by int("Background-R", 128, 0..255, subjective = true) { highlightSlot && !silentGUI }
    val backgroundGreen by int("Background-G", 128, 0..255, subjective = true) { highlightSlot && !silentGUI }
    val backgroundBlue by int("Background-B", 128, 0..255, subjective = true) { highlightSlot && !silentGUI }
    val backgroundAlpha by int(
        "Background-Alpha",
        255,
        0..255,
        subjective = true
    ) { highlightSlot && !silentGUI }

    val borderStrength by int("Border-Strength", 3, 1..5, subjective = true) { highlightSlot && !silentGUI }
    val borderRed by int("Border-R", 128, 0..255, subjective = true) { highlightSlot && !silentGUI }
    val borderGreen by int("Border-G", 128, 0..255, subjective = true) { highlightSlot && !silentGUI }
    val borderBlue by int("Border-B", 128, 0..255, subjective = true) { highlightSlot && !silentGUI }
    val borderAlpha by int("Border-Alpha", 255, 0..255, subjective = true) { highlightSlot && !silentGUI }

    private val chestDebug by choices("Chest-Debug", arrayOf("Off", "Text", "Notification"), "Off", subjective = true)
    private val itemStolenDebug by boolean("ItemStolen-Debug", false, subjective = true) { chestDebug != "Off" }

    private var progress: Float? = null
        set(value) {
            field = value?.coerceIn(0f, 1f)

            if (field == null)
                easingProgress = 0f
        }

    private var easingProgress = 0f
    private var receivedId: Int? = null
    private var chestPos: BlockPos? = null
    private var stacks = emptyList<ItemStack?>()

    private suspend fun shouldOperate(): Boolean {
        while (true) {
            if (!handleEvents())
                return false

            if (mc.playerController?.currentGameType?.isSurvivalOrAdventure != true)
                return false

            if (mc.currentScreen !is GuiChest)
                return false

            if (mc.thePlayer?.openContainer?.windowId != receivedId)
                return false

            // Wait till NoMove check isn't violated
            if (canClickInventory())
                return true

            // If NoMove is violated, wait a tick and check again
            // If there is no delay, very weird things happen: https://www.guilded.gg/CCBlueX/groups/1dgpg8Jz/channels/034be45e-1b72-4d5a-bee7-d6ba52ba1657/chat?messageId=94d314cd-6dc4-41c7-84a7-212c8ea1cc2a
            delay(50)
        }
    }

    suspend fun stealFromChest() {
        if (!handleEvents()) return

        val thePlayer = mc.thePlayer ?: return
        mc.currentScreen as? GuiChest ?: return

        if (!shouldOperate()) return
        delay(startDelay.toLong())

        debug("Stealing items...")
        while (shouldOperate() && hasSpaceInInventory()) {
            var hasTaken = false

            val itemsToSteal = getItemsToSteal()

            itemsToSteal.forEachIndexed { index, (slot, stack, sortableTo) ->
                if (!shouldOperate() || !hasSpaceInInventory()) return@forEachIndexed

                hasTaken = true
                chestStealerCurrentSlot = slot
                val stealingDelay = if (smartDelay && index + 1 < itemsToSteal.size) {
                    val dist = squaredDistanceOfSlots(slot, itemsToSteal[index + 1].index)
                    val trueDelay = sqrt(dist.toDouble()) * multiplier
                    randomDelay(trueDelay.toInt(), trueDelay.toInt() + 20)
                } else {
                    randomDelay(minDelay, maxDelay)
                }
                if (itemStolenDebug) debug("Item: ${stack.displayName.lowercase()} | Slot: $slot | Delay: ${stealingDelay}ms")
                TickScheduler.scheduleClick(slot, sortableTo ?: 0, if (sortableTo != null) 2 else 1) {
                    progress = (index + 1) / itemsToSteal.size.toFloat()

                    if (!AutoArmor.canEquipFromChest()) return@scheduleClick

                    val item = stack.item
                    if (item is ItemArmor && thePlayer.inventory.armorInventory[getArmorPosition(stack) - 1] == null) {
                        TickScheduler += {
                            val hotbarStacks = thePlayer.inventory.mainInventory.take(9)
                            val newIndex = hotbarStacks.indexOfFirst { it?.getIsItemStackEqual(stack) == true }

                            if (newIndex != -1) AutoArmor.equipFromHotbarInChest(newIndex, stack)
                        }
                    }
                }

                delay(stealingDelay.toLong())
            }

            if (!hasTaken) {
                progress = 1f
                delay(closeDelay.toLong())
                TickScheduler += { SilentHotbar.resetSlot() }
                break
            }
            waitUntil(TickScheduler::isEmpty)
            stacks = thePlayer.openContainer.inventory  // 更新库存
        }
        TickScheduler.scheduleAndSuspend ({
            chestStealerCurrentSlot = -1
            chestStealerLastSlot = -1
            thePlayer.closeScreen()
            progress = null
            debug("Chest closed")
        })
    }


    private fun squaredDistanceOfSlots(from: Int, to: Int): Int {
        fun getCoords(slot: Int): IntArray {
            val x = slot % 9
            val y = slot / 9
            return intArrayOf(x, y)
        }

        val (x1, y1) = getCoords(from)
        val (x2, y2) = getCoords(to)
        return (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
    }

    private data class ItemTakeRecord(
        val index: Int,
        val stack: ItemStack,
        val sortableToSlot: Int?
    )

    private fun getEnchantmentScore(stack: ItemStack): Float {
        if (!stack.isItemEnchanted) return 0f
        var score = 0f
        val enchantments = EnchantmentHelper.getEnchantments(stack)
        for ((_, level) in enchantments) {
            score += level * 0.5f
        }
        return score
    }

    private fun getStackScore(stack: ItemStack): Float {
        val item = stack.item

        if (!isStackUseful(stack, stacks)) return -1f

        var score = getEnchantmentScore(stack)

        when (item) {
            is ItemArmor -> score += item.damageReduceAmount * 2.5f
            is ItemSword -> score += ReflectionUtil.getFieldValue<Float>(item,"attackDamage") * 2f
            is ItemAxe -> score += 4f // Base score for being a weapon
            is ItemTool -> score += 0.5f // Lower priority for general tools unless enchanted
            is ItemBow -> score += 5f
            is ItemPotion -> {
                if (item.getEffects(stack)?.any { Potion.potionTypes[it.potionID]?.isBadEffect == false } == true) {
                    score += 7f
                }
            }
            is ItemFood -> {
                if (item == Items.golden_apple) score += 10f else score += item.getHealAmount(stack) * 0.5f
            }
            Items.ender_pearl -> score += 8f
            Items.arrow -> score += 0.2f
            Items.slime_ball -> if (stack.hasTagCompound() && stack.tagCompound.hasKey("ench")) score += 100f // Keep special case
        }

        return score
    }

    /**
     * [MODIFIED] This function is updated to dynamically support chests of any size.
     * It calculates the chest's inventory size by subtracting the player's static inventory size (36)
     * from the total number of item stacks received from the server. This makes it adaptable to
     * single chests, double chests, and any custom-sized chests (e.g., 4-row chests).
     */
    private fun getItemsToSteal(): MutableList<ItemTakeRecord> {
        val sortBlacklist = BooleanArray(9)

        // The player's inventory size is constant (9 hotbar + 27 main = 36 slots).
        val playerInventorySize = 36
        // Calculate the chest's size dynamically. The 'stacks' list contains both chest and player items.
        val chestSize = stacks.size - playerInventorySize

        // If for some reason there are no chest slots (e.g., it's not a real container), return an empty list.
        if (chestSize <= 0) {
            return mutableListOf()
        }

        var spaceInInventory = countSpaceInInventory()
        // We now iterate only over the chest's contents, which are the first 'chestSize' items in the 'stacks' list.
        val itemsToSteal = stacks.take(chestSize)
            .mapIndexedNotNullTo(ArrayList(chestSize)) { index, stack ->
                stack ?: return@mapIndexedNotNullTo null

                if (index in TickScheduler) return@mapIndexedNotNullTo null  // Skip already scheduled slots

                val mergeableCount = mc.thePlayer.inventory.mainInventory.sumOf { otherStack ->
                    otherStack?.takeIf { it.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(stack, otherStack) }
                        ?.let { it.maxStackSize - it.stackSize } ?: 0
                }

                if (mergeableCount == 0 && spaceInInventory <= 0) return@mapIndexedNotNullTo null
                if (handleEvents() && !isStackUseful(stack, stacks, noLimits = mergeableCount >= stack.stackSize)) return@mapIndexedNotNullTo null
                var sortableTo: Int? = null
                if (handleEvents() && mergeableCount <= 0) {
                    for (hotbarIndex in 0..8) {
                        if (!canBeSortedTo(hotbarIndex, stack.item)) continue
                        val hotbarStack = stacks.getOrNull(stacks.size - 9 + hotbarIndex)
                        if (hotbarStack == null || canBeSortedTo(hotbarIndex, hotbarStack.item)) {
                            sortableTo = hotbarIndex
                            sortBlacklist[hotbarIndex] = true
                            break
                        }
                    }
                }
                if (mergeableCount < stack.stackSize) spaceInInventory--

                ItemTakeRecord(index, stack, sortableTo)
            }.also { list ->
                if (randomSlot) {
                    list.shuffle()
                } else {
                    // New score-based sorting for enchanted items and general value
                    list.sortByDescending { getStackScore(it.stack) }
                }

                if (smartOrder) {
                    sortBasedOnOptimumPath(list)
                }
            }

        return itemsToSteal
    }


    private fun sortBasedOnOptimumPath(itemsToSteal: MutableList<ItemTakeRecord>) {
        if (itemsToSteal.isEmpty()) return
        val sortedList = mutableListOf<ItemTakeRecord>()
        val remainingItems = itemsToSteal.toMutableList()

        // Start with the first item from the pre-sorted list (which is the highest value one)
        var currentItem = remainingItems.removeAt(0)
        sortedList.add(currentItem)

        while (remainingItems.isNotEmpty()) {
            var nextIndex = -1
            var minDistance = Int.MAX_VALUE
            for (i in remainingItems.indices) {
                val distance = squaredDistanceOfSlots(currentItem.index, remainingItems[i].index)
                if (distance < minDistance) {
                    minDistance = distance
                    nextIndex = i
                }
            }
            currentItem = remainingItems.removeAt(nextIndex)
            sortedList.add(currentItem)
        }
        itemsToSteal.clear()
        itemsToSteal.addAll(sortedList)
    }

    // Progress bar
    @EventTarget
    fun onRender2D(event: Render2DEvent) {
        val scaledResolution = ScaledResolution(mc)
        if (silentGUI && progress != null) {
            RenderUtils.drawLoadingCircle(
                scaledResolution.scaledWidth / 2f,
                scaledResolution.scaledHeight / 4f
            )
            return
        }

        if (!progressBar || mc.currentScreen !is GuiChest || progress == null) return

        val (scaledWidth, scaledHeight) = ScaledResolution(mc)
        val minX = scaledWidth * 0.3f
        val maxX = scaledWidth * 0.7f
        val minY = scaledHeight * 0.75f
        val maxY = minY + 10f

        easingProgress += (progress!! - easingProgress) / 6f * event.partialTicks

        drawRect(minX - 2, minY - 2, maxX + 2, maxY + 2, Color(200, 200, 200).rgb)
        drawRect(minX, minY, maxX, maxY, Color(50, 50, 50).rgb)
        drawRect(minX, minY, minX + (maxX - minX) * easingProgress, maxY, Color.HSBtoRGB(easingProgress / 5, 1f, 1f) or 0xFF0000)
    }




    @EventTarget
    fun onPacket(event: PacketEvent) {
        when (val packet = event.packet) {
            is S2DPacketOpenWindow -> {
                if (packet.guiId == "minecraft:chest") {
                    chestPos = mc.objectMouseOver?.blockPos
                }
                receivedId = null
                progress = null
            }
            is C0DPacketCloseWindow, is S2EPacketCloseWindow -> {
                receivedId = null
                progress = null
                chestPos = null
            }
            is S30PacketWindowItems -> {
                if (packet.func_148911_c() == 0) return

                if (receivedId != packet.func_148911_c()) {
                    debug("Chest opened with ${stacks.size} items")
                }

                receivedId = packet.func_148911_c()
                stacks = packet.itemStacks.toList()
            }
        }
    }


    private fun debug(message: String) {
        if (chestDebug == "Off") return

        when (chestDebug.lowercase()) {
            "text" -> chat(message)
            "notification" -> hud.addNotification(Notification(message, 500F))
        }
    }
}