# Kotlin 转换记忆文档（Kotlin Conversion Notes）

本文档记录 jadx → Kotlin 增量转换的约定、流程与进度，供后续会话直接读取后继续工作。

## 项目背景

- 仓库：jadx（Dex 到 Java 反编译器），Gradle + Kotlin DSL 构建
- 目标：把全部 Java 源码逐步转成 Kotlin；Java/Kotlin 可同模块混编（`jadx-kotlin` convention plugin）
- 所有 `build.gradle.kts` 已加 `id("jadx-kotlin")`（commit a2baa5ec），任何模块现在都可以直接放 `.kt` 文件

## 转换约定（必须遵守）

1. **机械式转换**：保持语义与原 Java 完全一致，不做惯用风格化改造（不引入协程、高阶函数重写等）。风格化留作第二阶段。
2. **公共 API 名称不变**：类名、方法名保持一致，Java 调用方零改动。
3. **互操作注解**：
   - 原来被 Java 代码静态调用的方法 → Kotlin `companion object` + `@JvmStatic`（函数名保持原 getter 名如 `getConfigDir()`）
   - 原来被 Java 按字段访问的 public static final 常量 → companion 中 `val X = ...` + `@JvmField`（属性名必须与原 Java 字段名一致，大写命名会有 IDE 警告但无碍）
4. **Kotlin 文件位置**：`src/main/kotlin/<原包路径>/Xxx.kt`（与仓库现有 .kt 布局一致），然后删除对应 `.java`。
5. **中文注释**：每个转换出的 .kt 文件都要补充面向新手的中文 KDoc/行内注释，解释"做什么、为什么"，不能只翻译代码字面意思。
6. **引用修正**：转完后必须检查并修正所有引用该类的 Java/Kotlin 调用点（静态访问语法、字段改方法等），保证全仓编译通过。
7. **提交节奏**：每完成一个任务/批次就主动 `git commit`，conventional commits 风格（如 `refactor(app-commons): ...`）。

## 验证命令

```bash
# 受影响模块全部能编译（含测试源码）+ Kotlin 编译 + 运行测试
./gradlew <module>:compileJava <module>:compileKotlin <module>:compileTestJava \
         <dependents...>:compileJava <dependents...>:compileTestJava \
         :<module>:test
# 例：
./gradlew :jadx-commons:jadx-app-commons:test \
          :jadx-cli:compileJava :jadx-cli:compileTestJava \
          :jadx-plugins-tools:compileJava :jadx-plugins-tools:compileTestJava \
          :jadx-gui:compileJava :jadx-gui:compileTestJava
```

## 进度

| 模块 | 状态 | 说明 |
|---|---|---|
| build scripts（全部加 jadx-kotlin） | ✅ 完成 (a2baa5ec) | 14 个模块 |
| jadx-commons/jadx-app-commons | ✅ 完成 | 4 个类：JadxCommonEnv / JadxSystemInfo / JadxTempFiles / JadxCommonFiles，无自有测试，靠 cli/gui/plugins-tools 编译验证互操作 |
| jadx-commons/jadx-zip 批次1 | ✅ 完成 (3881759d) | IZipParser / ZipReaderOptions / ZipReaderFlags / FallbackException / IJadxZipSecurity / DisabledZipSecurity |
| jadx-commons/jadx-zip 批次2 | ✅ 完成 (f72a740a) | IZipEntry / ZipContent / LimitedInputStream / ByteBufferBackedInputStream / FallbackZipEntry / ZipDeflate（@JvmStatic） |
| jadx-commons/jadx-zip 批次3 | ✅ 完成 | FallbackZipParser / JadxZipSecurity / JadxZipEntry；剩 JadxZipParser.java、ZipReader.java |

### 待转换（建议顺序）

1. `jadx-commons/jadx-zip` — 已转 15/17，剩 2 个：`JadxZipParser.java`（~450 行，最大）与 `ZipReader.java`；被 jadx-core api 依赖
2. `jadx-plugins/*-input`、`rename-mappings`（各 ~30-60 行小类开始，按包分批转）
3. `jadx-commons/jadx-analysis` — 12 files + 1 test（有测试，适合做"测试兜底"试点）
4. `jadx-core` — 1242 files，主体工作量，**必须按 package 分批转换+提交+跑测试**
5. `jadx-gui` / `jadx-cli` — Swing/CLI 代码最后转（改动少、风险低）

## 已知互操作坑位备忘

- Java 静态方法 → Kotlin companion + @JvmStatic；字段访问 → @JvmField
- `Xxx::method` 方法引用传给函数式接口：Kotlin 用 lambda `{ method() }`，避免 SAM 转换歧义
- `instanceof`/`getClass().getSimpleName()` → `is` / `javaClass.simpleName`
- Java 泛型通配符 `<T extends X>` → `out T : X`（jadx-core 会大量出现）
- slf4j 的 `{}` 占位 varargs 调用在 Kotlin 里写法不变，可直接保留
- **Kotlin 属性不会自动实现接口里的抽象方法**：即使 `var maxEntriesCount` 属性的 getter JVM 名与抽象方法 `fun getMaxEntriesCount(): Int` 完全相同，编译器仍要求写显式 `override fun getUseLimited... no wait getMaxEntriesCount()`（已实测）
- 属性访问器与同名显式函数会产生 platform declaration clash：字段转成 `private var x = ...`（不生成 JVM 访问器方法）+ 显式 override fun，可完全避免冲突且零注解
- 需要改名 JVM 签名时用 `@getUseLimited... no wait getJvmName("x")`；但本工具链下该注解在部分位置会报 e 级诊断，优先用"私有属性 + 显式函数"的无注解方案
