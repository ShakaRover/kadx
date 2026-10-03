package jadx.tests.integration.inner

object TestAnonymousClass18Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inner;

public class TestAnonymousClass18Fixture {

	@SuppressWarnings({ "Convert2Lambda", "Anonymous2MethodRef", "unused" })
	public static class TestCls {

		public interface Job {
			void executeJob();
		}

		public void start() {
			runJob(new Job() {
				@Override
				public void executeJob() {
					runJob(new Job() {
						@Override
						public void executeJob() {
							doSomething();
						}
					});
				}

				private void doSomething() {
				}
			});
		}

		public static void runJob(Job job) {
		}
	}
}
"""
}
