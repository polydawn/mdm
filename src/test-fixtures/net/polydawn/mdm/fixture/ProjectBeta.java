package net.polydawn.mdm.fixture;

import static net.polydawn.mdm.fixture.FixtureUtil.*;
import java.io.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import us.exultant.ahs.iob.*;
import us.exultant.ahs.util.*;

/**
 * ProjectBeta is a project with no dependencies and one commit (1 file, 1 dir, 1 deep
 * file) (it's the exact same as ProjectAlpha, but with a different name, in case you have
 * two of them and want to be able to instantly disambiguate).
 */
public class ProjectBeta implements Fixture {
	public ProjectBeta(String path) throws IOException {
		repo = setUpPlainRepo(path);

		WithCwd wd = new WithCwd(repo.getWorkTree()); {
			IOForge.saveFile("beta file 1", new File("./beta").getCanonicalFile());
			new File("./beta.d/").getCanonicalFile().mkdirs();
			IOForge.saveFile("beta file 2", new File("./beta.d/beta2").getCanonicalFile());
		} wd.close();

		try {
			new Git(repo).add()
				.addFilepattern(".")
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new FixtureSetupError(e);
		}

		try {
			new Git(repo).commit()
				.setAll(true)
				.setMessage("commit 1 in ProjectBeta")
				.call();
		} catch (NoMessageException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (NoHeadException e) {
			throw new FixtureSetupError(e);
		} catch (UnmergedPathsException e) {
			throw new FixtureSetupError(e);
		} catch (ConcurrentRefUpdateException e) {
			throw new FixtureSetupError(e);
		} catch (WrongRepositoryStateException e) {
			throw new FixtureSetupError(e);
		} catch (GitAPIException e) {
			throw new FixtureSetupError(e);
		}
	}

	Repository repo;

	public Repository getRepo() {
		return repo;
	}
}
