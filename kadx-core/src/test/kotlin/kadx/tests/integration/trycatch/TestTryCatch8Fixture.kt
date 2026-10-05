package kadx.tests.integration.trycatch

object TestTryCatch8Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestTryCatch8Fixture {

	public static class TestCls {
		static class MyException extends Exception {
			private static final long serialVersionUID = 7963400419047287279L;

			MyException() {
			}

			MyException(String msg, Throwable cause) {
				super(msg, cause);
			}
		}

		MyException e = null;

		public void test() {
			synchronized (this) {
				try {
					throw new MyException();
				} catch (MyException myExc) {
					this.e = myExc;
				} catch (Exception ex) {
					this.e = new MyException("MyExc", ex);
				}
			}
		}

		public void check() {
			test();
			assertThat(e).isInstanceOf(MyException.class);
			assertThat(e.getMessage()).isNull();
		}
	}
}
"""
}
