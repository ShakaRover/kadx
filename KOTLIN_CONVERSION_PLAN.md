# jadx-java-input Kotlin 转换计划（当前活跃模块）

**模块：** `jadx-plugins/jadx-java-input`
**文件总数：** main 61 + test 3 = 64 个 Java 文件（main 约 4700 行）
**批次划分：** 10 个批次，每个 ≤10 文件，严格按 SOP 拓扑顺序（自底向上：utils → attributes → code → data model → 顶层入口 → test）

> 前置模块已全部完成：jadx-commons ✅、jadx-input-api ✅（含 IJadxAttribute/PinnedAttribute，9c04682e）、jadx-dex-input ✅。
> 本模块是阶段 2 的最后一个大输入插件；完成后仅剩 `jadx-plugins-tools`（18+1），随后进入阶段 3 jadx-core。

---

## 执行顺序总览

| # | 批次 | 包/范围 | 数量 | 依赖关系 |
|---|------|---------|------|----------|
| 1 | batch-1 | utils + 基础数据类型 | **7** | 无内部依赖（叶子） |
| 2 | batch-2 | attributes 核心接口与读取器 | **6** | 依赖 batch-1 (DataReader) |
| 3 | batch-3 | attributes/types 上半 | **7** | 依赖 batch-2 |
| 4 | batch-4 | attributes/types 下半 | **7** | 依赖 batch-2/3 |
| 5 | batch-5 | stack + debuginfo | **9** | 依赖 batch-4 (StackMapTableAttr) |
| 6 | batch-6 | code 核心（指令/栈状态） | **8** | 依赖 batch-1/2 |
| 7 | batch-7 | decoders + JavaCodeReader | **7** | 依赖 batch-6 |
| 8 | batch-8 | data model（类/方法/字段数据） | **6** | 依赖 batch-2/5/6 |
| 9 | batch-9 | 顶层入口（Loader/Plugin/Result） | **4** | 依赖全部 |
| 10 | batch-10 | test 源码迁移 | **3** | 最后收敛 |

---

## Batch #1: utils + 基础数据类型（7 文件，无内部依赖）

| 文件 | 说明 |
|------|------|
| `utils/DescriptorParser.java` (111) | JVM 描述符解析器，纯函数式工具 |
| `utils/DisasmUtils.java` | 反汇编辅助工具 |
| `utils/JavaClassParseException.java` | 异常定义 |
| `utils/ModifiedUTF8Decoder.java` | Modified UTF-8 解码（有独立测试） |
| `data/ConstantType.java` | 常量池类型枚举 |
| `data/ClassOffsets.java` (99) | class 文件各 section 偏移表 |
| `data/DataReader.java` (125) | 字节读取辅助器 |

## Batch #2: attributes 核心接口与读取器（6 文件）

| 文件 | 说明 |
|------|------|
| `attributes/IJavaAttribute.java` | 属性接口 — **通配符签名，见风险点 R1** |
| `attributes/IJavaAttributeReader.java` | 属性读取器接口 |
| `attributes/JavaAttrStorage.java` | 节点属性存储容器 |
| `attributes/JavaAttrType.java` (158) | 泛型类型常量类 — **见风险点 R1** |
| `attributes/AttributesReader.java` (106) | attribute 分发读取器（switch 大表） |
| `attributes/EncodedValueReader.java` | 编码值读取器 |

## Batch #3: attributes/types 上半（7 文件）

CodeAttr / ConstValueAttr / RawBootstrapMethod / IgnoredAttr / JavaAnnotationDefaultAttr / JavaAnnotationsAttr / JavaExceptionsAttr

## Batch #4: attributes/types 下半（7 文件）

JavaBootstrapMethodsAttr / JavaInnerClsAttr / JavaMethodParametersAttr / JavaParamAnnsAttr / JavaSignatureAttr / JavaSourceFileAttr / StackMapTableAttr

*注：types/* 共 14 文件拆两批；全部继承 IJavaAttribute，需按 R1 处理覆写兼容性。*

## Batch #5: stack + debuginfo（9 文件）

- `stack/`：StackFrame / StackFrameType / StackMapTableReader (176) / StackValueType / TypeInfoReader
- `debuginfo/`：JavaLocalVar (103) / LineNumberTableAttr / LocalVarsAttr / LocalVarTypesAttr

## Batch #6: code 核心（8 文件）

ArrayType / CodeDecodeState (211) / JavaInsnData (270) / JavaInsnInfo / JavaInsnsRegister (417，最大文件) / StackState / `trycatch/JavaSingleCatch` / `trycatch/JavaTryData`

## Batch #7: decoders + JavaCodeReader（7 文件）

- `decoders/`：IJavaInsnDecoder / InvokeDecoder / LoadConstDecoder / LookupSwitchDecoder / TableSwitchDecoder / WideDecoder
- `code/JavaCodeReader.java` (245) — 字节码解码主循环，依赖全部 decoder

## Batch #8: data model（6 文件）

ConstPoolReader (263) / JavaClassData (191) / JavaFieldData / JavaMethodData / JavaMethodProto / JavaMethodRef

## Batch #9: 顶层入口（4 文件）

JavaClassReader / JavaInputLoader (172) / JavaLoadResult / JavaInputPlugin — 插件注册与加载编排，最后转

## Batch #10: test 源码迁移（3 文件）

ModifiedUTF8DecoderTest / DescriptorParserTest / CustomLoadTest → `src/test/kotlin/`

---

## 风险点与转换要点（执行前必读）

### R1 — IJavaAttribute / JavaAttrType 通配符覆写问题（本模块最大坑位）
- input-api 的同类接口曾判定为"Kotlin 转换禁区"，后在 **9c04682e** 用**星投影**解决：`fun getAttrType(): IJadxAttrType<*>`（而非 `out T`——声明式协变不写入字节码，javac 看到不变型会拒绝窄化覆写；星投影两侧语言都接受）。
- java-input 的 `IJavaAttribute.getAttrType()` / `JavaAttrType<T extends IJavaAttribute>` 是同一模式 → **直接复用星投影方案**，转完必须全仓编译验证 jadx-core（大量 Java 子类以具体类型覆写）。

### R2 — Kotlin 关键字冲突
- 参数/字段名 `in`、`object`、`val` 等保留字：私有实现细节重命名（如 dex-input 的 `sectionReader`），公共 API 名称不变。

### R3 — 运行时可空但接口声明非空
- 沿用已验证模式：可空底层字段 + getter 内 `checkNotNull(x) { "..." }`，NPE 行为与原 Java 解引用等价（DexMethodRef/DexLocalVar 先例）。转换前 grep 所有构造调用点确认哪些参数可能为 null。

### R4 — Kotlin 属性 vs 显式 getter
- 已转 Kotlin 的上游模块（dex-input/smali-input/apks/apkm）若用 `.prop` 合成属性访问本模块类 → 必须声明真实 property；纯 Java 调用方两种写法都兼容。每批转换前 grep 该类的 .kt 调用点。

### R5 — K2/ktlint 已知红线（见 KOTLIN_CONVERSION.md 坑位备忘）
- `synchronized fun` 不被 K2 接受 → 普通 fun + kotlin.Synchronized；`(x & 1)` → `(x and 1)`；属性不自动实现接口抽象方法 → 显式 override fun；大写常量属性需 `@file:Suppress("ktlint:standard:property-naming")`。

---

## 提交节奏与验证（每批次）

```bash
# 1. 模块编译 + 测试
./gradlew :jadx-plugins:jadx-java-input:test
# 2. SOP 要求：commit 前全量 build 必须绿（含 spotlessCheck，先跑 ./gradlew spotlessApply）
./gradlew build
```

- **每批次完成后立即 git commit**：`refactor(java-input): migrate batch-N (X files)`
- 跨模块调用点检查重点：jadx-core / plugins-tools / cli / gui（Java 侧静态访问、字段改方法等）
- 全量完成标志：main 61/61 + test 3/3 Kotlin，`./gradlew build` 绿

---

## 后续路线图（本模块完成后）

| 顺序 | 目标 | 规模 |
|------|------|------|
| 阶段 2 收尾 | `jadx-plugins-tools` | main 18 + test 1 |
| 阶段 3.1~3.5 | `jadx-core`（严格按 SOP 子阶段：AST → utils/clsp/trycatch → blocks/ssa/regions → pass 链 → codegen/api） | main 556 + test 674，主体工作量 |
| 阶段 4 | `jadx-cli` | main 15 + test 6 |
| 阶段 5.1/5.2 | `jadx-gui`（先语法迁移，后协程重构） | main 405 + test 8 |
| 遗留清理 | `jadx-analysis` 剩余 1 个测试文件 JadxCallGraphTest.java | 1 |

---

**最后更新：** jadx-java-input ✅（61+3）、jadx-plugins-tools ✅（18+1）已完成。下一阶段：jadx-core（556 main + 674 test）。
