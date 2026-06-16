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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class QuarryWorkHandler {
    private static final Map<QuarryArea, List<BlockPos>> CHEST_CACHE = new HashMap<>();
    // 头顶显示计时器: catUUID -> remaining ticks
    private static final Map<UUID, Integer> NAME_DISPLAYS = new HashMap<>();
    // 保存猫的原始名字
    private static final Map<UUID, Component> ORIGINAL_NAMES = new HashMap<>();
    // 工具展示实体UUID -> 猫的UUID
    private static final Map<UUID, UUID> TOOL_CATS = new HashMap<>();
    private static final Set<UUID> CATS_WITH_TOOLS = new HashSet<>();
    private static int tickCounter = 0;

    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(QuarryWorkHandler::onWorldTick);
    }

    private static void onWorldTick(ServerLevel world) {
        tickCounter++;

        // 更新头顶显示计时器
        Iterator<Map.Entry<UUID, Integer>> nameIt = NAME_DISPLAYS.entrySet().iterator();
        while (nameIt.hasNext()) {
            var entry = nameIt.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                // 恢复原名
                var entity = world.getEntity(entry.getKey());
                if (entity instanceof Cat cat) {
                    cat.setCustomName(ORIGINAL_NAMES.remove(entry.getKey()));
                }
                nameIt.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        // 更新工具展示动画：跟随猫的位置和朝向，猫离开工作区则移除
        Iterator<Map.Entry<UUID, UUID>> toolIt = TOOL_CATS.entrySet().iterator();
        while (toolIt.hasNext()) {
            var entry = toolIt.next();
            UUID toolId = entry.getKey();
            UUID catId = entry.getValue();
            var toolEntity = world.getEntity(toolId);
            if (toolEntity == null) continue; // chunk 未加载，等下次
            if (!(toolEntity instanceof Display.ItemDisplay tool) || tool.isRemoved()) {
                CATS_WITH_TOOLS.remove(catId);
                toolIt.remove();
                continue;
            }
            var entity = world.getEntity(catId);
            if (entity == null) continue; // chunk 未加载
            if (!(entity instanceof Cat cat) || !cat.isAlive() || !isInQuarry(world, cat)) {
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

        // 每10秒刷新箱子缓存
        if (tickCounter % 200 == 0) {
            CHEST_CACHE.clear();
        }

        if (tickCounter % 200 != 0) return; // 每10秒一次

        for (QuarryArea quarry : QuarryCreationHandler.getQuarries()) {
            if (quarry.world() != world) continue;

            AABB bounds = quarry.getBounds();
            List<Cat> cats = world.getEntitiesOfClass(Cat.class, bounds, Cat::isTame);
            int count = cats.size();
            if (count == 0) continue;

            // 找结构内的箱子
            List<BlockPos> chests = CHEST_CACHE.computeIfAbsent(quarry,
                    q -> findChestsInBounds(world, bounds));

            // 随机选猫挖矿，每只猫60%概率产出
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
                    ItemStack ore = getRandomOre(rand, effective + rareBonus);
                    depositOre(world, chests, bounds, ore);
                    if (boosted && rand.nextFloat() < 0.5f) {
                        depositOre(world, chests, bounds, getRandomOre(rand, effective + rareBonus));
                    }
                    showCatMined(world, cat, ore);
                }
            }
        }
    }

    private static void showCatMined(ServerLevel world, Cat cat, ItemStack ore) {
        UUID id = cat.getUUID();
        if (!NAME_DISPLAYS.containsKey(id)) {
            ORIGINAL_NAMES.put(id, cat.getCustomName());
        }
        String oreName = ore.getHoverName().getString();
        cat.setCustomName(Component.literal("§6⛏ §e" + oreName));
        NAME_DISPLAYS.put(id, 40);

        // 猫旁边显示挖掘中的铁镐（已存在则不重复创建）
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
            tool.setItemStack(new ItemStack(Items.IRON_PICKAXE));
            tool.setItemTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            world.addFreshEntity(tool);
            TOOL_CATS.put(tool.getUUID(), id);
        }

        // 挖掘粒子效果
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

    private static boolean isInQuarry(ServerLevel world, Cat cat) {
        for (QuarryArea q : QuarryCreationHandler.getQuarries()) {
            if (q.world() == world && q.getBounds().contains(cat.position())) {
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

    private static void depositOre(ServerLevel world, List<BlockPos> chests, AABB bounds, ItemStack ore) {
        // 优先放入箱子
        for (BlockPos chestPos : chests) {
            BlockEntity be = world.getBlockEntity(chestPos);
            if (be instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack slot = chest.getItem(i);
                    if (slot.isEmpty()) {
                        chest.setItem(i, ore);
                        return;
                    } else if (ItemStack.isSameItem(slot, ore) && slot.getCount() < slot.getMaxStackSize()) {
                        slot.grow(ore.getCount());
                        return;
                    }
                }
            }
        }
        // 箱子都满了，掉在结构中心
        double cx = (bounds.minX + bounds.maxX) / 2;
        double cz = (bounds.minZ + bounds.maxZ) / 2;
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                world, cx, bounds.minY + 1, cz, ore);
        world.addFreshEntity(drop);
    }

    private static ItemStack getRandomOre(RandomSource rand, int catCount) {
        float roll = rand.nextFloat();
        float rareChance = Math.min(0.15f, catCount * 0.03f);

        if (roll < rareChance * 0.1f) return new ItemStack(Items.DIAMOND);
        if (roll < rareChance * 0.3f) return new ItemStack(Items.EMERALD);
        if (roll < rareChance) return new ItemStack(Items.GOLD_INGOT);
        if (roll < 0.25f) return new ItemStack(Items.IRON_INGOT);
        if (roll < 0.45f) return new ItemStack(Items.COPPER_INGOT);
        if (roll < 0.60f) return new ItemStack(Items.COAL);
        if (roll < 0.75f) return new ItemStack(Items.REDSTONE);
        if (roll < 0.88f) return new ItemStack(Items.LAPIS_LAZULI);
        return new ItemStack(Items.COBBLESTONE);
    }
}
