package jadx.tests.integration.arrays

object TestArrays3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arrays;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestArrays3Fixture {

	public static class TestCls {

		private Object test(byte[] bArr) {
			return new Object[] { bArr };
		}

		public void check() {
			byte[] inputArr = { 1, 2 };
			Object result = test(inputArr);
			assertThat(result).isInstanceOf(Object[].class);
			assertThat(((Object[]) result)[0]).isEqualTo(inputArr);
		}
	}
}
"""
}
