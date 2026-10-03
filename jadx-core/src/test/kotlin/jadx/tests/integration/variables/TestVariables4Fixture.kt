package jadx.tests.integration.variables

object TestVariables4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.variables;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("checkstyle:printstacktrace")
public class TestVariables4Fixture {

	public static class TestCls {
		public static boolean runTest(String clsName) {
			try {
				boolean pass = false;
				String msg = null;
				Throwable exc = null;

				Class<?> cls = Class.forName(clsName);
				if (cls.getSuperclass() == AbstractTest.class) {
					Method mth = cls.getMethod("testRun");
					try {
						AbstractTest test = (AbstractTest) cls.getConstructor().newInstance();
						pass = (Boolean) mth.invoke(test);
					} catch (InvocationTargetException e) {
						pass = false;
						exc = e.getCause();
					} catch (Throwable e) {
						pass = false;
						exc = e;
					}
				} else {
					msg = "not extends AbstractTest";
				}
				System.err.println(">> "
						+ (pass ? "PASS" : "FAIL") + '\t'
						+ clsName
						+ (msg == null ? "" : "\t - " + msg));
				if (exc != null) {
					exc.printStackTrace();
				}
				return pass;
			} catch (ClassNotFoundException e) {
				System.err.println("Class '" + clsName + "' not found");
			} catch (Exception e) {
				e.printStackTrace();
			}
			return false;
		}

		private static class AbstractTest {
		}
	}
}
"""
}
