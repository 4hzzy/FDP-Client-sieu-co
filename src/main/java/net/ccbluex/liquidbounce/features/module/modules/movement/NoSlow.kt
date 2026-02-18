package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.FDPClient
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.MovementUtils
import net.ccbluex.liquidbounce.utils.PacketUtils
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.features.value.BoolValue
import net.ccbluex.liquidbounce.features.value.FloatValue
import net.ccbluex.liquidbounce.features.value.IntegerValue
import net.ccbluex.liquidbounce.features.value.ListValue
import net.minecraft.item.*
import net.minecraft.network.Packet
import net.minecraft.network.play.INetHandlerPlayServer
import net.minecraft.network.play.client.*
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S09PacketHeldItemChange
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import java.util.*
import kotlin.math.sqrt

@ModuleInfo(name = "NoSlow", category = ModuleCategory.MOVEMENT)
object NoSlow : Module() {

    private val modeValue = ListValue(
        "PacketMode", arrayOf(
            "Vanilla", "LiquidBounce", "Custom", "WatchDogBlink", "WatchDog", "WatchDog2",
            "NCP", "AAC", "AAC4", "AAC5", "SwitchItem", "Matrix", "Vulcan", "Medusa",
            "OldIntave", "GrimAC"
        ), "Vanilla"
    )
    private val antiSwitchItem = BoolValue("AntiSwitchItem", false)
    private val onlyGround = BoolValue("OnlyGround", false)
    private val onlyMove = BoolValue("OnlyMove", false)

    private val blockModifyValue = BoolValue("Blocking", true)
    private val blockForwardMultiplier = FloatValue("BlockForwardMultiplier", 1.0F, 0.2F, 1.0F).displayable { blockModifyValue.get() }
    private val blockStrafeMultiplier = FloatValue("BlockStrafeMultiplier", 1.0F, 0.2F, 1.0F).displayable { blockModifyValue.get() }

    private val consumeModifyValue = BoolValue("Consume", true)
    private val consumePacketValue = ListValue(
        "ConsumePacket",
        arrayOf("None", "AAC5", "SpamItemChange", "SpamPlace", "SpamEmptyPlace", "Glitch", "Packet"),
        "None"
    ).displayable { consumeModifyValue.get() }
    private val consumeTimingValue = ListValue("ConsumeTiming", arrayOf("Pre", "Post"), "Pre").displayable { consumeModifyValue.get() }
    private val consumeForwardMultiplier = FloatValue("ConsumeForwardMultiplier", 1.0F, 0.2F, 1.0F).displayable { consumeModifyValue.get() }
    private val consumeStrafeMultiplier = FloatValue("ConsumeStrafeMultiplier", 1.0F, 0.2F, 1.0F).displayable { consumeModifyValue.get() }

    private val bowModifyValue = BoolValue("Bow", true)
    private val bowPacketValue = ListValue(
        "BowPacket",
        arrayOf("None", "AAC5", "SpamItemChange", "SpamPlace", "SpamEmptyPlace", "Glitch", "Packet"),
        "None"
    ).displayable { bowModifyValue.get() }
    private val bowTimingValue = ListValue("BowTiming", arrayOf("Pre", "Post"), "Pre").displayable { bowModifyValue.get() }
    private val bowForwardMultiplier = FloatValue("BowForwardMultiplier", 1.0F, 0.2F, 1.0F).displayable { bowModifyValue.get() }
    private val bowStrafeMultiplier = FloatValue("BowStrafeMultiplier", 1.0F, 0.2F, 1.0F).displayable { bowModifyValue.get() }

    private val sneakModifyValue = BoolValue("Sneaking", true)
    private val sneakPacketValue = ListValue(
        "SneakPacket",
        arrayOf("None", "SpamSneak", "SpamItemChange", "Glitch"),
        "None"
    ).displayable { sneakModifyValue.get() }
    private val sneakTimingValue = ListValue("SneakTiming", arrayOf("Pre", "Post"), "Pre").displayable { sneakModifyValue.get() }

    private val customOnGround = BoolValue("CustomOnGround", false).displayable { modeValue.equals("Custom") }
    private val customDelayValue = IntegerValue("CustomDelay", 60, 10, 200).displayable { modeValue.equals("Custom") }
    val soulSandValue = BoolValue("SoulSand", true)

    private val c07Value = BoolValue("AAC4-C07", true).displayable { modeValue.equals("AAC4") }
    private val c08Value = BoolValue("AAC4-C08", true).displayable { modeValue.equals("AAC4") }
    private val groundValue = BoolValue("AAC4-OnGround", true).displayable { modeValue.equals("AAC4") }

    private val teleportValue = BoolValue("Teleport", false)
    private val teleportModeValue = ListValue(
        "TeleportMode",
        arrayOf("Vanilla", "VanillaNoSetback", "Custom", "Decrease"),
        "Vanilla"
    ).displayable { teleportValue.get() }
    private val teleportNoApplyValue = BoolValue("TeleportNoApply", false).displayable { teleportValue.get() }
    private val teleportCustomSpeedValue = FloatValue("Teleport-CustomSpeed", 0.13f, 0f, 1f).displayable {
        teleportValue.get() && teleportModeValue.equals("Custom")
    }
    private val teleportCustomYValue = BoolValue("Teleport-CustomY", false).displayable {
        teleportValue.get() && teleportModeValue.equals("Custom")
    }
    private val teleportDecreasePercentValue = FloatValue("Teleport-DecreasePercent", 0.13f, 0f, 1f).displayable {
        teleportValue.get() && teleportModeValue.equals("Decrease")
    }

    private var pendingFlagApplyPacket = false
    private var lastMotionX = 0.0
    private var lastMotionY = 0.0
    private var lastMotionZ = 0.0
    private val msTimer = MSTimer()
    private var packetBuf = LinkedList<Packet<INetHandlerPlayServer>>()
    private var nextTemp = false
    private var waitC03 = false
    private var sendPacket = false
    private var lastBlockingStat = false

    override fun onDisable() {
        msTimer.reset()
        pendingFlagApplyPacket = false
        packetBuf.clear()
        nextTemp = false
        waitC03 = false
    }

    override val tag: String
        get() = modeValue.get()

    private val isBlocking: Boolean
        get() {
            val player = mc.thePlayer ?: return false
            val item = player.heldItem?.item ?: return false
            return item is ItemSword && (player.isUsingItem || FDPClient.moduleManager[KillAura::class.java]?.blockingStatus == true)
        }

    private val isSneaking: Boolean
        get() = mc.thePlayer?.isSneaking == true && !isBlocking && mc.thePlayer?.isUsingItem != true

    private fun sendC07C08(
        event: MotionEvent,
        sendC07: Boolean,
        sendC08: Boolean,
        delay: Boolean,
        delayValue: Long,
        onGround: Boolean,
        watchDog: Boolean = false
    ) {
        if (onGround && !mc.thePlayer.onGround) return

        val digging = C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos(-1, -1, -1), EnumFacing.DOWN
        )
        val blockPlace = C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem())
        val blockMent = C08PacketPlayerBlockPlacement(
            BlockPos(-1, -1, -1), 255, mc.thePlayer.inventory.getCurrentItem(), 0f, 0f, 0f
        )

        if (sendC07 && event.eventState == EventState.PRE) {
            if (!delay || msTimer.hasTimePassed(delayValue)) {
                mc.netHandler.addToSendQueue(digging)
            }
        }

        if (sendC08 && event.eventState == EventState.POST) {
            if (watchDog) {
                mc.netHandler.addToSendQueue(blockMent)
            } else if (!delay || msTimer.hasTimePassed(delayValue)) {
                mc.netHandler.addToSendQueue(blockPlace)
                if (delay) msTimer.reset()
            }
        }
    }

    private fun sendItemPacket(packetType: String) {
        val player = mc.thePlayer ?: return
        when (packetType.lowercase()) {
            "aac5" -> mc.netHandler.addToSendQueue(
                C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, player.inventory.getCurrentItem(), 0f, 0f, 0f)
            )
            "spamitemchange" -> mc.netHandler.addToSendQueue(
                C09PacketHeldItemChange(player.inventory.currentItem)
            )
            "spamplace" -> mc.netHandler.addToSendQueue(
                C08PacketPlayerBlockPlacement(player.inventory.getCurrentItem())
            )
            "spamemptyplace" -> mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement())
            "glitch" -> {
                mc.netHandler.addToSendQueue(C09PacketHeldItemChange((player.inventory.currentItem + 1) % 9))
                mc.netHandler.addToSendQueue(C09PacketHeldItemChange(player.inventory.currentItem))
            }
        }
    }

    private fun sendSneakPacket(packetType: String) {
        val player = mc.thePlayer ?: return
        when (packetType.lowercase()) {
            "spamsneak" -> {
                mc.netHandler.addToSendQueue(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SNEAKING))
                mc.netHandler.addToSendQueue(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SNEAKING))
            }
            "spamitemchange" -> {
                mc.netHandler.addToSendQueue(C09PacketHeldItemChange((player.inventory.currentItem + 1) % 9))
                mc.netHandler.addToSendQueue(C09PacketHeldItemChange(player.inventory.currentItem))
            }
            "glitch" -> {
                mc.netHandler.addToSendQueue(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SNEAKING))
                mc.netHandler.addToSendQueue(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SNEAKING))
                mc.netHandler.addToSendQueue(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SNEAKING))
                mc.netHandler.addToSendQueue(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SNEAKING))
            }
        }
    }

    @EventTarget
    fun onMotion(event: MotionEvent) {
        val player = mc.thePlayer ?: return
        mc.theWorld ?: return

        if (onlyMove.get() && !MovementUtils.isMoving()) return
        if (onlyGround.get() && !player.onGround) return

        val killAura = FDPClient.moduleManager[KillAura::class.java] ?: return
        val heldItem = player.heldItem?.item

        if (consumeModifyValue.get() && player.isUsingItem && (heldItem is ItemFood || heldItem is ItemPotion || heldItem is ItemBucketMilk)) {
            val timing = consumeTimingValue.get()
            if ((timing == "Pre" && event.eventState == EventState.PRE) || (timing == "Post" && event.eventState == EventState.POST)) {
                sendItemPacket(consumePacketValue.get())
            }
        }

        if (bowModifyValue.get() && player.isUsingItem && heldItem is ItemBow) {
            val timing = bowTimingValue.get()
            if ((timing == "Pre" && event.eventState == EventState.PRE) || (timing == "Post" && event.eventState == EventState.POST)) {
                sendItemPacket(bowPacketValue.get())
            }
        }

        if (sneakModifyValue.get() && isSneaking) {
            val timing = sneakTimingValue.get()
            if ((timing == "Pre" && event.eventState == EventState.PRE) || (timing == "Post" && event.eventState == EventState.POST)) {
                sendSneakPacket(sneakPacketValue.get())
            }
        }

        val shouldSendBlockingPacket =
            (blockModifyValue.get() && (player.isBlocking || killAura.blockingStatus) && heldItem is ItemSword) ||
                    (bowModifyValue.get() && player.isUsingItem && heldItem is ItemBow && bowPacketValue.equals("Packet")) ||
                    (consumeModifyValue.get() && player.isUsingItem && (heldItem is ItemFood || heldItem is ItemPotion || heldItem is ItemBucketMilk) && consumePacketValue.equals("Packet"))

        if (!shouldSendBlockingPacket) return

        when (modeValue.get().lowercase()) {
            "liquidbounce" -> sendC07C08(event, sendC07 = true, sendC08 = true, delay = false, delayValue = 0, onGround = false)

            "aac" -> {
                if (player.ticksExisted % 3 == 0)
                    sendC07C08(event, sendC07 = true, sendC08 = false, delay = false, delayValue = 0, onGround = false)
                else if (player.ticksExisted % 3 == 1)
                    sendC07C08(event, sendC07 = false, sendC08 = true, delay = false, delayValue = 0, onGround = false)
            }

            "aac4" -> sendC07C08(event, c07Value.get(), c08Value.get(), delay = true, delayValue = 80, onGround = groundValue.get())

            "aac5" -> {
                if (event.eventState == EventState.POST) {
                    mc.netHandler.addToSendQueue(
                        C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, player.inventory.getCurrentItem(), 0f, 0f, 0f)
                    )
                }
            }

            "custom" -> sendC07C08(event, sendC07 = true, sendC08 = true, delay = true, delayValue = customDelayValue.get().toLong(), onGround = customOnGround.get())

            "ncp" -> sendC07C08(event, sendC07 = true, sendC08 = true, delay = false, delayValue = 0, onGround = false)

            "watchdog2" -> {
                if (event.eventState == EventState.PRE)
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                else
                    mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, null, 0f, 0f, 0f))
            }

            "watchdog" -> {
                if (player.ticksExisted % 2 == 0)
                    sendC07C08(event, sendC07 = true, sendC08 = false, delay = true, delayValue = 50, onGround = true)
                else
                    sendC07C08(event, sendC07 = false, sendC08 = true, delay = false, delayValue = 0, onGround = true, watchDog = true)
            }

            "oldintave" -> {
                if (player.isUsingItem) {
                    if (event.eventState == EventState.PRE) {
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(player.inventory.currentItem % 8 + 1))
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(player.inventory.currentItem))
                    }
                    if (event.eventState == EventState.POST) {
                        mc.netHandler.addToSendQueue(
                            C08PacketPlayerBlockPlacement(player.inventoryContainer.getSlot(player.inventory.currentItem + 36).stack)
                        )
                    }
                }
            }

            "switchitem" -> {
                PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(player.inventory.currentItem % 8 + 1))
                PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(player.inventory.currentItem))
            }
        }
    }

    @EventTarget
    fun onSlowDown(event: SlowDownEvent) {
        val player = mc.thePlayer ?: return
        mc.theWorld ?: return
        if (onlyGround.get() && !player.onGround) return

        val heldItem = player.heldItem?.item

        if (isSneaking && sneakModifyValue.get()) {
            event.sneak = true
        }

        event.forward = getMultiplier(heldItem, true)
        event.strafe = getMultiplier(heldItem, false)
    }

    private fun getMultiplier(item: Item?, isForward: Boolean) = when (item) {
        is ItemFood, is ItemPotion, is ItemBucketMilk -> {
            if (consumeModifyValue.get())
                if (isForward) consumeForwardMultiplier.get() else consumeStrafeMultiplier.get()
            else 0.2F
        }
        is ItemSword -> {
            if (blockModifyValue.get())
                if (isForward) blockForwardMultiplier.get() else blockStrafeMultiplier.get()
            else 0.2F
        }
        is ItemBow -> {
            if (bowModifyValue.get())
                if (isForward) bowForwardMultiplier.get() else bowStrafeMultiplier.get()
            else 0.2F
        }
        else -> 0.2F
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val player = mc.thePlayer ?: return
        mc.theWorld ?: return
        if (onlyGround.get() && !player.onGround) return

        if (modeValue.equals("WatchDogBlink")) {
            if (msTimer.hasTimePassed(230)) {
                if (packetBuf.isNotEmpty()) {
                    PacketUtils.sendPacketNoEvent(
                        C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos(-1, -1, -1), EnumFacing.DOWN)
                    )
                    for (packet in packetBuf) {
                        PacketUtils.sendPacketNoEvent(packet)
                    }
                    packetBuf.clear()
                    PacketUtils.sendPacketNoEvent(
                        C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, player.inventory.getCurrentItem(), 0f, 0f, 0f)
                    )
                }
                msTimer.reset()
            }
        }

        if ((modeValue.equals("Matrix") || modeValue.equals("Vulcan") || modeValue.equals("GrimAC")) && (lastBlockingStat || isBlocking)) {
            if (msTimer.hasTimePassed(230) && nextTemp) {
                nextTemp = false
                if (modeValue.equals("GrimAC")) {
                    PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange((player.inventory.currentItem + 1) % 9))
                    PacketUtils.sendPacketNoEvent(C09PacketHeldItemChange(player.inventory.currentItem))
                } else {
                    PacketUtils.sendPacketNoEvent(
                        C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos(-1, -1, -1), EnumFacing.DOWN)
                    )
                }
                if (packetBuf.isNotEmpty()) {
                    var canAttack = false
                    for (packet in packetBuf) {
                        if (packet is C03PacketPlayer) canAttack = true
                        if (!((packet is C02PacketUseEntity || packet is C0APacketAnimation) && !canAttack)) {
                            PacketUtils.sendPacketNoEvent(packet)
                        }
                    }
                    packetBuf.clear()
                }
            }
            if (!nextTemp) {
                lastBlockingStat = isBlocking
                if (!isBlocking) return
                PacketUtils.sendPacketNoEvent(
                    C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, player.inventory.getCurrentItem(), 0f, 0f, 0f)
                )
                nextTemp = true
                waitC03 = modeValue.equals("Vulcan")
                msTimer.reset()
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return
        mc.theWorld ?: return
        if (onlyGround.get() && !player.onGround) return

        val packet = event.packet

        if (antiSwitchItem.get() && packet is S09PacketHeldItemChange && (player.isUsingItem || player.isBlocking)) {
            event.cancelEvent()
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(packet.heldItemHotbarIndex))
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(player.inventory.currentItem))
        }

        if (modeValue.equals("Medusa")) {
            if ((player.isUsingItem || player.isBlocking) && sendPacket) {
                PacketUtils.sendPacketNoEvent(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SPRINTING))
                sendPacket = false
            }
            if (!player.isUsingItem || !player.isBlocking) {
                sendPacket = true
            }
        }

        if (modeValue.equals("WatchDogBlink")) {
            if (packet is C03PacketPlayer || packet is C0APacketAnimation || packet is C0BPacketEntityAction || packet is C02PacketUseEntity) {
                @Suppress("UNCHECKED_CAST")
                packetBuf.add(packet as Packet<INetHandlerPlayServer>)
                event.cancelEvent()
            }
            if (packet is C07PacketPlayerDigging || packet is C08PacketPlayerBlockPlacement) {
                event.cancelEvent()
            }
        }

        if ((modeValue.equals("Matrix") || modeValue.equals("Vulcan") || modeValue.equals("GrimAC")) && nextTemp) {
            if ((packet is C07PacketPlayerDigging || packet is C08PacketPlayerBlockPlacement) && isBlocking) {
                event.cancelEvent()
            } else if (packet is C03PacketPlayer || packet is C0APacketAnimation || packet is C0BPacketEntityAction || packet is C02PacketUseEntity || packet is C07PacketPlayerDigging || packet is C08PacketPlayerBlockPlacement) {
                if (modeValue.equals("Vulcan") && waitC03 && packet is C03PacketPlayer) {
                    waitC03 = false
                    return
                }
                @Suppress("UNCHECKED_CAST")
                packetBuf.add(packet as Packet<INetHandlerPlayServer>)
                event.cancelEvent()
            }
        } else if (teleportValue.get() && packet is S08PacketPlayerPosLook) {
            pendingFlagApplyPacket = true
            lastMotionX = player.motionX
            lastMotionY = player.motionY
            lastMotionZ = player.motionZ
            when (teleportModeValue.get().lowercase()) {
                "vanillanosetback" -> {
                    val x = packet.x - player.posX
                    val y = packet.y - player.posY
                    val z = packet.z - player.posZ
                    val diff = sqrt(x * x + y * y + z * z)
                    if (diff <= 8) {
                        event.cancelEvent()
                        pendingFlagApplyPacket = false
                        PacketUtils.sendPacketNoEvent(
                            C06PacketPlayerPosLook(packet.x, packet.y, packet.z, packet.getYaw(), packet.getPitch(), player.onGround)
                        )
                    }
                }
            }
        } else if (pendingFlagApplyPacket && packet is C06PacketPlayerPosLook) {
            pendingFlagApplyPacket = false
            if (teleportNoApplyValue.get()) event.cancelEvent()
            when (teleportModeValue.get().lowercase()) {
                "vanilla", "vanillanosetback" -> {
                    player.motionX = lastMotionX
                    player.motionY = lastMotionY
                    player.motionZ = lastMotionZ
                }
                "custom" -> {
                    if (MovementUtils.isMoving()) MovementUtils.strafe(teleportCustomSpeedValue.get())
                    if (teleportCustomYValue.get()) {
                        player.motionY = if (lastMotionY > 0) teleportCustomSpeedValue.get().toDouble()
                        else -teleportCustomSpeedValue.get().toDouble()
                    }
                }
                "decrease" -> {
                    player.motionX = lastMotionX * teleportDecreasePercentValue.get()
                    player.motionY = lastMotionY * teleportDecreasePercentValue.get()
                    player.motionZ = lastMotionZ * teleportDecreasePercentValue.get()
                }
            }
        }
    }
}