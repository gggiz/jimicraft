package com.jimicraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class QuarryCreationHandler {
    private static final Identifier STRUCTURE_ID = Identifier.fromNamespaceAndPath("jimicraft", "quarry_structure");
    private static final List<QuarryArea> QUARRIES = new ArrayList<>();
    private static final Set<ResourceKey<Level>> LOADED_DIMENSIONS = new HashSet<>();
    private static int tickCounter = 0;

    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(QuarryCreationHandler::onWorldTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(QuarryCreationHandler::onServerStopping);
    }

    public static List<QuarryArea> getQuarries() {
        return QUARRIES;
    }

    private static void onServerStopping(MinecraftServer server) {
        QuarryState.save(server, QUARRIES);
        System.out.println("[JimiCraft] 矿场数据已保存");
    }

    private static void onWorldTick(ServerLevel world) {
        tickCounter++;

        // 首次进入维度时从磁盘恢复矿场
        ResourceKey<Level> dimKey = world.dimension();
        if (!LOADED_DIMENSIONS.contains(dimKey)) {
            LOADED_DIMENSIONS.add(dimKey);
            List<QuarryArea> restored = QuarryState.load(world.getServer());
            QUARRIES.addAll(restored);
            if (!restored.isEmpty()) {
                System.out.println("[JimiCraft] 已恢复 " + restored.size() + " 个矿场");
                // 清理上次会话遗留的工具展示实体
                for (QuarryArea q : restored) {
                    for (Display.ItemDisplay display : world.getEntitiesOfClass(Display.ItemDisplay.class, q.getBounds())) {
                        display.discard();
                    }
                }
            }
        }

        if (tickCounter % 10 != 0) return;

        var players = world.players();

        for (var player : players) {
            AABB area = new AABB(player.blockPosition()).inflate(32);
            for (ItemEntity itemEntity : world.getEntitiesOfClass(ItemEntity.class, area,
                    e -> e.getItem().is(Items.IRON_PICKAXE) && e.getDeltaMovement().y == 0)) {

                BlockPos below = itemEntity.blockPosition().below();
                if (world.getBlockState(below).is(Blocks.STONE)) {
                    placeQuarry(world, below);
                    itemEntity.discard();
                    return;
                }
            }
        }
    }

    private static void placeQuarry(ServerLevel world, BlockPos pos) {
        var manager = world.getStructureManager();
        var template = manager.get(STRUCTURE_ID);

        if (template.isPresent()) {
            var structure = template.get();
            var settings = new StructurePlaceSettings();
            structure.placeInWorld(world, pos, pos, settings, world.getRandom(), 2);

            var sizeVec = structure.getSize();
            BlockPos size = BlockPos.containing(sizeVec.getX(), sizeVec.getY(), sizeVec.getZ());
            QUARRIES.add(new QuarryArea(pos, size, world));

            System.out.println("[JimiCraft] 矿场已创建！位置: " + pos + " 大小: " + size);
        } else {
            System.out.println("[JimiCraft] 错误：找不到结构文件 quarry_structure.nbt！");
        }
    }
}
