package kadx.core.dex.visitors.rename

import kadx.api.data.CodeRefType
import kadx.api.data.ICodeData
import kadx.api.data.ICodeRename
import kadx.api.data.IJavaCodeRef
import kadx.api.data.IJavaNodeRef
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.InitCodeVariables
import kadx.core.dex.visitors.KadxVisitor
import kadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor
import kadx.core.utils.exceptions.KadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.HashMap

/**
 * 应用“代码级重命名”（方法参数名、局部变量名）。
 *
 * **做什么**：把用户保存的代码重命名记录按声明类分组缓存，在遍历每个类时，
 * 找到对应的方法，再按 [IJavaCodeRef] 的附件类型（方法参数 / 局部变量）
 * 修改变量名。
 *
 * **为什么放在调试信息之后**：变量名来自调试信息（[DebugInfoApplyVisitor]）
 * 与代码变量初始化（[InitCodeVariables]），必须等它们都跑完再改名，否则会被覆盖。
 *
 * **Kotlin 转换说明**：原 Java `>> 16`/`& 0xFFFF` 改位运算关键字 `shr`/`and`；
 * 引用比较用 `===`；stream 分组改为普通循环 + HashMap。
 */
@KadxVisitor(
	name = "ApplyCodeRename",
	desc = "Rename variables and other entities in methods",
	runAfter = [
		InitCodeVariables::class,
		DebugInfoApplyVisitor::class,
	],
)
class CodeRenameVisitor : AbstractVisitor() {

	private var clsRenamesMap: Map<String, List<ICodeRename>>? = null

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		updateRenamesMap(root.getArgs().codeData)
		root.registerCodeDataUpdateListener { updateRenamesMap(it) }
	}

	override fun visit(cls: ClassNode): Boolean {
		val renames = getRenames(cls)
		if (renames.isNotEmpty()) {
			applyRenames(cls, renames)
		}
		for (innerCls in cls.innerClasses) {
			visit(innerCls)
		}
		return false
	}

	private fun getRenames(cls: ClassNode): List<ICodeRename> {
		val map = clsRenamesMap ?: return emptyList()
		return map[cls.classInfo.rawName] ?: emptyList()
	}

	private fun updateRenamesMap(data: ICodeData?) {
		if (data == null) {
			this.clsRenamesMap = emptyMap()
		} else {
			val map = HashMap<String, MutableList<ICodeRename>>()
			for (r in data.getRenames()) {
				if (r.getCodeRef() != null) {
					map.computeIfAbsent(r.getNodeRef().declaringClass) { ArrayList() }.add(r)
				}
			}
			this.clsRenamesMap = map
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CodeRenameVisitor::class.java)

		private fun applyRenames(cls: ClassNode, renames: List<ICodeRename>) {
			for (rename in renames) {
				val nodeRef = rename.getNodeRef()
				if (nodeRef.getType() == IJavaNodeRef.RefType.METHOD) {
					val methodNode = cls.searchMethodByShortId(checkNotNull(nodeRef.getShortId()))
					if (methodNode == null) {
						LOG.warn("Method reference not found: {}", nodeRef)
					} else {
						val codeRef = rename.getCodeRef()
						if (codeRef != null) {
							processRename(methodNode, codeRef, rename)
						}
					}
				}
			}
		}

		private fun processRename(mth: MethodNode, codeRef: IJavaCodeRef, rename: ICodeRename) {
			when (codeRef.getAttachType()) {
				CodeRefType.MTH_ARG -> {
					val argRegs = mth.argRegs
					val argNum = codeRef.getIndex()
					if (argNum < argRegs.size) {
						checkNotNull(argRegs[argNum].sVar).codeVar.name = rename.getNewName()
					} else {
						LOG.warn("Incorrect method arg ref {}, should be less than {}", argNum, argRegs.size)
					}
				}

				CodeRefType.VAR -> {
					val regNum = codeRef.getIndex() shr 16
					val ssaVer = codeRef.getIndex() and 0xFFFF
					for (ssaVar in mth.SVars) {
						if (ssaVar.regNum == regNum && ssaVar.version == ssaVer) {
							ssaVar.codeVar.name = rename.getNewName()
							return
						}
					}
					LOG.warn("Can't find variable ref by {}_{}", regNum, ssaVer)
				}

				else -> LOG.warn("Rename code ref type {} not yet supported", codeRef.getAttachType())
			}
		}
	}
}
