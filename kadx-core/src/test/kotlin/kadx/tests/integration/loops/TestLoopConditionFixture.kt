package kadx.tests.integration.loops

object TestLoopConditionFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import java.util.List;

public class TestLoopConditionFixture {

	public static class TestCls {
		public void test(List<String> list) {
			for (int i = 0; i != 16 && i < 255; i++) {
				list.set(i, "ABC");
				if (i == 128) {
					return;
				}
				list.set(i, "DEF");
			}
		}
	}
}
"""
}
