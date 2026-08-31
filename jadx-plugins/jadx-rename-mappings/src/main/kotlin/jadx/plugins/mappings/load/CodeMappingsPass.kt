package jadx.plugins.mappings.load

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.plugins.mappings.RenameMappingsData
import jadx.plugins.mappings.utils.DalvikToJavaBytecodeUtils
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
import net.fabricmc.mappingio.tree.MappingTreeView.MethodArgMappingView
import net.fabricmc.mappingio.tree.MappingTreeView.MethodMappingView

/**
 * 代码级映射 Pass：把方法参数（以及将来的局部变量）的重命名应用到 SSAVar。
 *
 * **背景**：在 CodeRenameVisitor 之前运行；[clsRenamesMap] 缓存「有参数/变量映射」的类，
 * visit() 时只对命中的类做处理。lvIndex 匹配依赖 DalvikToJavaBytecodeUtils。
 */
public class CodeMappingsPass : JadxDecompilePass {

	private var clsRenamesMap: MutableMap<String, ClassMappingView>? = null

	override fun getInfo(): JadxPassInfo = OrderedJadxPassInfo(
		"CodeMappings",
		"Apply mappings to method args and vars",
	).before("CodeRenameVisitor")

	override fun init(root: RootNode) {
		val data = RenameMappingsData.getData(root) ?: return
		val mappingTree = data.mappings
		updateMappingsMap(mappingTree)
		root.registerCodeDataUpdateListener { updateMappingsMap(mappingTree) }
	}

	override fun visit(cls: ClassNode): Boolean {
		val classMapping = getMapping(cls)
		if (classMapping != null) {
			applyRenames(cls, classMapping)
		}
		cls.innerClasses.forEach { this.visit(it) }
		return false
	}

	override fun visit(mth: MethodNode) {
	}

	private fun getMapping(cls: ClassNode): ClassMappingView? {
		val map = clsRenamesMap ?: return null
		if (map.isEmpty()) {
			return null
		}
		val classPath = cls.classInfo.makeRawFullName().replace('.', '/')
		return map[classPath]
	}

	private fun updateMappingsMap(mappings: MappingTreeView) {
		val newMap = HashMap<String, ClassMappingView>()
		for (cls in mappings.getClasses()) {
			for (mth in cls.getMethods()) {
				if (!mth.getArgs().isEmpty() || !mth.getVars().isEmpty()) {
					newMap[cls.getSrcName()] = cls
					break
				}
			}
		}
		clsRenamesMap = newMap
	}

	private companion object {
		private fun applyRenames(cls: ClassNode, classMapping: ClassMappingView) {
			for (mth in cls.methods) {
				val methodName = mth.methodInfo.name
				val methodDesc = mth.methodInfo.shortId.substring(methodName.length)
				val ssaVars = mth.sVars
				if (ssaVars.isEmpty()) {
					continue
				}
				val methodMapping = classMapping.getMethod(methodName, methodDesc)
				if (methodMapping == null) {
					continue
				}
				// 方法参数
				for (argMapping in methodMapping.getArgs()) {
					val mappingLvIndex: Int? = argMapping.getLvIndex()
					for (ssaVar in ssaVars) {
						val actualLvIndex = DalvikToJavaBytecodeUtils.getMethodArgLvIndex(ssaVar, mth)
						if (actualLvIndex == mappingLvIndex) {
							ssaVar.codeVar.name = argMapping.getDstName(0)
							break
						}
					}
				}
				// TODO: 方法局部变量（如果可行的话）
			}
		}
	}
}
