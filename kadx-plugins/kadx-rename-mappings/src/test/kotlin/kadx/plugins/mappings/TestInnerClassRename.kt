package kadx.plugins.mappings

import kadx.api.KadxDecompiler
import kadx.api.JavaClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证内部类重命名映射：加载 smali 与 enigma 映射后，
 * 内部类应按映射结果被重命名为 `RenamedInner`。
 */
class TestInnerClassRename : BaseRenameMappingsTest() {

	@Test
	fun test() {
		testResDir = "inner-cls-rename"
		kadxArgs.inputFiles.add(loadResourceFile("base.smali"))
		kadxArgs.inputFiles.add(loadResourceFile("inner.smali"))
		kadxArgs.userRenamesMappingsPath = loadResourceFile("enigma.mapping").toPath()
		KadxDecompiler(kadxArgs).use { kadx ->
			kadx.load()
			val classes: List<JavaClass> = kadx.getClasses()
			printClassesCode(classes)
			assertThat(classes).hasSize(1)
			val baseCls = classes[0]
			assertThat(baseCls.getName()).isEqualTo("BaseCls")
			val innerClasses = baseCls.getInnerClasses()
			assertThat(innerClasses).hasSize(1)
			assertThat(innerClasses[0].getName()).isEqualTo("RenamedInner")
			assertThat(baseCls.getCode()).contains("class RenamedInner {")
		}
	}
}
