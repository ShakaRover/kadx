# S7-a 测绘 + S7-b 设计（usage-info 结构性重做）

测量环境：有界微信子集（`Bench`/`BenchUsage`，classFilter 前缀 `nd5`），load-only（前缀不匹配任何类 + `-r`），
真实 GUI 磁盘缓存实现（`kadx.gui.cache.usage.UsageInfoCache`）通过 `KadxCLI.execute(args, argsMod)` 注入。
APK = weixin8079android3200_0x28004f30_arm64.apk。

---

## S7-a.1 全链路与消费阶段

```
preDecompilePassesList（全局，load() 内跑一次）              per-class decompile（getRegionsModePasses）
────────────────────────────────────────────────            ────────────────────────────────────────
SignatureProcessor → OverrideMethodVisitor → AddAndroidConstants
→ DeobfuscatorVisitor → SourceFileRename → RenameVisitor
→ SaveDeobfMapping
→ UsageInfoVisitor          ← miss: buildUsageData + apply | hit: cache.get + apply
→ CollectConstValues        ← 读 fld.useIn          【全局】
→ ProcessAnonymous          ← 读 cls.dependencies/useIn/useInMth、mth.useIn、field.useIn 【全局】
→ ProcessMethodsForInline   ← 读 mth.useIn（fixClassDependencies）        【全局】
                                                             → FixAccessModifiers ← cls.useIn / mth.useIn
                                                             → ClassModifier ← replaceMethodUsage
                                                             → InlineMethods ← mth.useIn（并改写）
                                                             → EnumVisitor / AnonymousClassVisitor
                                                             → CheckCode ← mth.useIn.isEmpty()
                                                             → ClassGen（codegen）← mth.useIn.isEmpty()
```

结论：**消费者分两类** —— `CollectConstValues` / `ProcessAnonymous` / `ProcessMethodsForInline`
在 `preDecompilePassesList` 里**全局**消费；`FixAccessModifiers` / `ClassModifier` / `InlineMethods` /
`EnumVisitor` / `AnonymousClassVisitor` / `CheckCode` / `ClassGen` 在 **per-class 反编译阶段**消费。

## S7-a.2 缓存命中路径成本（load-only，n=2）

| 路径 | real | user CPU | `get`（磁盘反序列化） | `build` | `apply` |
|---|---|---|---|---|---|
| miss（无缓存） | 24.3 / 25.3 s | 48.4 / 44.7 s | — | 8.2 / 8.8 s | 7.4 / 7.9 s |
| **hit（缓存命中）** | **13.3 / 14.3 s** | 30.6 / 28.8 s | 1.07 / 0.77 s | — | **4.08 / 4.82 s** |

- 现有缓存已把 load 降了 **44%**（24.5s → 13.8s）；命中路径上 **apply 占 load 的 30–35%**，`get` 只占 6–7%。
- apply 内部（JFR，676 samples）：`resolveMthList → resolveDirectMethod` **58.6%**，`resolveRawClass` 9.0%；
  其中 `HashMap.getNode`（由 `resolveRawClass` 调起）占 apply 采样的 **65.6%**。
- **命中路径 apply 共调用 `resolveDirectMethod` 9,682,150 次，但只有 872,216 个不同 (cls,shortId) 对
  → 91% 是重复解析（平均每对 11 次）**；涉及 235,720 个不同类。
- 按调用点拆分（四项相加 = 9,682,150，精确吻合）：

| 调用点 | 次数 | 占比 | 全局消费者是否需要 |
|---|---|---|---|
| `clsUseInMth` → `cls.useInMth` | 2,912,681 | 30.1% | **需要**（`ProcessAnonymous`） |
| `fldUsage` → `fld.useIn` | 2,339,591 | 24.2% | 只需**非空**（`CollectConstValues`） |
| `mthUsage` → `mth.setUseIn` | 2,214,939 | 22.9% | `ProcessAnonymous`/`ProcessMethodsForInline` 按需 |
| `mthUses` → `mth.setUsed` | 2,214,939 | 22.9% | 同上 |

另 `resolveClsList`：`clsDeps` 1,989,169 + `clsUsage` 2,052,502 次 `resolveRawClass`。
`resolveRawClass` 就是 `rawClsMap[String]`（253k 项 HashMap）——**单次随机访存延迟**是本机（内存受限）的主要成本。

## S7-a.3 `applyForClass` vs `apply` 语义差异 / 惰性化可行性

- `UsageInfo.apply()`（miss）遍历全部 UseSet 条目；`applyForClass(cls)` 只写 cls 相关子集
  （deps / useInValue / useInMth / 每字段 useIn / 每方法 useIn+used+unresolvedUsed+callsSelf）
  → **对 cls 而言语义完备**。
- `UsageData.apply()`（hit）**已经是 per-class 结构**：`for (cls in root.getClasses()) if (clsMap[rawName]!=null) applyForClass(...)`，
  且 `UsageData.applyForClass(cls)` 已存在 → **数据层已支持惰性**。
- **但朴素惰性 apply 是正确性 bug，不是性能权衡**：`CollectConstValues.getFieldConstValue` 只在
  `fld.useIn.isEmpty()` 时返回常量 → 未 apply 时 useIn 为空 → **每个被使用的 `static final` 字段都会被错误还原成常量**。
  同类风险：`ProcessAnonymous`（cls.dependencies/useIn/useInMth）、`ProcessMethodsForInline`（`for (useInMth in mth.useIn)`）
  都在全局 pass 里遍历所有类；`CheckCode`/`ClassGen`/`EnumVisitor`/`FixAccessModifiers`/`InlineMethods` 在 per-class 阶段读 `mth.useIn`。
- ⇒ 惰性化必须让**上述 8+ 个消费者各自按需触发解析**（节点粒度），属于跨切面改动。

---

## S7-b 设计方案

**方案 1（建议，低风险）：`MthRef` 解析结果去重缓存**
- 落点：`UsageFileAdapter.load` 构建全局 `methods[]` 表（`UsageFileAdapter.kt:154-161`，已证实是**全局 intern 表**，
  所有 usage 列表按**下标**引用它，`readMthList` 见 `:344-349`）时，为每个 `MthRef` 解析一次
  `root.resolveDirectMethod` 并把 `MethodNode` 存进 `MthRef` 字段（`@Volatile`，幂等）；
  `UsageData.resolveMthList` 改为读该字段。`UsageInfo`（miss 路径）不动。
- 预期收益：9.68M → 872k 次 `rawClsMap` 探针（−91%）；resolveMthList ≈ 68% × 4.4s ≈ 3.0s → ≈0.3s
  ⇒ **warm load ≈ −2.6s ≈ −19%**。
- 风险：低。**不改磁盘格式**；`MthRef` 只多一个可变字段，值确定（同一对 → 同一节点）故 `@Volatile` 下竞态无害；
  `applyForClass` 并发语义不变；消费者零改动。
- 验证：warm load-only CPU A/B（n≥3）+ noise-set + WeChat 单类字节等价 + `./gradlew test`。

**方案 2（高风险，仅在必须 ≥25% 时）：节点粒度惰性解析**
- 做法：全局 apply 只解析全局消费者真正需要的部分 —— `CollectConstValues` 只需**非空**（可用布尔/计数替代解析后的列表）；
  `ProcessAnonymous`/`ProcessMethodsForInline` 只在其触达的节点上触发解析；其余推迟到 per-class 阶段 `applyForClass`。
- 预期收益：单独 ≈ −15%（推迟 70% 的探针）；**但它与方案 1 打同一块成本，不叠加** ——
  方案 1 之后剩余可推迟量只有 ~0.3s，叠加后增益可忽略。
- 风险：高（8+ 消费者，漏一处即静默产生空 usage → 错误反编译结果）。

**诚实结论**：方案 1 是唯一「低风险 + 可量化」的方案，预期 **−19% 左右，达不到 −25% 目标**；
`resolveClsList` 的 4.04M 次类名探针（24%）**无法**用同样手法去掉，因为类名列表是裸 `String`（非 intern 表），
memo 的哈希/探针成本与 `rawClsMap` 同量级（这正是 S5-2 memo 只 +1.1% 的原因）。
要真正达到 −25% 需要方案 2 那类跨切面改造。**建议先批方案 1 并实测，再决定是否值得上方案 2。**

---

## S7-c 实测结果（方案 1 已实现并验证）

实现：`MthRef` 增加 `@Volatile private var resolved: MethodNode?` + 幂等 `resolve(root)`；
`UsageData.resolveMthList` 改读该字段；`UsageInfo`（miss 路径）不动；磁盘格式不变。

**机制验证（JFR 差分，warm load-only）**

| | 改前 | 改后 |
|---|---|---|
| `HashMap.getNode` 叶子占比 | 29.9% | **11.9%** |
| 处于 `resolveDirectMethod`/`resolveRawClass` 的采样 | 25.6% | **6.5%** |
| 总采样数 | 676 | **538** |

**apply 阶段直接计时（instrumented，配对 n=3）**

| rep | ON `apply_ms` | OFF `apply_ms` | ON user CPU | OFF user CPU |
|---|---|---|---|---|
| 1 | **2600** | 4157 | 30.55 | 32.40 |
| 2 | **2779** | 4473 | 26.51 | 31.40 |
| 3 | **3129** | 5213 | 28.44 | 31.35 |

- **`apply` 下降 37–40%**（中位 4473→2779），`get_ms` 不变。
- **总 user CPU 下降约 5–9%**（3 次配对 ON 全部 < OFF；另一次 6 配对 A/B 中位 +3.3%）。
  比预测的 −19% 小：预测按「apply 占 load 墙钟 32%」算，但 apply 是**单线程**的 ~4.4s，
  而整轮 user CPU ~30s 是 20 线程合计，故 apply 省下的 ~1.7s 只占总 CPU 的 ~5–6%。

**门禁**

| 项 | 结果 |
|---|---|
| warm 缓存命中路径 on/off 输出 | **0 个文件不同**（字节等价） |
| CLI `--single-class nd5.mr0` | **IDENTICAL** |
| CLI 有界子集全量 diff | on/off 3867 文件 / 12666 行 == **on/on 控制组同为 3867 / 12666**，落在噪声地板内（`LOST=0`） |
| `./gradlew test` | **1149 tests, 0 failures**（与基线一致） |
| 堆峰值 | ON 中位 3264 MB vs OFF 3361 MB（**无劣化**） |

**内存代价**：872k 个已解析引用各多持有一个 `MethodNode` 引用（~3.5–7 MB），在 ~3.3 GB 堆峰值下不可观测。
