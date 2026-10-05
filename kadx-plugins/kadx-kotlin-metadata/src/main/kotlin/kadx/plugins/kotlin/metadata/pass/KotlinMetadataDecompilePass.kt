package kadx.plugins.kotlin.metadata.pass

import kadx.api.plugins.input.data.AccessFlags
import kadx.api.plugins.pass.KadxPassInfo
import kadx.api.plugins.pass.impl.OrderedKadxPassInfo
import kadx.api.plugins.pass.types.KadxDecompilePass
import kadx.core.deobf.NameMapper
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.nodes.RenameReasonAttr
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.plugins.kotlin.metadata.KotlinMetadataOptions
import kadx.plugins.kotlin.metadata.utils.KmClassWrapper
import kadx.plugins.kotlin.metadata.utils.KmClassWrapper.Companion.getWrapper

class KotlinMetadataDecompilePass(
	private val options: KotlinMetadataOptions,
) : KadxDecompilePass {

	override fun getInfo(): KadxPassInfo = OrderedKadxPassInfo(
		"KotlinMetadataDecompile",
		"Use kotlin.Metadata annotation perform various renames",
	)
		.before("CodeRenameVisitor")

	override fun init(root: RootNode) {
	}

	override fun visit(cls: ClassNode): Boolean {
		cls.innerClasses.forEach(::visit)

		val wrapper = cls.getWrapper() ?: return false
		if (options.isMethodArgs) renameMethodArgs(wrapper)
		if (options.isFields) renameFields(wrapper)
		if (options.isCompanion) renameCompanion(wrapper)
		if (options.isDataClass) fixDataClass(wrapper)
		if (options.isToString) renameToString(wrapper)
		if (options.isGetters) renameGetters(wrapper)

		return false
	}

	override fun visit(mth: MethodNode) {
		/* no op */
	}

	private fun renameMethodArgs(wrapper: KmClassWrapper) {
		val args = wrapper.methodArgs
		args.forEach { (_, list) ->
			list.forEach { (rArg, alias) ->
				// TODO: comment not being added?
				RenameReasonAttr.forNode(rArg).append(METADATA_REASON)
				rArg.name = alias
			}
		}
	}

	private fun renameFields(wrapper: KmClassWrapper) {
		val fields = wrapper.fields
		fields.forEach { (field, alias) ->
			if (AFlag.DONT_RENAME !in field) {
				RenameReasonAttr.forNode(field).append(METADATA_REASON)
				field.rename(alias)
			}
		}
	}

	private fun renameCompanion(wrapper: KmClassWrapper) {
		val companion = wrapper.companion
		companion?.run {
			if (AFlag.DONT_RENAME !in field) {
				RenameReasonAttr.forNode(field).append(METADATA_REASON)
				field.rename(COMPANION_FIELD)
			}
			if (AFlag.DONT_RENAME !in cls) {
				RenameReasonAttr.forNode(cls).append(METADATA_REASON)
				cls.rename(COMPANION_CLASS)
			}

			if (hide) {
				field.add(AFlag.DONT_GENERATE)
				cls.add(AFlag.DONT_GENERATE)
				cls.add(AFlag.DONT_INLINE)
			}
		}
	}

	private fun fixDataClass(wrapper: KmClassWrapper) {
		val isData = wrapper.isDataClass
		wrapper.cls.run {
			if (isData != accessFlags.isData()) {
				accessFlags = accessFlags.run {
					if (isData) {
						add(AccessFlags.DATA)
					} else {
						remove(AccessFlags.DATA)
					}
				}
			}
		}
	}

	private fun renameToString(wrapper: KmClassWrapper) {
		val toString = wrapper.parseToString()
		toString?.run {
			clsAlias?.let { alias ->
				if (NameMapper.isValidIdentifier(alias) && AFlag.DONT_RENAME !in cls) {
					RenameReasonAttr.forNode(cls).append(TO_STRING_REASON)
					cls.rename(alias)
				}
			}

			fields.forEach { (field, alias) ->
				if (NameMapper.isValidIdentifier(alias) && AFlag.DONT_RENAME !in field) {
					RenameReasonAttr.forNode(field).append(TO_STRING_REASON)
					field.rename(alias)
				}
			}
		}
	}

	private fun renameGetters(wrapper: KmClassWrapper) {
		val getters = wrapper.getters
		getters.forEach { (mth, alias) ->
			if (AFlag.DONT_RENAME !in mth) {
				RenameReasonAttr.forNode(mth).append(GETTER_REASON)
				mth.rename(alias)
			}
		}
	}

	companion object {
		private const val METADATA_REASON = "from kotlin metadata"
		private const val COMPANION_FIELD = "INSTANCE"
		private const val COMPANION_CLASS = "Companion"
		private const val TO_STRING_REASON = "from toString"
		private const val GETTER_REASON = "from getter"
	}
}
