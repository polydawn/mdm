package net.polydawn.mdm.scenarios;

import static org.junit.Assert.*;
import java.io.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class GitignoreInteractionsTest extends TestCaseUsingRepository {
	@Test
	public void add_into_gitignored_dir() throws Exception {
		Fixture projectAlpha = new ProjectAlpha("projectAlpha");
		Fixture releases = new ProjectAlphaReleases("projectRepo-releases");

		// gitignore lib.
		WithCwd wd = new WithCwd(projectAlpha.getRepo().getWorkTree()); {
			IOForge.saveFile("/lib/", new File(".gitignore").getCanonicalFile());
		} wd.close();

		// try to add
		wd = new WithCwd(projectAlpha.getRepo().getWorkTree()); {
			assertJoy(Mdm.run(
				"add",
				releases.getRepo().getWorkTree().toString(),
				"--version=v1",
				"--name=depname"
			));
		} wd.close();

		// now verify.
		File depWorkTreePath = new File(projectAlpha.getRepo().getWorkTree()+"/lib/depname").getCanonicalFile();
		File depGitDataPath = new File(projectAlpha.getRepo().getDirectory()+"/modules/lib/depname").getCanonicalFile();

		// i do hope there's a filesystem there now
		assertTrue("dependency module path exists on fs", depWorkTreePath.exists());
		assertTrue("dependency module path is dir", depWorkTreePath.isDirectory());

		// check the actual desired artifacts are inside the release module location
		assertEquals("exactly two files exist (.git and the artifact)", 2, depWorkTreePath.listFiles().length);
		assertEquals("content of artifact is correct", "alpha release", IOForge.readFileAsString(new File(depWorkTreePath, "alpha")));
	}
}
