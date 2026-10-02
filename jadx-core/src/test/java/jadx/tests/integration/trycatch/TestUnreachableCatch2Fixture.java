package jadx.tests.integration.trycatch;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class TestUnreachableCatch2Fixture {

	@SuppressWarnings({ "unused", "DataFlowIssue" })
	public static class UnusedExceptionHandlers1 implements AutoCloseable {
		public static void test(final Object unused1, final Object[] array, final Object o1,
				final Object o2, final Object unused2) {
			for (final Object item : array) {
				ByteBuffer buffer = null;
				try (final UnusedExceptionHandlers1 u = doSomething2(o1, "", o2)) {
					try (final FileInputStream fis = new FileInputStream(u.getFilename())) {
						final FileChannel fileChannel = fis.getChannel();
						buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, 42);
					} catch (IOException e) {
						// ignore
					}
				} catch (IOException e) {
					// ignore
				}
			}
		}

		private String getFilename() {
			return null;
		}

		private static UnusedExceptionHandlers1 doSomething2(final Object o1, final String s,
				final Object o2) {
			return null;
		}

		@Override
		public void close() throws IOException {
		}
	}
}
