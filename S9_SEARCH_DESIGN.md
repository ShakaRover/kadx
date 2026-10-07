# S9 设计稿：GUI 代码搜索的流式/分块改造（仅设计，未动工）

## 1. 现状测绘（有界微信子集 `nd5`，8,279 个非内部类）

bench `/tmp/kadx-perf/bench/SearchScan.java` 复刻 `CodeSearchProvider` 的扫描循环（逐类 `getCode` → 匹配 → 丢弃），`-Xlog:gc` + 强制 GC 的 live 曲线。

| 配置 | load 后 live | 扫完 live | **扫描增量** | 每类 | 外推 253k 类 |
|---|---|---|---|---|---|
| DISK_WITH_CACHE（`CodeStringCache(LRU512)`+`DiskCodeCache`） | 3145 MB | 3282 MB | **+137 MB** | 16.6 KB | ~4.2 GB |
| **MEMORY（用户可选，非默认）** | 3121 MB | 3398 MB | **+277 MB** | 33.5 KB | **~8.5 GB** |
| **MEMORY + P1 有界缓存** | 3124 MB | 3276 MB | **+152 MB** | 常量 | **常量 ~15 MB** |
| 关闭后 | — | 2262 / 2243 MB | 释放 ~1.15 GB | — | — |

（数字来自干净 `installDist` 重建后的复测；首次测量误用残留 S7 埋点的旧安装，复测确认埋点对堆无影响。）

**结论（含一处前期错误修正）**：
0. **`CodeCacheMode.MEMORY` 不是默认值** —— 默认是 `DISK`（`KadxSettingsData:94`；全库仅默认值与设置 UI 两处赋值）。MEMORY 是**用户可选**模式；前期稿误写为“默认”，源于 `CodeCacheMode` 的 KDoc 一句过时注释，已修正。
1. **峰值主因不是搜索逻辑，而是 MEMORY 分支的缓存实现**：该分支装**无界 `InMemoryCodeCache`**（`CodeStringCache` 的 LRU 只在 DISK_WITH_CACHE 下构造），故选了该模式的用户全量搜索会线性增长。⇒ 修正后的 T3 结论：**默认 DISK 配置下搜索已是内存安全的**（`BufferCodeCache` 仅 20 条 + 磁盘），但 MEMORY 模式会把每个类的 `ICodeInfo` 永久驻留（外推 ~8.5 GB）。
2. `CodeSearchProvider` **已经是流式的**：一次只持有当前类的 `code` 字符串，扫完即丢。
   逐类瞬时字符串（~100 KB 级）GC 友好，**不是峰值来源**。
3. 结果集默认按页截断：`searchResultsPerPage = 50`，达上限即 `cancel()`。只有 "load all"
   （limit=0）会让 `ResultsModel` + 无界 `JNodeCache` 随结果增长。
4. DISK_WITH_CACHE 仍 +137 MB（16.6 KB/类），主要是反编译后未 unload 的 ClassNode 状态；
   bench 未接 `BackgroundExecutor` 的内存管理，故这是**上界**。

## 2. 流式方案评估（逐类读取→匹配→丢弃）
- **接口层面已满足**：`ISearchProvider.next(cancelable): JNode?` 本就是 pull 式迭代器，
  `SearchJob` 循环取到 null/取消/达上限为止。所以"改流式"**几乎没有可回收的内存**。
- 分块（chunked）读取只省一次 String 拼接，收益在延迟而非内存；且会让
  `ISearchMethod.find(input, subStr, start)` 需要跨块边界处理（正则尤甚），
  **有改变搜索语义的风险 → 不建议做**。
- 真正值得"流式化"的是**结果集**：给 `ResultsModel`/`JNodeCache` 加上限或虚拟化。

## 3. 增量方案评估（直接 grep 磁盘 sources）
磁盘布局 `<cacheDir>/code/sources/<hex>/<hex>.java`；`CodeMetadataAdapter` 只写
lines/annotations，**不存类名**，文件名是类 id 的十六进制。
- 障碍：① 缓存**只含曾反编译过的类**，全量搜索仍需反编译其余类 → **grep 磁盘不能替代
  反编译**，只能加速"重复搜索"；② 需要 id→类名索引才能回报结果（现无）；
  ③ 与 `ICodeCache` 抽象不冲突（只读旁路），但需新增索引文件并纳入 `code-version` 失效。
- 结论：作为**重复搜索加速**有价值，**但不是内存方案**。

## 4. UX 约束（不得改变）
- 可取消：`Cancelable.isCanceled` 已逐类检查；`pauseSearch` → `task.cancel()`。
- 进度：`progress()` = 已扫描类数、`total()` = 类数，二者语义必须保持。
- 分页：`searchResultsPerPage`(50) + `loadMoreResults(all)`；注意后者现在是**重跑整个搜索**
  并放大 limit（非续跑），每次重复 O(N) 扫描。
- 语义：code/class/method/field/comment/resource 各选项、包过滤、正则/忽略大小写均不变。

## 5. 推荐方案与分期
- **P1（已实现）**：加固 MEMORY 分支 —— 改用有界 `BoundedMemoryCodeCache`（LRU 512，实测缓存自身
  恒为 ~15 MB 而非线性增长）；并修正 Gson 遇到未知枚举值时把非空字段置 null、
  导致 `when` 落到兜底分支而静默保留无界缓存的隐患（null → 回退 DISK 并 warn）。
  不改搜索代码、不改磁盘格式。代价：内存模式无磁盘兜底，被淘汰意味着下次重新反编译
  （单类实测 ~1.4 ms，可接受）。
- **P2（可选）**：结果集加上限/虚拟化（`ResultsModel` + `JNodeCache`）；把 `loadMoreResults`
  从"重跑"改为"续跑"（保存 provider 游标），顺带消掉重复 O(N)。
- **P3（可选，加速而非省内存）**：磁盘 sources 的 id→类名索引 + 增量 grep，供重复搜索
  跳过反编译；需纳入 `code-version` 失效并处理"缓存不完整"语义。
