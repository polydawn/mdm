package net.polydawn.mdm.fixture;

import static net.polydawn.mdm.fixture.FixtureUtil.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;

/**
 * Clones any other project.
 */
public class ProjectClone implements Fixture {
	public ProjectClone(String path, Repository remote) throws IOException {
		try {
			File dest = new File(path).getCanonicalFile();
			dest.mkdirs();
			repo = Git.cloneRepository()
				.setURI(remote.getWorkTree().getCanonicalPath())
				.setDirectory(dest)
				.call().getRepository();
		} catch (InvalidRemoteException e) {
			throw new FixtureSetupError(e);
		} catch (TransportException e) {
			throw new FixtureSetupError(e);
		} catch (GitAPIException e) {
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
			Repository repo = new ProjectAlpha("proj").getRepo();
			Repository clone = new ProjectClone("clone", repo).getRepo();
			List<String> paths = listTreePaths(clone, "refs/heads/master");
			assertEquals("two committed files", 2, paths.size());
			int i = 0;
			assertEquals("alpha", paths.get(i++));
			assertEquals("dir/alpha2", paths.get(i++));
		}
	}
}
