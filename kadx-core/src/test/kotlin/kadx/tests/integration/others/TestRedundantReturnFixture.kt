package kadx.tests.integration.others

object TestRedundantReturnFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static org.assertj.core.api.Assertions.fail;

public class TestRedundantReturnFixture {

	public static class TestCls {
		public void test(int num) {
			if (num == 4) {
				fail("");
			}
		}
	}
}
"""
}
