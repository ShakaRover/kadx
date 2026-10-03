package jadx.cli.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import jadx.cli.JCommanderWrapper
import jadx.cli.LogHelper
import jadx.core.utils.StringUtils
import jadx.plugins.tools.JadxPluginsList
import jadx.plugins.tools.JadxPluginsTools
import jadx.plugins.tools.data.JadxPluginMetadata
import java.util.HashSet

/**
 * `jadx plugins` 子命令：安装、卸载、列出、更新插件。
 *
 * **做什么**：解析子命令参数后，把请求转发给 [JadxPluginsTools] / [JadxPluginsList]。
 *
 * **为什么字段保持同名**：jcommander 通过字段反射读写参数，Kotlin 属性背后字段名与原 Java 一致，
 * `@Parameter` 注解默认落在字段上，因此解析行为不变。
 */
@Parameters(commandDescription = "manage jadx plugins")
class CommandPlugins : ICommand {

	@Parameter(names = ["-i", "--install"], description = "install plugin with locationId", defaultValueDescription = "<locationId>")
	private var install: String? = null

	@Parameter(names = ["-j", "--install-jar"], description = "install plugin from jar file", defaultValueDescription = "<path-to.jar>")
	private var installJar: String? = null

	@Parameter(names = ["-l", "--list"], description = "list installed plugins")
	private var list: Boolean = false

	@Parameter(names = ["-a", "--available"], description = "list available plugins from jadx-plugins-list (aka marketplace)")
	private var available: Boolean = false

	@Parameter(names = ["-u", "--update"], description = "update installed plugins")
	private var update: Boolean = false

	@Parameter(names = ["--uninstall"], description = "uninstall plugin with pluginId", defaultValueDescription = "<pluginId>")
	private var uninstall: String? = null

	@Parameter(names = ["--disable"], description = "disable plugin with pluginId", defaultValueDescription = "<pluginId>")
	private var disable: String? = null

	@Parameter(names = ["--enable"], description = "enable plugin with pluginId", defaultValueDescription = "<pluginId>")
	private var enable: String? = null

	@Parameter(names = ["--list-all"], description = "list all plugins including bundled and dropins")
	private var listAll: Boolean = false

	@Parameter(
		names = ["--list-versions"],
		description = "fetch latest versions of plugin from locationId (will download all artefacts, limited to 10)",
		defaultValueDescription = "<locationId>",
	)
	private var listVersions: String? = null

	@Parameter(names = ["-h", "--help"], description = "print this help", help = true)
	private var printHelp: Boolean = false

	override fun name(): String = "plugins"

	override fun process(jcw: JCommanderWrapper, subCommander: JCommander) {
		if (printHelp) {
			jcw.printUsage(subCommander)
			return
		}
		val unknownOptions = HashSet(subCommander.getUnknownOptions())
		val verbose = unknownOptions.remove("-v") || unknownOptions.remove("--verbose")
		LogHelper.setLogLevel(if (verbose) LogHelper.LogLevelEnum.DEBUG else LogHelper.LogLevelEnum.INFO)

		if (unknownOptions.isNotEmpty()) {
			println("Error: found unknown options: $unknownOptions")
		}

		install?.let {
			installPlugin(it)
			return
		}
		installJar?.let {
			installPlugin("file:$it")
			return
		}
		uninstall?.let {
			val uninstalled = JadxPluginsTools.instance.uninstall(it)
			println(if (uninstalled) "Uninstalled" else "Plugin not found")
			return
		}
		if (update) {
			val updates = JadxPluginsTools.instance.updateAll()
			if (updates.isEmpty()) {
				println("No updates")
			} else {
				println("Installed updates: " + updates.size)
				for (updateItem in updates) {
					println("  " + updateItem.pluginId + ": " + updateItem.oldVersion + " -> " + updateItem.newVersion)
				}
			}
			return
		}
		if (list) {
			printPlugins(JadxPluginsTools.instance.getInstalled())
			return
		}
		if (listAll) {
			printAllPlugins()
			return
		}
		listVersions?.let {
			printVersions(it, 10)
			return
		}
		if (available) {
			val availableList = JadxPluginsList.instance.get()
			println("Available plugins: " + availableList.size)
			for (plugin in availableList) {
				println(" - " + plugin.name + ": " + plugin.description + " (" + plugin.locationId + ")")
			}
			return
		}

		disable?.let {
			if (JadxPluginsTools.instance.changeDisabledStatus(it, true)) {
				println("Plugin '$it' disabled.")
			} else {
				println("Plugin '$it' already disabled.")
			}
			return
		}
		enable?.let {
			if (JadxPluginsTools.instance.changeDisabledStatus(it, false)) {
				println("Plugin '$it' enabled.")
			} else {
				println("Plugin '$it' already enabled.")
			}
			return
		}
	}

	private fun printPlugins(installed: List<JadxPluginMetadata>) {
		println("Installed plugins: " + installed.size)
		for (plugin in installed) {
			val sb = StringBuilder()
			sb.append(" - ").append(plugin.pluginId)
			val version = plugin.version
			if (version != null) {
				sb.append(" (").append(version).append(')')
			}
			if (plugin.isDisabled) {
				sb.append(" (disabled)")
			}
			sb.append(" - ").append(plugin.name)
			sb.append(": ").append(formatDescription(plugin.description ?: ""))
			println(sb)
		}
	}

	private fun printVersions(locationId: String, limit: Int) {
		println("Loading ...")
		val versions = JadxPluginsTools.instance.getVersionsByLocation(locationId, 1, limit)
		if (versions.isEmpty()) {
			println("No versions found")
			return
		}
		val plugin = versions[0]
		println("Versions for plugin id: " + plugin.pluginId)
		for (version in versions) {
			val sb = StringBuilder()
			sb.append(" - ").append(version.version)
			val reqVer = version.requiredJadxVersion
			if (StringUtils.notBlank(reqVer)) {
				sb.append(", require jadx: ").append(reqVer)
			}
			println(sb)
		}
	}

	private fun printAllPlugins() {
		val installed = JadxPluginsTools.instance.getInstalled()
		printPlugins(installed)
		val installedSet = installed.map { it.pluginId }.toSet()

		val plugins = JadxPluginsTools.instance.getAllPluginsInfo()
		println("Other plugins: " + plugins.size)
		for (plugin in plugins) {
			if (plugin.getPluginId() !in installedSet) {
				println(
					" - " + plugin.getPluginId() +
						" - " + plugin.getName() +
						": " + formatDescription(plugin.getDescription()),
				)
			}
		}
	}

	private fun formatDescription(desc: String): String {
		var result = desc
		if (result.contains("\n")) {
			// 去掉换行
			result = result.replace(Regex("\\R+"), " ")
		}
		val maxLen = 512
		if (result.length > maxLen) {
			// 截断超长描述
			result = result.substring(0, maxLen) + " ..."
		}
		return result
	}

	private fun installPlugin(locationId: String) {
		val plugin = JadxPluginsTools.instance.install(locationId)
		println("Plugin installed: " + plugin.pluginId + ":" + plugin.version)
	}
}
