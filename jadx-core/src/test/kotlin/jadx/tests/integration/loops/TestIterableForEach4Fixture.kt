package jadx.tests.integration.loops

object TestIterableForEach4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

import java.util.List;

public class TestIterableForEach4Fixture {

	public static class TestCls {
		public void test(List<Object> objects) {
			for (Object o : objects) {
				if (o.hashCode() != 42 || o.hashCode() != 1) {
					break;
				}
			}
		}
	}
}
"""
}
