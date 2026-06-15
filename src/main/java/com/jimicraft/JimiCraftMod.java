package com.jimicraft;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class JimiCraftMod implements ModInitializer {
    public static final String MOD_ID = "jimicraft";

    @Override
    public void onInitialize() {
        ModEntitySpawns.init();
        QuarryCreationHandler.init();
        QuarryWorkHandler.init();
        System.out.println("[JimiCraft] 基米工艺已加载！");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
