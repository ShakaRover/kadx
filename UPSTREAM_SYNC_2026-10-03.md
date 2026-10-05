# 上游同步计划（upstream/master @ 4e2b8d54, 2026-10-03）

## ✅ 同步完成（S1–S4）

| 批次 | 内容 | 提交 |
|------|------|------|
| S1 | 核心反编译逻辑（ExtractFieldInit #2956、ClassGen #2957、KadxCodeComment #2940、ClspGraph/ListUtils/DotGraphUtils/RootNode…）+ 9 个 i18n + 4 个新测试 | `0ef31249c` |
| S2 | 插件系统重构 + 新模块 `kadx-gui-api`（PluginRuntime/GuiPluginsManager/GuiPluginsRegistry/IKadxGuiPlugin/NoPluginOptions；删 CloseablePlugins）+ 新测试 | `9a4b63f68` |
| S3 | GUI 功能（树过滤 #2941、复制 smali 引用 #2955、双向继承图 #2935、hexviewer 块边界 #2948、导出目录记忆 #2954）+ 测试 | `ef940933a` |
| S4 | 版本目录迁移（`gradle/libs.versions.toml`）+ Gradle 9.7.1 + 工作流 | `fef3ca0a3` |

**结果**：全部 `./gradlew build` 绿；core 测试 1018 → **1027+**；Kotlin 升至 2.4.20、Gradle 9.7.1。
**保留我们的差异**：协程依赖（catalog 内）、`kadx-gui-api` 模块、已移除的 RxJava；`kadx-rewrite.gradle.kts` 删除。
**核对**：`FilterableTreeModel` 的 `runBlocking` 与上游 `Future.get()` 语义一致（在后台 filter 线程，不在 EDT）。

---

> 背景：本仓库已把上游 **全部 Java 转成 Kotlin**（0 `.java`），并做了现代化/协程化。
> 上游从我们的分叉点 `6b3116f0` 之后新增 **23 个提交**。**无法直接 merge**（上游改 Java，我们已删 Java 改 Kotlin）。
> 做法：**逐文件把上游的功能性改动手工移植到我们的 Kotlin 实现**；新增文件转 Kotlin 加入；删除文件同步删除；资源/build 直接应用。

## 引用
- 分叉点：`6b3116f0`（已在我们 HEAD 祖先中）
- 目标：`upstream/master` = `4e2b8d54`
- 查看某文件上游改动：`git diff 6b3116f0..upstream/master -- <path>`
- 上游文件内容：`git show upstream/master:<path>`

---

## A. 核心反编译逻辑修复（最高优先级）

| 文件（上游） | 对应我们的文件 | 内容 |
|--------------|----------------|------|
| `kadx-core/.../dex/visitors/ExtractFieldInit.java` | `.../ExtractFieldInit.kt` | PR #2956 跨构造函数比较字段初始化值 |
| `kadx-core/.../codegen/ClassGen.java` | `.../ClassGen.kt` | PR #2957 按别名稳定排序成员 |
| `kadx-core/.../api/data/impl/KadxCodeComment.java` | `.../KadxCodeComment.kt` | PR #2940 清洗用户注释 |
| `kadx-core/.../clsp/ClspGraph.java` | `.../ClspGraph.kt` | 小修 |
| `kadx-core/.../utils/DotGraphUtils.java` | `.../DotGraphUtils.kt` | 图工具 |
| `kadx-core/.../utils/ListUtils.java` | `.../ListUtils.kt` | +7 |
| `kadx-core/.../dex/nodes/ClassNode.java` / `RootNode.java` | 同名 `.kt` | 小修 |
| `kadx-core/.../utils/android/{DataInputDelegate,ExtDataInput,Res9patchStreamDecoder}.java` | 同名 `.kt` | 小修 |
| `kadx-core/.../xmlgen/ResTableBinaryParser.java` / `entry/EntryConfig.java` | 同名 `.kt` | 小修 |

**配套新测试**（上游 Java，需转 Kotlin + Option A fixture）：
`TestFieldInitDifferentArguments`、`TestFieldInitDifferentValues`、`TestFieldInitSameThis`、`TestUserRenamesMemberOrder`；修改 `TestSwitchInLoop9`。

## B. 插件系统重构 + 新模块 `kadx-gui-api`

| 文件 | 动作 |
|------|------|
| `kadx-core/.../core/plugins/PluginRuntime.java` | 新增 → Kotlin |
| `kadx-core/.../core/plugins/KadxPluginManager.java` | 改（118 行） |
| `kadx-core/.../core/plugins/PluginContext.java` | 改（简化，73） |
| `kadx-core/.../core/plugins/KadxPluginsData.java` | 改 |
| `kadx-core/.../api/plugins/gui/IKadxGuiPlugin.java` | 新增 |
| `kadx-core/.../api/plugins/options/impl/NoPluginOptions.java` | 新增 |
| `kadx-core/.../api/KadxArgs.java`、`api/KadxDecompiler.java` | 改（插件加载不再需要 decompiler 实例） |
| **`kadx-gui-api/`（新模块）** | `IMainWindow`、`KadxGlobalGuiPlugin`、`KadxGuiContextExt`、`KadxGuiPlugin` + build + settings.gradle |
| `kadx-gui/.../plugins/GuiPluginsManager.java` | 新增 |
| `kadx-gui/.../plugins/context/GuiPluginsRegistry.java` | 新增 |
| `kadx-gui/.../plugins/context/{CommonGuiPluginsContext,GuiPluginContext}.java` | 改 |
| `kadx-gui/.../utils/plugins/CloseablePlugins.java` | **删除** |
| `kadx-plugins-tools/.../KadxExternalPluginsLoader.java` | 改 |

## C. GUI 功能

| 文件 | 内容 |
|------|------|
| `kadx-gui/.../ui/FilterableTreeModel.java`（新 282） | PR #2941 树过滤 |
| `kadx-gui/.../ui/action/CopySmaliReferenceAction.java`（新 66） | PR #2955 复制 smali 引用 |
| `kadx-gui/.../ui/graphs/InheritanceDataAttr.java`（新 118） + `ClassInheritanceGraphDialog.java`（419） | PR #2935 继承图双向 |
| `kadx-gui/.../ui/MainWindow.java`（100） | 集成上述 |
| `kadx-gui/.../ui/hexviewer/LazyLoadingBinaryData.java` | PR #2948 块边界 |
| `kadx-gui/.../utils/plugins/CollectPlugins.java`、`settings/ui/plugins/*`、`QuarkReport*`、`KadxProject`、`KadxWrapper`、popupmenu、`CodeArea`、`ActionModel` | 配套 |
| `kadx-gui/.../utils/rx/RxUtils.java` | +12（我们已删 rx 包，需评估） |

## D. 构建/基础设施

- **版本目录迁移**：新增 `gradle/libs.versions.toml`，所有 `build.gradle.kts` 改用 `libs.*`；`buildSrc/build.gradle.kts` + 新增 `buildSrc/settings.gradle.kts`；删除 `kadx-rewrite.gradle.kts`。
- Gradle wrapper 升级（`gradle-wrapper.jar/properties`）。
- `.github/workflows/*` 更新。
- `settings.gradle.kts` 增加 `kadx-gui-api`。

> 版本目录迁移体量大且与我们的迁移无冲突，可整包应用；但要注意我们已加 `kotlinx-coroutines-*` 依赖需并入 catalog。

## E. i18n 资源（9 个 `Messages_*.properties`）

直接应用（+4 行/文件）。

---

## 执行顺序

1. **S1**：A（核心逻辑）+ E（i18n）+ A 的新测试 → 最影响输出正确性。
2. **S2**：B（插件重构 + `kadx-gui-api` 新模块）→ 结构性、影响面大。
3. **S3**：C（GUI 功能）。
4. **S4**：D（版本目录 + wrapper + workflows）。
5. 每步 `./gradlew build` 绿 + 对应新测试通过后提交。

## 注意

- 上游若已把某 `.kt` 从 `src/main/java` 移到 `src/main/kotlin`（apks/apkm input），我们**已经在 `src/main/kotlin`**，无需处理。
- 移植时以**上游语义**为准，但保持我们的 Kotlin 风格/API 形态（属性、无 `!!`、协程）。
- 新测试的嵌套 `TestCls` 沿用我们的 **Option A**（`*Fixture.kt` + `JAVA_SOURCE`）机制。
