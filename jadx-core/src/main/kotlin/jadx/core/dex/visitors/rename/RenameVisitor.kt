package jadx.core.dex.visitors.rename

import jadx.api.JadxArgs
import jadx.api.deobf.IAliasProvider
import jadx.core.Consts
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.MethodOverrideAttr
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.utils.StringUtils
import java.util.ArrayList
import java.util.HashSet
import java.util.regex.Pattern

/**
 * 统一的重命名 Pass：应用用户重命名，并做名字合法性/唯一性检查。
 *
 * **做什么**：
 * 1. [process] 先调用 [UserRenames] 应用用户指定的重命名；
 * 2. [checkNames] 按重命名选项（valid/printable/case）检查类名、字段名、方法名、包名，
 *    发现非法名、不可打印字符、同名冲突时用 [IAliasProvider]（反混淆器）改名；
 * 3. 处理大小写不敏感文件系统上的包/类文件名冲突；
 * 4. 字段名与根包名冲突时改名。
 *
 * **为什么需要**：反编译输出必须能通过 javac 编译，因此名字必须是合法且唯一的
 * Java 标识符；这一步是输出正确性的最后保障。
 *
 * **Kotlin 转换说明**：`ANONYMOUS_CLASS_PATTERN` 等静态成员放 companion；
 * 引用比较用 `===`，字符串比较用 `==`（对应 Java `equals`）；保持普通循环。
 */
class RenameVisitor : AbstractVisitor() {

	override fun init(root: RootNode) {
		val inputFiles = root.getArgs().inputFiles
		if (inputFiles.isEmpty()) {
			return
		}
		process(root)
		root.registerCodeDataUpdateListener { process(root) }
	}

	private fun process(root: RootNode) {
		UserRenames.apply(root)
		checkNames(root)
	}

	override fun getName(): String = "RenameVisitor"

	companion object {
		private val ANONYMOUS_CLASS_PATTERN: Pattern = Pattern.compile("^\\d+$")

		private fun checkNames(root: RootNode) {
			val args = root.getArgs()
			if (args.renameFlags.isEmpty()) {
				return
			}

			val aliasProvider = args.aliasProvider

			val classes = root.getClasses(true)
			for (cls in classes) {
				checkClassName(aliasProvider, cls, args)
				checkFields(aliasProvider, cls, args)
				checkMethods(aliasProvider, cls, args)
			}
			var pkgUpdated = false
			for (pkg in root.getPackages()) {
				pkgUpdated = checkPackage(args, aliasProvider, pkg) || pkgUpdated
			}
			if (!args.isFsCaseSensitive && args.isRenameCaseSensitive) {
				// 在大小写不敏感的文件系统上检查包目录冲突
				val pkgPaths = HashSet<String>()
				for (pkg in root.getPackages()) {
					val pkgPath = pkg.getAliasPkgInfo().fullName.lowercase()
					if (!pkgPaths.add(pkgPath)) {
						pkg.setLeafAlias(checkNotNull(aliasProvider.forPackage(pkg)), false)
						pkgUpdated = true
						// 验证新名字也不再冲突
						if (!pkgPaths.add(pkg.getAliasPkgInfo().fullName.lowercase())) {
							pkg.setLeafAlias(checkNotNull(aliasProvider.forPackage(pkg)), false)
						}
					}
				}
			}
			if (pkgUpdated) {
				root.runPackagesUpdate()
			}
			if (!args.isFsCaseSensitive && args.isRenameCaseSensitive) {
				// 检查类文件冲突（在包重命名之后）
				val clsFullPaths = HashSet<String>(classes.size)
				for (cls in classes) {
					val clsInfo = cls.classInfo
					if (!clsFullPaths.add(clsInfo.aliasFullPath.lowercase())) {
						clsInfo.changeShortName(aliasProvider.forClass(cls))
						cls.addAttr(RenameReasonAttr(cls).append("case insensitive filesystem"))
						// 验证新名字也不再冲突
						if (!clsFullPaths.add(clsInfo.aliasFullPath.lowercase())) {
							clsInfo.changeShortName(aliasProvider.forClass(cls))
						}
					}
				}
			}
			processRootPackages(aliasProvider, root, classes)
		}

		private fun checkClassName(aliasProvider: IAliasProvider, cls: ClassNode, args: JadxArgs) {
			if (cls.contains(AFlag.DONT_RENAME)) {
				return
			}
			val classInfo = cls.classInfo
			val clsName = classInfo.aliasShortName

			val newShortName = fixClsShortName(args, clsName)
			if (newShortName == null) {
				// 重命名失败，用反混淆器
				cls.rename(checkNotNull(aliasProvider.forClass(cls)))
				cls.addAttr(RenameReasonAttr(cls).notPrintable())
				return
			}
			if (newShortName != clsName) {
				classInfo.changeShortName(newShortName)
				cls.addAttr(RenameReasonAttr(cls).append("invalid class name"))
			}
			if (classInfo.isInner && args.isRenameValid) {
				// 检查内部类名字
				var parentClass = classInfo.parentClass
				while (parentClass != null) {
					if (parentClass.aliasShortName == newShortName) {
						cls.rename(checkNotNull(aliasProvider.forClass(cls)))
						cls.addAttr(RenameReasonAttr(cls).append("collision with other inner class name"))
						break
					}
					parentClass = parentClass.parentClass
				}
			}
		}

		private fun checkPackage(args: JadxArgs, aliasProvider: IAliasProvider, pkg: PackageNode): Boolean {
			if (args.isRenameValid && pkg.getAliasPkgInfo().isDefaultPkg()) {
				pkg.setFullAlias(Consts.DEFAULT_PACKAGE_NAME, false)
				return true
			}
			val pkgName = pkg.getAliasPkgInfo().name
			val notValid = args.isRenameValid && !NameMapper.isValidIdentifier(pkgName)
			val notPrintable = args.isRenamePrintable && !NameMapper.isAllCharsPrintable(pkgName)
			if (notValid || notPrintable) {
				pkg.setLeafAlias(checkNotNull(aliasProvider.forPackage(pkg)), false)
				return true
			}
			return false
		}

		private fun fixClsShortName(args: JadxArgs, clsName: String): String? {
			if (StringUtils.isEmpty(clsName)) {
				return null
			}
			val renameValid = args.isRenameValid
			if (renameValid) {
				if (ANONYMOUS_CLASS_PATTERN.matcher(clsName).matches()) {
					return Consts.ANONYMOUS_CLASS_PREFIX + NameMapper.removeInvalidCharsMiddle(clsName)
				}

				val firstChar = clsName[0]
				if (firstChar == '$' || Character.isDigit(firstChar)) {
					return 'C' + NameMapper.removeInvalidCharsMiddle(clsName)
				}
			}
			var cleanClsName = if (args.isRenamePrintable) {
				NameMapper.removeNonPrintableCharacters(clsName)
			} else {
				clsName
			}
			if (cleanClsName.isEmpty()) {
				return null
			}
			if (renameValid) {
				cleanClsName = NameMapper.removeInvalidChars(clsName, "C")
				if (!NameMapper.isValidIdentifier(cleanClsName)) {
					return 'C' + cleanClsName
				}
			}
			return cleanClsName
		}

		private fun checkFields(aliasProvider: IAliasProvider, cls: ClassNode, args: JadxArgs) {
			val names = HashSet<String>()
			for (field in cls.fields) {
				val fieldInfo = field.getFieldInfo()
				val fieldName = fieldInfo.alias
				val notUnique = !names.add(fieldName)
				val notValid = args.isRenameValid && !NameMapper.isValidIdentifier(fieldName)
				val notPrintable = args.isRenamePrintable && !NameMapper.isAllCharsPrintable(fieldName)
				if (notUnique || notValid || notPrintable) {
					field.rename(checkNotNull(aliasProvider.forField(field)))
					field.addAttr(RenameReasonAttr(field, notValid, notPrintable))
					if (notUnique) {
						field.addAttr(RenameReasonAttr(field).append("collision with other field name"))
					}
				}
			}
		}

		private fun checkMethods(aliasProvider: IAliasProvider, cls: ClassNode, args: JadxArgs) {
			val methods = ArrayList<MethodNode>(cls.methods.size)
			for (method in cls.methods) {
				if (!method.accessFlags.isConstructor()) {
					methods.add(method)
				}
			}
			for (mth in methods) {
				val alias = mth.alias
				val notValid = args.isRenameValid && !NameMapper.isValidIdentifier(alias)
				val notPrintable = args.isRenamePrintable && !NameMapper.isAllCharsPrintable(alias)
				if (notValid || notPrintable) {
					mth.rename(checkNotNull(aliasProvider.forMethod(mth)))
					mth.addAttr(RenameReasonAttr(mth, notValid, notPrintable))
				}
			}
			// 重命名签名相同的方法
			if (args.isRenameValid) {
				val names = HashSet<String>(methods.size)
				for (mth in methods) {
					val signature = mth.methodInfo.makeSignature(true, false)
					if (!names.add(signature) && canRename(mth)) {
						mth.rename(checkNotNull(aliasProvider.forMethod(mth)))
						mth.addAttr(RenameReasonAttr("collision with other method in class"))
					}
				}
			}
		}

		private fun canRename(mth: MethodNode): Boolean {
			if (mth.contains(AFlag.DONT_RENAME)) {
				return false
			}
			val overrideAttr = mth.get(AType.METHOD_OVERRIDE)
			if (overrideAttr != null) {
				for (relatedMth in overrideAttr.relatedMthNodes) {
					if (relatedMth !== mth && mth.parentClass == relatedMth.parentClass) {
						// 同类里存在关联方法（通常是桥接方法）时放弃重命名
						return false
					}
				}
			}
			return true
		}

		private fun processRootPackages(aliasProvider: IAliasProvider, root: RootNode, classes: List<ClassNode>) {
			val rootPkgs = collectRootPkgs(root)
			root.cacheStorage.rootPkgs = rootPkgs

			if (root.getArgs().isRenameValid) {
				// 字段名与根包名冲突时重命名
				for (cls in classes) {
					for (field in cls.fields) {
						if (rootPkgs.contains(field.alias)) {
							field.rename(checkNotNull(aliasProvider.forField(field)))
							field.addAttr(RenameReasonAttr("collision with root package name"))
						}
					}
				}
			}
		}

		private fun collectRootPkgs(root: RootNode): Set<String> {
			val rootPkgs = HashSet<String>()
			for (pkg in root.getPackages()) {
				if (pkg.isRoot()) {
					rootPkgs.add(pkg.getPkgInfo().name)
				}
			}
			return rootPkgs
		}
	}
}
