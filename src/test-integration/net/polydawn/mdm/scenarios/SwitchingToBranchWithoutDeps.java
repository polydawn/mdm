package net.polydawn.mdm.scenarios;

import static org.junit.Assert.assertTrue;
import java.io.*;
import net.polydawn.josh.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import net.polydawn.mdm.test.WithCwd;
import net.polydawn.mdm.util.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class SwitchingToBranchWithoutDeps extends TestCaseUsingRepository {
	final Josh git = new Josh("git");

	@Test
	public void should_delete_unlinked_deps_on_update() throws Exception {
		Fixture project = new ProjectAlpha("projectAlpha");
		Fixture releases = new ProjectAlphaReleases("projectAlpha-releases");

		// create a branch and link a dep on it.  (leave master with no deps.)
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "-b", "with-mdm").start().get();
			assertJoy(Mdm.run(
				"add",
				releases.getRepo().getWorkTree().toString(),
				"--version=v1",
				"--name=alpha"
			));
		} wd.close();

		// sanity check: dep path should exist
		File depWorkTreePath = new File(project.getRepo().getWorkTree()+"/lib/alpha").getCanonicalFile();
		assertTrue("dependency module path should exist on fs", depWorkTreePath.exists());
		assertTrue("dependency module path should be dir", depWorkTreePath.isDirectory());

		// checkout back onto master.  update.
		wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "master").start().get();
			assertJoy(Mdm.run("update"));
		} wd.close();

		// dep path should not exist
		assertTrue("dependency module path should be absent on fs", !depWorkTreePath.exists());
	}

	@Test
	public void should_leave_any_submodules_alone_if_dirty_working_tree() throws Exception {
		Fixture project = new ProjectAlpha("projectAlpha");
		Fixture releases = new ProjectAlphaReleases("projectAlpha-releases");

		// create a branch and link a dep on it.  (leave master with no deps.)
		// put dirty files in the dep dir after linking it.
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "-b", "with-mdm").start().get();
			assertJoy(Mdm.run(
				"add",
				releases.getRepo().getWorkTree().toString(),
				"--version=v1",
				"--name=alpha"
			));
			IOForge.saveFile("junk", new File("lib/alpha/uncommitted").getCanonicalFile());
		} wd.close();

		// checkout back onto master.  update.
		wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "master").start().get();
			assertJoy(Mdm.run("update"));
		} wd.close();

		// dep path should remain
		assertTrue("dependency module path should remain present on fs", new File(project.getRepo().getWorkTree()+"/lib/alpha").getCanonicalFile().exists());
	}

	@Test
	public void should_leave_normal_unlinked_submodules_alone_on_update() throws Exception {
		Fixture project = new ProjectAlpha("projectAlpha");
		Fixture project2 = new ProjectBeta("projectBeta");

		// create a branch and link a dep on it.  (leave master with no deps.)
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "-b", "with-submodule").start().get();
			git.args("submodule", "add", project2.getRepo().getWorkTree().getPath()).start().get();
		} wd.close();

		// sanity check: submodule path should exist
		File depWorkTreePath = new File(project.getRepo().getWorkTree()+"/projectBeta").getCanonicalFile();
		assertTrue("submodule path should exist on fs", depWorkTreePath.exists());
		assertTrue("submodule path should be dir", depWorkTreePath.isDirectory());

		// checkout back onto master.  update.
		wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "master").start().get();
			assertJoy(Mdm.run("update"));
		} wd.close();

		// submodule path should remain
		assertTrue("submodule path should remain present on fs", depWorkTreePath.exists());
	}

	@Test
	public void should_leave_symlinks_even_if_they_point_to_deps() throws Exception {
		Fixture project = new ProjectAlpha("projectAlpha");
		Fixture releases = new ProjectAlphaReleases("projectAlpha-releases");

		// create a branch and link a dep on it.  (leave master with no deps.)
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "-b", "with-mdm").start().get();
			assertJoy(Mdm.run(
				"add",
				releases.getRepo().getWorkTree().toString(),
				"--version=v1",
				"--name=alpha"
			));
			new Josh("ln").args("-s", "lib/alpha", "shenanigans").start().get();
		} wd.close();

		// sanity check: dep path should exist
		File depWorkTreePath = new File(project.getRepo().getWorkTree()+"/lib/alpha").getCanonicalFile();
		File symlinkPath = new File(project.getRepo().getWorkTree()+"/shenanigans");
		assertTrue("dependency module path should exist on fs", depWorkTreePath.exists());
		assertTrue("dependency module path should be dir", depWorkTreePath.isDirectory());
		assertTrue("symlink path should exist on fs", symlinkPath.exists());

		// checkout back onto master.  update.
		wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("checkout", "master").start().get();
			assertJoy(Mdm.run("update"));
		} wd.close();

		// dep path should not exist... but the symlink still should!
		assertTrue("dependency module path should be absent on fs", !depWorkTreePath.exists());
//		new File("projectAlpha/lib/alpha").getCanonicalFile().mkdir();
		new Josh("ls").args("-la", "projectAlpha").start().get();
		new Josh("ls").args("-la", "projectAlpha/lib").start().get();
		new Josh("ls").args("-la", "projectAlpha/shenanigans/").okExit(new int[] { 0, 2 }).start().get();
		new Josh("pwd").start().get();
		// ... the behavior is *correct*, I just can't for the life of me figure out how to write a test for it, because a symlink that's dangling is pretty much invisible.
		// damnit, java, would it have been so hard to just have an lstat implementation that noops on fucking windows?
		// this was stupid in 2005 and it's still stupid now: https://bugs.openjdk.java.net/browse/JDK-6246549
		System.err.println(symlinkPath);
		System.err.println(symlinkPath.exists());
		System.err.println(FileUtils.isSymlink(symlinkPath));
		assertTrue("symlink path should remain present on fs", symlinkPath.exists());
	}
}
