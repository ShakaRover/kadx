# KADX 大 APK 性能优化任务书（pi 实施版）

> **协作协议**：本文件由审查方（ZCode agent）维护。pi 按任务顺序实施，完成后在终端报告
> `TASK-N DONE`，审查方 review diff、跑验证。**实施前必读下方「当前基线」与「红线」。**
> 工作分支：master（小步提交，每个任务一个 commit，消息格式 `perf(scope): ...`）。

## 当前基线（2026-10-06，master @ 374f47426+，Linux 机器测得）

- 测试样本：`/Users/shakarover/Downloads/weixin8079android3200_0x28004f30_arm64.apk`
  （272MB、18 dex、188,767 类、939,022 方法、47.6M 指令；macOS 上路径，
  Linux 会话原路径为 `/home/shakarover/Downloads/wechat/`）
- CLI 全量反编译（`-j 20 --show-bad-code`）【Linux 数据，M4 需重测校准】：
  - kadx（当前）：**~715s**，失败方法 **0**，错误注释 **0**（此前的 520/547 已全部修复，勿回退）
  - 上游 1.5.6 与 master：401-409s，失败方法 520、错误注释 547
- GUI 冷启动：加载 253,250 类；磁盘缓存命中 **7 个类**（接近零命中，见任务三）
- 全量测试：1034 个集成测试全绿（`./gradlew test`）

## 会话 2 状态（2026-10-06，macOS Apple M4 / 10 核 / 16GB）

- 任务 1 已完成并合入（56cd4f8aa）：重试风暴修复本身正确，但**诚实结论**：
  墙钟时间未变（738s vs 715s，Linux 噪声内），风暴只影响 ~8 个方法，
  **kadx 相对上游 +270s 的主因仍未定位**——待 JFR 实测剖析。
- 本机注意：无 GNU `timeout`（用后台+kill 或 perl alarm 代替）；16GB 内存紧张，
  GUI `MaxRAMPercentage=70` 是用户内存抱怨的直接相关项。
- 本轮分工：pi 负责调研/实现/测试，ZCode（审查方）负责计划、review、红线把关。
  调研产物放 `/tmp/kadx-perf/`，报告写入仓库根 `INVESTIGATION_REPORT.md`（先不提交）。

## 会话 2 调研结论（报告全文见 INVESTIGATION_REPORT.md，已通过审查）

1. **默认 JVM 配置 = GC 死亡螺旋**：`MaxRAMPercentage=70` + `ParallelGCThreads=3`（继承自上游）
   在微信负载下堆打满 99.99%、8 次 Full GC（最长 164s）、STW 占墙钟 62%。前 30% 反编译只要 97s
   （3100 类/s）——内存充足时 kadx 一点不慢。
2. **单点最大热点**：`NameGen.addNamesUsedInClass` 每方法拷贝全 APK 顶层包名集合，
   ~30% 计算 CPU + ~32% 分配（与上游逐行等价，属继承缺陷，修好两边同时受益）。
3. **`-j` 语义被协程迁移破坏**：`Dispatchers.Default` 并行度 = 核数，`-j 20` 实测只跑 10 worker
   （commit 99b1cda95 引入）。
4. **上游同机对照**：计算吞吐差仅 ~18%（前 50% 进度稳定 1.13-1.16×，60% 处反超），
   上游同样 Full GC 螺旋——「慢 70%」主要是内存阈值效应，非系统性算法退化。
5. **二次启动**：磁盘缓存是写穿设计（每类反编译即落盘），「关闭批量落盘」是空操作；
   `load()`（dex 解析+ClassNode 构建）完全无缓存才是二次启动的大头。

## 会话 2 实施计划（v2，基准样本已更换）

**基准样本改为**：`/Users/shakarover/Downloads/game-killer-v5.4.2-MOD3-gamekillerapp.com.apk`（68MB）。
微信 APK 在本机只允许 `--single-class` 抽查（a03/ra2/zy0 选 2-3 个），**禁止全量跑**（机器承受不了，
已发生过系统级内存耗尽）。

**Step 0（新增，正确性 bug 修复，Step 2 前置）**：`KadxZipParser` 三个入口方法
（getInputStream/getBytes/initFallbackParser）在 Java→Kotlin 迁移时丢失 `synchronized`
（上游 303/325/374 行均为 synchronized，已双方独立核实）→ 并行资源解码数据竞争，
随机产出 `Error decode xml` 占位内容、输出非确定（hash 三次全不同、failed 5/6/5 波动）。
修复 = 三处 `@Synchronized` + 改掉误导性注释；修完重测噪声底（-j 20 ×5 + -j 1 ×2）。

**等价性验证协议 v3（硬门槛）**：
- game-killer（68MB，14 dex，14,653 类，~22s/次）= **正确性门禁**：基线跑 ≥5 次取「噪声集」，
  改动后要求 `diff ⊆ noise set` 且 failed 稳定核心集（5 个类）不变。逐字节相同不可行
  （存在 ~0.04% 固有源码非确定性，-j 1 下也有 7/16,856 文件差异，属 pass 迭代顺序依赖）。
- 微信有界子集（`KadxArgs.classFilter` + 测试基建，不新增 CLI 参数）= **性能门禁**；
  微信 `--single-class` 抽查 = 跨样本等价验证；微信全量聚合测**暂缓**（本机已被微信全量搞崩过）。
- `./gradlew test` 全绿。

**Step 1 结论（已验证）**：game-killer 无法体现 P0/P1/P3 收益（顶层包仅 15 个、零 Full GC、
-j 4..40 墙钟平坦且 30% 运行噪声）——它只做门禁。`-j` 截断证据链已闭合
（-j 4/8/10/20/40 → worker 恒为 10；ActiveProcessorCount=20 → 20，P3 修复的现成验收手段）。

**Step 2（第一批实现，每项独立 commit）**：
- P1：删除 `-XX:ParallelGCThreads=3`（CLI 与 GUI launcher 同步），GC 线程数回归自动。
- P0：`NameGen` 不再每方法拷贝 `rootPkgs`（isUsed 双查；新名字绝不写入共享集合）。
- P3：`TaskExecutor` 每 stage 独立固定线程池恢复 `-j` 语义（阻塞型负载下
  limitedParallelism 无法超过 Dispatchers.Default 的真实线程数——审查方已自我修正）+
  Channel 分批喂任务避免物化十万级协程；保持结构化并发与 terminate() 语义。

**Step 2 结果（全部已合入 master 并通过审查）**：
- `4404d62b8` Step 0：KadxZipParser 三处 `@Synchronized` 恢复——正确性 bug 修复，
  resources 从每运行 1-7 个损坏文件变为**完全确定性**（11 对运行 diff=0，聚合 hash 一致）。
- `fd4b35023` P1：删 `-XX:ParallelGCThreads=3`——本机 GC 并行度 3→9（ergonomics）。
- `ae32b1430` P0：NameGen 不再每方法拷贝 rootPkgs——**实测 user CPU −15.1%**，
  输出与基线零重叠（微信 rootPkgs=7,033 项 × 939,022 方法 ≈ 66 亿次 HashSet 插入被消除）。
- `1a56edec1` P3：TaskExecutor 并行阶段恢复真实 `-j` 语义（独立定容 task-p 线程池 +
  有界 Channel 投递）——机制修复（-j 20 实测 task-p=20），本机 10 核墙钟中性，
  多核机器上兑现收益；附带消除 ~15 万协程物化 + 新增并行 terminate() 单元测试。
- 门禁协议 v3 全程执行：game-killer ×3（resources 逐字节同 / sources diff ⊆ 噪声集
  src-noise-52.txt / failed 稳定 5 类）+ `./gradlew test` 全绿（1147）+ 微信单类抽查等价。

**Step 3 结果**：
- `d64f120db` 注释修正（GC 线程 ergonomic 公式 8+(n-8)*5/8 → 10 核实为 9）。
- `32f7cd027` P5-A1：GUI 启动分段埋点（StartupTimer，JVM uptime 原点，
  load.begin/end + tree.begin/end + initial-view.done，纯日志零行为变更）。
- **P2 `--unload-after-save`：实现后实测否决，已回滚不提交**。数据：4 种组合
  （unload/deepUnload × game-killer/微信有界子集）均无内存收益（微信子集 −5%，噪声内）；
  deepUnload 峰值反而恶化（2.2→3.8GB，它是「重置待重编译」原语——结尾 load(clsData,true)
  重建指令图，不是释放原语）且 failed 5→2；unload() 真释放但峰值由类树 clsData
  （253k 类本体）与在途反编译窗口主导，封顶已产出物不动峰值。诚实结论：
  存活集的结构性大头在 dex 数据本身，卸载原语救不了；实现细节存档于
  STEP1_FINDINGS.md §13.1（~30 行，如需带 flag 备查可随时恢复）。

**会话 2 最终账目（6 commits，全部经审查方逐个 review）**：

| commit | 内容 | 实测效果 |
|---|---|---|
| 4404d62b8 | Step 0：KadxZipParser 三处 @Synchronized 恢复 | 正确性 bug 修复；resources 完全确定性 |
| fd4b35023 | P1：删 -XX:ParallelGCThreads=3 | GC 并行度 3→9；机制已证，端到端待复测 |
| ae32b1430 | P0：NameGen 不拷贝 rootPkgs | **user CPU −15.1%**，输出零重叠——唯一实锤提速 |
| 1a56edec1 | P3：TaskExecutor 真实 -j 并行 | 语义修复（task-p=20）；本机墙钟中性，多核兑现 |
| d64f120db | 注释修正 | 文档 |
| 32f7cd027 | P5-A1：GUI 启动分段埋点 | 纯日志，二次启动可归因 |

三大痛点的会话 2 结论：
① **慢**：主因是 GC 死亡螺旋（默认参数下 STW 占 62%）而非算法退化；P0 砍掉 ~1/4
   计算量与 ~1/3 分配压力，P1 缩短 GC 停顿，P3 修复并行度语义。端到端复测待机器空闲。
② **内存**：根因（分配速率+GC 参数）已修；结构性卸载经实测否决——存活集大头是
   dex 数据本体，属架构性成本。
③ **二次启动**：埋点已就位可归因；代码缓存是写穿设计、批量落盘是空操作（已证）；
   真正的解法（渐进式树/load 索引/load 并行化）在 backlog，按 ROI 排序待下轮。

**Backlog（本轮不做）**：A3 渐进式可交互类树；A2 load 索引持久化（高风险）；
`RootNode.loadClasses` 并行化（利好启动）；`DiskCodeCache.close()` drain 上限健壮性；
buildInputsHash 只含 mtime 不含 size 的正确性隐患；
**源码侧迭代顺序非确定性**（-j 1 顺序路径也有 ~0.07-0.21%/对 的 sources 差异，
疑为某 pass 的 identity-hash/HashSet 迭代顺序依赖，独立工作项）。

**红线不变**：不回退优雅降级四项修复；不重命名；不动 NOTICE；测试全绿。
红线 1 调整为：game-killer 全量反编译失败方法数 = 基线值（预期 0）且输出逐字节等价。

## 红线（违反即打回）

1. **零失败方法必须保持**：优化后微信全量反编译失败方法必须仍为 0。
   验证命令：`kadx-cli/build/install/kadx/bin/kadx -j 20 -d <out> --show-bad-code <apk>`
   然后 `grep -rho "Method not decompiled" <out>/sources | wc -l` 必须为 0。
2. **不要回退任何优雅降级**：`TypeUpdate.apply` 预算耗尽 REJECT、
   `InsnRemover.removeSsaVar` 解绑、`BlockProcessor` 不可达块移除、
   `SwitchRegionMaker.appendBreakContainer` 类型感知追加——这些是已验证的修复。
3. **不要做任何重命名**（jadx→kadx 已完成），不要动 `NOTICE`。
4. 改动前后跑 `./gradlew test` 必须全绿。
5. 大改动先在单类上验证：`--single-class <cls>` + 微信 APK（加载约 8-10 分钟，
   用 `timeout -k 30 900` 包住）。

---

## 任务 1（最高优先）：类型推断「重试风暴」——反编译变慢的主因

### 现象与证据
kadx 反编译微信比上游 1.5.6 慢 ~70%（715s vs 401s）。回归点明确：
`TypeUpdate.apply` 预算耗尽后现在返回 `REJECT`（优雅降级，保留），但
`FixTypesVisitor.tryPossibleTypes` 会继续尝试**下一个候选类型**——而每次
`typeUpdate.apply` 都新建 `TypeUpdateInfo`，预算 = `insnsCount × typeUpdatesLimitCount`
**全额重置**。实测（`--type-update-limit 100`，a03 类，2454 指令）：
updateSeq 恰好烧到 245401 = 2454×100+1，即**每个候选都烧满全部预算**。
候选数 K × 变量数 V × 全预算 = 重试风暴，这就是 +270s 的来源。

### 修复设计
1. `TypeUpdate`（kadx-core/.../typeinference/TypeUpdate.kt）增加：
   ```kotlin
   private val budgetExhaustedVars: MutableSet<SSAVar> = ConcurrentHashMap.newKeySet()
   fun isBudgetExhausted(ssaVar: SSAVar): Boolean = ssaVar in budgetExhaustedVars
   ```
   （TypeUpdate 是 per-RootNode 单例，多类并行处理会并发访问，必须并发集合。）
2. `apply()` 的 `JadxOverflowException` catch 块（现为返回 REJECT）中追加：
   `budgetExhaustedVars.add(ssaVar)`。
3. `FixTypesVisitor.tryPossibleTypes`：候选循环内、每次 `typeUpdate.apply` 前，
   `if (typeUpdate.isBudgetExhausted(ssaVar)) return false`（首个候选烧穿后不再
   尝试后续候选）。
4. `FixTypesVisitor.deduceType`：开头同样早退（避免 tryWiderObjects 继续烧）。
5. 检查 `tryWiderObjects` 内部是否也调用 apply——同样加守卫。

### 验收
- 微信全量 walltime 从 ~715s 降到 **≤550s**；
- 失败方法仍为 0（抽查 `a03`、`ra2`、`zy0` 单类输出，`Type inference failed for`
  警告允许存在）；
- `./gradlew test` 全绿（重点：TestMoveInline、TestConditions*）。

---

## 任务 2：内存画像 + 快速止血

### 现状
253,250 类 / 939,022 方法 / 47.6M 指令常驻内存。GUI 默认 `-XX:MaxRAMPercentage=70`。
无测量数据，先画像再动手。

### 步骤
1. CLI 全量反编译运行中（load 完成后、decompile 50% 时、save 前）三次采样：
   ```bash
   jcmd <pid> GC.class_histogram > /tmp路径/histo-<phase>.txt
   ```
   （注意 jcmd 需对 java 子进程执行，`pgrep -P <timeout的pid>` 找真实 pid。）
2. 取 top-10 类按实例内存排序，识别大头（预期：InsnNode/RegisterArg/AttrList/
   String(domNative)/SSAVar）。
3. 依据画像实施**一个**快速止血项（候选，按画像结果择优）：
   - a) `MethodNode` 标记 REMOVE/卸载后，其 `instructions` 列表清空（保留占位）；
   - b) `AttrList` 中的调试属性在 save 后批量剥离；
   - c) 若 String 大头：类名/描述符 intern 或共享。
   每项都要先跑内存对比（同相位两次 histogram 对比），有效才保留。

### 验收
- 报告画像数据（前后对比表）+ 所选止血项的效果数据；
- 1034 测试全绿；微信 0 失败保持。

---

## 任务 3：第二次启动慢——磁盘缓存命中率 ~0%

### 现象与机制
GUI 日志：`Found 7 classes in disk cache, time: 39ms`——253k 类只命中 7 个。
机制（DiskCodeCache.kt）：缓存按类粒度 `add()` 写入（GUI 打开某类→反编译→add），
version = `DATA_FORMAT_VERSION:kadx.version:argsHash:inputsHash`。
根因分析：用户上次会话关闭时 `closeAll` NPE（已修复于 4b18b3494）中断保存——
但**惰性缓存设计下**用户没打开过的类本来就没有缓存。

### 任务
1. 实测确认：修复后正常打开→关闭→再启动，记录命中数曲线（开 10 个类 vs 50 个类）。
2. 提出并实现「关闭时批量保存」：`MainWindow.closeAll` 在 dispose 面板前，
   对所有**已反编译**（codeCache 有内存副本）的类批量 `add` 落盘，避免只存
   打开过的标签。注意用后台线程 + 进度提示，不要阻塞关闭。
3. 度量：二次启动从进程启动到「树可交互」的分段耗时（load/loadUI/tree），
   优化前后对比。

### 验收
- 打开 ≥20 个类后关闭，二次启动磁盘缓存命中数 ≥20；
- 二次启动可交互时间有可测改善（数据入报告）；
- 关闭时无 NPE/挂起（4b18b3494 的修复保持）。

---

## 协作流程

- pi：实现 → 自测（含红线验证）→ commit（`perf(scope): ...`）→ 终端输出 `TASK-N DONE`。
- 审查方：review diff → 跑微信全量+套件 → 通过则合入报告，不通过打回并注明原因。
- 有好点子（任何能让大 APK 更快/更省的）：先在本文档追加「提案」小节，经审查方
  确认无红线冲突后实施。
