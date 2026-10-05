package kadx.plugins.mappings.load

import kadx.api.plugins.pass.KadxPassInfo
import kadx.api.plugins.pass.impl.OrderedKadxPassInfo
import kadx.api.plugins.pass.types.KadxPreparePass
import kadx.core.codegen.TypeGen
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.plugins.mappings.RenameMappingsData
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
import net.fabricmc.mappingio.tree.MappingTreeView.FieldMappingView
import net.fabricmc.mappingio.tree.MappingTreeView.MethodMappingView

/**
 * 应用映射 Pass：把类/字段/方法的重命名与注释应用到 AST 节点。
 *
 * **背景**：在 LoadMappings 之后、RenameVisitor 之前运行；并注册代码数据更新监听，
 * 使后续增量更新时重新应用映射。参数/局部变量的重命名由 CodeMappingsPass 处理。
 */
public class ApplyMappingsPass : KadxPreparePass {

	override fun getInfo(): KadxPassInfo = OrderedKadxPassInfo(
		"ApplyMappings",
		"Apply mappings to classes, fields and methods",
	).after("LoadMappings").before("RenameVisitor")

	override fun init(root: RootNode) {
		val data = RenameMappingsData.getData(root) ?: return
		val mappingTree = data.mappings
		process(root, mappingTree)
		root.registerCodeDataUpdateListener { process(root, mappingTree) }
	}

	private fun process(root: RootNode, mappingTree: MappingTreeView) {
		for (cls in root.getClasses()) {
			val clsRawName = cls.classInfo.rawName.replace('.', '/')
			val mapping = mappingTree.`getClass`(clsRawName)
			if (mapping != null) {
				processClass(cls, mapping)
			}
		}
	}

	private companion object {
		private fun processClass(cls: ClassNode, classMapping: ClassMappingView) {
			val alias = classMapping.getDstName(0)
			if (alias != null) {
				cls.rename(alias.replace('/', '.'))
			}
			val comment = classMapping.getComment()
			if (comment != null) {
				cls.addCodeComment(comment)
			}
			for (field in cls.fields) {
				val fieldInfo = field.fieldInfo
				val signature = TypeGen.signature(fieldInfo.type)
				val fieldMapping = classMapping.getField(fieldInfo.name, signature)
				if (fieldMapping != null) {
					processField(field, fieldMapping)
				}
			}
			for (method in cls.methods) {
				val methodInfo = method.methodInfo
				val methodName = methodInfo.name
				val methodDesc = methodInfo.shortId.substring(methodName.length)
				val methodMapping = classMapping.getMethod(methodName, methodDesc)
				if (methodMapping != null) {
					processMethod(method, methodMapping)
				}
			}
		}

		private fun processField(field: FieldNode, fieldMapping: FieldMappingView) {
			val alias = fieldMapping.getDstName(0)
			if (alias != null) {
				field.rename(alias)
			}
			val comment = fieldMapping.getComment()
			if (comment != null) {
				field.addCodeComment(comment)
			}
		}

		private fun processMethod(method: MethodNode, methodMapping: MethodMappingView) {
			val alias = methodMapping.getDstName(0)
			if (alias != null) {
				method.rename(alias)
			}
			val comment = methodMapping.getComment()
			if (comment != null) {
				method.addCodeComment(comment)
			}
			// 方法参数与局部变量由 CodeMappingsPass 处理
		}
	}
}
