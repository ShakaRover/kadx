package jadx.tests.integration.types;

public class TestTypeResolver23Fixture {

	public static class TestCls {
		public long test(int a) {
			long v = 1L;
			if (a == 2) {
				v = 2L;
			} else if (a == 3) {
				v = 3L;
			}
			System.out.println(v);
			return v;
		}
	}
}
