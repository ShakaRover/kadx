package kadx.gui.ui.action

import kadx.core.dex.info.MethodInfo
import kadx.tests.api.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 引用拼接测试（PR #2955）。
 *
 * **做什么**：校验类/方法/字段的 smali 引用使用“原始（未重命名）”名称。
 *
 * **Option A**：原始 Java fixture 内嵌在 [JAVA_SOURCE] 中，
 * [IntegrationTest] 会把它编译到临时目录再反编译。
 */
class CopySmaliReferenceActionTest : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		val outerCls = getClassNode(CopySmaliReferenceActionTest::class.java)
		val cls = outerCls.innerClasses
			.stream()
			.filter { c -> c.classInfo.shortName == "Inner" }
			.findFirst()
			.orElseThrow()

		val clsRef = "Lkadx/gui/ui/action/CopySmaliReferenceActionTest\$Inner;"
		assertThat(CopySmaliReferenceAction.getSmaliReference(outerCls.classInfo))
			.isEqualTo("Lkadx/gui/ui/action/CopySmaliReferenceActionTest;")
		assertThat(CopySmaliReferenceAction.getSmaliReference(cls.classInfo)).isEqualTo(clsRef)

		val ctor = checkNotNull(cls.searchMethodByShortId("<init>(JLjava/lang/Object;)V"))
		assertThat(CopySmaliReferenceAction.getSmaliReference(ctor.methodInfo))
			.isEqualTo("$clsRef-><init>(JLjava/lang/Object;)V")

		val method = checkNotNull(cls.searchMethodByShortId("method(I[Ljava/lang/String;)V"))
		assertThat(CopySmaliReferenceAction.getSmaliReference(method.methodInfo))
			.isEqualTo("$clsRef->method(I[Ljava/lang/String;)V")

		val noArgs = checkNotNull(cls.searchMethodByShortId("noArgs()[Z"))
		assertThat(CopySmaliReferenceAction.getSmaliReference(noArgs.methodInfo))
			.isEqualTo("$clsRef->noArgs()[Z")

		val count = checkNotNull(cls.searchFieldByName("count"))
		assertThat(CopySmaliReferenceAction.getSmaliReference(count.fieldInfo))
			.isEqualTo("$clsRef->count:I")

		val names = checkNotNull(cls.searchFieldByName("names"))
		assertThat(CopySmaliReferenceAction.getSmaliReference(names.fieldInfo))
			.isEqualTo("$clsRef->names:[[Ljava/lang/String;")
	}

	@Test
	fun testRenamed() {
		disableCompilation()
		val cls = getClassNode(CopySmaliReferenceActionTest::class.java)
		val mth: MethodInfo = checkNotNull(cls.searchMethodByShortId("testRenamed()V")).methodInfo
		cls.classInfo.changePkgAndName("a.b", "Renamed")
		mth.alias = "renamed"

		// 应使用原始名称
		assertThat(CopySmaliReferenceAction.getSmaliReference(mth))
			.isEqualTo("Lkadx/gui/ui/action/CopySmaliReferenceActionTest;->testRenamed()V")
	}

	companion object {
		@Suppress("unused")
		const val JAVA_SOURCE = """package kadx.gui.ui.action;

public class CopySmaliReferenceActionTest {

	public static class Inner {
		private int count;
		private String[][] names;

		public Inner(long id, Object obj) {
		}

		public void method(int i, String[] arr) {
		}

		public boolean[] noArgs() {
			return null;
		}
	}

	public void testRenamed() {
	}
}
"""
	}
}
