package jadx.core.plugins.versions

import jadx.core.Jadx
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 校验插件声明的 `requiredJadxVersion` 是否与当前 jadx 版本兼容。
 *
 * **做什么**：插件在元信息里声明形如 `"1.5.1, r3000"` 的最低版本要求
 * （发布版本号 + 不稳定修订号）。本类解析该字符串，并根据当前 jadx 是
 * 稳定版还是 `r` 不稳定版，用 [VersionComparator] 判断是否满足。
 *
 * **转换注意**：
 * - 静态方法 [isJadxCompatible]、[verify] 放进 `companion object` 并加 `@JvmStatic`；
 * - [parse] 与 [RequiredVersionData] 是私有实现细节，保持私有；
 * - `dev` 版本跳过实际比较但仍解析，以便尽早暴露格式错误。
 */
class VerifyRequiredVersion {

	companion object {
		/** 便捷静态方法：判断给定要求是否与当前 jadx 版本兼容。 */
		@JvmStatic
		fun isJadxCompatible(reqVersionStr: String?): Boolean = VerifyRequiredVersion().isCompatible(reqVersionStr)

		/** 仅校验 `requiredJadxVersion` 格式是否合法，不比较版本。 */
		@JvmStatic
		fun verify(requiredJadxVersion: String) {
			try {
				parse(requiredJadxVersion)
			} catch (e: Exception) {
				throw IllegalArgumentException("Malformed 'requiredJadxVersion': " + e.message, e)
			}
		}

		/** 要求字符串格式：`<发布版本>, <不稳定修订>`，例如 `1.5.1, r3000`。 */
		private val REQ_VER_FORMAT: Pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+),\\s+(r\\d+)")

		/** 解析要求字符串，格式不符时抛 [RuntimeException]。 */
		private fun parse(reqVersionStr: String): RequiredVersionData {
			val matcher: Matcher = REQ_VER_FORMAT.matcher(reqVersionStr)
			if (!matcher.matches()) {
				throw RuntimeException("Expect format: " + REQ_VER_FORMAT + ", got: " + reqVersionStr)
			}
			return RequiredVersionData(matcher.group(1), matcher.group(2))
		}
	}

	/** 当前 jadx 版本字符串。 */
	private val jadxVersion: String

	/** 当前是否为 `r` 开头的不稳定版本。 */
	private val unstable: Boolean

	/** 当前是否为开发版（版本号等于 [Jadx.VERSION_DEV]）。 */
	private val dev: Boolean

	constructor() : this(Jadx.getVersion())

	constructor(jadxVersion: String) {
		this.jadxVersion = jadxVersion
		this.unstable = jadxVersion.startsWith("r")
		this.dev = jadxVersion == Jadx.VERSION_DEV
	}

	/**
	 * 判断给定要求是否与当前版本兼容。
	 *
	 * @param reqVersionStr 形如 `"1.5.1, r3000"`；为空 / null 表示不限制
	 * @return 当前版本不低于要求时返回 true
	 */
	fun isCompatible(reqVersionStr: String?): Boolean {
		if (reqVersionStr == null || reqVersionStr.isEmpty()) {
			return true
		}
		val reqVer = parse(reqVersionStr)
		if (dev) {
			// 开发版始终兼容，但仍执行解析以校验格式
			return true
		}
		if (unstable) {
			return VersionComparator.checkAndCompare(jadxVersion, reqVer.getUnstableRev()) >= 0
		}
		return VersionComparator.checkAndCompare(jadxVersion, reqVer.getReleaseVer()) >= 0
	}

	fun getJadxVersion(): String = jadxVersion

	/** 解析后的版本要求数据。 */
	private class RequiredVersionData(
		private val releaseVer: String,
		private val unstableRev: String,
	) {
		fun getReleaseVer(): String = releaseVer

		fun getUnstableRev(): String = unstableRev
	}
}
