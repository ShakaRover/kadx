# JADX 全量 Kotlin 化 —— 总指挥计划（MASTER PLAN）

> 本文件是**唯一权威的进度与派单台账**。SOP 管"怎么做"（KOTLIN_MIGRATION_SOP.md），
> 记忆文档记"坑位"（KOTLIN_CONVERSION.md），本文件记"谁在什么时候做什么、做到哪了"。
> 每个批次完成后由总指挥更新本文件的【状态】列。

---

## 0. 治理模型（Roles）

| 角色 | 职责 | 边界 |
|------|------|------|
| **Commander（总指挥 / 本会话）** | 制定与维护计划、侦察依赖、拆分批次、撰写派单提示、验收构建结果、更新台账 | **不直接改业务代码**；只做侦察、写计划、派单、复核 |
| **Worker（子代理）** | 按批次执行单文件 SOP：转换 → 删原 .java → 补中文 KDoc → 全仓编译 → 测试 → 提交 | 一次只做一个批次；不改本文件 |

**并发纪律（硬性）：** 同一时间**只允许一个 Worker 在仓库内运行 Gradle / 改文件**，
因为 Gradle 守护进程、构建缓存与 git 工作树会被并发写坏。侦察类子代理可与 Worker 并行。

---

## 1. 验收门（Gate）—— 任何批次提交前必须全绿

```bash
./gradlew spotlessApply                                  # 先修格式，否则 spotlessCheck 拦 build
./gradlew <mod>:compileKotlin <mod>:compileJava <mod>:compileTestJava --console=plain
./gradlew <mod>:test --console=plain                     # 集成测试可能较慢，允许长超时
# 里程碑批次（每完成一个子阶段）额外执行：
./gradlew build --console=plain                          # SOP 硬性要求：全量 build 绿才能提交
git add -A && git commit -m "refactor(<mod>): migrate <pkg> to Kotlin (<N> files)"
```

Commander 复核项：① `git show --stat` 里 .java 删除数与 .kt 新增数匹配；② 构建输出 `BUILD SUCCESSFUL`；
③ 台账 java/kt 计数更新；④ 无 `!!`、无 `data class` 节点、无 `synchronized fun`。

---

## 2. Worker 通用派单简报（每个任务都内嵌，保证无状态可执行）

- 先读 `KOTLIN_MIGRATION_SOP.md` 与 `KOTLIN_CONVERSION.md`。
- 机械式平替，语义与原 Java 完全一致；不做惯用风格化。
- 新文件放 `src/main/kotlin/<pkg>/Xxx.kt`（测试放 `src/test/kotlin/...`），**同批次删除原 `.java`**。
- 公共 API 名称不变；Java 静态调用 → `companion object` + `@JvmStatic`；静态字段 → `@JvmField`。
- **AST/图节点禁用 `data class`**；对象引用比较用 `===`。
- K2 红线：禁 `synchronized fun`（用 `@Synchronized`）；位运算用 `and/or/xor/shl/shr/ushr`；
  接口 getter 冲突用「`private var x` + 显式 `override fun getX()`」。
- 禁裸 `!!`；用 `?.` / `?:` / `checkNotNull(...)`。
- Kotlin 侧集合用 Kotlin 类型；仅在**精确覆写**显式声明 `java.util.List` 的接口时才写全限定名。
- 每个 .kt 补面向新手的中文 KDoc/行内注释（解释"做什么/为什么"，位掩码与 DEX 标志位重点注释）。
- 转完 grep 所有调用点，修静态访问、字段↔方法、合成属性↔显式 getter 的差异。
- 未过 Gate 禁止提交；提交信息用 conventional commits。

---

## 3. 全局进度台账（截至本计划制定）

| 模块 | main java | main kt | test java | test kt | 状态 |
|------|----------:|--------:|----------:|--------:|------|
| jadx-commons/jadx-app-commons | 0 | 4 | 0 | 0 | ✅ |
| jadx-commons/jadx-zip | 0 | 17 | 0 | 0 | ✅ |
| jadx-commons/jadx-analysis | 0 | 12 | **1** | 0 | 🟡 仅剩 1 测试 |
| jadx-plugins/jadx-input-api | 0 | 57 | 0 | 0 | ✅ |
| jadx-plugins/jadx-dex-input | 0 | 40 | 0 | 2 | ✅ |
| jadx-plugins/jadx-java-input | 0 | 61 | 0 | 3 | ✅ |
| jadx-plugins/jadx-smali-input | 0 | 4 | 0 | 0 | ✅ |
| jadx-plugins/jadx-apks-input | 0 | 3 | 0 | 0 | ✅ |
| jadx-plugins/jadx-apkm-input | 0 | 5 | 0 | 0 | ✅ |
| jadx-plugins/jadx-java-convert | 0 | 7 | 0 | 0 | ✅ |
| jadx-plugins/jadx-raung-input | 0 | 2 | 0 | 0 | ✅ |
| jadx-plugins-tools | 0 | 18 | 0 | 1 | ✅ |
| **jadx-core** | **532** | **36** | **674** | **0** | 🟡 C01 ✅，进 C02 |
| jadx-cli | 15 | 0 | 6 | 0 | ⏳ |
| jadx-gui | 405 | 2 | 8 | 1 | ⏳ |
| **合计剩余 .java** | | | | | **1641** |

> jadx-core main 的 556 含批次 1 的 **24 个待删重复 .java**，真实待转 **532**。

## 4. 批次登记表 —— 阶段 3：jadx-core main（真实待转 532）

> 依赖原则：AST 叶子 → attributes/instructions → utils/clsp → CFG/SSA/regions → pass 链 → codegen/api。
> 每批次 ≤ ~30 文件；若单包超限，Worker 可内部再拆，但只提交一次、只更新本表一次。

### 3.1 基础数据结构与 AST

| ID | 范围（包/文件） | 约数 | 依赖 | 状态 |
|----|----------------|-----:|------|------|
| C01 | `dex/nodes`（24 已转）收尾：删原 .java + 修 InsnNode 访问器 | 24 | — | ✅ 7ef88abe |
| C02 | `dex/attributes`（9） + `dex/attributes/nodes` 上半 | ~26 | C01 | ✅ 95d112d9 |
| C03 | `dex/attributes/nodes` 下半 | ~17 | C02 | ✅ 79869792 |
| C04 | `dex/instructions`（25） + `instructions/mods`（2） + `instructions/java`（1） | ~28 | C01 | ✅ 1d78cfb1 |
| C05 | `dex/instructions/invokedynamic`（4） + `dex/info`（8） + `nodes/parser`（1） + `nodes/utils`（3） | ~16 | C04 | ✅ c67a4e3a |

### 3.2 utils / clsp / trycatch / regions

| ID | 范围 | 约数 | 依赖 | 状态 |
|----|------|-----:|------|------|
| C06 | `dex/regions`（5） + `regions/conditions`（5） + `regions/loops`（4） | ~14 | C04 | ✅ 7129435c |
| C07 | `core/utils`（26） | 26 | C05 | ✅ 25575424 |
| C08 | `utils/android`(9)+`utils/blocks`(3)+`utils/exceptions`(7)+`utils/files`(1)+`utils/input`(1)+`utils/log`(1)+`utils/tasks`(1) | ~23 | C07 | ✅ 33a461ec |
| C09 | `clsp`（6） + `dex/trycatch`（7） | ~13 | C07 | ✅ 5ab3e5ef |

### 3.3 CFG / SSA / Regions

| ID | 范围 | 约数 | 依赖 | 状态 |
|----|------|-----:|------|------|
| C10 | `dex/visitors/blocks`（8） + `dex/visitors/ssa`（3） | ~11 | C06 | ✅ e193335b |
| C11 | `dex/visitors/regions`(18)+`regions/maker`(7)+`regions/variables`(4) | ~29 | C10 | ✅ ad789ff1 |
| C12a | `dex/visitors/typeinference` 上半 | ~15 | C10 | ✅ 764d6499 |
| C12b | `dex/visitors/typeinference` 下半 | ~15 | C12a | ✅ e299c908 |

### 3.4 Pass 链

| ID | 范围 | 约数 | 依赖 | 状态 |
|----|------|-----:|------|------|
| C13 | `dex/visitors/finaly` 主干（finaly 5 + traverser 3 + state 12） | ~20 | C12b | ✅ d6df60b6 |
| C14 | `finaly/traverser/handlers`(8)+`visitors`(4)+`visitors/comparator`(2)+`factory`(2) | ~16 | C13 | ✅ 929c3f23 |
| C15 | `deobf`（6） + `deobf/conditions`（8） | ~14 | C12b | ✅ ec7c288a |
| C16 | `dex/visitors` 顶层 39 个 pass 上半 | ~20 | C14 | ✅ 99e78587 |
| C17 | `dex/visitors` 顶层 39 个 pass 下半 | ~19 | C16 | ✅ faf2df5e |
| C18 | `visitors/rename`(4)+`usage`(3)+`shrink`(3)+`prepare`(2)+`fixaccessmodifiers`(2)+`debuginfo`(2)+`methods`(1)+`kotlin`(1)+`gradle`(1) | ~19 | C16 | ✅ 1b54a6ac |

### 3.5 codegen / xmlgen / export / api

| ID | 范围 | 约数 | 依赖 | 状态 |
|----|------|-----:|------|------|
| C19 | `codegen`(10)+`codegen/utils`(2)+`codegen/json`(2)+`json/cls`(5)+`json/mapping`(4) | ~23 | C18 | ✅ e3d77fe8 |
| C20 | `xmlgen`（19） + `xmlgen/entry`（6） | ~25 | C18 | ✅ 1cdfd66b |
| C21 | `export`（5） + `export/gen`（4） | ~9 | C19 | ✅ cf163394 |
| C22 | `api` 顶层（20） + `api/args`（5） | ~25 | C19 | ✅ 21bd1286 |
| C23 | `api/data`(8)+`api/data/impl`(5)+`api/impl`(7)+`api/impl/passes`(3) | ~23 | C22 | ⏳ |
| C24 | `api/deobf`(3)+`deobf/impl`(3)+`api/metadata`(3)+`metadata/impl`(1)+`metadata/annotations`(5) | ~15 | C22 | ⏳ |
| C25 | `api/usage`(3)+`usage/impl`(2)+`api/security`(3)+`security/impl`(1)+`api/resources`(1)+`api/gui/tree`(1)+`api/utils`(1)+`api/utils/tasks`(1) | ~13 | C22 | ⏳ |
| C26 | `api/plugins`(5)+`plugins/pass`(2)+`pass/impl`(3)+`pass/types`(4)+`plugins/options`(4)+`options/impl`(4) | ~22 | C22 | ⏳ |
| C27 | `api/plugins/events`(4)+`events/types`(3)+`plugins/resources`(3)+`plugins/loader`(2)+`plugins/data`(3)+`plugins/utils`(3)+`plugins/gui`(3) | ~21 | C26 | ⏳ |
| C28 | `core/plugins`(4)+`plugins/files`(4)+`plugins/versions`(2)+`plugins/events`(2)+`core` 顶层(3) | ~15 | C22 | ⏳ |

**里程碑 M1：** C01–C05 完成后 → 全量 `./gradlew build`（AST 子阶段收口）。 ✅ 已达成（c67a4e3a，BUILD SUCCESSFUL）
**里程碑 M2：** C01–C18 完成后 → 全量 build（`dex/*` 全转完，仅剩 codegen/api/xmlgen/export）。 ✅ 已达成（1b54a6ac）
**里程碑 M3：** C01–C28 完成后 → 全量 build（core main + api 收口）。

## 5. 阶段 3 收尾：jadx-core test 迁移（674）

> 测试代码属阶段最后，**必须在 core main 全部转完（M3）后开始**，否则 main 改签名会反复返工。
> 测试类只需机械平替 + 保留 JUnit/断言语义；包内 helper 一并转。

| ID | 目录 | 约数 | 备注 |
|----|------|-----:|------|
| T01–T04 | `tests/integration/others` | 98 | 每批 ~25 |
| T05–T06 | `tests/integration/conditions` | 60 | |
| T07–T08 | `tests/integration/trycatch` | 59 | |
| T09–T10 | `tests/integration/loops` | 56 | |
| T11–T12 | `tests/integration/types` | 47 | |
| T13–T14 | `tests/integration/inner` | 39 | |
| T15 | `tests/integration/switches` | 33 | |
| T16 | `tests/integration/enums` | 26 | |
| T17 | `tests/integration/invoke` | 23 | |
| T18 | `tests/integration/generics` | 21 | |
| T19 | `tests/integration/names` + `pkg` + `pkg2` | 22 | |
| T20–T21 | `tests/integration/inline`(18) / `arrays`(17) | 35 | |
| T22 | `tests/integration/variables`(16) + `arith`(14) | 30 | |
| T23 | `integration/java8`(11) + `functional`(8) + `api/compiler`(8) | 27 | |
| T24 | `synchronize`(7)+`rename`(7)+`deobf`(7)+`annotations`(7)+`android`(7) | 35 | |
| T25 | `core/utils`(6)+`debuginfo`(5)+`export`(5)+`api/utils/assertj`(5)+`usethis`(4)+`api`(4) | 29 | |
| T26 | 剩余零散包（profiles/extensions/jbc/fallback/code/xmlgen/plugins…）+ 单例 | ~30 | Worker 按 `find` 实时收尾 |

**里程碑 M4：** T01–T26 完成 → 全量 `./gradlew :jadx-core:test` + `./gradlew build`（core 彻底 Kotlin 化）。

## 6. 阶段 4：jadx-cli（main 15 + test 6）

| ID | 范围 | 约数 | 依赖 | 状态 |
|----|------|-----:|------|------|
| CL01 | `cli` 包全部 main（含 `config`/`commands`/`plugins`/`tools`/`clst`） | 15 | M3 | ⏳ |
| CL02 | `cli` test（含 `plugins/tools/utils/PluginUtilsTest`） | 6 | CL01 | ⏳ |

> CLI 依赖 core 公共 API；core main 收口后再动，避免 API 震荡。

## 7. 阶段 5：jadx-gui（main 405 + test 8）—— **先语法迁移**

> 阶段 5.1 严格保持原 Swing 线程模型（`SwingWorker` / `invokeLater`）；**不引入协程**。
> 阶段 5.2（协程重构）待全部语法迁移 + 测试通过后另立专项，不在本计划范围。

| ID | 包组 | 约数 |
|----|------|-----:|
| G01 | `utils` | 26 |
| G02 | `ui/codearea` + `ui/codearea/mode` | 23 |
| G03 | `treemodel` | 21 |
| G04 | `ui/action` | 18 |
| G05 | `jobs` | 17 |
| G06 | `ui/tab` + `ui/tab/dnd` | 22 |
| G07 | `ui/dialog` | 15 |
| G08 | `ui/panel` + `ui/popupmenu` | 20 |
| G09 | `ui/codearea/sync` + `sync/fallback` | 24 |
| G10 | `device/debugger`(+`smali`,`protocol`) | 20 |
| G11 | `cache/usage` + `cache/code/disk/adapters` | 24 |
| G12 | `settings` + `settings/data` | 17 |
| G13 | `settings/ui`(+`plugins`,`cache`,`shortcut`,`font`) + `settings/font` | 25 |
| G14 | `utils/ui` + `utils/shortcut`/`pkgs`/`fileswatcher`/`rx`/零散 | 25 |
| G15 | `search` + `search/providers` | 14 |
| G16 | `logs` + `report` + `ui/graphs` | 20 |
| G17 | `ui/codearea/theme` + `ui/hexviewer`(+`search`,`service`) | 18 |
| G18 | `plugins/context`+`quark`+`mappings` + `utils/plugins` | 18 |
| G19 | `ui`(+`startpage`,`filedialog`,`menu`,`cellrenders`,`export`,`treenodes`) | 23 |
| G20 | `cache/code`(+`disk`,`manager`) + `events`(+`types`,`services`) + `tree` + 顶层入口 | 15 |
| G21 | gui test（8 java；`TestJadxUpdate.kt` 已转） | 8 |

## 8. 阶段 1 尾巴

| ID | 范围 | 约数 | 状态 |
|----|------|-----:|------|
| A01 | `jadx-commons/jadx-analysis` 剩余 1 个测试（JadxCallGraphTest） | 1 | ⏳ |

---

## 9. 坑位速查（Worker 必须自查）

1. 删原 `.java`（最常见返工点）；`.kt` 与 `.java` 同名共存 = `Redeclaration`。
2. `parentInsn` 这类 `protected` 字段：Kotlin `protected` 不跨包 → 用公共 getter/setter（C01 踩过）。
3. Kotlin 属性不自动实现接口抽象方法 → 显式 `override fun`。
4. 合成属性 `.name`/`.type` 依赖 Java 平台类型；转 Kotlin 后上游 `.kt` 调用点需改为真实 property 或显式 getter。
5. 可空性：转前 grep `new Xxx(` 确认可能为 null 的参数，如实标 `?`，否则集成测试才爆 NPE。
6. `java.util.List` 仅在精确覆写接口时出现；其余用 Kotlin 集合。
7. 大写属性名需 `@file:Suppress("ktlint:standard:property-naming")`；构造参数行尾注释要上移。
8. 提交前必跑 `./gradlew spotlessApply`，否则 `spotlessCheck` 在后续全量 build 才爆。

### 9.1 批次 C01 实战教训（后续每单必看）

删除原 .java 后，Java 调用方会立刻暴露“JVM 表面”差异。C01 共修了 **100 个 compileJava 错误 + 58 个下游 Kotlin 调用点 + 多个运行时回归**，清单：

**编译期（Kotlin 侧改）：**
- 属性 getter 与显式函数同名冲突 → `@get:JvmName("…Value")`（C01 用于 `instructions/predecessors/successors/packages/subPackages/classes/arguments`）。
- 原 Java `getIDom()/setIDom()` → `@get:JvmName("getIDom") @set:JvmName("setIDom")`；缺失的 `getCId()` 补回。
- Java 子类访问 `protected` 字段 → `@JvmField protected`（C01：`InsnNode.insnType/offset`）。
- Java 静态调用 → `@JvmStatic`（`updateBlockPositions`、`wrapArg`、`duplicateArg`、`addSyntheticClass`、`getForClass/getOrBuild`）；泛型静态方法需去 reified。
- Java 子类覆写的方法必须 `open`；协变返回要显式（`getUseIn()` 返回 `List<ClassNode>`）；受检异常加 `@Throws(CodegenException::class)`。
- SAM 接口 → `fun interface`（`ICodeDataUpdateListener`）；构造函数多默认参 → `@JvmOverloads`；被测试使用的旧构造器要保留（`RootNode(JadxArgs)`）。

**运行时（重点）：**
- Kotlin `!!`/非空返回 会在原 Java 返回 null 处 NPE → 恢复可空（`getBasicBlocks`、`BlockNode.doms/postDoms`、`clsData`）。
- `lateinit` 字段可能未初始化 → 改可空（`ClassNode.clsData`）。
- 构造函数初始化顺序：先赋 `packageNode` 再 `load()`（否则 lateinit 未初始化）。
- `lock()/unlock()` 集合别名与 `as ArrayList` 强转 → 保留原始可变列表语义，`lockList` 返回 `singletonList/emptyList` 不可强转。
- 自递归陷阱：`MethodNode.toAttrString()` 应调 `super.toAttrString()`。

**下游 Kotlin 调用点（重点）：**
- Java 类的合成属性（`.methodInfo`、`.sVars`、`.argRegs`、`.useIn`、`.type`、`.isConstructor` …）在类转 Kotlin 后**失效**，必须改为真实 property 或显式 `getXxx()`。C01 改了 `jadx-analysis`、`jadx-rename-mappings`、`jadx-kotlin-metadata`、`jadx-gui`。
- **每单派发时必须提醒 Worker：转完后 grep 全仓该类的 `.kt` 调用点。**

### 9.2 批次 C02 追加教训

- **Kotlin `contains` 不是 operator**：原 Java 接口的 `contains(...)` 被下游 Kotlin 写成 `AFlag.X !in node`，转 Kotlin 后必须给 `contains` 加 `operator` 修饰符，否则报 Unresolved。
- **`sourceLine` 变 private property 会切断合成属性访问**：`LineAttrNode.sourceLine` 转私有后，`InsnNode.kt` 里的 `.sourceLine` 失效 → 改显式 `getSourceLine()`。再次印证「转完必 grep 全仓 .kt 调用点」。
- 只要 `@JvmStatic`/`@JvmField`/`open`/`override` 到位，Java 调用方基本零改动；C02 仅需上述两处 Kotlin 侧修正。

### 9.3 批次 C03 追加教训
- **原 Java 合成属性转 Kotlin 后需改真 property**：`RegDebugInfoAttr.getName()/getRegType()` 转 Kotlin 后，`SSAVar.kt` 的 `debugInfoAttr.getName()` 失效 → 改 `debugInfoAttr.name`。
- **Getter 命名差异**：`isVisited()` 若写成属性会变成 `getVisited` → 用「私有字段 + 显式 `isVisited()/setVisited()`」。
- **K2 下裸 `SortedSet` 无法解析** → 写全限定 `java.util.SortedSet`（JVM 擦除相同）。
- 哨兵类用私有构造器 + 嵌套 `enum class` 保留。

### 9.4 批次 C04–C07 追加教训

- **`object` / `companion object` 静态成员在 Kotlin 调用点的写法不同**：`companion object` 的成员从其他 Kotlin 文件访问需 `Xxx.Companion.member`（如 `EmptyBitSet.Companion.EMPTY`、`StringUtils.Companion.notBlank`）；`object` 单例则直接 `Xxx.member`。转类时选错会让下游 Kotlin 全红。
- **自定义集合/工具返回 Kotlin `List`** 会让下游 `MutableList` 声明不兼容（`BlockNode.dominatesOn`）。
- **可空参数要如实标 `?`**：`InsnUtils.replaceInsns` 的 `Function<InsnNode, InsnNode?>`（Java lambda 返回 null）、`ListUtils.concat` 的 `first`、`BlockUtils.containsExitInsn` 等；否则集成测试才 NPE。
- **`toString` 自递归**、`lateinit` 未初始化、集合别名 `as ArrayList` 强转，都是 C01 已记录但会在新包重现的坑，逐包排查。

### 9.5 批次 C16–C17 追加教训（高危，必读）

- **Kotlin `String.split("...")` 按字面量切分，不是正则**！Java 的 `split("\\.")` 在 Kotlin 里必须写 `split(Regex("\\."))`，否则只按字面 `\.` 切。C17 用此修好 `SignatureProcessor` 3 个测试。
- **Kotlin lambda 版 `visitInsns { }` / `visitArgs { }` 在 lambda 返回非 null 时提前 return**，与 Java 的完整嵌套遍历语义不同；需要完整遍历时必须显式传 `java.util.function.Consumer`。C17 修好 26 个测试。
- 类转 Kotlin 后，`RootNode.kt` 的 `p.name` 合成属性失效 → 改 `p.getName()`（C16）。
- `object` 单例（如 `DepthTraversal`、`SaveCode`）保留 `import Xxx.member` 的静态导入可用；`companion object` 则需 `Xxx.Companion.member`。

### 9.6 批次 C18–C20 追加教训

- **`String.trim()` ≠ Kotlin `trim()`**：Java `trim()` 只去 ≤ U+0020，Kotlin `trim()` 去所有 `isWhitespace`（含 NUL 之外的更多字符）。C20 在 `readString16Fixed` 里因 NUL 填充差异导致 R 类包名回归，必须显式复刻 Java 语义。
- **Kotlin `and/or/xor` 对 `Byte`/`Short` 未定义** → 先 `.toInt()` 再位运算。
- **负数字面量方法调用优先级**：`-71.toByte()` 解析为 `-(71.toByte())`，要写 `(-71).toByte()`。
- **KDoc 里出现 `/*`（如 `res/values/*.xml`）会开嵌套注释** → 用反引号包裹路径。
- `sourceFileRename` 等保留 Java `default` 分支时，Kotlin `when` 需冗余 `else` 保语义。
- 接口 getter 转 Kotlin 后可空性变化会让下游 `.prop` 失效（C20：`parser.resStorage` → `getResStorage()` + `checkNotNull`）。

### 9.7 批次 C22 追加教训（公共 API 高危）

- **公共 API getter 一律保留显式函数**（`fun getX()`），不用 Kotlin 属性：Java 调用方零改动，且 `getRoot()` 等可空返回值语义清晰。代价是 Kotlin 调用点的 `.prop` 合成属性失效，需改 `.getX()`（本批共修 ~90 处，脚本批量处理）。
- **`internal fun` 会被 Kotlin 改名（`convertClassNode$jadx_core`），Java 测试无法调用**：`JadxDecompiler.convertClassNode/convertFieldNode/convertMethodNode/convertPackageNode/convertNodes` 必须为 public（加 `@ApiStatus.Internal` 标注非稳定），否则 `jadx-core` test 的 `JadxInternalAccess.java` 编译失败。
- **`JadxArgs` 用 Kotlin 属性最省事**：getter/setter 自动生成，但布尔 `isX` 与普通 `getX` 的 Kotlin 调用点混用；`pluginOptions` 必须是 `Map`（而非 `MutableMap`）才能接受 `mapValues` 的只读 Map，同时 Java 侧仍能 `.put()`。
- **`java.util.List` 显式参数（`JadxCodeInput.loadFiles`）从 Kotlin 传参**：Kotlin `List` 与 `java.util.ArrayList` 都不匹配显式 `java.util.List`，需 `@Suppress("UNCHECKED_CAST") input as java.util.List<Path>`。
- **构造器里的初始化逻辑不能丢**：`ResourcesLoader` 原构造器 `resTableParserProviders.add(new ResTableBinaryParserProvider())` 被漏掉，导致 ARSC 解码返回空 subFiles（`testResourcesLoad` 失败）；转换时务必逐行比对构造器/静态块。
- **`ICodeWriter.attachAnnotation/attachDefinition/attachLineAnnotation/add(String)` 参数要可空**：原 Java 实现显式判空 no-op（`add` 则 `append(null)`），声明非空会让 `InsnGen` 等调用点编译失败。
- **`@JvmStatic` 静态成员在 Kotlin 调用点写 `Xxx.member` 即可**（companion 也支持），无需 `.Companion.`。
- **`ResourceType` 枚举 companion 初始化顺序**：`EXT_MAP` 的填充代码在 `<clinit>` 中位于枚举常量之后，`getFileType` 调用时已就绪，安全。

## 10. 当前状态与下一单

- **已完成：** C01–C22（AST → utils/clsp → CFG/SSA/regions → pass 链 → codegen/xmlgen/export → api 核心）。
- **进行中：** C23（`api/data` + `api/impl` + `passes`，23 文件）。
- **待办（core main）：** C24–C28（api/deobf、metadata、usage/security、plugins 各子包、core/plugins + core 顶层）。
- **然后：** M3 全量 build → jadx-core test 迁移（T01–T26）→ jadx-cli（CL01–02）→ jadx-gui（G01–G21）→ jadx-analysis 尾测试（A01）。
- **派单原则：** 每单结尾强制要求 Worker 回报「删了哪些 .java / 新增哪些 .kt / 构建输出 / commit hash / 遗留错误」；Worker 不得自行改本文件（教训记录由 Commander 汇总）。
