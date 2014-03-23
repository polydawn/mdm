package net.polydawn.mdm.fixture;

import java.io.*;
import java.util.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.commands.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.*;

public class FixtureUtil {
	public static Repository setUpPlainRepo(String path) {
		try {
			Repository repo = new RepositoryBuilder()
				.setWorkTree(new File(path).getCanonicalFile())
				.build();
			repo.create(false);
			return repo;
		} catch (IOException e) {
			throw new FixtureSetupError(e);
		}
	}

	public static Repository setUpReleaseRepo(String path) {
		try {
			MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null);
			cmd.path = new File(path).getCanonicalPath();
			cmd.validate();
			cmd.call();
			Repository releaserepo = new RepositoryBuilder()
				.setWorkTree(new File(path).getCanonicalFile())
				.build();
			return releaserepo;
		} catch (IOException e) {
			throw new FixtureSetupError(e);
		} catch (ConfigInvalidException e) {
			throw new FixtureSetupError(e);
		} catch (MdmException e) {
			throw new FixtureSetupError(e);
		}
	}

	public static List<String> listTreePaths(Repository repo, String ref) throws IOException {
		ObjectId commitId = repo.getRef(ref).getObjectId();
		RevCommit revCommit = new RevWalk(repo).parseCommit(commitId);
		TreeWalk treeWalk = new TreeWalk(repo);
		treeWalk.setRecursive(true);
		treeWalk.reset(revCommit.getTree());
		List<String> answer = new ArrayList<String>();
		while (treeWalk.next()) {
			answer.add(treeWalk.getPathString());
		}
		return answer;
	}
}
