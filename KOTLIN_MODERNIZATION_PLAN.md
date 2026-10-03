# Kotlin 现代化 + 协程化 计划（KOTLIN_MODERNIZATION_PLAN.md）

> 前置：机械式 Java→Kotlin 迁移已完成，**仓库零 `.java` 源文件**（2436 个 `.kt`），`./gradlew build` 全绿。
> 本阶段目标：**去 Java 味**（惯用 Kotlin）+ **引入协程**（把适合并发的代码改为 coroutines/Flow）。
> 与前一阶段不同：**本阶段改变行为**（线程模型、空安全、API 形态），必须比机械迁移更保守。

---

## 0. 治理与铁律

- **行为等价优先于风格**。每个批次必须 `./gradlew build` 绿 + 相关测试绿；UI 线程语义不得改变（该在 EDT 的仍在 EDT）。
- **协程化必须成对改造**：把「启动后台任务 + 回调/进度 + 取消」整体迁到 `CoroutineScope`/`Flow`，不要混用一半协程一半 `SwingWorker`。
- **保留 Java 互操作面**（外部契约，不可随意去 Java 味）：
  - `jadx.api.*` / `jadx.api.plugins.*`（第三方插件作者用 Java 实现/静态调用）——`@JvmStatic`/`@JvmField`/显式 `getX()` 保留。
  - `JadxAssertions`（`*Fixture.kt` 内嵌的 Java 源码 `import static ...JadxAssertions.assertThat`）。
  - `IntegrationTest`（ECJ 反射 + 测试基类）。
- **热点 pass 不追风格**：`jadx.core.dex.visitors.*`、SSA/regions 里的 `for` 循环保持命令式（SOP 规则 5），不改成 `.map/.filter`。
- **每批一个小目标、独立 commit**；Worker 不改本文件。

---

## 1. 阶段 N1：协程基础设施 + jadx-gui 后台/任务协程化

### 依赖
- `jadx-gui/build.gradle.kts` 增加 `org.jetbrains.kotlinx:kotlinx-coroutines-core` 与 `kotlinx-coroutines-swing`（Kotlin 2.3.10 对应版本）。
- 需要协程的其它模块按需加 `coroutines-core`（core 不用 swing）。
- 可选：`kotlinx-coroutines-rx3`（若需与现有 RxJava 互操作过渡）。

### 目标清单（按价值/风险排序）
| ID | 范围 | 现状 | 目标 |
|----|------|------|------|
| N1a | `jadx/gui/jobs/*`（BackgroundExecutor、SimpleTask、TaskProgress、ITaskInfo/IBackgroundTask、DecompileTask、ExportTask、LoadTask…） | `ExecutorService`/`Future`/`AtomicInteger`/`SwingUtilities` | `CoroutineScope(SupervisorJob()+Dispatchers.Swing)` + `launch/async` + `Flow<TaskProgress>` + `Job` 取消 |
| N1b | `SwingWorker` 调用点：`MainWindow`、`TabsController`、`TabbedPane`、`ApkSignatureNode` | `SwingWorker.doInBackground/done` | `scope.launch { withContext(Dispatchers.IO){…} ; /*EDT 收尾*/ }` |
| N1c | RxJava：`HeapUsageBar`、`SearchDialog`、`utils/rx/*`、`cache/usage` 后台加载 | `Flowable`/`Schedulers`/`SwingSchedulers` | `flow{}` + `debounce` + `Dispatchers.Swing`；`utils/rx/*` 若无引用则删/替换 |
| N1d | `utils/fileswatcher`（FilesWatcher、LiveReloadWorker） | 后台线程 + `WatchService` | `flow{}` + `Dispatchers.IO` + 取消 |
| N1e | `Thread.sleep` 轮询（16 处） | 阻塞轮询 | `delay` + 协程（仅在已协程化的上下文中） |
| N1f | 清理：删除 `rxjava3-swing` 依赖（若 N1c 后无引用）、`utils/rx` | — | 依赖瘦身 |

### 验收
- `./gradlew :jadx-gui:test` 绿 + `./gradlew build` 绿；手工核对：所有 UI 更新仍在 EDT（`Dispatchers.Swing`）。
- 禁止 `GlobalScope`；每个组件的 `CoroutineScope` 随组件生命周期取消（`dispose()`/`close()` 里 `scope.cancel()`）。

---

## 2. 阶段 N2：jadx-core / cli 并发协程化

| ID | 范围 | 现状 | 目标 |
|----|------|------|------|
| N2a | `jadx/core/utils/tasks/TaskExecutor`、`utils/tasks/*` | `ExecutorService` | `CoroutineScope` + `Dispatchers.Default`；API 保持可被同步调用方使用 |
| N2b | `jadx/core/utils/DecompilerScheduler`、`api/impl/…`、`JadxDecompiler` 异步/线程池 | `ThreadPoolExecutor`/`Executors` | 协程调度（注意 CLI 无 Swing） |
| N2c | `jadx/core/ProcessClass`、`RootNode` 加载等 | `Thread`/`Executor` | 协程 + 结构化并发 |
| N2d | `synchronized`/`@Synchronized`（66/37 处） | 监视器锁 | 评估：多数是短临界区，可保留；仅对「等待型」改 `Mutex`（不盲目全改） |

> 原则：core 是库，公共 API 不得强制调用方用协程；仅在**内部实现**用协程，或提供 `suspend` 重载。

---

## 3. 阶段 N3：去 Java 味（惯用 Kotlin，内部代码）

按「机械、低风险 → 高收益」排序，逐包推进：

| ID | 模式 | 约计 | 目标 |
|----|------|-----:|------|
| N3a | `java.util.function.{Function,Consumer,Supplier,BiFunction,Predicate}` | 146 文件 | Kotlin 函数类型 `(T)->R`、`(T)->Unit`；仅当接口需 Java 实现时保留 |
| N3b | `Optional<T>` | 12 | 可空类型 `T?` |
| N3c | `Objects.requireNonNull(x)` | 18 | `requireNotNull(x) { … }` |
| N3d | `String.format(...)` | 52 | 字符串模板 / `buildString`（注意本地化语义） |
| N3e | `!!` | 80 | `?:`/`checkNotNull`/`requireNotNull`/重构 |
| N3f | `.stream()` + `Collectors.*` | 40+28 | Kotlin 集合算子（**热点 pass 除外**） |
| N3g | `for (int i=…)` C 风格 | 43 | `for (i in a until b)` / `indices`（性能敏感处保留） |
| N3h | `instanceof`+cast | 25 | `is` + 智能转换 |
| N3i | 显式 `fun getX()`/`fun isX()`（内部类） | 755/310 | 改 Kotlin 属性（**公共 API/插件面保留**） |
| N3j | `@JvmStatic`/`@JvmField`（无 Java 调用方者） | 327/90 | 评估后移除；保留公共 API 与 fixture 相关者 |

> N3 是**长期、按包推进**的工作；每批限定一个包 + 一个模式，避免大爆炸式改动。

---

## 4. 执行顺序与里程碑

1. **N1（协程）** —— 优先，用户明确要求；先 pilot（`jobs` + 1 个 SwingWorker 调用点）验证 EDT 语义。
2. **N2（core 并发）** —— N1 稳定后。
3. **N3（去 Java 味）** —— 与 N1/N2 并行按包推进（不同文件，低冲突）。

**每个批次的 Gate：**
```
./gradlew spotlessApply
./gradlew <module>:compileKotlin <module>:compileTestKotlin --console=plain
./gradlew <module>:test --console=plain
./gradlew build --console=plain
```

## 5. 风险

- **EDT 死锁 / UI 冻结**：协程化最大的坑。所有 `Dispatchers.Swing` 的使用必须保证不阻塞 EDT（耗时操作 `withContext(Dispatchers.IO)`）。
- **取消语义**：原 `Future.cancel`/`SwingWorker.cancel` 与 `Job.cancel` 语义不同；需逐处核对中断处理。
- **公共 API 兼容**：第三方插件（Java）依赖静态面；去 Java 味不得触碰 `jadx.api.*` 契约。
- **性能回归**：`jadx-core` pass 链对集合算子敏感；N3f 只在非热点处做。
