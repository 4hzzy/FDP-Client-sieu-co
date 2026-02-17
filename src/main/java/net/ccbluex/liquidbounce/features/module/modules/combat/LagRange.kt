package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.FDPClient
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.world.Scaffold
import net.ccbluex.liquidbounce.features.value.IntegerValue
import net.ccbluex.liquidbounce.utils.MovementUtils
import net.ccbluex.liquidbounce.utils.PacketUtils
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.network.Packet
import net.minecraft.network.handshake.client.C00Handshake
import net.minecraft.network.play.INetHandlerPlayServer
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion
import net.minecraft.network.status.client.C00PacketServerQuery
import net.minecraft.network.status.client.C01PacketPing
import net.minecraft.network.status.server.S01PacketPong

@ModuleInfo(name = "LagRange", category = ModuleCategory.COMBAT)
object LagRange : Module() {

    private val delay = IntegerValue("Delay", 550, 0, 1000)
    private val recoilTime = IntegerValue("RecoilTime", 750, 0, 2000)

    private val packetQueue = Queues.newArrayDeque<QueueData>()
    private val resetTimer = MSTimer()
    private var ignoreWholeTick = false

    override fun onEnable() {
        packetQueue.clear()
        resetTimer.reset()
        ignoreWholeTick = false
    }

    override fun onDisable() {
        if (mc.thePlayer == null) return
        blink()
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val player = mc.thePlayer ?: return
        val packet = event.packet

        if (player.isDead || ignoreWholeTick) {
            return
        }

        // Check if player is moving
        if (!MovementUtils.isMoving()) {
            blink()
            return
        }

        // Flush on damage received
        if (player.health < player.maxHealth) {
            if (player.hurtTime != 0) {
                blink()
                return
            }
        }

        FDPClient.moduleManager[Scaffold::class.java]?.let {
            if(it.state){
                blink()
                return
            }
        }

        if (mc.currentScreen is GuiContainer) {
            blink()
            return
        }

        if (!resetTimer.hasTimePassed(recoilTime.get().toLong())) return

        if (mc.isSingleplayer || mc.currentServerData == null) {
            blink()
            return
        }

        when (packet) {
            is C00Handshake, is C00PacketServerQuery, is C01PacketPing, is C01PacketChatMessage, is S01PacketPong -> return

            is C0EPacketClickWindow, is C0DPacketCloseWindow -> {
                blink()
                return
            }

            is S08PacketPlayerPosLook, is C08PacketPlayerBlockPlacement, is C07PacketPlayerDigging, is C12PacketUpdateSign, is C19PacketResourcePackStatus -> {
                blink()
                return
            }

            is C02PacketUseEntity -> {
                blink()
                return
            }

            is S12PacketEntityVelocity -> {
                if (player.entityId == packet.entityID) {
                    blink()
                    return
                }
            }

            is S27PacketExplosion -> {
                if (packet.func_149149_c() != 0f || packet.func_149144_d() != 0f || packet.func_149147_e() != 0f) {
                    blink()
                    return
                }
            }
        }

        if(event.type == PacketEvent.Type.SEND){
            event.cancelEvent()

            synchronized(packetQueue) {
                packetQueue.add(QueueData(packet, System.currentTimeMillis()))
            }
        }
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        if (event.worldClient == null) {
            synchronized(packetQueue) {
                packetQueue.clear()
            }
        }
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val player = mc.thePlayer ?: return

        if (player.isDead || player.isUsingItem) {
            blink()
            return
        }

        if (!resetTimer.hasTimePassed(recoilTime.get().toLong())) return

        handlePackets()
        ignoreWholeTick = false
    }

    override val tag
        get() = packetQueue.size.toString()

    private fun blink(handlePackets: Boolean = true) {
        if (handlePackets) {
            resetTimer.reset()
        }

        handlePackets(true)
        ignoreWholeTick = true
    }

    private fun handlePackets(clear: Boolean = false) {
        synchronized(packetQueue) {
            val threshold = System.currentTimeMillis() - delay.get()

            packetQueue.removeIf { (packet, timestamp) ->
                if (clear || timestamp <= threshold) {
                    PacketUtils.sendPacketNoEvent(packet as Packet<INetHandlerPlayServer>)
                    true
                } else {
                    false
                }
            }
        }
    }
}

data class QueueData(val packet: Packet<*>, val time: Long)