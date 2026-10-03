package jadx.tests.integration.usethis

object TestInlineThis2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.usethis;

import java.util.Objects;

public class TestInlineThis2Fixture {

	@SuppressWarnings("ConstantValue")
	public static class TestCls {
		public int field;

		public void test() {
			TestCls thisVar = this;
			if (Objects.isNull(thisVar)) {
				System.out.println("null");
			}
			thisVar.method();
			thisVar.field = 123;
		}

		private void method() {
		}
	}
}
"""
}
