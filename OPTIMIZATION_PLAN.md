# KADX 大 APK 性能优化任务书（pi 实施版）

> **协作协议**：本文件由审查方（ZCode agent）维护。pi 按任务顺序实施，完成后在终端报告
> `TASK-N DONE`，审查方 review diff、跑验证。**实施前必读下方「当前基线」与「红线」。**
> 工作分支：master（小步提交，每个任务一个 commit，消息格式 `perf(scope): ...`）。

## 当前基线（2026-10-06，master @ 374f47426+）

- 测试样本：`/home/shakarover/Downloads/wechat/weixin8079android3200_0x28004f30_arm64.apk`
  （285MB、18 dex、188,767 类、939,022 方法、47.6M 指令）
- CLI 全量反编译（`-j 20 --show-bad-code`）：
  - kadx（当前）：**~715s**，失败方法 **0**，错误注释 **0**（此前的 520/547 已全部修复，勿回退）
  - 上游 1.5.6 与 master：401-409s，失败方法 520、错误注释 547
- GUI 冷启动：加载 253,250 类；磁盘缓存命中 **7 个类**（接近零命中，见任务三）
- 全量测试：1034 个集成测试全绿（`./gradlew test`）

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
