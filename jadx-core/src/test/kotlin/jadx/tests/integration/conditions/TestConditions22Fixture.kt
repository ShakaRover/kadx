package jadx.tests.integration.conditions

object TestConditions22Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestConditions22Fixture {

	public static class TestCls {

		public static int test(int i, int j) {
			int k;
			if (i == 1) {
				if (j == 2) {
					k = 3;
				} else {
					k = 0;
				}
			} else if (i == 2) {
				if (j == 3) {
					k = 4;
				} else {
					k = 0;
				}
			} else if (i == 3) {
				if (j == 4) {
					k = 5;
				} else {
					k = 0;
				}
			} else {
				k = 0;
			}
			System.out.println("k = " + k);
			return k;
		}

		public void check() {
			verify(1, 2, 3);
			verify(1, 1, 0);
			verify(2, 3, 4);
			verify(2, 2, 0);
			verify(3, 4, 5);
			verify(3, 3, 0);
			verify(4, 4, 0);
		}

		private static void verify(int a, int b, int result) {
			assertThat(test(a, b)).isEqualTo(result);
		}
	}
}
"""
}
