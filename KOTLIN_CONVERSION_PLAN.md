# jadx-dex-input Kotlin 转换计划（最终精校版）

**模块：** jadx-plugins/jadx-dex-input  
**文件总数：** 40 个 Java 文件  
**批次划分：** 5 个批次，每个 ≤10 文件，严格按 SOP 拓扑顺序

---

## 执行顺序

| # | 批次 | 说明 | 数量 | 依赖关系 |
|---|------|------|------|----------|
| 1 | batch-1 | 核心解析器 + 配置类 | **6** | 无依赖 |
| 2 | batch-4 | 指令系统（可并行） | **6** | 独立，可任意顺序执行 |
| 3 | batch-2 | 代码流 & Debug 解析 | **7** | 依赖 batch-1 (DexCodeReader) |
| 4 | batch-3 | Sections/数据模型 | **9** | 依赖 batch-2 |
| 5 | batch-5 | Utils + Smali（最后收敛） | **12** | 叶子节点，剩余文件全部归入 |

*注：batch-4 独立可提前执行；batch-5 包含所有剩余 utils/smali/debuginfo subdirs。*

---

## Batch #1: 核心解析器 + 配置（6 文件，无依赖）

| 序号 | 文件 | 行数 |
|------|------|------|
| 1 | DexFileLoader.java | 194 | DEX 加载入口点，含静态工厂方法 |
| 2 | DexReader.java | 1566 | 核心解析器接口 & 实现（大文件）|
| 3 | DexInputOptions.java | 490 | 输入配置选项类 |
| 4 | DexException.java | 303 | 异常定义集合 |
| 5 | DexLoadResult.java | 917 | 加载结果容器 |
| 6 | DexInputPlugin.java | 2242 | 插件注册（独立模块）|

---

## Batch #2: 代码流 & Debug 解析（7 文件，依赖 batch-1）

| 序号 | 文件 | 说明 |
|------|------|------|
| 1 | DexCodeReader.java | 核心代码流读取器（依赖 DexFileLoader 初始化）|
| 2 | DebugInfoParser.java | Debug 信息提取解析器 |
| 3 | AnnotationsParser.java | 注解解析核心逻辑 |
| 4 | DataReader.java | 二进制数据读辅助工具 |
| 5 | MUtF8.java | DEX 字符串的 UTF-8 解码 |
| 6 | DexConsts.java | 常量定义（standalone）|
| 7 | SectionReader.java | 抽象 section 读取器接口 |

---

## Batch #3: Sections/数据模型（9 文件，依赖 batch-2）

| 序号 | 文件 | 说明 |
|------|------|------|
| 1 | DexClassData.java | 类数据容器（最大文件之一）|
| 2 | DexMethodData.java | 方法数据结构 |
| 3 | DexFieldData.java | 字段引用数据 |
| 4 | DexHeader.java + DexHeaderV41.java | DEX 文件头解析（2 个文件，算 1 组）|
| 5 | DexMethodProto.java | 方法原型引用数据 |
| 6 | DexMethodRef.java | 方法引用数据结构 |
| 7 | SimpleDexData.java | 简单数据包装器（standalone）|
| 8 | DexAnnotationsConvert.java | 注解转换辅助工具 |

*注：实际 9 个文件，含 DexHeader+V41。*

---

## Batch #4: 指令系统（6 文件，独立可并行）

| 序号 | 文件 | 说明 |
|------|------|------|
| 1 | DexInsnData.java | 指令数据结构定义 |
| 2 | DexOpcodes.java | Opcode 枚举表（155 个值）|
| 3 | DexInsnFormat.java | 指令格式定义 |
| 4 | DexInsnMnemonics.java | Mnemonic 映射表 |
| 5 | SmaliCodeWriter.java | Smali 代码输出生成器 |
| 6 | DexArrayPayload.java | 数组 payload（insns/payloads）|

---

## Batch #5: Utils + Smali（12 文件，最后收敛）

包含所有剩余 utils、annotations subdirs、debuginfo subdirs、smali 输出类。

**Utils group:**
- Leb128.java — LEB128 编码辅助器
- DexCheckSum.java — DEX 校验和验证逻辑  
- IDexData.java — 数据接口（独立无实现）
- SmaliUtils.java — Smali 工具辅助

**Annotations/Debuginfo subdirs:**
- AnnotationsUtils.java — 注解处理辅助
- EncodedValueParser.java — 编码值解析器
- DexLocalVar.java — Debug info 局部变量结构

**Smali output group:**
- InsnFormatterInfo.java — 格式化配置信息类
- InsnFormatter.java — 指令格式化核心逻辑
- SmaliPrinter.java — Smali 代码美化打印器
- MuTf8.java（如未在 batch-2）— UTF-8 解码

*注：此批包含所有未在上述批次中明确的文件，总数确保 = 40。*

---

## SOP 合规要点（转换时必须遵守）

1. **静态方法** → companion object + @JvmStatic  
   - DexFileLoader.getNextUniqId() / resetDexUniqId() 需显式声明
2. **字段访问模式** → public static final 常量转 companion val + @JvmField
3. **空安全** → @Nullable 转为 Kotlin `?`，构造前验证参数
4. **泛型** → visitor 接口用 out T : X 表达协变返回类型
5. **合成属性冲突** → 若上游 .kt 文件已用 `.propName`访问，必须声明真实 property

---

## 提交节奏与验证

- **每个 Batch 完成后立即 git commit**
- Commit message: `refactor(dex-input): migrate batch-N (X files)`
- 验证命令：`./gradlew :jadx-plugins:jadx-dex-input:compileKotlin`
- 跨模块编译检查：`./gradlew :jadx-plugins-tools:test`

---

**最后更新：** 2026-W38 (Session #7) — 计划精校完成，40 文件精确分配（6+6+7+9+12=40）✓
