package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.PreUpdateEvent
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.value.*
import net.ccbluex.liquidbounce.utils.MovementUtils
import kotlin.random.Random

@ModuleInfo(name = "HitSelect", description = "Choose the best time to hit.", category = ModuleCategory.COMBAT)
object HitSelect : Module() {

    private val modesValue = ListValue("Modes", arrayOf("Pause", "Active"), "Active")
    private val preferencesValue = ListValue("Preferences", arrayOf("MoveSpeed", "KBReduction", "CriticalHits"), "MoveSpeed")
    private val chanceValue = IntegerValue("Chance", 80, 0, 100)
    private val delayValue = IntegerValue("Delay", 420, 300, 500)

    private var attackTime = -1L
    private var currentShouldAttack = false
    private val random = Random(System.currentTimeMillis())

    fun canAttack(): Boolean {
        if (!state || modesValue.get().equals("Active"))
            return true

        return currentShouldAttack
    }

    @EventTarget
    fun onAttack(event: AttackEvent) {
        if (modesValue.get().equals("Active") && !currentShouldAttack) {
            event.cancelEvent()
            return
        }

        if (canAttack())
            attackTime = System.currentTimeMillis()
    }

    @EventTarget
    fun onPreUpdate(event: PreUpdateEvent) {
        currentShouldAttack = false

        if (random.nextInt(100) > chanceValue.get()) {
            currentShouldAttack = true
        } else {
           when (preferencesValue.get().lowercase()) {
               "kbreduction" -> currentShouldAttack = mc.thePlayer.hurtTime > 0 && !mc.thePlayer.onGround && MovementUtils.isMoving()
               "criticalhits" -> currentShouldAttack = !mc.thePlayer.onGround && mc.thePlayer.motionY < 0
           }

           if (!currentShouldAttack) {
               currentShouldAttack = System.currentTimeMillis() - attackTime >= delayValue.get()
           }
        }
    }
}