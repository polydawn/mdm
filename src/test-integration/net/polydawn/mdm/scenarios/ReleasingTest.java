package net.polydawn.mdm.scenarios;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.josh.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.errors.*;
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
	public void releasing_in_linked_relrepo_after_manually_reacquring_relrepo() throws Exception {
		Fixture projectAlpha = new ProjectAlpha("projectAlpha");
		Repository releaseHubRepo = new RepositoryBuilder()
			.setGitDir(new File("projectAlpha-releases.git").getCanonicalFile())
			.setBare()
			.build();
		releaseHubRepo.create(true);

		// initialize a new release repo locally and make a release into it
		WithCwd wd = new WithCwd(projectAlpha.getRepo().getWorkTree()); {
			assertJoy(Mdm.run(
				"release-init",
				"--use-defaults"
				// note: it's guessing the remote here.  correctly, as it happens.
			));

			new Josh("cat").args(".gitmodules").start().get();
			new Josh("cat").args("releases/.git/config").start().get();

			IOForge.saveFile("placeholder", new File("whatever").getCanonicalFile());

			assertJoy(Mdm.run(
				"release",
				"--version=v1",
				"--files=whatever"
			));
		} wd.close();

		// push that release to a hub repo
		wd = new WithCwd(projectAlpha.getRepo().getWorkTree()+"/releases"); {
			new Josh("git").args("push", "--all").start().get();
			new Josh("git").args("push", "--tags").start().get();
		} wd.close();

		// clone the project.  the local releases repo doesn't come along, of course.
		Fixture projectClone = new ProjectClone("projectClone", projectAlpha.getRepo());

		// fetch the release repo again manually by using the git submodule command and the '--checkout' option to override the usual don't-fetch configuration.
		// then make another release to it.
		wd = new WithCwd(projectClone.getRepo().getWorkTree()); {

			new Josh("git").args("submodule", "update", "--init", "--checkout", "releases").start().get();
			new Josh("git").args("status").start().get();

			//assertJoy(Mdm.run(
			//	"release-init",
			//	"--use-defaults"
			//	// TODO: separate test, should explode.  for now.
			//	// ... it doesn't though.  what the hell?
			//	// oh.  it gives a cramped face and says there's already one here.  that's reasonable.  though not what we'll do in the future.
			//));

			IOForge.saveFile("placeholder2", new File("whatever").getCanonicalFile());

			assertJoy(Mdm.run(
				"release",
				"--version=v2",
				"--files=whatever"
			));
		} wd.close();

		// pushing new releases again to a hub repo should also fly
		WithCwd wd2 = new WithCwd(projectClone.getRepo().getWorkTree()+"/releases"); {
			new Josh("git").args("push", "--all").start().get();
			new Josh("git").args("push", "--tags").start().get();
		} wd2.close();
	}

	@Test
	public void releasing_in_a_random_repo_should_fail() throws Exception {
		Fixture projectAlpha = new ProjectAlpha("projectAlpha");

		try {
			MdmExitMessage result = Mdm.run(
				"release",
				"--repo=projectAlpha",
				"--version=v1",
				"--files=whatever"
			);
			fail("expected release in non-release repo to fail, but command exited with '"+result.happy+"' -- \""+result.getMessage()+"\".");
		} catch (MdmModuleRelease.MdmModuleReleaseNeedsBranch result) {
			/* superb */
		}
	}

	@Test
	public void releasing_to_an_empty_path_in_a_repo_should_fail() throws Exception {
		Fixture projectAlpha = new ProjectAlpha("projectAlpha");

		WithCwd wd = new WithCwd(projectAlpha.getRepo().getWorkTree()); {
			try {
				MdmExitMessage result = Mdm.run(
					"release",
					"--version=v1",
					"--files=whatever"
				);
				fail("expected release in non-repo path to fail, but command exited with '"+result.happy+"' -- \""+result.getMessage()+"\".");
			} catch (MdmRepositoryNonexistant result) {
				/* superb */
			}
		} wd.close();
	}
}
