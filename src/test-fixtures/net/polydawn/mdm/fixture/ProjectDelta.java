package net.polydawn.mdm.fixture;

import java.io.*;
import java.util.*;
import net.polydawn.mdm.commands.*;
import net.polydawn.mdm.test.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import us.exultant.ahs.iob.*;

/**
 * ProjectDelta is a project with several dependencies and several commits, but no
 * branches or merges (history is a straight line).
 *
 * ProjectDelta depends on several mdm commands already working in order to produce
 * itself: - MdmReleaseInitCommand - MdmReleaseCommand - MdmAddCommand
 */
public class ProjectDelta implements Fixture {
	public ProjectDelta(String path) throws Exception {
		try {
			repo = FixtureUtil.setUpPlainRepo(path);
			commits = new ArrayList<ObjectId>(4);

			// first commit: just a file
			WithCwd wd = new WithCwd(repo.getWorkTree());
			IOForge.saveFile("delta", new File("./delta").getCanonicalFile());
			wd.close();
			new Git(repo).add()
				.addFilepattern(".")
				.call();

			new Git(repo).commit()
				.setAll(true)
				.setMessage("inital content in ProjectDelta")
				.call();
			commits.add(repo.getRef(Constants.HEAD).getObjectId());

			// spin up releases repos
			//  inside our own working dir (because we have nowhere better to put them); throw them away later
			final Fixture alphaReleases = new ProjectAlphaReleases(new File(path, ".tmp/alpha-releases").getPath());
			final Fixture betaReleases = new ProjectBetaReleases(new File(path, ".tmp/beta-releases").getPath());

			// second commit: add dep on alpha
			wd = new WithCwd(repo.getWorkTree());
			{
				MdmAddCommand cmd = new MdmAddCommand(repo);
				cmd.parse(new Namespace(new HashMap<String,Object>() {
					{
						put("url", alphaReleases.getRepo().getWorkTree().toString());
						put("version", "v1");
						put("lib", "lib");
					}
				}));
				cmd.validate();
				cmd.call();
			}
			wd.close();
			commits.add(repo.getRef(Constants.HEAD).getObjectId());

			// third commit: add dep on beta
			wd = new WithCwd(repo.getWorkTree());
			{
				MdmAddCommand cmd = new MdmAddCommand(repo);
				cmd.parse(new Namespace(new HashMap<String,Object>() {
					{
						put("url", betaReleases.getRepo().getWorkTree().toString());
						put("version", "v1.0");
						put("lib", "lib");
					}
				}));
				cmd.validate();
				cmd.call();
			}
			wd.close();
			commits.add(repo.getRef(Constants.HEAD).getObjectId());

			// toss out the temporary release repos
			IOForge.delete(new File(path, ".tmp"));

			commits = Collections.unmodifiableList(commits);
		} catch (Exception e) {
			throw new FixtureSetupError(e);
		}
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
