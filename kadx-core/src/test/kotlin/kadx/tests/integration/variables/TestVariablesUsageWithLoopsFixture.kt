package kadx.tests.integration.variables

object TestVariablesUsageWithLoopsFixture {
	class TestEnhancedFor

	const val JAVA_SOURCE = """package kadx.tests.integration.variables;

import java.util.ArrayList;
import java.util.List;

public class TestVariablesUsageWithLoopsFixture {

	public static class TestEnhancedFor {
		public void test() {
			List<Object> list;
			synchronized (this) {
				list = new ArrayList<>();
			}
			for (Object o : list) {
				System.out.println(o);
			}
		}
	}

	public static class TestForLoop {
		@SuppressWarnings("rawtypes")
		public void test() {
			List<Object> list;
			synchronized (this) {
				list = new ArrayList<>();
			}
			for (int i = 0; i < list.size(); i++) {
				System.out.println(i);
			}
		}
	}
}
"""
}
