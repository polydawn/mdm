package net.polydawn.mdm.test;

import static org.junit.Assert.*;
import java.io.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.jgit.*;
import org.junit.*;

public class TestCaseUsingRepository {
	static {
		// apply fixes for questionable jgit behavior
		SystemReaderFilteringProxy.apply();
	}

	private WithCwd wd;

	@After
	public void cleanup() {
		if (wd != null) {
			wd.clear();
			wd = null;
		}
	}

	@Before
	public void setUp() throws IOException {
		wd = WithCwd.temp();
	}

	public void assertJoy(MdmExitMessage result) {
		if (result != null && result.code != 0) {
			fail("command exited with '"+result.happy+"' -- \""+result.getMessage()+"\".");
		}
	}
}
