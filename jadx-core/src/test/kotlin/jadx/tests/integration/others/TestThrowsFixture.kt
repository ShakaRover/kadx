package jadx.tests.integration.others

object TestThrowsFixture {
	class MissingThrowsTest

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class TestThrowsFixture {

	public static class MissingThrowsTest extends Exception {

		private void throwCustomException() throws MissingThrowsTest {
			throw new MissingThrowsTest();
		}

		private void throwException() throws Exception {
			throw new Exception();
		}

		private void throwRuntimeException1() {
			throw new RuntimeException();
		}

		private void throwRuntimeException2() {
			throw new NullPointerException();
		}

		private void throwError() {
			throw new Error();
		}

		private void throwError2() {
			throw new OutOfMemoryError();
		}

		@SuppressWarnings("checkstyle:illegalThrows")
		private void throwThrowable() throws Throwable {
			throw new Throwable();
		}

		private void exceptionSource() throws FileNotFoundException {
			throw new FileNotFoundException("");
		}

		public void mergeThrownExceptions() throws IOException {
			exceptionSource();
		}

		public void rethrowThrowable() {
			try {
			} catch (Throwable t) {
				throw t;
			}
		}

		public void doSomething1(int i) throws FileNotFoundException {
			if (i == 1) {
				doSomething2(i);
			} else {
				doSomething1(i);
			}
		}

		public void doSomething2(int i) throws FileNotFoundException {
			if (i == 1) {
				exceptionSource();
			} else {
				doSomething1(i);
			}
		}

		public int doSomething3(int i) throws IllegalArgumentException {
			if (i < 0) {
				throw new IllegalArgumentException();
			}
			return 1;
		}

		public void noThrownExceptions1(InputStream i1) {
			try {
				i1.close();
			} catch (IOException ignore) {
			}
		}

		public void noThrownExceptions2() {
			try {
				throw new FileNotFoundException("");
			} catch (IOException ignore) {
			}
		}

		public void noThrownExceptions3() {
			int i = doSomething3(0);
			System.out.print(i);
		}
	}
}
"""
}
