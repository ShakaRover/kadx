package jadx.plugins.mappings

import jadx.api.JadxDecompiler
import jadx.api.JavaClass
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
		jadxArgs.inputFiles.add(loadResourceFile("base.smali"))
		jadxArgs.inputFiles.add(loadResourceFile("inner.smali"))
		jadxArgs.userRenamesMappingsPath = loadResourceFile("enigma.mapping").toPath()
		JadxDecompiler(jadxArgs).use { jadx ->
			jadx.load()
			val classes: List<JavaClass> = jadx.getClasses()
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
