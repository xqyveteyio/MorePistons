package com.gollum.morepistons.mixin.client;

import com.gollum.morepistons.block.LongPistonBlock;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes vanilla's final retract animation select a sticky head for long sticky pistons. */
@Mixin(PistonHeadRenderer.class)
abstract class PistonHeadRendererMixin {
    @Redirect(
        method = "render(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z",
            ordinal = 1
        )
    )
    private boolean morePistons$isStickyPiston(BlockState state, Block vanillaStickyPiston) {
        if (state.getBlock() instanceof LongPistonBlock longPiston && longPiston.isSticky()) {
            return true;
        }
        return state.is(vanillaStickyPiston);
    }
}
