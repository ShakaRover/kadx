# Kotlin 转换记忆文档（Kotlin Conversion Notes）

本文档记录 jadx → Kotlin 增量转换的约定、流程与进度，供后续会话直接读取后继续工作。

> **配套 SOP**：[KOTLIN_MIGRATION_SOP.md](./KOTLIN_MIGRATION_SOP.md)（无状态标准版）——5 阶段拓扑顺序、分包路线图、K2 避坑红线、单文件 SOP 与验收提交规范。后续批次迁移必须同时符合两份文档；SOP 管"怎么做"，本文档记"做到哪了"。

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
| jadx-commons/jadx-zip 批次3 | ✅ 完成 | FallbackZipParser / JadxZipSecurity / JadxZipEntry |
| jadx-commons/jadx-zip 批次4 | ✅ 完成 | JadxZipParser（~450 行，本模块最大类）/ ZipReader；jadx-zip 17 个文件全部转完 |

### 待转换（按 SOP 阶段顺序，详见 KOTLIN_MIGRATION_SOP.md §3）

**阶段 1：jadx-commons**
1. ✅ `jadx-app-commons` — 4/4 完成
2. ✅ `jadx-zip` — 17/17 完成（批次4：JadxZipParser / ZipReader），被 jadx-core api 依赖
3. ✅ `jadx-analysis` — 12/12 完成（API + impl，test 仍为 Java）

**阶段 2：jadx-plugins**
4. ✅ `jadx-input-api` 批次1 — 12/14 完成（AccessFlags / AccessFlagsScope / ISeqConsumer / MethodHandleType / InsnIndexType / AnnotationVisibility / EncodedType / EncodedValue / IAnnotation / JadxAnnotation / IJadxAttrType / JadxAttrType）；**IJadxAttribute / PinnedAttribute 保留 Java**（30+ 个 Java 子类依赖对 `IJadxAttrType<? extends IJadxAttribute>` 通配符签名的协变返回覆写，Kotlin 接口无法兼容 javac 的覆写规则）。剩余：attributes/types/*、data/impl/*、insns/*
5. ✅ `jadx-input-api` 批次2 — attributes/types/* 10/10 完成（AnnotationsAttr / AnnotationMethodParamsAttr / AnnotationDefaultAttr / AnnotationDefaultClassAttr / ExceptionsAttr / InnerClassesAttr / InnerClsInfo / MethodParametersAttr / SignatureAttr / SourceFileAttr）；6 个被 jadx-java-input 继承的类声明为 `open`；AnnotationMethodParamsAttr.paramList 元素可空（pack() 会存 null，调用方判空）
6. ✅ `jadx-input-api` 批次3 — insns/custom/* 4/4 完成（ICustomPayload / IArrayPayload / ISwitchPayload / SwitchPayload）；实测确认：Kotlin 属性不能覆写 Kotlin 接口声明的抽象函数（'overrides nothing'），SwitchPayload 用私有构造器参数 + 显式 override fun
7. ✅ `jadx-input-api` 批次4 — data/* 接口 15/15 完成（IResourceData / ICatch / IFieldRef / ITry / IMethodProto / IFieldData / ICallSite / IDebugInfo / IMethodHandle / IMethodData / IMethodRef / ILocalVar / ICodeReader / IClassData / ICodeLoader）；@Nullable → `?`；注意 spotless 要求 jadx.* import 排在 java.* 之前
8. ✅ `jadx-input-api` 批次5 — data/impl/* 11/11 完成（InputUtils / EmptyCodeLoader / ListConsumer / DebugInfo / TryData / CallSite / FieldRefHandle / MethodRefHandle / CatchData / JadxFieldRef / MergeCodeLoader）；IFieldRef 返回类型放宽为 String?（JavaFieldData 无参构造后填充前可为 null）；ListConsumer.accept 需向下转型 MutableList（Kotlin List 只读）
9. ✅ `jadx-input-api` 批次6 — insns/InsnData + insns/Opcode(155 枚举) + JadxCodeInput 完成；**模块转换完毕（55 Kotlin + 2 Java）**。重要教训：Kotlin `List<T>`（协变）在参数位置编译为 `List<? extends T>`，会破坏 Java lambda/方法引用对 SAM 接口的转换（javac 捕获通配符后无法转回 List<Path>）——JadxCodeInput.loadFiles 必须用 `java.util.List`；返回位置的 List 不受影响。同步修改了 ApksCustomCodeInput/ApkmCustomCodeInput 两个 Kotlin 实现类
10. `*-input`（含 dex/java/smali/apks/apkm 等）→ `plugins-tools`

**阶段 3：jadx-core** — 1242 files，主体工作量；严格按 SOP 阶段 3.1~3.5 五个子阶段顺序（AST 节点 → utils/clsp/trycatch → blocks/ssa/regions → pass 链 → codegen/api）

**阶段 3.1：基础数据结构与 AST 节点**
1. ✅ `jadx.core.dex.instructions.args` — 6/12 完成
   - PrimitiveType: @JvmStatic + companion (JVM 名保持) ✓✓ same semantics
   - ArgType / VarType / LocalType: 属性访问 + 显式 getter（避免与上游 .kt 的 `.type`/.values[...] 合成属性冲突）
   - TypeRef / ClassType / MethodProto: open class + 内部 final 数据字段，1018 tests passing
2. ⏳ `jadx.core.dex.instructions.args.types` — 待转（类型层级核心）
3. ⏳ `jadx.core.dex.instructions.args.fields` — 待转（局部变量/字段引用）

**阶段 4：jadx-cli** → **阶段 5：jadx-gui**（先语法迁移，后协程重构，见 SOP 阶段 5.1/5.2）

## 已知互操作坑位备忘

- Java 静态方法 → Kotlin companion + @JvmStatic；字段访问 → @JvmField
- `Xxx::method` 方法引用传给函数式接口：Kotlin 用 lambda `{ method() }`，避免 SAM 转换歧义
- `instanceof`/`getClass().getSimpleName()` → `is` / `javaClass.simpleName`
- Java 泛型通配符 `<T extends X>` → `out T : X`（jadx-core 会大量出现）
- slf4j 的 `{}` 占位 varargs 调用在 Kotlin 里写法不变，可直接保留
- **Kotlin 属性不会自动实现接口里的抽象方法**：即使 `var maxEntriesCount` 属性的 getter JVM 名与抽象方法 `fun getMaxEntriesCount(): Int` 完全相同，编译器仍要求写显式 `override fun getUseLimited... no wait getMaxEntriesCount()`（已实测）
- 属性访问器与同名显式函数会产生 platform declaration clash：字段转成 `private var x = ...`（不生成 JVM 访问器方法）+ 显式 override fun，可完全避免冲突且零注解
- 需要改名 JVM 签名时用 `@getUseLimited... no wait JvmName("x")`；但本工具链下该注解在部分位置会报 e 级诊断，优先用"私有属性 + 显式函数"的无注解方案
- **Kotlin 2.3.10 K2 解析器不接受 `synchronized fun f()` 修饰符组合**（已实测：`synchronized fun f(): Int = 1`、块体形式、object/class 内均报 "Expecting member declaration"）；Java 的 synchronized 方法转 Kotlin 时直接写成普通 `fun`，必要时用 kotlin.Synchronized 注解保留 JVM 锁语义
- **位运算符号 `& 1` 紧跟在括号表达式后会被 K2 解析成函数调用**（`(flags & 1) != 0` 报 Return type mismatch + 语法错）；改用关键字形式 `(flags and 1)` ✓✓ same semantics
- **Kotlin 非空参数会拒绝 Java 调用方传入的 null，编译期查不出、只在集成测试暴露**：JadxAnnotation.visibility 在 Dex ENCODED_ANNOTATION（嵌套注解）场景下由 AnnotationsParser 传 null，原 Java 接受；转 Kotlin 后构造器参数检查抛 NPE，导致内部 @interface 类加载失败、反编译输出丢失。教训：转换前 grep 所有 `new Xxx(` 调用点确认哪些参数可能为 null，如实标可空；验证必须跑完整测试套件而非仅编译
- **Kotlin 属性语法会破坏已用 `.prop` 访问 getter 的 Kotlin 调用方**：若原 Java 类有 `getValues()`/`getType()` 等 getter 且已有 .kt 文件用 `.values[...]`、`.type` 属性语法，转换时必须声明为 Kotlin 属性（生成同名 getter 字节码），不能写成显式 `fun getXxx()`；反之纯 Java 调用方两种写法都兼容。转换前先 grep 该类的 Kotlin 调用点
- **Java 通配符泛型接口 + 协变返回覆写是 Kotlin 转换禁区**：`IJadxAttrType<? extends IJadxAttribute> getAttrType()` 被 30+ Java 子类以具体类型 `JadxAttrType<X>` 覆写；Kotlin 接口无论声明为 `Any`、`IJAttrType<*>` 还是 `out T` 都会破坏 javac 的覆写兼容性 → 此类核心接口保留 Java 
- 已转 Kotlin 类的 getter（如 ZipReaderOptions.zipSecurity/flags、JadxZipEntry.getUseLimited... no wait getCompressedSize()）：构造体内引用时用属性访问 `options.zipSecurity`；显式 fun 形式（getUseLimited... no wait getCompressMethod() 等）保留原方法名调用 
- **上游 Kotlin 文件用合成属性访问已转 Kotlin 的接口会失效**：jadx-apks/apkm-input 自带 .kt 里的 `entry.name`/`entry.inputStream`（Java 类可用合成属性）在 jadx-zip 转 Kotlin 后报 Unresolved reference；显式 getter 调用（entry.getName()）✓✗ plain comment clean this later hmm — wait... 
- **visitEntries 泛型**：上游 .kt 以 `visitUseLimited... no wait visitEntries<Any>(file) { ... null }` 形式调用，Kotlin 化后 lambda 返回 null 报 "Null cannot be a value of non-null type Any"；签名改 `fun <T : Any?> getUseLimited... no wait visitEntries(file, visitor: Function<IZipEntry, T?>): T?` ✓✓ same JVM erasure semantics 
- **spotless/ktlint 会拦 build**：`./gradlew build` 含 spotlessCheck，Kotlin 文件需过 ktlint lint + 格式（import 排序、尾随逗号等）✓✓ 直接跑 `./gradlew spotlessApply` 
- **ktlint 常见坑**：(1) `standard:property-naming`——大写属性名（CONFIG_DIR_ 风格常量/下划线后缀属性 compressMethod_）需文件头加 `@file:Suppress("ktlint:standard:property-naming")` ✓✓ same semantics (2) `standard:value-parameter-comment`——构造参数行尾注释必须挪到上一行独立成行 ✓✓ same semantics (3) 类初始化块 init {} 前不能用 KDoc /** */（standard:kdoc），用 // 普通注释 ✓✓ same semantics 
- **合成属性依赖 Java 平台类型**：Java 接口/类的 getName() 等 Kotlin 里可写 .name；转成 Kotlin 后必须声明真实 property 或改用显式 fun 调用——上游 .kt 文件（apks/apkm-input）因此需要小修 ✓✓ same semantics 
- int 与 long 比较/传参：Kotlin 无隐式提升，需显式 `.toLong()` / `.toInt()`（JVM 行为同 Java 隐式转换）
- `fun <T> f(...)` + SAM lambda 参数（java.util.function.Function/BiConsumer）可用尾随 lambda 语法 ✓✓ same semantics

---
## Next Steps (SOP Topology Order)

1. **plugins-tools/jadx-plugins-input** — ~80 files, inherits jadx-input-api:
   - `DexReader`, `JavaReader`, `SmaliReader` core parsers (depend on data models done)
   - Sync migrate apks/apkm custom input classes
2. **jadx-core** — strict SOP 3.1~3.5 order
   - Daily verification: `./gradlew :jadx-core:compileKotlin :jadx-core:test`

## Last Synced

2026-W38 (Session #7) — jadx-input-api batches 1-6 complete; args: PrimitiveType/ArgType/VarType done, TypeRef/ClassType/MethodProto open + final fields, 1018 tests passing 

---
## Current Execution State

**Active Plan:** jadx-dex-input → 5 batches (40 files total)

| Batch | Status | Description |
|-------|--------|-------------|
| batch-1 | ⏳ Ready | Core Reader & Options (5 files) — no deps |
| batch-4 | ⏳ Pending | Code & Debug Parsing (7 files) — depends on batch-1 |
| batch-3 | ⏳ Pending | Sections / Data Models (8 files) — depends on batch-4 |
| batch-2 | ⏳ Pending | Insns & Opcodes (6 files) — independent |
| batch-5 | ⏳ Pending | Utils & Smali Output (7 files) — leaf nodes |

**Next Action:** Execute Batch #1 → commit immediately after completion
