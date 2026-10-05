package kadx.api.plugins

import kadx.api.ResourceFile
import kadx.api.ResourcesLoader
import java.io.Closeable
import java.io.File

/**
 * 自定义资源加载器接口：让插件有机会在 kadx 默认资源解析之前接管某些文件。
 *
 * **做什么**：`ResourcesLoader` 在加载输入文件里的资源时，会先依次询问所有已注册的
 * [CustomResourcesLoader]；只要有一个返回 `true`，就认为该文件已被处理，跳过默认逻辑。
 *
 * **为什么保留这个接口**：这是插件扩展点（如 apks/apkm/xapk 输入插件用它把容器内的
 * apk 解出来再交给默认加载器），会被 Java 插件实现，因此方法名、参数类型、JVM 签名
 * 必须与原 Java 完全一致。
 */
interface CustomResourcesLoader : Closeable {

	/**
	 * 尝试把 [file] 解析成资源并追加到 [list]。
	 *
	 * @param loader 默认资源加载器，可在自定义解包后回调它的 `defaultLoadFile` 继续处理
	 * @param list   已加载资源的输出列表（Java 侧仍写作 `List<ResourceFile>`，需要 add）
	 * @param file   待加载的输入文件
	 * @return 是否已处理该文件（true 表示默认加载逻辑应跳过它）
	 */
	fun load(loader: ResourcesLoader, list: MutableList<ResourceFile>, file: File): Boolean
}
