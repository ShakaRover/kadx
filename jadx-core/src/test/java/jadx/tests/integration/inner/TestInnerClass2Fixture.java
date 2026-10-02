package jadx.tests.integration.inner;

import java.util.Timer;
import java.util.TimerTask;

public class TestInnerClass2Fixture {

	public static class TestCls {
		private static class TerminateTask extends TimerTask {
			@Override
			public void run() {
				System.err.println("Test timed out");
			}
		}

		public void test() {
			new Timer().schedule(new TerminateTask(), 1000L);
		}
	}
}
