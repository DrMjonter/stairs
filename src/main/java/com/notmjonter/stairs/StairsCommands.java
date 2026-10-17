package com.notmjonter.stairs;

import java.util.Set;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class StairsCommands {
    private static final int TELEPORT_RADIUS = 10_000;
    private static final int MAX_ATTEMPTS = 16;

    private StairsCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stairs")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn").executes(context -> spawnStaircase(context.getSource(), 0))
                        .then(Commands.literal("future").executes(context -> spawnStaircase(context.getSource(), 1)))
                        .then(Commands.literal("unfinished_sun").executes(context -> spawnStaircase(context.getSource(), UnfinishedSunDimension.DESTINATION)))
                        .then(Commands.literal("echo").executes(context -> spawnStaircase(context.getSource(), EchoBasinDimension.DESTINATION)))
                        .then(Commands.literal("tickstorm").executes(context -> spawnStaircase(context.getSource(), TickstormDimension.DESTINATION)))
                        .then(Commands.literal("garbage_collector").executes(context -> spawnStaircase(context.getSource(), GarbageCollectorDimension.DESTINATION)))
                        .then(Commands.literal("mirror").executes(context -> spawnStaircase(context.getSource(), MirrorDimension.DESTINATION))))
                .then(Commands.literal("return").executes(context -> GarageTravel.returnPlayer(context.getSource().getPlayerOrException())))
                .then(Commands.literal("mirror")
                        .then(Commands.literal("snapshot").executes(context -> MirrorSnapshots.request(context.getSource())))
                        .then(Commands.literal("status").executes(context -> MirrorSnapshots.status(context.getSource()))))
                .then(Commands.literal("echo")
                        .then(Commands.literal("whisper").executes(context -> testEchoWhisper(context.getSource()))))
                .then(Commands.literal("rtp")
                        .executes(context -> teleportRandomly(context.getSource()))));
    }

    private static int testEchoWhisper(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.serverLevel().dimension() != EchoBasinDimension.KEY) {
            source.sendFailure(Component.translatable("command.mjontersstairs.echo.whisper_wrong_dimension"));
            return 0;
        }
        if (!EchoListeningWells.playGuidanceWhisper(player)) {
            source.sendFailure(Component.translatable("command.mjontersstairs.echo.whisper_no_well"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mjontersstairs.echo.whisper_played"), false);
        return 1;
    }

    private static int spawnStaircase(CommandSourceStack source, int destination) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.translatable("command.mjontersstairs.spawn.wrong_dimension"));
            return 0;
        }
        // Place beside the player, with the first tread at foot level.
        BlockPos origin = player.blockPosition().offset(3, -1, 0);
        StaircaseData data = StaircaseData.get(level);
        if (data.stairs.containsKey(origin)) {
            source.sendFailure(Component.translatable("command.mjontersstairs.spawn.already_recorded"));
            return 0;
        }
        int result = PlaceCommand.placeTemplate(source,
                ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "forest_stairs"),
                origin, Rotation.NONE, Mirror.NONE, 1.0F, 0);
        if (result == 1) {
            if (destination != 0) {
                data.stairs.put(origin, new StaircaseData.Staircase(origin, Rotation.NONE, destination));
                data.setDirty();
            } else data.register(origin, Rotation.NONE, level.getSeed());
        }
        return result;
    }

    private static int teleportRandomly(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.translatable("command.mjontersstairs.rtp.wrong_dimension"));
            return 0;
        }

        double originX = player.getX();
        double originZ = player.getZ();
        RandomSource random = level.getRandom();
        source.sendSuccess(() -> Component.translatable("command.mjontersstairs.rtp.searching"), false);

        // Bound chunk generation work, and sample a circle rather than a square
        // whose corners would exceed the requested travel distance.
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(random.nextDouble()) * TELEPORT_RADIUS;
            int x = Mth.floor(originX + Math.cos(angle) * distance);
            int z = Mth.floor(originZ + Math.sin(angle) * distance);
            double dx = x + 0.5 - originX;
            double dz = z + 0.5 - originZ;
            if (dx * dx + dz * dz > (double) TELEPORT_RADIUS * TELEPORT_RADIUS) {
                continue;
            }

            // Check the entire destination column against the border before
            // requesting a chunk, including space for the player's body.
            if (!level.getWorldBorder().isWithinBounds(x, z)
                    || !level.getWorldBorder().isWithinBounds(x + 1, z + 1)) {
                continue;
            }
            // Fully generate the chunk before inspecting its surface blocks.
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!isSafeLanding(level, feet)) {
                continue;
            }

            if (!player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot())) {
                source.sendFailure(Component.translatable("command.mjontersstairs.rtp.failed"));
                return 0;
            }
            player.stopFallFlying();
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            player.setOnGround(true);
            source.sendSuccess(() -> Component.translatable("command.mjontersstairs.rtp.success", x, y, z), false);
            return 1;
        }

        source.sendFailure(Component.translatable("command.mjontersstairs.rtp.no_safe_location"));
        return 0;
    }

    static boolean isSafeLanding(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
            return false;
        }

        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        return ground.getFluidState().isEmpty()
                && !ground.is(BlockTags.LEAVES)
                && !ground.is(Blocks.MAGMA_BLOCK)
                && !ground.is(Blocks.CACTUS)
                && !ground.is(Blocks.POWDER_SNOW)
                && Block.isFaceFull(ground.getCollisionShape(level, below), Direction.UP);
    }
}
