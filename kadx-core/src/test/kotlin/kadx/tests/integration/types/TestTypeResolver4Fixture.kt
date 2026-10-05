package kadx.tests.integration.types

object TestTypeResolver4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTypeResolver4Fixture {

	public static class TestCls {

		private static String test(byte[] strArray, int offset) {
			int len = strArray.length;
			int start = offset + f(strArray, offset);
			int end = start;
			while (end + 1 < len && (strArray[end] != 0 || strArray[end + 1] != 0)) {
				end += 2;
			}
			byte[] arr = Arrays.copyOfRange(strArray, start, end);
			return new String(arr);
		}

		private static int f(byte[] strArray, int offset) {
			return 0;
		}

		public void check() {
			String test = test(("1234" + "utfstr\0\0" + "4567").getBytes(), 4);
			assertThat(test).isEqualTo("utfstr");
		}
	}
}
"""
}
