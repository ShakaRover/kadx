package kadx.tests.integration.conditions

object TestConditions6Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

import java.util.List;

public class TestConditions6Fixture {

	public static class TestCls {
		public boolean test(List<String> l1, List<String> l2) {
			if (l2.size() > 0) {
				l1.clear();
			}
			return l1.size() == 0;
		}
	}
}
"""
}
