package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.utils.misc.RandomUtils
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.features.value.*
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C03PacketPlayer.*

@ModuleInfo(name = "Knockback", category = ModuleCategory.COMBAT)
object SuperKnockback : Module() {

    private val debugValue = BoolValue("Debug", false)
    private val modeValue = ListValue("Mode", arrayOf("Wtap", "SprintReset", "Legit", "LegitFast", "StartPacket", "Packet", "PacketLegit", "DoublePacket", "SneakPacket"), "Wtap")

    private val minDelayValue: FloatValue = object : FloatValue("MinDelay", 50f, 0f, 500f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val i = maxDelayValue.get()
            if (i < newValue) set(i)
        }
    }.displayable { modeValue.isMode("Legit") || modeValue.isMode("LegitFast") } as FloatValue

    private val maxDelayValue: FloatValue = object : FloatValue("MaxDelay", 50f, 0f, 500f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val i = minDelayValue.get()
            if (i > newValue) set(i)
        }
    }.displayable { modeValue.isMode("Legit") || modeValue.isMode("LegitFast") } as FloatValue

    private val hurtTimeDelay = BoolValue("HurtTimeDelay", true)
    private val hurtTimeValue = IntegerValue("HurtTime", 10, 0, 10).displayable { hurtTimeDelay.get() }

    private val hitTimer = MSTimer()
    private var pendingReset = false
    private var nextDelay = 0

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    private fun resetState() {
        pendingReset = false
        hitTimer.reset()
        nextDelay = 0
    }

    @EventTarget
    fun onAttack(event: AttackEvent) {
        val player = mc.thePlayer ?: return
        val target = event.targetEntity

        if (!mc.gameSettings.keyBindForward.isKeyDown || mc.thePlayer.isSneaking)
            return

        if (target is EntityLivingBase && (target.hurtTime == hurtTimeValue.get() || !hurtTimeDelay.get()) ) {
            when (modeValue.get().lowercase()) {

                "legit", "legitfast", "wtap" -> {
                    pendingReset = true
                }

                "packetlegit" -> if(mc.thePlayer.isSprinting){
                    if (!player.serverSprintState) {
                        mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING))
                        player.serverSprintState = true
                    }
                }

                "startpacket" -> {
                    player.isSprinting = true
                    player.serverSprintState = true
                }

                "packet" -> {
                    player.isSprinting = false
                    player.isSprinting = true
                    player.serverSprintState = true
                }

                "doublepacket" -> {
                    repeat(2) {
                        player.isSprinting = false
                        player.isSprinting = true
                    }
                    player.serverSprintState = true
                }

                "sneakpacket" -> {
                    if (!mc.thePlayer.isSprinting) {
                        mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING))
                    }
                    mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING))
                    mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING))
                    mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING))
                    mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING))
                    mc.thePlayer.serverSprintState = true
                }

                "sprintreset" -> {
                    mc.netHandler.addToSendQueue(C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING))
                }
            }
        }
    }

    fun debugMessage(str: String) {
        if (debugValue.get()) {
            alert("§7§c§l§7§b$str")
        }
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent){
        val player = mc.thePlayer ?: return
        if (modeValue.isMode("LegitFast") && pendingReset && hitTimer.hasTimePassed(nextDelay.toLong())) {
            if (player.serverSprintState) {
                player.sprintingTicksLeft = 0
                debugMessage("Sprint Reset (Legit Fast)")
            }

            resetState()
        }

        nextDelay = RandomUtils.nextInt(minDelayValue.get().toInt(), maxDelayValue.get().toInt())
        hitTimer.reset()
    }

    @EventTarget
    fun onForward(event: MoveInputEvent) {
        if (modeValue.isMode("Wtap") && pendingReset) {
            if(mc.thePlayer.serverSprintState) {
                event.forward
                debugMessage("Sprint Reset (Wtap)")
            }

            resetState()
        }
    }

    @EventTarget
    fun onPostSprint(event: PostSprintEvent) {
        if (modeValue.isMode("Legit") && hitTimer.hasTimePassed(nextDelay.toLong())) {
            if (pendingReset) {
                if(mc.thePlayer.serverSprintState) {
                    mc.thePlayer.isSprinting = false
                    debugMessage("Sprint Reset (Legit)")
                }

                resetState()
            }

            nextDelay = RandomUtils.nextInt(minDelayValue.get().toInt(), maxDelayValue.get().toInt())
            hitTimer.reset()
        }
    }

    override val tag: String
        get() = modeValue.get()
}