package kadx.tests.integration.enums

object TestEnums9Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.enums;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestEnums9Fixture {

	public static class TestCls {
		public enum Types {
			INT,
			FLOAT,
			LONG,
			DOUBLE,
			OBJECT,
			ARRAY;

			private static Set<Types> primitives = EnumSet.of(INT, FLOAT, LONG, DOUBLE);
			public static List<Types> references = new ArrayList<>();

			static {
				references.add(OBJECT);
				references.add(ARRAY);
			}

			public static Set<Types> getPrimitives() {
				return primitives;
			}
		}

		public void check() {
			assertThat(Types.getPrimitives()).contains(Types.INT);
			assertThat(Types.references).hasSize(2);
		}
	}
}
"""
}
