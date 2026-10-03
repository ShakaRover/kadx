package jadx.core.dex.visitors.rename

import jadx.api.data.ICodeRename
import jadx.api.data.IJavaNodeRef
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.HashMap

/**
 * 应用“用户重命名”（来自 jadx 的代码数据 / mappings 文件）。
 *
 * **做什么**：读取 [jadx.api.data.ICodeData] 里的重命名记录，按“声明类”分组，
 * 然后对类、字段、方法、包逐一改名。带代码引用（`getCodeRef() != null`）的记录
 * 由 [CodeRenameVisitor] 处理，这里只处理类/字段/方法/包级别。
 *
 * **为什么按类分组**：同一条重命名记录需要先解析到具体的 [ClassNode]，
 * 分组后每个类只解析一次，避免重复查找。
 *
 * **Kotlin 转换说明**：原 Java 用 stream 分组，Kotlin 改为普通循环 + HashMap；
 * 引用比较用 `===`；`ArgType.object(...)` 因 `object` 是关键字写作 `ArgType.`object``。
 */
class UserRenames {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(UserRenames::class.java)

		/** 应用所有用户重命名（类/字段/方法/包）。 */
		fun apply(root: RootNode) {
			val codeData = root.getArgs().codeData
			if (codeData == null || codeData.getRenames().isEmpty()) {
				return
			}
			val infoStorage = root.infoStorage
			val renamesByCls = HashMap<String, MutableList<ICodeRename>>()
			for (rename in codeData.getRenames()) {
				if (rename.getCodeRef() == null && rename.getNodeRef().getType() != IJavaNodeRef.RefType.PKG) {
					renamesByCls.computeIfAbsent(rename.getNodeRef().getDeclaringClass()) { ArrayList() }.add(rename)
				}
			}
			for ((clsRawName, renames) in renamesByCls) {
				val clsInfo = infoStorage.getCls(ArgType.`object`(clsRawName))
				if (clsInfo != null) {
					val cls = root.resolveClass(clsInfo)
					if (cls != null) {
						for (rename in renames) {
							applyRename(cls, rename)
						}
						continue
					}
				}
				LOG.warn("Class info with reference '{}' not found", clsRawName)
			}
			applyPkgRenames(root, codeData.getRenames())
		}

		private fun applyRename(cls: ClassNode, rename: ICodeRename) {
			val nodeRef = rename.getNodeRef()
			when (nodeRef.getType()) {
				IJavaNodeRef.RefType.CLASS -> cls.rename(rename.getNewName())

				IJavaNodeRef.RefType.FIELD -> {
					val fieldNode = cls.searchFieldByShortId(checkNotNull(nodeRef.getShortId()))
					if (fieldNode == null) {
						val fieldName = StringUtils.getPrefix(checkNotNull(nodeRef.getShortId()), ":")
						val fieldSign = StringBuilder()
						for (f in cls.fields) {
							if (f.getFieldInfo().name == fieldName) {
								fieldSign.append(f.getFieldInfo().shortId)
							}
						}
						LOG.warn("Field reference not found: {}. Fields with same name: {}", nodeRef, fieldSign)
					} else {
						fieldNode.rename(rename.getNewName())
					}
				}

				IJavaNodeRef.RefType.METHOD -> {
					val mth = cls.searchMethodByShortId(checkNotNull(nodeRef.getShortId()))
					if (mth == null) {
						LOG.warn("Method reference not found: {}", nodeRef)
					} else {
						val codeRef = rename.getCodeRef()
						if (codeRef == null) {
							mth.rename(rename.getNewName())
						}
					}
				}

				else -> {}
			}
		}

		private fun applyPkgRenames(root: RootNode, renames: List<ICodeRename>) {
			for (pkgRename in renames) {
				if (pkgRename.getNodeRef().getType() == IJavaNodeRef.RefType.PKG) {
					val pkgFullName = pkgRename.getNodeRef().getDeclaringClass()
					val pkgNode = root.resolvePackage(pkgFullName)
					if (pkgNode == null) {
						LOG.warn("Package for rename not found: {}", pkgFullName)
					} else {
						pkgNode.rename(pkgRename.getNewName())
					}
				}
			}
		}
	}
}
