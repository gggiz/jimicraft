# 基米工艺 (Jimi Craft)

Minecraft 26.1.2 Fabric 模组 —— 让驯服的猫猫在矿场结构中自动挖矿。

## 玩法

1. **矿场结构** — 模组自带矿场结构，丢铁镐即可生成
2. **生成矿场** — 将铁镐丢在石头上
3. **猫猫工作** — 驯服的猫只要在矿场结构范围内就会自动挖矿，坐卧均可，矿石会自动存入结构内的箱子

## 特性

- 无需 GUI、无需绑定物品、无需单独矿场方块 —— 结构即矿场
- 猫越多稀有矿物概率越高（最多 5 只加成）
- 猫挖矿时头顶显示矿物名 + 挖掘粒子特效
- 全主世界群系猫刷新率大幅提升
- 矿场数据 JSON 持久化，重启服务器不丢失
- 中英双语支持

## 矿物产出（5 只猫时）

| 矿物 | 占比 |
|------|------|
| 钻石 | 1.5% |
| 绿宝石 | 3.0% |
| 金锭 | 10.5% |
| 铁锭 | 10% |
| 铜锭 | 20% |
| 煤炭 | 15% |
| 红石 | 15% |
| 青金石 | 13% |
| 圆石 | 12% |

## 下载

[Releases](https://github.com/gggiz/jimicraft/releases) 页面下载最新版 JAR，放入 `mods` 文件夹即可。

## 构建

- **Minecraft**: 26.1.2 (Mojang mappings)
- **Java**: 25
- **Gradle**: 9.5.1
- **Fabric Loom**: 1.17.11
- **Fabric API**: 0.151.0+

```bash
./gradlew build
```

JAR 输出在 `build/libs/jimicraft-1.0.0.jar`。

## 自定义结构

默认模组自带一个示例矿场结构。如需自定义：

1. 在创造模式用结构方块保存你的建筑为 `quarry_structure.nbt`
2. 替换 `src/main/resources/data/jimicraft/structure/quarry_structure.nbt`
3. 确保结构内有箱子（矿石会自动存入）
4. 重新构建模组

## 许可证

MIT
