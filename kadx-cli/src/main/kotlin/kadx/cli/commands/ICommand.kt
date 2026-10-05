package kadx.cli.commands

import com.beust.jcommander.JCommander
import kadx.cli.JCommanderWrapper

/**
 * kadx CLI 子命令接口（例如 `plugins` 命令）。
 *
 * **做什么**：定义子命令的注册名与执行入口。
 *
 * **为什么保持 Java 风格签名**：[CommandPlugins] 等实现类可能由 Java 或 Kotlin 编写，
 * 方法名 `name()` / `process(...)` 与参数类型原样保留，保证 Java 实现方零改动。
 */
interface ICommand {

	/** 子命令名称（用于 `kadx <command> ...`）。 */
	fun name(): String

	/** 执行该子命令。[subCommander] 是该子命令对应的 jcommander 解析器。 */
	fun process(jcw: JCommanderWrapper, subCommander: JCommander)
}
