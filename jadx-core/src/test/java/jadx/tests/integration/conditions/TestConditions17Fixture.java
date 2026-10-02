package jadx.tests.integration.conditions;

public class TestConditions17Fixture {

	public static class TestCls {

		public static final int SOMETHING = 2;

		public static void test(int a) {
			if ((a & SOMETHING) != 0) {
				print(1);
			}
			print(2);
		}

		public static void print(Object o) {
		}
	}
}
