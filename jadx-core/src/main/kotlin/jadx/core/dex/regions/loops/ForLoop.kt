package jadx.core.dex.regions.loops

import jadx.core.dex.nodes.InsnNode

/**
 * 普通 `for` 循环的类型标记。
 *
 * 保存初始化指令 [initInsn] 与自增指令 [incrInsn]，供 codegen 生成
 * `for (init; cond; incr)` 的三段式头部。
 */
class ForLoop(val initInsn: InsnNode, val incrInsn: InsnNode) : LoopType()
