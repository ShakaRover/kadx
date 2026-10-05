package kadx.tests.integration.generics

object TestTypeVarsFromOuterClassFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.generics;

import java.util.Map;

public class TestTypeVarsFromOuterClassFixture {

	public static class TestCls {
		public interface I<X> {
			Map.Entry<X, X> entry();
		}

		public static class Outer<Y> {
			public class Inner implements I<Y> {
				@Override
				public Map.Entry<Y, Y> entry() {
					return null;
				}
			}

			public Inner getInner() {
				return null;
			}
		}

		private Outer<String> outer;

		public void test() {
			Outer<String>.Inner inner = this.outer.getInner();
			use(inner, inner);
			Map.Entry<String, String> entry = inner.entry();
			use(entry.getKey(), entry.getValue());
		}

		public void test2() {
			// force interface virtual call
			I<String> base = this.outer.getInner();
			use(base, base);
			Map.Entry<String, String> entry = base.entry();
			use(entry.getKey(), entry.getValue());
		}

		public void use(Object a, Object b) {
		}
	}
}
"""
}
