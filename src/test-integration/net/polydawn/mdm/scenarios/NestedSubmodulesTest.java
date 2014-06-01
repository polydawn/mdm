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
import net.polydawn.mdm.test.WithCwd;

@RunWith(OrderedJUnit4ClassRunner.class)
public class NestedSubmodulesTest extends TestCaseUsingRepository {
	@Test
	public void nestedSubmodulesTest() throws Exception {
		// set up a project... and then set up another one... and then put them on top of each other, because we're CRAZY
		Fixture projectAlpha = new ProjectAlpha("projectAlpha");
		Fixture projectBetaAlone = new ProjectAlpha("projectBeta");
		WithCwd wd = new WithCwd(projectAlpha.getRepo().getWorkTree()); {
			new Josh("git").args("submodule", "add", projectBetaAlone.getRepo().getDirectory().toString()).start().get();
		} wd.close();
		Repository projectBetaNested = new RepositoryBuilder()
			.setWorkTree(new File(projectAlpha.getRepo().getDirectory(), "projectBeta").getCanonicalFile())
			.build();

		// now try to drop a dependency into projectBeta when it's a nested submodule.
		Fixture releases = new ProjectAlphaReleases("projectRepo-releases");
		File projectBetaWorkTree = new File(projectAlpha.getRepo().getWorkTree(), "projectBeta");
		assertTrue("i even understand where the first layer goes", projectBetaWorkTree.exists());
		wd = new WithCwd(projectBetaWorkTree); {
			assertJoy(Mdm.run(
				"add",
				releases.getRepo().getWorkTree().toString(),
				"--version=v1",
				"--name=depname"
			));
		} wd.close();

		// note that not completely blowing up here is a major victory.

		// now verify.
		File depWorkTreePath = new File(projectBetaWorkTree+"/lib/depname").getCanonicalFile();
		File depGitDataPath = new File(projectAlpha.getRepo().getDirectory()+"/modules/projectBeta/modules/lib/depname").getCanonicalFile();
		// (note the 'modules/' *twice* in the above path.  this is good: otherwise it would sure be a shame if someone made a submodule called 'objects'!)

		// i do hope there's a filesystem there now
		assertTrue("dependency module path exists on fs", depWorkTreePath.exists());
		assertTrue("dependency module path is dir", depWorkTreePath.isDirectory());

		// assert on the refs in the dependency module we added to the project repo
		Collection<Ref> refs = new Git(projectBetaNested).lsRemote()
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

		// all of the above still only checked that `mdm add` understood itself.
		// now check that there's still something anyone else can read again with a straight face.
		new Josh("git").args("status").cwd(depWorkTreePath)/*.opts(Opts.NullIO)*/.start().get();
	}
}
