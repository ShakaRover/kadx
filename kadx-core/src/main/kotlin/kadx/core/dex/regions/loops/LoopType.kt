package kadx.core.dex.regions.loops

/**
 * 循环类型标记的抽象基类。
 *
 * 具体子类：
 * - [ForLoop]：普通 `for (init; cond; incr)` 形式；
 * - [ForEachLoop]：`for (x : iterable)` 增强 for 形式。
 *
 * 只是类型标签，不含状态。
 */
abstract class LoopType
