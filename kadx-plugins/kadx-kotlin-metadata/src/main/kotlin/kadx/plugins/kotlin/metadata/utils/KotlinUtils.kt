package kadx.plugins.kotlin.metadata.utils

import kadx.core.Consts
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.args.PrimitiveType
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.plugins.kotlin.metadata.model.MethodRename
import kadx.plugins.kotlin.metadata.model.ToStringRename
import java.util.Locale

object KotlinUtils {

	fun parseToString(cls: ClassNode): ToStringRename? {
		val mthToString = cls.searchMethodByShortId(Consts.MTH_TOSTRING_SIGNATURE)
			?: return null

		return ToStringParser.parse(mthToString)
	}

	fun findGetters(cls: ClassNode): List<MethodRename> {
		return cls.fields.filter(FieldNode::isInstance).mapNotNull { field ->
			val mth = getFieldGetterMethod(cls, field.fieldInfo)
				?: return@mapNotNull null
			MethodRename(
				mth = mth,
				alias = getGetterAlias(field.alias),
			)
		}
	}

	private fun getFieldGetterMethod(cls: ClassNode, field: FieldInfo): MethodNode? = cls.methods.firstOrNull {
		it.returnType == field.type &&
			it.argTypes.isEmpty() &&
			it.insnsCount == 3 &&
			it.SVars.size == 2 &&
			(it.SVars[1].assignInsn as? IndexInsnNode)?.index == field
	}

	private fun getGetterAlias(fieldAlias: String): String {
		val capitalized = fieldAlias.replaceFirstChar {
			if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
		}
		return "get$capitalized"
	}

	// untested & overly complicated
	fun parseDefaultMethods(cls: ClassNode): List<MethodRename> {
		val possibleMthList = cls.methods.filter {
			it.accessFlags.isStatic() && it.accessFlags.isSynthetic() &&
				it.argTypes.run {
					size > 3 &&
						first().isObject() && first().getObject() == cls.fullName &&
						get(size - 2).isPrimitive() && get(size - 2).getPrimitiveType() == PrimitiveType.INT &&
						last().isObject() && last().getObject() == Consts.CLASS_OBJECT
				}
		}
		val insnList = possibleMthList.filter {
			val exit = it.exitBlock ?: return@filter false
			val dom = exit.idom ?: return@filter false
			if (dom.instructions.firstOrNull()?.type != InsnType.RETURN) {
				return@filter false
			}
			val dom2 = dom.idom ?: return@filter false
			dom2.instructions.firstOrNull() is InvokeNode
		}

		val remapped = insnList.mapNotNull {
			val dom2 = checkNotNull(it.exitBlock?.idom?.idom) { "No second dominator" }
			val insn = dom2.instructions.first() as InvokeNode
			cls.searchMethodByShortId(insn.callMth.shortId)?.run { it to this }
		}

		return remapped.map { (defaultMethod, originalMethod) ->
			MethodRename(
				mth = defaultMethod,
				alias = getDefaultMethodAlias(originalMethod.alias),
			)
		}
	}

	private fun getDefaultMethodAlias(alias: String): String = "$alias\$default"
}
