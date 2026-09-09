# JADX Java 至 Kotlin 全量渐进式重构与注释补全规范指南（无状态标准版）

本指南为 **JADX (Dex to Java decompiler)** 项目从 Java 全量迁移至 Kotlin 的**标准化操作手册（SOP）**。

无论是开发者还是 AI Agent（如 Claude Code, Cursor, Copilot 等），在执行逐文件迁移与注释补全任务时，**必须严格遵循本文档中定义的拓扑顺序、转换标准、K2 避坑红线与验证流程**。

> 配套文档：[KOTLIN_CONVERSION.md](./KOTLIN_CONVERSION.md)（进度记录 + 互操作坑位备忘）。两者配合使用：SOP 管"怎么做"，CONVERSION 记"做到哪了"。

---

## 1. 项目全局架构与依赖拓扑分析

JADX 内部模块存在明确的自底向上依赖关系。为了防止出现"底层 Kotlin 类变更导致上层 Java 类编译崩溃"或"类型循环依赖"的问题，**必须严格按照以下 5 个阶段的顺序逐模块、逐 Package 推进**。

```
                    [ 阶段 1: jadx-commons ]
                    (jadx-app-commons, jadx-zip, jadx-analysis)
                                 │
                                 ▼
               [ 阶段 2: jadx-plugins/jadx-input-api ]
                     与各大 Input Plugin 接口
                                 │
                                 ▼
                     [ 阶段 3: jadx-core 核心 ]
                    (内部细分为 5 个子阶段 Pass)
                                 │
            ┌────────────────────┴────────────────────┐
            ▼                                         ▼
  [ 阶段 4: jadx-cli ]                      [ 阶段 5: jadx-gui ]
                                            (需分为语法转换与协程重构)
```

---

## 2. 转换基本约定与文件目录规范

1. **机械式平替**：第一阶段保持语义与原 Java 完全一致，不做惯用风格化改造（不引入协程、高阶函数重写等）。风格化留作第二阶段。
2. **公共 API 名称与兼容性不变**：类名、方法名保持一致，保证 Java 调用方零改动。
3. **文件位置规范**：新建 `.kt` 文件于 `src/main/kotlin/<原包路径>/Xxx.kt`（测试代码置于 `src/test/kotlin/...`），**转换并验证完成后必须同步删除原 `.java` 文件**。
4. **互操作注解**：
   - 被 Java 静态调用的方法 → `companion object` + `@JvmStatic`（方法名保持原名如 `getConfigDir()`）。
   - 被 Java 字段访问的 `public static final` 常量 → `companion object` 内 `val X = ...` + `@JvmField`。

---

## 3. 分阶段逐包迁移路线图（Roadmap）

执行迁移时，**一次只处理一个 Package 或独立文件批次**。处理完该批次内的所有文件并编译通过、测试通过、**及时完成 Git 提交**后，方可进入下一个批次。

### 阶段 1：通用基础库 (`jadx-commons`)
*迁移目标：基础环境定义、ZIP 格式解包工具、分析导出工具。*
1. `jadx-commons/jadx-app-commons` ✅ 已完成（JadxCommonEnv/JadxSystemInfo/JadxTempFiles/JadxCommonFiles）
2. `jadx-commons/jadx-zip` ✅ 已完成（4 个批次：接口/选项/安全、条目/内容/io、fallback/parser/security、JadxZipParser/ZipReader）
3. `jadx-commons/jadx-analysis` ← **下一个批次**

### 阶段 2：插件 API 与输入插件接口 (`jadx-plugins`)
*迁移目标：插件开放 API、解包数据接口、输入插件实现。*
1. `jadx-plugins/jadx-input-api`
2. `jadx-plugins/*-input` (包含 `jadx-dex-input`, `jadx-java-input`, `jadx-smali-input` 等，建议从小类开始分批迁移)
3. `jadx-plugins-tools`

### 阶段 3：反编译引擎核心 (`jadx-core`)
*风险等级：极高（重度依赖图论、位运算、抽象语法树及类型推导，热点性能敏感）*  
*内部依赖严禁错乱，必须严格按照以下 5 个子阶段顺序执行：*

1. **阶段 3.1 基础数据结构与 AST 节点**：
   - `jadx.core.dex.instructions.args` (`ArgType`, `RegisterArg`, `InsnArg` 等)
   - `jadx.core.dex.instructions` (`InsnNode`, `InsnType` 等)
   - `jadx.core.dex.attributes` (`AFlag`, `AType` 属性标记架构)
   - `jadx.core.dex.nodes` (`BlockNode`, `MethodNode`, `ClassNode`, `RootNode`)
2. **阶段 3.2 DEX/Class 工具与 Classpath 校验**：
   - `jadx.core.utils` (`StringUtils`, `ListUtils` 等)
   - `jadx.core.utils.files` (`FileUtils` 等)
   - `jadx.core.clsp` (Classpath 校验与类层次结构加载)
   - `jadx.core.dex.trycatch` (Catch 块与 Exception 处理器)
3. **阶段 3.3 控制流图与 SSA 变换**：
   - `jadx.core.dex.visitors.blocks` (Block 切分与 Dominator 树计算)
   - `jadx.core.dex.visitors.ssa` (SSA  静态单赋值转换)
   - `jadx.core.dex.visitors.regions` (Region 区域分析)
4. **阶段 3.4 核心 Pass 转换链**：
   - 按逻辑复杂度递增：`debuginfo` ➔ `typeinference` ➔ `shrink` ➔ `reconstruction` ➔ 其余 Visitor
5. **阶段 3.5 代码生成与对外 API**：
   - `jadx.core.codegen` (`CodeWriter`, `ClassGen`, `InsnGen`)
   - `jadx.api` (`JadxDecompiler`, `JadxArgs` 等主入口)

### 阶段 4：命令行工具 (`jadx-cli`)
1. `jadx.cli.clsp`
2. `jadx.cli` (`JadxCLI`, `JadxCLIArgs`)

### 阶段 5：图形界面系统 (`jadx-gui`)
> **注意**：为了规避线程死锁与 UI 异常，`jadx-gui` 的迁移**必须分为两步**：
1. **阶段 5.1（语法直接迁移）**：保持原 Swing 线程模型（`SwingWorker`、`SwingUtilities.invokeLater`），将 `.java` 逐包转为 `.kt`。
2. **阶段 5.2（协程异步重构）**：在语法全量迁移完成且测试通过后，再单独将 `jadx.gui.jobs` / `BackgroundWorker` 机制重构为 Kotlin Coroutines (`Dispatchers.Swing`)。

---

## 4. 关键规则、K2 避坑指南与防御性编码规范

AI 在自动转换代码时极易引入死锁、内存泄漏、GC 停顿或逻辑变更。**以下规则为绝对红线，必须强制遵守：**

### 🔴 规则 1：AST 节点与图节点类绝对禁用 `data class`
* **涉及类**：`InsnNode`, `BlockNode`, `ArgType`, `RegisterArg`, `MethodNode`, `ClassNode` 等。
* **原因**：AST 和 Block 图存在大量双向/循环引用（例如 `BlockNode.successors` 与 `InsnNode.parent`）。声明为 `data class` 自动生成的 `equals/hashCode/toString` 会引发 **`StackOverflowError`**。
* **正确做法**：使用普通 `class`，保留基于 Identity 的比较。

### 🔴 规则 2：显式区分引用比较 `===` 与值比较 `==`
* **原因**：Java 中 `a == b` 对对象比较内存地址。Kotlin 的 `==` 会编译为 `equals()` 调用。
* **正确做法**：控制流与 AST 节点比较严格写作 **`===`**。

### 🔴 规则 3：Kotlin 2.3.10 (K2) 编译器语法避坑避雷
* **禁用 `synchronized fun f()`**：K2 编译器不接受此语法，报 `Expecting member declaration`。必须写为普通 `fun f()` 并在函数头添加 **`@Synchronized`** 注解。
* **位运算必须使用中缀关键字**：`(flags & 1)` 会被 K2 解析为函数调用报错。必须写作 **`(flags and 1)`** 或 **`(flags or 2)`**。
* **接口方法与属性 Getter 签名冲突**：若接口有 `getMaxEntriesCount()`，直接声明 `var maxEntriesCount` 属性可能会产生 JVM 签名冲突。
  * **解决方案**：使用 **"私有属性 `private var maxEntriesCount` + 显式 `override fun getMaxEntriesCount()`"** 模式，零注解且无签名冲突。
* **类型提升**：Kotlin 无隐式数字提升，Int/Long 比较或传参必须显式使用 `.toLong()` / `.toInt()`。

### 🔴 规则 4：消除平台类型与 `!!` 强解包
* 不可空声明 `T`，可空声明 `T?`。对于可空变量使用 `?.`、`?:` 或 `(checkNotNull())`，严禁裸写 `!!`。

### 🔴 规则 5：热点循环性能规范
* 在 `jadx-core` 密集 Pass 中，避免使用高频创建 Iterator/Collection 对象的集合函数（如 `.map/.filter`），保持标准 `for` 循环。

### 🔴 规则 6：严禁在 Kotlin 源码中显式引用 `java.util.List` / `Map` / `Set`（Kotlin 侧目标位置）
* **现象**（2026-09 实测，Kotlin 1.9.24 / 2.2.20 / 2.3.10 / 2.3.21 均复现，非版本回归，属 Kotlin 设计行为）：
  * `java.util.ArrayList` → 目标 `java.util.List`（显式）：❌ `return type mismatch`
  * `kotlin.collections.ArrayList` → 目标 `java.util.List`（显式）：❌
  * `java.util.List`（值）→ `kotlin.collections.List` 参数：❌ `argument type mismatch`
  * `java.util.ArrayList`（类）→ 目标 `kotlin.collections.List`：✅
  * `java.util.List` 作**参数**类型：✅（如 `JadxCodeInput.loadFiles(input: java.util.List<Path>)`）
  * 本质：Kotlin 把 `java.util.ArrayList` 等**类**映射到 `kotlin.collections.*`，其超类型是 Kotlin 侧接口；而源码中显式写的 `java.util.List` 是另一个描述符，两者互不兼容。字节码层面二者都是 `java.util.List`，对 Java 调用方无差异。
* **正确做法**：
  1. Kotlin 函数签名中的集合类型一律用 Kotlin 侧 `List` / `MutableList`（字节码不变，Java 调用方兼容）。
  2. 实现 `JadxCodeInput` 等**显式声明 `java.util.List` 的 Kotlin 接口**时，覆写签名必须精确匹配（`java.util.List<Path>`）；接口传来的值要传给 Kotlin 侧 `List` 参数时，需经 `java.util.ArrayList` 中转：`val l = java.util.ArrayList<T>(); l.addAll(input); f(l)`（`java.util.ArrayList` → Kotlin List 参数 ✅）。
  3. 接收 Java 方法返回的 List（Kotlin 视角为 Kotlin List）的 Kotlin 函数，参数用 Kotlin 侧 `List`。
* **已验证案例**：`jadx-dex-input` batch-1 的 `DexInputPlugin.loadFiles`（公共方法 Kotlin List 参数 + 接口覆写 `java.util.List` + `java.util.ArrayList` 桥接）。

---

## 5. 单文件重构与注释补全标准执行流程（SOP）

```
┌─────────────────────────────────────────────────────────┐
│ 步骤 1：深度上下文分析                                   │
│ - 读取目标文件及其继承链、引用类                         │
│ - 识别算法意图（如位掩码、图遍历、控制流恢复）            │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│ 步骤 2：Kotlin 语法重构                                 │
│ - 建立在 src/main/kotlin/<package>/Xxx.kt               │
│ - 应用 @Synchronized, and/or 位运算, === 引用比较        │
│ - 避免 data class 滥用与签名冲突                        │
│ - 删除原 .java 文件                                     │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│ 步骤 3：补全 KDoc 文档与行内算法注释                    │
│ - 编写类/方法级别的中文 KDoc 注释                      │
│ - 对位运算、DEX 规范标志位补全意图说明                   │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│ 步骤 4：编译校验、回归测试与及时 Git 提交                 │
│ - 运行 `./gradlew spotlessApply`（自动修复格式违规）      │
│ - 运行 `./gradlew build`（全量构建，含 checkstyle/       │
│   spotlessCheck/test，必须 BUILD SUCCESSFUL）            │
│ - 构建通过后立即提交 Git:                               │
│ `git commit -m "refactor(<mod>): migrate <pkg>"`        │
└─────────────────────────────────────────────────────────┘
```

---

## 6. 给 AI Agent (提示词) 的标准工作 Prompt 模板

````markdown
你现在是一名熟悉 JVM 字节码、DEX 文件结构及 Kotlin 2.3.10 K2 编译器特性的资深编译器工程师。
请按照《JADX Java 至 Kotlin 全量渐进式重构与注释补全规范指南（无状态标准版）》的要求，将我提供的 JADX Java 文件重构为高质量的 Kotlin 代码。

【重构与注释要求】：
1. **代码质量与互操作性**：
   - 彻底消灭平台类型与 `!!` 强解包。被 Java 静态引用的方法/字段添加 `@JvmStatic` / `@JvmField`。
   - 原 Java `==` 节点引用比较严格写作 `===`。
   - 严禁将 AST/Block 节点声明为 `data class`。
   - **K2 避坑**：synchronized 方法改写为普通 `fun` + `@Synchronized` 注解；位运算改用 `and`/`or` 关键字；接口重写方法如遇到签名冲突使用"private 属性 + 显式 override 函数"。
   - 新文件放置在 `src/main/kotlin/<package_path>/` 目录，并在完成后提示删除原 `.java` 文件。

2. **中文注释与文档补全**：
   - 将原 Javadoc 重构为标准中文 KDoc。补充位运算、DEX 标志位与 CFG 算法目的注释。

3. **输出格式**：
   - 仅输出完整重构后的 `.kt` 代码文件，不要截断代码。

以下是需要重构的 Java 文件源码：

```java
// [在此处粘贴你的 Java 源码]
```
````

---

## 7. 质量验收与 Git 提交规范

每完成一个 Package 的迁移，执行跨模块验证并提交：

```bash
# 自动修复代码格式违规（spotless），避免提交后 spotlessCheck 失败
./gradlew spotlessApply

# 🔴 硬性要求：全量构建必须通过（含 checkstyle / spotlessCheck / 全部测试）
#    未通过 `./gradlew build` 严禁执行 git commit，防止把格式/编译问题带入主干
./gradlew build

# BUILD SUCCESSFUL 后立即进行 Git Commit 提交
git add .
git commit -m "refactor(<module_name>): migrate <package_name> to Kotlin"
```

> **教训记录（2026-10）**：曾出现仅跑模块级 `compileKotlin`/`test` 就提交，导致 `spotlessKotlinCheck`（import 顺序、尾随逗号等）在后续全量构建时才暴露失败。因此**每次提交前必须保证 `./gradlew build` 通过**。
