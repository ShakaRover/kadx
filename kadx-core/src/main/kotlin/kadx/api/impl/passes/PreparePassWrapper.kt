package kadx.api.impl.passes

import kadx.api.plugins.pass.KadxPass
import kadx.api.plugins.pass.types.KadxPreparePass
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.utils.exceptions.KadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 把插件的“准备 Pass”（[KadxPreparePass]）包装成 kadx 内部访问者。
 *
 * **做什么**：准备 Pass 只在反编译前对整棵树做一次性初始化（[init]），
 * 不参与逐类遍历；这里做异常兜底，保证单个插件失败不影响主流程。
 */
class PreparePassWrapper(
	private val preparePass: KadxPreparePass,
) : AbstractVisitor(),
	IPassWrapperVisitor {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(PreparePassWrapper::class.java)
	}

	override fun getPass(): KadxPass = preparePass

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		try {
			preparePass.init(root)
		} catch (e: Exception) {
			LOG.error("Error in prepare pass init: {}", this, e)
		}
	}

	override fun getName(): String = preparePass.getInfo().getName()
}
