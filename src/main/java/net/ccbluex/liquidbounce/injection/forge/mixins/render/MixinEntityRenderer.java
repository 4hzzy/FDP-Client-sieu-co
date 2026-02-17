/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.render;

import com.google.common.base.Predicates;
import net.ccbluex.liquidbounce.FDPClient;
import net.ccbluex.liquidbounce.event.Render3DEvent;
import net.ccbluex.liquidbounce.features.module.modules.client.HurtCam;
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura;
import net.ccbluex.liquidbounce.features.module.modules.combat.Reach;
import net.ccbluex.liquidbounce.features.module.modules.render.CameraClip;
import net.ccbluex.liquidbounce.features.module.modules.render.KillESP;
import net.ccbluex.liquidbounce.features.module.modules.render.Tracers;
import net.ccbluex.liquidbounce.features.module.modules.render.PerspectiveMod;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;
import static org.objectweb.asm.Opcodes.GETFIELD;

import java.util.List;
import java.util.ArrayList;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Shadow
    public abstract void loadShader(ResourceLocation resourceLocationIn);

    @Shadow
    public abstract void setupCameraTransform(float partialTicks, int pass);

    @Shadow
    private Entity pointedEntity;

    @Shadow
    private Minecraft mc;

    @Shadow
    private float thirdPersonDistanceTemp;

    @Shadow
    private float thirdPersonDistance;

    @Shadow
    private boolean cloudFog;

    @Inject(method = "renderWorldPass", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z", shift = At.Shift.BEFORE))
    private void renderWorldPass(int pass, float partialTicks, long finishTimeNano, CallbackInfo callbackInfo) {
        FDPClient.eventManager.callEvent(new Render3DEvent(partialTicks));
    }

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void injectHurtCameraEffect(CallbackInfo callbackInfo) {
        if(!FDPClient.moduleManager.getModule(HurtCam.class).getModeValue().get().equalsIgnoreCase("Vanilla")) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "orientCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Vec3;distanceTo(Lnet/minecraft/util/Vec3;)D"), cancellable = true)
    private void cameraClip(float partialTicks, CallbackInfo callbackInfo) {
        if (FDPClient.moduleManager.getModule(CameraClip.class).getState()) {
            callbackInfo.cancel();

            Entity entity = this.mc.getRenderViewEntity();
            float f = entity.getEyeHeight();

            if(entity instanceof EntityLivingBase && ((EntityLivingBase) entity).isPlayerSleeping()) {
                f += 1;
                GlStateManager.translate(0F, 0.3F, 0.0F);

                if(!this.mc.gameSettings.debugCamEnable) {
                    BlockPos blockpos = new BlockPos(entity);
                    IBlockState iblockstate = this.mc.theWorld.getBlockState(blockpos);
                    net.minecraftforge.client.ForgeHooksClient.orientBedCamera(this.mc.theWorld, blockpos, iblockstate, entity);

                    GlStateManager.rotate(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks + 180.0F, 0.0F, -1.0F, 0.0F);
                    GlStateManager.rotate(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks, -1.0F, 0.0F, 0.0F);
                }
            }else if(this.mc.gameSettings.thirdPersonView > 0) {
                double d3 = this.thirdPersonDistanceTemp + (this.thirdPersonDistance - this.thirdPersonDistanceTemp) * partialTicks;

                if(this.mc.gameSettings.debugCamEnable) {
                    GlStateManager.translate(0.0F, 0.0F, (float) (-d3));
                }else{
                    float f1 = entity.rotationYaw;
                    float f2 = entity.rotationPitch;

                    if(this.mc.gameSettings.thirdPersonView == 2)
                        f2 += 180.0F;

                    if(this.mc.gameSettings.thirdPersonView == 2)
                        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);

                    GlStateManager.rotate(entity.rotationPitch - f2, 1.0F, 0.0F, 0.0F);
                    GlStateManager.rotate(entity.rotationYaw - f1, 0.0F, 1.0F, 0.0F);
                    GlStateManager.translate(0.0F, 0.0F, (float) (-d3));
                    GlStateManager.rotate(f1 - entity.rotationYaw, 0.0F, 1.0F, 0.0F);
                    GlStateManager.rotate(f2 - entity.rotationPitch, 1.0F, 0.0F, 0.0F);
                }
            } else {
                GlStateManager.translate(0.0F, 0.0F, -0.1F);
            }

            if(!this.mc.gameSettings.debugCamEnable) {
                float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks + 180.0F;
                float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
                float roll = 0.0F;
                if(entity instanceof EntityAnimal) {
                    EntityAnimal entityanimal = (EntityAnimal) entity;
                    yaw = entityanimal.prevRotationYawHead + (entityanimal.rotationYawHead - entityanimal.prevRotationYawHead) * partialTicks + 180.0F;
                }

                Block block = ActiveRenderInfo.getBlockAtEntityViewpoint(this.mc.theWorld, entity, partialTicks);
                net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event = new net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup((EntityRenderer) (Object) this, entity, block, partialTicks, yaw, pitch, roll);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
                GlStateManager.rotate(event.roll, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(event.pitch, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(event.yaw, 0.0F, 1.0F, 0.0F);
            }

            GlStateManager.translate(0.0F, -f, 0.0F);
            double d0 = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
            double d1 = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks + f;
            double d2 = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;
            this.cloudFog = this.mc.renderGlobal.hasCloudFog(d0, d1, d2, partialTicks);
        }
    }

    @Inject(method = "setupCameraTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V", shift = At.Shift.BEFORE))
    private void setupCameraViewBobbingBefore(final CallbackInfo callbackInfo) {
        final KillESP killESP = FDPClient.moduleManager.getModule(KillESP.class);
        final KillAura aura = FDPClient.moduleManager.getModule(KillAura.class);

        if ((killESP != null && aura != null && killESP.getModeValue().get().equalsIgnoreCase("tracers") && !aura.getTargetModeValue().get().equalsIgnoreCase("multi") && aura.getCurrentTarget() != null) || FDPClient.moduleManager.getModule(Tracers.class).getState()) GL11.glPushMatrix();
    }

    @Inject(method = "setupCameraTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V", shift = At.Shift.AFTER))
    private void setupCameraViewBobbingAfter(final CallbackInfo callbackInfo) {
        final KillESP killESP = FDPClient.moduleManager.getModule(KillESP.class);
        final KillAura aura = FDPClient.moduleManager.getModule(KillAura.class);

        if ((killESP != null && aura != null && killESP.getModeValue().get().equalsIgnoreCase("tracers") && !aura.getTargetModeValue().get().equalsIgnoreCase("multi") && aura.getCurrentTarget() != null) || FDPClient.moduleManager.getModule(Tracers.class).getState()) GL11.glPopMatrix();
    }

    /**
     * @author Liuli
     */
    @Overwrite
    public void getMouseOver(float partialTicks) {
        Entity renderViewEntity = this.mc.getRenderViewEntity();
        if (renderViewEntity == null || this.mc.theWorld == null) return;

        this.mc.mcProfiler.startSection("pick");
        this.mc.pointedEntity = null;
        this.pointedEntity = null;

        final Reach reach = FDPClient.moduleManager.getModule(Reach.class);
        final boolean reachEnabled = reach.getState();

        double blockReachDistance = reachEnabled ? reach.getMaxRange() : mc.playerController.getBlockReachDistance();
        double buildReach = reachEnabled ? reach.getBuildReachValue().get() : blockReachDistance;
        double combatReach = reachEnabled ? reach.getCombatReachValue().get() : 3.0;

        this.mc.objectMouseOver = renderViewEntity.rayTrace(buildReach, partialTicks);

        Vec3 eyePos = renderViewEntity.getPositionEyes(partialTicks);
        boolean restrictToThree = false;

        if (this.mc.playerController.extendedReach()) {
            blockReachDistance = 6.0;
        } else if (blockReachDistance > 3.0) {
            restrictToThree = true;
        }

        double closestDistance = blockReachDistance;

        if (this.mc.objectMouseOver != null) {
            closestDistance = this.mc.objectMouseOver.hitVec.distanceTo(eyePos);
        }

        if (reachEnabled) {
            MovingObjectPosition blockTrace = renderViewEntity.rayTrace(buildReach, partialTicks);
            if (blockTrace != null) {
                closestDistance = blockTrace.hitVec.distanceTo(eyePos);
            }
        }

        Vec3 lookVec = renderViewEntity.getLook(partialTicks);
        Vec3 reachVec = eyePos.addVector(
                lookVec.xCoord * blockReachDistance,
                lookVec.yCoord * blockReachDistance,
                lookVec.zCoord * blockReachDistance
        );

        Vec3 hitVec = null;

        List<Entity> entities = this.mc.theWorld.getEntitiesInAABBexcluding(
                renderViewEntity,
                renderViewEntity.getEntityBoundingBox()
                        .addCoord(
                                lookVec.xCoord * blockReachDistance,
                                lookVec.yCoord * blockReachDistance,
                                lookVec.zCoord * blockReachDistance
                        )
                        .expand(1.0, 1.0, 1.0),
                Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBeCollidedWith)
        );

        double entityDistance = closestDistance;

        for (Entity candidate : entities) {
            float border = candidate.getCollisionBorderSize();
            AxisAlignedBB entityBox = candidate.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = entityBox.calculateIntercept(eyePos, reachVec);

            if (entityBox.isVecInside(eyePos)) {
                if (entityDistance >= 0.0) {
                    this.pointedEntity = candidate;
                    hitVec = intercept == null ? eyePos : intercept.hitVec;
                    entityDistance = 0.0;
                }
            } else if (intercept != null) {
                double dist = eyePos.distanceTo(intercept.hitVec);
                if (dist < entityDistance || entityDistance == 0.0) {
                    if (candidate == renderViewEntity.ridingEntity && !renderViewEntity.canRiderInteract()) {
                        if (entityDistance == 0.0) {
                            this.pointedEntity = candidate;
                            hitVec = intercept.hitVec;
                        }
                    } else {
                        this.pointedEntity = candidate;
                        hitVec = intercept.hitVec;
                        entityDistance = dist;
                    }
                }
            }
        }

        if (this.pointedEntity != null && restrictToThree && eyePos.distanceTo(hitVec) > combatReach) {
            this.pointedEntity = null;
            this.mc.objectMouseOver = new MovingObjectPosition(
                    MovingObjectPosition.MovingObjectType.MISS, hitVec, null, new BlockPos(hitVec)
            );
        }

        if (this.pointedEntity != null && (entityDistance < closestDistance || this.mc.objectMouseOver == null)) {
            this.mc.objectMouseOver = new MovingObjectPosition(this.pointedEntity, hitVec);
            if (this.pointedEntity instanceof EntityLivingBase || this.pointedEntity instanceof EntityItemFrame) {
                this.mc.pointedEntity = this.pointedEntity;
            }
        }

        this.mc.mcProfiler.endSection();
    }


    @Redirect(method = "updateCameraAndRender", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;inGameHasFocus:Z", opcode = GETFIELD))
    public boolean updateCameraAndRender(Minecraft minecraft) {
        return PerspectiveMod.overrideMouse();
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = GETFIELD))
    public float getRotationYaw(Entity entity) {
        return PerspectiveMod.perspectiveToggled ? PerspectiveMod.cameraYaw : entity.rotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F", opcode = GETFIELD))
    public float getPrevRotationYaw(Entity entity) {
        return PerspectiveMod.perspectiveToggled ? PerspectiveMod.cameraYaw : entity.prevRotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F", opcode = GETFIELD))
    public float getRotationPitch(Entity entity) {
        return PerspectiveMod.perspectiveToggled ? PerspectiveMod.cameraPitch : entity.rotationPitch;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F"))
    public float getPrevRotationPitch(Entity entity) {
        return PerspectiveMod.perspectiveToggled ? PerspectiveMod.cameraPitch : entity.prevRotationPitch;
    }
}