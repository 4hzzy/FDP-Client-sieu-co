package net.ccbluex.liquidbounce.features.module.modules.combat.velocitys.intave

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.value.*
import net.ccbluex.liquidbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion


class IntaveReduce : VelocityMode("IntaveReduce") {
    private val reduceFactor = FloatValue("Factor", 0.6f, 0.6f,1f)
    private val hurtTime = IntegerValue("HurtTime", 9, 1, 10)
    private val pauseOnExplosion = BoolValue("PauseOnExplosion", true)
    private val ticksToPause = IntegerValue("TicksToPause", 20, 1, 50).displayable { pauseOnExplosion.get() }

    private var hasReceivedVelocity = false

    private var intaveTick = 0
    private var lastAttackTime = 0L
    private var intaveDamageTick = 0

    private var pauseTicks = 0

    override fun onDisable() {
        pauseTicks = 0
        mc.thePlayer?.speedInAir = 0.02F
    }

    override fun onUpdate(event: UpdateEvent) {
        if (!hasReceivedVelocity) return
        intaveTick++

        if (mc.thePlayer.hurtTime == 2) {
            intaveDamageTick++
            if (mc.thePlayer.onGround && intaveTick % 2 == 0 && intaveDamageTick <= 10) {
                mc.thePlayer.jump()
                intaveTick = 0
            }
            hasReceivedVelocity = false
        }
    }

    override fun onAttack(event: AttackEvent) {
        val player = mc.thePlayer ?: return

        if (player.hurtTime == hurtTime.get() && System.currentTimeMillis() - lastAttackTime <= 8000) {
            player.motionX *= reduceFactor.get()
            player.motionZ *= reduceFactor.get()
        }

        lastAttackTime = System.currentTimeMillis()
    }

    override fun onPacket(event: PacketEvent) {
        if (!event.isServerSide()) return
        val player = mc.thePlayer ?: return

        when (val packet = event.packet) {
            is S12PacketEntityVelocity -> {
                if (packet.entityID != player.getEntityId()) return

                if (pauseTicks > 0) {
                    event.cancelEvent()
                    return
                }

                hasReceivedVelocity = true
                intaveDamageTick = 0
            }

            is S27PacketExplosion -> {
                if (!pauseOnExplosion.get()) return

                val motionX = player.motionX + packet.func_149149_c()
                val motionY = player.motionY + packet.func_149144_d()
                val motionZ = player.motionZ + packet.func_149147_e()

                if (motionY > 0.0 && (motionX != 0.0 || motionZ != 0.0)) {
                    pauseTicks = ticksToPause.get()
                }
            }
        }
    }
}