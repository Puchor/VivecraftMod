package org.vivecraft.mod_compat_vr.reacharound.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.OffhandAimHelper;

@Pseudo
@Mixin(targets = "com.spanser.reacharound.client.handler.RayTraceHandler")
public class RayTraceHandlerMixin {
    /**
     * reacharound aims from the head, in VR aim from the hand that places the block instead
     */
    @Inject(method = "getEntityParams", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vivecraft$aimFromController(Entity entity, CallbackInfoReturnable<Pair<Vec3, Vec3>> cir) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        if (!VRState.VR_RUNNING || dataHolder.vrPlayer == null || dataHolder.vrPlayer.vrdata_world_pre == null ||
            entity != Minecraft.getInstance().player)
        {
            return;
        }

        LocalPlayer player = (LocalPlayer) entity;
        VRData data = dataHolder.vrPlayer.vrdata_world_pre;
        // reacharound uses the main hand if it holds a block, otherwise the offhand
        boolean offhand = !(player.getMainHandItem().getItem() instanceof BlockItem) &&
            player.getOffhandItem().getItem() instanceof BlockItem && OffhandAimHelper.isActive(player);
        VRData.VRDevicePose pose = offhand ? data.getController(1) : data.getAim();
        cir.setReturnValue(Pair.of(pose.getPosition(), new Vec3(pose.getDirection()).normalize()));
    }
}
