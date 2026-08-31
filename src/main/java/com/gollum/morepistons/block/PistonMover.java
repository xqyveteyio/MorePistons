package com.gollum.morepistons.block;

import com.gollum.morepistons.MorePistons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Uses vanilla's structure resolver and moving block entity, but supplies the long-piston head. */
final class PistonMover {
    private static final int UPDATE_MOVE_BY_PISTON = 68;
    private static final int UPDATE_INVISIBLE = 82;

    private PistonMover() {
    }

    static boolean moveBlocks(Level level, BlockPos pistonPos, Direction pistonDirection,
                              boolean extending, boolean sticky) {
        BlockPos headPos = pistonPos.relative(pistonDirection);
        if (!extending && level.getBlockState(headPos).is(MorePistons.LONG_PISTON_HEAD)) {
            level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 20);
        }

        PistonStructureResolver resolver = new PistonStructureResolver(level, pistonPos, pistonDirection, extending);
        if (!resolver.resolve()) {
            MorePistons.LOGGER.debug("Piston move could not resolve at {} (direction={}, extending={}, head={}, pull={})",
                pistonPos, pistonDirection, extending, level.getBlockState(headPos),
                level.getBlockState(pistonPos.relative(pistonDirection, 2)));
            return false;
        }

        Map<BlockPos, BlockState> vacated = new HashMap<>();
        List<BlockPos> toPush = resolver.getToPush();
        List<BlockPos> toDestroy = resolver.getToDestroy();
        if (!extending && !toDestroy.isEmpty()) {
            // A sticky piston is allowed to leave a non-pullable block behind, but it must not
            // turn a failed pull into a block-breaking action. PistonStructureResolver reports
            // DESTROY-reaction blocks through this list even though this is a contraction.
            toPush = List.of();
            toDestroy = List.of();
        }
        List<BlockState> pushedStates = new ArrayList<>(toPush.size());
        for (BlockPos source : toPush) {
            BlockState sourceState = level.getBlockState(source);
            pushedStates.add(sourceState);
            vacated.put(source, sourceState);
        }

        BlockState[] changedStates = new BlockState[toPush.size() + toDestroy.size()];
        Direction movementDirection = extending ? pistonDirection : pistonDirection.getOpposite();
        int changed = 0;

        for (int i = toDestroy.size() - 1; i >= 0; i--) {
            BlockPos destroyPos = toDestroy.get(i);
            BlockState destroyState = level.getBlockState(destroyPos);
            BlockEntity blockEntity = destroyState.hasBlockEntity() ? level.getBlockEntity(destroyPos) : null;
            Block.dropResources(destroyState, level, destroyPos, blockEntity);
            level.setBlock(destroyPos, Blocks.AIR.defaultBlockState(), 18);
            level.gameEvent(GameEvent.BLOCK_DESTROY, destroyPos, GameEvent.Context.of(destroyState));
            if (!destroyState.is(BlockTags.FIRE)) {
                level.addDestroyBlockEffect(destroyPos, destroyState);
            }
            changedStates[changed++] = destroyState;
        }

        for (int i = toPush.size() - 1; i >= 0; i--) {
            BlockPos source = toPush.get(i);
            BlockState sourceState = level.getBlockState(source);
            BlockPos destination = source.relative(movementDirection);
            vacated.remove(destination);

            BlockState movingState = Blocks.MOVING_PISTON.defaultBlockState()
                .setValue(MovingPistonBlock.FACING, pistonDirection);
            level.setBlock(destination, movingState, UPDATE_MOVE_BY_PISTON);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(
                destination, movingState, pushedStates.get(i), pistonDirection, extending, false));
            changedStates[changed++] = sourceState;
        }

        if (extending) {
            PistonType type = sticky ? PistonType.STICKY : PistonType.DEFAULT;
            BlockState headState = MorePistons.LONG_PISTON_HEAD.defaultBlockState()
                .setValue(PistonHeadBlock.FACING, pistonDirection)
                .setValue(PistonHeadBlock.TYPE, type)
                .setValue(PistonHeadBlock.SHORT, false);
            BlockState movingHead = Blocks.MOVING_PISTON.defaultBlockState()
                .setValue(MovingPistonBlock.FACING, pistonDirection)
                .setValue(MovingPistonBlock.TYPE, type);
            vacated.remove(headPos);
            level.setBlock(headPos, movingHead, UPDATE_MOVE_BY_PISTON);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(
                headPos, movingHead, headState, pistonDirection, true, true));
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos source : vacated.keySet()) {
            level.setBlock(source, air, UPDATE_INVISIBLE);
        }
        for (Map.Entry<BlockPos, BlockState> entry : vacated.entrySet()) {
            BlockPos source = entry.getKey();
            BlockState oldState = entry.getValue();
            oldState.updateIndirectNeighbourShapes(level, source, 2);
            air.updateNeighbourShapes(level, source, 2);
            air.updateIndirectNeighbourShapes(level, source, 2);
        }

        changed = 0;
        for (int i = toDestroy.size() - 1; i >= 0; i--) {
            BlockState oldState = changedStates[changed++];
            BlockPos changedPos = toDestroy.get(i);
            oldState.updateIndirectNeighbourShapes(level, changedPos, 2);
            level.updateNeighborsAt(changedPos, oldState.getBlock());
        }
        for (int i = toPush.size() - 1; i >= 0; i--) {
            level.updateNeighborsAt(toPush.get(i), changedStates[changed++].getBlock());
        }
        if (extending) {
            level.updateNeighborsAt(headPos, MorePistons.LONG_PISTON_HEAD);
        }
        return true;
    }
}
