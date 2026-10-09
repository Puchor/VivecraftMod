package org.vivecraft.mod_compat_vr.reacharound.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.VRState;

@Pseudo
@Mixin(targets = "com.spanser.reacharound.client.feature.PlacementFeature")
public class PlacementFeatureMixin {
    /**
     * an empty hand has nothing to place, don't let it count as a block
     */
    @Inject(method = "validateReacharoundStack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vivecraft$noEmptyHand(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * nothing can be placed while an item is in use, like a raised shield.
     * in VR the player also looks along the shield hand then, which would move the indicator to that hand
     */
    @Inject(method = "checkPlayerReacharoundTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vivecraft$noTargetWhileUsing(LocalPlayer player, CallbackInfo ci) {
        if (VRState.VR_RUNNING && player.isUsingItem()) {
            ci.cancel();
        }
    }
}
