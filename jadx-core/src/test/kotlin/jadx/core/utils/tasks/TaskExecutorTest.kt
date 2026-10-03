package jadx.core.utils.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * [TaskExecutor] 协程化后的行为回归测试：顺序、并发、限流、取消、进度。
 */
class TaskExecutorTest {

	@Test
	fun sequentialOrder() {
		val order = ConcurrentLinkedQueue<Int>()
		val executor = TaskExecutor()
		for (i in 0 until 5) {
			executor.addSequentialTask(Runnable { order.add(i) })
		}
		executor.execute()
		executor.awaitTermination()
		assertEquals(listOf(0, 1, 2, 3, 4), order.toList())
		assertEquals(5, executor.getProgress())
	}

	@Test
	fun parallelRunsAll() {
		val counter = AtomicInteger(0)
		val executor = TaskExecutor()
		executor.setThreadsCount(4)
		executor.addParallelTasks((0 until 20).map { Runnable { counter.incrementAndGet() } })
		executor.execute()
		executor.awaitTermination()
		assertEquals(20, counter.get())
		assertEquals(20, executor.getProgress())
	}

	@Test
	fun boundedParallelism() {
		val active = AtomicInteger(0)
		val maxActive = AtomicInteger(0)
		val executor = TaskExecutor()
		executor.setThreadsCount(2)
		executor.addParallelTasks(
			(0 until 8).map {
				Runnable {
					val now = active.incrementAndGet()
					maxActive.accumulateAndGet(now) { a, b -> maxOf(a, b) }
					Thread.sleep(20)
					active.decrementAndGet()
				}
			},
		)
		executor.execute()
		executor.awaitTermination()
		assertTrue(maxActive.get() <= 2) { "max active was ${maxActive.get()}" }
		assertEquals(8, executor.getProgress())
	}

	@Test
	fun terminateSkipsPending() {
		val executed = AtomicInteger(0)
		val executor = TaskExecutor()
		executor.setThreadsCount(1)
		executor.addSequentialTasks(
			(0 until 3).map {
				Runnable {
					executed.incrementAndGet()
					executor.terminate()
				}
			},
		)
		executor.execute()
		executor.awaitTermination()
		assertEquals(1, executed.get())
	}
}
