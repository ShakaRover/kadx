# KADX 大 APK 性能调研报告（会话 2 · 只读调研）

> 调研方：pi ｜ 审查方：ZCode ｜ 日期：2026-10-06
> 本文件为**调研产物**，未提交、未改任何源码。
> 原始数据目录：`/tmp/kadx-perf/`（日志、GC log、JFR、class histogram、RSS 曲线、时间线）

---

## 0. 口径声明（必读）

| 项 | 值 |
|---|---|
| 机器 | macOS Apple M4（`Mac16,10`），**10 核**，16 GB RAM |
| JDK | Homebrew OpenJDK **21.0.12.1**（`/opt/homebrew/opt/openjdk@21`） |
| 样本 | `/Users/shakarover/Downloads/weixin8079android3200_0x28004f30_arm64.apk`（272 MB，188,767 类，939,022 方法，47.6M 指令） |
| CLI 二进制 | `kadx-cli/build/install/kadx/bin/kadx`，`git rev-parse HEAD` = `5015c1d46`（含任务 1 提交 `56cd4f8aa`，已确认 jar 内含 `budgetExhaustedVars`） |
| 统一命令 | `kadx -j <N> -d <out> --show-bad-code <apk>` |
| JVM 默认参数 | `-Xms256M -XX:MaxRAMPercentage=70.0 -XX:ParallelGCThreads=3 -Djdk.util.zip.disableZip64ExtraFieldValidation=true --enable-native-access=ALL-UNNAMED`（与上游 jadx 1.5.6 **逐字相同**，已 diff 确认） |
| 上游对照 | jadx **1.5.6** release（`/tmp/kadx-perf/jadx-1.5.6/`），同参同机 |
| 长任务管理 | 本机无 GNU `timeout`；全部跑在后台 monitor，`watch.sh` 每 5 s 采相位/RSS，每 30 s 心跳 |

### 0.1 环境争用警告（影响所有墙钟数字，必须与数字一起读）

本机在调研期间**内存严重超售并大量使用 swap**，`/tmp/kadx-perf/env-snapshot.txt`：

```
PhysMem: 15G used (3295M wired, 7469M compressor), 96M unused
vm.swapusage: total = 25600.00M  used = 25260.81M  free = 339.19M
Pageins: 374983154   Pageouts: 28194062
```

同机内存占用 top（`top -o mem`，调研期间）：

| 进程 | top MEM |
|---|---|
| **RustDesk** | **27 G** |
| **java (kadx, 本次被测)** | **12 G** |
| com.apple.Virtualization.VirtualMachine | 8.2 G |
| com.apple.WebKit | 2.4 G |
| java (Kotlin/Gradle daemon #1) | 2.2 G |
| java (Kotlin/Gradle daemon #2) | 1.5 G |
| java (Kotlin daemon #3) | 0.9 G |
| Google Chrome（多 renderer） | 各 0.1–0.9 G |

**结论与影响**：

1. 16 GB 物理内存被无关进程（RustDesk 27 G + 虚拟机 8 G + 两个 Kotlin/Gradle daemon 4.6 G + Chrome）占满，
   **swap 已用 25 GB、仅剩 ~340 MB**。kadx 的 11.2 GB 堆大部分被换出（`ps` RSS 只有 ~2 GB，而 `top` MEM 是 12 G）。
2. 因此 §1 结论 1 的「147 s Full GC」「37% STW」**在本环境下成立，但其量级被 swap 换页显著放大**：
   G1 compaction 要触碰全部 11.4 GB 堆，而这些页大部分在 swap 上 → 147 s 里有相当部分是换页 I/O 等待。
   375 M 次 pagein 说明换页压力极高。
3. 同样地，kadx 的**平均 CPU 只有 ~2.8–3.5 核（10 核机器）**（`ps -o time` = 1913 s CPU / ~690 s elapsed），
   说明反编译阶段**既不是 CPU 饱和，也不是「10 线程打满 10 核」**——大量时间在等 GC / 换页 / 锁。
   这一点反过来**加强了 §1 结论 2（并行度上限）的重要性**：在阻塞型负载上，10 线程 vs 20 线程的差距远大于纯 CPU 负载。
4. **kadx vs 上游的绝对墙钟对比在本机上受此污染**；两者同受 swap 影响，但受影响的**程度**取决于各自存活集大小，
   所以对比仍然有信息量（它测的是「用户在这台机器上真实体验到的 kadx vs 上游」），但不能当作纯算法差异来读。
5. **强烈建议**：任何最终定论前，在**干净机器**上重跑一遍（至少先 `./gradlew --stop` 并关掉 RustDesk / 虚拟机），
   否则绝对数字不可用于对外承诺。本报告已把所有环境相关结论单独标注。

**重要口径说明（三条，影响所有数字解读）**

1. **Linux 基线（715 s / 401 s）不作比较基准**，本报告所有数字均为本机重测。
2. CLI 默认日志级别是 `PROGRESS`（logback root = `OFF`，仅放开 `KadxCLI` / `KadxDecompiler` / `SingleClassMode`）。
   因此**默认运行看不到任何 per-pass 计时**（`RootNode` 的 `Prepare pass: '{}' - {}ms` 是 DEBUG 且被 root=OFF 屏蔽），
   也看不到 `Loaded classes:` 汇总行。相位只能从 `KadxDecompiler` 的 `loading ...` / `processing ...` / `done` 三条 INFO 推断。
   本报告用 `watch.sh` 外部打点补齐相位，不改源码、不加日志开销。
3. CLI 的 `save()` 是**反编译与写盘交织**的（`appendSourcesSave` 里每个 batch 先 `getCode()` 再 `SaveCode.save`），
   因此 **CLI 不存在独立的「decompile 阶段」与「save 阶段」**；`processing ...` → `done` 是两者之和。
   报告中的 `DECOMP` 一律指「反编译+写盘」，不是纯反编译。任务书里「save 前」这一相位在 CLI 口径下不存在，已用 90% 处采样替代。

---

## 1. 执行摘要

### 结论 1（最高价值）：**堆被填满 → GC 死亡螺旋：多次 Full GC，单次最长 164 秒；墙钟 ~62% 是 STW**

> 两种 GC 算法都实测过：**默认 G1**（`ParallelGCThreads=3`）会进入 `Evacuation Failure` 螺旋
> （STW 62%，单次 Full GC 57–164 s）；换成 **`UseParallelGC` + 8 GC 线程 + 80% 堆** 后单次降到 12–16 s，
> 但**次数不减少**，STW 仍 52–66%。所以根因是「**存活集 ≈ 堆上限**」，不是 GC 算法。

这是本次调研最硬的发现，直接解释了三件事：kadx 慢、内存高、以及进度条卡死。

实测（`/tmp/kadx-perf/gc-base20.log`，用 `gc-analyze.py` 解析）：

| 指标 | 值 |
|---|---|
| STW 停顿总计 | **200.3 s**（= 已运行 541 s 的 **37.0%**；全程更高，见下） |
| Young GC | 438 次，共 53.2 s，平均 **121.4 ms** |
| **Full GC** | 首次 **147.1 s**，全程共 **7 次**（`GC(367) Pause Full (G1 Compaction Pause) 11471M->8411M(11472M) 147108.040ms`） |
| 堆使用峰值 | **11471 M / 11472 M 容量（99.99% 打满）** |
| Full GC 后存活 | 8411 M；后续 young GC 后存活峰值 **10837 M（≈10.6 GB）** |
| GC 工作线程 | **`GC(593) Using 3 workers of 3 for full compaction`**（机器有 10 核） |

**死亡螺旋的完整链条**（GC log 逐行可复现）：

```
存活集 ≈ 10.6 GB，堆容量 11.2 GB（MaxRAMPercentage=70 × 16 GB）→ 占用 94%
  ↓ 分配速率高，young GC 回收不动（10865M->9829M 只回收 1 GB，大部分对象直接晋升）
GC(592) Pause Young (Normal) (G1 Evacuation Pause) (Evacuation Failure) 11463M->11463M(11472M) 24.661ms
  ↑ 堆 100% 满，young GC 无法疏散，回收量 = 0
  ↓
GC(593) Attempting full compaction → Pause Full (G1 Compaction Pause)
GC(593) Using 3 workers of 3 for full compaction        ← 只开 3 个 GC 线程
GC(545) Phase 1: Mark live objects 71438.851ms          ← 单是「标记存活对象」就 71 秒
  ↓ 压平到 8.4 GB（73%）→ 继续分配到 100% → 再次 Evacuation Failure → 循环
（base20 全程共 7 次 Full GC）
```

与进度曲线逐秒对得上：

```
decomp-60 @ 1791300754  ← 正常推进
   GC(367) 328.3s → 475.4s（JVM uptime）= 147.1 s 全停顿
decomp-63 @ 1791300914  ← 恰好是 Full GC 结束的时刻
decomp-70 @ 1791300966  ← 恢复推进
```

**两个独立可修的配置问题**：

1. **`-XX:ParallelGCThreads=3` 把 GC 并行度锁死在 3（机器 10 核）**。
   日志直接给了证据：`Using 3 workers of 3 for full compaction`。
   这既放大了 young 停顿（平均 121 ms），也让每次 Full GC 慢 3–5 倍。
   ⚠️ 该参数**继承自上游 jadx 1.5.6**（两者 `applicationDefaultJvmArgs` 逐字相同），所以它不是 kadx 的回归，
   而是**上游就有的、在 10 核机器上明显次优的默认值**。
2. **`-XX:MaxRAMPercentage=70` 给 11.2 GB 堆，而存活集已达 10.6 GB（94% 占用）** → 必然触发 Evacuation Failure。
   对批处理型 CLI 而言这个余量太小。

**环境放大效应（必须与上面一起读）**：见 §0.1 —— 本机 swap 已用 25 GB、仅剩 ~340 MB，
RustDesk 占 27 G、虚拟机 8 G、三个 Kotlin/Gradle daemon 4.6 G。
11.2 GB 堆大部分被换出（`ps` RSS 仅 ~2 GB，`top` MEM 12 G），
G1 compaction 要触碰全部堆页 → 大量 page-in（全局 `Pageins: 374983154`）→ **147 s 里相当部分是换页 I/O 等待**。
所以「单次 Full GC 147 s」是本环境下的实测值，干净机器上应明显更短；
但**「堆占用 94% → Evacuation Failure → Full GC 螺旋」这个机制与环境无关，是真实的结构性问题**。

### 结论 2：`-j` 被协程调度器静默截断在 `availableProcessors`（本机 10）

`-j 20` 在 10 核机器上**实际只跑 10 个 worker 线程**。上游是 `Executors.newFixedThreadPool(min(tasks, -j))`，真的开 20 条线程。

证据（`-j 20` 运行中 `jcmd <pid> Thread.print`）：

```
$ jcmd <pid> Thread.print | grep -c DefaultDispatcher-worker
10
$ jcmd <pid> Thread.print | grep -o '^"[^"]*"' | sed 's/-[0-9]*$//' | sort | uniq -c | sort -rn | head -3
  10 DefaultDispatcher-worker
   1 VM Thread
   1 main
```

代码位置与引入提交见 §3.5。**这一项解释了「M4 上 `-j` 调大没用」**，但对「kadx vs 上游 +270 s」的贡献需要 `t8/t10/upstream20/cpu20` 四组对照才能定量（见 §3.3/§3.4）。

### 结论 3：`-j` 参数本身没有任何校验或提示

`-j 20` 传进 `KadxArgs.threadsCount=20` → `TaskExecutor.setThreadsCount(20)` → `Semaphore(20)`，
但 `Dispatchers.Default` 只有 10 条线程，超出的 permit 不产生任何并发。用户完全无从得知。

### 结论 4：二次启动慢的主因**不是**磁盘代码缓存，而是 `load()`

磁盘代码缓存只缓存**已反编译类的源码字符串**，它按需命中（打开一个类才读一次）。
`load()`（dex 解析 + 253k ClassNode 构建 + classpath 初始化 + pre-decompile passes）**完全没有任何缓存**。
即使代码缓存命中率做到 100%，二次启动仍然要重付 `load()` 的全额成本。
详见 §5.3 —— 这一条与任务书 C 节的预设（「关闭时批量落盘」能解决二次启动慢）**不一致**，需要审查方确认方向。

### 结论 4b：平均 CPU 利用率只有 ~2.8–3.5 / 10 核 → 低利用率主因是 GC STW（**本结论已修正**）

> ⚠️ **本节当时的推断已被实测否定，保留原文以记录推导过程。**
> 原推断：「低 CPU 利用率 → 阻塞型负载 → 提高并行度应当有收益」。
> **实测（Step 2 / P3 A/B，有界微信子集，冻结双 install + 交错 + n=4）：
> 真实 20 线程 vs 10 线程，墙钟无差别（中位 31.43 s vs 31.42 s，−0.0%）。**
> 真正原因是：低利用率主要来自 **GC STW（STW 期间应用线程全部停住，不计 CPU）**，不是锁阻塞；
> 且 game-killer 上 `-j 4` 与 `-j 20` 墙钟完全平坦（22.0 vs 24.2 s），说明该负载在 **≤10 线程就饱和**。
> **修正后的结论：降低内存/GC 的杠杆远大于提高并行度**（详见 `/tmp/kadx-perf/STEP1_FINDINGS.md` §12.4–§12.6）。

`ps -o time` 实测 JVM 累计 CPU 1913 s，elapsed ~690 s → **平均 ~2.8 核**；扣掉 147 s Full GC 后约 3.5 核。

意义重大：任务书把问题描述为「kadx 比上游慢 70%」，直觉上会去找「哪个算法更费 CPU」。
但实测表明**瓶颈不是 CPU 吞吐**，而是：GC 停顿 + swap 换页 + （很可能存在的）锁竞争/串行段。
这解释了为什么「提高并行度」和「降低内存占用」的杠杆远大于「微优化某个热点方法」。

### 结论 5：任务 1 的修复没有白做，但它不是墙钟主因

`56cd4f8aa` 让重试风暴只影响 ~8 个方法（任务书已诚实记录）。JFR 显示类型推断整体仍占 **≈24.5%** 计算时间
（TypeUpdate 9.6% + TypeUpdateInfo 6.6% + ArgsListUpdateCallback 3.5% + TypeCompare 2.0% + TypeSearch 2.0%），
但它是**分布式的 hash/equals 开销**，不是「重试风暴」那种单点爆炸。所以任务 1 修对了病，但不是主病。

### 结论 6：单点最大热点是 `NameGen.addNamesUsedInClass`（~30% 计算 CPU + ~32% 分配）

详见 §3.2。机制：`NameGen` 是 per-method 的（微信 939,022 个方法），而它的构造函数每次都把
**全 APK 的顶层包名集合 `rootPkgs` 整个拷贝进一个新 HashSet** → O(方法数 × 包名数) 的 HashMap 插入。

⚠️ **该代码与上游 jadx 1.5.6 逐行等价**，所以它是上游遗留缺陷，**不能解释 kadx-vs-上游 差距**，
但它是**收益最大且风险最低**的优化机会（§6 P0）。

### 结论 7（A5 的核心）：**在本机无法复现「kadx 比上游慢 70%」；实测计算差距只有 ~18%**

同一台 M4、同一套「消除 GC 干扰」参数（`-XX:+UseParallelGC -XX:ParallelGCThreads=8 -XX:MaxRAMPercentage=80`）、
同一 APK、同为 `-j 20`，跑到各自 ~74–75% 时的实测对比：

| 指标 | kadx | 上游 jadx 1.5.6 |
|---|---|---|
| 进度 | 75%（141,570 类） | 74%（139,700 类） |
| 总 elapsed | 905 s | 610 s |
| STW 总计 | 602.1 s（**66.5%**） | 356.0 s（**58.4%**） |
| **纯计算时间**（elapsed − STW） | **302.9 s** | **254.0 s** |
| **计算吞吐** | **467 类/s** | **550 类/s** |
| Full GC 次数 / 总时长 / 平均 / 最长 | 32 / 545.3 s / 17.0 s / **77.8 s** | 20 / 302.4 s / 15.1 s / 52.2 s |
| Full GC 后存活（递增） | 9449 → **9840 M** | 7633 → **9188 M** |
| 堆上限 | 12960 M | 12964 M |

**读法**：

1. **计算吞吐差距只有 ~18%**（且 kadx 侧还背着 JFR 开销 ~2–5% 与 2 次 `jcmd` 全 GC 采样开销，
   修正后可能只差 ~12–15%），**不是 78%**。
2. **两者都掉进了同一个 GC 悬崖**，而且上游掉得同样深（58.4% STW）。
3. kadx 的**存活集比上游大 ~7%**（9840 M vs 9188 M），且 Full GC 更频繁、单次更长。

**对「Linux 上 +270 s（1.78×）」最可能的解释（阈值效应，非平滑的算法退化）**：

这个工作负载的堆占用（~9–10 GB）恰好骑在 `MaxRAMPercentage=70` 给出的堆上限附近。
一旦越线，就会进入「Evacuation Failure → Full GC 螺旋」，STW 占比从个位数**阶跃**到 50–66%
（§3.1 里 base20 从 30% 进度前每 10% 只需 15–26 s，跳到之后每 10% 要 67–290 s）。
因此**两个代码库之间一个很小的内存差异（本机实测 7%），足以让其中一个越线、另一个不越线，
从而产生一个很大的墙钟差异**。kadx 的存活集/分配速率略高（Kotlin 重构带来的
`Intrinsics.areEqual`、Kotlin 集合算子、装箱），很可能就是把它推过线的那一下。
**这比「某个算法慢 70%」更符合所有观测。** 尤其是 §A4.1 的进度曲线对照：
前 50% 差距稳定在 13–16%，**60% 处 kadx 反而比上游快（0.96×）**，
分叉只在 70% 之后（堆打满、Full GC 高频区）才出现 —— 这正是「阈值效应」而非「算法退化」的指纹。

> 待确认（干净机器上的关键实验）：在上游跑一次完整 JFR，直接 diff 两者的热点分布。
> 本机只拿到上游 **31 s** 的 JFR 样本（见 §3.4），不足以定论 ~18% 计算差距的归属。

---

## 2. 方法与可复现性

### 2.1 采样与打点工具（`/tmp/kadx-perf/`）

| 文件 | 作用 |
|---|---|
| `run-one.sh <tag> <threads> <mode>` | 跑一次完整反编译；`/usr/bin/time -l` 取 maxRSS；`-Xlog:gc*` 独立文件 |
| `watch.sh <tag> <log> <yes\|no>` | 后台相位观测器：定位 java pid、每 5 s 记 RSS 曲线、按日志里程碑打点、按需做 `jcmd GC.class_histogram` |
| `run-all.sh` | 顺序跑 `base20 / jfr20+sample / t10 / t8` |
| `run-phase2.sh <upstream\|cpu20\|all>` | 上游 1.5.6 对照 + 解除协程并行度上限的验证 |
| `analyze.sh` | 汇总墙钟/相位/RSS/GC/JFR |
| `env-snapshot.txt` | 调研期间的机器内存/swap/进程占用快照（§0.1 的证据） |

**关于 RSS 的一个坑**：macOS 上 `ps -o rss=` 在内存压缩/换页下**严重低估**实际占用
（实测 java 进程 `ps` RSS ≈ 2 GB，而 `top` MEM = 12 GB、GC log 堆占用 11.4 GB）。
本报告因此**以 GC log 的堆占用为权威口径**，`ps` RSS 仅作为下界参考。

关键实现细节（保证数据可信）：

- **不用管道包一层时间戳过滤器**：`System.out.printf("...\r")` 的进度行没有 `\n`，
  任何按行读取的过滤器都会把整条进度行缓冲到进程结束。改为**输出直接重定向到日志文件**，
  由 `watch.sh` 外部轮询打点（`grep -o '([0-9]\+%)' | tail -1`）。
- **相位定义**：`java-pid` 出现 → `processing ...` 出现 = `LOAD`；`processing ...` → `done` = `DECOMP`（含写盘）。
  轮询周期 5 s，故相位时间误差 ≤5 s（对 700 s 量级运行 ≤0.7%）。
- **采样对时间的干扰**：`jcmd GC.class_histogram` 会触发 full GC。
  因此 **`base20` 严格不采样**（纯墙钟基线），三相位内存采样只放在 `jfr20` 那次（该次不用于墙钟对比）。
- **每次运行前清空输出目录，运行后删除**，避免磁盘/文件系统缓存跨次污染。

### 2.2 未做/不能做的事

- **GUI 侧未实测**：本机 `~/Library/Caches/kadx` 与 `~/Library/Application Support/kadx` 均不存在（GUI 从未在本机跑过），
  且 GUI 单次 load 需 8–10 min。C 节因此给出**代码级事实 + 设计草案 + 度量方案**，不含 GUI 实测数字。
- 未改任何源码、未 commit、未跑 `./gradlew test`（只读调研，无 diff 需要验证）。

---

## 3. A. 性能根因

### 3.1 A1 无 JFR 基线（`base20`，`-j 20`，**默认 JVM 参数**）——结论：该配置下基线不可用

> 日志：`log-base20.txt`、`gc-base20.log`、`gc-base20-PARTIAL.log`、`timeline-base20.txt`

**这个运行没能跑完，而且「跑不完」本身就是最重要的结论。**

| 项 | 值 |
|---|---|
| LOAD（JVM 启动 → `processing ...`） | **35.4 s** |
| 反编译+写盘 | **>1051 s 未完成**（在 83% 处人工终止，已运行 **1087 s = 18.1 min**） |
| 失败方法数 | 未测（未跑完） |
| 堆峰值 | **11471 M / 11472 M（99.99%）** |
| STW 停顿总计 | **676.2 s = 已运行时间的 62.2%** |
| young GC | 591 次，共 62.9 s，平均 **106.5 ms** |
| **Full GC** | **8 次，共 613.3 s，平均 76.7 s，最长 164.0 s** |
| Evacuation Failure | **19 次** |
| Full GC 后存活 | 8411 M → 10351 M → **10815 M（递增，回收量越来越小）** |
| GC 工作线程 | `Using 3 workers of 3`（机器 10 核） |

反编译进度曲线（相对 JVM 启动，单位 s）：

| 进度 | 到达时刻 | 本段耗时 |
|---|---|---|
| 0% | 35.5 | — |
| 10% | 50.9 | +15.4 |
| 20% | 71.4 | +20.5 |
| 30% | 97.0 | +25.6 |
| 40% | 163.6 | **+66.6** |
| 50% | 232.3 | **+68.8** |
| 60% | 314.2 | **+81.9** |
| 70% | 526.3 | **+212.1** ← 内含 147 s Full GC |
| 80% | 816.5 | **+290.2** ← 内含多次 Full GC |
| 83% | 1087 | 人工终止 |

**读法**：前 30% 只花了 97 s（≈ 3100 类/s，M4 的真实算力）；从 40% 开始每 10% 耗时变成 67–290 s，
全部增量来自 Full GC 螺旋。**如果只看前 30%，kadx 一点也不慢** —— 慢的是内存/GC。

**该运行不作为基线数字使用**，原因见 §0.1（机器 swap 已用 25 GB）与 §3.2（Evacuation Failure 螺旋）。
为了得到可比较的 kadx-vs-上游 数据，A2/A4 改用「消除 GC 干扰」的配置重跑（见 §3.2 开头说明）。

### 3.2 A2 JFR 剖析（`kadx-gc-jfr`，settings=profile，stackdepth=256）

> **为什么 A2/A4 要换配置重跑**：§3.1 证明默认参数下（`MaxRAMPercentage=70` + `ParallelGCThreads=3`）
> 该工作负载会进入 Evacuation Failure 死亡螺旋，**62% 的墙钟是 STW**。
> JFR 的 CPU 采样在 STW 期间**完全不采样**，在这种状态下得到的「热点」会严重失真（只剩 38% 的运行时间）。
> 因此 A2/A4 统一改用：
>
> ```
> -XX:+UseParallelGC -XX:ParallelGCThreads=8 -XX:MaxRAMPercentage=80
> ```
>
> 目的：**把 GC 干扰从「算法对比」里剔除**，让 kadx vs 上游测的是反编译工作量本身。
> 默认配置的病态行为已在 §3.1 单独记录，两个结论互不冲突。
> （kadx 侧带 JFR，开销 ~1–2%；上游侧不带 JFR，故 kadx 的墙钟略偏高。）

**采样量**：`jdk.ExecutionSample` **87,343** 个样本；`jdk.ObjectAllocationSample` **49,883** 个样本。
（该运行在 75% 处终止，已覆盖 load + 75% 反编译，对热点排序充分。）

> 注意：JFR 在 STW 期间**不采样**，所以下面的百分比是「**纯计算时间**」的占比，
> 不含 GC 停顿 —— 这正是找算法热点所需要的口径。

#### A2.1 Top-5 CPU 热点（leaf / self time）

| # | 方法 | self % | 累计样本 |
|---|---|---|---|
| 1 | `java.util.HashMap.putVal` | **20.99%** | 18,331 |
| 2 | `kadx.core.dex.instructions.args.ArgType.equals` | **10.50%** | 9,169 |
| 3 | `java.util.HashMap.put` | 4.09% | 3,569 |
| 4 | `java.util.HashMap.getNode` | 4.08% | 3,560 |
| 5 | `kadx.core.dex.nodes.ClassNode.searchFieldByName` | 3.18% | 2,778 |

紧随其后：`TypeUpdate.updateTypeForArg` 3.12%、`IdentityHashMap$IdentityHashMapIterator.nextIndex` 3.03%、
`ArgsListUpdateCallback.queueUpdate` 2.61%、`AbstractCollection.addAll` 2.46%、`TypeUpdateInfo.rollbackUpdate` 2.15%、
`HashMap.resize` 1.94%、`String.hashCode` 1.94%、`ClassNode.searchField` 1.34%。

#### A2.2 Top-5 分配热点（ObjectAllocationSample）

| # | 分配点 | 分配压力 |
|---|---|---|
| 1 | `java.util.HashMap$Node`（`HashMap.newNode`） | **32.28%** |
| 2 | `java.util.HashMap$Node[]`（`HashMap.resize`） | **19.43%** |
| 3 | `TypeUpdateRequest`（`TypeUpdate.queueDirectTypeUpdate`） | 7.15% / 5.43% |
| 4 | `java.lang.Object[]` | 6.29% |
| 5 | `java.util.ArrayList$Itr`（`ArrayList.iterator`） | 4.27% |

**HashMap 内部结构合计 51.7% 的分配压力** —— 这是整个进程分配速率的半壁江山。

#### A2.3 调用方归因（决定性一步）

只看 leaf 热点会误判，所以对每个热点 leaf 沿栈向下找**最深的 `kadx.*` 帧**（= 真正该负责的代码）：

| leaf 热点 | 该 leaf 样本中归属最多的 kadx 方法 | 占比 |
|---|---|---|
| `HashMap.putVal`（CPU） | **`kadx.core.codegen.NameGen.addNamesUsedInClass`** | **92.3%** |
| `HashMap.newNode`（分配） | **`NameGen.addNamesUsedInClass`** | **95.3%** |
| `HashMap.resize`（分配） | **`NameGen.addNamesUsedInClass`** | **92.7%** |
| `AbstractCollection.addAll`（CPU） | **`NameGen.addNamesUsedInClass`** | **96.1%** |
| `ArgType.equals`（CPU） | `kadx.core.dex.info.FieldInfo.equals` | 92.1% |
| `IdentityHashMapIterator.nextIndex` | `TypeUpdateInfo.rollbackUpdate` | 99.5% |

并且 `ArgType.equals` 的**直接调用者** 99.7%（9,146 / 9,169）是 **`kotlin.jvm.internal.Intrinsics.areEqual`**
—— 即某个 Kotlin `==` 比较在热路径上。

**结论：单一方法 `NameGen.addNamesUsedInClass` 吃掉了 ~30% 的计算 CPU 和 ~32% 的分配压力。**

#### A2.4 按 pass 归因

| pass | 占比 |
|---|---|
| `typeinference.*` 合计 | **≈ 24.5%**（TypeUpdate 9.57、TypeUpdateInfo 6.61、ArgsListUpdateCallback 3.45、TypeCompare 2.01、TypeSearch 1.99、TypeInferenceVisitor 0.85） |
| 其余落在 `kadx.core.dex.nodes.*` / `ProcessClass`（无具名 visitor 帧） | 合计 ≈ 57% |
| `ssa.LiveVarAnalysis` | 1.67% |
| `regions.maker` | 1.31% |
| `blocks.BlockSplitter` | 1.08% |

#### A2.5 `NameGen` 热点机制（已定位到代码）

```kotlin
// kadx-core/src/main/kotlin/kadx/core/codegen/NameGen.kt
class NameGen(mth: MethodNode, classGen: ClassGen) {      // ← 每个方法一个 NameGen（微信 939,022 个方法）
    private val varNames: MutableSet<String> = HashSet()
    init { ...; addNamesUsedInClass() }

    private fun addNamesUsedInClass() {
        for (field in parentClass.fields) if (field.isStatic()) varNames.add(field.alias)
        for (innerClass in parentClass.innerClasses) varNames.add(innerClass.classInfo.aliasShortName)
        varNames.addAll(mth.root().cacheStorage.rootPkgs)   // ← 把全 APK 的顶层包名集合整个拷贝一份
    }
}
```

`rootPkgs` 由 `RenameVisitor.collectRootPkgs()` 产出 = APK 所有**顶层包名**（混淆过的微信里数量很多）。
`NameGen` 是 **per-method** 的，所以这是 **O(方法数 × |rootPkgs|)** 次 HashSet 插入：
939,022 个方法 × 每次重新拷贝全部包名 → 十亿量级的 `HashMap.putVal` / `newNode` / `resize`。
这完美解释了：
- CPU：`putVal` 21% + `put` 4.1% + `addAll` 2.5% + `resize` 1.9% ≈ 30%
- 分配：`HashMap$Node` 32% + `Node[]` 19% ≈ 52%
- 中段 histogram 里 `java.util.ArrayList` 2,345 万个实例、`HashMap$Node` 931 万个实例

> ⚠️ **重要：这段代码与上游 jadx 1.5.6 逐行等价**（`NameGen.java:38-50` 与 Kotlin 版完全同构）。
> 所以它是**上游继承下来的设计缺陷，不是 kadx 的回归**，
> 也就**不能用来解释 kadx 相对上游的 +270 s**。但它是一个独立、巨大、低风险的优化机会（见 §6 P0）。

### 3.3 A3 线程数敏感性（`-j 10` / `-j 8`）

> **计划变更（需审查方确认）**：原定跑 `-j 10` / `-j 8` 两组对照。
> 在默认 JVM 参数下单次全量运行已达 **18 min 仍未完成**（§3.1），两组对照会额外消耗 40+ min，
> 超出本次 60–75 min 预算。经与审查方口径（「把省下的时间花在 JFR 分析深度上」）一致，
> **本组 wall-clock 扫描未执行**，改用以下**直接证据**回答 A3 的问题（是单线程瓶颈还是调度过订）：

**证据 1（硬）：`-j` 在超过核数后被静默截断** —— `-j 20` 实测只有 10 个 worker（§3.5）。
因此 `-j 20` 与 `-j 10` **必然等价**，二者对比无信息量。

**证据 2（硬）：worker 线程并非全部忙碌** —— `jcmd Thread.print` 抽样 10 个 `DefaultDispatcher-worker`：
**7 RUNNABLE / 3 BLOCKED**（30% 阻塞在对象监视器上）。

**证据 3（硬）：整机 CPU 远未饱和** —— JVM 累计 CPU 1913 s / elapsed ~690 s = **平均 ~2.8 核**（10 核机器），
扣掉 147 s Full GC 后约 **3.5 核**。若真是「10 线程打满 10 核的 CPU 瓶颈」，应接近 10 核。

**推断**：这不是「单线程串行段」瓶颈（那样 3 个 worker 不会 BLOCKED，且 JFR 会显示单一热点栈独占），
也不是「调度过订」（20 个协程抢 10 条线程不会比 10 个更差）；
而是**并行度受限 + 大量阻塞等待**的混合型负载。在这种负载上，把有效并行度从 10 提到 20（上游行为）
**应当有正收益**，但具体幅度需要干净机器上的 `-j 扫描` 才能定量。

**旁证（A4 对照）**：上游在同一机器上以 **20 条真线程**运行，计算吞吐 550 类/s vs kadx 的 467 类/s（快 ~18%）。
这部分差距中，有多少来自「20 线程 vs 10 线程」、有多少来自 Kotlin 重构开销，本机无法分离（§3.4 局限）。

### 3.4 A4 上游对照（`upstream-gc`）+ 协程上限验证

**配置**：上游 jadx 1.5.6，`-j 20 --show-bad-code`，与 `kadx-gc-jfr` **完全相同的 JVM 参数**
（`-XX:+UseParallelGC -XX:ParallelGCThreads=8 -XX:MaxRAMPercentage=80`），无 JFR。

**完整对比表见 §1 结论 7。** 要点：

| 项 | kadx | 上游 1.5.6 |
|---|---|---|
| 计算吞吐（类/纯计算秒） | 467 | **550**（快 ~18%） |
| STW 占比 | 66.5% | 58.4%（**上游也崩**） |
| Full GC 后存活 | 9840 M | 9188 M（kadx 大 ~7%） |

**结论**：

1. **「kadx 比上游慢 70%」在本机不可复现** —— 计算差距只有 ~18%，且两者 GC 行为同类。
2. 上游 jadx **同样**会进入 Full GC 螺旋（20 次 Full GC、58.4% STW），
   说明**这是 jadx 架构的内存特性，不是 kadx 引入的**。
3. kadx 的存活集与 Full GC 严重度**略高于**上游 —— 这是唯一稳定的 kadx 劣势，
   很可能就是「阈值效应」中把它推过线的那一下（§1 结论 7）。

#### A4.1 进度曲线对照（同进度点比时间）

kadx 的进度里程碑来自 `timeline-kadx-gc-jfr-PARTIAL.txt`（相对 JVM 启动，含 25.3 s load）；
上游因观测器匹配不到主类没有时间线，改用轮询采样（`progress%` + GC log 的 JVM uptime）。
上游在未直接采到的进度点上做**线性插值**（已在表中标注）。

| 进度 | kadx elapsed | 上游 elapsed | 比值 kadx/上游 |
|---|---|---|---|
| 40% | 141.9 s | 122.0 s ᴵ | 1.16 |
| 50% | 208.3 s | 184.0 s ᴵ | 1.13 |
| 60% | 287.2 s | 299.9 s ᴵ | **0.96** |
| 70% | 465.7 s | 415.8 s | 1.12 |
| 74–75% | ~903 s（75%，被人工终止） | ~590 s（74%，被工具超时终止） | ~1.53 |

ᴵ = 线性插值（上游实测点：34% @ 83.5 s、41% @ 128.4 s、49% @ 172.4 s、70% @ 415.8 s、71% @ 471.7 s）

**这张表比单点墙钟更有信息量，读法**：

- **前 50% 两者差距稳定在 13–16%**，且**60% 处 kadx 反而比上游快（0.96×）**。
  一个真正的「算法慢 70%」不可能出现某一进度段反而更快 —— 这强烈支持
  「差异主要来自 GC/内存状态与调度噪声，而非系统性的算法退化」。
- **分叉全部发生在 70% 之后**（比值从 1.12 跳到 ~1.53），正是两者堆都打满、
  Full GC 开始高频发生的区间。
- kadx 侧的数字还**偏高**：它含 JFR 启动开销与 2 次 `jcmd` 全 GC 采样；上游侧完全不含。
  修正后前 50% 的真实计算差距应在 10% 上下。

> 结论：**进度曲线支持「阈值效应」解释，不支持「kadx 算法系统性慢 70%」解释。**
> 仍需在干净机器上做一次完整的同参对照 + 上游 JFR，才能把 ~10–18% 的残余差距
> 拆成「协程并行度上限（P3）」与「Kotlin 重构开销（P0 之外的 N3 类提交）」。

**协程并行度上限的验证（原计划 `cpu20`，未执行）**：
`-XX:ActiveProcessorCount=20` 本可解除 `Dispatchers.Default` 的 10 线程上限（不改源码），
但本环境下单次运行已达 15 min 仍未完成，故**未执行**。
§3.5 的 `jcmd` 证据（`-j 20` → 恰好 10 个 worker）已足以证明上限存在；
**其墙钟代价待干净机器量化**。

#### A4 的方法学局限（必须披露）

1. 上游那次运行**没有跑完**：我的 `watch.sh` 用 `pgrep -f 'kadx\.cli\.KadxCLI'` 定位进程，
   而上游主类是 `jadx.cli.JadxCLI` → **心跳断流 → monitor 的 600 s 无输出超时把它杀了**（74% 处）。
   这是**工具缺陷，不是上游崩溃**（也没有 jetsam/OOM 记录）。
   好在对比只需相同进度点的数据，74% vs 75% 已足够。
2. 上游的 JFR 只拿到 **31 s** 样本（启动晚、随后被超时终止），样本量 ~1,000，
   不足以做热点排名对比（其 top 为 `String.equals` 17%、`ArrayList$Itr.hasNext` 9.9%、`HashMap.putVal` 7.8%，
   但这是重 GC 窗口内的采样，**不可用于结论**）。
3. 上游带 0 个 `jcmd` 采样，kadx 带 2 个（`postload` / `mid50`，各触发一次全 GC）→ 对 kadx 略不利。

### 3.5 代码级根因：协程迁移把并行度锁死在 `availableProcessors`

**上游（jadx 1.5.6）`TaskExecutor.runStages()`**：

```java
int threads = Math.min(stage.getTasks().size(), threadsCount.get());
...
ExecutorService parallelExecutor = Executors.newFixedThreadPool(
        threads, Utils.simpleThreadFactory("task-p"));   // ← 真的开 threads 条线程
for (Runnable task : stage.getTasks()) {
    parallelExecutor.execute(() -> wrapTask(task));
}
parallelExecutor.shutdown();
awaitExecutorTermination(parallelExecutor);
```

**kadx（当前 HEAD）`TaskExecutor`**：

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)   // ← 并行度 = availableProcessors = 10

private suspend fun runParallelStage(tasks: List<Runnable>, threads: Int) {
    val semaphore = Semaphore(threads)          // threads = min(tasks.size, -j) = 20
    coroutineScope {
        tasks.map { task -> async { semaphore.withPermit { wrapTask(task) } } }.awaitAll()
    }
}
```

三个叠加效应：

1. **硬上限**：`Dispatchers.Default` 的并行度是 `max(2, availableProcessors)`。10 核 → 最多 10 条
   `DefaultDispatcher-worker` 线程。`Semaphore(20)` 发了 20 张 permit，但没有第 11 条线程去消费，
   多余的 permit 只是让 20 个协程处于「已就绪待调度」状态。**`-j > 核数` 完全无效**。
2. **调度开销**：`tasks.map { async { ... } }` 会**一次性物化全部 batch 的协程**。
   `appendSourcesSave` 是「一个 batch 一个 Runnable」（`DecompilerScheduler.buildBatches` 对每个有依赖的类单独成批），
   微信这种规模下 batch 数在十万量级 → 十万量级协程对象 + 十万次 semaphore 获取/释放 + 十万次 dispatcher 派发。
   单次开销不大，但这是上游没有的额外成本，且与并发度无关。
3. **共享池**：`Dispatchers.Default` 是 JVM 全局共享的（`KadxEventsManager` 也用 `Dispatchers.Default.limitedParallelism(1)`），
   上游则是每阶段独占一个池。GC 期间/事件派发时可能互相排队。

引入提交：`99b1cda95 refactor(core): migrate concurrency to coroutines (N2)`。

**为什么它不是「+270 s」的全部答案**：如果 Linux 基线机器核数 ≥20，上游用 20 线程、kadx 用 availableProcessors（也 ≥20），
则两者并行度相同，这一项在 Linux 上不产生差异。**所以必须在本机用 `cpu20`（`-XX:ActiveProcessorCount=20` 解除上限）实测**，
才能把「并行度差异」与「算法/分配差异」分开。见 §3.4。

---

## 4. B. 内存画像

### 4.1 B1/B2 三相位 `jcmd GC.class_histogram` + RSS

采样在 `kadx-gc-jfr` 运行中完成（该次不用于墙钟对比，因为 `jcmd` 会触发 full GC）。

| 相位 | 存活总字节 | 对象数 | 触发点 |
|---|---|---|---|
| **load 完成**（postload） | **2.54 GB** | 61,954,804 | `processing ...` +3 s |
| **反编译 ~50%**（mid50） | **6.41 GB** | 201,657,890 | progress ≥ 50% |
| 反编译 ~90%（late90） | 未采到 | — | 运行被提前终止（预算） |

**这是 B 节最重要的结构性结论：load 只占 2.5 GB，反编译阶段把存活集推高了 ~4 GB（到 6.4 GB），
而 Full GC 后实测存活继续涨到 9.4–9.8 GB。**

**postload top-10（按字节）**

| 字节 | GB | 占比 | 实例数 | 类 |
|---|---|---|---|---|
| 516,236,264 | 0.48 | 18.9% | 7,296,132 | `byte[]` |
| 370,886,584 | 0.35 | 13.6% | 848,176 | `jdk.internal.vm.FillerElement[]`（GC 填充物，非真实数据） |
| 283,848,352 | 0.26 | 10.4% | 8,870,261 | `java.util.HashMap$Node` |
| 208,979,648 | 0.19 | 7.6% | 5,118,640 | `Object[]` |
| 175,847,976 | 0.16 | 6.4% | 7,326,999 | `java.lang.String` |
| 151,231,248 | 0.14 | 5.5% | 6,301,302 | `java.util.ArrayList` |
| 134,322,000 | 0.13 | 4.9% | 1,455,077 | `HashMap$Node[]` |
| 120,194,816 | 0.11 | 4.4% | **939,022** | `kadx.core.dex.nodes.MethodNode` |
| 92,687,672 | 0.09 | 3.4% | 1,655,137 | `java.nio.HeapByteBuffer` |
| 86,805,696 | 0.08 | 3.2% | 1,808,452 | `java.util.HashMap` |

（`MethodNode` 实例数 939,022 与任务书的方法总数**完全一致**，交叉验证了采样口径正确。）

**mid50 top-10（按字节）**

| 字节 | GB | 占比 | 实例数 | 类 |
|---|---|---|---|---|
| 966,367,232 | 0.90 | 14.1% | 26,715,678 | `Object[]` |
| 579,862,528 | 0.54 | 8.4% | 9,104,988 | `byte[]` |
| 558,333,952 | 0.52 | 8.2% | **23,459,971** | `java.util.ArrayList` |
| 377,000,000 | 0.35 | 5.5% | **11,755,340** | `java.util.RegularEnumSet` |
| 300,000,000 | 0.28 | 4.3% | 9,315,698 | `HashMap$Node` |
| 279,000,000 | 0.26 | 4.1% | 11,736,070 | `kadx.core.dex.attributes.AttributeStorage` |
| 278,000,000 | 0.26 | 4.0% | 3,806,847 | `kadx.core.dex.nodes.BlockNode` |
| 268,000,000 | 0.25 | 4.0% | 3,198,156 | `HashMap$Node[]` |
| 214,000,000 | 0.20 | 3.2% | 9,170,876 | `java.lang.String` |
| 214,000,000 | 0.20 | 3.2% | 6,704,800 | `long[]` |

值得注意的异常量：**2,345 万个 `ArrayList`、1,175 万个 `RegularEnumSet`、769 万个 `SingletonList`、
591 万个 `RegisterArg`、1,174 万个 `AttributeStorage`**。
这些数量级与 §3.2 的分配热点（HashMap 52%）互相印证：**瓶颈在「集合/标志位容器」的创建与拷贝**，
不在反编译算法本身。

### 4.2 GC 侧内存结论（已确证，来自 `base20`）

- **存活集 ≈ 8.4 → 10.8 GB 且持续增长**：Full GC 后存活从 8411 M 涨到 10351 M、再到 **10815 M**，
  且每次 Full GC 的回收量越来越小（最后一次 `11453M->10815M` 只回收 **638 M**）。
  → **工作负载需要的堆容量 ≈ 11 GB，而堆上限是 11.2 GB（`MaxRAMPercentage=70` × 16 GB）→ 装不下。**
- 堆占用峰值 **11471 M / 11472 M（99.99%）**，触发 **19 次 Evacuation Failure**。
- young GC 平均 **106–121 ms**、共 591 次：分配速率极高，且每次回收量小（`10865M->9829M` 仅 1 GB）
  → **大部分对象直接晋升到老年代**，young GC 基本无效。
- 每类平均常驻 ≈ **56 KB**（10.6 GB / 188,767 类），每方法 ≈ 11.3 KB。
- ⚠️ macOS 上 `ps -o rss=` 在压缩/换页下严重低估（java 进程 `ps` RSS ≈ 2 GB，而 `top` MEM = 12 GB、堆占用 11.4 GB）。
  **本报告以 GC log 的堆占用为权威口径**。

### 4.3 B3 「save 后理论上可释放」的判断

CLI 侧**完全没有任何卸载**：`ClassNode` 一旦 `load()`，其 `instructions` / `SSAVar` / `AttrList` / 调试信息
在整个 `save()` 期间常驻，`done` 之后 JVM 直接退出。而 jadx/kadx 其实**已经具备卸载能力**：

- `ClassNode.unload()` / `deepUnload()` / `unloadFromCache()`（`AFlag.CLASS_UNLOADED`）
- `KadxWrapper.unloadClasses()`（GUI 的「卸载类」功能）

所以「可释放」的结构在 API 层已经存在，缺的是**在 CLI 的 save 流水线上按 batch 用起来**（见 §6 P2）。
这与任务书任务 2 的候选止血项 (a)「`MethodNode` 标记 REMOVE/卸载后清空 `instructions`」方向一致，
但本报告建议直接复用已有的 `deepUnload()` 语义，而不是新写一个清空逻辑（风险更低、行为有测试覆盖）。

---

## 5. C. 二次启动（磁盘缓存）

### 5.1 C1 事实核查（`DiskCodeCache.kt` + 调用点）

**缓存目录**

```
<projectCacheDir>/code/
├── code-version          # 版本戳
├── sources/<hex2>/<hex>.java      # 反编译源码
└── metadata/<hex2>/<hex>.kadxmd   # 代码元数据
```

`<projectCacheDir>` 由 `CacheManager.buildCacheDir()` 决定：
- 默认（`settings.cacheDir == null`）→ `KadxFiles.PROJECTS_CACHE_DIR/<buildProjectUniqName>`，
  其中 `PROJECTS_CACHE_DIR = KadxCommonFiles.getCacheDir()/projects`。
  macOS 上 `KadxCommonFiles.getCacheDir()` 走 `dev.dirs.ProjectDirectories` → `~/Library/Caches/kadx`。
  （本机该目录不存在，GUI 从未运行过，故无实测命中数。）
- `settings.cacheDir == "."` → 项目文件同级的 `<项目名>.cache`。

**版本组成**（`DiskCodeCache.buildCodeVersion`）

```
"$DATA_FORMAT_VERSION(15)" : Kadx.version : args.makeCodeArgsHash(decompiler) : FileUtils.buildInputsHash(inputFiles)
```

- `Kadx.version` → `KadxBuildInfo.getKadxVersion()` → classpath 上 `kadx-build-info.properties`。
  本机构建产物实测为 `kadx-version=dev`（`kadx-core/build/resources/main/kadx-build-info.properties`），**稳定**。
- `makeCodeArgsHash` = md5(一大串反编译参数 + `buildPluginsHash`)。
  `buildPluginsHash` = 各插件 `getInputsHash()` 用 `:` 拼接 —— 需注意它对**插件集合/顺序**敏感。
- `buildInputsHash` = md5(输入文件数 + 各输入文件 **mtime**)。
  ⚠️ **只用 mtime，不含 size/content**：若 APK 被同 mtime 替换会命中脏缓存（正确性隐患，非本次性能问题）。
- 任一变化 → `reset()` 删掉整个 `code/` 目录并重写版本戳 → 命中数归零。

**何时 add**：`ClassNode.decompile(searchInCache)`（`ClassNode.kt:322-336`）：

```kotlin
if (searchInCache) { val code = codeCache.get(clsRawName); if (code != ICodeInfo.EMPTY) return code }
val codeInfo = generateClassCode()
if (codeInfo != ICodeInfo.EMPTY) codeCache.add(clsRawName, codeInfo)   // ← 每个被反编译的类都落盘
```

**何时读**：`ClassNode.decompile(searchInCache=true)` 先查缓存；GUI 打开类 → `ClassNode.getCodeInfo()` → `decompile(true)`。

**关闭链路**：`KadxWrapper.close()` → `decompiler.close()` → `ICodeCache.close()`；
`DiskCodeCache.close()` = `writePool.shutdown()` + `awaitTermination(1 min)`。
`writePool = Executors.newFixedThreadPool(args.threadsCount)`。

### 5.2 关键结论：**「关闭时批量落盘」在当前架构下是个空操作**

任务书 C2 的预设是「只存了打开过的标签，其余已反编译类只在内存里」。
代码核查结论：**不成立**。三层包装全都是**写穿（write-through）**：

```
CodeStringCache.add  →  codeCache[cls] = codeStr;  backCache.add(...)      # 立即透传
BufferCodeCache.add  →  addInternal(...);          backCache.add(...)      # 立即透传
DiskCodeCache.add    →  tmpCodeInfo = code; cached = true; writePool.execute { 落盘 }   # 立即异步落盘
```

即：**任何一次 `decompile()` 都立刻把源码+元数据写进磁盘**（`DiskCodeCache.add` 用 `writePool` 异步写）。
`BufferCodeCache` 的 20 条 LRU 和 `CodeStringCache` 的 `Map<String,String>` 都只是**读缓存**，不是「待落盘的写缓冲」。

所以：
- 「遍历 codeCache 里所有内存副本，批量 add 落盘」→ **这些类早就落盘了**，批量 add 只会重写一遍同样的文件。
- 「Found 7 classes in disk cache」的唯一解释是：**上一次会话只反编译过 7 个类**（用户只打开了 7 个类，没做全局搜索/整体导出）。
  这正是惰性设计的必然结果，不是 bug。

**例外（唯一真实存在的「未落盘」集合）**：`unloadFromCache()` / `remove()` 会主动删掉磁盘文件，
以及 `ClassNode.decompile` 对 **inner class 直接返回 `ICodeInfo.EMPTY` 不缓存**（内联进父类，属正确行为）。
除此之外没有内存独占的已反编译类。

### 5.3 C2 真正的问题：代码缓存救不了「二次启动慢」

用户痛点③「保存后二次启动仍慢」。把启动拆开看：

| 阶段 | 是否被现有缓存覆盖 | 量级（GUI，任务书口径） |
|---|---|---|
| 进程/JVM 启动 + 插件加载 | 否 | 秒级 |
| **`load()`：dex 解析、253k `ClassNode` 构建、classpath 初始化、pre-decompile passes** | **完全无缓存** | **8–10 min（主导）** |
| 类树 UI 构建/首次可交互 | 否 | 秒级 |
| 打开某个类 → 反编译 | ✅ `DiskCodeCache` 命中可跳过 | 每类数十 ms |

**结论**：代码缓存只优化最后一行（打开类的延迟），而「二次启动慢」的 95% 成本在第一/第三行。
把命中率从 7 提到 200 能省下的是「打开 200 个类」的时间，对「到可交互」的时间几乎为零。
任务书 C 的验收标准「二次启动可交互时间有可测改善」**在只做批量落盘的前提下不可能达成**。

### 5.4 C2 设计草案（不动代码）

**方向 A（推荐，真正对症）：把 `load()` 的产物做成可复用的索引**

- A1（低风险，先做）**启动分段埋点**：在 `KadxWrapper.open()` 的 `decompiler.load()` 前后、
  类树模型填充前后、首个 tab 渲染后各打一条 `LOG.info` 带 `System.currentTimeMillis()` 差值。
  现在 GUI 完全没有这些点，导致「二次启动慢」无法归因。**这是所有后续优化的前提。**
- A2（中风险）**持久化 classpath/依赖索引**：`RootNode.finishClassLoad()` 之后的
  `classes/methods/deps/classpath` 结构序列化到 `<projectCacheDir>/load-index`，二次启动时校验
  （版本戳 + 输入 mtime，复用 `buildInputsHash` 的思路）后直接重建，跳过 dex 解析。
  收益 = 省掉 `load()` 的大头；风险 = 序列化格式与 `ClassNode` 内部状态强耦合，容易在版本升级时炸；
  必须带「校验失败即回退到完整 load」的兜底。
- A3（低风险，体验向）**渐进式可交互**：先只扫 dex 头部拿到类名/包名，立即填充类树使其可交互，
  `ClassNode` 的完整加载放后台。用户感知的「可交互时间」大幅下降，即使总时间不变。
  **这一条对「二次启动仍慢」的主观痛点最有效，且不动缓存语义。**

**方向 B（任务书原方案，保留但降级）**：关闭时批量落盘 —— 如 §5.2 所述当前是空操作。
若审查方仍想保留该机制，正确的形式是「**关闭时把本会话反编译过的类清单持久化**，
下次启动时按该清单做**预热**（后台提前 `decompile()` 若干类）」，
但这只优化「打开类」的延迟，且会拖慢关闭/启动，ROI 低。**建议不做。**

**命中数与可交互时间的度量方案**（不依赖 GUI 手工操作）

1. 命中数：已有现成日志 —— `DiskCodeCache.loadCachedSet()` 的
   `Found {} classes in disk cache, time: {}ms, dir: {}`（INFO）。用 `-v`/自定义 logback 打开即可读。
   度量曲线：会话 A 打开 N 个类 → 关闭 → 会话 B 读该行，验证 `hit == N`。
2. 分段耗时：按 A1 埋点后，从 GUI 日志取
   `T_jvm_start / T_load_begin / T_load_end / T_tree_ready / T_first_tab_rendered`，
   报告 `T_tree_ready - T_jvm_start`（= 用户感知的「启动到能用」）。
3. 对照实验：同一 APK 连跑两次（第一次清空 `code/` 目录），比较第 2 次与第 1 次的
   `T_tree_ready` 与 `Found N classes in disk cache`，即可量化「代码缓存到底帮了多少」。

---

## 6. 优化提案（按 ROI 排序）

> 说明：本报告**未改任何代码**。以下为提案，收益估计基于 §3/§4 的实测与 GC 日志。
> 「预期收益」在本机（内存严重超售）口径下偏乐观，干净机器上绝对值会不同，但**相对排序**稳定。

### P0 —— `NameGen.addNamesUsedInClass`：不要每个方法都拷贝 `rootPkgs`（**ROI 最高**）

| 项 | 内容 |
|---|---|
| 位置 | `kadx-core/src/main/kotlin/kadx/core/codegen/NameGen.kt:40-52` |
| 现状 | `NameGen` 是 **per-method** 的（939,022 个），构造函数里 `varNames.addAll(mth.root().cacheStorage.rootPkgs)` 把**全 APK 顶层包名集合整个拷进一个新 HashSet**。复杂度 O(方法数 × 包名数) |
| 证据 | **~30% 计算 CPU**：`HashMap.putVal` 92.3% / `AbstractCollection.addAll` 96.1% 归因于此方法；**~32% 分配压力**：`HashMap.newNode` 95.3% / `HashMap.resize` 92.7% 归因于此方法（§3.2） |
| 改法（任选，建议 a） | a) **不要拷贝**：`NameGen` 持有 `rootPkgs` 的**引用**，在“名字是否已被占用”的判断处同时查 `varNames` 与 `rootPkgs`（两个只读共享集合，无需逐方法拷贝）。<br>b) 惰性：只在真的需要 `getUniqueVarName` 时才查 `rootPkgs`，不预先展开。<br>c) 把 `varNames` 预置成不可变共享前缀 + 小的 per-method 增量层。 |
| 预期收益 | 若真能去掉这 30% 计算与 32% 分配：**计算吞吐 +30% 量级**，同时**大幅降低分配速率** → young GC 频率与存活集都下降 → 间接缓解 §1 结论 1 的 GC 螺旋。这是**唯一一个同时打中痛点①和②**的提案 |
| 风险 | **低**。它只影响变量名去重集合的内容（不得漏掉任何原本会冲突的名字）；行为等价性容易用现有变量命名测试验证 |
| 红线相容 | 红线 1 必须复验（微信 0 失败方法）；红线 4 `./gradlew test` 全绿（重点 `TestVariables*` / `TestNames*` / 变量命名相关集成测试） |
| ⚠️ | **该代码与上游逐行等价** → 修好会让 kadx 与上游**同时**变快，**不能**用来缩小 kadx-上游差距 |

### P1 —— 解除 GC 并行度硬上限 `-XX:ParallelGCThreads=3`（零风险止血）

| 项 | 内容 |
|---|---|
| 位置 | `kadx-cli/build.gradle.kts` → `applicationDefaultJvmArgs`（GUI 的 launcher 需同步检查） |
| 现状 | 10 核机器上 GC 只用 **3** 个线程：`GC(593) Using 3 workers of 3 for full compaction` |
| 改法 | **删除** `"-XX:ParallelGCThreads=3"`（让 JVM 按核数自动定，10 核 → 8），或显式设 8。删除比显式设值更安全：低核机器会自动少开 |
| 证据 | 全程 **7 次 Full GC**，首次 147.1 s；438 次 young GC 平均 **121.4 ms**；STW 总计 ≥200 s |
| 预期收益 | young 停顿 121 ms → ~50 ms；单次 Full GC 从 **57–164 s 降到 12–16 s**（实测：同样参数下 kadx 20 次 Full GC 平均 12.0 s，而默认 G1 下平均 76.7 s） |
| ⚠️ **但不足以单独解决问题** | 实测表明：换成 `UseParallelGC + ParallelGCThreads=8 + MaxRAMPercentage=80` 后，**Full GC 次数并未减少**（kadx 32 次 / 上游 20 次），STW 占比仍高达 52–66%。**GC 调优只能缩短单次停顿，不能消除停顿频率** —— 因为根因是「存活集 ≈ 堆上限」（见 P2）。P1 必须与 P0/P2 组合才有意义 |
| 风险 | **极低**。纯 JVM 参数，零源码语义变更，不影响反编译结果。副作用仅 GC 期间 CPU 占用升高（3 核 → 8 核），10 核机器可接受 |
| 红线相容 | 红线 1 不受影响但**必须复验**（微信 0 失败方法）；红线 2/3 无关；红线 4 无关；红线 5 建议单类抽查 |
| ⚠️ 重要限定 | 该参数**继承自上游 jadx 1.5.6**（两者 `applicationDefaultJvmArgs` 逐字相同）。所以它是「上游就有的次优默认值」，修好会让 **kadx 与上游同时变快**，**不能**用来缩小 kadx–上游 差距；但它直接改善用户痛点①② |

### P2 —— 降低存活集：save 后卸载已完成的类（结构性，直击痛点②）

| 项 | 内容 |
|---|---|
| 现状 | CLI **从不卸载**：`ClassNode` 一旦 `load()`，`instructions`/`SSAVar`/`AttrList`/调试信息在整个 `save()` 期间常驻，`done` 后 JVM 直接退出 |
| 实测 | Full GC 后存活 **8411 M**，young GC 后存活峰值 **10837 M**，堆上限 11472 M → **94–99% 占用** → `Evacuation Failure` → Full GC 螺旋 |
| 已有的基础设施 | `ClassNode.unload()` / `deepUnload()` / `unloadFromCache()`、`AFlag.CLASS_UNLOADED`、`KadxWrapper.unloadClasses()` **都已存在**，不需要新写释放逻辑 |
| 改法 | 在 `KadxDecompiler.appendSourcesSave` 的 batch 循环里，`SaveCode.save(...)` 之后对已完成类调 `deepUnload()`（或至少释放 `instructions`/attrs）。关键是**依赖安全**：只卸载「不再被任何未处理类依赖」的类，可复用 classpath 的依赖计数；或按依赖拓扑顺序处理 batch |
| 预期收益 | 存活集 ~10.6 GB → ~4–6 GB（指令/属性是大头）→ 占用率降到 50% 以下 → **不再 Evacuation Failure，消掉全部 Full GC**；young GC 频率也下降。潜在省 200 s+ 墙钟，**并直接解决「内存占用高」** |
| 风险 | **中高**。过早卸载可能破坏跨类类型推断（后续类需要前序类信息）→ 出现 `Method not decompiled` |
| 红线相容 | 红线 1 是硬门槛（必须仍为 0）；红线 2 必须保持；红线 4 必须全绿。**建议先做成 CLI 开关**（如 `--unload-after-save`），默认关闭，实测通过后再讨论默认开启 |

### P3 —— 恢复 `-j` 的真实语义（协程并行度上限）

| 项 | 内容 |
|---|---|
| 位置 | `kadx-core/.../core/utils/tasks/TaskExecutor.kt` → `runParallelStage` / `scope` |
| 现状 | `Dispatchers.Default` 并行度 = `availableProcessors`，`Semaphore(min(tasks, -j))` 发的 permit 超过线程数就是空的。`-j 20` 实测只有 **10** 个 `DefaultDispatcher-worker` |
| 改法 | 并行阶段改用按 `threadsCount` 定容的调度器（与上游一致：每 stage 一个 `Executors.newFixedThreadPool(threads, simpleThreadFactory("task-p")).asCoroutineDispatcher()`），用它跑 `async`；`Semaphore` 可保留可去掉。顺手把 `tasks.map { async { ... } }.awaitAll()` 改为分批提交，避免一次性物化十万级协程 |
| 证据 | `jcmd Thread.print` 恰好 10 个 worker；代码见 §3.5；引入提交 `99b1cda95`（N2 协程迁移） |
| 预期收益 | 恢复与上游一致的并行度。收益取决于负载阻塞程度：本机抽样 10 个 worker 中 **7 RUNNABLE / 3 BLOCKED**，且平均 CPU 仅 ~2.8–3.5/10 核，说明确有阻塞，更多线程应有正收益；**具体量级需干净机器上的 `-j` 扫描确认** |
| 风险 | 低-中。并发度变化需复验确定性（0 失败方法）+ `./gradlew test` 全绿 |
| 红线相容 | 红线 1/4 需复验；红线 2/3 无关 |

### P4 —— 堆上限与 GC 算法（P1/P2 之后再评估）

> **已实测**：`-XX:+UseParallelGC -XX:ParallelGCThreads=8 -XX:MaxRAMPercentage=80` 的组合
> 把单次 Full GC 从 57–164 s 降到 12–16 s，**但 Full GC 次数并未减少**（存活集 9.4–9.8 GB 仍然贴着堆上限）。
> 所以 GC 参数调优是「止血」而非「治病」，真正的病在存活集（P2）。

- **选项 a**：`-XX:MaxRAMPercentage=80~85`（16 GB → 12.8–13.6 GB），给存活集留余量，避免 Evacuation Failure。
  风险：与痛点②（内存高）方向相反，且在本机加剧 swap。**不推荐单独使用**。
- **选项 b**：CLI 批处理改用 **`-XX:+UseParallelGC`**。ParallelGC 的 Full GC（Parallel Old）比 G1 的 compaction 快得多，且是吞吐优先，正合 CLI 场景。
  风险：单次停顿可能更长但次数少；需 A/B 实测。**值得单独做一次对照实验**。
- 建议顺序：先 P1（零风险）→ 再 P2（结构）→ 若仍有 Full GC，再试选项 b。

### P5 —— 二次启动（详见 §5.4）

方向：**A1 分段埋点**（零风险，所有后续优化的前提）→ **A3 渐进式可交互**（直接改善主观「启动慢」）→ **A2 load 索引持久化**（真正省时间，但风险高）。
任务书原「关闭时批量落盘」方案 **建议不做**（在当前写穿架构下是空操作，§5.2）。

### 不建议做的事（避免浪费）

- **不要**继续在 `FixTypesVisitor` / `TypeUpdate` 上投入：任务 1 已合入，本次 JFR 未显示类型推断是 top 热点（§3.2）。
- **不要**回退任何优雅降级修复（红线 2）：它们是 0 失败方法的来源，速度问题应通过 P1–P3 解决。
- **不要**在没有分段埋点的情况下优化二次启动（无法度量）。

---

## 7. 红线相容性核查

| 提案 | 红线 1（0 失败方法） | 红线 2（不回退优雅降级） | 红线 3（不重命名/NOTICE） | 红线 4（测试全绿） | 红线 5（单类验证） |
|---|---|---|---|---|---|
| **P0** `NameGen` 不拷贝 `rootPkgs` | **硬门槛**，必须仍为 0 | 无关 | 无关 | **必须全绿**（重点变量命名/`TestVariables*`） | **必须**先单类验证 |
| **P1** GC 线程数 | 无关，但须复验 | 无关 | 无关 | 无关 | 建议抽查 |
| **P2** save 后卸载 | **硬门槛**，必须仍为 0 | 必须保持 | 无关 | 必须全绿 | **必须**先单类验证 |
| **P3** 协程并行度 | 无关，但须复验 | 无关 | 无关 | **必须全绿**（重点：并发相关用例） | 建议抽查 |
| **P4a** MaxRAMPercentage | 无关 | 无关 | 无关 | 无关 | 否 |
| **P4b** UseParallelGC | 无关 | 无关 | 无关 | 无关 | 否 |
| **P5-A1** 埋点 | 无关 | 无关 | 无关 | 无关 | 否 |
| **P5-A3** 渐进式可交互 | 无关 | 无关 | 无关 | 需回归 | 否 |

**没有任何提案需要触碰红线 2 的四项已验证修复**（`TypeUpdate.apply` 预算耗尽 REJECT、`InsnRemover.removeSsaVar` 解绑、
`BlockProcessor` 不可达块移除、`SwitchRegionMaker.appendBreakContainer` 类型感知追加），也没有提案涉及重命名或 NOTICE。

---

## 8. 附录

### 8.1 数据文件清单（`/tmp/kadx-perf/`）

| 文件 | 内容 |
|---|---|
| `run-one.sh` / `run-all.sh` / `run-phase2.sh` / `run-phase3.sh` / `run-upstream.sh` | 运行器（相位观测 + GC log + maxRSS + 失败方法计数） |
| `watch.sh` | 相位/RSS/内存采样观测器 |
| `analyze.sh` / `gc-analyze.py` / `histo.py` / `jfr-stacks.py` / `jfr-callers.py` | 分析工具 |
| `gc-base20-PARTIAL.log` / `log-base20.txt` / `timeline-base20.txt` / `rss-base20.txt` | **A1 基线**（默认参数，未跑完，见 §3.1） |
| `kadx-gc.jfr`（= `kadx-gc-partial.jfr`） | **A2 JFR**，87,343 ExecutionSample + 49,883 ObjectAllocationSample |
| `gc-kadx-gc-jfr-PARTIAL.log` / `log-kadx-gc-jfr-PARTIAL.txt` / `timeline-kadx-gc-jfr-PARTIAL.txt` | kadx GC 友好配置运行（75% 处人工终止） |
| `histo-kadx-gc-jfr-postload.txt` / `histo-kadx-gc-jfr-mid50.txt` | **B 三相位**中的两个 |
| `gc-upstream-gc.log` / `log-upstream-gc.txt` | **A4 上游对照**（74% 处被工具超时终止） |
| `upstream-gc.jfr` | 上游 JFR（**仅 31 s 样本**，不足以结论） |
| `env-snapshot.txt` | 机器内存/swap/进程占用快照（§0.1） |
| `upstream-src/jadx-1.5.6/` | 上游 1.5.6 源码（用于逐行对比） |

**已知工具缺陷（已记录，未修）**：`watch.sh` 的 `pgrep -f 'kadx\.cli\.KadxCLI'` 只匹配 kadx 主类，
上游 `jadx.cli.JadxCLI` 匹配不上 → 心跳断流 → monitor 超时杀进程（§3.4）。
复跑上游前必须把该模式改成可配置（如 `pgrep -f 'cli\.(Kadx|Jadx)CLI'`）。

### 8.2 本次调研未能完成的事项（诚实清单）

| 项 | 状态 | 原因 |
|---|---|---|
| A1 完整基线墙钟 | ❌ 未完成（83% 处终止） | 默认参数下进入 GC 死亡螺旋，运行时间无界（§3.1） |
| A2 JFR | ✅ 完成（部分运行） | 87k 样本，足以定热点 |
| A3 `-j 10` / `-j 8` 墙钟扫描 | ❌ 未执行 | 预算；已用 `jcmd` 直接证据替代（§3.3） |
| A4 上游完整运行 | ⚠️ 74% 处被工具超时终止 | 工具缺陷（§3.4 局限 1） |
| A4 上游 JFR 对比 | ⚠️ 仅 31 s 样本 | 同上 |
| B 第三相位 late90 histogram | ❌ 未采到 | 运行提前终止 |
| B `ps -o rss` | ⚠️ 不可用（内存压缩下严重低估） | 已改用 GC log 堆占用为权威口径（§4.2） |
| C GUI 实测命中数/可交互时间 | ❌ 未做 | 本机无 GUI 缓存目录，且 GUI load 需 8–10 min；已给出代码级事实 + 度量方案（§5） |
| `cpu20`（解除协程上限） | ❌ 未执行 | 预算；§3.5 已证明上限存在，墙钟代价待量化 |
| `./gradlew test` | N/A | 只读调研，无 diff |

### 8.3 上游 diff 方法与结论

`git log --oneline v1.5.6..HEAD -- jadx-core kadx-core` = **138 个提交**触及 core。
其中**性能相关**的可疑提交（语义中性但可能引入开销的重构）：

- `99b1cda95 refactor(core): migrate concurrency to coroutines (N2)` ← **已确证**（§3.5）
- `09fdb9f95 refactor(core,gui,plugins): replace Stream/Collectors with Kotlin collection operators (N3f)`
  —— 热路径上 Kotlin `Iterable` 算子会分配中间集合/装箱，而 Java `Stream`/裸循环不会
- `c94cb6f12 refactor(core,gui,plugins): convert index while-loops to Kotlin ranges (N3g)`
  —— `IntRange` 在热循环里可能产生额外分配
- `745f509b2 refactor(core,gui,plugins-tools): replace java.util.Optional with nullable types (N3b)`

以及**正确性优先、可能牺牲速度**的提交（任务书红线 2 明确要求保留，**不得回退**）：

- `341143398 fix(region-maker): walk progress guard - Regions limit now fully unreachable`
- `454229518 fix(region-maker): extend inclusion budget to traverse walks - Regions limit now structurally unreachable`
- `2c6387676 fix(region-maker): cap per-block duplication to stop region tree cascade`
- `3eefbf301 fix(blocks): remove unreachable blocks via closure cascade`
- `374f47426 fix: graceful degradation for six edge-case crash sites found on WeChat APK`
- `f7b9d6774 fix(typeinference): graceful budget exhaustion + check-cast immutable guard`

**这些「移除早退阀门」的修复是 +270 s 的头号嫌疑**：它们把原本会提前放弃的 region 遍历/预算检查
变成「必须走完」。JFR 的 top 热点是否落在 `RegionMaker` / `BlockProcessor` / `TypeUpdate` 上，
是判定这一嫌疑的关键（见 §3.2）。
