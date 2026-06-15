package com.jimicraft;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntitySpawns {

    public static void init() {
        // === 豹猫 (Ocelot) ===
        // 原本只在丛林生成，扩展到所有主世界群系
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                MobCategory.CREATURE,
                EntityType.OCELOT,
                50,    // 刷新权重 (原版丛林: 30)
                2, 4   // 每次生成 2~4 只
        );

        // === 流浪猫 (Cat) ===
        // MC 26.1 已自带 SpawnPlacements，只需添加到全群系
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                MobCategory.CREATURE,
                EntityType.CAT,
                80,    // 高权重，让猫比其他被动生物更常见
                2, 5   // 每次生成 2~5 只
        );

        System.out.println("[JimiCraft] 猫猫已占领主世界！");
    }
}
