package jadx.cli

import com.beust.jcommander.JCommander
import jadx.cli.commands.CommandPlugins
import jadx.cli.commands.ICommand
import jadx.core.utils.exceptions.JadxArgsValidateException
import java.util.LinkedHashMap

/**
 * CLI 子命令注册表。
 *
 * **做什么**：维护“命令名 -> [ICommand]”的映射，并负责把命令注册到 jcommander、
 * 以及在解析后分派执行。
 *
 * **为什么这样写**：原 Java 使用 `private static final Map` + `static {}` 代码块注册默认命令。
 * Kotlin 用 `companion object` 的 `init {}` 块达到同样效果；所有静态方法加 `@JvmStatic`
 * 以便 Java 调用方写法不变。
 */
class JadxCLICommands {

	companion object {
		private val COMMANDS_MAP: MutableMap<String, ICommand> = LinkedHashMap()

		init {
			// 注册内置的 plugins 命令
			register(CommandPlugins())
		}

		/** 注册一个子命令（同名会覆盖）。 */
		@JvmStatic
		fun register(command: ICommand) {
			COMMANDS_MAP[command.name()] = command
		}

		/** 把所有已注册命令追加到 jcommander builder。 */
		@JvmStatic
		fun append(builder: JCommander.Builder) {
			COMMANDS_MAP.forEach { (name, command) -> builder.addCommand(name, command) }
		}

		/** 执行已解析出的子命令。 */
		@JvmStatic
		fun process(jcw: JCommanderWrapper, jc: JCommander, parsedCommand: String): Boolean {
			val command = COMMANDS_MAP[parsedCommand]
				?: throw JadxArgsValidateException(
					"Unknown command: $parsedCommand. Expected one of: ${COMMANDS_MAP.keys}",
				)
			val subCommander = checkNotNull(jc.getCommands()[parsedCommand])
			command.process(jcw, subCommander)
			return true
		}
	}
}
