@file:Suppress("ktlint:standard:property-naming")

package jadx.gui.plugins.quark

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import jadx.core.utils.Utils

/**
 * Quark 引擎分析报告的数据模型（由 Gson 从 JSON 反序列化）。
 *
 * **做什么**：映射 Quark 输出的 `crimes` / `native_api` / `register` 等字段；
 * [validate] 做最小校验，[Crime.parseConfidence] 把 `"80%"` 转成整数。
 *
 * **为什么保留下划线字段名**：JSON 字段名即如此（如 `native_api`），
 * 改名会破坏反序列化，故用文件级 `@Suppress` 关掉命名规则告警。
 *
 * **注意**：这些类由 Gson 通过反射填充字段，不是 `data class`，
 * 且字段保持可空/默认值以容忍缺字段的 JSON。
 */
class QuarkReportData {

	class Crime {
		var crime: String = ""
		var confidence: String? = null
		var permissions: List<String>? = null
		var native_api: List<Method>? = null
		var combination: List<JsonElement>? = null
		var register: List<Map<String, InvokePlace>>? = null

		/** 把 `"80%"` 之类的置信度转成整数。 */
		fun parseConfidence(): Int = Integer.parseInt(checkNotNull(confidence).replace("%", ""))

		override fun toString(): String {
			val sb = StringBuffer("Crime{")
			sb.append("crime='").append(crime).append('\'')
			sb.append(", confidence='").append(confidence).append('\'')
			sb.append(", permissions=").append(permissions)
			sb.append(", native_api=").append(native_api)
			sb.append(", combination=").append(combination)
			sb.append(", register=").append(register)
			sb.append('}')
			return sb.toString()
		}
	}

	class Method {
		@SerializedName("class")
		var cls: String = ""
		var method: String = ""
		var descriptor: String? = null

		override fun toString(): String {
			val sb = StringBuilder()
			sb.append(Utils.cleanObjectName(cls)).append(".").append(method)
			if (descriptor != null) {
				sb.append(descriptor)
			}
			return sb.toString()
		}
	}

	class InvokePlace {
		var first: List<String>? = null
		var second: List<String>? = null
	}

	var apk_filename: String? = null
	var threat_level: String? = null
	var total_score: Int = 0
	var crimes: MutableList<Crime>? = null

	/** 校验报告数据：`crimes` 必须存在，且每条 crime 的置信度必须可解析。 */
	fun validate() {
		val crimesList = crimes ?: throw RuntimeException("Invalid data: \"crimes\" list missing")
		for (crime in crimesList) {
			if (crime.confidence == null) {
				throw RuntimeException("Confidence value missing: $crime")
			}
			try {
				crime.parseConfidence()
			} catch (e: Exception) {
				throw RuntimeException("Invalid crime entry: $crime")
			}
		}
	}
}
