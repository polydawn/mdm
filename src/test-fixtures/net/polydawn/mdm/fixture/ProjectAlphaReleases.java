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
 * ProjectAlphaReleases has one release ("v1") with a single file ("alpha").
 */
public class ProjectAlphaReleases implements Fixture {
	public ProjectAlphaReleases(String path) {
		try {
			repo = setUpReleaseRepo(path);

			WithCwd wd = new WithCwd(new File(path, ".git/staging")); {
				IOForge.saveFile("alpha release", new File("./alpha").getCanonicalFile());
			} wd.close();

			MdmReleaseCommand cmd = new MdmReleaseCommand(null);
			cmd.relRepoPath = new File(path).getCanonicalPath();
			cmd.version = "v1";
			cmd.inputPath = cmd.relRepoPath+"/.git/staging/alpha";
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
			Repository repo = new ProjectAlphaReleases("proj-releases").getRepo();
			assertEquals(
				Arrays.asList(new String[] {
					"README",
					"v1/alpha"
				}),
				listTreePaths(repo, "refs/heads/master")
			);
		}
	}
}
