package kadx.plugins.kotlin.metadata.utils

import kadx.core.dex.nodes.ClassNode
import kotlin.metadata.KmClass
import kotlin.metadata.isData
import kotlin.metadata.jvm.KotlinClassMetadata

// don't expose kotlinx.metadata.* types ?
class KmClassWrapper private constructor(
	val cls: ClassNode,
	private val kmCls: KmClass,
) {

	val methodArgs get() = KotlinMetadataUtils.mapMethodArgs(cls, kmCls)

	val fields get() = KotlinMetadataUtils.mapFields(cls, kmCls)

	val companion get() = KotlinMetadataUtils.mapCompanion(cls, kmCls)

	val isDataClass get() = kmCls.isData

	// does not require metadata, may be useful for plain java ?
	fun parseToString() = KotlinUtils.parseToString(cls)

	// does not require metadata, may be useful for plain java ?
	val getters get() = KotlinUtils.findGetters(cls)

	companion object {

		fun ClassNode.getWrapper(): KmClassWrapper? {
			val metadata = getKotlinClassMetadata()
			val kmCls = (metadata as? KotlinClassMetadata.Class)?.kmClass ?: return null
			return KmClassWrapper(this, kmCls)
		}
	}
}
