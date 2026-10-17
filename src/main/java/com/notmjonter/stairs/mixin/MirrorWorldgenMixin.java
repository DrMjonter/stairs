package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.MirrorDimension;
import com.notmjonter.stairs.MirrorSnapshots;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Clone the actual Overworld settings before the mirror's chunk cache is created. */
@Mixin(MinecraftServer.class)
public abstract class MirrorWorldgenMixin {
    @ModifyArgs(method = "createLevels", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;<init>(Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/world/level/storage/ServerLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/dimension/LevelStem;Lnet/minecraft/server/level/progress/ChunkProgressListener;ZJLjava/util/List;ZLnet/minecraft/world/RandomSequences;)V"))
    private void stairs$mirrorGenerator(Args args) {
        if (!MirrorDimension.KEY.equals(args.get(4))) return;
        MinecraftServer server = args.get(0);
        var overworld = server.overworld();
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        // Separate generator instance: do not share mutable generation caches between worlds.
        var encoded = ChunkGenerator.CODEC.encodeStart(ops, overworld.getChunkSource().getGenerator()).getOrThrow();
        var generator = ChunkGenerator.CODEC.parse(ops, encoded).getOrThrow();
        var mirrorType = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mjontersstairs", "mirror")));
        LevelStem stem = new LevelStem(mirrorType, generator);
        args.set(5, stem);
        java.util.concurrent.Executor executor = args.get(1);
        net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess storage = args.get(2);
        net.minecraft.world.level.storage.ServerLevelData data = args.get(3);
        boolean debug = args.get(7);
        long seed = args.get(8);
        java.util.List<net.minecraft.world.level.CustomSpawner> spawners = args.get(9);
        boolean tickTime = args.get(10);
        net.minecraft.world.RandomSequences random = args.get(11);
        MirrorSnapshots.factory(server, () -> new net.minecraft.server.level.ServerLevel(server, executor, storage,
                data, MirrorDimension.KEY, stem, new net.minecraft.server.level.progress.ChunkProgressListener() {
                    public void updateSpawnPos(net.minecraft.world.level.ChunkPos pos) {}
                    public void onStatusChange(net.minecraft.world.level.ChunkPos pos, net.minecraft.world.level.chunk.status.ChunkStatus status) {}
                    public void start() {}
                    public void stop() {}
                }, debug, seed, spawners, tickTime, random));
    }
}
