package kadx.commons.app

import java.util.Locale

/**
 * 系统和 JVM 环境信息。
 *
 * 所有属性都是"static final"风格：companion object 中的 val，随类加载一次性求值。
 */
class KadxSystemInfo {
	companion object {
		/** JVM 名称（如 OpenJDK 64-Bit Server VM） */
		val JAVA_VM = System.getProperty("java.vm.name", "?")

		/** Java 版本号字符串（如 17.0.x） */
		val JAVA_VER = System.getProperty("java.version", "?")

		val OS_NAME = System.getProperty("os.name", "?")

		val OS_ARCH = System.getProperty("os.arch", "?")

		val OS_VERSION = System.getProperty("os.version", "?")

		// 操作系统名称转小写，便于后面的前缀/等值判断
		val OS_NAME_LOWER = OS_NAME.lowercase(Locale.ENGLISH)

		/** 是否 Windows 系统 */
		val IS_WINDOWS = OS_NAME_LOWER.startsWith("windows")

		/** 是否 macOS（包括 Apple Silicon） */
		val IS_MAC = OS_NAME_LOWER.startsWith("mac")

		/** 是否 Linux（非 Windows 且非 Mac） */
		val IS_LINUX = !IS_WINDOWS && !IS_MAC

		/** 类 Unix 系统（Linux + macOS），用于区分命令行参数风格等场景 */
		val IS_UNIX = !IS_WINDOWS

		// CPU 架构转小写，便于下面的等值判断
		val OS_ARCH_LOWER = OS_ARCH.lowercase(Locale.ENGLISH)

		/** x86-64 架构（amd64） */
		val IS_AMD64 = OS_ARCH_LOWER.equals("amd64")

		/** ARM 64位架构（aarch64 / Apple Silicon） */
		val IS_ARM64 = OS_ARCH_LOWER.equals("aarch64")
	}
}
