package kadx.api.plugins.pass

/**
 * Pass 元信息：名称、描述以及相对其它 pass 的执行顺序声明。
 *
 * **为什么常量放在 companion object**：原 Java 接口里的 `START` / `END` 是
 * `public static final` 字段，Java 插件以 `KadxPassInfo.START` 访问；
 * Kotlin 用 `companion object` 中的 `const val` 生成同样的静态字段。
 */
interface KadxPassInfo {

	companion object {
		/**
		 * 把该值加入 `runAfter` 列表，可让本 pass 排到所有 pass 之前。
		 */
		const val START: String = "start"

		/**
		 * 把该值加入 `runBefore` 列表，可让本 pass 排到所有 pass 之后。
		 */
		const val END: String = "end"
	}

	/**
	 * pass 的短 id，应保持唯一。
	 */
	fun getName(): String

	/**
	 * pass 描述。
	 */
	fun getDescription(): String

	/**
	 * 本 pass 将在这些 pass 之后执行（按 pass 名称列表声明）。
	 */
	fun runAfter(): List<String>

	/**
	 * 本 pass 将在这些 pass 之前执行（按 pass 名称列表声明）。
	 */
	fun runBefore(): List<String>
}
