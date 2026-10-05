package kadx.tests.integration.generics

object TestTypeVarsFromSuperClassFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.generics;

import java.util.Objects;

public class TestTypeVarsFromSuperClassFixture {

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static class TestCls {

		public static class C1<A> {
		}

		public static class C2<B> extends C1<B> {
			public B call() {
				return null;
			}
		}

		public static class C3<C> extends C2<C> {
		}

		public static class C4 extends C3<String> {
			public Object test() {
				String str = call();
				Objects.nonNull(str);
				return str;
			}
		}
	}
}
"""
}
