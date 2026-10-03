package jadx.tests.integration.trycatch

object TestTryCatchFinally8Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.trycatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class TestTryCatchFinally8Fixture {

	@SuppressWarnings({ "ResultOfMethodCallIgnored", "TryFinallyCanBeTryWithResources", "DataFlowIssue" })
	public static class TestCls {
		public Object test(Object obj) {
			File file = new File("r");
			FileOutputStream output = null;
			try {
				output = new FileOutputStream(file);
				if (obj.equals("a")) {
					return new Object();
				} else {
					return null;
				}
			} catch (IOException e) {
				System.out.println("Exception");
				return null;
			} finally {
				if (output != null) {
					try {
						output.close();
					} catch (IOException e) {
						// Ignored
					}
				}
				file.delete();
			}
		}
	}
}
"""
}
