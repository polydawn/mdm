package net.polydawn.mdm.test;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.mdm.*;
import org.junit.*;
import us.exultant.ahs.iob.*;

public class TestCaseUsingRepository {
	public TestCaseUsingRepository() {
		testRootDir = new File(System.getProperty("java.io.tmpdir"), "mdm-test");
		testDirs = new ArrayList<File>();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.gc();
				cleanup();
			}
		});
	}

	public final File testRootDir;
	private final List<File> testDirs;
	private WithCwd wd;

	protected File createUniqueTestFolderPrefix() {
		while (true) {
			File f = new File(testRootDir, "test-"+UUID.randomUUID().toString());
			if (f.mkdirs()) {
				testDirs.add(f);
				return f;
			}
		}
	}

	@After
	public void cleanup() {
		if (wd != null) {
			wd.close();
			wd = null;
		}
		Iterator<File> itr = testDirs.iterator();
		while (itr.hasNext())
			try {
				IOForge.delete(itr.next());
				itr.remove();
			} catch (IOException e) {
				System.err.println("failed to delete test dir: "+e.getMessage());
			}
	}

	@Before
	public void setUp() throws IOException {
		wd = new WithCwd(createUniqueTestFolderPrefix().getCanonicalPath());
	}

	public void assertJoy(MdmExitMessage result) {
		if (result.code != 0) {
			fail("command exited with '"+result.happy+"' -- \""+result.getMessage()+"\".");
		}
	}
}
