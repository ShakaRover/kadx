package kadx.cli

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterDescription
import com.beust.jcommander.ParameterException
import kadx.api.KadxDecompiler
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.options.KadxPluginOptions
import kadx.api.plugins.options.OptionDescription
import kadx.core.utils.Utils
import java.io.PrintStream
import java.lang.reflect.Field
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

/**
 * jcommander 的薄封装：负责参数解析、配置覆盖、命令分派以及帮助信息打印。
 *
 * **做什么**：把 [KadxCLIArgs] 注册到 jcommander，解析命令行；支持用配置文件对象
 * “覆盖已显式提供的参数”；打印与原生 kadx 一致的 usage 文本（含插件选项）。
 *
 * **为什么这样写**：原 Java 通过反射读写字段，Kotlin 属性的背后字段名一致，因此反射逻辑不变。
 * 私有辅助方法保留原签名（`Class<?>`、`String[]` 等）。
 */
class JCommanderWrapper(private val argsObj: KadxCLIArgs) {

	private val jc: JCommander

	init {
		val builder = JCommander.newBuilder().addObject(argsObj)
		builder.acceptUnknownOptions(true) // 兼容未显式声明的 "default" 命令
		KadxCLICommands.append(builder)
		this.jc = builder.build()
	}

	/** 解析命令行参数；失败时打印错误并返回 false。 */
	fun parse(args: Array<String>): Boolean = try {
		val fixedArgs = fixArgsForEmptySaveConfig(args)
		jc.parse(*fixedArgs)
		applyFiles(argsObj)
		true
	} catch (e: ParameterException) {
		System.err.println("Arguments parse error: " + e.message)
		false
	}

	/** 用命令行里“显式提供”的参数覆盖 [obj]（用于合并配置文件）。 */
	fun overrideProvided(obj: KadxCLIArgs) {
		applyFiles(obj)
		for (parameter in jc.getParameters()) {
			if (parameter.isAssigned()) {
				overrideProperty(obj, parameter)
			}
		}
	}

	/** 处理已解析出的子命令。返回 false 表示没有子命令。 */
	fun processCommands(): Boolean {
		val parsedCommand = jc.getParsedCommand() ?: return false
		return KadxCLICommands.process(this, jc, parsedCommand)
	}

	/**
	 * 主参数解析在开启 `acceptUnknownOptions` 后无法正常工作，
	 * 因此把未知选项统一当作输入文件。
	 */
	private fun applyFiles(argsObj: KadxCLIArgs) {
		argsObj.files = jc.getUnknownOptions()
	}

	/** 把参数中已赋值的字段覆盖到 [obj]。 */
	private fun overrideProperty(obj: KadxCLIArgs, parameter: ParameterDescription) {
		val parameterized = parameter.getParameterized()
		val providedValue = parameterized.get(parameter.getObject())
		val newValue = mergeValues(parameterized.getType(), providedValue) { parameterized.get(obj) }
		parameterized.set(obj, newValue)
	}

	/** 对 Map 类型做合并（而非整体替换），其余类型直接覆盖。 */
	@Suppress("UNCHECKED_CAST")
	private fun mergeValues(type: Class<*>, value: Any?, prevValueProvider: () -> Any?): Any? {
		if (type.isAssignableFrom(Map::class.java)) {
			// 合并 map，而不是整体替换
			val prevMap = prevValueProvider() as Map<Any?, Any?>?
			return Utils.mergeMaps(prevMap, value as Map<Any?, Any?>?) // value 中的 key 会覆盖 prevMap
		}
		// 简单覆盖
		return value
	}

	/**
	 * 允许 `--save-config` 使用空值（零元）。
	 * 若该选项后面紧跟另一个选项或位于末尾，则插入一个空字符串参数。
	 */
	private fun fixArgsForEmptySaveConfig(args: Array<String>): Array<String> {
		val len = args.size
		for (i in 0 until len) {
			val arg = args[i]
			if (arg == "--save-config") {
				val next = i + 1
				if (next == len) {
					return insertEmptyArg(args, next, true)
				}
				if (next < len) {
					val nextArg = args[next]
					if (nextArg.startsWith("-")) {
						return insertEmptyArg(args, next, false)
					}
				}
				break
			}
		}
		return args
	}

	private fun insertEmptyArg(args: Array<String>, i: Int, add: Boolean): Array<String> {
		val strings = ArrayList(args.asList())
		if (add) {
			strings.add("")
		} else {
			strings.add(i, "")
		}
		return strings.toTypedArray()
	}

	/** 打印主命令的帮助信息。 */
	fun printUsage() {
		LogHelper.setLogLevel(LogHelper.LogLevelEnum.ERROR) // 打印帮助时先屏蔽日志

		// 按字段声明顺序打印（jcommander 默认按描述排序）
		val out = System.out
		out.println()
		out.println("kadx - dex to java decompiler, version: " + KadxDecompiler.getVersion())
		out.println()
		out.println("usage: kadx [command] [options] " + jc.getMainParameterDescription())

		out.println("commands (use '<command> --help' for command options):")
		for (command in jc.getCommands().keys) {
			out.println("  " + command + "\t  - " + jc.getUsageFormatter().getCommandDescription(command))
		}
		out.println()

		val maxNamesLen = printOptions(jc, out, true)
		out.println(appendPluginOptions(maxNamesLen))
		out.println()
		out.println("Environment variables:")
		out.println("  KADX_DISABLE_XML_SECURITY - set to 'true' to disable all security checks for XML files")
		out.println("  KADX_DISABLE_ZIP_SECURITY - set to 'true' to disable all security checks for zip files")
		out.println("  KADX_ZIP_MAX_ENTRIES_COUNT - maximum allowed number of entries in zip files (default: 100 000)")
		out.println("  KADX_CONFIG_DIR - custom config directory, using system by default")
		out.println("  KADX_CACHE_DIR - custom cache directory, using system by default")
		out.println("  KADX_TMP_DIR - custom temp directory, using system by default")
		out.println()
		out.println("Examples:")
		out.println("  kadx -d out classes.dex")
		out.println("  kadx --rename-flags \"none\" classes.dex")
		out.println("  kadx --rename-flags \"valid, printable\" classes.dex")
		out.println("  kadx --log-level ERROR app.apk")
		out.println("  kadx -Pdex-input.verify-checksum=no app.apk")
	}

	/** 打印子命令的帮助信息。 */
	fun printUsage(subCommander: JCommander) {
		val out = System.out
		out.println("usage: " + subCommander.getProgramName() + " [options]")
		printOptions(subCommander, out, false)
	}

	private fun printOptions(jc: JCommander, out: PrintStream, addDefaults: Boolean): Int {
		out.println("options:")

		val params = jc.getParameters()
		val paramsMap = HashMap<String, ParameterDescription>(params.size)
		var maxNamesLen = 0
		for (p in params) {
			paramsMap[p.getParameterized().getName()] = p
			var len = p.getNames().length
			val valueDesc = getValueDesc(p)
			if (valueDesc != null) {
				len += 1 + valueDesc.length
			}
			maxNamesLen = Math.max(maxNamesLen, len)
		}
		maxNamesLen += 3

		val args = jc.getObjects()[0]
		for (f in getFields(args.javaClass)) {
			val name = f.name
			val p = paramsMap[name]
			if (p == null || p.getParameter().hidden()) {
				continue
			}
			val opt = StringBuilder()
			opt.append("  ").append(p.getNames())
			val valueDesc = getValueDesc(p)
			if (valueDesc != null) {
				opt.append(' ').append(valueDesc)
			}
			addSpaces(opt, maxNamesLen - opt.length)
			val description = p.getDescription()
			if (description.contains("\n")) {
				val lines = description.split("\n")
				opt.append("- ").append(lines[0])
				for (i in 1 until lines.size) {
					opt.append('\n')
					addSpaces(opt, maxNamesLen + 2)
					opt.append(lines[i])
				}
			} else {
				opt.append("- ").append(description)
			}
			if (addDefaults) {
				val defaultValue = getDefaultValue(args, f)
				if (defaultValue != null &&
					defaultValue.isNotEmpty() &&
					!description.contains("(default)")
				) {
					opt.append(", default: ").append(defaultValue)
				}
			}
			out.println(opt)
		}
		return maxNamesLen
	}

	private fun getValueDesc(p: ParameterDescription): String? {
		val parameterAnnotation: Parameter? = p.getParameterAnnotation()
		return parameterAnnotation?.defaultValueDescription
	}

	/** 获取指定类及其所有父类声明的全部字段。 */
	private fun getFields(clazz: Class<*>): List<Field> {
		val fieldList = ArrayList<Field>()
		var cls: Class<*>? = clazz
		while (cls != null) {
			fieldList.addAll(cls.declaredFields.toList())
			cls = cls.superclass
		}
		return fieldList
	}

	private fun getDefaultValue(args: Any, f: Field): String? {
		try {
			val fieldType = f.type
			if (fieldType == Int::class.javaPrimitiveType) {
				return Integer.toString(f.getInt(args))
			}
			if (fieldType == String::class.java) {
				return f.get(args) as String?
			}
			if (Enum::class.java.isAssignableFrom(fieldType)) {
				val value = f.get(args) as Enum<*>?
				if (value != null) {
					return value.name.lowercase(Locale.ROOT)
				}
			}
		} catch (e: Exception) {
			// 忽略取值失败
		}
		return null
	}

	private fun addSpaces(str: StringBuilder, count: Int) {
		for (i in 0 until count) {
			str.append(' ')
		}
	}

	/** 打印插件选项（加载插件以获取全部选项描述）。 */
	private fun appendPluginOptions(maxNamesLen: Int): String {
		val sb = StringBuilder()
		// 加载并初始化所有插件，以便打印全部选项
		KadxDecompiler(argsObj.toKadxArgs()).use { decompiler ->
			val pluginManager = decompiler.getPluginManager()
			pluginManager.load(decompiler.getArgs().pluginLoader)
			pluginManager.initAll(decompiler)
			try {
				for (plugin in pluginManager.allPlugins) {
					val options = plugin.options
					if (options != null) {
						appendPlugin(plugin.pluginInfo, options, sb, maxNamesLen)
					}
				}
			} finally {
				pluginManager.unloadAll()
			}
		}
		if (sb.isEmpty()) {
			return ""
		}
		return "\nPlugin options (-P<name>=<value>):$sb"
	}

	private fun appendPlugin(pluginInfo: KadxPluginInfo, options: KadxPluginOptions, out: StringBuilder, maxNamesLen: Int): Boolean {
		val descs: List<OptionDescription> = options.getOptionsDescriptions()
		if (descs.isEmpty()) {
			return false
		}
		out.append("\n  ")
		out.append(pluginInfo.getPluginId()).append(": ").append(pluginInfo.getDescription())
		for (desc in descs) {
			val opt = StringBuilder()
			opt.append("    - ").append(desc.name())
			addSpaces(opt, maxNamesLen - opt.length)
			opt.append("- ").append(desc.description())
			if (desc.values().isNotEmpty()) {
				opt.append(", values: ").append(desc.values())
			}
			if (desc.defaultValue() != null) {
				opt.append(", default: ").append(desc.defaultValue())
			}
			out.append("\n").append(opt)
		}
		return true
	}
}
