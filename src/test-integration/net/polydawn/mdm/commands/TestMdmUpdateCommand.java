package net.polydawn.mdm.commands;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class TestMdmUpdateCommand extends TestCaseUsingRepository {
	@Test
	public void testUpdateFromLocalRelrepWithMutipleVersions() throws Exception {
		Fixture remoteProject = new ProjectAlpha("projectRemote");
		Fixture releases = new ProjectBetaReleases("projectRepo-releases");

		// Add a thing to the project.
		WithCwd wd = new WithCwd(remoteProject.getRepo().getWorkTree()); {
			MdmAddCommand cmd = new MdmAddCommand(remoteProject.getRepo());
			cmd.url = releases.getRepo().getWorkTree().toString();
			cmd.name = "depname";
			cmd.pathLibs = new File("lib");
			cmd.version = "v1.1";
			cmd.validate();
			assertJoy(cmd.call());
		} wd.close();

		// Clone.  After update, all the asserts that would have passed against the original should pass against the clone.
		Fixture project = new ProjectClone("projectRepo", remoteProject.getRepo());

		File depPath = new File(project.getRepo().getWorkTree()+"/lib/depname").getCanonicalFile();

		// note that at present jgit clone doesn't appear realize it needs to mkdir for submodules even if absent, or they show up as deleted.
		// mdmupdate currently blazes right by this without stopping, but not's not necessarily the most correct behavior either.
		//depPath.mkdirs();

		// Do update.  Should fetch the one version of the one dependency.
		new MdmUpdateCommand(project.getRepo()).call();

		// i do hope there's a filesystem there now
		assertTrue("dependency module path exists on fs", depPath.exists());
		assertTrue("dependency module path is dir", depPath.isDirectory());

		// assert on the refs in the release module we added to the project repo
		Collection<Ref> refs = new Git(project.getRepo()).lsRemote()
				.setRemote(depPath.toString())
				.call();
		List<String> refNames = new ArrayList<String>(refs.size());
		for (Ref r : refs) refNames.add(r.getName());
		assertTrue("head ref present in dependency module", refNames.contains("HEAD"));
		assertTrue("release branch present in dependency module", refNames.contains("refs/heads/mdm/release/v1.1"));
		assertTrue("release tag present in dependency module", refNames.contains("refs/tags/release/v1.1"));
		assertEquals("exactly these three refs present in dependency module", 3, refNames.size());

		// check the actual desired artifacts are inside the release module location
		assertEquals("exactly two files exist (.git and the artifact)", 2, depPath.listFiles().length);
		assertEquals("content of artifact is correct", "beta release 1.1", IOForge.readFileAsString(new File(depPath, "beta")));
	}
}
