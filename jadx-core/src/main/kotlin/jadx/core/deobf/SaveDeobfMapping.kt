package jadx.core.deobf

import jadx.api.JadxArgs
import jadx.api.args.GeneratedRenamesMappingFileMode
import jadx.core.codegen.json.JsonMappingGen
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.utils.exceptions.JadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * 保存反混淆映射的 pass。
 *
 * **用途**：把本次反混淆产生的“原名 → 别名”映射写盘（`.jobf` 文件），以便下次运行
 * 时复用同一套名字（保证反编译结果稳定）。当开启 JSON 输出时，还会额外导出 JSON 映射。
 *
 * 写入前会判断映射文件模式（[GeneratedRenamesMappingFileMode]）：
 * - 只有 [GeneratedRenamesMappingFileMode.shouldWrite] 为真才写；
 * - `READ_OR_SAVE` 模式下若文件已存在则跳过（避免覆盖用户已有映射）。
 */
class SaveDeobfMapping : AbstractVisitor() {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SaveDeobfMapping::class.java)
	}

	@Throws(JadxException::class)
	override fun init(root: RootNode) {
		val args: JadxArgs = root.args
		if (args.isDeobfuscationOn || !args.isJsonOutput) {
			saveMappings(root)
		}
		if (args.isJsonOutput) {
			JsonMappingGen.dump(root)
		}
	}

	private fun saveMappings(root: RootNode) {
		val mode = root.args.generatedRenamesMappingFileMode
		if (!mode.shouldWrite()) {
			return
		}
		val mapping = DeobfPresets.build(root)
		val deobfMapFile: Path = mapping.deobfMapFile
		if (mode == GeneratedRenamesMappingFileMode.READ_OR_SAVE && Files.exists(deobfMapFile)) {
			return
		}
		try {
			mapping.clear()
			mapping.fill(root)
			mapping.save()
		} catch (e: Exception) {
			LOG.error("Failed to save deobfuscation map file '{}'", deobfMapFile.toAbsolutePath(), e)
		}
	}

	override fun getName(): String = "SaveDeobfMapping"
}
