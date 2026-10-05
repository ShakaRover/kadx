package kadx.api

/**
 * 反编译调度器接口：决定把哪些类分批交给哪些线程去反编译，以降低锁竞争。
 *
 * 这是公共 API，可被插件替换。`buildBatches` 的 JVM 签名（`java.util.List`）保持不变。
 */
interface IDecompileScheduler {

	/**
	 * 把待反编译的类划分成多个批次。
	 *
	 * @param classes 待反编译的类列表
	 * @return 批次列表，每个批次是一个类列表
	 */
	fun buildBatches(classes: List<JavaClass>): List<List<JavaClass>>
}
