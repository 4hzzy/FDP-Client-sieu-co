package net.ccbluex.liquidbounce.features.module.modules.combat.velocitys.vanilla

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minecraft.network.play.server.S12PacketEntityVelocity

class DHMTVelocity : VelocityMode("DHMT") {
    override fun onVelocityPacket(event: PacketEvent) {
        mc.thePlayer ?: return
        val packet = event.packet

        if (packet is S12PacketEntityVelocity) {
            if (mc.thePlayer.onGround)
            mc.thePlayer.motionY = packet.getMotionY() / 8000.0
            packet.motionX *= 0
            packet.motionZ *= 0
        }

        if (mc.thePlayer.hurtTime > 0) {
            mc.thePlayer.motionX *= 1
            mc.thePlayer.motionY *= 1
            mc.thePlayer.motionZ *= 1
        }
    }
}
