@file:Suppress("ktlint:standard:property-naming")

package kadx.core.utils

import kadx.api.ICodeWriter
import kadx.api.impl.SimpleCodeWriter
import kadx.core.codegen.ConditionGen
import kadx.core.codegen.InsnGen
import kadx.core.codegen.MethodGen
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.IAttributeNode
import kadx.core.dex.attributes.nodes.MethodOverrideAttr
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.IBlock
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.regions.Region
import kadx.core.dex.regions.conditions.IfCondition
import kadx.core.dex.regions.loops.LoopRegion
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.DotGraphVisitor
import kadx.core.dex.visitors.IDexTreeVisitor
import kadx.core.dex.visitors.regions.DepthRegionTraversal
import kadx.core.dex.visitors.regions.TracedRegionVisitor
import kadx.core.utils.exceptions.CodegenException
import kadx.core.utils.exceptions.KadxException
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

	val TEST_MTH_FILTER: (MethodNode) -> Boolean = { mth -> mth.name == "test" }

	fun dump(mth: MethodNode) {
		dump(mth, "dump")
	}

	fun dumpRaw(mth: MethodNode, desc: String, dumpCondition: (MethodNode) -> Boolean) {
		if (dumpCondition(mth)) {
			dumpRaw(mth, desc)
		}
	}

	fun dumpRawTest(mth: MethodNode, desc: String) {
		dumpRaw(mth, desc, TEST_MTH_FILTER)
	}

	fun dumpRaw(mth: MethodNode, desc: String) {
		val out = File("test-graph-$desc-tmp")
		DotGraphVisitor.dumpRaw().save(out, mth)
	}

	fun dumpRawVisitor(desc: String): IDexTreeVisitor = object : AbstractVisitor() {
		@Throws(KadxException::class)
		override fun visit(mth: MethodNode) {
			dumpRaw(mth, desc)
		}
	}

	fun dumpRawVisitor(desc: String, filter: (MethodNode) -> Boolean): IDexTreeVisitor = object : AbstractVisitor() {
		override fun visit(mth: MethodNode) {
			if (filter(mth)) {
				dumpRaw(mth, desc)
			}
		}
	}

	fun dumpRawTestVisitor(desc: String): IDexTreeVisitor = dumpRawVisitor(desc, TEST_MTH_FILTER)

	fun dump(mth: MethodNode, desc: String) {
		val out = File("test-graph-$desc-tmp")
		DotGraphVisitor.dump().save(out, mth)
		DotGraphVisitor.dumpRaw().save(out, mth)
		DotGraphVisitor.dumpRegions().save(out, mth)
	}

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

	fun printRegionsVisitor(): IDexTreeVisitor = object : AbstractVisitor() {
		@Throws(KadxException::class)
		override fun visit(mth: MethodNode) {
			printRegions(mth, true)
		}
	}

	fun printRegions(mth: MethodNode) {
		printRegions(mth, false)
	}

	fun printRegions(mth: MethodNode, printInsns: Boolean) {
		val mthRegion = mth.region
		if (mthRegion == null) {
			return
		}
		printRegion(mth, mthRegion, printInsns)
	}

	fun printRegion(mth: MethodNode, region: IRegion, printInsns: Boolean) {
		val cw = SimpleCodeWriter()
		cw.startLine('|').add(mth.toString())
		printRegion(mth, region, cw, "|  ", printInsns)
		LOG.debug("{}{}", '\n', cw.finish().codeStr)
	}

	private fun printRegion(mth: MethodNode, region: IRegion, cw: ICodeWriter, indent0: String, printInsns: Boolean) {
		var indent = indent0
		printWithAttributes(cw, indent, region.toString(), region)
		indent += "|  "
		printRegionSpecificInfo(cw, indent, mth, region, printInsns)
		for (container in region.subBlocks) {
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
			val condition = region.condition
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
		for (insn in block.instructions) {
			try {
				val mg = MethodGen.getFallbackMethodGen(mth)
				val ig = InsnGen(mg, true)
				val code = SimpleCodeWriter()
				ig.makeInsn(insn, code)
				val codeStr = code.codeStr

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

	fun printMap(map: Map<*, *>, desc: String) {
		LOG.debug("Map {} (size = {}):", desc, map.size)
		for ((key, value) in map) {
			LOG.debug("  {}: {}", key, value)
		}
	}

	fun printStackTrace(label: String) {
		LOG.debug("StackTrace: {}\n{}", label, Utils.getFullStackTrace(Exception()))
	}

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

	fun initExecTimes() {
		execTimes = ConcurrentHashMap()
	}

	fun mergeExecTimeFromStart(tag: String, startTimeMillis: Long) {
		mergeExecTime(tag, System.currentTimeMillis() - startTimeMillis)
	}

	fun mergeExecTime(tag: String, execTimeMillis: Long) {
		checkNotNull(execTimes).merge(tag, execTimeMillis) { a, b -> (a ?: 0L) + b }
	}

	fun printExecTimes() {
		println("Exec times:")
		checkNotNull(execTimes).forEach { (tag, time) -> println(" $tag: ${time}ms") }
	}

	fun printExecTimesWithTotal(totalMillis: Long) {
		println("Exec times: total ${totalMillis}ms")
		checkNotNull(execTimes).forEach { (tag, time) ->
			println(" $tag: ${time}ms" + String.format(" (%.2f%%)", time * 100.0 / totalMillis.toDouble()))
		}
	}
}
