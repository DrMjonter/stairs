package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.FutureClock;
import com.notmjonter.stairs.FutureDimension;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Install local solar data before either Level or ServerLevel stores its data reference. */
@Mixin(MinecraftServer.class)
public abstract class FutureClockMixin {
    @ModifyArgs(method = "createLevels", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;<init>(Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/world/level/storage/ServerLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/dimension/LevelStem;Lnet/minecraft/server/level/progress/ChunkProgressListener;ZJLjava/util/List;ZLnet/minecraft/world/RandomSequences;)V"))
    private void stairs$localClock(Args args) {
        if (!FutureDimension.KEY.equals(args.get(4))) return;
        MinecraftServer server = args.get(0);
        args.set(3, new FutureClock.Data(server.getWorldData(), args.get(3)));
        // Keep vanilla's secondary-dimension game-time ticking disabled. Our
        // solar clock must not run the Overworld scheduled-event queue twice.
        args.set(10, false);
    }
}
