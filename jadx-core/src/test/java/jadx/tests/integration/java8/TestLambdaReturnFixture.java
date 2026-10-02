package jadx.tests.integration.java8;

public class TestLambdaReturnFixture {

	@SuppressWarnings("unused")
	public static class TestCls {
		interface Function0<R> {
			R apply();
		}

		public static class T2 {
			public long l;

			public T2(long l) {
				this.l = l;
			}

			public void w() {
			}
		}

		public Byte test(Byte b1) {
			Function0<Void> f1 = () -> {
				new T2(94L).w();
				return null;
			};
			f1.apply();
			return null;
		}
	}
}
