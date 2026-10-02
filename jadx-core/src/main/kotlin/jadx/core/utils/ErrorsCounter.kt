package jadx.core.utils

import jadx.core.Consts
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.attributes.nodes.JadxError
import jadx.core.dex.nodes.IDexNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.exceptions.JadxOverflowException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * 全局错误/警告计数器。
 *
 * **用途**：反编译过程中遇到的每个错误/警告都记录到这里（并挂到对应节点上），
 * 最后统一打印报告。方法级错误还会被去重（同一节点只统计一次）。
 *
 * **Kotlin 转换说明**：两个静态入口 [error] / [warning] 使用带交集类型约束的泛型
 * （`N : IDexNode & IAttributeNode`），Kotlin 用 `where` 子句表达；`@JvmStatic`
 * 保证 Java 侧 `ErrorsCounter.error(...)` 静态调用不变。原 `synchronized` 方法
 * 改写为普通函数 + `@Synchronized`（K2 不接受 `synchronized fun`）。
 */
class ErrorsCounter {

	private val errorNodes: MutableSet<IAttributeNode> = HashSet()
	private var errorsCount: Int = 0
	private val warnNodes: MutableSet<IAttributeNode> = HashSet()
	private var warnsCount: Int = 0

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ErrorsCounter::class.java)
		private val PRINT_MTH_SIZE: Boolean = Consts.DEBUG

		@JvmStatic
		fun <N> error(node: N, warnMsg: String, th: Throwable?): String where N : IDexNode, N : IAttributeNode = node.root().getErrorsCounter().addError(node, warnMsg, th)

		@JvmStatic
		fun <N> warning(node: N, warnMsg: String) where N : IDexNode, N : IAttributeNode {
			node.root().getErrorsCounter().addWarning(node, warnMsg)
		}

		@JvmStatic
		fun formatMsg(node: IDexNode, msg: String): String = msg + " in " + node.typeName() + ": " + node + ", file: " + node.getInputFileName()
	}

	@Synchronized
	private fun <N> addError(node: N, error: String, e: Throwable?): String where N : IDexNode, N : IAttributeNode {
		errorNodes.add(node)
		errorsCount++

		var err = error
		var throwable = e
		var msg = formatMsg(node, error)
		if (PRINT_MTH_SIZE && node is MethodNode) {
			val mthSize = "[" + node.getInsnsCount() + "] "
			msg = mthSize + msg
			err = mthSize + err
		}
		if (throwable == null) {
			LOG.error(msg)
		} else if (throwable is StackOverflowError) {
			LOG.error("{}, error: StackOverflowError", msg)
		} else if (throwable is JadxOverflowException) {
			// 不打印完整堆栈，只保留 details 信息
			val details = throwable.message
			throwable = JadxOverflowException(details)
			if (details == null || details.isEmpty()) {
				LOG.error("{}", msg)
			} else {
				LOG.error("{}, details: {}", msg, details)
			}
		} else {
			LOG.error(msg, throwable)
		}
		node.addAttr(AType.JADX_ERROR, JadxError(err, throwable))
		return msg
	}

	@Synchronized
	private fun <N> addWarning(node: N, warn: String) where N : IDexNode, N : IAttributeNode {
		warnNodes.add(node)
		warnsCount++
		LOG.warn(formatMsg(node, warn))
	}

	fun printReport() {
		if (getErrorCount() > 0) {
			LOG.error("{} errors occurred in following nodes:", getErrorCount())
			val errors = ArrayList<String>(errorNodes.size)
			for (node in errorNodes) {
				val nodeName = node.javaClass.simpleName.replace("Node", "")
				errors.add("$nodeName: $node")
			}
			Collections.sort(errors)
			for (err in errors) {
				LOG.error("  {}", err)
			}
		}
		if (getWarnsCount() > 0) {
			LOG.warn("{} warnings in {} nodes", getWarnsCount(), warnNodes.size)
		}
	}

	fun getErrorCount(): Int = errorsCount

	fun getWarnsCount(): Int = warnsCount

	fun getErrorNodes(): Set<IAttributeNode> = errorNodes

	fun getWarnNodes(): Set<IAttributeNode> = warnNodes
}
