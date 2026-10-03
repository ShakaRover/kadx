package jadx.tests.integration.others

object TestFieldInit2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestFieldInit2Fixture {

	public static class TestCls {

		public interface BasicAbstract {
			void doSomething();
		}

		public BasicAbstract x = new BasicAbstract() {
			@Override
			public void doSomething() {
				y = 1;
			}
		};
		public int y = 0;

		public TestCls() {
		}

		public TestCls(int z) {
		}
	}
}
"""
}
