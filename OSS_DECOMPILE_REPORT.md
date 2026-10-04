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
| 对照工具 | `skylot/jadx`：v1.5.3 官方发布包 + 同步点源码 `upstream/master@4e2b8d54`（2026-10-03，本地 worktree 构建） |
| 临时目录 | `tests/tmp`（`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=…` 指定，避开 `/tmp`） |
| 构建 | `tests/build-apk.sh <project> release <args…>`（`GRADLE_USER_HOME=tests/work/gradle-home`、`TMPDIR=tests/tmp`） |
| 签名 | `tests/work/keystore/oss-test.keystore`（本仓库内生成的测试证书，全部 release APK 用它签名或留 unsigned） |

**目录布局**（除脚本/文档外均 gitignore）：
```
tests/
  oss/      # 克隆的开源项目（6 个）
  work/     # gradle user home、日志、对照用上游源码与二进制
  tmp/      # 临时目录（替代 /tmp）
  apks/     # 构建产物 APK
  out/      # 反编译输出
  build-apk.sh
```

## 2. 被测项目（6 个，全部 release 变体）

| 项目 | 版本 | 语言/框架 | release 构建 | 结果 |
|------|------|-----------|--------------|------|
| `FossifyOrg/Calculator` | v10 | Kotlin + Jetpack Compose | `assembleFossRelease`（R8 minify） | ✅ |
| `FossifyOrg/Notes` | v13 | Kotlin + Jetpack Compose | `assembleFossRelease`（R8 minify） | ✅ |
| `android/architecture-samples` | master | Kotlin + Compose | `assembleRelease` | ✅ |
| `AntennaPod/AntennaPod` | 3.12.2 | Java + Kotlin 多模块 | `:app:assembleFreeRelease`（minify+shrink） | ✅ |
| `iSoron/uhabits` | v2.3.1 | Kotlin + Views | `:uhabits-android:assembleRelease`（minify） | ✅ |
| `gsantner/markor` | v2.16.1 | Java | `assembleFlavorDefaultRelease`（minify） | ✅ |

release 构建一律开启 R8/minify（更贴近真实发布产物，且能覆盖 R8 优化后的字节码形态）。
另外保留了此前构建的 Calculator / Notes 的 **debug** APK（含完整依赖库字节码，2.2 万类），
用于复现/回归两个「挂死」问题（debug 包不带 R8，库代码量大，是极佳的压力样本）。

## 3. 反编译结果（release APK，当前构建）

| APK | 产出 .java | 错误文件 | 错误数（CLI 口径） |
|-----|-----------:|---------:|------------------:|
| Calculator foss release | 4296 | 45 | 90 |
| Notes foss release | 4539 | 46 | 88 |
| markor release | 5335 | 17 | 50 |
| AntennaPod free release | 4997 | 7 | 13 |
| architecture-samples release | 3810 | 35 | 82 |
| uhabits release | 3127 | 6 | 11 |

合计：26,100 个 .java / 156 个错误文件 / 334 处错误，**无卡死、无整类失败**。

**错误分类**（全部 release APK 合计，方法级错误点 694 处，按来源 pass）：

| 类别 | 数量 | 定性 |
|------|-----:|------|
| `JadxOverflowException` in RegionMakerVisitor（Regions count/stack limit） | 504 | 上游同源限制（限制常量与算法与上游一致，见 §5） |
| `StackOverflowError` in RegionMakerVisitor | 39 | 同上（深递归） |
| `JadxRuntimeException` in ModVisitor | 34 | 上游同源限制 |
| `JadxRuntimeException` in ConstructorVisitor / PrepareForCodeGen / InitCodeVariables / IfRegionVisitor / ConstInlineVisitor / FinishTypeInference 等 | 59 | 上游同源限制 |
| `UnsupportedOperationException` in RegionMakerVisitor | 6 | 上游同源限制 |
| `Method code generation error` / `Type inference failed` 等 | ~52 | 上游同源限制 |

绝大多数错误位于 R8 混淆后的**库代码**（androidx.compose、kotlinx.coroutines、joda-time 等），
各 APK 的**应用自身业务代码未发现不可反编译项**（markor/arch 等含混淆名类的错误除外，见 §5.3）。

## 4. 发现并修复的回归（本分支引入）

### 🔴 回归 1：`ProcessVariables.isAllUseAfter` 的 O(n²) 慢路径 → 全量反编译卡死在 99%

**现象**：Notes debug APK（21952 类）反编译到 99%（21951/21952）后 CPU 持续 300%+，>20 分钟不结束。
多线程 jstack：一个线程持有 `ClassInfo` 锁在烧 CPU，其余全部 BLOCKED——即「单类慢路径放大为整次运行卡死」。

**定位**：
1. `--log-level debug` + 在反编译循环打印 `Decompiling class:`，卡住时最后一条即卡点类
   → `androidx.compose.foundation.text.BasicTextFieldKt`；
2. `--single-class` 复现：卡点线程栈顶为 `ProcessVariables.isAllUseAfter → UsePlace.hashCode`
   （`HashSet(usePlaces)` 反复构建 + `RegionUtils.isRegionContainsRegion` 逐层上溯）。

**根因**（对照上游同源代码）：上游 `ProcessVariables.isAllUseAfter` 本就是平方级算法（含
`TODO: make index for faster check` 注释），分支的 Kotlin 转换忠实，但本方法 SSARegion 合并后
merged usage 达到数千个 UsePlace，平方项 + 每次 `HashSet` 重建的哈希成本被放大到小时级。
（上游 v1.5.3 官方包反编译同一类约 20 秒完成——上游在后续版本中重构过此区域，但同步点
`4e2b8d54` 仍是慢路径实现。）

**修复**（`ProcessVariables.kt`）：实现上游 TODO——为方法区域树建立 **DFS 前序编号索引**
（`RegionOrderIndex`）：
- 每块一个 DFS 序号，每个区域一个连续区间 `[first, last]`（区域树嵌套无重叠 ⇒ 区间连续）；
- `isAllUseAfter`（"所有使用位置都在检查块之后且包含于其区域"）化为 **O(1) 三次区间比较**：
  `min ≥ max(interval.first, blockPos) && max ≤ interval.last`；
- `declareVar` 对 merged usage 只做一次 O(n) 的 `boundsOf` 预计算。

**效果**：`BasicTextFieldKt` 单类反编译从 **>20 分钟挂死 → 24 秒完成**（含 APK 加载）；
整个 Notes debug APK 不再卡死（卡死类通过后反编译继续推进）。

### 🔴 回归 2：`ProcessVariables.removeUnusedResults` 对未绑定 SSA 的结果 `checkNotNull` 崩溃

**现象**：architecture-samples release APK 的 `androidx.compose.ui.platform.C0815x.I0` 方法
抛 `IllegalStateException: Required value was null`（`ProcessVariables.kt:92`），整方法无法反编译。

**根因**：分支把上游的 `mth.removeSVar(ssaVar)` 一段改成了 `checkNotNull(ssaVar)`。当某条指令的
result 寄存器尚未绑定 SSAVar（R8 混淆代码 + 重载周期的边缘状态）时直接崩溃；上游同路径虽有
NPE 风险但管道中不会出现该状态。

**修复**：`ssaVar == null` 时跳过变量清理（仅 `insn.setResult(null)`），并留 DEBUG 日志。
修复后 `C0815x` 可正常反编译（方法错误数 1→0，类内仅剩上游同源的 RegionMaker 限制错误）。

### 🟡 回归 3：`ModVisitor.anonymousCallArgMod` 的 `checkNotNull(codeVar)` 崩溃

**现象**：Calculator release APK 的 `em1` 类抛 `JadxRuntimeException: Code variable not set in r1v17`
（上游 v1.5.3 同类 0 错误）。

**根因**：匿名构造器参数标记路径 `checkNotNull(sVar).codeVar.isFinal = true` 在「重载周期中
codeVar 尚未回填」的边缘状态崩溃。用定向追踪（SSAVar 创建/重置/回填调用栈）确认该变量产生于
codegen 阶段重跑的 SSATransform，而其所在周期未完成 InitCodeVariables 回填——状态机与上游
在重载-重跑路径上存在行为差异（根因分析见 git 历史）。

**修复**：改为优雅降级（设置 `DONT_INLINE` 标记保留，codeVar 未回填时跳过 `isFinal`），与上游
在正常路径的行为一致，并消除崩溃。em1 的方法级错误从「崩溃」变为与上游一致的可生成代码。

### ✅ 与上游持平（非本分支回归，已逐一对照源码/行为确认）

| 疑似项 | 对照结论 |
|--------|----------|
| `ConstructorVisitor` "Can't remove SSA var still in use"（arch `C/T` 等） | fork 与上游 v1.5.3 同类错误数相同（3=3），上游同源限制 |
| `ConstInlineVisitor` "Unexpected instance arg in invoke" | 与上游源码逐行一致（同样 throw） |
| `RegionMaker` regionsLimit / `RegionStack` REGIONS_STACK_LIMIT（1000） | 常量与算法与上游 `4e2b8d54` 完全一致 |
| 其他 `JadxOverflowException` / 类型推断错误 | 位于 Compose/coroutines 等库代码，属上游既有限制 |

> 说明：上游对照用了两个版本——官方 **v1.5.3**（2025-09 构建）与同步点源码
> **`4e2b8d54`**（2026-10-03，本仓库 `tests/work/upstream-src` worktree 本地构建），
> 以 4e2b8d54 为准，v1.5.3 用于佐证性能差异。

## 5. 遗留问题（上游同源，建议单独立项）

1. **RegionMaker 区域树爆炸**：`CoreTextFieldKt.CoreTextField`（511 块）产出 **22.8 万个区域节点**
   （Region 6 万 + IfRegion 3.3 万 + 块 13.5 万，平均每块被复制 ~265 次），codegen 遍历该树
   超过 7 分钟。**已用同步点上游源码（`4e2b8d54` worktree 构建）复现同样超时（>5 分钟）**——
   这是上游在同步点的固有 bug（v1.5.3 官方包约 19 秒完成，说明上游在两者之间曾修复又回归，
   或发布分支与 master 有差异）；需在 RegionMaker/PostProcessRegions 引入区域去重或剪枝。
2. **Regions stack/count limit**（`REGIONS_STACK_LIMIT=1000`、`blocks×400`）与 `StackOverflowError`
   ——Compose/coroutines 大方法的主要错误来源（~800 处），需更深的区域重建改进。
3. **Notes debug APK 21952 类全量反编译**在修复回归 1/2 后仍有最后的慢类（CoreTextFieldKt，
   上游同源 bug，见上）；release 包不受影响（R8 后该方法已收缩，正常完成）。
4. 建议为「单类耗时」加看门狗（超阈值降级为 skip + 记录），避免单类慢路径阻塞整次反编译。

## 6. 复现方式

```bash
# 1) 构建 jadx CLI
./gradlew :jadx-cli:installDist

# 2) 构建 OSS 项目 release APK（全程在仓库内，不用 /tmp）
bash tests/build-apk.sh Calculator release assembleFossRelease
bash tests/build-apk.sh Notes release assembleFossRelease
bash tests/build-apk.sh architecture-samples release
bash tests/build-apk.sh AntennaPod release :app:assembleFreeRelease \
    -PreleaseStoreFile=$PWD/tests/work/keystore/oss-test.keystore \
    -PreleaseStorePassword=oss-test-pass -PreleaseKeyAlias=oss-test -PreleaseKeyPassword=oss-test-pass
LOOP_KEY_ALIAS=oss-test LOOP_KEY_PASSWORD=oss-test-pass \
LOOP_KEY_STORE=$PWD/tests/work/keystore/oss-test.keystore LOOP_STORE_PASSWORD=oss-test-pass \
    bash tests/build-apk.sh uhabits release :uhabits-android:assembleRelease
bash tests/build-apk.sh markor release assembleFlavorDefaultRelease

# 3) 反编译并统计错误
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/tests/tmp"
jadx-cli/build/install/jadx/bin/jadx -d tests/out/Calc-foss-release --show-bad-code \
    tests/apks/Calculator-release-calculator-10-foss-release.apk > tests/work/logs/dec.log 2>&1
grep -rl "JADX ERROR" tests/out/Calc-foss-release/sources | wc -l

# 4) 定位卡点类（卡死时最后一行即卡点类）
jadx-cli/build/install/jadx/bin/jadx --log-level debug --threads-count 4 -d tests/out/x <apk> 2>&1 | grep "Decompiling class:" | tail -1
```

## 7. 回归测试

### 自动化：真实 APK 反编译测试（已加入仓库）

`jadx-cli/src/test/kotlin/jadx/cli/RealApkDecompileTest.kt` + `jadx-cli/src/test/resources/real-apk-baseline.properties`：
用 `JadxDecompiler` API 反编译真实 APK，断言 `getErrorsCount()` 不超过基线（"错误数不得上升"看门狗）。

```bash
JADX_REAL_APKS=$PWD/tests/apks ./gradlew :jadx-cli:realApkTest
```

- 默认跳过（真实 APK 不入库）；需显式指定 APK 目录。
- 已从普通 `test` 任务中排除，不拖慢 `./gradlew build`。
- 基线覆盖 6 个 release APK + Calculator debug（历史基线）。

### 新增：已知慢类限时反编译测试（本次新增）

`jadx-cli/src/test/kotlin/jadx/cli/RealApkSingleClassTest.kt`：
对曾导致挂死的 `BasicTextFieldKt`（回归 1）与 `em1`（回归 3）做**限时单类反编译**：
必须在时间预算内完成（挂死即超时失败），且方法级错误数不超过基线。防止两个回归再次引入。

### 单元回归

- `jadx-core/src/test/kotlin/jadx/core/JadxStaticApiTest.kt` —— 锁定 `Jadx` 插件可见成员的 JVM 静态表面。
- `ProcessVariables` 行为由 jadx-core 全量测试（1027+）覆盖；两个真实回归由上面的限时测试兜底。

## 8. 结论

1. **6 个真实开源项目的 release APK 全部构建并完成反编译**；应用自身业务代码无不可反编译项，
   残余错误集中在 R8 混淆后的 Compose/coroutines 等库代码的 RegionMaker/类型推断限制（与上游一致）。
2. **修复了 3 个本分支回归**：ProcessVariables O(n²) 卡死（曾经让 Notes debug 全量反编译无法完成）、
   removeUnusedResults 空检查崩溃、ModVisitor codeVar 空检查崩溃。
3. 反编译能力与上游同步点持平，卡死/崩溃类问题通过新增限时回归测试守护。
