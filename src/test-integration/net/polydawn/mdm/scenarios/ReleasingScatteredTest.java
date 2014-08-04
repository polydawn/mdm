package net.polydawn.mdm.scenarios;

import java.io.*;
import net.polydawn.josh.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import net.polydawn.mdm.test.WithCwd;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class ReleasingScatteredTest extends TestCaseUsingRepository {
	@Before
	public void setup() throws Exception {
		projectAlpha = new ProjectAlpha("projectAlpha");
		releaseHubRepo = new RepositoryBuilder()
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
		projectClone = new ProjectClone("projectClone", projectAlpha.getRepo());
	}

	Fixture projectAlpha;
	Repository releaseHubRepo;
	Fixture projectClone;

	@Test
	public void releasing_in_linked_relrepo_after_manually_reacquring_relrepo() throws Exception {
		// fetch the release repo again manually by using the git submodule command and the '--checkout' option to override the usual don't-fetch configuration.
		// then make another release to it.
		WithCwd wd = new WithCwd(projectClone.getRepo().getWorkTree()); {

			new Josh("git").args("submodule", "update", "--init", "--checkout", "releases").start().get();

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
}
