package com.gollum.morepistons.block;

import com.gollum.morepistons.MorePistons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public final class LongPistonBlock extends DirectionalBlock {
    public static final int MAX_LENGTH = 8;
    public static final IntegerProperty EXTENSION = IntegerProperty.create("extension", 0, MAX_LENGTH);
    private static final int EVENT_EXTEND = 0;
    private static final int EVENT_RETRACT = 1;
    // Vanilla's animation advances by half a block per tick. On the third tick the previous
    // moving entities are complete and can be handed directly to the next segment.
    private static final int STEP_DELAY = 3;

    private static final VoxelShape EAST_EXTENDED = box(0, 0, 0, 12, 16, 16);
    private static final VoxelShape WEST_EXTENDED = box(4, 0, 0, 16, 16, 16);
    private static final VoxelShape SOUTH_EXTENDED = box(0, 0, 0, 16, 16, 12);
    private static final VoxelShape NORTH_EXTENDED = box(0, 0, 4, 16, 16, 16);
    private static final VoxelShape UP_EXTENDED = box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape DOWN_EXTENDED = box(0, 4, 0, 16, 16, 16);

    private final int length;
    private final boolean sticky;

    public LongPistonBlock(int length, boolean sticky, Properties properties) {
        super(properties);
        if (length < 2 || length > MAX_LENGTH) {
            throw new IllegalArgumentException("Long piston length must be between 2 and " + MAX_LENGTH);
        }
        this.length = length;
        this.sticky = sticky;
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(EXTENSION, 0)
            .setValue(PistonBaseBlock.EXTENDED, false));
    }

    public int getLength() {
        return length;
    }

    public boolean isSticky() {
        return sticky;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getNearestLookingDirection().getOpposite())
            .setValue(EXTENSION, 0)
            .setValue(PistonBaseBlock.EXTENDED, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        scheduleIfNeeded(level, pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            scheduleIfNeeded(level, pos, state);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean movedByPiston) {
        scheduleIfNeeded(level, pos, state);
    }

    @Override
    public void tick(BlockState ignoredState, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != this) {
            return;
        }

        int current = state.getValue(EXTENSION);
        boolean powered = hasPistonSignal(level, pos, state.getValue(FACING));
        if (!finishPreviousStep(level, pos, state.getValue(FACING), current)) {
            level.scheduleTick(pos, this, 1);
            return;
        }

        if (powered && current < length) {
            Direction facing = state.getValue(FACING);
            BlockPos virtualBase = pos.relative(facing, current);
            if (new PistonStructureResolver(level, virtualBase, facing, true).resolve()) {
                level.blockEvent(pos, this, EVENT_EXTEND, current);
            }
        } else if (!powered && current > 0) {
            level.blockEvent(pos, this, EVENT_RETRACT, current);
        }
    }

    /**
     * Block events are delivered to both the logical server and every client. Creating the
     * vanilla moving block entities on both sides is essential: their animation data is not
     * otherwise carried by an ordinary block-state update.
     */
    @Override
    public boolean triggerEvent(BlockState eventState, Level level, BlockPos pos, int eventId, int previousExtension) {
        if (eventId != EVENT_EXTEND && eventId != EVENT_RETRACT) {
            return super.triggerEvent(eventState, level, pos, eventId, previousExtension);
        }
        if (previousExtension < 0 || previousExtension > length) {
            return false;
        }

        Direction facing = eventState.getValue(FACING);
        if (!level.isClientSide) {
            boolean powered = hasPistonSignal(level, pos, facing);
            if ((eventId == EVENT_EXTEND && !powered) || (eventId == EVENT_RETRACT && powered)) {
                return false;
            }
        }

        // The event contains the authoritative pre-step length, so a client does not depend on
        // whether its base block-state update arrived just before or just after the event packet.
        BlockState state = eventState.setValue(EXTENSION, previousExtension);
        boolean moved = eventId == EVENT_EXTEND
            ? extendOne(level, pos, state, previousExtension)
            : retractOne(level, pos, state, previousExtension);

        if (moved && !level.isClientSide && level.getBlockState(pos).getBlock() == this) {
            int now = level.getBlockState(pos).getValue(EXTENSION);
            boolean powered = hasPistonSignal(level, pos, facing);
            if ((powered && now < length) || (!powered && now > 0)) {
                level.scheduleTick(pos, this, STEP_DELAY);
            }
        }
        return moved;
    }

    private static boolean finishPreviousStep(Level level, BlockPos basePos, Direction facing, int current) {
        // A piston may move a line of twelve blocks. Finalize every completed moving entity in
        // front before resolving the next one-block segment, eliminating a stationary gap.
        return finishPreviousStep(level, basePos, facing, current, false);
    }

    private static boolean finishPreviousStep(Level level, BlockPos basePos, Direction facing,
                                              int current, boolean force) {
        int firstDistance = Math.max(0, current - 1);
        boolean waiting = false;
        for (int distance = firstDistance; distance <= current + 13; distance++) {
            BlockPos movingPos = basePos.relative(facing, distance);
            if (!level.getBlockState(movingPos).is(Blocks.MOVING_PISTON)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(movingPos);
            if (blockEntity instanceof PistonMovingBlockEntity moving && moving.getDirection() == facing) {
                if (force || moving.getProgress(0.0F) >= 1.0F) {
                    moving.finalTick();
                } else {
                    waiting = true;
                }
            }
        }
        return !waiting;
    }

    private boolean extendOne(Level level, BlockPos basePos, BlockState state, int current) {
        if (current < 0 || current >= length) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        finishPreviousStep(level, basePos, facing, current, true);
        BlockPos virtualBase = basePos.relative(facing, current);
        BlockState advanced = state
            .setValue(EXTENSION, current + 1)
            .setValue(PistonBaseBlock.EXTENDED, true);
        level.setBlock(basePos, advanced, 67);

        if (!PistonMover.moveBlocks(level, virtualBase, facing, true, sticky)) {
            level.setBlock(basePos, state, 67);
            return false;
        }

        if (current > 0) {
            level.setBlock(virtualBase, MorePistons.LONG_PISTON_ROD.defaultBlockState()
                .setValue(LongPistonRodBlock.FACING, facing), 67);
        }

        if (current == 0) {
            level.playSound(null, basePos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F,
                level.random.nextFloat() * 0.25F + 0.6F);
        }
        return true;
    }

    private boolean retractOne(Level level, BlockPos basePos, BlockState state, int current) {
        if (current <= 0 || current > length) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        finishPreviousStep(level, basePos, facing, current, true);
        int next = current - 1;
        BlockPos virtualBase = basePos.relative(facing, next);
        BlockPos oldHead = virtualBase.relative(facing);
        level.setBlock(basePos, state
            .setValue(EXTENSION, next)
            .setValue(PistonBaseBlock.EXTENDED, next > 0), 67);

        if (sticky) {
            PistonMover.moveBlocks(level, virtualBase, facing, false, true);
        } else if (level.getBlockState(oldHead).is(MorePistons.LONG_PISTON_HEAD)) {
            level.setBlock(oldHead, Blocks.AIR.defaultBlockState(), 67);
        }

        if (next > 0) {
            BlockState headState = MorePistons.LONG_PISTON_HEAD.defaultBlockState()
                .setValue(LongPistonHeadBlock.FACING, facing)
                .setValue(LongPistonHeadBlock.TYPE, sticky ? PistonType.STICKY : PistonType.DEFAULT)
                .setValue(LongPistonHeadBlock.SHORT, false);
            BlockState movingState = Blocks.MOVING_PISTON.defaultBlockState()
                .setValue(MovingPistonBlock.FACING, facing)
                .setValue(MovingPistonBlock.TYPE,
                    sticky ? PistonType.STICKY : PistonType.DEFAULT);
            level.setBlock(virtualBase, movingState, 68);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(
                virtualBase, movingState, headState, facing, false, false));
        } else {
            // The final segment mirrors a vanilla retract: the base temporarily becomes a moving
            // source entity, whose renderer draws the head sliding into the base.
            BlockState contractedBase = state
                .setValue(EXTENSION, 0)
                .setValue(PistonBaseBlock.EXTENDED, false);
            BlockState movingState = Blocks.MOVING_PISTON.defaultBlockState()
                .setValue(MovingPistonBlock.FACING, facing)
                .setValue(MovingPistonBlock.TYPE,
                    sticky ? PistonType.STICKY : PistonType.DEFAULT);
            level.setBlock(basePos, movingState, 20);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(
                basePos, movingState, contractedBase, facing, false, true));
            level.blockUpdated(basePos, movingState.getBlock());
        }

        if (next == 0) {
            level.playSound(null, basePos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F,
                level.random.nextFloat() * 0.15F + 0.6F);
        }
        return true;
    }

    private void scheduleIfNeeded(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || level.getBlockTicks().hasScheduledTick(pos, this)) {
            return;
        }
        boolean powered = hasPistonSignal(level, pos, state.getValue(FACING));
        int current = state.getValue(EXTENSION);
        if ((powered && current < length) || (!powered && current > 0)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    private static boolean hasPistonSignal(Level level, BlockPos pos, Direction facing) {
        for (Direction direction : Direction.values()) {
            if (direction != facing && level.hasSignal(pos.relative(direction), direction)) {
                return true;
            }
        }
        if (level.hasSignal(pos, Direction.DOWN)) {
            return true;
        }
        BlockPos above = pos.above();
        for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN && level.hasSignal(above.relative(direction), direction)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !newState.is(Blocks.MOVING_PISTON)) {
            cleanupParts(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void cleanupParts(Level level, BlockPos basePos, Direction facing) {
        for (int distance = 1; distance <= MAX_LENGTH; distance++) {
            BlockPos partPos = basePos.relative(facing, distance);
            BlockState part = level.getBlockState(partPos);
            if (part.is(MorePistons.LONG_PISTON_ROD) || part.is(MorePistons.LONG_PISTON_HEAD)) {
                level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
                continue;
            }
            if (part.is(Blocks.MOVING_PISTON)) {
                BlockEntity blockEntity = level.getBlockEntity(partPos);
                if (blockEntity instanceof PistonMovingBlockEntity moving
                    && moving.isSourcePiston()
                    && moving.getMovedState().is(MorePistons.LONG_PISTON_HEAD)) {
                    moving.finalTick();
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
                    continue;
                }
            }
            break;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // The vanilla renderer temporarily flips EXTENDED while drawing the last retracting
        // segment, so the long-piston base must expose that same property.
        builder.add(FACING, EXTENSION, PistonBaseBlock.EXTENDED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(EXTENSION) == 0) {
            return net.minecraft.world.phys.shapes.Shapes.block();
        }
        return switch (state.getValue(FACING)) {
            case EAST -> EAST_EXTENDED;
            case WEST -> WEST_EXTENDED;
            case SOUTH -> SOUTH_EXTENDED;
            case NORTH -> NORTH_EXTENDED;
            case UP -> UP_EXTENDED;
            case DOWN -> DOWN_EXTENDED;
        };
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this));
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }
}
