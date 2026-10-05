package kadx.plugins.input.dex

import kadx.api.plugins.input.data.AccessFlags
import kadx.api.plugins.input.data.AccessFlagsScope
import kadx.api.plugins.input.data.IFieldData
import kadx.api.plugins.input.data.IMethodData
import kadx.api.plugins.input.data.ISeqConsumer
import kadx.plugins.input.dex.utils.SmaliTestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

/**
 * DEX 输入插件端到端测试：加载样例 APK/DEX/smali，遍历类/字段/方法/指令并断言非空。
 */
internal class DexInputPluginTest {

	@Test
	fun loadSampleApk() {
		processFile(Paths.get(ClassLoader.getSystemResource("samples/app-with-fake-dex.apk").toURI()))
	}

	@Test
	fun loadHelloWorld() {
		processFile(Paths.get(ClassLoader.getSystemResource("samples/hello.dex").toURI()))
	}

	@Test
	fun loadTestSmali() {
		processFile(SmaliTestUtils.compileSmaliFromResource("samples/test.smali"))
	}

	private fun processFile(sample: Path) {
		System.out.println("Input file: " + sample.toAbsolutePath())
		val start = System.currentTimeMillis()
		val files = listOf(sample)
		DexInputPlugin().loadFiles(files).use { result ->
			val count = AtomicInteger()
			result.visitClasses { cls ->
				System.out.println()
				System.out.println("Class: " + cls.type)
				System.out.println("AccessFlags: " + AccessFlags.format(cls.accessFlags, AccessFlagsScope.CLASS))
				System.out.println("SuperType: " + cls.superType)
				System.out.println("Interfaces: " + cls.interfacesTypes)
				System.out.println("Attributes: " + cls.attributes)
				count.incrementAndGet()

				// ISeqConsumer 是 Kotlin 接口（无 SAM 转换），需显式 object 实现
				cls.visitFieldsAndMethods(
					object : ISeqConsumer<IFieldData> {
						override fun accept(field: IFieldData) {
							System.out.println(field)
						}
					},
					object : ISeqConsumer<IMethodData> {
						override fun accept(mth: IMethodData) {
							System.out.println("---")
							System.out.println(mth)
							val codeReader = mth.codeReader
							if (codeReader != null) {
								codeReader.visitInstructions { insn ->
									insn.decode()
									System.out.println(insn)
								}
							}
							System.out.println("---")
							System.out.println(mth.disassembleMethod())
							System.out.println("---")
						}
					},
				)
				System.out.println("----")
				System.out.println(cls.disassembledCode)
				System.out.println("----")
			}
			assertThat(count.get()).isGreaterThan(0)
		}
		System.out.println("Time: " + (System.currentTimeMillis() - start) + "ms")
	}
}
