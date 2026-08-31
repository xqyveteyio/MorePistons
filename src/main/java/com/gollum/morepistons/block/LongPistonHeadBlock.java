package com.gollum.morepistons.block;

import com.gollum.morepistons.MorePistons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class LongPistonHeadBlock extends PistonHeadBlock {
    public LongPistonHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return findBase(level, pos, state.getValue(FACING)) != null;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos basePos = findBase(level, pos, state.getValue(FACING));
        if (!level.isClientSide && basePos != null) {
            if (player.isCreative()) {
                level.setBlock(basePos, Blocks.AIR.defaultBlockState(), 35);
            } else {
                level.destroyBlock(basePos, true, player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    static BlockPos findBase(LevelReader level, BlockPos partPos, Direction facing) {
        BlockPos.MutableBlockPos cursor = partPos.mutable();
        for (int distance = 1; distance <= LongPistonBlock.MAX_LENGTH; distance++) {
            cursor.move(facing.getOpposite());
            BlockState behind = level.getBlockState(cursor);
            if (behind.getBlock() instanceof LongPistonBlock) {
                return behind.getValue(LongPistonBlock.FACING) == facing
                    && behind.getValue(LongPistonBlock.EXTENSION) >= distance
                    ? cursor.immutable()
                    : null;
            }
            if (!behind.is(MorePistons.LONG_PISTON_ROD)) {
                return null;
            }
        }
        return null;
    }
}
