package kadx.api.impl.passes

import kadx.api.plugins.pass.KadxPass
import kadx.core.dex.visitors.IDexTreeVisitor

/**
 * Pass 包装访问者接口：把插件提供的 [KadxPass] 包装成 kadx 内部的
 * [IDexTreeVisitor]，以便插入到固定的 Pass 执行链中。
 *
 * **做什么**：[getPass] 返回被包装的原始 Pass，供合并/排序逻辑识别。
 * 实现类（[DecompilePassWrapper]、[PreparePassWrapper]）同时继承 `AbstractVisitor`。
 *
 * 保持为接口（Java 可实现），方法签名与 JVM 名与原 Java 一致。
 */
interface IPassWrapperVisitor : IDexTreeVisitor {

	/** 返回被包装的原始 Pass。 */
	fun getPass(): KadxPass
}
