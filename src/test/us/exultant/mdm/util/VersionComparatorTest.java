package us.exultant.mdm.util;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.*;

public class VersionComparatorTest {
	@Test
	public void testEasy() {
		List<String> list = Arrays.asList(new String[] {
				"1.6",
				"1.5.2",
				"1.5",
				"1.5.1",
		});

		Collections.sort(list, new VersionComparator());

		List<String> expected = Arrays.asList(new String[] {
				"1.5",
				"1.5.1",
				"1.5.2",
				"1.6",
		});

		assertEquals(expected, list);
	}

	@Test
	/** A whole bunch of utterly mad values.  Python distutil's LooseVersion agrees with this entire list. */
	public void test() {
		List<String> list = Arrays.asList(new String[] {
				"1.5.1",
				"1.5.1+1",
				"1.5.2b2",
				//"3.10A",	// java String.compareTo thinks that 'a' < 'b' (correct), but 'A' > 'a' (what?!).  python (rightfully, i daresay) disagrees.
				"161",
				"3.10b",
				"3.10a",
				"8.02",
				"3.4j",
				"1996.07.12",
				"3.2.pl0",
				"3.1.1.6",
				"2g6",
				"11g",
				"1.13-",
				"0.960923",
				"1.13+",
				"1.13",
				"2.2beta29",
				"1.13++",
				"5.5.kw",
				"2.0b1pl0",
		});

		Collections.sort(list, new VersionComparator());

		List<String> expected = Arrays.asList(new String[] {
				"0.960923",
				"1.5.1",
				"1.5.1+1",
				"1.5.2b2",
				"1.13",
				"1.13+",
				"1.13++",
				"1.13-",
				"2.0b1pl0",
				"2.2beta29",
				"2g6",
				"3.1.1.6",
				"3.2.pl0",
				"3.4j",
				//"3.10A",
				"3.10a",
				"3.10b",
				"5.5.kw",
				"8.02",
				"11g",
				"161",
				"1996.07.12",
		});

		assertEquals(expected, list);
	}
}
