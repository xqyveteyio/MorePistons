package com.gollum.morepistons.gametest;

import com.gollum.morepistons.MorePistons;
import com.gollum.morepistons.block.LongPistonBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class LongPistonGameTests implements FabricGameTest {
    private static final BlockPos BASE = new BlockPos(1, 2, 1);
    private static final BlockPos POWER = BASE.relative(Direction.WEST);

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
    public void longPistonPushesAndLeavesBlock(GameTestHelper helper) {
        LongPistonBlock piston = MorePistons.LONG_PISTONS.get(2);
        helper.setBlock(BASE, piston.defaultBlockState()
            .setValue(LongPistonBlock.FACING, Direction.EAST));
        helper.setBlock(BASE.relative(Direction.EAST), Blocks.STONE);
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);

        helper.runAtTickTime(16, () -> {
            helper.assertBlockProperty(BASE, LongPistonBlock.EXTENSION, 2);
            helper.assertBlockPresent(MorePistons.LONG_PISTON_ROD, BASE.relative(Direction.EAST));
            helper.assertBlockPresent(MorePistons.LONG_PISTON_HEAD, BASE.relative(Direction.EAST, 2));
            helper.assertBlockPresent(Blocks.STONE, BASE.relative(Direction.EAST, 3));
            helper.setBlock(POWER, Blocks.AIR);
        });

        helper.runAtTickTime(36, () -> {
            helper.assertBlockProperty(BASE, LongPistonBlock.EXTENSION, 0);
            helper.assertBlockPresent(Blocks.STONE, BASE.relative(Direction.EAST, 3));
            helper.assertBlockNotPresent(MorePistons.LONG_PISTON_HEAD, BASE.relative(Direction.EAST, 2));
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
    public void longStickyPistonPullsBlockHome(GameTestHelper helper) {
        LongPistonBlock piston = MorePistons.LONG_STICKY_PISTONS.get(2);
        helper.setBlock(BASE, piston.defaultBlockState()
            .setValue(LongPistonBlock.FACING, Direction.EAST));
        helper.setBlock(BASE.relative(Direction.EAST), Blocks.STONE);
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);

        helper.runAtTickTime(16, () -> {
            helper.assertBlockProperty(BASE, LongPistonBlock.EXTENSION, 2);
            helper.assertBlockPresent(Blocks.STONE, BASE.relative(Direction.EAST, 3));
            helper.setBlock(POWER, Blocks.AIR);
        });

        helper.runAtTickTime(36, () -> {
            helper.assertBlockProperty(BASE, LongPistonBlock.EXTENSION, 0);
            helper.assertTrue(helper.getBlockState(BASE.relative(Direction.EAST)).is(Blocks.STONE),
                "Expected pulled stone at +1; states were +1="
                    + helper.getBlockState(BASE.relative(Direction.EAST))
                    + ", +2=" + helper.getBlockState(BASE.relative(Direction.EAST, 2))
                    + ", +3=" + helper.getBlockState(BASE.relative(Direction.EAST, 3)));
            helper.assertBlockNotPresent(MorePistons.LONG_PISTON_ROD, BASE.relative(Direction.EAST, 2));
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
    public void longStickyPistonDoesNotDestroyUnpullableBlock(GameTestHelper helper) {
        LongPistonBlock piston = MorePistons.LONG_STICKY_PISTONS.get(2);
        helper.setBlock(BASE, piston.defaultBlockState()
            .setValue(LongPistonBlock.FACING, Direction.EAST));
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);

        helper.runAtTickTime(16, () -> {
            helper.assertBlockProperty(BASE, LongPistonBlock.EXTENSION, 2);
            helper.setBlock(BASE.relative(Direction.EAST, 3), Blocks.COBWEB);
            helper.setBlock(POWER, Blocks.AIR);
        });

        helper.runAtTickTime(36, () -> {
            helper.assertBlockProperty(BASE, LongPistonBlock.EXTENSION, 0);
            helper.assertBlockPresent(Blocks.COBWEB, BASE.relative(Direction.EAST, 3));
            helper.succeed();
        });
    }
}
