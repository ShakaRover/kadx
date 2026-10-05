package kadx.plugins.kotlin.metadata.pass

import kadx.api.plugins.pass.KadxPassInfo
import kadx.api.plugins.pass.impl.OrderedKadxPassInfo
import kadx.api.plugins.pass.types.KadxPreparePass
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.nodes.RootNode
import kadx.plugins.kotlin.metadata.KotlinMetadataOptions
import kadx.plugins.kotlin.metadata.utils.KotlinMetadataUtils

class KotlinMetadataPreparePass(
	private val options: KotlinMetadataOptions,
) : KadxPreparePass {

	override fun getInfo(): KadxPassInfo = OrderedKadxPassInfo(
		"KotlinMetadataPrepare",
		"Use kotlin.Metadata annotation to rename class & package",
	)
		.before("RenameVisitor")

	override fun init(root: RootNode) {
		if (options.isClassAlias) {
			for (cls in root.classes) {
				if (cls.contains(AFlag.DONT_RENAME)) {
					continue
				}

				// rename class & package
				val kotlinCls = KotlinMetadataUtils.getAlias(cls)
				if (kotlinCls != null) {
					cls.rename(kotlinCls.name)
					cls.packageNode.rename(kotlinCls.pkg)
				}
			}
		}
	}
}
