package com.notmjonter.stairs.mixin;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(RegionFileStorage.class)
public interface MirrorRegionAccess {
    @Invoker("write") void stairs$write(ChunkPos pos, CompoundTag tag) throws IOException;
}
