package jadx.gui.utils

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 基于弱引用的轻量对象池。
 *
 * **做什么**：复用生命周期短、创建代价高的对象（例如调试器里的列表/队列）。
 * 池内只保存 [WeakReference]，因此当对象不再被外部强引用时可被 GC 回收，
 * 不会造成内存泄漏。
 *
 * **为什么保持普通类**：Java 调用方用 `new ObjectPool<>(...)` 构造。
 *
 * @param T 池化对象类型
 */
class ObjectPool<T>(private val creator: Creator<T>) {

	/**
	 * 池化对象的创建工厂。原 Java 为嵌套接口，Java 调用方用 lambda 实现，故保持接口形态。
	 */
	fun interface Creator<T> {
		fun create(): T
	}

	/** 空闲对象队列（弱引用）。使用并发队列以支持多线程取用。 */
	private val pool: ConcurrentLinkedQueue<WeakReference<T>> = ConcurrentLinkedQueue()

	/**
	 * 从池中取出一个对象；池为空或引用已被 GC 回收时创建新对象。
	 */
	fun get(): T {
		while (true) {
			val wNode = pool.poll() ?: return creator.create()
			val node = wNode.get()
			if (node != null) {
				return node
			}
			// 引用已被回收，继续尝试下一个
		}
	}

	/** 把对象归还到池中（以弱引用形式保存）。 */
	fun put(node: T) {
		pool.add(WeakReference(node))
	}
}
