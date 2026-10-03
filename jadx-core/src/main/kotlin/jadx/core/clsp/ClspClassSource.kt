package jadx.core.clsp

/**
 * classpath 中某个类的来源标记：它来自哪个 jar 包。
 *
 * **用途**：jadx 构建类路径图（.jcst 文件）时，会记录每个类来自 android.jar、
 * 还是应用自身（APP）。反编译时据此决定是否使用 Android SDK 的特殊处理。
 *
 * **Kotlin 转换说明**：原 Java 枚举的 `jarFile` 字段 + `getJarFile()` 改为构造参数
 * [jarFile] 属性，JVM 上仍生成 `getJarFile()`，Java 调用方无差异；
 * 静态方法 [getClspClassSource] 放入 companion 并标注 `@JvmStatic`。
 */
enum class ClspClassSource(val jarFile: String) {
	/** 应用自身的类（没有对应的 jar 文件） */
	APP(""),

	/** Android 核心库 android.jar */
	CORE("android.jar"),

	/** Android Car 库 */
	ANDROID_CAR("android.car.jar"),

	/** 已废弃的 Apache HTTP 客户端库 */
	APACHE_HTTP_LEGACY_CLIENT("org.apache.http.legacy.jar"),
	;

	companion object {
		/**
		 * 根据 jar 文件名反查来源枚举。
		 *
		 * 遍历所有枚举值，找到 `jarFile` 完全相等的一项；找不到时回退为 [APP]
		 * （与原 Java 行为一致，例如无法识别的第三方 jar）。
		 */
		fun getClspClassSource(jarFile: String): ClspClassSource {
			for (classSource in values()) {
				if (classSource.jarFile == jarFile) {
					return classSource
				}
			}
			return APP
		}
	}
}
