package jadx.api.plugins.input

import java.nio.file.Path
import java.util.List

/**
 * 代码输入接口：把一组文件路径交给输入插件解析。
 *
 * **背景**：jadx-core / GUI 不直接依赖具体输入格式，而是通过本接口
 * 让已注册的输入插件（dex / class file...）加载文件并返回 [ICodeLoader]。
 */
public interface JadxCodeInput {

	/**
	 * @param input 要解析的文件路径列表
	 * @return 交付解析结果的代码加载器；无内容时返回 EmptyCodeLoader.INSTANCE
	 */
	public fun loadFiles(input: java.util.List<Path>): ICodeLoader
}
