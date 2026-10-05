package kadx.plugins.kotlin.smap.pass

import kadx.api.plugins.pass.KadxPassInfo
import kadx.api.plugins.pass.impl.OrderedKadxPassInfo
import kadx.api.plugins.pass.types.KadxPreparePass
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.nodes.RootNode
import kadx.plugins.kotlin.smap.KotlinSmapOptions
import kadx.plugins.kotlin.smap.utils.KotlinSmapUtils

class KotlinSourceDebugExtensionPass(
	private val options: KotlinSmapOptions,
) : KadxPreparePass {

	override fun getInfo(): KadxPassInfo = OrderedKadxPassInfo(
		"SourceDebugExtensionPrepare",
		"Use kotlin.jvm.internal.SourceDebugExtension annotation to rename class & package",
	)
		.before("RenameVisitor")

	override fun init(root: RootNode) {
		if (options.isClassAliasSourceDbg) {
			for (cls in root.classes) {
				if (cls.contains(AFlag.DONT_RENAME)) {
					continue
				}

				// rename class & package
				val kotlinCls = KotlinSmapUtils.getClassAlias(cls)
				if (kotlinCls != null) {
					cls.rename(kotlinCls.name)
					cls.packageNode.rename(kotlinCls.pkg)
				}
			}
		}
	}
}
