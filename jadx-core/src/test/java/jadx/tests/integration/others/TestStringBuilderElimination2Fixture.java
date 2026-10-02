package jadx.tests.integration.others;

@SuppressWarnings("StringBufferReplaceableByString")
public class TestStringBuilderElimination2Fixture {

	public static class TestCls1 {
		public String test() {
			return new StringBuilder("[init]").append("a1").append('c').append(2).append(0L).append(1.0f).append(2.0d).append(true)
					.toString();
		}
	}

	public static class TestCls2 {
		public String test() {
			// A chain with non-final variables
			String sInit = "[init]";
			String s = "a1";
			char c = 'c';
			int i = 1;
			long l = 2;
			float f = 1.0f;
			double d = 2.0d;
			boolean b = true;
			return new StringBuilder(sInit).append(s).append(c).append(i).append(l).append(f).append(d).append(b).toString();
		}
	}

	public static class TestClsStringUtilsReverse {

		/**
		 * Simplified version of org.apache.commons.lang3.StringUtils.reverse()
		 */
		public static String reverse(final String str) {
			return new StringBuilder(str).reverse().toString();
		}
	}

	public static class TestClsChainWithDelete {
		public String test() {
			// a chain we can't simplify
			return new StringBuilder("[init]").append("a1").delete(1, 2).toString();
		}
	}
}
