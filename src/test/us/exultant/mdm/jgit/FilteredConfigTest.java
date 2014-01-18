/*
 * Copyright 2012 - 2014 Eric Myhre <http://exultant.us>
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

package us.exultant.mdm.jgit;

import static org.junit.Assert.assertEquals;
import java.util.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;

public class FilteredConfigTest {
	@Test
	public void testEmptyWhitelistOnDepthThreeValues() {
		Config base = new Config();
		base.setInt("a", "b", "c", 14);

		Config whitelist = new Config();

		FilteredConfig filtered = new FilteredConfig(base, whitelist);

		assertEquals(-1, filtered.getInt("a", "b", "c", -1));
	}

	@Test
	public void testEmptyWhitelistOnDepthMixedValues() {
		Config base = new Config();
		base.setInt("a", "b", "c", 14);

		Config whitelist = new Config();
		whitelist.setBoolean("a", "b", "c", true);

		FilteredConfig filtered = new FilteredConfig(base, whitelist);

		assertEquals(14, filtered.getInt("a", "b", "c", -1));
	}

	@Test
	public void testPartialWhitelistOnDepthMixedValues() {
		Config base = new Config();
		base.setInt("a", "b", "c", 14);
		base.setInt("a", "b", "n", 14);
		base.setInt("a", null, "b", 14);

		Config whitelist = new Config();
		whitelist.setBoolean("a", "b", "c", true);
		whitelist.setBoolean("a", null, "b", true);

		FilteredConfig filtered = new FilteredConfig(base, whitelist);

		assertEquals(14, filtered.getInt("a", "b", "c", -1));
		assertEquals(-1, filtered.getInt("a", "b", "n", -1));
		assertEquals(14, filtered.getInt("a", null, "b", -1));
	}

	@Test
	public void testPartialWhitelistOnValuesIncludingLists() {
		List<String> list = Arrays.asList(new String[] { "x", "y" });

		Config base = new Config();
		base.setInt("a", "b", "c", 14);
		base.setStringList("a", "b", "x", list);

		Config whitelist = new Config();
		whitelist.setBoolean("a", "b", "x", true);

		FilteredConfig filtered = new FilteredConfig(base, whitelist);

		assertEquals(-1, filtered.getInt("a", "b", "c", -1));
		assertEquals(list, Arrays.asList(filtered.getStringList("a", "b", "x")));
	}
}
