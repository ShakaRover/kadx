# 真实开源项目反编译验证报告（OSS decompile report）

**目的**：用真实开源 Android 项目验证本仓库（jadx 的 Kotlin 化 + 现代化分支）能否**完整、正常地反编译**；
对「能编译但反编译失败」的类，对照源码定位根因、修复 jadx、补测试。

**约束**：所有工作产物放在仓库内 `tests/`（已加入 `.gitignore`），**不使用 `/tmp`**。

---

## 1. 环境与工具链

| 项 | 值 |
|----|----|
| JDK | 21.0.12 |
| Android SDK | `~/Android/Sdk`（build-tools 19–37、platforms 21–37） |
| 被测工具 | 本仓库构建的 `jadx-cli/build/install/jadx/bin/jadx` |
| 临时目录 | `tests/tmp`（通过 `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=…` 指定，避开 `/tmp`） |
| 构建 | `tests/build-apk.sh <project>`（`GRADLE_USER_HOME=tests/work/gradle-home`、`TMPDIR=tests/tmp`） |

**目录布局**（全部 gitignore）：
```
tests/
  oss/      # 克隆的开源项目
  work/     # gradle user home / 日志
  tmp/      # 临时目录（替代 /tmp）
  apks/     # 构建产物 APK
  out/      # 反编译输出
  build-apk.sh
```

## 2. 被测项目

| 项目 | 语言 | 结果 |
|------|------|------|
| `FossifyOrg/Calculator`（v10） | Kotlin + Jetpack Compose | ✅ 构建成功（foss/core/gplay 三个变体），已完整反编译：22245 类 |
| `FossifyOrg/Notes`（v13） | Kotlin + Compose | ✅ 构建成功；反编译到 **99%（21951/21952）后超时（>20min）**，产出 23532 个 `.java`，错误 111 个文件 |
| `android/architecture-samples` | Kotlin | 已克隆，构建脚本就绪 |

> Calculator 的 debug APK 含 **22245 个类**，覆盖 Kotlin stdlib、kotlinx.coroutines、AndroidX Compose、Room、Jackson、Glide、jsoup 等大量真实字节码，是很好的压力样本。

### Notes 额外发现

- **有一个类让反编译卡死**：进度停在 `21951 of 21952 (99%)`，20 分钟超时未结束 → 某个 pass 存在**死循环/超慢路径**（待定位，建议后续加「单类超时」看门狗）。
- 错误分布与 Calculator 同类：`JadxOverflowException` 137、`JadxRuntimeException` 34、代码生成 6、类型推断 3、`StackOverflowError` 2。
- **App 自身代码（`org.fossify`）仅 2 个错误文件**，其余均为库字节码。

## 3. 发现与修复

### 🔴 问题 1：`Jadx.getVersion()` 丢失静态性（**本分支引入的回归**）

**现象**：jadx 一启动即失败，`exit=1`，**完全没有反编译**：
```
ERROR - Process error:
java.lang.IncompatibleClassChangeError: Expected static method 'java.lang.String jadx.core.Jadx.getVersion()'
    at jadx-plugin:example-plugin-0.1.1.jar//jadx.plugins.example.AddCommentPass.init(AddCommentPass.java:25)
    at jadx.core.ProcessClass.initPasses(...)
```
**根因**：`jadx.core.Jadx` 是**插件可见**的工具类；随 jadx 分发的 Java 插件 `example-plugin` 以**静态方式**调用 `Jadx.getVersion()`。
在「去 Java 味」阶段清理 `@JvmStatic` 时，把 `Jadx` 的成员一并去掉了静态性 → 外部 Java 插件在运行时抛 `IncompatibleClassChangeError`。
（教训：插件 API 面**不止** `jadx.api.*`，`jadx.core.Jadx` 等同样被插件静态调用。）

**修复**：`jadx-core/.../core/Jadx.kt` 给全部插件可见成员恢复 `@JvmStatic`：
`getPassesList`、`preDecompilePassesList`、`getRegionsModePasses`、`getSimpleModePasses`、`fallbackPassesList`、`version`、`isDevVersion`。

**测试**：新增 `jadx-core/src/test/kotlin/jadx/core/JadxStaticApiTest.kt`，用反射锁定这些成员的 JVM 静态性（防止再次回归）。

---

### 🔴 问题 2：`TypeUpdate.sameFirstArgListener` 的空检查回归（**本分支引入的回归**）

**现象**：修复问题 1 后，反编译完成但报 **316 个错误 / 131 个错误文件**。其中 **187 处**错误堆栈都指向同一行：
```
Caused by: java.lang.IllegalStateException: Required value was null.
    at jadx.core.dex.visitors.typeinference.TypeUpdate.sameFirstArgListener(TypeUpdate.kt:389)
    ...
    at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryPossibleTypes(...)
```
示例：`androidx.compose.material3.ProgressIndicatorKt.m2558drawLinearIndicatorqYKTg0g` 整个方法无法反编译。

**根因**：对照上游 Java：
```java
InsnArg changeArg = isAssign(insn, arg) ? insn.getArg(0) : insn.getResult();   // 可为 null
```
而 Kotlin 机械转换时把它写成了：
```kotlin
val changeArg = if (isAssign(insn, arg)) insn.getArg(0) else checkNotNull(insn.result)  // 抛异常
```
当 CONST 指令没有 result（且 `arg` 不是赋值目标）时，`checkNotNull` 抛 `IllegalStateException`，导致该方法的类型推断整体失败。

**修复**：`TypeUpdate.kt` 恢复为可空语义——没有可传播的另一侧时直接返回 `SAME`：
```kotlin
val changeArg = if (isAssign(insn, arg)) insn.getArg(0) else insn.result ?: return TypeUpdateResult.SAME
```

**效果**（同一 APK 前后对比）：

| 指标 | 修复前 | 修复后 |
|------|-------:|-------:|
| 错误文件数 | 131 | **119** |
| 总错误数 | 316 | **296** |
| `Required value was null` 真实回归 | 188 | **0**（剩余的 44 是 Compose 自身反编译出的字符串） |

---

### 🟡 问题 3：`ArgType.getObject()` 在未知类型上抛异常（**上游既有限制，非本分支回归**）

**现象**：76 处 `UnsupportedOperationException: ArgType.getObject(), call class: ArgType$UnknownArg`：
```
at jadx.core.dex.attributes.nodes.ClassTypeVarsAttr.getTypeVarsMapFor(ClassTypeVarsAttr.kt:29)
at jadx.core.dex.nodes.utils.TypeUtils.replaceClassGenerics(TypeUtils.kt:158)
at jadx.core.dex.visitors.typeinference.FixTypesVisitor.applyFieldType(FixTypesVisitor.kt:320)
```
**根因**：`ClassTypeVarsAttr.getTypeVarsMapFor(type)` 调用 `type.getObject()`，未先判 `type.isObject()`；当泛型实参为未知类型时抛异常。**对照上游同源代码，行为完全一致**——即这是 jadx 自身的既有限制，不是本分支引入的。已记录，待后续单独处理（需要在 `replaceClassGenerics` 侧对未知类型做保护）。

### 🟡 其余错误分类（均为上游既有限制）

| 类别 | 数量 | 说明 |
|------|-----:|------|
| `JadxOverflowException: Regions stack size limit reached` | 122 | 区域重建栈溢出（Compose/coroutines 大型方法） |
| `JadxRuntimeException in pass` | 33 | 类型搜索/不可变类型冲突等 |
| `Type inference failed` / `updates count limit reached` | 12 | 类型推断震荡 |
| `Method code generation error` / `Method generation error` | 9 | 代码生成 |
| `StackOverflowError in pass` | 2 | 递归 |
| `ArgType.getObject() on UnknownArg` | 76 | 见问题 3 |

按包分布：`androidx.compose` 71、`kotlinx.coroutines` 26、`androidx.collection` 10、`androidx.room` 6、`com.fasterxml.jackson` 5 … 绝大多数是**库字节码**，非 App 自身代码。

## 4. 结论

1. **本分支存在两个真实回归，均已修复**：
   - `Jadx` 插件可见成员的静态性被误删（导致 jadx 完全无法运行）；
   - `TypeUpdate.sameFirstArgListener` 的 `checkNotNull` 误用（导致 187 处类型推断失败）。
2. 修复后 Calculator 的 **119/131 错误文件下降**，其余错误集中在 Compose / coroutines 等**高难度库字节码**，且经比对多为**上游既有限制**，需要更深入的区域重建 / 类型推断改进，建议单独立项。
3. App 自身业务代码（`org.fossify.calculator`）**未发现反编译失败**。

## 5. 复现方式

```bash
# 1) 构建 jadx CLI
./gradlew :jadx-cli:installDist

# 2) 构建被测 APK（全程在仓库内，不用 /tmp）
bash tests/build-apk.sh Calculator

# 3) 反编译并收集错误
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/tests/tmp"
jadx-cli/build/install/jadx/bin/jadx -d tests/out/Calculator --show-bad-code \
    tests/apks/Calculator-calculator-10-foss-debug.apk > tests/work/logs/Calculator.log 2>&1

# 4) 统计
grep -rl "JADX ERROR" tests/out/Calculator/sources | wc -l
grep -rhoE "JADX ERROR: [A-Za-z ]+" tests/out/Calculator/sources | sort | uniq -c
```

## 6. 回归测试

### 自动化：真实 APK 反编译测试（已加入仓库）

`jadx-cli/src/test/kotlin/jadx/cli/RealApkDecompileTest.kt` + `jadx-cli/src/test/resources/real-apk-baseline.properties`：
用 `JadxDecompiler` API 反编译真实 APK，断言 `getErrorsCount()` 不超过基线（“错误数不得上升”看门狗）。

```bash
JADX_REAL_APKS=$PWD/tests/apks ./gradlew :jadx-cli:realApkTest
```

- 默认跳过（真实 APK 不入库）；需显式指定 APK 目录。
- 已从普通 `test` 任务中排除，不拖慢 `./gradlew build`。
- 当前基线：`Calculator-calculator-10-foss-debug.apk = 342`
  （注意：这是 `JadxDecompiler.getErrorsCount()` 的口径；CLI 日志里的 “finished with errors, count: N” 统计口径不同）。

### 单元回归

- `jadx-core/src/test/kotlin/jadx/core/JadxStaticApiTest.kt` —— 锁定 `Jadx` 插件可见成员的 JVM 静态表面（覆盖问题 1）。
- 问题 2 由上述真实 APK 流程覆盖（其触发需要特定 Compose 字节码形态，难以用最小 fixture 稳定复现）；已在此文档记录触发类与堆栈，便于后续补充最小复现。

## 7. 后续建议

1. 继续用 `architecture-samples` 等扩充样本，建立**错误数基线**，在 CI 中做「错误数不得上升」的看门狗。
2. **单类超时看门狗**：Notes 出现「99% 卡死 >20min」，需定位该 pass 的死循环/超慢路径（可先用 `--single-class` 二分定位）。
3. 针对 `Regions stack size limit`（122+137 处）与 `ArgType.getObject() on UnknownArg`（76 处）立项优化。
4. 把「插件可见静态面」纳入更系统的守护（`jadx.core.*` 中被插件调用的类）。
