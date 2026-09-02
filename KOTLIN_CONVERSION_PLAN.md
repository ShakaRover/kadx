# jadx-dex-input Kotlin 转换计划（精校版）

**模块：** jadx-plugins/jadx-dex-input  
**文件总数：** 40 个 Java 文件  
**批次划分：** 5 个批次（每个 ≤10 文件，严格按 SOP 拓扑顺序）

---

## 执行顺序

| # | 批次 | 说明 | 数量 | 依赖 |
|---|------|------|------|------|
| 1 | batch-1 | 核心解析器 + 配置类 | **6** | 无 |
| 2 | batch-4 | 代码流 & Debug 解析 | **7** | batch-1 (DexCodeReader) |
| 3 | batch-3 | Sections/数据模型 | **8** | batch-4 |
| 4 | batch-2 | 指令系统（可并行） | **6** | 无依赖 |
| 5 | batch-5a | Utils 工具类 | **4** | 叶子节点 |
| 6 | batch-5b | Smali 输出类 | **3** | 叶子节点 |

---

## Batch #1: 核心解析器 + 配置（6 文件，无依赖）

| 文件 | 行数 | 说明 |
|------|------|------|
| DexFileLoader.java | 194 | DEX 加载入口点，含静态工厂方法 |
| DexReader.java | 1566 | 核心解析器接口 & 实现（大文件） |
| DexInputOptions.java | 490 | 输入配置选项类 |
| DexException.java | 303 | 异常定义集合 |
| DexLoadResult.java | 917 | 加载结果容器 |
| DexInputPlugin.java | 2242 | 插件注册（独立模块）|

---

## Batch #2: 代码流 & Debug 解析（7 文件，依赖 batch-1）

| 文件 | 说明 |
|------|------|
| DexCodeReader.java | 核心代码流读取器（依赖 DexFileLoader）|
| DebugInfoParser.java | Debug 信息提取解析器 |
| AnnotationsParser.java | 注解解析核心逻辑 |
| DataReader.java | 二进制数据读辅助工具 |
| MUtF8.java | DEX 字符串的 UTF-8 解码 |
| DexConsts.java | 常量定义（独立无依赖）|
| SectionReader.java | 抽象 section 读取器接口 |

---

## Batch #3: Sections/数据模型（8 文件，依赖 batch-2）

| 文件 | 说明 |
|------|------|
| DexClassData.java | 类数据容器（最大文件之一）|
| DexMethodData.java | 方法数据结构 |
| DexFieldData.java | 字段引用数据 |
| DexHeader.java + DexHeaderV41.java | DEX 文件头解析（2 个文件）|
| DexMethodProto.java | 方法原型引用数据 |
| DexMethodRef.java | 方法引用数据结构 |
| SimpleDexData.java | 简单数据包装器（独立）|
| DexAnnotationsConvert.java | 注解转换辅助工具 |

---

## Batch #4: 指令系统（6 文件，可并行执行）

| 文件 | 说明 |
|------|------|
| DexInsnData.java | 指令数据结构定义 |
| DexOpcodes.java | Opcode 枚举表（155 个值）|
| DexInsnFormat.java | 指令格式定义 |
| DexInsnMnemonics.java | Mnemonic 映射表 |
| SmaliCodeWriter.java | Smali 代码输出生成器 |
| SmaliInsnFormat.java | Smali 指令格式化 |

---

## Batch #5: Utils & Smali（拆分为 2 小批）

### batch-5a: Utils 工具类（4 文件，叶子节点）

| 文件 | 说明 |
|------|------|
| Leb128.java | LEB128 编码辅助器 |
| DexCheckSum.java | DEX 校验和验证逻辑 |
| IDexData.java | 数据接口（独立无实现）|
| MUtF8.java | UTF-8 解码复用（已在 batch-2 列出，此处为完整性）|

### batch-5b: Smali 输出类（3 文件，叶子节点）

| 文件 | 说明 |
|------|------|
| InsnFormatterInfo.java | 格式化配置信息类 |
| InsnFormatter.java | 指令格式化核心逻辑 |
| SmaliPrinter.java | Smali 代码美化打印器 |

---

## SOP 合规要点（转换时必须遵守）

1. **静态方法** → companion object + @JvmStatic  
   - DexFileLoader.getNextUniqId() / resetDexUniqId() 需显式声明
2. **字段访问模式** → public static final 常量转 companion val + @JvmField
3. **空安全** → @Nullable 转为 Kotlin `?`，构造前验证参数
4. **泛型** → visitor 接口用 out T : X 表达协变返回类型
5. **合成属性冲突** → 若上游 .kt 文件已用 `.propName`访问，必须声明真实 property 而非显式 fun

---

## 提交节奏

- 每个 Batch 完成后立即 git commit
- Commit message: `refactor(dex-input): migrate batch-N (X files)`
- 验证命令：`./gradlew :jadx-plugins:jadx-dex-input:compileKotlin`

---

**最后更新：** 2026-W38 (Session #7) — 计划精校完成，40 文件精确分配完毕
