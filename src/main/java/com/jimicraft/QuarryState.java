package com.jimicraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class QuarryState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<JsonEntry>>() {}.getType();

    public record JsonEntry(String dimension, int ox, int oy, int oz, int sx, int sy, int sz) {}

    public static Path getSavePath(MinecraftServer server) {
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return worldDir.resolve("data").resolve("jimicraft_quarries.json");
    }

    public static void save(MinecraftServer server, List<QuarryArea> quarries) {
        // 构建 ServerLevel → 维度ID 的映射
        Map<ServerLevel, String> dimIds = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            dimIds.put(level, getDimId(level.dimension()));
        }

        List<JsonEntry> entries = new ArrayList<>();
        for (QuarryArea q : quarries) {
            String dimId = dimIds.get(q.world());
            if (dimId != null) {
                entries.add(new JsonEntry(dimId,
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
            System.out.println("[JimiCraft] 保存矿场数据失败: " + e.getMessage());
        }
    }

    public static List<QuarryArea> load(MinecraftServer server) {
        List<QuarryArea> result = new ArrayList<>();
        Path path = getSavePath(server);
        if (!Files.exists(path)) return result;

        // 构建 维度ID → ServerLevel 的映射
        Map<String, ServerLevel> dimMap = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            dimMap.put(getDimId(level.dimension()), level);
        }

        try (Reader r = Files.newBufferedReader(path)) {
            List<JsonEntry> entries = GSON.fromJson(r, LIST_TYPE);
            if (entries != null) {
                for (JsonEntry e : entries) {
                    ServerLevel world = dimMap.get(e.dimension());
                    if (world != null) {
                        BlockPos origin = new BlockPos(e.ox(), e.oy(), e.oz());
                        BlockPos size = new BlockPos(e.sx(), e.sy(), e.sz());
                        result.add(new QuarryArea(origin, size, world));
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println("[JimiCraft] 加载矿场数据失败: " + ex.getMessage());
        }
        return result;
    }

    private static String getDimId(ResourceKey<Level> dimKey) {
        // ResourceKey 在 MC 26.1 中的 record 字段可能不是 location，用反射获取
        try {
            var method = dimKey.getClass().getMethod("location");
            return method.invoke(dimKey).toString();
        } catch (Exception e1) {
            try {
                var method = dimKey.getClass().getMethod("value");
                return method.invoke(dimKey).toString();
            } catch (Exception e2) {
                // 最后兜底：toString 通常类似 ResourceKey[... / minecraft:overworld]
                String s = dimKey.toString();
                int lastSlash = s.lastIndexOf('/');
                return lastSlash >= 0 ? s.substring(lastSlash + 1).replace("]", "") : s;
            }
        }
    }
}
