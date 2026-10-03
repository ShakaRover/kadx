package jadx.core.utils

import jadx.api.ICodeWriter
import jadx.api.JavaMethod
import jadx.api.impl.SimpleCodeWriter
import jadx.core.codegen.MethodGen
import jadx.core.codegen.MethodGen.FallbackOption.BLOCK_DUMP
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.IfNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.regions.SwitchRegion
import jadx.core.dex.regions.SynchronizedRegion
import jadx.core.dex.regions.TryCatchRegion
import jadx.core.dex.regions.conditions.IfRegion
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.dex.visitors.SaveCode
import jadx.core.utils.files.FileUtils
import java.awt.Color
import java.io.File
import java.util.Collections
import java.util.HashSet
import java.util.regex.Matcher

/**
 * 导出方法 CFG 为 DOT 图（graphviz 格式）。
 *
 * **用途**：调试区域划分/支配树，把方法的基本块与区域画成图。
 *
 * **Kotlin 转换说明**：实例方法保持实例；静态工具方法放 companion + `@JvmStatic`。
 */
class DotGraphUtils {

	private val dot: ICodeWriter = SimpleCodeWriter()
	private val conn: ICodeWriter = SimpleCodeWriter()

	private val useRegions: Boolean
	private val rawInsn: Boolean

	// 若存在，非区域模式下仍会绘制该区域及其子区域
	private val highlightRegion: IRegion?

	// 高亮区域处理过一次后置位，避免重复处理其子块
	private var processedHighlightRegion = false

	constructor(useRegions: Boolean, rawInsn: Boolean) : this(useRegions, rawInsn, null)

	constructor(useRegions: Boolean, rawInsn: Boolean, highlightRegion: IRegion?) {
		this.useRegions = useRegions
		this.rawInsn = rawInsn
		this.highlightRegion = highlightRegion
	}

	// 默认输出目录下该方法 CFG 的文件名
	fun getFullFile(mth: MethodNode): File = getFullFile(mth, getOutDir(mth))

	// 指定输出目录下该方法 CFG 的文件名
	fun getFullFile(mth: MethodNode, outDir: File): File {
		val fileName = StringUtils.escape(mth.methodInfo.shortId) +
			(if (useRegions) ".regions" else "") +
			(if (rawInsn) ".raw" else "") +
			".dot"
		val file = outDir.toPath()
			.resolve(mth.parentClass.classInfo.aliasFullPath + "_graphs")
			.resolve(fileName)
			.toFile()
		return FileUtils.cutFileName(file)
	}

	fun dumpToFile(mth: MethodNode) {
		val dir = getOutDir(mth)
		dumpToFile(mth, dir)
	}

	fun dumpToFile(mth: MethodNode, dir: File) {
		val graph = dumpToString(mth) ?: return
		val file = getFullFile(mth, dir)
		SaveCode.save(graph, file)
	}

	fun dumpToString(mth: MethodNode): String? {
		dot.startLine("digraph \"CFG for")
		dot.add(escape(mth.methodInfo.fullId))
		dot.add("\" {")

		var enterBlock = mth.enterBlock
		if (useRegions) {
			if (mth.region == null) {
				return null
			}
			processMethodRegion(mth)
		} else {
			var blocks = mth.basicBlocks
			if (blocks == null) {
				val insnArr = mth.instructions
				if (insnArr == null) {
					return null
				}
				val block = BlockNode(0, 0, 0)
				for (insn in insnArr) {
					if (insn != null) {
						block.instructions.add(insn)
					}
				}
				enterBlock = block
				blocks = Collections.singletonList(block)
			}
			for (block in blocks) {
				if (processedHighlightRegion && highlightRegion != null &&
					RegionUtils.isRegionContainsBlock(highlightRegion, block)
				) {
					// 高亮区域已处理过，其内部块无需再处理
					continue
				}
				processBlock(mth, block)
			}
		}

		dot.startLine("MethodNode[shape=record,label=\"{")
		dot.add(escape(mth.accessFlags.makeString(true)))
		dot.add(
			escape(
				mth.returnType.toString() + " " +
					mth.parentClass + '.' + mth.name +
					'(' + Utils.listToString(mth.allArgRegs) + ") ",
			),
		)

		val attrs = attributesString(mth)
		if (attrs.isNotEmpty()) {
			dot.add(" | ").add(attrs)
		}
		dot.add("}\"];")

		dot.startLine("MethodNode -> ").add(makeName(checkNotNull(enterBlock))).add(';')

		dot.add(conn.toString())

		dot.startLine('}')
		dot.startLine()

		return dot.finish().codeStr
	}

	private fun processMethodRegion(mth: MethodNode) {
		val regionsBlocks: MutableSet<IBlock> = HashSet(checkNotNull(mth.basicBlocks).size)
		RegionUtils.getAllRegionBlocks(checkNotNull(mth.region), regionsBlocks)
		for (handler in mth.getExceptionHandlers()) {
			val handlerRegion = handler.getHandlerRegion()
			if (handlerRegion != null) {
				RegionUtils.getAllRegionBlocks(handlerRegion, regionsBlocks)
			}
		}

		processRegion(mth, checkNotNull(mth.region), regionsBlocks)
		for (h in mth.getExceptionHandlers()) {
			if (h.getHandlerRegion() != null) {
				processRegion(mth, checkNotNull(h.getHandlerRegion()), regionsBlocks)
			}
		}

		for (block in checkNotNull(mth.basicBlocks)) {
			if (!regionsBlocks.contains(block)) {
				processBlock(mth, block, true, false)
			}
		}
	}

	private fun processRegion(mth: MethodNode, region: IContainer, regionsBlocks: MutableSet<IBlock>?) {
		if (region is IRegion) {
			dot.startLine("subgraph " + makeName(region) + " {")
			dot.startLine("color = " + getColorForRegion(region))
			dot.startLine("label = \"").add(truncateRegionName(region))
			dot.add("\";")
			dot.startLine("node [shape=record,color=blue];")

			for (c in region.subBlocks) {
				processRegion(mth, c, regionsBlocks)
			}

			dot.startLine('}')
		} else if (region is BlockNode) {
			checkAndFixFloatingBlocks(mth, region, regionsBlocks)
			processBlock(mth, region)
		} else if (region is IBlock) {
			processIBlock(mth, region)
		}
	}

	private fun getColorForRegion(region: IRegion): String = when {
		region is IfRegion -> "lightgoldenrod3"
		region is LoopRegion -> "lightpink2"
		region is SwitchRegion -> "lightsteelblue3"
		region is SynchronizedRegion -> "mediumpurple3"
		region is TryCatchRegion -> "olivedrab4"
		region.contains(AType.EXC_HANDLER) -> "orangered4"
		else -> "gray"
	}

	private fun truncateRegionName(r: IRegion): String {
		var regionName = r.toString()
		val attrs = attributesString(r)
		if (attrs.isNotEmpty()) {
			regionName += " | " + attrs
		}
		if (regionName.length > MAX_REGION_NAME_LENGTH) {
			regionName = regionName.substring(0, MAX_REGION_NAME_LENGTH)
			regionName += "..."
		}
		return regionName
	}

	/**
	 * 如果一个块不属于任何区域（floating），为了图可读性，把它临时放入前驱或后继所在的区域。
	 */
	private fun checkAndFixFloatingBlocks(mth: MethodNode, block: BlockNode, regionBlocks: MutableSet<IBlock>?) {
		if (regionBlocks == null || regionBlocks.isEmpty()) {
			return
		}
		for (floating in block.successors) {
			if (!regionBlocks.contains(floating) && floating.predecessors.size <= floating.successors.size) {
				processBlock(mth, floating, true, true)
				regionBlocks.add(floating)
			}
		}
		for (floating in block.predecessors) {
			if (!regionBlocks.contains(floating) && floating.predecessors.size > floating.successors.size) {
				processBlock(mth, floating, true, true)
				regionBlocks.add(floating)
			}
		}
	}

	private fun processBlock(mth: MethodNode, block: BlockNode) {
		processBlock(mth, block, false, false)
	}

	private fun processBlock(mth: MethodNode, block: BlockNode, error: Boolean, pseudoInRegion: Boolean) {
		if (!processedHighlightRegion && highlightRegion != null &&
			RegionUtils.isRegionContainsBlock(highlightRegion, block)
		) {
			processedHighlightRegion = true
			processRegion(mth, highlightRegion, null)
			return
		}

		val isMthStart = block.contains(AFlag.MTH_ENTER_BLOCK)
		val isMthEnd = block.contains(AFlag.MTH_EXIT_BLOCK)

		if (isMthEnd) {
			dot.startLine("subgraph { rank = sink; ")
		}

		dot.startLine(makeName(block))
		dot.add(" [shape=record,")
		if (error) {
			dot.add("color=red,")
		}
		if (pseudoInRegion) {
			dot.add("style = \"filled,dashed\"")
		} else {
			dot.add("style = filled,")
		}
		if (isMthStart || isMthEnd) {
			dot.add("fillcolor = \"#def3fd\",")
		} else {
			dot.add("fillcolor = \"#f8fafb\",")
		}
		dot.add("label=\"{")
		dot.add(block.cid.toString()).add("\\:\\ ")
		dot.add(InsnUtils.formatOffset(block.startOffset))
		if (pseudoInRegion) {
			dot.add("\\nNOT IN ANY REGION")
		}

		val attrs = attributesString(block)
		if (attrs.isNotEmpty()) {
			dot.add('|').add(attrs)
		}

		if (PRINT_DOMINATORS_INFO) {
			dot.add('|')
			dot.startLine("doms: ").add(escape(block.doms))
			dot.startLine("\\lidom: ").add(escape(block.idom))
			dot.startLine("\\lpost-doms: ").add(escape(block.postDoms))
			dot.startLine("\\lpost-idom: ").add(escape(block.iPostDom))
			dot.startLine("\\ldom-f: ").add(escape(block.domFrontier))
			dot.startLine("\\ldoms-on: ").add(escape(Utils.listToString(block.dominatesOn)))
			dot.startLine("\\l")
		}
		val insns = insertInsns(mth, block)
		if (insns.isNotEmpty()) {
			dot.add('|').add(insns)
		}
		dot.add("}\"];")

		if (isMthEnd) {
			dot.add("};")
		}

		var falsePath: BlockNode? = null
		val lastInsn = BlockUtils.getLastInsn(block)
		if (lastInsn != null && lastInsn.type == InsnType.IF) {
			falsePath = (lastInsn as IfNode).getElseBlock()
		}
		for (next in block.successors) {
			val style = if (next === falsePath) "[style=dashed]" else ""
			addEdge(block, next, style)
		}

		if (PRINT_DOMINATORS) {
			for (c in block.dominatesOn) {
				conn.startLine(block.cid.toString() + " -> " + c.cid + "[color=green];")
			}
			for (dom in BlockUtils.bitSetToBlocks(mth, checkNotNull(block.domFrontier))) {
				conn.startLine("f_" + block.cid + " -> f_" + dom.cid + "[color=blue];")
			}
		}
	}

	private fun processIBlock(mth: MethodNode, block: IBlock) {
		processIBlock(mth, block, false)
	}

	private fun processIBlock(mth: MethodNode, block: IBlock, error: Boolean) {
		val attrs = attributesString(block)
		dot.startLine(makeName(block))
		dot.add(" [shape=record,")
		if (error) {
			dot.add("color=red,")
		}
		dot.add("label=\"{")
		if (attrs.isNotEmpty()) {
			dot.add(attrs)
		}
		val insns = insertInsns(mth, block)
		if (insns.isNotEmpty()) {
			dot.add('|').add(insns)
		}
		dot.add("}\"];")
	}

	private fun addEdge(from: BlockNode, to: BlockNode, style: String) {
		conn.startLine(makeName(from)).add(" -> ").add(makeName(to))
		conn.add(style)
		conn.add(';')
	}

	private fun attributesString(block: IAttributeNode): String {
		val attrs = StringBuilder()
		for (attr in block.getAttributesStringsList()) {
			attrs.append(escape(attr)).append(NL)
		}
		return attrs.toString()
	}

	private fun makeName(c: IContainer): String = if (c is BlockNode) {
		"Node_" + c.cid
	} else if (c is IBlock) {
		"Node_" + c.javaClass.simpleName + '_' + c.hashCode()
	} else {
		"cluster_" + c.javaClass.simpleName + '_' + c.hashCode()
	}

	private fun insertInsns(mth: MethodNode, block: IBlock): String {
		if (rawInsn) {
			val sb = StringBuilder()
			for (insn in block.instructions) {
				sb.append(escape(insn)).append(NL)
			}
			return sb.toString()
		} else {
			val code = SimpleCodeWriter()
			val instructions = block.instructions
			MethodGen.addFallbackInsns(code, mth, instructions.toTypedArray(), BLOCK_DUMP)
			// 这里的指令会多经过一次反转义
			var str = escape(code.newLine().toString())
			if (str.startsWith(NL)) {
				str = str.substring(NL.length)
			}
			return str
		}
	}

	companion object {
		private const val NL = "\\l"
		private val NLQR: String = Matcher.quoteReplacement(NL)
		private const val PRINT_DOMINATORS = false
		private const val PRINT_DOMINATORS_INFO = false
		private const val MAX_REGION_NAME_LENGTH = 2000

		// 方法 CFG 的默认输出目录
		fun getOutDir(mth: MethodNode): File = checkNotNull(mth.root().getArgs().outDir)

		fun escape(obj: Any?): String {
			if (obj == null) {
				return "null"
			}
			return escape(obj.toString())
		}

		fun escape(string: String): String = escape(string, NLQR)

		fun escape(string: String, newline: String): String = string
			.replace("\\", "") // TODO replace \"
			.replace("/", "\\/")
			.replace(">", "\\>").replace("<", "\\<")
			.replace("{", "\\{").replace("}", "\\}")
			.replace("\"", "\\\"")
			.replace("-", "\\-")
			.replace("|", "\\|")
			.replace(Regex("\\R")) { newline }

		fun classFormatName(cls: ClassNode, longName: Boolean): String = classFormatName(cls.classInfo, longName)

		fun classFormatName(cls: ClassInfo, longName: Boolean): String = if (longName) cls.aliasFullName else cls.aliasShortName

		fun methodFormatName(javaMethod: JavaMethod, longName: Boolean): String = methodFormatName(javaMethod.getMethodNode(), longName)

		fun methodFormatName(methodNode: MethodNode, longName: Boolean): String {
			if (longName) {
				val parentClass = methodNode.parentClass
				val argTypes = methodNode.argTypes
				val retType = methodNode.returnType
				return classFormatName(parentClass, true) + "." + methodFormatName(methodNode, false) +
					'(' + Utils.listToString(argTypes, ", ") { e -> argTypeFormatName(e, parentClass, true) } + "):" +
					argTypeFormatName(retType, parentClass, true)
			}
			return methodNode.alias
		}

		fun unresolvedMethodFormatName(mthInfo: MethodInfo, longName: Boolean): String {
			val name = mthInfo.name
			if (longName) {
				val className = mthInfo.declClass.fullName
				val returnName = mthInfo.returnType.toString()
				val argStr = Utils.listToString(mthInfo.argumentsTypes) { it.toString() }
				return "$className.$name($argStr):$returnName"
			}
			return name
		}

		fun interfaceFormatName(iface: ArgType, cls: ClassNode, longName: Boolean): String {
			val ifaceInfo = ClassInfo.fromType(cls.root(), iface)
			return if (longName) ifaceInfo.aliasFullName else ifaceInfo.aliasShortName
		}

		fun argTypeFormatName(arg: ArgType, cls: ClassNode, longName: Boolean): String {
			if (arg.isObject() && !arg.isGenericType()) {
				val superCls = cls.root().resolveClass(arg)
				if (superCls != null) {
					return classFormatName(superCls, longName)
				}
			}
			return arg.toString()
		}

		fun formatColor(color: Color): String = String.format("\"#%02x%02x%02x\"", color.red, color.green, color.blue)

		fun toDotNodeName(fullName: String): String = fullName.replace("<", "\\<").replace(">", "\\>")
	}
}
