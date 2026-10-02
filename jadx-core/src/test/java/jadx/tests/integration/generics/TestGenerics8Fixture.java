package jadx.tests.integration.generics;

import java.util.Iterator;
import java.util.LinkedHashMap;

public class TestGenerics8Fixture {

	@SuppressWarnings("IllegalType")
	public static class TestCls<I> extends LinkedHashMap<I, Integer> implements Iterable<I> {
		@Override
		public Iterator<I> iterator() {
			return keySet().iterator();
		}
	}
}
