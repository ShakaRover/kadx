package jadx.tests.api.utils.assertj

import jadx.api.ICodeInfo
import jadx.api.metadata.ICodeAnnotation
import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail

/**
 * [ClassNode] 的 AssertJ 断言封装，提供 `code()` / `decompile()` / `disasmCode()`
 * 以及自动检查相关入口。
 */
class JadxClassNodeAssertions(cls: ClassNode) : AbstractObjectAssert<JadxClassNodeAssertions, ClassNode>(cls, JadxClassNodeAssertions::class.java) {

	fun decompile(): JadxCodeInfoAssertions {
		isNotNull()
		val codeInfo: ICodeInfo = actual.getCode()
		assertThat(codeInfo).isNotNull()
		return JadxCodeInfoAssertions(codeInfo)
	}

	fun code(): JadxCodeAssertions {
		isNotNull()
		val code = actual.getCode()
		assertThat(code).isNotNull()
		val codeStr = code.getCodeStr()
		assertThat(codeStr).isNotBlank()
		return JadxCodeAssertions(codeStr)
	}

	fun disasmCode(): JadxCodeAssertions {
		isNotNull()
		val disasmCode = actual.getDisassembledCode()
		assertThat(disasmCode).isNotNull().isNotBlank()
		return JadxCodeAssertions(disasmCode)
	}

	fun reloadCode(testInstance: IntegrationTest): JadxCodeAssertions {
		isNotNull()
		val code = actual.reloadCode()
		assertThat(code).isNotNull()
		val codeStr = code.getCodeStr()
		assertThat(codeStr).isNotBlank()

		val codeAssertions = JadxCodeAssertions(codeStr)
		codeAssertions.print()
		testInstance.runChecks(actual)
		return codeAssertions
	}

	/**
	 * 强制对反编译后的代码执行自动检查（smali 测试常用）。
	 */
	fun runDecompiledAutoCheck(testInstance: IntegrationTest): JadxClassNodeAssertions {
		isNotNull()
		testInstance.runDecompiledAutoCheck(actual)
		return this
	}

	fun checkCodeAnnotationFor(refStr: String, node: ICodeAnnotation): JadxClassNodeAssertions {
		checkCodeAnnotationFor(refStr, 0, node)
		return this
	}

	fun checkCodeAnnotationFor(refStr: String, refOffset: Int, node: ICodeAnnotation): JadxClassNodeAssertions {
		val code = actual.getCode()
		val codePos = code.getCodeStr().indexOf(refStr)
		assertThat(codePos).describedAs("String '%s' not found", refStr).isNotEqualTo(-1)
		val refPos = codePos + refOffset
		for (entry in code.getCodeMetadata().getAsMap().entries) {
			if (entry.key == refPos) {
				assertThat(entry.value).isEqualTo(node)
				return this
			}
		}
		fail<Nothing>("Annotation for reference string: '%s' at position %d not found", refStr, refPos)
		return this
	}
}
