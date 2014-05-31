package net.polydawn.mdm.scenarios;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;
import net.polydawn.josh.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class RevivingEmptyWorkdirsTest extends TestCaseUsingRepository {
	@Test
	public void revivingEmptyWorkdirsTest() throws Exception {
		// set up a project and link it to a dependency
		Fixture project = new ProjectAlpha("projectRepo");
		Fixture releases = new ProjectAlphaReleases("projectRepo-releases");
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			assertJoy(Mdm.run(
				"add",
				releases.getRepo().getWorkTree().toString(),
				"--version=v1",
				"--name=depname"
			));
		} wd.close();

		// now blow its working tree away
		IOForge.delete(new File(project.getRepo().getWorkTree(), "lib"));

		// check it out again (this will just create the submodule's working dir)
		wd = new WithCwd(project.getRepo().getWorkTree()); {
			new Josh("git").args("checkout", "--", "lib").start().get();
		} wd.close();

		// run mdm update.  should leave us with a sane working tree for that lib again.
		wd = new WithCwd(project.getRepo().getWorkTree()); {
			assertJoy(Mdm.run(
				"update"
			));
		} wd.close();

		// now verify.
		File depWorkTreePath = new File(project.getRepo().getWorkTree()+"/lib/depname").getCanonicalFile();
		File depGitDataPath = new File(project.getRepo().getDirectory()+"/modules/lib/depname").getCanonicalFile();

		// i do hope there's a filesystem there now
		assertTrue("dependency module path exists on fs", depWorkTreePath.exists());
		assertTrue("dependency module path is dir", depWorkTreePath.isDirectory());

		// assert on the refs in the dependency module we added to the project repo
		Collection<Ref> refs = new Git(project.getRepo()).lsRemote()
				.setRemote(depGitDataPath.toString())
				.call();
		List<String> refNames = new ArrayList<String>(refs.size());
		for (Ref r : refs) refNames.add(r.getName());
		assertTrue("head ref present in dependency module", refNames.contains("HEAD"));
		assertTrue("release branch present in dependency module", refNames.contains("refs/heads/mdm/release/v1"));
		assertTrue("release tag present in dependency module", refNames.contains("refs/tags/release/v1"));
		assertEquals("exactly these three refs present in dependency module", 3, refNames.size());

		// check the actual desired artifacts are inside the release module location
		assertEquals("exactly two files exist (.git and the artifact)", 2, depWorkTreePath.listFiles().length);
		assertEquals("content of artifact is correct", "alpha release", IOForge.readFileAsString(new File(depWorkTreePath, "alpha")));
	}
}
