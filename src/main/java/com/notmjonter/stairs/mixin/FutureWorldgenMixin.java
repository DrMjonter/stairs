package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.FutureDimension;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Substitutes village and decoration materials only during future-world generation. */
@Mixin(WorldGenRegion.class)
public abstract class FutureWorldgenMixin {
    @ModifyVariable(method = "setBlock", at = @At("HEAD"), argsOnly = true)
    private BlockState stairs$futureMaterials(BlockState state) {
        WorldGenRegion region = (WorldGenRegion) (Object) this;
        return region.getLevel().dimension() == FutureDimension.KEY ? FutureDimension.material(state) : state;
    }
}
