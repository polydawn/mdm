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
