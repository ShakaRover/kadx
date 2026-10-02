package jadx.tests.integration.conditions;

public class TestConditions7Fixture {

	public static class TestCls {
		public void test(int[] a, int i) {
			if (i >= 0 && i < a.length) {
				a[i]++;
			}
		}
	}
}
