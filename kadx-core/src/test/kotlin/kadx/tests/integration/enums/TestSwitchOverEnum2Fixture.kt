package kadx.tests.integration.enums

object TestSwitchOverEnum2Fixture {
	const val JAVA_SOURCE = """package kadx.tests.integration.enums;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestSwitchOverEnum2Fixture {

	public enum Count {
		ONE, TWO, THREE
	}

	public enum Animal {
		CAT, DOG
	}

	public int testEnum(Count c, Animal a) {
		int result = 0;
		switch (c) {
			case ONE:
				result = 1;
				break;
			case TWO:
				result = 2;
				break;
		}
		switch (a) {
			case CAT:
				result += 10;
				break;
			case DOG:
				result += 20;
				break;
		}
		return result;
	}

	public void check() {
		assertThat(testEnum(Count.ONE, Animal.DOG)).isEqualTo(21);
	}
}
"""
}
