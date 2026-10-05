package kadx.tests.integration.types

object TestGenerics4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

public class TestGenerics4Fixture {

	public static class TestCls {

		public static class Inner<T> {
			public void overload(IList<? super T> list) {
			}

			public void overload(T t) {
			}
		}

		public interface IList<T> {
			void list(T t);
		}

		public static class ObjIList implements IList<Object> {
			@Override
			public void list(Object o) {
			}
		}

		public Inner<Object> test() {
			Inner<Object> inner = new Inner<>();
			inner.overload(new ObjIList());
			return inner;
		}
	}
}
"""
}
