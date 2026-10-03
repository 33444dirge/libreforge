# libreforge 2026.27 内存分配与代码优化修复报告

## 1. 修复范围

本报告对应 `libreforge-2026.27-内存分配与代码优化审查.md` 中的 P0-A、P0-B、P0-C 建议。

审查报告基于 Allocation Profile 指出了以下高频分配来源：

- `ItemHolderFinderProvider` 每次扫描复制 `SlotTypes.baseTypes`，并通过 `flatMap/filter/map` 创建中间集合。
- 非玩家实体也会遍历数字槽位；这些槽位对非玩家实体必然返回空列表。
- 拾取事件处理器在热路径上重复读取 `Config.getBool`。
- 已经被其他监听器取消的拾取事件仍可能提交实体刷新任务。

本次没有修改配置默认值，也没有改变 holder provider 的注册顺序、holder 输出顺序或事件模型。

## 2. 已实施修复

### 2.1 P0-A：重写 ItemHolderFinder 热循环

涉及文件：

- `core/common/src/main/kotlin/com/willfp/libreforge/slot/ItemHolderFinder.kt`
- `core/common/src/main/kotlin/com/willfp/libreforge/slot/impl/NumericSlotType.kt`

具体改动：

1. 新增命令式 `appendHolders` 累加路径，替代 `flatMap/filter/map` 中间集合。
2. 直接遍历 `SlotTypes.baseTypes`，移除每次计算的 `toMutableSet()`。
3. `NumericSlotType.slot` 改为 `internal`，用于无反射、无临时对象的当前槽位比较。
4. 非玩家实体跳过全部 `NumericSlotType`，因为 `NumericSlotType.getItems()` 对非玩家始终返回空列表。
5. 玩家继续跳过当前选中的数字槽位，避免与主手槽重复扫描。
6. 保持 `SlotTypes.baseTypes`、物品列表和 finder 返回结果的遍历顺序。

对应代码位置：

- `ItemHolderFinder.kt:49-66`：命令式 holder 累加。
- `ItemHolderFinder.kt:89-105`：槽位扫描与数字槽位跳过。
- `NumericSlotType.kt:10-12`：暴露只读的内部槽位编号。

### 2.2 P0-B：配置读取改为 reload 快照

涉及文件：

- `core/common/src/main/kotlin/com/willfp/libreforge/LibreforgeSpigotPlugin.kt`
- `core/common/src/main/kotlin/com/willfp/libreforge/HolderProvider.kt`
- `core/common/src/main/kotlin/com/willfp/libreforge/HolderUpdates.kt`

新增不可变配置快照 `RefreshSettings`，并使用 `@Volatile` 引用在 reload 时整体替换：

- `pickupEnabled`
- `pickupRequireMeta`
- `entitiesEnabled`
- `entityInterval`
- `skipAfkPlayers`

配置只在 `LibreforgeSpigotPlugin.handleReload()` 中读取。实体轮询、玩家轮询、拾取事件和实体 holder 计算均读取同一份快照，避免在高频调用中重复执行 `Config.getBool`/`Config.getInt`。

对应代码位置：

- `LibreforgeSpigotPlugin.kt:90-113`：快照类型与默认值。
- `LibreforgeSpigotPlugin.kt:182-190`：reload 时原子替换快照。
- `LibreforgeSpigotPlugin.kt:239-240`、`347`：轮询任务读取快照。
- `HolderProvider.kt:290`：实体 holder 计算读取快照。
- `HolderUpdates.kt:33-41`：拾取监听读取快照。

### 2.3 P0-C：忽略已取消的拾取事件

涉及文件：

- `core/common/src/main/kotlin/com/willfp/libreforge/HolderUpdates.kt`

`ItemRefreshListener.onItemPickup` 已改为：

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
```

监听器只观察最终未取消的事件，不修改事件本身；成功拾取后的实体刷新仍通过实体调度器执行。被其他插件取消的拾取事件不会再创建无效刷新任务。

## 3. 兼容性说明

- 未改变 `refresh.*` 配置项名称或默认值。
- reload 时使用完整快照替换，避免读取到半更新配置。
- holder provider 的注册顺序未改变。
- holder 生成顺序保持原有槽位、物品和 finder 顺序。
- 玩家当前主手数字槽仍只扫描一次。
- 自定义非 combined `SlotType` 仍按原逻辑扫描。
- 成功拾取事件仍会刷新 holder；仅取消的拾取事件不再刷新。

## 4. 验证结果

执行命令：

```text
./gradlew --no-daemon "-Pkotlin.incremental=false" :core:common:compileKotlin
```

结果：

```text
BUILD SUCCESSFUL
1 actionable task: 1 executed
```

另外完成了以下静态验证：

- `git diff --check` 无空白错误。
- 编译产物字节码中确认 `ItemHolderFinderProvider` 直接遍历 `SlotTypes.baseTypes`，不存在 `toMutableSet` 热路径调用。
- 编译产物字节码中确认拾取处理器读取 `RefreshSettings`，而不是在拾取分支内读取 `refresh.pickup.*` 配置。

## 5. 当前未完成事项

本次没有直接实施以下需要运行时验证的优化：

1. P1-A：拾取刷新 single-flight/dirty 合并。
2. P1-B：三个 `ItemHolderFinder` 共享一次物品槽位快照。
3. P1-C：基于 dirty/version 的长期 holder discovery 缓存。
4. P2：替换 Folia 上从 Global Region 枚举 `world.entities` 的架构。

这些改动可能改变 `HolderEnableEvent`/`HolderDisableEvent` 次数、第三方 provider 调用时机或 Folia 区域所有权边界，应先完成事件 trace、Folia 实机测试和 A/B Allocation Profile，再继续实施。

## 6. A/B 验收建议

当前只能确认源码和编译正确，不能仅凭编译结果宣称已获得固定 GiB 或百分比收益。发布前建议使用与原审查相同的生产负载，至少采集三次 baseline 与三次修复版 profile，并比较中位数：

- `ItemHolderFinderProvider -> toMutableSet` 应接近 0。
- 非玩家 `NumericSlotType` 扫描计数应为 0。
- `onItemPickup -> Config.getBool` 应接近 0。
- holder 列表顺序及 enable/disable 事件 trace 应与 baseline 一致。
- 取消拾取事件不应产生实体刷新任务。
- Folia 日志中不应出现新的 `Accessing entity state off owning region's thread`。

如需证明 retained heap 泄漏是否消失，还必须另行执行 Full GC 前后的 heap histogram 或 heap dump 对比；Allocation Profile 的累计分配量不能单独证明内存泄漏。

## 7. 工作区说明

本报告只记录本次内存优化修复。工作区中已有的 FeatherStep Folia 修复和攻击冷却相关改动保留原状，不在本报告中重新归因。
