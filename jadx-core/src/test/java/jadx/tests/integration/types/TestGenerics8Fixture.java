package jadx.tests.integration.types;

public class TestGenerics8Fixture {

	public static class TestCls<T> {

		public abstract static class Class2<S extends I1 & I2> extends Parent2<S> {
			public void test() {
				S s = get();
				s.i1();
				s.i2();
			}
		}

		static class Parent2<T extends I1> {
			T t;

			protected T get() {
				return t;
			}
		}

		interface I1 {
			void i1();
		}

		interface I2 {
			void i2();
		}
	}
}
