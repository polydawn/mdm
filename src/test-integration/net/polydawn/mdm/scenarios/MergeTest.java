package net.polydawn.mdm.scenarios;

import static org.junit.Assert.assertTrue;
import java.io.*;
import net.polydawn.josh.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import net.polydawn.mdm.test.WithCwd;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class MergeTest extends TestCaseUsingRepository {
	final Josh git = new Josh("git");

	Fixture project;
	Fixture releases;

	public void setup() throws Exception {
		project = new ProjectAlpha("projectAlpha");
		releases = new ProjectBetaReleases("projectBeta-releases");

		// set up a library, then two branches with divering versions of it
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			assertJoy(Mdm.run(
				"add",
				releases.getRepo().getWorkTree().toString(),
				"--version=v1.0",
				"--name=beta"
			));
			git.args("checkout", "-b", "blue").start().get();
			git.args("checkout", "-b", "green").start().get();

			git.args("checkout", "blue").start().get();
			assertJoy(Mdm.run(
				"alter",
				"lib/beta",
				"--version=v1.1"
			));

			git.args("checkout", "green").start().get();
			assertJoy(Mdm.run(
				"alter",
				"lib/beta",
				"--version=v2.0"
			));
		} wd.close();
	}

	@Test
	public void mergeTakingTheirs() throws Exception {
		setup();

		// do a merges.  the second should fail with conflicts.
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "master").start().get();
			// TODO: notice how we didn't switch to the master's state, we left it in... green?  should probably affix this in setup and make tests covering the range of start points

			// merge one branch.  should go clean.
			git.args("merge", "--no-ff", "blue").start().get();

			// merge second branch.  should conflict (exit code is nonzero)
			git.args("merge", "--no-ff", "green").okExit(new int[] { 1 }).start().get();

			// choose their gitmodules file, then update to put that version in place
			git.args("checkout", "--theirs", ".gitmodules").start().get();
			assertJoy(Mdm.run("update"));

			// should be able to stage changes and commit
			git.args("add", ".gitmodules", "lib/beta").start().get();
			git.args("commit", "--no-edit").start().get();
		} wd.close();

		// now verify.
		File depWorkTreePath = new File(project.getRepo().getWorkTree()+"/lib/beta").getCanonicalFile();
		File depGitDataPath = new File(project.getRepo().getDirectory()+"/modules/lib/beta").getCanonicalFile();

		// i do hope there's a filesystem there now
		assertTrue("dependency module path exists on fs", depWorkTreePath.exists());
		assertTrue("dependency module path is dir", depWorkTreePath.isDirectory());

		// check that anyone else can read this state with a straight face; status should be clean
		new Josh("git").args("status").cwd(project.getRepo().getWorkTree())/*.opts(Opts.NullIO)*/.start().get();
		new Josh("git").args("status").cwd(depWorkTreePath)/*.opts(Opts.NullIO)*/.start().get();
	}
}
