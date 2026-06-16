package com.jimicraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class LumberMillCreationHandler {
    private static final Identifier STRUCTURE_ID = Identifier.fromNamespaceAndPath("jimicraft", "lumber_mill_structure");
    private static final List<QuarryArea> MILLS = new ArrayList<>();
    private static final Set<ResourceKey<Level>> LOADED_DIMENSIONS = new HashSet<>();
    private static int tickCounter = 0;

    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(LumberMillCreationHandler::onWorldTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(LumberMillCreationHandler::onServerStopping);
    }

    public static List<QuarryArea> getMills() {
        return MILLS;
    }

    private static void onServerStopping(MinecraftServer server) {
        LumberMillState.save(server, MILLS);
        System.out.println("[JimiCraft] 伐木场数据已保存");
    }

    private static void onWorldTick(ServerLevel world) {
        tickCounter++;

        ResourceKey<Level> dimKey = world.dimension();
        if (!LOADED_DIMENSIONS.contains(dimKey)) {
            LOADED_DIMENSIONS.add(dimKey);
            List<QuarryArea> restored = LumberMillState.load(world.getServer());
            MILLS.addAll(restored);
            if (!restored.isEmpty()) {
                System.out.println("[JimiCraft] 已恢复 " + restored.size() + " 个伐木场");
            }
        }

        if (tickCounter % 10 != 0) return;

        var players = world.players();

        for (var player : players) {
            AABB area = new AABB(player.blockPosition()).inflate(32);
            for (ItemEntity itemEntity : world.getEntitiesOfClass(ItemEntity.class, area,
                    e -> e.getItem().is(Items.IRON_AXE) && e.getDeltaMovement().y == 0)) {

                // 箱子非完整方块，物品落在箱子上时 blockPosition 就是箱子位置
                BlockPos pos = itemEntity.blockPosition();
                BlockPos below = pos.below();
                BlockPos target = null;
                if (world.getBlockState(pos).is(Blocks.CHEST) || world.getBlockState(pos).is(Blocks.TRAPPED_CHEST)) {
                    target = pos;
                } else if (world.getBlockState(below).is(Blocks.CHEST) || world.getBlockState(below).is(Blocks.TRAPPED_CHEST)) {
                    target = below;
                }
                if (target != null) {
                    placeLumberMill(world, target);
                    itemEntity.discard();
                    return;
                }
            }
        }
    }

    private static void placeLumberMill(ServerLevel world, BlockPos pos) {
        var manager = world.getStructureManager();
        var template = manager.get(STRUCTURE_ID);

        if (template.isPresent()) {
            var structure = template.get();
            var settings = new StructurePlaceSettings();
            structure.placeInWorld(world, pos, pos, settings, world.getRandom(), 2);

            var sizeVec = structure.getSize();
            BlockPos size = BlockPos.containing(sizeVec.getX(), sizeVec.getY(), sizeVec.getZ());
            MILLS.add(new QuarryArea(pos, size, world));

            System.out.println("[JimiCraft] 伐木场已创建！位置: " + pos + " 大小: " + size);
        } else {
            System.out.println("[JimiCraft] 错误：找不到结构文件 lumber_mill_structure.nbt！");
        }
    }
}
