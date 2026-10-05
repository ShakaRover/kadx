// SPDX-License-Identifier: Apache-2.0
package kadx.analysis.callgraph.test

import kadx.analysis.callgraph.CallGraphExportDot
import kadx.analysis.callgraph.CallGraphExportJson
import kadx.analysis.callgraph.KadxCallGraph
import kadx.analysis.callgraph.api.ICallGraph
import kadx.analysis.callgraph.api.ICallGraphEdge
import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Path

/**
 * [kadx.analysis.callgraph.KadxCallGraph] 的集成测试。
 *
 * 该测试会真实地加载 smali 样例文件，构建调用图（call graph），
 * 然后验证边的数量、导出 DOT/JSON 等能力。
 */
class KadxCallGraphTest {

	/**
	 * JUnit 5 提供的临时目录。
	 *
	 * 每个测试方法执行前，JUnit 会注入一个全新的临时目录；
	 * 测试结束后自动清理，避免污染工作区。
	 */
	@field:TempDir
	private lateinit var tempDir: Path

	/**
	 * 使用示例（不是测试方法，仅用于演示 API 用法）。
	 *
	 * 展示如何通过 [KadxCallGraph.builder] 链式配置并构建调用图，
	 * 以及如何遍历边、导出 DOT/JSON 文件。
	 */
	@Suppress("unused")
	fun usageExample() {
		val args = KadxArgs()
		args.addInputFile(File("input.apk"))
		KadxDecompiler(args).use { kadx ->
			kadx.load()

			// 链式配置：只分析 com.example 包，并包含未解析的调用边
			val callGraph: ICallGraph = KadxCallGraph.builder(kadx)
				.includePackages("com.example")
				.resolvedOnly(false)
				.build()

			for (edge in callGraph.edges()) {
				if (edge.isResolved) {
					println("Edge from '${edge.from}' to '${edge.to()}'")
				}
			}
			callGraph.writeDot(Path.of("test.dot"))
			callGraph.writeJson(Path.of("test.json"))
		}
	}

	/**
	 * 简单调用图测试：加载 `simple.smali`，只分析 `test.pkg` 包。
	 *
	 * 样例中只存在一条调用边，因此断言边数为 1；
	 * 同时验证 DOT/JSON 两种导出方式都能正常工作。
	 */
	@Test
	fun simpleTest() {
		val args = KadxArgs()
		args.addInputFile(getSampleFile("simple.smali"))
		KadxDecompiler(args).use { kadx ->
			kadx.load()

			val callGraph: ICallGraph = KadxCallGraph.builder(kadx)
				.includePackages("test.pkg")
				.resolvedOnly(false)
				.build()

			// 断言：样例中只有一条调用边
			assertThat(callGraph.edges()).hasSize(1)

			for (edge in callGraph.edges()) {
				println("Edge from ${edge.from} to ${edge.to()}")
			}

			// 导出为 DOT 字符串并打印
			val dotStr = CallGraphExportDot(kadx.getArgs(), callGraph).writeToString()
			println("dot: $dotStr")

			// 导出为 JSON 字符串并打印
			val jsonStr = CallGraphExportJson(callGraph).writeToString()
			println("json: $jsonStr")

			// 验证真正写入文件也不报错
			callGraph.writeDot(tempDir.resolve("test.dot"))
			callGraph.writeJson(tempDir.resolve("test.json"))
		}
	}

	/**
	 * 从测试资源目录 `src/test/resources/samples/` 中加载样例文件。
	 *
	 * @param sampleName 样例文件名（相对于 `samples/` 目录）
	 * @return 样例文件对应的 [File] 对象
	 * @throws RuntimeException 当资源不存在或路径无法转换时抛出
	 */
	private fun getSampleFile(sampleName: String): File {
		try {
			val resource: URL? = javaClass.getResource("/samples/$sampleName")
			assertThat(resource).describedAs("Sample not found: %s", sampleName).isNotNull()
			// 断言通过后，使用 checkNotNull 做智能转换，避免裸 `!!`
			val url = checkNotNull(resource) { "Sample not found: $sampleName" }
			return File(url.toURI().toURL().file)
		} catch (e: MalformedURLException) {
			throw RuntimeException("Failed to load sample file: $sampleName", e)
		} catch (e: URISyntaxException) {
			throw RuntimeException("Failed to load sample file: $sampleName", e)
		}
	}
}
