package net.polydawn.mdm.fixture;

import static net.polydawn.mdm.fixture.FixtureUtil.*;
import java.io.*;
import java.util.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import us.exultant.ahs.iob.*;
import us.exultant.ahs.util.*;

/**
 * ProjectGamma is a project with no dependencies and several commits, but no branches or
 * merges (history is a straight line).
 */
public class ProjectGamma implements Fixture {
	public ProjectGamma(String path) throws IOException {
		repo = setUpPlainRepo(path);
		commits = new ArrayList<ObjectId>(3);

		for (int i = 0; i < 3; i++) {
			WithCwd wd = new WithCwd(repo.getWorkTree()); {
				IOForge.saveFile("gamma version " + i, new File("./gamma").getCanonicalFile());
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
					.setMessage("commit " + i + " in ProjectGamma")
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

			commits.add(repo.getRef(Constants.HEAD).getObjectId());
		}

		commits = Collections.unmodifiableList(commits);
	}

	Repository repo;
	List<ObjectId> commits;

	public Repository getRepo() {
		return repo;
	}

	public List<ObjectId> getCommits() {
		return commits;
	}
}
