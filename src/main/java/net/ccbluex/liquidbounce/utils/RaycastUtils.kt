/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.utils

import net.ccbluex.liquidbounce.utils.RotationUtils.getVectorForRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.serverRotation
import net.ccbluex.liquidbounce.utils.extensions.eyes
import net.ccbluex.liquidbounce.utils.extensions.hitBox
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer

object RaycastUtils : MinecraftInstance() {

    @JvmStatic
    @JvmOverloads
    fun raycastEntity(
        range: Double,
        yaw: Float = serverRotation.yaw,
        pitch: Float = serverRotation.pitch,
        entityFilter: (Entity) -> Boolean
    ): Entity? {
        val renderViewEntity = mc.renderViewEntity ?: return null
        val theWorld = mc.theWorld ?: return null

        val eyePosition = renderViewEntity.eyes
        val entityLook = getVectorForRotation(Rotation(yaw, pitch))

        val lookScaled = eyePosition.addVector(
            entityLook.xCoord * range,
            entityLook.yCoord * range,
            entityLook.zCoord * range
        )

        val searchBB = renderViewEntity.entityBoundingBox
            .addCoord(
                entityLook.xCoord * range,
                entityLook.yCoord * range,
                entityLook.zCoord * range
            )
            .expand(1.0, 1.0, 1.0)

        val entityList = theWorld.getEntitiesInAABBexcluding(renderViewEntity, searchBB) {
            it != null && (it !is EntityPlayer || !it.isSpectator) && it.canBeCollidedWith()
        }

        var pointedEntity: Entity? = null
        var closestDistance = range

        for (entity in entityList) {
            if (!entityFilter(entity)) continue

            val entityBB = entity.hitBox
            val intercept = entityBB.calculateIntercept(eyePosition, lookScaled)

            if (entityBB.isVecInside(eyePosition)) {
                if (closestDistance >= 0.0) {
                    pointedEntity = entity
                    closestDistance = 0.0
                }
            } else if (intercept != null) {
                val dist = eyePosition.distanceTo(intercept.hitVec)

                if (dist < closestDistance) {
                    pointedEntity = if (entity == renderViewEntity.ridingEntity && !renderViewEntity.canRiderInteract()) {
                        if (closestDistance == 0.0) entity else continue
                    } else {
                        closestDistance = dist
                        entity
                    }
                }
            }
        }

        return pointedEntity
    }
}