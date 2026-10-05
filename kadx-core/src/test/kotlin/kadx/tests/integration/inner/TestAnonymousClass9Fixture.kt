package kadx.tests.integration.inner

object TestAnonymousClass9Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class TestAnonymousClass9Fixture {

	public static class TestCls {

		public Callable<String> c = new Callable<String>() {
			@Override
			public String call() throws Exception {
				return "str";
			}
		};

		public Runnable test() {
			return new FutureTask<String>(this.c) {
				public void run() {
					System.out.println(6);
				}
			};
		}
	}
}
"""
}
