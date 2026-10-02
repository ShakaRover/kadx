package jadx.tests.integration.arrays;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestArrays2Fixture {

	public static class TestCls {

		private static Object test4(int type) {
			if (type == 1) {
				return new int[] { 1, 2 };
			} else if (type == 2) {
				return new float[] { 1, 2 };
			} else if (type == 3) {
				return new short[] { 1, 2 };
			} else if (type == 4) {
				return new byte[] { 1, 2 };
			} else {
				return null;
			}
		}

		public void check() {
			assertThat(test4(4)).isInstanceOf(byte[].class);
		}
	}
}
