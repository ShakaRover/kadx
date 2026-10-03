package jadx.plugins.mappings

import jadx.api.JadxArgs
import jadx.api.args.UserRenamesMappingsMode
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.core.utils.files.FileUtils
import jadx.plugins.mappings.load.ApplyMappingsPass
import jadx.plugins.mappings.load.CodeMappingsPass
import jadx.plugins.mappings.load.LoadMappingsPass
import java.nio.file.Files
import java.nio.file.Path

/**
 * rename-mappings 插件：加载用户提供的映射文件并应用重命名。
 *
 * **背景**：init() 注册选项；当用户启用了重命名映射且映射文件可读时，
 * 依次添加 Load/Apply/Code 三个 Pass，并用「文件路径 + 修改时间 + 选项」的 md5
 * 作为输入哈希（文件变化时触发重新反编译）。
 */
public class RenameMappingsPlugin : JadxPlugin {

	private val options = RenameMappingsOptions()

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfo(PLUGIN_ID, "Rename Mappings", "various mappings support")

	override fun init(context: JadxPluginContext) {
		context.registerOptions(options)
		val args = context.getArgs()
		if (args.userRenamesMappingsMode == UserRenamesMappingsMode.IGNORE) {
			return
		}
		val mappingsPath = args.userRenamesMappingsPath
		if (mappingsPath == null || !Files.isReadable(mappingsPath)) {
			return
		}
		context.addPass(LoadMappingsPass(options))
		context.addPass(ApplyMappingsPass())
		context.addPass(CodeMappingsPass())

		// 用映射文件的修改时间检查变化
		context.registerInputsHashSupplier { FileUtils.md5Sum(getInputsHashString(mappingsPath)) }
	}

	private fun getInputsHashString(mappingsPath: Path): String = getFileHashString(mappingsPath) + ':' + options.optionsHashString

	public companion object {
		const val PLUGIN_ID: String = "rename-mappings"

		private fun getFileHashString(mappingsPath: Path): String = try {
			mappingsPath.toAbsolutePath().normalize().toString() + ":" + Files.getLastModifiedTime(mappingsPath).toMillis()
		} catch (e: Exception) {
			""
		}
	}
}
