package jadx.api.plugins.input

import jadx.api.plugins.input.data.IClassData
import java.io.Closeable
import java.util.function.Consumer

/**
 * 代码加载器接口：输入插件向 jadx-core 交付解析结果的入口。
 *
 * **背景**：每个输入插件（dex / class file / smali...）实现本接口，
 * jadx-core 通过 [visitClasses] 逐个拉取类数据；继承 [Closeable] 以便
 * 释放底层文件句柄等资源。
 */
public interface ICodeLoader : Closeable {

	/**
	 * 遍历所有解析出的类，每发现一个就回调 [consumer]。
	 * @param consumer 接收每个 [IClassData] 的消费者
	 */
	public fun visitClasses(consumer: Consumer<IClassData>)

	/** @return 是否没有可交付的类（空输入）*/
	public val isEmpty: Boolean
}
