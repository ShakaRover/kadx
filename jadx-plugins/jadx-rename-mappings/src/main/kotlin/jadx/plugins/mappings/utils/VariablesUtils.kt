package jadx.plugins.mappings.utils

import jadx.api.ICodeInfo
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.api.metadata.annotations.NodeDeclareRef
import jadx.api.metadata.annotations.VarNode
import jadx.api.utils.CodeUtils
import jadx.core.dex.nodes.MethodNode
import org.slf4j.LoggerFactory

/**
 * 收集方法内局部变量在代码中的位置区间。
 *
 * **背景**：[collect] 用 [CodeVisitor] 向下搜索方法的代码元数据，按 InsnCodeOffset /
 * NodeDeclareRef 注解重建每个变量的起止操作码索引（VarInfo），供映射导出定位变量。
 */
public object VariablesUtils {

	private val LOG = LoggerFactory.getLogger(VariablesUtils::class.java)

	/** 单个局部变量的位置信息：[var] + 起止操作码索引 */
	public class VarInfo(val `var`: VarNode, val startOpIdx: Int) {
		var endOpIdx: Int = startOpIdx
	}

	public fun collect(mth: MethodNode): List<VarInfo> {
		val codeInfo = mth.getTopParentClass().getCode()
		val mthDefPos = mth.getDefPosition()
		val mthLineEndPos = CodeUtils.getLineEndForPos(codeInfo.getCodeStr(), mthDefPos)
		val codeVisitor = CodeVisitor(mth)
		codeInfo.getCodeMetadata().searchDown(mthLineEndPos) { pos, ann -> codeVisitor.process(pos, ann) }
		return codeVisitor.vars
	}

	private class CodeVisitor(private val mth: MethodNode) {
		val vars = ArrayList<VarInfo>()
		var lastOffset = -1

		fun process(pos: Int?, ann: ICodeAnnotation): Boolean? {
			if (ann is InsnCodeOffset) {
				lastOffset = ann.getOffset()
			}
			if (ann is NodeDeclareRef) {
				val declRef = ann.getNode()
				if (declRef is VarNode) {
					if (declRef.getMth() !== mth) { // 已经越过当前方法、进入其他方法时停止
						if (vars.isNotEmpty()) {
							vars[vars.size - 1].endOpIdx = declRef.getDefPosition() - 1
						}
						return true
					}
					if (lastOffset != -1) {
						if (vars.isNotEmpty()) {
							vars[vars.size - 1].endOpIdx = lastOffset - 1
						}
						vars.add(VarInfo(declRef, lastOffset))
					} else {
						LOG.warn(
							"Local variable not present in bytecode, skipping: {}#{}",
							mth.getMethodInfo().rawFullId,
							declRef.getName(),
						)
					}
					lastOffset = -1
				}
			}
			return null
		}
	}
}
