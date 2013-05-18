/*
 * Copyright 2012, 2013 Eric Myhre <http://exultant.us>
 *
 * This file is part of mdm <https://github.com/heavenlyhash/mdm/>.
 *
 * mdm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

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
				"3.10A",
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
				"3.10A",
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

	/** A few other assertions on which distutil's LooseVersion differs. */
	@Test
	public void test2() {
		List<String> list = Arrays.asList(new String[] {
				"3.10b",
				"3.10a",
				"3.10A",	// LooseVersion's regexp had opinions about case which lead them to tokenize this string differently.
		});

		Collections.sort(list, new VersionComparator());

		List<String> expected = Arrays.asList(new String[] {
				"3.10A",
				"3.10a",
				"3.10b",
		});

		assertEquals(expected, list);
	}
}
