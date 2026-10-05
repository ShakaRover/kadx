package kadx.tests.integration.generics

object TestUsageInGenericsFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.generics;

import java.util.List;

public class TestUsageInGenericsFixture {

	public static class TestCls {

		public static class A {
		}

		public static class B<T extends A> {
		}

		public static class C {
			public List<? extends A> list;
		}

		public <T extends A> T test() {
			return null;
		}
	}
}
"""
}
