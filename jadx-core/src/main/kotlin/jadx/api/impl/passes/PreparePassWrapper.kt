package jadx.api.impl.passes

import jadx.api.plugins.pass.JadxPass
import jadx.api.plugins.pass.types.JadxPreparePass
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.utils.exceptions.JadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 把插件的“准备 Pass”（[JadxPreparePass]）包装成 jadx 内部访问者。
 *
 * **做什么**：准备 Pass 只在反编译前对整棵树做一次性初始化（[init]），
 * 不参与逐类遍历；这里做异常兜底，保证单个插件失败不影响主流程。
 */
class PreparePassWrapper(
	private val preparePass: JadxPreparePass,
) : AbstractVisitor(),
	IPassWrapperVisitor {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(PreparePassWrapper::class.java)
	}

	override fun getPass(): JadxPass = preparePass

	@Throws(JadxException::class)
	override fun init(root: RootNode) {
		try {
			preparePass.init(root)
		} catch (e: Exception) {
			LOG.error("Error in prepare pass init: {}", this, e)
		}
	}

	override fun getName(): String = preparePass.getInfo().getName()
}
