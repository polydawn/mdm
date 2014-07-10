package net.polydawn.mdm.scenarios;

import net.polydawn.josh.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import net.polydawn.mdm.test.WithCwd;
import org.junit.*;
import org.junit.runner.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class PlainSubmoduleMergeExample extends TestCaseUsingRepository {
	final Josh git = new Josh("git");

	@Test
	public void should_not_conflict_when_merging_branch_where_submodule_moved_forward() throws Exception {
		Fixture project = new ProjectAlpha("projectAlpha");
		ProjectGamma sourceDepUpstream = new ProjectGamma("projectGamma");

		new Josh("pwd").start();

		// set up a submodule on master, then branch and change it
		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			git.args("submodule", "add", sourceDepUpstream.getRepo().getWorkTree().toString()).start().get();
			WithCwd wd2 = new WithCwd("projectGamma"); {
				git.args("checkout", sourceDepUpstream.getCommits().get(1).name()).start().get();
			}; wd2.close();
			git.args("add", "projectGamma").start().get();
			git.args("commit", "-m", "link projectGamma at its 1th commit").start().get();

			git.args("checkout", "-b", "blue").start().get();
			wd2 = new WithCwd("projectGamma"); {
				git.args("checkout", sourceDepUpstream.getCommits().get(2).name()).start().get();
			}; wd2.close();
			git.args("add", "projectGamma").start().get();
			git.args("commit", "-m", "link projectGamma at its 2th commit").start().get();
		} wd.close();

		// do a merge.  this should not be conflicting.
		wd = new WithCwd(project.getRepo().getWorkTree()); {
			// merge one branch.  should go clean.
			git.args("merge", "--no-ff", "blue").start().get();
		} wd.close();

		// check that anyone else can read this state with a straight face; status should be clean
		new Josh("git").args("status").cwd(project.getRepo().getWorkTree())/*.opts(Opts.NullIO)*/.start().get();
	}
}
