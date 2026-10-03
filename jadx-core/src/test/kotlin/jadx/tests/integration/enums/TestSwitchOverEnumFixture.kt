package jadx.tests.integration.enums

object TestSwitchOverEnumFixture {
	const val JAVA_SOURCE = """package jadx.tests.integration.enums;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestSwitchOverEnumFixture {

	public enum Count {
		ONE, TWO, THREE
	}

	public int testEnum(Count c) {
		switch (c) {
			case ONE:
				return 1;
			case TWO:
				return 2;
		}
		return 0;
	}

	public void check() {
		assertThat(testEnum(Count.ONE)).isEqualTo(1);
		assertThat(testEnum(Count.TWO)).isEqualTo(2);
		assertThat(testEnum(Count.THREE)).isEqualTo(0);
	}
}
"""
}
