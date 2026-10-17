package com.notmjonter.stairs;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** A lamp whose emitted light and appearance follow its strongest neighboring redstone signal. */
public final class EchoLanternBlock extends Block {
    public static final MapCodec<EchoLanternBlock> CODEC = simpleCodec(EchoLanternBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public EchoLanternBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWER,
                context.getLevel().getBestNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
            BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            int power = level.getBestNeighborSignal(pos);
            if (power != state.getValue(POWER)) level.setBlock(pos, state.setValue(POWER, power), 2);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }
}
