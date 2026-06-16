package com.jimicraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CatnipFactoryHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<QuarryState.JsonEntry>>() {}.getType();
    private static final Identifier STRUCTURE_ID = Identifier.fromNamespaceAndPath("jimicraft", "catnip_factory_structure");
    private static final List<QuarryArea> FACTORIES = new ArrayList<>();
    private static final Set<ResourceKey<Level>> LOADED_DIMENSIONS = new HashSet<>();
    private static final int RADIUS = 50;
    private static final int REFRESH_INTERVAL = 100; // 每5秒刷新
    private static final Map<UUID, Integer> NAME_DISPLAYS = new HashMap<>();
    private static final Map<UUID, Component> ORIGINAL_NAMES = new HashMap<>();
    private static int tickCounter = 0;

    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(CatnipFactoryHandler::onWorldTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(CatnipFactoryHandler::onServerStopping);
    }

    private static void onServerStopping(MinecraftServer server) {
        save(server);
        System.out.println("[JimiCraft] 猫薄荷工厂数据已保存");
    }

    private static void onWorldTick(ServerLevel world) {
        tickCounter++;

        // 更新头顶显示计时器
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

        // 首次加载恢复工厂数据
        ResourceKey<Level> dimKey = world.dimension();
        if (!LOADED_DIMENSIONS.contains(dimKey)) {
            LOADED_DIMENSIONS.add(dimKey);
            List<QuarryArea> restored = load(world.getServer());
            FACTORIES.addAll(restored);
            if (!restored.isEmpty()) {
                System.out.println("[JimiCraft] 已恢复 " + restored.size() + " 个猫薄荷工厂");
            }
        }

        // 每0.5秒检测生成触发器 + 清除无效工厂
        if (tickCounter % 10 == 0) {
            var players = world.players();
            if (!players.isEmpty()) {
                for (var player : players) {
                    AABB area = new AABB(player.blockPosition()).inflate(32);
                    for (ItemEntity itemEntity : world.getEntitiesOfClass(ItemEntity.class, area,
                            e -> e.getItem().is(CatnipHandler.CATNIP_ITEM) && e.getDeltaMovement().y == 0)) {

                        BlockPos below = itemEntity.blockPosition().below();
                        if (world.getBlockState(below).is(BlockTags.WOOL)) {
                            placeFactory(world, below);
                            itemEntity.discard();
                            return;
                        }
                    }
                }
            }
        }

        // 每5秒刷新工厂范围内的猫薄荷加成
        if (tickCounter % REFRESH_INTERVAL == 0) {
            for (QuarryArea factory : FACTORIES) {
                if (factory.world() != world) continue;
                // 工厂结构内必须有至少5只猫才能生效
                List<Cat> catsInside = world.getEntitiesOfClass(Cat.class, factory.getBounds(), Cat::isTame);
                if (catsInside.size() < 5) continue;
                // 结构内猫头顶显示栽培状态
                for (Cat c : catsInside) {
                    UUID id = c.getUUID();
                    if (!NAME_DISPLAYS.containsKey(id)) {
                        ORIGINAL_NAMES.put(id, c.getCustomName());
                    }
                    c.setCustomName(Component.literal("§a🌿 §e栽培猫薄荷中..."));
                    NAME_DISPLAYS.put(id, REFRESH_INTERVAL + 10);
                }
                AABB zone = factory.getBounds().inflate(RADIUS);
                List<Cat> cats = world.getEntitiesOfClass(Cat.class, zone, Cat::isTame);
                for (Cat cat : cats) {
                    CatnipHandler.applyBoost(cat, REFRESH_INTERVAL + 40);
                }
            }
        }
    }

    private static void placeFactory(ServerLevel world, BlockPos pos) {
        var manager = world.getStructureManager();
        var template = manager.get(STRUCTURE_ID);

        if (template.isPresent()) {
            var structure = template.get();
            var settings = new StructurePlaceSettings();
            structure.placeInWorld(world, pos, pos, settings, world.getRandom(), 2);

            var sizeVec = structure.getSize();
            BlockPos size = BlockPos.containing(sizeVec.getX(), sizeVec.getY(), sizeVec.getZ());
            FACTORIES.add(new QuarryArea(pos, size, world));

            System.out.println("[JimiCraft] 猫薄荷工厂已创建！位置: " + pos);
        } else {
            System.out.println("[JimiCraft] 错误：找不到结构文件 catnip_factory_structure.nbt！");
        }
    }

    // === 持久化 ===

    private static Path getSavePath(MinecraftServer server) {
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return worldDir.resolve("data").resolve("jimicraft_factories.json");
    }

    private static void save(MinecraftServer server) {
        Map<ServerLevel, String> dimIds = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            dimIds.put(level, getDimId(level.dimension()));
        }
        List<QuarryState.JsonEntry> entries = new ArrayList<>();
        for (QuarryArea q : FACTORIES) {
            String dimId = dimIds.get(q.world());
            if (dimId != null) {
                entries.add(new QuarryState.JsonEntry(dimId,
                        q.origin().getX(), q.origin().getY(), q.origin().getZ(),
                        q.size().getX(), q.size().getY(), q.size().getZ()));
            }
        }
        Path path = getSavePath(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(entries, w);
            }
        } catch (IOException e) {
            System.out.println("[JimiCraft] 保存工厂数据失败: " + e.getMessage());
        }
    }

    private static List<QuarryArea> load(MinecraftServer server) {
        List<QuarryArea> result = new ArrayList<>();
        Path path = getSavePath(server);
        if (!Files.exists(path)) return result;
        Map<String, ServerLevel> dimMap = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            dimMap.put(getDimId(level.dimension()), level);
        }
        try (Reader r = Files.newBufferedReader(path)) {
            List<QuarryState.JsonEntry> entries = GSON.fromJson(r, LIST_TYPE);
            if (entries != null) {
                for (QuarryState.JsonEntry e : entries) {
                    ServerLevel lvl = dimMap.get(e.dimension());
                    if (lvl != null) {
                        result.add(new QuarryArea(new BlockPos(e.ox(), e.oy(), e.oz()),
                                new BlockPos(e.sx(), e.sy(), e.sz()), lvl));
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println("[JimiCraft] 加载工厂数据失败: " + ex.getMessage());
        }
        return result;
    }

    private static String getDimId(ResourceKey<Level> dimKey) {
        try {
            var method = dimKey.getClass().getMethod("location");
            return method.invoke(dimKey).toString();
        } catch (Exception e1) {
            try {
                var method = dimKey.getClass().getMethod("value");
                return method.invoke(dimKey).toString();
            } catch (Exception e2) {
                String s = dimKey.toString();
                int lastSlash = s.lastIndexOf('/');
                return lastSlash >= 0 ? s.substring(lastSlash + 1).replace("]", "") : s;
            }
        }
    }
}
