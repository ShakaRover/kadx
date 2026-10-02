package jadx.core.utils.android

/**
 * AndroidManifest.xml 中可解析的应用属性枚举。
 *
 * **用途**：调用方用 [java.util.EnumSet] 选择需要从清单里提取哪些属性，
 * [AndroidManifestParser] 据此按需解析，避免做无用功。
 */
enum class AppAttribute {
	APPLICATION_LABEL,
	MIN_SDK_VERSION,
	COMPILE_SDK_VERSION,
	TARGET_SDK_VERSION,
	VERSION_CODE,
	VERSION_NAME,
	MAIN_ACTIVITY,
	APPLICATION,
}
