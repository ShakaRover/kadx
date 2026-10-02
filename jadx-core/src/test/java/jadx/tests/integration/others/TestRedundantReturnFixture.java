package jadx.tests.integration.others;

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
