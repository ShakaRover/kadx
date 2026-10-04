# 上游同步计划（upstream/master @ 4e2b8d54, 2026-10-03）

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
| `jadx-core/.../dex/visitors/ExtractFieldInit.java` | `.../ExtractFieldInit.kt` | PR #2956 跨构造函数比较字段初始化值 |
| `jadx-core/.../codegen/ClassGen.java` | `.../ClassGen.kt` | PR #2957 按别名稳定排序成员 |
| `jadx-core/.../api/data/impl/JadxCodeComment.java` | `.../JadxCodeComment.kt` | PR #2940 清洗用户注释 |
| `jadx-core/.../clsp/ClspGraph.java` | `.../ClspGraph.kt` | 小修 |
| `jadx-core/.../utils/DotGraphUtils.java` | `.../DotGraphUtils.kt` | 图工具 |
| `jadx-core/.../utils/ListUtils.java` | `.../ListUtils.kt` | +7 |
| `jadx-core/.../dex/nodes/ClassNode.java` / `RootNode.java` | 同名 `.kt` | 小修 |
| `jadx-core/.../utils/android/{DataInputDelegate,ExtDataInput,Res9patchStreamDecoder}.java` | 同名 `.kt` | 小修 |
| `jadx-core/.../xmlgen/ResTableBinaryParser.java` / `entry/EntryConfig.java` | 同名 `.kt` | 小修 |

**配套新测试**（上游 Java，需转 Kotlin + Option A fixture）：
`TestFieldInitDifferentArguments`、`TestFieldInitDifferentValues`、`TestFieldInitSameThis`、`TestUserRenamesMemberOrder`；修改 `TestSwitchInLoop9`。

## B. 插件系统重构 + 新模块 `jadx-gui-api`

| 文件 | 动作 |
|------|------|
| `jadx-core/.../core/plugins/PluginRuntime.java` | 新增 → Kotlin |
| `jadx-core/.../core/plugins/JadxPluginManager.java` | 改（118 行） |
| `jadx-core/.../core/plugins/PluginContext.java` | 改（简化，73） |
| `jadx-core/.../core/plugins/JadxPluginsData.java` | 改 |
| `jadx-core/.../api/plugins/gui/IJadxGuiPlugin.java` | 新增 |
| `jadx-core/.../api/plugins/options/impl/NoPluginOptions.java` | 新增 |
| `jadx-core/.../api/JadxArgs.java`、`api/JadxDecompiler.java` | 改（插件加载不再需要 decompiler 实例） |
| **`jadx-gui-api/`（新模块）** | `IMainWindow`、`JadxGlobalGuiPlugin`、`JadxGuiContextExt`、`JadxGuiPlugin` + build + settings.gradle |
| `jadx-gui/.../plugins/GuiPluginsManager.java` | 新增 |
| `jadx-gui/.../plugins/context/GuiPluginsRegistry.java` | 新增 |
| `jadx-gui/.../plugins/context/{CommonGuiPluginsContext,GuiPluginContext}.java` | 改 |
| `jadx-gui/.../utils/plugins/CloseablePlugins.java` | **删除** |
| `jadx-plugins-tools/.../JadxExternalPluginsLoader.java` | 改 |

## C. GUI 功能

| 文件 | 内容 |
|------|------|
| `jadx-gui/.../ui/FilterableTreeModel.java`（新 282） | PR #2941 树过滤 |
| `jadx-gui/.../ui/action/CopySmaliReferenceAction.java`（新 66） | PR #2955 复制 smali 引用 |
| `jadx-gui/.../ui/graphs/InheritanceDataAttr.java`（新 118） + `ClassInheritanceGraphDialog.java`（419） | PR #2935 继承图双向 |
| `jadx-gui/.../ui/MainWindow.java`（100） | 集成上述 |
| `jadx-gui/.../ui/hexviewer/LazyLoadingBinaryData.java` | PR #2948 块边界 |
| `jadx-gui/.../utils/plugins/CollectPlugins.java`、`settings/ui/plugins/*`、`QuarkReport*`、`JadxProject`、`JadxWrapper`、popupmenu、`CodeArea`、`ActionModel` | 配套 |
| `jadx-gui/.../utils/rx/RxUtils.java` | +12（我们已删 rx 包，需评估） |

## D. 构建/基础设施

- **版本目录迁移**：新增 `gradle/libs.versions.toml`，所有 `build.gradle.kts` 改用 `libs.*`；`buildSrc/build.gradle.kts` + 新增 `buildSrc/settings.gradle.kts`；删除 `jadx-rewrite.gradle.kts`。
- Gradle wrapper 升级（`gradle-wrapper.jar/properties`）。
- `.github/workflows/*` 更新。
- `settings.gradle.kts` 增加 `jadx-gui-api`。

> 版本目录迁移体量大且与我们的迁移无冲突，可整包应用；但要注意我们已加 `kotlinx-coroutines-*` 依赖需并入 catalog。

## E. i18n 资源（9 个 `Messages_*.properties`）

直接应用（+4 行/文件）。

---

## 执行顺序

1. **S1**：A（核心逻辑）+ E（i18n）+ A 的新测试 → 最影响输出正确性。
2. **S2**：B（插件重构 + `jadx-gui-api` 新模块）→ 结构性、影响面大。
3. **S3**：C（GUI 功能）。
4. **S4**：D（版本目录 + wrapper + workflows）。
5. 每步 `./gradlew build` 绿 + 对应新测试通过后提交。

## 注意

- 上游若已把某 `.kt` 从 `src/main/java` 移到 `src/main/kotlin`（apks/apkm input），我们**已经在 `src/main/kotlin`**，无需处理。
- 移植时以**上游语义**为准，但保持我们的 Kotlin 风格/API 形态（属性、无 `!!`、协程）。
- 新测试的嵌套 `TestCls` 沿用我们的 **Option A**（`*Fixture.kt` + `JAVA_SOURCE`）机制。
