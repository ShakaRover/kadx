package kadx.gui.device.debugger.smali

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.SmaliTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * smali 反汇编测试。
 *
 * **做什么**：把 `src/test/smali` 下的 smali 文件加载为 [ClassNode]，
 * 再用 [Smali.disassemble] 反汇编，校验输出内容。
 *
 * 继承 core 的 [SmaliTest]（已是 Kotlin），保持原有 public 方法签名不变。
 */
class DbgSmaliTest : SmaliTest() {

	@BeforeEach
	fun initProject() {
		// smali 文件位于 kadx-gui 模块，需要告诉基类从哪个工程目录查找
		setCurrentProject("kadx-gui")
	}

	@Test
	fun testSwitch() {
		disableCompilation()
		val cls: ClassNode = getClassNodeFromSmali("switch", "SwitchTest")
		val disasm = Smali.disassemble(cls)
		LOG.debug("{}", disasm.getCode())
	}

	@Test
	fun testParams() {
		disableCompilation()
		val cls = getClassNodeFromSmali("params", "ParamsTest")
		val disasm = Smali.disassemble(cls)
		val code = disasm.getCode()
		LOG.debug("{}", code)
		assertThat(code)
			.doesNotContain("Failed to write method")
			.doesNotContain(".param p1")
			.contains(".local p1, \"arg0\":Landroid/widget/AdapterView;, \"Landroid/widget/AdapterView<*>;\"")
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(DbgSmaliTest::class.java)
	}
}
