package org.vivecraft.client_vr.gameplay;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;

/**
 * lets the offhand item be aimed with the offhand controller,
 * used when the main hand item has no use and vanilla falls back to the offhand item
 */
public class OffhandAimHelper {

    /**
     * @param player player to check
     * @return if offhand items should be aimed with the offhand controller
     */
    public static boolean isActive(LocalPlayer player) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        return VRState.VR_RUNNING && player != null && dataHolder.vrPlayer != null &&
            dataHolder.vrSettings.offhandAim && !dataHolder.vrSettings.seated &&
            !player.getOffhandItem().isEmpty();
    }

    /**
     * @param player player to check
     * @return if the offhand crosshair should show for the current offhand item.
     * items that are not aimed, like shields and totems, don't get one
     */
    public static boolean showCrosshair(LocalPlayer player) {
        if (!isActive(player)) return false;
        ItemStack stack = player.getOffhandItem();
        return !stack.is(Items.SHIELD) && !stack.is(Items.TOTEM_OF_UNDYING);
    }

    /**
     * @return the offhand controller pose used for aiming
     */
    public static VRData.VRDevicePose getOffhandPose() {
        return ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getController(1);
    }

    /**
     * raytraces blocks and entities from the offhand controller, with the players interaction ranges
     *
     * @param player player that aims
     * @return what the offhand points at, a miss has its location at the end of the block reach
     */
    public static HitResult pick(LocalPlayer player) {
        VRData.VRDevicePose controller = getOffhandPose();
        Vec3 start = controller.getPosition();
        Vec3 direction = new Vec3(controller.getDirection());

        double blockReach = player.blockInteractionRange();
        double entityReach = player.entityInteractionRange();

        Vec3 blockEnd = start.add(direction.scale(blockReach));
        HitResult result = player.level().clip(
            new ClipContext(start, blockEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        // entities only count when they are closer than the hit block
        double maxEntityDistSq = entityReach * entityReach;
        if (result.getType() != HitResult.Type.MISS) {
            maxEntityDistSq = Math.min(maxEntityDistSq, start.distanceToSqr(result.getLocation()));
        }
        Vec3 entityEnd = start.add(direction.scale(entityReach));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, start, entityEnd,
            new AABB(start, entityEnd).inflate(1.0D),
            entity -> !entity.isSpectator() && entity.isPickable() && entity != player.getVehicle(),
            maxEntityDistSq);

        return entityHit != null ? entityHit : result;
    }
}
