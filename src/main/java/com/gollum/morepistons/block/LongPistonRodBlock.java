package com.gollum.morepistons.block;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LongPistonRodBlock extends DirectionalBlock {
    private static final VoxelShape X_SHAPE = box(0, 6, 6, 16, 10, 10);
    private static final VoxelShape Y_SHAPE = box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape Z_SHAPE = box(6, 6, 0, 10, 10, 16);

    public LongPistonRodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos basePos = LongPistonHeadBlock.findBase(level, pos, state.getValue(FACING));
        if (!level.isClientSide && basePos != null) {
            if (player.isCreative()) {
                level.setBlock(basePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
            } else {
                level.destroyBlock(basePos, true, player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
