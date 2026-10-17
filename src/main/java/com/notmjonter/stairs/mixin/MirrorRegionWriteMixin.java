package com.notmjonter.stairs.mixin;
import java.io.IOException;
import com.notmjonter.stairs.MirrorSnapshots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(IOWorker.class)
public abstract class MirrorRegionWriteMixin {
    @Redirect(method = "runStore", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/storage/RegionFileStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V"))
    private void stairs$stableSnapshot(RegionFileStorage storage, ChunkPos pos, CompoundTag tag) throws IOException {
        var lock = MirrorSnapshots.DISK_GATE.readLock();
        lock.lock();
        try { ((MirrorRegionAccess) (Object) storage).stairs$write(pos, tag); }
        finally { lock.unlock(); }
    }
}
