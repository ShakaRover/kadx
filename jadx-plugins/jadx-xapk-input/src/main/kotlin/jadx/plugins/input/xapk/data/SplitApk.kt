package jadx.plugins.input.xapk.data

/**
 * XApk manifest 中声明的一个 split apk。
 *
 * **背景**：由 Gson 反序列化（直接反射写字段），字段在 JSON 缺少对应键时保持 null，
 * 所以两个属性都是可空的。
 */
public class SplitApk {
	/** @Nullable split apk 文件名（相对 xapk 根目录） */
	public var file: String? = null

	/** @Nullable split apk 的 id（如 base / config.arm64_v8a） */
	public var id: String? = null
}
