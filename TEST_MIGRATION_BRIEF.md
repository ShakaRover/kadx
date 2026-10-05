# 测试迁移 Worker 简报（Test Migration Brief）

> 每个测试批次 Worker 开工前必读本文件 + `KOTLIN_MASTER_PLAN.md` §11 + §9.1–9.7。
> Commander 的派单只需给出「批次号 + 文件清单」，其余规则以本文件为准。

## 背景
`kadx-core/src/main` 已 100% Kotlin。当前迁移 `src/test`（及后续 kadx-cli / kadx-gui）。
集成测试的嵌套 `TestCls` 是**被测 Java 输入**（kadx 是 Java 反编译器），必须保持 Java 字节码。

## Option A（绑定策略）
对测试 `TestFoo`：
1. **测试驱动** → `kadx-core/src/test/kotlin/<package>/TestFoo.kt`（Kotlin）。
2. **Java fixture**（嵌套 `TestCls`/`TestCls2`/…）→ `kadx-core/src/test/java/<package>/TestFooFixture.java`，
   `public class TestFooFixture { ... }`；fixture Java 源码**逐字节复制**（只允许加外层 wrapper 类、
   以及 fixture 引用到的外层成员/注解）。
3. `git rm` 原 `TestFoo.java`。
4. 驱动引用 `TestFooFixture.TestCls::class.java`；嵌套类名与 `searchTestCls(classes, "TestCls")` 字符串保持不变。
5. **禁止改写断言**。唯一允许的机械改名：断言里嵌了外层 fixture 类名（`TestFoo$TestCls`）→ `TestFooFixture$TestCls`
   （外层类确实改名了）；需在报告中说明。
6. 无 Java fixture 的测试（纯 smali / 纯单元）→ 直接转 `.kt`，不建 fixture 文件。

## Kotlin 转换规则
- 保留仓库现有测试注解与期望值（`@Test`、`@NotYetImplemented`、`@TestWithProfiles`、`@Ignore`、`@Before` …）完全不变。
- K2 红线：`@Synchronized`（不用 `synchronized fun`）；位运算 `and/or/xor/shl/shr/ushr`（Byte/Short 先 `.toInt()`）；
  禁裸 `!!`；Kotlin 集合类型；数组用 `intArrayOf`/`arrayOf`。
- 对象引用比较用 `===`；字符串/数字/枚举比较用 `==`。
- 主源码已是 Kotlin：属性用 `.prop`（勿写 `getX()`）；`internal` 主源码成员在 Kotlin 测试中可访问（friend path）。
- §9.5/§9.6 陷阱：Java 正则 `split("\\.")` → `split(Regex("\\."))`；`String.trim()` ≠ Kotlin `trim()`；
  Kotlin 字符串里的 Java `$` 要写 `\$`。
- 补面向新手的中文 KDoc/行内注释。

## 验收门（未全绿禁止提交）
```
./gradlew spotlessApply
./gradlew :kadx-core:compileTestKotlin :kadx-core:compileTestJava --console=plain
./gradlew :kadx-core:test --console=plain
./gradlew build --console=plain
```
禁止删测试/关测试来变绿。若预算内做不完，提交已完成子集并在报告里列出剩余。
提交信息：`test(core): migrate <批次号> <包> to Kotlin`。

## 回报格式（≤ 10 行）
- #driver 转换数 / #fixture 提取数 / #原文件删除数；任何断言机械改名；未完成文件。
- `:kadx-core:test` 计数 + `./gradlew build` 结果。
- commit hash；任何阻塞（附完整报错文本）。
- **不要编辑 `KOTLIN_MASTER_PLAN.md`**（由 Commander 维护）。
