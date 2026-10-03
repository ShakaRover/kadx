package jadx.tests.functional

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.AttributeStorage
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [AttributeStorage] 的标志位与属性增删查语义。
 */
class AttributeStorageTest {

	private lateinit var storage: AttributeStorage

	@BeforeEach
	fun setup() {
		storage = AttributeStorage()
	}

	@Test
	fun testAdd() {
		storage.add(SYNTHETIC)
		assertThat(storage.contains(SYNTHETIC)).isTrue()
	}

	@Test
	fun testRemove() {
		storage.add(SYNTHETIC)
		storage.remove(SYNTHETIC)
		assertThat(storage.contains(SYNTHETIC)).isFalse()
	}

	@Test
	fun testAddAttribute() {
		val attr = TestAttr()
		storage.add(attr)

		assertThat(storage.contains(TEST)).isTrue()
		assertThat(storage.get(TEST)).isEqualTo(attr)
	}

	@Test
	fun testRemoveAttribute() {
		val attr = TestAttr()
		storage.add(attr)
		storage.remove(attr)

		assertThat(storage.contains(TEST)).isFalse()
		assertThat(storage.get(TEST)).isNull()
	}

	@Test
	fun testRemoveOtherAttribute() {
		val attr = TestAttr()
		storage.add(attr)
		storage.remove(TestAttr())

		assertThat(storage.contains(TEST)).isTrue()
		assertThat(storage.get(TEST)).isEqualTo(attr)
	}

	/** 测试用属性实现，仅返回固定的 [TEST] 类型。 */
	class TestAttr : IJadxAttribute {
		override val attrType: IJadxAttrType<*> get() = TEST
	}

	companion object {
		val TEST: AType<TestAttr> = AType()

		private val SYNTHETIC = AFlag.SYNTHETIC
	}
}
