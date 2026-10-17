package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.FutureVillagerReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Trigger only after clicking a bed actually evicts a sleeping villager. */
@Mixin(BedBlock.class)
public abstract class FutureVillagerBedMixin {
    @Shadow private boolean kickVillagerOutOfBed(Level level, BlockPos pos) { throw new AssertionError(); }

    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/BedBlock;kickVillagerOutOfBed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean stairs$evictionWarning(BedBlock bed, Level level, BlockPos pos,
            BlockState state, Level clickedLevel, BlockPos clickedPos, Player player, BlockHitResult hit) {
        boolean evicted = kickVillagerOutOfBed(level, pos);
        if (evicted && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer)
            FutureVillagerReactions.react(serverLevel, serverPlayer);
        return evicted;
    }
}
