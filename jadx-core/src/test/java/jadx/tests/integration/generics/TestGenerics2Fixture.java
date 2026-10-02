package jadx.tests.integration.generics;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;

public class TestGenerics2Fixture {

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	public static class TestCls {
		public static class ItemReference<V> extends WeakReference<V> {
			public Object id;

			public ItemReference(V item, Object objId, ReferenceQueue<? super V> queue) {
				super(item, queue);
				this.id = objId;
			}
		}

		public static class ItemReferences<V> {
			private Map<Object, ItemReference<V>> items;

			public V get(Object id) {
				WeakReference<V> ref = this.items.get(id);
				if (ref != null) {
					return ref.get();
				}
				return null;
			}
		}
	}
}
