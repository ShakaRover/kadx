package jadx.core.utils.android

import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.security.IJadxSecurity
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.xmlgen.ResContainer
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.Objects

/**
 * AndroidManifest.xml 解析器。
 *
 * **用途**：从已解码的清单 XML（以及可选的 `strings.xml`）中提取应用名、版本号、
 * 主 Activity、min/target/compileSdkVersion 等，供导出与 GUI 使用。
 *
 * **Kotlin 转换说明**：
 * - `androidManifest` 字段在清单缺失时为 null（原 Java 靠 `isManifestFound()` 判断），
 *   因此如实声明为 `Document?`，用私有 [manifest] 在真正解析时取出非空引用；
 * - 构造器第一个参数可空（[getAndroidManifest] 可能返回 null），[parseAndroidManifest] 也接受可空；
 * - 静态方法 [getAndroidManifest] 放入 `companion object` + `@JvmStatic`，Java 调用不变。
 */
class AndroidManifestParser {

	private val androidManifest: Document?
	private val appStrings: Document?
	private val parseAttrs: EnumSet<AppAttribute>
	private val security: IJadxSecurity

	constructor(androidManifestRes: ResourceFile?, parseAttrs: EnumSet<AppAttribute>, security: IJadxSecurity) :
		this(androidManifestRes, null, parseAttrs, security)

	constructor(
		androidManifestRes: ResourceFile?,
		appStrings: ResContainer?,
		parseAttrs: EnumSet<AppAttribute>,
		security: IJadxSecurity,
	) {
		this.parseAttrs = parseAttrs
		this.security = Objects.requireNonNull(security)

		this.androidManifest = parseAndroidManifest(androidManifestRes)
		this.appStrings = parseAppStrings(appStrings)
	}

	/** 清单是否存在（解析成功）。 */
	fun isManifestFound(): Boolean = androidManifest != null

	/** 执行解析；清单缺失时抛出 [JadxRuntimeException]。 */
	fun parse(): ApplicationParams {
		if (!isManifestFound()) {
			throw JadxRuntimeException("AndroidManifest.xml is missing")
		}
		return parseAttributes()
	}

	private fun manifest(): Document = checkNotNull(androidManifest)

	private fun parseAttributes(): ApplicationParams {
		val appParams = ApplicationParams()

		val manifest = manifest().getElementsByTagName("manifest").item(0) as Element?
		val usesSdk = manifest().getElementsByTagName("uses-sdk").item(0) as Element?

		if (parseAttrs.contains(AppAttribute.APPLICATION_LABEL)) {
			appParams.applicationLabel = getApplicationLabel()
		}
		if (usesSdk != null) {
			if (parseAttrs.contains(AppAttribute.MIN_SDK_VERSION)) {
				appParams.minSdkVersion = Utils.safeParseInteger(usesSdk.getAttribute("android:minSdkVersion"))
			}
			if (parseAttrs.contains(AppAttribute.TARGET_SDK_VERSION)) {
				val stringTargetSdk = usesSdk.getAttribute("android:targetSdkVersion")
				if (!stringTargetSdk.isEmpty()) {
					appParams.targetSdkVersion = Utils.safeParseInteger(stringTargetSdk)
				} else {
					if (appParams.minSdkVersion == null) {
						appParams.minSdkVersion = Utils.safeParseInteger(usesSdk.getAttribute("android:minSdkVersion"))
					}
					appParams.targetSdkVersion = appParams.minSdkVersion
				}
			}
			if (parseAttrs.contains(AppAttribute.COMPILE_SDK_VERSION)) {
				val stringCompileSdk = usesSdk.getAttribute("android:compileSdkVersion")
				if (!stringCompileSdk.isEmpty()) {
					appParams.compileSdkVersion = Utils.safeParseInteger(stringCompileSdk)
				} else {
					appParams.compileSdkVersion = appParams.targetSdkVersion
				}
			}
		}
		if (manifest != null) {
			if (parseAttrs.contains(AppAttribute.VERSION_CODE)) {
				appParams.versionCode = Utils.safeParseInteger(manifest.getAttribute("android:versionCode"))
			}
			if (parseAttrs.contains(AppAttribute.VERSION_NAME)) {
				appParams.versionName = manifest.getAttribute("android:versionName")
			}
		}
		if (parseAttrs.contains(AppAttribute.MAIN_ACTIVITY)) {
			appParams.mainActivity = getMainActivityName()
		}
		if (parseAttrs.contains(AppAttribute.APPLICATION)) {
			appParams.application = getApplicationName()
		}
		return appParams
	}

	private fun getApplicationLabel(): String {
		val application = manifest().getElementsByTagName("application").item(0) as Element
		if (application.hasAttribute("android:label")) {
			var appLabelName = application.getAttribute("android:label")
			if (appLabelName.startsWith("@string")) {
				val stringsDoc = appStrings
					?: throw IllegalArgumentException("APPLICATION_LABEL attribute requires non null appStrings")
				appLabelName = appLabelName.split("/")[1]
				val strings = stringsDoc.getElementsByTagName("string")

				for (i in 0 until strings.getLength()) {
					val stringName = strings.item(i)
						.attributes
						.getNamedItem("name")
						.nodeValue

					if (stringName == appLabelName) {
						return strings.item(i).textContent
					}
				}
			} else {
				return appLabelName
			}
		}
		return "UNKNOWN"
	}

	private fun getMainActivityName(): String? {
		var mainActivityName = getMainActivityNameThroughActivityTag()
		if (mainActivityName == null) {
			mainActivityName = getMainActivityNameThroughActivityAliasTag()
		}
		return mainActivityName
	}

	private fun getApplicationName(): String? {
		val application = manifest().getElementsByTagName("application").item(0) as Element
		if (application.hasAttribute("android:name")) {
			return application.getAttribute("android:name")
		}
		return null
	}

	private fun getMainActivityNameThroughActivityAliasTag(): String? {
		val activityAliasNodes = manifest().getElementsByTagName("activity-alias")
		for (i in 0 until activityAliasNodes.getLength()) {
			val activityElement = activityAliasNodes.item(i) as Element
			if (isMainActivityElement(activityElement)) {
				return activityElement.getAttribute("android:targetActivity")
			}
		}
		return null
	}

	private fun getMainActivityNameThroughActivityTag(): String? {
		val activityNodes = manifest().getElementsByTagName("activity")
		for (i in 0 until activityNodes.getLength()) {
			val activityElement = activityNodes.item(i) as Element
			if (isMainActivityElement(activityElement)) {
				return activityElement.getAttribute("android:name")
			}
		}
		return null
	}

	/** 判断该 `activity`/`activity-alias` 是否声明了 `MAIN` + `LAUNCHER` intent-filter。 */
	private fun isMainActivityElement(element: Element): Boolean {
		val intentFilterNodes = element.getElementsByTagName("intent-filter")
		for (j in 0 until intentFilterNodes.getLength()) {
			val intentFilterElement = intentFilterNodes.item(j) as Element
			val actionNodes = intentFilterElement.getElementsByTagName("action")
			val categoryNodes = intentFilterElement.getElementsByTagName("category")

			var isMainAction = false
			var isLauncherCategory = false

			for (k in 0 until actionNodes.getLength()) {
				val actionElement = actionNodes.item(k) as Element
				val actionName = actionElement.getAttribute("android:name")
				if ("android.intent.action.MAIN" == actionName) {
					isMainAction = true
					break
				}
			}

			for (k in 0 until categoryNodes.getLength()) {
				val categoryElement = categoryNodes.item(k) as Element
				val categoryName = categoryElement.getAttribute("android:name")
				if ("android.intent.category.LAUNCHER" == categoryName) {
					isLauncherCategory = true
					break
				}
			}

			if (isMainAction && isLauncherCategory) {
				return true
			}
		}
		return false
	}

	private fun parseXml(xmlContent: String): Document {
		try {
			ByteArrayInputStream(xmlContent.toByteArray(StandardCharsets.UTF_8)).use { xmlStream ->
				val document = security.parseXml(xmlStream)
				document.documentElement.normalize()
				return document
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Can not parse xml content", e)
		}
	}

	private fun parseAppStrings(appStrings: ResContainer?): Document? {
		if (appStrings == null) {
			return null
		}
		val content = appStrings.getText().getCodeStr()
		return parseXml(content)
	}

	private fun parseAndroidManifest(androidManifest: ResourceFile?): Document? {
		if (androidManifest == null) {
			return null
		}
		val content = androidManifest.loadContent().getText().getCodeStr()
		return parseXml(content)
	}

	companion object {
		/** 在资源列表中查找 AndroidManifest.xml；找不到返回 null。 */
		@JvmStatic
		fun getAndroidManifest(resources: List<ResourceFile>): ResourceFile? = resources.stream()
			.filter { resourceFile -> resourceFile.getType() === ResourceType.MANIFEST }
			.findFirst()
			.orElse(null)
	}
}
