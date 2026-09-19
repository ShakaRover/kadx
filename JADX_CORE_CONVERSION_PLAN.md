# jadx-core Kotlin 转换计划

**模块：** `jadx-core`
**文件总数：** main 556 + test 674 = 1230 个 Java 文件
**批次划分：** ~55 个批次（main），每批 ≤10-15 文件，按 SOP 拓扑顺序

> 前置模块已全部完成：jadx-commons ✅、jadx-input-api ✅、jadx-dex-input ✅、jadx-java-input ✅、jadx-plugins-tools ✅。
> jadx-core 是整个项目的主体，包含 AST、反混淆、SSA、区域分析、pass 链、代码生成等核心逻辑。

---

## SOP 子阶段与包映射

| 子阶段 | 范围 | 主要包 | 预估文件数 |
|--------|------|--------|-----------|
| 3.1 AST | 数据模型（类/方法/字段/指令节点） | `dex/nodes`, `dex/attributes`, `dex/instructions` | ~120 |
| 3.2 utils/clsp/trycatch | 工具类、类路径解析、异常处理 | `utils/*`, `clsp`, `dex/trycatch` | ~80 |
| 3.3 blocks/ssa/regions | 基本块、SSA 分析、区域构建 | `dex/visitors/blocks`, `dex/visitors/typeinference`, `dex/regions` | ~100 |
| 3.4 pass 链 | 所有 visitors/passes | `dex/visitors/*`（除 blocks/typeinference）, `deobf` | ~200 |
| 3.5 codegen/api | 代码生成 + 公共 API | `codegen`, `api`, `export`, `xmlgen` | ~156 |

---

## Batch #1: dex/nodes（AST 核心节点，~24 文件）

所有 AST 节点类：ClassNode, MethodNode, FieldNode, Register, Type, AccessFlags, etc.
**依赖：** 无内部依赖（叶子层），仅依赖 jadx-commons/jadx-input-api

## Batch #2: dex/attributes + nodes/parser（~43 文件）

属性节点（AnnotationAttr, CodeAttr, etc.）+ 解析器
**依赖：** batch-1 (nodes)

## Batch #3: dex/instructions（~25 文件）

指令类：Insn, InvokeInsn, LoadConstInsn, etc. + invokedynamic/java/mods 子包
**依赖：** batch-1 (nodes)

## Batch #4: utils 核心（~26 文件）

StringUtils, Utils, ListUtils, FileUtils, GsonUtils, etc.
**依赖：** 无内部依赖

## Batch #5: clsp + trycatch + dex/info（~30 文件）

类路径解析、异常处理表、dex info
**依赖：** batch-1/4

## Batch #6: dex/regions + conditions + loops（~20 文件）

区域节点：Region, LoopRegion, IfElseRegion, etc.
**依赖：** batch-1/3

## Batch #7: visitors/blocks（~8 文件）

基本块构建 visitor
**依赖：** batch-1/3/6

## Batch #8: visitors/typeinference（~30 文件）

类型推断 pass
**依赖：** batch-1/3/7

## Batch #9: visitors/finaly + traverser（~25 文件）

finally 块处理
**依赖：** batch-1/3/6/7

## Batch #10: deobf + conditions（~14 文件）

反混淆 pass
**依赖：** batch-1/3/8

## Batch #11: visitors/regions + maker（~25 文件）

区域构建 visitor
**依赖：** batch-6/7/8

## Batch #12: codegen 核心（~10 文件）

代码生成器入口和基础类
**依赖：** batch-1/3/8/11

## Batch #13: codegen/json + utils（~15 文件）

JSON 输出 + 代码生成工具
**依赖：** batch-12

## Batch #14: api 核心（~20 文件）

公共 API：Jadx, JadxArgs, ICodeGenerator, etc.
**依赖：** batch-1/3/8/12

## Batch #15: api/data + impl（~15 文件）

API 数据类和实现
**依赖：** batch-14

## Batch #16: api/plugins（~30 文件）

插件系统 API
**依赖：** batch-14

## Batch #17: export + xmlgen（~25 文件）

导出和 XML 生成
**依赖：** batch-1/12

## Batch #18+: 剩余 visitors/passes（~100 文件）

按依赖关系分批处理剩余的 dex/visitors/* pass

## Final: test 迁移（674 文件）

所有测试类迁移到 src/test/kotlin/

---

## 风险点与转换要点

### R1 — AST 节点泛型覆写
- ClassNode<T>, MethodNode<T> 等泛型节点被大量 Java 子类继承
- Kotlin 侧用星投影 `ClassNode<*>` 或保留泛型参数，确保 Java 子类能正常覆写

### R2 — AccessFlags 位运算
- `(flags & Modifier.PUBLIC)` → `(flags and Modifier.PUBLIC)`（Kotlin 无 & 用于 int）

### R3 — synchronized 方法
- K2 不接受 `synchronized fun`，改用普通 fun + `kotlin.Synchronized(lock) { }`

### R4 — 大 switch 语句
- Java 的 switch(int) → Kotlin when(expr)，注意 fall-through 需要显式处理

### R5 — 静态内部类
- Java static inner class → Kotlin companion object 或顶层类（视使用情况）

---

## 提交节奏与验证

```bash
# 每批次完成后
./gradlew :jadx-core:spotlessApply
./gradlew :jadx-core:test
# SOP 要求：commit 前全量 build 必须绿
./gradlew build
```

- **每批次完成后立即 git commit**：`refactor(core): migrate batch-N (X files)`
- 跨模块调用点检查重点：jadx-cli / jadx-gui / plugins（Java 侧静态访问）
- 全量完成标志：main 556/556 + test 674/674 Kotlin，`./gradlew build` 绿

---

**最后更新：** 计划制定完成，待执行 batch-1。
