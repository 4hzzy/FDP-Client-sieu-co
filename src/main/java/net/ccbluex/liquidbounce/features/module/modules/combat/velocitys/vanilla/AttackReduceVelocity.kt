package net.ccbluex.liquidbounce.features.module.modules.combat.velocitys.vanilla

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.value.*
import net.ccbluex.liquidbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minecraft.util.MovingObjectPosition

class AttackReduceVelocity : VelocityMode("AttackReduce") {
    val forward = BoolValue("Forward", true)
    val reduceY = BoolValue("ReduceY", true)
    val power = IntegerValue("Power", 1, 1, 10)
    val keepSprint = BoolValue("KeepSprint", true)

    @EventTarget
    fun onKnockback(event: KnockbackEvent) {
        event.reduceY = reduceY.get()
        event.power = power.get()
        event.sprint = keepSprint.get()
    }

    @EventTarget
    fun onInput(event: MoveInputEvent) {
        if (mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mc.thePlayer.hurtTime > 0)
            event.forward = 1f
    }
}
