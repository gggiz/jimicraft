package com.jimicraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class LumberMillWorkHandler {
    private static final Map<QuarryArea, List<BlockPos>> CHEST_CACHE = new HashMap<>();
    private static final Map<UUID, Integer> NAME_DISPLAYS = new HashMap<>();
    private static final Map<UUID, Component> ORIGINAL_NAMES = new HashMap<>();
    private static final Map<Display.ItemDisplay, UUID> TOOL_CATS = new HashMap<>();
    private static final Set<UUID> CATS_WITH_TOOLS = new HashSet<>();
    private static int tickCounter = 0;

    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(LumberMillWorkHandler::onWorldTick);
    }

    private static void onWorldTick(ServerLevel world) {
        tickCounter++;

        Iterator<Map.Entry<UUID, Integer>> nameIt = NAME_DISPLAYS.entrySet().iterator();
        while (nameIt.hasNext()) {
            var entry = nameIt.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                var entity = world.getEntity(entry.getKey());
                if (entity instanceof Cat cat) {
                    cat.setCustomName(ORIGINAL_NAMES.remove(entry.getKey()));
                }
                nameIt.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        Iterator<Map.Entry<Display.ItemDisplay, UUID>> toolIt = TOOL_CATS.entrySet().iterator();
        while (toolIt.hasNext()) {
            var entry = toolIt.next();
            Display.ItemDisplay tool = entry.getKey();
            UUID catId = entry.getValue();
            if (tool.level() != world) continue;
            if (tool.isRemoved()) {
                CATS_WITH_TOOLS.remove(catId);
                toolIt.remove();
                continue;
            }
            var entity = world.getEntity(catId);
            if (!(entity instanceof Cat cat) || !cat.isAlive() || !isInMill(world, cat)) {
                tool.discard();
                CATS_WITH_TOOLS.remove(catId);
                toolIt.remove();
                continue;
            }
            float yawRad = cat.yBodyRot * (float) Math.PI / 180f;
            double fx = -Math.sin(yawRad) * 0.4;
            double fz = Math.cos(yawRad) * 0.4;
            double rx = Math.cos(yawRad) * 0.3;
            double rz = Math.sin(yawRad) * 0.3;
            double bobY = cat.getY() + 0.9 + Math.sin(tickCounter * 0.5) * 0.15;
            tool.setPos(cat.getX() + fx + rx, bobY, cat.getZ() + fz + rz);
            tool.setYRot(cat.yBodyRot);
        }

        if (tickCounter % 200 == 0) {
            CHEST_CACHE.clear();
        }

        if (tickCounter % 200 != 0) return;

        for (QuarryArea mill : LumberMillCreationHandler.getMills()) {
            if (mill.world() != world) continue;

            AABB bounds = mill.getBounds();
            List<Cat> cats = world.getEntitiesOfClass(Cat.class, bounds, Cat::isTame);
            int count = cats.size();
            if (count == 0) continue;

            List<BlockPos> chests = CHEST_CACHE.computeIfAbsent(mill,
                    q -> findChestsInBounds(world, bounds));

            int effective = Math.min(count, 5);
            RandomSource rand = world.getRandom();
            List<Cat> shuffled = new ArrayList<>(cats);
            Collections.shuffle(shuffled, new java.util.Random());

            for (int i = 0; i < Math.min(effective, shuffled.size()); i++) {
                Cat cat = shuffled.get(i);
                boolean boosted = CatnipHandler.isCatnipped(cat.getUUID());
                float chance = boosted ? 0.9f : 0.6f;
                int rareBonus = boosted ? 2 : 0;
                if (rand.nextFloat() < chance) {
                    ItemStack wood = getRandomWood(rand, effective + rareBonus);
                    depositWood(world, chests, bounds, wood);
                    if (boosted && rand.nextFloat() < 0.5f) {
                        depositWood(world, chests, bounds, getRandomWood(rand, effective + rareBonus));
                    }
                    showCatMined(world, cat, wood);
                }
            }
        }
    }

    private static void showCatMined(ServerLevel world, Cat cat, ItemStack wood) {
        UUID id = cat.getUUID();
        if (!NAME_DISPLAYS.containsKey(id)) {
            ORIGINAL_NAMES.put(id, cat.getCustomName());
        }
        String woodName = wood.getHoverName().getString();
        cat.setCustomName(Component.literal("§2🪓 §e" + woodName));
        NAME_DISPLAYS.put(id, 40);

        if (!CATS_WITH_TOOLS.contains(id)) {
            CATS_WITH_TOOLS.add(id);
            Display.ItemDisplay tool = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, world);
            float yawRad = cat.yBodyRot * (float) Math.PI / 180f;
            double fx = -Math.sin(yawRad) * 0.4;
            double fz = Math.cos(yawRad) * 0.4;
            double rx = Math.cos(yawRad) * 0.3;
            double rz = Math.sin(yawRad) * 0.3;
            tool.setPos(cat.getX() + fx + rx, cat.getY() + 0.9, cat.getZ() + fz + rz);
            tool.setYRot(cat.yBodyRot);
            tool.setItemStack(new ItemStack(Items.IRON_AXE));
            tool.setItemTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            world.addFreshEntity(tool);
            TOOL_CATS.put(tool, id);
        }

        RandomSource rand = world.getRandom();
        for (ServerPlayer player : world.players()) {
            for (int i = 0; i < 3; i++) {
                double ox = cat.getX() + (rand.nextDouble() - 0.5) * 0.6;
                double oy = cat.getY() + 0.1 + rand.nextDouble() * 0.4;
                double oz = cat.getZ() + (rand.nextDouble() - 0.5) * 0.6;
                world.sendParticles(player, ParticleTypes.POOF, false, false,
                        ox, oy, oz, 1, 0, 0.05, 0, 0.02);
            }
        }
    }

    private static boolean isInMill(ServerLevel world, Cat cat) {
        for (QuarryArea m : LumberMillCreationHandler.getMills()) {
            if (m.world() == world && m.getBounds().contains(cat.position())) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> findChestsInBounds(ServerLevel world, AABB bounds) {
        List<BlockPos> chests = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = (int) bounds.minX; x <= (int) bounds.maxX; x++) {
            for (int y = (int) bounds.minY; y <= (int) bounds.maxY; y++) {
                for (int z = (int) bounds.minZ; z <= (int) bounds.maxZ; z++) {
                    mp.set(x, y, z);
                    if (world.getBlockEntity(mp) instanceof ChestBlockEntity) {
                        chests.add(mp.immutable());
                    }
                }
            }
        }
        return chests;
    }

    private static void depositWood(ServerLevel world, List<BlockPos> chests, AABB bounds, ItemStack wood) {
        for (BlockPos chestPos : chests) {
            BlockEntity be = world.getBlockEntity(chestPos);
            if (be instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack slot = chest.getItem(i);
                    if (slot.isEmpty()) {
                        chest.setItem(i, wood);
                        return;
                    } else if (ItemStack.isSameItem(slot, wood) && slot.getCount() < slot.getMaxStackSize()) {
                        slot.grow(wood.getCount());
                        return;
                    }
                }
            }
        }
        double cx = (bounds.minX + bounds.maxX) / 2;
        double cz = (bounds.minZ + bounds.maxZ) / 2;
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                world, cx, bounds.minY + 1, cz, wood);
        world.addFreshEntity(drop);
    }

    // 主世界全部10种木材，均匀分布
    private static final Item[] OVERWORLD_LOGS = {
        Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.ACACIA_LOG,
        Items.JUNGLE_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
        Items.BAMBOO_BLOCK, Items.PALE_OAK_LOG
    };

    private static ItemStack getRandomWood(RandomSource rand, int catCount) {
        return new ItemStack(OVERWORLD_LOGS[rand.nextInt(OVERWORLD_LOGS.length)]);
    }
}
