package jadx.plugins.input.xapk.data

import com.google.gson.annotations.SerializedName

/**
 * XApk 包的 manifest.json 结构。
 *
 * **背景**：由 Gson 反序列化（反射写字段），JSON 缺少键时字段保持默认值，
 * 所以 [splitApks] 可空；[version] 为原始 int，Gson 未设置时为 0。
 */
public class XApkManifest {

	@SerializedName("xapk_version")
	public var version: Int = 0

	/** @Nullable split apk 列表（JSON 缺少 split_apks 键时为 null） */
	@SerializedName("split_apks")
	public var splitApks: List<SplitApk>? = null
}
