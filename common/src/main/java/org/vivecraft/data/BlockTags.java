package org.vivecraft.data;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * holds Vivecrafts tags to identify Blocks
 */
public class BlockTags {
    public static final TagKey<Block> VIVECRAFT_CLIMBABLE = tag("climbable");

    public static final TagKey<Block> VIVECRAFT_CROPS = tag("crops");

    public static final TagKey<Block> VIVECRAFT_MUSIC_BLOCKS = tag("music_blocks");

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("vivecraft", name));
    }
}