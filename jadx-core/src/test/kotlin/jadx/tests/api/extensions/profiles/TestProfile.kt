package jadx.tests.api.extensions.profiles

import jadx.tests.api.IntegrationTest
import java.util.function.Consumer

/**
 * 测试运行档位：定义输入类型（java / dex / raung）与目标 Java 版本等组合。
 *
 * 每个常量本身就是一个 `Consumer<IntegrationTest>`，在被选中时对测试实例做配置。
 */
enum class TestProfile(
	private val description: String,
	private val setup: Consumer<IntegrationTest>,
) : Consumer<IntegrationTest> {

	DX_J8(
		"dx-j8",
		Consumer { test ->
			test.useTargetJavaVersion(8)
			test.useDexInput("dx")
		},
	),
	D8_J8(
		"d8-j8",
		Consumer { test ->
			test.useTargetJavaVersion(8)
			test.useDexInput("d8")
		},
	),
	D8_J11(
		"d8-j11",
		Consumer { test ->
			test.useTargetJavaVersion(11)
			test.useDexInput("d8")
		},
	),
	D8_J11_DESUGAR(
		"d8-j11-desugar",
		Consumer { test ->
			test.useTargetJavaVersion(11)
			test.useDexInput("d8")
			test.keepParentClassOnInput()
			@Suppress("UNCHECKED_CAST")
			val options = test.getArgs().pluginOptions as MutableMap<String, String>
			options["java-convert.d8-desugar"] = "yes"
		},
	),
	JAVA(
		"java",
		Consumer { test ->
			// 不设置 Java 版本，使用当前编译器默认值
			test.useTargetJavaVersion(0)
			test.useJavaInput()
		},
	),
	JAVA8(
		"java-8",
		Consumer { test ->
			test.useTargetJavaVersion(8)
			test.useJavaInput()
		},
	),
	JAVA11(
		"java-11",
		Consumer { test ->
			test.useTargetJavaVersion(11)
			test.useJavaInput()
		},
	),
	JAVA17(
		"java-17",
		Consumer { test ->
			test.useTargetJavaVersion(17)
			test.useJavaInput()
		},
	),
	ECJ_DX_J8(
		"ecj-dx-j8",
		Consumer { test ->
			test.useEclipseCompiler()
			test.useTargetJavaVersion(8)
			test.useDexInput()
		},
	),
	ECJ_J8(
		"ecj-j8",
		Consumer { test ->
			test.useEclipseCompiler()
			test.useTargetJavaVersion(8)
			test.useJavaInput()
		},
	),
	ALL(
		"all",
		Consumer { },
	),
	;

	override fun accept(test: IntegrationTest) {
		setup.accept(test)
		test.setOutDirSuffix(description)
	}

	fun getDescription(): String = description
}
