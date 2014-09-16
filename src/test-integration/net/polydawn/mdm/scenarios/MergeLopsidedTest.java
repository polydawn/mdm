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

/**
 * Test merges where one branch is lacking mdm at all. May come up the first time you're
 * introducing mdm to an existing project, or really any time you add a library on one
 * branch and then are doing merges out from there.
 */
@RunWith(OrderedJUnit4ClassRunner.class)
public class MergeLopsidedTest extends TestCaseUsingRepository {
	final Josh git = new Josh("git");

	Fixture project;
	Fixture releases;

	public void setup() throws Exception {
		project = new ProjectAlpha("projectAlpha");
		releases = new ProjectBetaReleases("projectBeta-releases");

		// create one branch off of master; add a library only on the branch
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "-b", "blue").start().get();
			assertJoy(Mdm.run(
				"add",
				releases.getRepo().getWorkTree().toString(),
				"--version=v1.0",
				"--name=beta"
			));

			// switch back to master
			git.args("checkout", "master").start().get();

			// make one more commit just so it's not a degenerate fast-forward merge this direction
			IOForge.saveFile("asdf", new File("somefile").getCanonicalFile());
			git.args("add", "--", "somefile").start().get();
			git.args("commit", "-m", "somecommit").start().get();


			// we still left the library sitting in the working tree
			// i don't think any of the tests in this set care one way or the other
		} wd.close();
	}

	@Test
	public void merge_a_branch_that_adds_a_dep() throws Exception {
		setup();

		// do a merges.
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			// merge one branch.  should go clean.
			git.args("merge", "--no-ff", "blue").start().get();

			// that should have just committed cleanly, that's it
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

	@Test
	public void merge_a_branch_without_a_dep() throws Exception {
		setup();

		// do a merges.
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			// switch to the branch with the lib
			git.args("checkout", "blue").start().get();

			// merge the master branch, which doesn't have the lib yet
			git.args("merge", "--no-ff", "master").start().get();

			// that should have just committed cleanly, that's it
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

	@Test
	public void update_while_merging_a_branch_without_a_dep() throws Exception {
		setup();

		// do a merges.
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			// switch to the branch with the lib
			git.args("checkout", "blue").start().get();

			// merge the master branch, which doesn't have the lib yet
			git.args("merge", "--no-ff", "--no-commit", "master").start().get();

			// we simulated a merge conflict with --no-commit.  try to mdm-update during it  // TODO: maybe not well enough...
			assertJoy(Mdm.run("update", "--strict"));

			// finish the merge.
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

	@Test
	public void update_while_merging_a_branch_that_adds_a_dep() throws Exception {
		setup();

		// do a merges.
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			// merge one branch.  should go clean.
			git.args("merge", "--no-ff", "--no-commit", "blue").start().get();

			// we simulated a merge conflict with --no-commit.  try to mdm-update during it  // TODO: maybe not well enough...
			assertJoy(Mdm.run("update", "--strict"));

			// finish the merge.
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
