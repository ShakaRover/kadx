package jadx.tests.integration.loops

object TestEndlessLoopFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

public class TestEndlessLoopFixture {

	public static class TestCls {

		void test1() {
			while (this == this) {
			}
		}

		void test2() {
			do {
			} while (this == this);
		}

		void test3() {
			while (true) {
				if (this != this) {
					return;
				}
			}
		}
	}
}
"""
}
