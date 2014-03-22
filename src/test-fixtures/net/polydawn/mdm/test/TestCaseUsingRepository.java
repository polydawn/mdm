package net.polydawn.mdm.test;

import static org.junit.Assert.*;
import java.io.*;
import net.polydawn.mdm.*;
import org.junit.*;

public class TestCaseUsingRepository {
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
		if (result.code != 0) {
			fail("command exited with '"+result.happy+"' -- \""+result.getMessage()+"\".");
		}
	}
}
