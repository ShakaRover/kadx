package jadx.core.utils.android

import jadx.api.JadxDecompiler
import jadx.api.JavaClass

/**
 * 从 AndroidManifest.xml 解析出的应用参数容器。
 *
 * **用途**：承载应用名、版本、SDK 版本、主 Activity 等字段，供导出 Gradle 工程、
 * GUI 跳转等场景使用。
 *
 * **Kotlin 转换说明**：
 * - 全部字段声明为 Kotlin `var` 属性（可空，未解析到即为 null），
 *   编译器生成的 `getXxx()/setXxx()` 与原 Java 完全一致，Java 调用方零改动；
 * - [getApplicationJavaClass]/[getMainActivityJavaClass] 是额外查询方法，保留原名。
 */
class ApplicationParams {

	var application: String? = null

	var applicationLabel: String? = null

	var mainActivity: String? = null

	var compileSdkVersion: Int? = null

	var minSdkVersion: Int? = null

	var targetSdkVersion: Int? = null

	var versionCode: Int? = null

	var versionName: String? = null

	/** 按别名全名在反编译器里查找 Application 对应的 [JavaClass]。 */
	fun getApplicationJavaClass(decompiler: JadxDecompiler): JavaClass? {
		val app = application ?: return null
		return decompiler.searchJavaClassByAliasFullName(app)
	}

	/** 按别名全名在反编译器里查找主 Activity 对应的 [JavaClass]。 */
	fun getMainActivityJavaClass(decompiler: JadxDecompiler): JavaClass? {
		val activity = mainActivity ?: return null
		return decompiler.searchJavaClassByAliasFullName(activity)
	}
}
