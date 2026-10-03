package jadx.gui.cache.code

import jadx.api.ICodeCache
import jadx.api.ICodeInfo
import jadx.api.impl.DelegateCodeCache

/**
 * 固定大小的代码缓存装饰器。
 *
 * **做什么**：包一层 [DelegateCodeCache]，但把 [remove] / [add] 变成空操作，
 * 从而让底层缓存的内容保持不变（只读缓存）。
 *
 * **为什么覆写为空实现**：用于“代码只写入一次、之后不允许修改”的场景
 * （例如反编译完成后锁定结果）。
 */
class FixedCodeCache(codeCache: ICodeCache) : DelegateCodeCache(codeCache) {

	override fun remove(clsFullName: String) {
		// 空操作：固定缓存不允许删除
	}

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		// 空操作：固定缓存不允许新增
	}
}
