/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.utils

import net.ccbluex.liquidbounce.injection.implementations.IMixinEntityLivingBase
import net.minecraft.client.Minecraft
import net.minecraft.entity.EntityLivingBase

open class MinecraftInstance {
    companion object {
        @JvmField
        val mc: Minecraft = Minecraft.getMinecraft()
    }

    var EntityLivingBase.realPosX: Double
        get() = (this as IMixinEntityLivingBase).realPosX
        set(value) {
            (this as IMixinEntityLivingBase).realPosX = value
        }

    var EntityLivingBase.realPosY: Double
        get() = (this as IMixinEntityLivingBase).realPosY
        set(value) {
            (this as IMixinEntityLivingBase).realPosY = value
        }

    var EntityLivingBase.realPosZ: Double
        get() = (this as IMixinEntityLivingBase).realPosZ
        set(value) {
            (this as IMixinEntityLivingBase).realPosZ = value
        }
}
