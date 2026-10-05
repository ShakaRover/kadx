package kadx.api.plugins.pass.impl

import kadx.api.plugins.pass.KadxPassInfo
import java.util.Collections

/**
 * 最简 [KadxPassInfo] 实现：只有名称与描述，不声明任何执行顺序依赖。
 *
 * **为什么属性是 private**：Kotlin 若生成 `getName()` 会与显式实现的
 * `getName()` 方法产生 JVM 签名冲突，私有属性只做字段访问即可。
 */
class SimpleKadxPassInfo(private val name: String, private val desc: String) : KadxPassInfo {

	/** 名称与描述相同时的便捷构造器。 */
	constructor(name: String) : this(name, name)

	override fun getName(): String = name

	override fun getDescription(): String = desc

	override fun runAfter(): List<String> = Collections.emptyList()

	override fun runBefore(): List<String> = Collections.emptyList()
}
