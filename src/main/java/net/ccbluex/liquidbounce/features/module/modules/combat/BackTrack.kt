package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.FDPClient
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.world.Scaffold
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.features.value.*
import net.ccbluex.liquidbounce.injection.implementations.IMixinEntityLivingBase
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.INetHandler
import net.minecraft.network.Packet
import net.minecraft.network.play.server.*
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import org.lwjgl.opengl.GL11
import java.awt.Color

@ModuleInfo(name = "BackTrack", category = ModuleCategory.COMBAT)
class BackTrack : Module() {

    private val delayValue = IntegerValue("Delay", 400, 0, 10000)
    private val maxRangeValue = FloatValue("MaxRange", 3f, 0.5f, 10f)
    private val velocityValue = BoolValue("Velocity", true)
    private val explosionValue = BoolValue("Explosion", true)
    private val espValue = BoolValue("ESP", true)
    private val espRedValue = IntegerValue("ESP-Red", 255, 0, 255) { espValue.get() }
    private val espGreenValue = IntegerValue("ESP-Green", 0, 0, 255) { espValue.get() }
    private val espBlueValue = IntegerValue("ESP-Blue", 0, 0, 255) { espValue.get() }
    private val espAlphaValue = IntegerValue("ESP-Alpha", 100, 0, 255) { espValue.get() }

    private val packets = mutableListOf<Packet<*>>()
    private val timer = MSTimer()
    private var active = false

    private val target: EntityLivingBase?
        get() = FDPClient.moduleManager[KillAura::class.java]?.currentTarget

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        flush()
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        flush()
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        mc.thePlayer ?: return
        val theWorld = mc.theWorld ?: return
        mc.netHandler ?: return

        if (event.type == PacketEvent.Type.SEND) return

        if (FDPClient.moduleManager[Scaffold::class.java]?.state == true) {
            flush()
            return
        }

        when (val packet = event.packet) {
            is S14PacketEntity -> {
                val entity = packet.getEntity(theWorld)
                if (entity is EntityLivingBase) {
                    entity.realPosX += packet.func_149062_c().toDouble()
                    entity.realPosY += packet.func_149061_d().toDouble()
                    entity.realPosZ += packet.func_149064_e().toDouble()
                }
            }
            is S18PacketEntityTeleport -> {
                val entity = theWorld.getEntityByID(packet.entityId)
                if (entity is EntityLivingBase) {
                    entity.realPosX = packet.x.toDouble()
                    entity.realPosY = packet.y.toDouble()
                    entity.realPosZ = packet.z.toDouble()
                }
            }
            is S08PacketPlayerPosLook -> {
                flush()
                return
            }
            is S13PacketDestroyEntities -> {
                val targetId = target?.entityId ?: return
                if (packet.entityIDs.any { it == targetId }) {
                    flush()
                    return
                }
            }
        }

        if (target == null) {
            flush()
            return
        }

        tryDelayPacket(event)
    }

    @EventTarget
    fun onUpdate(event: GameLoopEvent) {
        val thePlayer = mc.thePlayer ?: return
        mc.theWorld ?: return
        val target = target ?: run { flush(); return }

        if (!active) return

        val range = getRange(thePlayer.canEntityBeSeen(target))

        if (thePlayer.getDistance(target.realPosX / 32.0, target.realPosY / 32.0, target.realPosZ / 32.0) >= range
            || timer.hasTimePassed(delayValue.get().toLong())
        ) {
            flush()
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (!espValue.get()) return
        val target = target ?: return
        if (target == mc.thePlayer || target.isInvisible || packets.isEmpty()) return
        drawBox(target, target.realPosX / 32.0, target.realPosY / 32.0, target.realPosZ / 32.0)
    }

    private fun tryDelayPacket(event: PacketEvent) {
        val packet = event.packet
        if (packet::class.java in WHITELISTED_PACKETS) return

        val shouldFreeze = when (packet) {
            is S12PacketEntityVelocity -> velocityValue.get()
            is S27PacketExplosion -> explosionValue.get()
            else -> true
        }

        if (!shouldFreeze) return

        val target = target ?: return
        val thePlayer = mc.thePlayer ?: return

        val eyes = thePlayer.getPositionEyes(1.0f)
        val halfWidth = target.width / 2.0f
        val range = getRange(thePlayer.canEntityBeSeen(target))

        val eyesToReal = distanceToBox(eyes, target.realPosX / 32.0, target.realPosY / 32.0, target.realPosZ / 32.0, halfWidth, target.height)
        val eyesToClient = distanceToBox(eyes, target.posX, target.posY, target.posZ, halfWidth, target.height)

        if (eyesToClient < eyesToReal && eyesToClient < range && !active) {
            active = true
            timer.reset()
        }

        if (timer.hasTimePassed(delayValue.get().toLong()) || thePlayer.getDistance(target.realPosX / 32.0, target.realPosY / 32.0, target.realPosZ / 32.0) >= range
        ) {
            flush()
            return
        }

        if (active) {
            synchronized(packets) { packets.add(packet) }
            event.cancelEvent()
        }
    }

    private fun flush() {
        synchronized(packets) {
            if (packets.isEmpty()) {
                active = false
                timer.reset()
                return
            }

            val handler = mc.netHandler?.networkManager?.netHandler
            if (handler == null) {
                packets.clear()
                active = false
                timer.reset()
                return
            }

            for (packet in packets) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    (packet as Packet<INetHandler>).processPacket(handler)
                } catch (_: Exception) {}
            }

            packets.clear()
        }

        active = false
        timer.reset()
    }

    private fun reset() {
        packets.clear()
        active = false
        timer.reset()
    }

    private fun getRange(canSee: Boolean): Double {
        val range = maxRangeValue.get().toDouble()
        return if (canSee) range else range.coerceAtMost(3.0)
    }

    private fun distanceToBox(eyes: Vec3, x: Double, y: Double, z: Double, halfWidth: Float, height: Float): Double {
        return eyes.distanceTo(
            Vec3(
                MathHelper.clamp_double(eyes.xCoord, x - halfWidth, x + halfWidth),
                MathHelper.clamp_double(eyes.yCoord, y, y + height),
                MathHelper.clamp_double(eyes.zCoord, z - halfWidth, z + halfWidth)
            )
        )
    }

    private fun drawBox(entity: EntityLivingBase, x: Double, y: Double, z: Double) {
        val rx = x - mc.renderManager.viewerPosX
        val ry = y - mc.renderManager.viewerPosY
        val rz = z - mc.renderManager.viewerPosZ
        val hw = entity.width / 2.0
        val h = entity.height.toDouble()
        val box = AxisAlignedBB(rx - hw, ry, rz - hw, rx + hw, ry + h, rz + hw)
        val color = Color(espRedValue.get(), espGreenValue.get(), espBlueValue.get(), espAlphaValue.get())

        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.disableTexture2D()
        GlStateManager.disableDepth()
        GlStateManager.depthMask(false)
        GL11.glLineWidth(2.0f)
        RenderUtils.drawAxisAlignedBB(box, color)
        GlStateManager.depthMask(true)
        GlStateManager.enableDepth()
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    companion object {
        private val WHITELISTED_PACKETS = setOf(
            S00PacketKeepAlive::class.java,
            S01PacketJoinGame::class.java,
            S06PacketUpdateHealth::class.java,
            S07PacketRespawn::class.java,
            S08PacketPlayerPosLook::class.java,
            S0CPacketSpawnPlayer::class.java,
            S38PacketPlayerListItem::class.java,
            S3FPacketCustomPayload::class.java,
            S40PacketDisconnect::class.java,
            S41PacketServerDifficulty::class.java,
            S48PacketResourcePackSend::class.java
        )
    }

    override val tag: String
        get() = "${packets.size} | ${delayValue.get()}"
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