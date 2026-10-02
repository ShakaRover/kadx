package jadx.tests.integration.trycatch;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

@SuppressWarnings("checkstyle:printstacktrace")
public class TestMultiExceptionCatch2Fixture {

	public static class TestCls {
		public void test(Constructor<?> constructor) {
			try {
				constructor.newInstance();
			} catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
	}
}
