@file:Suppress("ktlint:standard:property-naming")

package jadx.core.utils

import jadx.api.ICodeWriter
import jadx.api.impl.SimpleCodeWriter
import jadx.core.codegen.ConditionGen
import jadx.core.codegen.InsnGen
import jadx.core.codegen.MethodGen
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.attributes.nodes.MethodOverrideAttr
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IBlock
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.regions.Region
import jadx.core.dex.regions.conditions.IfCondition
import jadx.core.dex.regions.loops.LoopRegion
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.DotGraphVisitor
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.dex.visitors.regions.DepthRegionTraversal
import jadx.core.dex.visitors.regions.TracedRegionVisitor
import jadx.core.utils.exceptions.CodegenException
import jadx.core.utils.exceptions.JadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap

/**
 * 仅用于调试的工具集（CheckStyle 会拒绝在正式代码中调用）。
 *
 * **用途**：导出 CFG/区域图、打印区域与指令、统计各阶段耗时等。
 */
object DebugUtils {
	private val LOG: Logger = LoggerFactory.getLogger(DebugUtils::class.java)

	@JvmField
	val TEST_MTH_FILTER: (MethodNode) -> Boolean = { mth -> mth.getName() == "test" }

	@JvmStatic
	fun dump(mth: MethodNode) {
		dump(mth, "dump")
	}

	@JvmStatic
	fun dumpRaw(mth: MethodNode, desc: String, dumpCondition: (MethodNode) -> Boolean) {
		if (dumpCondition(mth)) {
			dumpRaw(mth, desc)
		}
	}

	@JvmStatic
	fun dumpRawTest(mth: MethodNode, desc: String) {
		dumpRaw(mth, desc, TEST_MTH_FILTER)
	}

	@JvmStatic
	fun dumpRaw(mth: MethodNode, desc: String) {
		val out = File("test-graph-$desc-tmp")
		DotGraphVisitor.dumpRaw().save(out, mth)
	}

	@JvmStatic
	fun dumpRawVisitor(desc: String): IDexTreeVisitor = object : AbstractVisitor() {
		@Throws(JadxException::class)
		override fun visit(mth: MethodNode) {
			dumpRaw(mth, desc)
		}
	}

	@JvmStatic
	fun dumpRawVisitor(desc: String, filter: (MethodNode) -> Boolean): IDexTreeVisitor = object : AbstractVisitor() {
		override fun visit(mth: MethodNode) {
			if (filter(mth)) {
				dumpRaw(mth, desc)
			}
		}
	}

	@JvmStatic
	fun dumpRawTestVisitor(desc: String): IDexTreeVisitor = dumpRawVisitor(desc, TEST_MTH_FILTER)

	@JvmStatic
	fun dump(mth: MethodNode, desc: String) {
		val out = File("test-graph-$desc-tmp")
		DotGraphVisitor.dump().save(out, mth)
		DotGraphVisitor.dumpRaw().save(out, mth)
		DotGraphVisitor.dumpRegions().save(out, mth)
	}

	@JvmStatic
	fun printRegionsWithBlock(mth: MethodNode, block: BlockNode) {
		val regions: MutableSet<IRegion> = LinkedHashSet()
		DepthRegionTraversal.traverse(
			mth,
			object : TracedRegionVisitor() {
				override fun processBlockTraced(mth: MethodNode, container: IBlock, currentRegion: IRegion) {
					if (block == container) {
						regions.add(currentRegion)
					}
				}
			},
		)
		LOG.debug(" Found block: {} in regions: {}", block, regions)
	}

	@JvmStatic
	fun printRegionsVisitor(): IDexTreeVisitor = object : AbstractVisitor() {
		@Throws(JadxException::class)
		override fun visit(mth: MethodNode) {
			printRegions(mth, true)
		}
	}

	@JvmStatic
	fun printRegions(mth: MethodNode) {
		printRegions(mth, false)
	}

	@JvmStatic
	fun printRegions(mth: MethodNode, printInsns: Boolean) {
		val mthRegion = mth.region
		if (mthRegion == null) {
			return
		}
		printRegion(mth, mthRegion, printInsns)
	}

	@JvmStatic
	fun printRegion(mth: MethodNode, region: IRegion, printInsns: Boolean) {
		val cw = SimpleCodeWriter()
		cw.startLine('|').add(mth.toString())
		printRegion(mth, region, cw, "|  ", printInsns)
		LOG.debug("{}{}", '\n', cw.finish().getCodeStr())
	}

	private fun printRegion(mth: MethodNode, region: IRegion, cw: ICodeWriter, indent0: String, printInsns: Boolean) {
		var indent = indent0
		printWithAttributes(cw, indent, region.toString(), region)
		indent += "|  "
		printRegionSpecificInfo(cw, indent, mth, region, printInsns)
		for (container in region.getSubBlocks()) {
			if (container is IRegion) {
				printRegion(mth, container, cw, indent, printInsns)
			} else {
				printWithAttributes(cw, indent, container.toString(), container)
				if (printInsns && container is IBlock) {
					printInsns(mth, cw, indent, container)
				}
			}
		}
	}

	private fun printRegionSpecificInfo(
		cw: ICodeWriter,
		indent: String,
		mth: MethodNode,
		region: IRegion,
		printInsns: Boolean,
	) {
		if (region is LoopRegion) {
			val condition = region.getCondition()
			if (printInsns && condition != null) {
				val conditionGen = ConditionGen(InsnGen(MethodGen.getFallbackMethodGen(mth), true))
				cw.startLine(indent).add("|> ")
				try {
					conditionGen.add(cw, condition)
				} catch (e: Exception) {
					cw.startLine(indent).add(">!! ").add(condition.toString())
				}
			}
		}
	}

	private fun printInsns(mth: MethodNode, cw: ICodeWriter, indent: String, block: IBlock) {
		for (insn in block.getInstructions()) {
			try {
				val mg = MethodGen.getFallbackMethodGen(mth)
				val ig = InsnGen(mg, true)
				val code = SimpleCodeWriter()
				ig.makeInsn(insn, code)
				val codeStr = code.getCodeStr()

				val insnStrings = codeStr.split(Regex("\\R"))
					.filter { StringUtils.notBlank(it) }
					.map { s -> "|> $s" }
				val it = insnStrings.iterator()
				while (true) {
					val insnStr = it.next()
					if (it.hasNext()) {
						cw.startLine(indent).add(insnStr)
					} else {
						printWithAttributes(cw, indent, insnStr, insn)
						break
					}
				}
			} catch (e: CodegenException) {
				cw.startLine(indent).add(">!! ").add(insn.toString())
			}
		}
	}

	private fun printWithAttributes(cw: ICodeWriter, indent: String, codeStr: String, attrNode: IAttributeNode) {
		val str = if (attrNode.isAttrStorageEmpty()) codeStr else codeStr + ' ' + attrNode.getAttributesString()
		val attrStrings = str.split(Regex("\\R"))
			.filter { StringUtils.notBlank(it) }
		val it = attrStrings.iterator()
		if (!it.hasNext()) {
			return
		}
		cw.startLine(indent).add(it.next())
		while (it.hasNext()) {
			cw.startLine(indent).add("|+  ").add(it.next())
		}
	}

	@JvmStatic
	fun printMap(map: Map<*, *>, desc: String) {
		LOG.debug("Map {} (size = {}):", desc, map.size)
		for ((key, value) in map) {
			LOG.debug("  {}: {}", key, value)
		}
	}

	@JvmStatic
	fun printStackTrace(label: String) {
		LOG.debug("StackTrace: {}\n{}", label, Utils.getFullStackTrace(Exception()))
	}

	@JvmStatic
	fun printMethodOverrideTop(root: RootNode) {
		LOG.debug("Methods override top 10:")
		val distinctByOverrideCount = distinctByKey<MethodOverrideAttr> { attr -> attr.relatedMthNodes.size }
		val distinctByRelatedMthNodes = distinctByKey<MethodOverrideAttr> { attr -> attr.relatedMthNodes }
		root.getClasses().asSequence()
			.flatMap { c -> c.methods.asSequence() }
			.filter { m -> m.contains(AType.METHOD_OVERRIDE) }
			.map { m -> checkNotNull(m.get(AType.METHOD_OVERRIDE)) }
			.filter { o -> o.overrideList.isNotEmpty() }
			.filter { o -> distinctByOverrideCount(o) }
			.filter { o -> distinctByRelatedMthNodes(o) }
			.sortedBy { o -> -o.relatedMthNodes.size }
			.take(10)
			.forEach { o -> LOG.debug("  {} : {}", o.relatedMthNodes.size, Utils.last(o.overrideList)) }
	}

	private fun <T> distinctByKey(keyExtractor: (T) -> Any?): (T) -> Boolean {
		val seen: MutableSet<Any?> = ConcurrentHashMap.newKeySet()
		return { t -> seen.add(keyExtractor(t)) }
	}

	private var execTimes: MutableMap<String, Long>? = null

	@JvmStatic
	fun initExecTimes() {
		execTimes = ConcurrentHashMap()
	}

	@JvmStatic
	fun mergeExecTimeFromStart(tag: String, startTimeMillis: Long) {
		mergeExecTime(tag, System.currentTimeMillis() - startTimeMillis)
	}

	@JvmStatic
	fun mergeExecTime(tag: String, execTimeMillis: Long) {
		checkNotNull(execTimes).merge(tag, execTimeMillis) { a, b -> (a ?: 0L) + b }
	}

	@JvmStatic
	fun printExecTimes() {
		println("Exec times:")
		checkNotNull(execTimes).forEach { (tag, time) -> println(" $tag: ${time}ms") }
	}

	@JvmStatic
	fun printExecTimesWithTotal(totalMillis: Long) {
		println("Exec times: total ${totalMillis}ms")
		checkNotNull(execTimes).forEach { (tag, time) ->
			println(" $tag: ${time}ms" + String.format(" (%.2f%%)", time * 100.0 / totalMillis.toDouble()))
		}
	}
}
