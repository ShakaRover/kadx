package jadx.tests.integration.others

object TestNullInlineFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestNullInlineFixture {

	@SuppressWarnings({ "RedundantCast", "DataFlowIssue", "unused" })
	public static class TestCls {
		public static Long test(Double d1) {
			T1<T2, Byte> t1 = (T1<T2, Byte>) null;
			return t1.t2.l;
		}

		static class T2 {
			public long l;
		}

		static class T1<H, P extends Byte> {
			public T2 t2;

			public T1(T2 t2) {
				this.t2 = t2;
			}
		}
	}
}
"""
}
