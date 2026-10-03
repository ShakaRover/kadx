package jadx.gui.cache.code.disk

import jadx.api.ICodeInfo
import jadx.api.impl.AnnotatedCodeInfo
import jadx.api.impl.SimpleCodeInfo
import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeMetadata
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.files.FileUtils
import jadx.gui.cache.code.disk.adapters.CodeAnnotationAdapter
import jadx.gui.cache.code.disk.adapters.DataAdapterHelper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * 代码元数据的磁盘编解码器。
 *
 * **做什么**：把 [ICodeMetadata]（行号映射 + 位置注解映射）序列化到磁盘上的
 * `.jadxmd` 文件，或从该文件还原并构建 [ICodeInfo]。
 *
 * **⚠️ 二进制格式必须逐字节保持**（G11 已确立、本批次继续遵守）：
 * ```
 * "jadxmd" 头部（US-ASCII，6 字节）
 * int  行号映射条目数（0 表示空）
 * [UVInt 生成行号, UVInt dex 行号] * n
 * int  注解条目数（0 表示空）
 * [UVInt 位置, CodeAnnotationAdapter 编码的注解] * n
 * ```
 * 其中 UVInt（无符号变长整数）由 [DataAdapterHelper] 读写，注解由
 * [CodeAnnotationAdapter] 按各自适配器格式编码。任何字段顺序/宽度变化都会导致
 * 旧缓存无法读取。
 *
 * **为什么空映射返回 `emptyMap()`**：与原 Java `Collections.emptyMap()` 语义一致
 * （不可变空映射），且条目数为 0 时不再分配 `HashMap`。
 */
class CodeMetadataAdapter(root: RootNode) {

	private val codeAnnotationAdapter = CodeAnnotationAdapter(root)

	/** 把元数据写入 [metadataFile]（自动创建父目录，已存在则覆盖）。 */
	fun write(metadataFile: Path, metadata: ICodeMetadata) {
		FileUtils.makeDirsForFile(metadataFile)
		try {
			Files.newOutputStream(metadataFile, WRITE, CREATE, TRUNCATE_EXISTING).use { fileOutput ->
				DataOutputStream(BufferedOutputStream(fileOutput)).use { out ->
					out.write(JADX_METADATA_HEADER)
					writeLines(out, metadata.getLineMapping())
					writeAnnotations(out, metadata.getAsMap())
				}
			}
		} catch (e: Exception) {
			throw RuntimeException("Failed to write metadata file", e)
		}
	}

	/** 从 [metadataFile] 读取元数据并与 [code] 组合成 [ICodeInfo]；文件不存在时退化为纯代码。 */
	fun readAndBuild(metadataFile: Path, code: String): ICodeInfo {
		if (!Files.exists(metadataFile)) {
			return SimpleCodeInfo(code)
		}
		try {
			Files.newInputStream(metadataFile).use { fileInput ->
				DataInputStream(BufferedInputStream(fileInput)).use { input ->
					input.skipBytes(JADX_METADATA_HEADER.size)
					val lines = readLines(input)
					val annotations = readAnnotations(input)
					return AnnotatedCodeInfo(code, lines, annotations)
				}
			}
		} catch (e: Exception) {
			throw RuntimeException("Failed to parse code annotations", e)
		}
	}

	@Throws(IOException::class)
	private fun writeLines(out: DataOutput, lines: Map<Int, Int>) {
		out.writeInt(lines.size)
		for ((key, value) in lines) {
			DataAdapterHelper.writeUVInt(out, key)
			DataAdapterHelper.writeUVInt(out, value)
		}
	}

	@Throws(IOException::class)
	private fun readLines(input: DataInput): Map<Int, Int> {
		val size = input.readInt()
		if (size == 0) {
			return emptyMap()
		}
		val lines = HashMap<Int, Int>(size)
		for (i in 0 until size) {
			val key = DataAdapterHelper.readUVInt(input)
			val value = DataAdapterHelper.readUVInt(input)
			lines[key] = value
		}
		return lines
	}

	@Throws(IOException::class)
	private fun writeAnnotations(out: DataOutputStream, annotations: Map<Int, ICodeAnnotation>) {
		out.writeInt(annotations.size)
		for ((pos, annotation) in annotations) {
			DataAdapterHelper.writeUVInt(out, pos)
			codeAnnotationAdapter.write(out, annotation)
		}
	}

	@Throws(IOException::class)
	private fun readAnnotations(input: DataInputStream): Map<Int, ICodeAnnotation> {
		val size = input.readInt()
		if (size == 0) {
			return emptyMap()
		}
		val map = HashMap<Int, ICodeAnnotation>(size)
		for (i in 0 until size) {
			val pos = DataAdapterHelper.readUVInt(input)
			val annotation = codeAnnotationAdapter.read(input)
			if (annotation != null) {
				map[pos] = annotation
			}
		}
		return map
	}

	companion object {
		private val JADX_METADATA_HEADER: ByteArray = "jadxmd".toByteArray(StandardCharsets.US_ASCII)
	}
}
