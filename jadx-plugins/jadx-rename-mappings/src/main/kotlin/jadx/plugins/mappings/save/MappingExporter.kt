package jadx.plugins.mappings.save

import jadx.api.data.ICodeComment
import jadx.api.data.ICodeRename
import jadx.api.data.IJavaNodeRef.RefType
import jadx.api.data.impl.JadxCodeData
import jadx.api.data.impl.JadxCodeRef
import jadx.api.metadata.annotations.VarNode
import jadx.core.Consts
import jadx.core.codegen.TypeGen
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.files.FileUtils
import jadx.plugins.mappings.RenameMappingsData
import jadx.plugins.mappings.utils.DalvikToJavaBytecodeUtils
import jadx.plugins.mappings.utils.VariablesUtils
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingWriter
import net.fabricmc.mappingio.format.MappingFormat
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.mappingio.tree.VisitOrder
import net.fabricmc.mappingio.tree.VisitableMappingTree
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * 把当前反编译结果中的重命名/注释导出为映射文件。
 *
 * **背景**：只导出「手动重命名」的元素（通过 JadxCodeData 的 renames/comments 判定），
 * 并合并已加载的映射树；支持单文件格式与目录格式两种输出。
 */
public class MappingExporter(private val root: RootNode) {

	private val loadedMappingTree: MappingTreeView? = RenameMappingsData.getTree(root)

	public fun exportMappings(path: Path, codeData: JadxCodeData, mappingFormat: MappingFormat) {
		val mappingTree = MemoryMappingTree()
		// Map < SrcName >
		val mappedClasses = HashSet<String>()
		// Map < DeclClass + ShortId >
		val mappedFields = HashSet<String>()
		val mappedMethods = HashSet<String>()
		val methodsWithMappedElements = HashSet<String>()
		// Map < DeclClass + MethodShortId + CodeRef, NewName >
		val mappedMethodArgsAndVars = HashMap<String, String>()
		// Map < DeclClass [+ ShortId] [+ CodeRef], Comment >
		val comments = HashMap<String, String>()

		// 必须这样做才能确定哪些元素是*手动*重命名的
		for (codeRename in codeData.renames) {
			if (codeRename.nodeRef.type == RefType.CLASS) {
				mappedClasses.add(codeRename.nodeRef.declaringClass)
			} else if (codeRename.nodeRef.type == RefType.FIELD) {
				mappedFields.add(codeRename.nodeRef.declaringClass + codeRename.nodeRef.shortId)
			} else if (codeRename.nodeRef.type == RefType.METHOD) {
				if (codeRename.codeRef == null) {
					mappedMethods.add(codeRename.nodeRef.declaringClass + codeRename.nodeRef.shortId)
				} else {
					methodsWithMappedElements.add(codeRename.nodeRef.declaringClass + codeRename.nodeRef.shortId)
					mappedMethodArgsAndVars[
						codeRename.nodeRef.declaringClass +
							codeRename.nodeRef.shortId +
							codeRename.codeRef,
					] = codeRename.newName
				}
			}
		}
		for (codeComment in codeData.comments) {
			comments[
				codeComment.nodeRef.declaringClass +
					if (codeComment.nodeRef.shortId == null) {
						""
					} else {
						codeComment.nodeRef.shortId!! +
							if (codeComment.codeRef == null) "" else codeComment.codeRef!!
					},
			] = codeComment.comment
			if (codeComment.codeRef != null) {
				methodsWithMappedElements.add(codeComment.nodeRef.declaringClass + codeComment.nodeRef.shortId)
			}
		}

		try {
			val srcNamespace = MappingUtil.NS_SOURCE_FALLBACK
			val dstNamespace = MappingUtil.NS_TARGET_FALLBACK

			// 从可能导入的映射文件复制映射
			if (loadedMappingTree != null && loadedMappingTree.getDstNamespaces() != null) {
				loadedMappingTree.accept(mappingTree)
			}

			mappingTree.visitHeader()
			mappingTree.visitNamespaces(srcNamespace, listOf(dstNamespace))
			mappingTree.visitContent()

			for (cls in root.classes) {
				val classInfo = cls.classInfo
				val classPath = classInfo.makeRawFullName().replace('.', '/')
				val rawClassName = classInfo.rawName

				if (classInfo.hasAlias() &&
					classInfo.aliasShortName != classInfo.shortName &&
					mappedClasses.contains(rawClassName)
				) {
					mappingTree.visitClass(classPath)
					var alias = classInfo.makeAliasRawFullName().replace('.', '/')

					if (alias.startsWith(Consts.DEFAULT_PACKAGE_NAME)) {
						alias = alias.substring(Consts.DEFAULT_PACKAGE_NAME.length + 1)
					}
					mappingTree.visitDstName(MappedElementKind.CLASS, 0, alias)
				}
				if (comments.containsKey(rawClassName)) {
					mappingTree.visitClass(classPath)
					mappingTree.visitComment(MappedElementKind.CLASS, comments[rawClassName])
				}

				for (fld in cls.fields) {
					val fieldInfo = fld.fieldInfo
					if (fieldInfo.hasAlias() && mappedFields.contains(rawClassName + fieldInfo.shortId)) {
						visitField(mappingTree, classPath, fieldInfo.name, TypeGen.signature(fieldInfo.type))
						mappingTree.visitDstName(MappedElementKind.FIELD, 0, fieldInfo.alias)
					}
					if (comments.containsKey(rawClassName + fieldInfo.shortId)) {
						visitField(mappingTree, classPath, fieldInfo.name, TypeGen.signature(fieldInfo.type))
						mappingTree.visitComment(MappedElementKind.FIELD, comments[rawClassName + fieldInfo.shortId])
					}
				}

				for (mth in cls.methods) {
					val methodInfo = mth.methodInfo
					val methodName = methodInfo.name
					val methodDesc = methodInfo.shortId.substring(methodName.length)
					if (methodInfo.hasAlias() && mappedMethods.contains(rawClassName + methodInfo.shortId)) {
						visitMethod(mappingTree, classPath, methodName, methodDesc)
						mappingTree.visitDstName(MappedElementKind.METHOD, 0, methodInfo.alias)
					}
					if (comments.containsKey(rawClassName + methodInfo.shortId)) {
						visitMethod(mappingTree, classPath, methodName, methodDesc)
						mappingTree.visitComment(MappedElementKind.METHOD, comments[rawClassName + methodInfo.shortId])
					}

					if (!methodsWithMappedElements.contains(rawClassName + methodInfo.shortId)) {
						continue
					}
					// 方法参数
					var lvtIndex = if (mth.accessFlags.isStatic()) 0 else 1
					val args = mth.collectArgNodes()
					for (arg in args) {
						var lvIndex: Int? = DalvikToJavaBytecodeUtils.getMethodArgLvIndex(arg)
						if (lvIndex == null) {
							lvIndex = -1
						}
						val key = rawClassName + methodInfo.shortId +
							JadxCodeRef.forVar(arg.reg, arg.ssa)
						if (mappedMethodArgsAndVars.containsKey(key)) {
							visitMethodArg(mappingTree, classPath, methodName, methodDesc, args.indexOf(arg), lvIndex!!)
							mappingTree.visitDstName(MappedElementKind.METHOD_ARG, 0, mappedMethodArgsAndVars[key])
							mappedMethodArgsAndVars.remove(key)
						}
						lvtIndex++
						// 方法参数没有注释，所以不检查
					}
					// 方法局部变量
					for (info in VariablesUtils.collect(mth)) {
						val varNode = info.`var`
						val startOpIdx = info.startOpIdx
						val endOpIdx = info.endOpIdx
						val lvIndex = DalvikToJavaBytecodeUtils.getMethodVarLvIndex(varNode)!!
						val key = rawClassName + methodInfo.shortId +
							JadxCodeRef.forVar(varNode.reg, varNode.ssa)
						if (mappedMethodArgsAndVars.containsKey(key)) {
							visitMethodVar(mappingTree, classPath, methodName, methodDesc, lvtIndex, lvIndex, startOpIdx, endOpIdx)
							mappingTree.visitDstName(MappedElementKind.METHOD_VAR, 0, mappedMethodArgsAndVars[key])
						}
						val key2 = rawClassName + methodInfo.shortId + JadxCodeRef.forInsn(startOpIdx)
						if (comments.containsKey(key2)) {
							visitMethodVar(mappingTree, classPath, methodName, methodDesc, lvtIndex, lvIndex, startOpIdx, endOpIdx)
							mappingTree.visitComment(MappedElementKind.METHOD_VAR, comments[key2])
						}
						lvtIndex++
					}
				}
			}
			// 尽量晚地写文件，因为映射收集可能抛异常
			if (mappingFormat.hasSingleFile()) {
				FileUtils.deleteFileIfExists(path)
				FileUtils.makeDirsForFile(path)
				Files.createFile(path)
			} else {
				FileUtils.makeDirs(path)
			}
			// 写文件
			mappingTree.accept(MappingWriter.create(path, mappingFormat), VisitOrder.createByName())
			mappingTree.visitEnd()
		} catch (e: Exception) {
			LOG.error("Failed to save deobfuscation map file '{}'", path.toAbsolutePath(), e)
		}
	}

	private fun visitField(tree: VisitableMappingTree, classPath: String, srcName: String, srcDesc: String) {
		tree.visitClass(classPath)
		tree.visitField(srcName, srcDesc)
	}

	private fun visitMethod(tree: VisitableMappingTree, classPath: String, srcName: String, srcDesc: String) {
		tree.visitClass(classPath)
		tree.visitMethod(srcName, srcDesc)
	}

	private fun visitMethodArg(
		tree: VisitableMappingTree,
		classPath: String,
		methodSrcName: String,
		methodSrcDesc: String,
		argPosition: Int,
		lvIndex: Int,
	) {
		visitMethod(tree, classPath, methodSrcName, methodSrcDesc)
		tree.visitMethodArg(argPosition, lvIndex, null)
	}

	private fun visitMethodVar(
		tree: VisitableMappingTree,
		classPath: String,
		methodSrcName: String,
		methodSrcDesc: String,
		lvtIndex: Int,
		lvIndex: Int,
		startOpIdx: Int,
		endOpIdx: Int,
	) {
		visitMethod(tree, classPath, methodSrcName, methodSrcDesc)
		tree.visitMethodVar(lvtIndex, lvIndex, startOpIdx, endOpIdx, null)
	}

	private companion object {
		private val LOG = LoggerFactory.getLogger(MappingExporter::class.java)
	}
}
