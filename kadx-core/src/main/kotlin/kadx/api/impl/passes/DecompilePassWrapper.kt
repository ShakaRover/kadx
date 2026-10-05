package kadx.api.impl.passes

import kadx.api.plugins.pass.KadxPass
import kadx.api.plugins.pass.types.KadxDecompilePass
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.utils.exceptions.KadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 把插件的“反编译 Pass”（[KadxDecompilePass]）包装成 kadx 内部访问者。
 *
 * **做什么**：转发 [init]/[visit] 到被包装的 Pass，并做统一异常兜底——
 * 单个 Pass 出错不应该让整次反编译崩溃，而是把错误记录到类/方法上。
 *
 * **为什么逐个 catch `StackOverflowError` 与 `Exception`**：原 Java 用
 * `catch (StackOverflowError | Exception e)`；Kotlin 拆成两个 catch 分支，
 * 避免误捕其它 `Error`（语义保持一致）。
 */
class DecompilePassWrapper(
	private val decompilePass: KadxDecompilePass,
) : AbstractVisitor(),
	IPassWrapperVisitor {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DecompilePassWrapper::class.java)
	}

	override fun getPass(): KadxPass = decompilePass

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		try {
			decompilePass.init(root)
		} catch (e: StackOverflowError) {
			LOG.error("Error in decompile pass init: {}", this, e)
		} catch (e: Exception) {
			LOG.error("Error in decompile pass init: {}", this, e)
		}
	}

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean = try {
		decompilePass.visit(cls)
	} catch (e: StackOverflowError) {
		cls.addError("Error in decompile pass: $this", e)
		false
	} catch (e: Exception) {
		cls.addError("Error in decompile pass: $this", e)
		false
	}

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		try {
			decompilePass.visit(mth)
		} catch (e: StackOverflowError) {
			mth.addError("Error in decompile pass: $this", e)
		} catch (e: Exception) {
			mth.addError("Error in decompile pass: $this", e)
		}
	}

	override fun getName(): String = decompilePass.getInfo().getName()
}
