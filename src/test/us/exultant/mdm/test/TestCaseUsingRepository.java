package us.exultant.mdm.test;

import java.io.*;
import java.util.*;
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
		System.getProperties().setProperty("user.dir", createUniqueTestFolderPrefix().getCanonicalPath());
	}
}
