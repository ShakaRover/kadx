package kadx.tests.api.utils

import kadx.NotYetImplementedExtension
import kadx.api.CommentsLevel
import kadx.api.KadxArgs
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.IAttributeNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

/**
 * 所有测试的公共工具基类。
 *
 * 原 Java 类的静态方法全部放入 `companion object` 并标注 `@JvmStatic`，
 * 这样 Java 子类（如 [kadx.tests.api.IntegrationTest]、`BaseExternalTest`）
 * 以及 500+ 个已迁移的 Kotlin 测试都能零改动继续调用 `TestUtils.indent()` 等。
 */
@ExtendWith(NotYetImplementedExtension::class)
open class TestUtils {

	companion object {

		/** 默认缩进字符串（一个层级）。 */
		@JvmStatic
		fun indent(): String = KadxArgs.DEFAULT_INDENT_STR

		/** 按层级返回缩进字符串。 */
		@JvmStatic
		fun indent(indent: Int): String {
			if (indent == 1) {
				return KadxArgs.DEFAULT_INDENT_STR
			}
			return Utils.strRepeat(KadxArgs.DEFAULT_INDENT_STR, indent)
		}

		/** 统计 [substring] 在 [string] 中出现的次数（不重叠、逐次前进）。 */
		@JvmStatic
		fun count(string: String, substring: String?): Int {
			if (substring.isNullOrEmpty()) {
				throw IllegalArgumentException("Substring can't be null or empty")
			}
			var count = 0
			var idx = 0
			while (true) {
				idx = string.indexOf(substring, idx)
				if (idx == -1) {
					break
				}
				idx++
				count++
			}
			return count
		}

		/** 检查反编译结果是否包含错误（或不允许的警告）。 */
		@JvmStatic
		fun checkCode(cls: ClassNode, allowWarnInCode: Boolean) {
			assertThat(hasErrors(cls, allowWarnInCode)).`as`("Inconsistent cls: " + cls).isFalse()
			for (mthNode in cls.methods) {
				if (hasErrors(mthNode, allowWarnInCode)) {
					fail<Nothing>(
						"Method with problems: " + mthNode +
							"\n " + Utils.listToString(mthNode.getAttributesStringsList(), "\n "),
					)
				}
			}
			if (!cls.contains(AFlag.DONT_GENERATE)) {
				assertThat(cls)
					.code()
					.doesNotContain("inconsistent")
					.doesNotContain("KADX ERROR")
			}
		}

		/** 节点是否被标记为不一致/错误，或（在未放宽时）含 WARN 注释。 */
		@JvmStatic
		fun hasErrors(node: IAttributeNode, allowWarnInCode: Boolean): Boolean {
			if (node.contains(AFlag.INCONSISTENT_CODE) || node.contains(AType.KADX_ERROR)) {
				return true
			}
			if (!allowWarnInCode) {
				val commentsAttr = node.get(AType.KADX_COMMENTS)
				if (commentsAttr != null) {
					return commentsAttr.comments.get(CommentsLevel.WARN) != null
				}
			}
			return false
		}

		/** 按 classpath 资源路径取得对应的 [File]（测试样例用）。 */
		@JvmStatic
		fun getFileForSample(resPath: String): File = try {
			File(ClassLoader.getSystemResource(resPath).toURI().rawPath)
		} catch (e: Exception) {
			throw KadxRuntimeException("Resource load failed: $resPath", e)
		}
	}
}
