package kadx.api.plugins.pass.impl

import kadx.api.plugins.pass.KadxPassInfo
import java.util.ArrayList

/**
 * 可声明执行顺序的 [KadxPassInfo] 实现。
 *
 * **做什么**：用 [after] / [before] 链式登记「本 pass 应在某 pass 之后/之前执行」，
 * 由 `PassMerge` 读取这些列表完成拓扑排序。
 *
 * **为什么属性是 private**：`runAfter` / `runBefore` 同时是属性名与方法名，
 * 私有属性不会生成 getter，避免与显式 override 的 `runAfter()` 冲突。
 */
class OrderedKadxPassInfo(
	private val name: String,
	private val desc: String,
	private val runAfter: MutableList<String>,
	private val runBefore: MutableList<String>,
) : KadxPassInfo {

	constructor(name: String, desc: String) : this(name, desc, ArrayList(), ArrayList())

	/** 登记「本 pass 在 [pass] 之后执行」。 */
	fun after(pass: String): OrderedKadxPassInfo {
		runAfter.add(pass)
		return this
	}

	/** 登记「本 pass 在 [pass] 之前执行」。 */
	fun before(pass: String): OrderedKadxPassInfo {
		runBefore.add(pass)
		return this
	}

	override fun getName(): String = name

	override fun getDescription(): String = desc

	override fun runAfter(): List<String> = runAfter

	override fun runBefore(): List<String> = runBefore

	override fun toString(): String = "PassInfo{'$name', desc='$desc', runAfter=$runAfter, runBefore=$runBefore}"
}
