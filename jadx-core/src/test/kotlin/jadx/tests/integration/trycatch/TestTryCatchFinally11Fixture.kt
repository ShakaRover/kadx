package jadx.tests.integration.trycatch

object TestTryCatchFinally11Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.trycatch;

import java.util.Arrays;
import java.util.List;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestTryCatchFinally11Fixture {

	public static class TestCls {
		private int count = 0;

		public void test(List<Object> list) {
			try {
				call1();
			} finally {
				for (Object item : list) {
					call2(item);
				}
			}
		}

		private void call1() {
			count += 100;
		}

		private void call2(Object item) {
			count++;
		}

		public void check() {
			TestCls t = new TestCls();
			t.test(Arrays.asList("1", "2"));
			assertThat(t.count).isEqualTo(102);
		}
	}
}
"""
}
