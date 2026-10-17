package com.notmjonter.stairs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.Optional;

/** A harmless luminous fluid with vanilla lava flow and water-contact behavior. */
public final class EchoLava {
    public static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Stairs.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Stairs.MODID);
    public static final DeferredHolder<FluidType, FluidType> TYPE = TYPES.register("echo_lava",
            () -> new FluidType(FluidType.Properties.create().descriptionId("fluid.mjontersstairs.echo_lava")
                    .density(3000).viscosity(6000).temperature(300).lightLevel(15)
                    .motionScale(0.0023333333333333335).canSwim(true).canDrown(false).canExtinguish(true)
                    .pathType(PathType.WATER).adjacentPathType(PathType.WATER_BORDER)
                    .sound(SoundActions.BUCKET_FILL, EchoSounds.LAVA_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, EchoSounds.LAVA_EMPTY)));
    public static final DeferredHolder<Fluid, Source> SOURCE = FLUIDS.register("echo_lava", Source::new);
    public static final DeferredHolder<Fluid, Flowing> FLOWING = FLUIDS.register("flowing_echo_lava", Flowing::new);
    public static final DeferredBlock<LiquidBlock> BLOCK = FutureBlocks.BLOCKS.register("echo_lava",
            () -> new LiquidBlock(SOURCE.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)));
    public static final DeferredItem<BucketItem> BUCKET = FutureBlocks.ITEMS.register("echo_lava_bucket",
            () -> new BucketItem(SOURCE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private EchoLava() {}
    public static void initialize() {}

    public abstract static class Base extends LavaFluid {
        @Override public Fluid getSource() { return SOURCE.get(); }
        @Override public Fluid getFlowing() { return FLOWING.get(); }
        @Override public FluidType getFluidType() { return TYPE.get(); }
        @Override public Item getBucket() { return BUCKET.get(); }
        @Override public Optional<SoundEvent> getPickupSound() { return Optional.of(EchoSounds.LAVA_FILL); }
        @Override public boolean isSame(Fluid other) { return other == SOURCE.get() || other == FLOWING.get(); }
        @Override public BlockState createLegacyBlock(FluidState state) {
            return BLOCK.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
        }
        @Override public ParticleOptions getDripParticle() { return ParticleTypes.DRIPPING_OBSIDIAN_TEAR; }
        @Override public void randomTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
            // Keep lava flow, but never ignite nearby blocks or entities.
        }
        @Override public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
            if (!level.getBlockState(pos.above()).isAir()) return;
            if (random.nextInt(100) == 0) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + random.nextDouble(),
                        pos.getY() + 1, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
                level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        EchoSounds.LAVA_POP, SoundSource.BLOCKS, 0.2f, 0.8f, false);
            }
            if (random.nextInt(200) == 0) level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(),
                    EchoSounds.LAVA_AMBIENT, SoundSource.BLOCKS, 0.25f, 0.75f, false);
        }
    }

    public static final class Source extends Base {
        @Override public int getAmount(FluidState state) { return 8; }
        @Override public boolean isSource(FluidState state) { return true; }
    }

    public static final class Flowing extends Base {
        @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }
        @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
        @Override public boolean isSource(FluidState state) { return false; }
    }
}
