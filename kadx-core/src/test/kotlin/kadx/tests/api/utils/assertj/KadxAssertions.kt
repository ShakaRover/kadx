package kadx.tests.api.utils.assertj

import kadx.api.ICodeInfo
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import org.assertj.core.api.AbstractBooleanArrayAssert
import org.assertj.core.api.AbstractBooleanAssert
import org.assertj.core.api.AbstractByteArrayAssert
import org.assertj.core.api.AbstractByteAssert
import org.assertj.core.api.AbstractCharArrayAssert
import org.assertj.core.api.AbstractCharSequenceAssert
import org.assertj.core.api.AbstractCharacterAssert
import org.assertj.core.api.AbstractDoubleArrayAssert
import org.assertj.core.api.AbstractDoubleAssert
import org.assertj.core.api.AbstractFileAssert
import org.assertj.core.api.AbstractFloatArrayAssert
import org.assertj.core.api.AbstractFloatAssert
import org.assertj.core.api.AbstractIntArrayAssert
import org.assertj.core.api.AbstractIntegerAssert
import org.assertj.core.api.AbstractLongArrayAssert
import org.assertj.core.api.AbstractLongAssert
import org.assertj.core.api.AbstractPathAssert
import org.assertj.core.api.AbstractShortArrayAssert
import org.assertj.core.api.AbstractShortAssert
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions
import org.assertj.core.api.ClassAssert
import org.assertj.core.api.IterableAssert
import org.assertj.core.api.ListAssert
import org.assertj.core.api.MapAssert
import org.assertj.core.api.ObjectArrayAssert
import org.assertj.core.api.ObjectAssert
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.util.stream.Stream

/**
 * kadx 测试专用的 AssertJ 入口。
 *
 * **做什么**：在 AssertJ 的基础上提供 4 个自定义重载：
 * [assertThat] 接受 [ClassNode] / [MethodNode] / [ICodeInfo] / [String]，
 * 返回 kadx 自己的断言封装（[KadxClassNodeAssertions] 等），从而支持
 * `assertThat(cls).code().containsOne(...)` 这类链式调用。
 *
 * **为什么仍然 `extends Assertions`**：ECJ 编译的 Java fixture 里会
 * `import static ...KadxAssertions.assertThat;` 并直接调用继承自 AssertJ 的重载。
 * Java 允许通过子类名访问继承的静态方法，保留继承关系可让这些 fixture 零改动。
 *
 * **为什么声明为 `object`**：Kotlin **不会**继承 Java 的静态方法，因此 700+ 个
 * Kotlin 测试 `import ...KadxAssertions.assertThat` 后，只有本类**显式声明**
 * （`@JvmStatic` 转发）的重载可见。用 `object` 而非 `class + companion` 是因为
 * Kotlin 只支持从 `object`（而非 companion）用 `类名.成员` 形式导入；同时
 * `@JvmStatic` 会在 `KadxAssertions` 上生成真正的静态方法，Java fixture 的
 * `import static ...KadxAssertions.assertThat` 照常可用。转发方法只是把调用
 * 原样交给 [Assertions.assertThat]，返回类型与 AssertJ 完全一致。
 */
@Suppress("ACCIDENTAL_OVERRIDE")
object KadxAssertions : Assertions() {

	/** 类节点断言：额外提供 `code()` / `decompile()` / `disasmCode()` 等。 */
	@JvmStatic
	fun assertThat(cls: ClassNode): KadxClassNodeAssertions {
		Assertions.assertThat(cls).isNotNull()
		return KadxClassNodeAssertions(cls)
	}

	/** 方法节点断言。 */
	@JvmStatic
	fun assertThat(mth: MethodNode): KadxMethodNodeAssertions {
		Assertions.assertThat(mth).isNotNull()
		return KadxMethodNodeAssertions(mth)
	}

	/** 反编译代码信息断言。 */
	@JvmStatic
	fun assertThat(codeInfo: ICodeInfo): KadxCodeInfoAssertions {
		Assertions.assertThat(codeInfo).isNotNull()
		return KadxCodeInfoAssertions(codeInfo)
	}

	/** 反编译代码字符串断言。 */
	@JvmStatic
	fun assertThat(code: String?): KadxCodeAssertions = KadxCodeAssertions(code)

	// ------------------------------------------------------------------
	// 以下为 AssertJ 基础重载的 Kotlin 可见转发（Kotlin 不继承 Java 静态方法）。
	// 仅做透传，返回类型与原 AssertJ 方法保持一致。
	// ------------------------------------------------------------------

	/** 泛型兜底：任意对象走 AssertJ 的 `ObjectAssert`。 */
	@JvmStatic
	fun <T> assertThat(actual: T): ObjectAssert<T> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Boolean): AbstractBooleanAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Int): AbstractIntegerAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Long): AbstractLongAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Short): AbstractShortAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Byte): AbstractByteAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Double): AbstractDoubleAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Float): AbstractFloatAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Char): AbstractCharacterAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: CharSequence?): AbstractCharSequenceAssert<*, *> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: StringBuilder?): AbstractCharSequenceAssert<*, *> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: StringBuffer?): AbstractCharSequenceAssert<*, *> = Assertions.assertThat(actual)

	@JvmStatic
	fun <T : Throwable> assertThat(actual: T?): AbstractThrowableAssert<*, T> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Class<*>?): ClassAssert = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: File?): AbstractFileAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: Path?): AbstractPathAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: URI?): org.assertj.core.api.AbstractUriAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: URL?): org.assertj.core.api.AbstractUrlAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun <T> assertThat(actual: Iterable<T>?): IterableAssert<T> = Assertions.assertThat(actual)

	@JvmStatic
	fun <T> assertThat(actual: List<T>?): ListAssert<T> = Assertions.assertThat(actual)

	@JvmStatic
	fun <K, V> assertThat(actual: Map<K, V>?): MapAssert<K, V> = Assertions.assertThat(actual)

	@JvmStatic
	fun <T> assertThat(actual: Stream<T>?): ListAssert<T> = Assertions.assertThat(actual)

	@JvmStatic
	fun <T> assertThat(actual: Array<T>?): ObjectArrayAssert<T> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: BooleanArray?): AbstractBooleanArrayAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: IntArray?): AbstractIntArrayAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: LongArray?): AbstractLongArrayAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: ShortArray?): AbstractShortArrayAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: ByteArray?): AbstractByteArrayAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: DoubleArray?): AbstractDoubleArrayAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: FloatArray?): AbstractFloatArrayAssert<*> = Assertions.assertThat(actual)

	@JvmStatic
	fun assertThat(actual: CharArray?): AbstractCharArrayAssert<*> = Assertions.assertThat(actual)
}
