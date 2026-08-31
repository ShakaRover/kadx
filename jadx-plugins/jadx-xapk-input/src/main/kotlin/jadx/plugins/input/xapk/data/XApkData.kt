package jadx.plugins.input.xapk.data

import java.nio.file.Path

/**
 * 解包后的 XApk 数据：manifest + 临时目录 + apk/资源文件列表。
 *
 * **背景**：由 [jadx.plugins.input.xapk.XApkLoader] 在解包时创建，
 * apks 是 manifest 声明的 split apk（交给 dex 解析），files 是其余资源文件。
 */
public class XApkData(
	public val manifest: XApkManifest,
	public val tmpDir: Path,
	public val apks: List<Path>,
	public val files: List<Path>,
)
