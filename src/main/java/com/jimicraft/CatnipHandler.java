package com.jimicraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class CatnipHandler {
    public static Item CATNIP_ITEM;

    private static final Map<UUID, Integer> BOOSTED_CATS = new HashMap<>();
    private static final Map<UUID, ServerLevel> CAT_WORLDS = new HashMap<>();
    private static final int BOOST_DURATION = 6000;

    public static void init() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JimiCraftMod.id("catnip"));
        CATNIP_ITEM = Registry.register(BuiltInRegistries.ITEM, key,
                new Item(new Item.Properties().stacksTo(64).setId(key)));

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) return;
            if (isGrassBlock(state.getBlock()) && world.getRandom().nextFloat() < 0.08f) {
                ItemEntity drop = new ItemEntity(world,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(CATNIP_ITEM));
                world.addFreshEntity(drop);
            }
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (entity instanceof Cat cat && cat.isTame()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.is(CATNIP_ITEM)) {
                    stack.shrink(1);
                    applyBoost(cat, BOOST_DURATION);
                    ServerLevel sw = (ServerLevel) world;
                    for (ServerPlayer sp : sw.players()) {
                        for (int i = 0; i < 8; i++) {
                            sw.sendParticles(sp, ParticleTypes.HEART, false, false,
                                    cat.getX() + (sw.getRandom().nextDouble() - 0.5) * 0.8,
                                    cat.getY() + 0.6 + sw.getRandom().nextDouble() * 0.6,
                                    cat.getZ() + (sw.getRandom().nextDouble() - 0.5) * 0.8,
                                    1, 0, 0, 0, 0);
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            if (BOOSTED_CATS.isEmpty()) return;
            Iterator<Map.Entry<UUID, Integer>> it = BOOSTED_CATS.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                UUID id = entry.getKey();
                // 跳过别的世界的猫
                if (CAT_WORLDS.get(id) != world) continue;
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    it.remove();
                    CAT_WORLDS.remove(id);
                } else {
                    entry.setValue(remaining);
                    if (remaining % 15 == 0) {
                        var entity = world.getEntity(id);
                        if (entity instanceof Cat cat) {
                            for (ServerPlayer sp : world.players()) {
                                world.sendParticles(sp, ParticleTypes.HEART, false, false,
                                        cat.getX(), cat.getY() + 1.2, cat.getZ(),
                                        1, 0.15, 0.1, 0.15, 0.02);
                            }
                        }
                    }
                }
            }
        });

        System.out.println("[JimiCraft] 猫薄荷系统已加载！");
    }

    private static boolean isGrassBlock(Block block) {
        return block == Blocks.SHORT_GRASS
                || block == Blocks.TALL_GRASS
                || block == Blocks.FERN
                || block == Blocks.LARGE_FERN;
    }

    public static void applyBoost(Cat cat, int ticks) {
        UUID id = cat.getUUID();
        BOOSTED_CATS.put(id, ticks);
        CAT_WORLDS.put(id, (ServerLevel) cat.level());
    }

    public static boolean isCatnipped(UUID uuid) {
        return BOOSTED_CATS.containsKey(uuid);
    }
}
