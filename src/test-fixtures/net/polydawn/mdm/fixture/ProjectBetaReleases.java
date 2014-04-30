package net.polydawn.mdm.fixture;

import static net.polydawn.mdm.fixture.FixtureUtil.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.commands.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import us.exultant.ahs.iob.*;

/**
 * ProjectBetaReleases has three releases, all of which contain a single file.
 */
public class ProjectBetaReleases implements Fixture {
	public ProjectBetaReleases(String path) {
			repo = setUpReleaseRepo(path);

			release("1.0");
			release("1.1");
			release("2.0");
	}

	private void release(String version) {
		try {
			WithCwd wd = new WithCwd(new File(repo.getWorkTree(), ".git/staging")); {
				IOForge.saveFile("beta release "+version, new File("./beta").getCanonicalFile());
			} wd.close();

			MdmReleaseCommand cmd = new MdmReleaseCommand(null);
			cmd.relRepoPath = repo.getWorkTree().getCanonicalPath();
			cmd.version = "v"+version;
			cmd.inputPath = cmd.relRepoPath+"/.git/staging/beta";
			cmd.validate();
			cmd.call();
		} catch (IOException e) {
			throw new FixtureSetupError(e);
		} catch (MdmExitMessage e) {
			throw new FixtureSetupError(e);
		} catch (MdmException e) {
			throw new FixtureSetupError(e);
		}
	}

	Repository repo;

	public Repository getRepo() {
		return repo;
	}



	public static class SanityCheck extends TestCaseUsingRepository {
		@Test
		public void sanityCheck() throws IOException {
			Repository repo = new ProjectBetaReleases("proj-releases").getRepo();
			assertEquals(
				Arrays.asList(new String[] {
					"README",
					"v1.0/beta",
					"v1.1/beta",
					"v2.0/beta"
				}),
				listTreePaths(repo, "refs/heads/master")
			);
		}
	}
}
