package kadx.tests.integration.usethis

object TestDontInlineThisFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.usethis;

import java.util.Random;

public class TestDontInlineThisFixture {

	public static class TestCls {
		public int field = new Random().nextInt();

		public TestCls test() {
			TestCls res;
			if (field == 7) {
				res = this;
				System.out.println();
			} else {
				res = new TestCls();
			}
			res.method();
			return res;
		}

		private void method() {
		}
	}
}
"""
}
