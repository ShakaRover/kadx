package jadx.tests.integration.inner

object TestAnonymousClass11Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inner;

import java.util.Random;

public class TestAnonymousClass11Fixture {

	public static class TestCls {

		public void test() {
			final int a = new Random().nextInt();
			final long l = new Random().nextLong();
			func(new A(l) {
				@Override
				public void m() {
					System.out.println(a);
				}
			});
			System.out.println("a" + a);
			print(a);
			print2(1, a);
			print3(1, l);
		}

		public abstract class A {
			public A(long l) {
			}

			public abstract void m();
		}

		private void func(A a) {
		}

		private void print(int a) {
		}

		private void print2(int i, int a) {
		}

		private void print3(int i, long l) {
		}
	}
}
"""
}
