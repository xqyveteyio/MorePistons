package com.gollum.morepistons;

import com.gollum.morepistons.block.LongPistonBlock;
import com.gollum.morepistons.block.LongPistonHeadBlock;
import com.gollum.morepistons.block.LongPistonRodBlock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MorePistons implements ModInitializer {
    public static final String MOD_ID = "morepistons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final LongPistonHeadBlock LONG_PISTON_HEAD = new LongPistonHeadBlock(
        BlockBehaviour.Properties.copy(Blocks.PISTON_HEAD).noLootTable()
    );
    public static final LongPistonRodBlock LONG_PISTON_ROD = new LongPistonRodBlock(
        BlockBehaviour.Properties.copy(Blocks.PISTON_HEAD).noLootTable()
    );

    public static final Map<Integer, LongPistonBlock> LONG_PISTONS = new LinkedHashMap<>();
    public static final Map<Integer, LongPistonBlock> LONG_STICKY_PISTONS = new LinkedHashMap<>();

    @Override
    public void onInitialize() {
        registerBlockOnly("long_piston_head", LONG_PISTON_HEAD);
        registerBlockOnly("long_piston_rod", LONG_PISTON_ROD);

        for (int length = 2; length <= LongPistonBlock.MAX_LENGTH; length++) {
            LONG_PISTONS.put(length, registerPiston("long_piston_" + length, length, false));
            LONG_STICKY_PISTONS.put(length, registerPiston("long_sticky_piston_" + length, length, true));
        }

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            LONG_PISTONS.values().forEach(entries::accept);
            LONG_STICKY_PISTONS.values().forEach(entries::accept);
        });

        LOGGER.info("Registered long pistons with lengths 2 through {}", LongPistonBlock.MAX_LENGTH);
    }

    private static LongPistonBlock registerPiston(String path, int length, boolean sticky) {
        LongPistonBlock block = new LongPistonBlock(
            length,
            sticky,
            BlockBehaviour.Properties.copy(sticky ? Blocks.STICKY_PISTON : Blocks.PISTON)
        );
        ResourceLocation id = id(path);
        Block registered = net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, id, block);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(registered, new Item.Properties()));
        return block;
    }

    private static void registerBlockOnly(String path, Block block) {
        net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, id(path), block);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
