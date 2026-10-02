package jadx.core.utils.android

import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * Android 资源 id → 名称映射表（内置 `res-map.txt`）。
 *
 * **用途**：在无法从 APK 中解析出资源名时，用系统/框架资源 id 的已知名称做兜底显示。
 *
 * **Kotlin 转换说明**：全部为静态成员，用 `object` + `@JvmStatic` 保持 Java 调用
 * `AndroidResourcesMap.getResName(...)` 不变；映射表在单例初始化时惰性加载一次。
 */
object AndroidResourcesMap {

	private val RES_MAP: Map<Int, String> = loadBundled()

	/** 按资源 id 查询名称；未知 id 返回 null。 */
	@JvmStatic
	fun getResName(resId: Int): String? = RES_MAP[resId]

	/** 返回整张映射表（只读视图）。 */
	@JvmStatic
	fun getMap(): Map<Int, String> = RES_MAP

	private fun loadBundled(): Map<Int, String> {
		try {
			val stream = checkNotNull(AndroidResourcesMap::class.java.getResourceAsStream("/android/res-map.txt"))
			return stream.use { TextResMapFile.read(it) }
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to load android resource file (res-map.txt)", e)
		}
	}
}
