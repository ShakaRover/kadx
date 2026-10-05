package kadx.tests.api.utils.assertj

import kadx.api.ICodeInfo
import kadx.api.metadata.ICodeAnnotation
import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.IntegrationTest
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail

/**
 * [ClassNode] 的 AssertJ 断言封装，提供 `code()` / `decompile()` / `disasmCode()`
 * 以及自动检查相关入口。
 */
class KadxClassNodeAssertions(cls: ClassNode) : AbstractObjectAssert<KadxClassNodeAssertions, ClassNode>(cls, KadxClassNodeAssertions::class.java) {

	fun decompile(): KadxCodeInfoAssertions {
		isNotNull()
		val codeInfo: ICodeInfo = actual.getCode()
		assertThat(codeInfo).isNotNull()
		return KadxCodeInfoAssertions(codeInfo)
	}

	fun code(): KadxCodeAssertions {
		isNotNull()
		val code = actual.getCode()
		assertThat(code).isNotNull()
		val codeStr = code.codeStr
		assertThat(codeStr).isNotBlank()
		return KadxCodeAssertions(codeStr)
	}

	fun disasmCode(): KadxCodeAssertions {
		isNotNull()
		val disasmCode = actual.disassembledCode
		assertThat(disasmCode).isNotNull().isNotBlank()
		return KadxCodeAssertions(disasmCode)
	}

	fun reloadCode(testInstance: IntegrationTest): KadxCodeAssertions {
		isNotNull()
		val code = actual.reloadCode()
		assertThat(code).isNotNull()
		val codeStr = code.codeStr
		assertThat(codeStr).isNotBlank()

		val codeAssertions = KadxCodeAssertions(codeStr)
		codeAssertions.print()
		testInstance.runChecks(actual)
		return codeAssertions
	}

	/**
	 * 强制对反编译后的代码执行自动检查（smali 测试常用）。
	 */
	fun runDecompiledAutoCheck(testInstance: IntegrationTest): KadxClassNodeAssertions {
		isNotNull()
		testInstance.runDecompiledAutoCheck(actual)
		return this
	}

	fun checkCodeAnnotationFor(refStr: String, node: ICodeAnnotation): KadxClassNodeAssertions {
		checkCodeAnnotationFor(refStr, 0, node)
		return this
	}

	fun checkCodeAnnotationFor(refStr: String, refOffset: Int, node: ICodeAnnotation): KadxClassNodeAssertions {
		val code = actual.getCode()
		val codePos = code.codeStr.indexOf(refStr)
		assertThat(codePos).describedAs("String '%s' not found", refStr).isNotEqualTo(-1)
		val refPos = codePos + refOffset
		for (entry in code.codeMetadata.getAsMap().entries) {
			if (entry.key == refPos) {
				assertThat(entry.value).isEqualTo(node)
				return this
			}
		}
		fail<Nothing>("Annotation for reference string: '%s' at position %d not found", refStr, refPos)
		return this
	}
}
