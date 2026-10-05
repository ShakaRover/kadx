// SPDX-License-Identifier: Apache-2.0
package kadx.analysis.callgraph.api

/**
 * 调用图构建器的配置接口（Fluent API 风格）
 *
 * 用于配置和构建调用图分析器。通过链式调用来设置各种选项，
 * 最后调用 [build] 方法生成调用图。
 *
 * @see ICallGraph 构建结果
 */
interface ICallGraphBuilder {
	/**
	 * 设置包名过滤器，只包含匹配这些包的类和方法
	 *
	 * @param pkgFilter 包名过滤模式（支持通配符）
	 * @return this 引用，用于链式调用
	 */
	fun includePackages(pkgFilter: String): ICallGraphBuilder

	/**
	 * 设置是否只包含已解析的调用边
	 *
	 * 当设置为 true 时，只显示能够确定目标方法的调用；
	 * 设为 false 时会包含未解析的虚拟调用。
	 *
	 * @param resolved true=只保留已解析的边，false=包含所有边
	 * @return this 引用，用于链式调用
	 */
	fun resolvedOnly(resolved: Boolean): ICallGraphBuilder

	/**
	 * 根据当前配置构建调用图
	 *
	 * @return 构建完成的 [ICallGraph] 实例
	 */
	fun build(): ICallGraph
}
