package net.polydawn.mdm.scenarios;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.josh.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import net.polydawn.mdm.test.WithCwd;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class ReleasingTest extends TestCaseUsingRepository {
	@Test
	public void releasing_in_a_fresh_init_linked() throws Exception {
		Fixture projectAlpha = new ProjectAlpha("projectAlpha");

		// initialize a new release repo locally and make a release into it
		WithCwd wd = new WithCwd(projectAlpha.getRepo().getWorkTree()); {
			assertJoy(Mdm.run(
				"release-init",
				"--use-defaults"
			));

			IOForge.saveFile("placeholder", new File("whatever").getCanonicalFile());

			assertJoy(Mdm.run(
				"release",
				"--version=v1",
				"--files=whatever"
			));
		} wd.close();

		// assert on the refs in the release module we added to the project repo
		Repository releaseRepo = new RepositoryBuilder()
			.setWorkTree(new File("projectAlpha/releases/").getCanonicalFile())
			.build();
		Collection<Ref> refs = new Git(releaseRepo).lsRemote()
				.setRemote(releaseRepo.getWorkTree().toString())
				.call();
		List<String> refNames = new ArrayList<String>(refs.size());
		for (Ref r : refs) refNames.add(r.getName());
		assertTrue("head ref present in release module", refNames.contains("HEAD"));
		assertTrue("master ref present in release module", refNames.contains("refs/heads/master"));
		assertTrue("mdm/init ref present in release module", refNames.contains("refs/heads/mdm/init"));
		assertTrue("release branch present in release module", refNames.contains("refs/heads/mdm/release/v1"));
		assertTrue("release tag present in release module", refNames.contains("refs/tags/release/v1"));
		assertTrue("accumlation tag present in release module", refNames.contains("refs/tags/mdm/master/v1"));
		assertEquals("exactly these refs present in release module", 6, refNames.size());
	}

	@Test
	public void releasing_in_a_random_repo_should_fail() throws Exception {
		Fixture projectAlpha = new ProjectAlpha("projectAlpha");

		WithCwd wd = new WithCwd(projectAlpha.getRepo().getWorkTree()); {
			try {
				MdmExitMessage result = Mdm.run(
					"release",
					"--version=v1",
					"--files=whatever"
				);
				fail("expected release in non-release repo to fail, but command exited with '"+result.happy+"' -- \""+result.getMessage()+"\".");
			} catch (MdmModuleRelease.MdmModuleReleaseNeedsBranch result) {
				/* superb */
			}
		} wd.close();

		// TODO: this *does* fail correctly, but the inane error message gets to me, why is the "releases" directory being mentioned here, it doesn't exist
		// i think the problem begins in the arg parsing (!) of the MdmReleaseCommand.
	}
}
