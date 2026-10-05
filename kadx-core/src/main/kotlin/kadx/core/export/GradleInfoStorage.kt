package kadx.core.export

/**
 * 记录导出 Gradle 工程时需要的一些“能力开关”。
 *
 * **用途**：反编译/资源解析过程中（例如发现矢量图、Apache HTTP 旧库、`switch` 里引用
 * 资源 ID 等情况）会在这里打标记；导出 Gradle 工程时再读取这些标记，决定是否往
 * `build.gradle` / `gradle.properties` 里追加相应配置。
 *
 * **Kotlin 转换说明**：原 Java 的 `isXxx()` / `setXxx()` 直接改写为 Kotlin 布尔属性
 * `isXxx`。Kotlin 对以 `is` 开头的布尔属性采用特殊命名规则：getter 名保持
 * `isXxx()`，setter 名把 `is` 换成 `set` 即 `setXxx()`，因此 **Java 调用方无需改动**；
 * 而 Kotlin 调用方不能再写 `setXxx(true)`，需改为属性赋值 `isXxx = true`。
 */
class GradleInfoStorage {

	/** 是否使用了矢量图的 pathData（低版本 SDK 需要 support library 支持）。 */
	var isVectorPathData: Boolean = false

	/** 是否使用了矢量图的 fillType（低版本 SDK 需要 support library 支持）。 */
	var isVectorFillType: Boolean = false

	/** 是否引用了已废弃的 org.apache.http.legacy 库。 */
	var isUseApacheHttpLegacy: Boolean = false

	/**
	 * 资源 ID 是否不是 `final` 常量。
	 *
	 * Android Gradle Plugin 8.0+ 默认资源 ID 非 final；若代码把资源 ID 当作
	 * `switch` 的 case 键使用，就需要在 `gradle.properties` 里显式设置
	 * `android.nonFinalResIds=false`。
	 */
	var isNonFinalResIds: Boolean = false
}
