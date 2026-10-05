package kadx.core.dex.visitors

import kadx.api.data.CodeRefType
import kadx.api.data.ICodeComment
import kadx.api.data.ICodeData
import kadx.api.data.IJavaCodeRef
import kadx.api.data.IJavaNodeRef
import kadx.core.codegen.utils.CodeComment
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.IAttributeNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.exceptions.KadxException
import kadx.core.utils.exceptions.KadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 用户注释挂载访问者。
 *
 * **做什么**：读取用户在 kadx 中保存的代码注释（[ICodeData]），按“声明类名”分组后，
 * 把注释挂到对应的类、字段、方法或方法内指令上（[AType.CODE_COMMENTS]）。
 *
 * **为什么**：注释是用户手工维护的信息，需要在每次反编译时重新绑定到新生成的节点上；
 * 通过 `registerCodeDataUpdateListener` 监听注释数据变化，做到热更新。
 */
@KadxVisitor(
	name = "AttachComments",
	desc = "Attach user code comments",
	runBefore = [ProcessInstructionsVisitor::class],
)
class AttachCommentsVisitor : AbstractVisitor() {

	/** 按类名分组的注释；init 之前为 null，视为无注释。 */
	private var clsCommentsMap: Map<String, List<ICodeComment>>? = null

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		updateCommentsData(root.getArgs().codeData)
		root.registerCodeDataUpdateListener { data -> updateCommentsData(data) }
	}

	override fun visit(cls: ClassNode): Boolean {
		val clsComments = getCommentsData(cls)
		if (clsComments.isNotEmpty()) {
			applyComments(cls, clsComments)
		}
		for (inner in cls.innerClasses) {
			visit(inner)
		}
		return false
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(AttachCommentsVisitor::class.java)

		private fun applyComments(cls: ClassNode, clsComments: List<ICodeComment>) {
			for (comment in clsComments) {
				val nodeRef = comment.getNodeRef()
				when (nodeRef.getType()) {
					IJavaNodeRef.RefType.CLASS -> addComment(cls, comment)

					IJavaNodeRef.RefType.FIELD -> {
						val fieldNode = cls.searchFieldByShortId(checkNotNull(nodeRef.getShortId()))
						if (fieldNode == null) {
							LOG.warn("Field reference not found: {}", nodeRef)
						} else {
							addComment(fieldNode, comment)
						}
					}

					IJavaNodeRef.RefType.METHOD -> {
						val methodNode = cls.searchMethodByShortId(checkNotNull(nodeRef.getShortId()))
						if (methodNode == null) {
							LOG.warn("Method reference not found: {}", nodeRef)
						} else {
							val codeRef = comment.getCodeRef()
							if (codeRef == null) {
								addComment(methodNode, comment)
							} else {
								processCustomAttach(methodNode, codeRef, comment)
							}
						}
					}

					else -> {}
				}
			}
		}

		private fun getInsnByOffset(mth: MethodNode, offset: Int): InsnNode? = try {
			checkNotNull(mth.instructions)[offset]
		} catch (e: Exception) {
			LOG.warn("Insn reference not found in: {} with offset: {}", mth, offset)
			null
		}

		private fun processCustomAttach(mth: MethodNode, codeRef: IJavaCodeRef, comment: ICodeComment) {
			val attachType = codeRef.getAttachType()
			when (attachType) {
				CodeRefType.INSN -> {
					val insn = getInsnByOffset(mth, codeRef.getIndex())
					addComment(insn, comment)
				}

				else -> throw KadxRuntimeException("Unexpected attach type: $attachType")
			}
		}

		private fun addComment(node: IAttributeNode?, comment: ICodeComment) {
			if (node == null) {
				return
			}
			node.addAttr(AType.CODE_COMMENTS, CodeComment(comment))
		}
	}

	private fun getCommentsData(cls: ClassNode): List<ICodeComment> {
		val map = clsCommentsMap ?: return emptyList()
		return map[cls.classInfo.rawName] ?: emptyList()
	}

	private fun updateCommentsData(data: ICodeData?) {
		if (data == null) {
			clsCommentsMap = emptyMap()
			return
		}
		// 等价于 Java 的 Collectors.groupingBy(nodeRef.declaringClass)
		val map = LinkedHashMap<String, MutableList<ICodeComment>>()
		for (comment in data.getComments()) {
			val key = comment.getNodeRef().declaringClass
			map.getOrPut(key) { ArrayList() }.add(comment)
		}
		clsCommentsMap = map
	}
}
