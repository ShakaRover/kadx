package jadx.core.utils

import jadx.api.IDecompileScheduler
import jadx.api.JavaClass
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * 反编译批调度器：把待反编译的类划分成若干批次，尽量减少线程之间的锁竞争。
 *
 * **策略**：依赖多的类放最后；单个类的依赖单独成批（因为依赖会被该批先处理并缓存）。
 * 无依赖的类合并成固定大小的大批。任何异常都回退到“每个类单独一批”的保守方案。
 */
class DecompilerScheduler : IDecompileScheduler {

	override fun buildBatches(classes: List<JavaClass>): List<List<JavaClass>> {
		try {
			val start = System.currentTimeMillis()
			val result = internalBatches(classes)
			if (LOG.isDebugEnabled) {
				LOG.debug(
					"Build decompilation batches in {}ms for {} classes",
					System.currentTimeMillis() - start,
					classes.size,
				)
			}
			if (DEBUG_BATCHES) {
				check(result, classes)
			}
			return result
		} catch (e: StackOverflowError) {
			LOG.warn("Stack overflow while building decompile batches, continue with fallback")
		} catch (e: BootstrapMethodError) {
			LOG.warn("Stack overflow while building decompile batches, continue with fallback")
		} catch (e: Exception) {
			LOG.warn("Build batches failed (continue with fallback)", e)
		}
		return buildFallback(classes)
	}

	/**
	 * 依赖多的类放最后；对单个类的依赖建批，避免其它线程先处理导致锁竞争。
	 */
	fun internalBatches(classes: List<JavaClass>): List<List<JavaClass>> {
		val deps = sumDependencies(classes)
		val added: MutableSet<JavaClass> = HashSet(classes.size)
		val cmpDepSize = Comparator.comparingInt<JavaClass> { it.getTotalDepsCount() }
		val result = ArrayList<List<JavaClass>>()
		var mergedBatch = ArrayList<JavaClass>(MERGED_BATCH_SIZE)
		for (depInfo in deps) {
			val cls = depInfo.cls
			if (!added.add(cls)) {
				continue
			}
			val depsSize = cls.getTotalDepsCount()
			if (depsSize == 0) {
				// 无依赖的类合并进同一个批次
				mergedBatch.add(cls)
				if (mergedBatch.size >= MERGED_BATCH_SIZE) {
					result.add(mergedBatch)
					mergedBatch = ArrayList(MERGED_BATCH_SIZE)
				}
			} else {
				val batch = ArrayList<JavaClass>()
				for (dep in cls.getDependencies()) {
					val topDep = dep.getTopParentClass()
					if (!added.contains(topDep)) {
						batch.add(topDep)
						added.add(topDep)
					}
				}
				batch.sortWith(cmpDepSize)
				batch.add(cls)
				result.add(Utils.lockList(batch))
			}
		}
		if (mergedBatch.isNotEmpty()) {
			result.add(mergedBatch)
		}
		if (DEBUG_BATCHES) {
			dumpBatchesStats(classes, result, deps)
		}
		return result
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DecompilerScheduler::class.java)
		private const val MERGED_BATCH_SIZE = 16
		private const val DEBUG_BATCHES = false

		private fun sumDependencies(classes: List<JavaClass>): List<DepInfo> {
			val deps = ArrayList<DepInfo>(classes.size)
			for (cls in classes) {
				var count = 0
				for (dep in cls.getDependencies()) {
					count += 1 + dep.getTotalDepsCount()
				}
				deps.add(DepInfo(cls, count))
			}
			Collections.sort(deps)
			return deps
		}

		private fun buildFallback(classes: List<JavaClass>): List<List<JavaClass>> = classes
			.sortedBy { c: JavaClass -> c.getClassNode().totalDepsCount }
			.map { c: JavaClass -> Collections.singletonList(c) }

		private fun dumpBatchesStats(classes: List<JavaClass>, result: List<List<JavaClass>>, deps: List<DepInfo>) {
			val clsInBatches = result.sumOf { it.size }
			val avg = if (result.isEmpty()) -1.0 else result.sumOf { it.size }.toDouble() / result.size
			val maxSingleDeps = classes.maxOfOrNull { it.getTotalDepsCount() } ?: -1
			val maxSubDeps = deps.maxOfOrNull { it.depsCount } ?: -1
			LOG.info(
				"Batches stats:" +
					"\n input classes: " + classes.size +
					",\n classes in batches: " + clsInBatches +
					",\n batches: " + result.size +
					",\n average batch size: " + String.format("%.2f", avg) +
					",\n max single deps count: " + maxSingleDeps +
					",\n max sub deps count: " + maxSubDeps,
			)
		}

		private fun check(result: List<List<JavaClass>>, classes: List<JavaClass>) {
			val classInBatches = result.sumOf { it.size }
			if (classes.size != classInBatches) {
				throw JadxRuntimeException(
					"Incorrect number of classes in result batch: $classInBatches, expected: " + classes.size,
				)
			}
		}
	}

	private class DepInfo(val cls: JavaClass, val depsCount: Int) : Comparable<DepInfo> {

		override fun compareTo(o: DepInfo): Int {
			val deps = depsCount.compareTo(o.depsCount)
			if (deps == 0) {
				return cls.getClassNode().compareTo(o.cls.getClassNode())
			}
			return deps
		}

		override fun toString(): String = "$cls:$depsCount"
	}
}
