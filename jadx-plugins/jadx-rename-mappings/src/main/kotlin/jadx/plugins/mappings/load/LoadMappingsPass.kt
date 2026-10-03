package jadx.plugins.mappings.load

import jadx.api.JadxArgs
import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.SimpleJadxPassInfo
import jadx.api.plugins.pass.types.JadxPreparePass
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.plugins.mappings.RenameMappingsData
import jadx.plugins.mappings.RenameMappingsOptions
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.mappingio.tree.VisitableMappingTree

/**
 * 加载映射文件 Pass：读取用户指定的映射文件（格式可自动探测），
 * 必要时交换源/目标命名空间，然后把结果存入 [RenameMappingsData]。
 */
public class LoadMappingsPass(private val options: RenameMappingsOptions) : JadxPreparePass {

	override fun getInfo(): JadxPassInfo = SimpleJadxPassInfo("LoadMappings", "Load mappings file")

	override fun init(root: RootNode) {
		val mappings = loadMapping(root.getArgs())
		root.attributes.add(RenameMappingsData(mappings))
	}

	private fun loadMapping(args: JadxArgs): MappingTreeView = try {
		val mappingsPath = checkNotNull(args.userRenamesMappingsPath)
		val mappingTree = MemoryMappingTree()
		MappingReader.read(mappingsPath, options.format, mappingTree)
		if (mappingTree.getSrcNamespace() == null) {
			mappingTree.setSrcNamespace(MappingUtil.NS_SOURCE_FALLBACK)
		}
		if (mappingTree.getDstNamespaces() == null || mappingTree.getDstNamespaces().isEmpty()) {
			mappingTree.setDstNamespaces(listOf(MappingUtil.NS_TARGET_FALLBACK))
		} else if (mappingTree.getDstNamespaces().size > 1) {
			throw JadxRuntimeException(
				"JADX only supports mappings with just one destination namespace! The provided ones have ${mappingTree.getDstNamespaces().size}.",
			)
		}
		if (options.isInvert) {
			val invertedMappingTree = MemoryMappingTree()
			val dstNamespace = mappingTree.getDstNamespaces()[0]
			mappingTree.accept(MappingSourceNsSwitch(invertedMappingTree, dstNamespace))
			invertedMappingTree
		} else {
			mappingTree
		}
	} catch (e: Exception) {
		throw JadxRuntimeException("Failed to load mappings", e)
	}
}
