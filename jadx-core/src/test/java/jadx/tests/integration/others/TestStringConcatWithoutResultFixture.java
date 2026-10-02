package jadx.tests.integration.others;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestStringConcatWithoutResultFixture {
	private static final Logger LOG = LoggerFactory.getLogger(TestStringConcatWithoutResultFixture.class);

	public static class TestCls {
		public static final boolean LOG_DEBUG = false;

		public void test(int i) {
			String msg = "Input arg value: " + i;
			if (LOG_DEBUG) {
				LOG.debug(msg);
			}
		}
	}
}
