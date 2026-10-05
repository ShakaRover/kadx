// SPDX-License-Identifier: Apache-2.0
package kadx.analysis.callgraph

import kadx.analysis.callgraph.api.ICallGraphBuilder
import kadx.api.KadxDecompiler

/**
 * 调用图分析的入口点工具类
 *
 * 提供工厂方法来创建调用图构建器。
 * 用户通过这个类的 [builder] 方法开始配置和构建调用图分析。
 *
 * @see ICallGraphBuilder 返回的构建器接口
 */
object KadxCallGraph {
	/**
	 * 创建一个调用图构建器
	 *
	 * 这是启动调用图分析的入口点。传入反编译器实例后，
	 * 可以链式调用各种配置方法，最后生成调用图。
	 *
	 * @param decompiler kadx 反编译器实例，提供访问 Dex 文件的上下文
	 * @return 配置好的 [ICallGraphBuilder] 实例
	 *
	 * @sample
	 * KadxCallGraph.builder(decompiler)
	 *     .includePackages("com.example..")
	 *     .resolvedOnly(true)
	 *     .build()
	 */
	fun builder(decompiler: KadxDecompiler): ICallGraphBuilder = CallGraphBuilder(decompiler)
}
