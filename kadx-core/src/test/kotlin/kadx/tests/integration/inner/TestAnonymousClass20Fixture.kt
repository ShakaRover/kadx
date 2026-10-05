@file:Suppress("ktlint:standard:class-naming")

package kadx.tests.integration.inner

object TestAnonymousClass20Fixture {
	class `Test$Cls`

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestAnonymousClass20Fixture {

	@SuppressWarnings({ "unused", "checkstyle:TypeName", "Convert2Lambda", "Anonymous2MethodRef" })
	public static class Test${'$'}Cls {
		public Runnable test() {
			return new Runnable() {
				@Override
				public void run() {
					new Test${'$'}Cls();
				}
			};
		}
	}
}
"""
}
