package kadx.core.dex.visitors.rename

import kadx.api.args.UseSourceNameAsClassNameAlias
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.core.deobf.NameMapper
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.nodes.RenameReasonAttr
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.utils.BetterName
import kadx.core.utils.StringUtils
import kadx.core.utils.exceptions.KadxException
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.ArrayList
import java.util.HashMap

/**
 * 用“源文件名”作为类别名（当调试信息里保留了 `SourceFile` 属性时）。
 *
 * **做什么**：统计每个源文件名被多少类使用；只有当重复次数低于
 * [kadx.api.KadxArgs.getSourceNameRepeatLimit] 时，才把类改名为该源文件名
 * （避免内部类/匿名类共用同一源文件导致重名冲突）。
 *
 * **为什么**：`SourceFile` 属性常保留原始文件名（如 `MainActivity.java`），
 * 用它命名比混淆后的 `a`、`b` 更可读，但必须做重名保护。
 *
 * **Kotlin 转换说明**：`when` 枚举分支保持穷尽；引用比较用 `===`；
 * 嵌套 `ClsRename` 为私有辅助类，简单 getter 改为只读属性。
 */
class SourceFileRename : AbstractVisitor() {

	override fun getName(): String = "SourceFileRename"

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		val useSourceName = root.getArgs().useSourceNameAsClassNameAlias
		if (useSourceName == UseSourceNameAsClassNameAlias.NEVER) {
			return
		}
		val repeatLimit = root.getArgs().sourceNameRepeatLimit
		if (repeatLimit <= 1) {
			return
		}

		val classes = root.getClasses()
		val aliasUseCount = HashMap<String, Int>()
		for (cls in classes) {
			aliasUseCount[cls.classInfo.shortName] = 1
		}
		val renames = ArrayList<ClsRename>()
		for (cls in classes) {
			if (cls.contains(AFlag.DONT_RENAME)) {
				continue
			}
			val alias = getAliasFromSourceFile(cls)
			if (alias != null) {
				val count = (aliasUseCount[alias] ?: 0) + 1
				aliasUseCount[alias] = count
				if (count < repeatLimit) {
					renames.add(ClsRename(cls, alias, count))
				}
			}
		}
		for (clsRename in renames) {
			val alias = clsRename.alias
			val count = aliasUseCount[alias] ?: 0
			if (count < repeatLimit) {
				applyRename(clsRename.cls, clsRename.buildAlias(), useSourceName)
			}
		}
	}

	private fun applyRename(cls: ClassNode, alias: String, useSourceName: UseSourceNameAsClassNameAlias) {
		if (cls.classInfo.hasAlias()) {
			val currentAlias = cls.alias
			val betterName = getBetterName(currentAlias, alias, useSourceName)
			if (betterName == currentAlias) {
				return
			}
		}
		cls.classInfo.changeShortName(alias)
		cls.addAttr(RenameReasonAttr(cls).append("use source file name"))
	}

	private fun getBetterName(currentName: String, sourceName: String, useSourceName: UseSourceNameAsClassNameAlias): String = when (useSourceName) {
		UseSourceNameAsClassNameAlias.ALWAYS -> sourceName
		UseSourceNameAsClassNameAlias.IF_BETTER -> BetterName.getBetterClassName(sourceName, currentName)
		UseSourceNameAsClassNameAlias.NEVER -> currentName
		else -> throw KadxRuntimeException("Unhandled strategy: $useSourceName")
	}

	private fun getAliasFromSourceFile(cls: ClassNode): String? {
		val sourceFileAttr = cls.get(KadxAttrType.SOURCE_FILE) ?: return null
		if (cls.classInfo.isInner) {
			return null
		}
		var name = sourceFileAttr.fileName
		name = StringUtils.removeSuffix(name, ".java")
		name = StringUtils.removeSuffix(name, ".kt")
		if (!NameMapper.isValidAndPrintable(name)) {
			return null
		}
		if (name == cls.name) {
			return null
		}
		return name
	}

	/** 待应用的类重命名记录：类、目标别名、同名序号（>=2 时追加后缀）。 */
	private class ClsRename(val cls: ClassNode, val alias: String, private val suffix: Int) {

		fun buildAlias(): String = if (suffix < 2) alias else alias + suffix

		override fun toString(): String = "ClsRename{$cls -> '$alias$suffix'}"
	}
}
