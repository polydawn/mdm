package net.polydawn.mdm.commands;

import static org.junit.Assert.*;
import java.io.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.*;
import org.junit.*;
import org.junit.rules.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class TestMdmReleaseInitCommand extends TestCaseUsingRepository {
	@Rule
	public ExpectedException exception = ExpectedException.none();

	MdmReleaseInitCommand cmd;
	Repository releaseRepo;
	Repository parentRepo;

	@Test
	public void emptyCwdDirIsCleanForRelease() throws Exception {
		cmd = new MdmReleaseInitCommand(null);
		cmd.path = new File(".").getCanonicalPath();
		cmd.validate();
		cmd.assertReleaseRepoAreaClean();
	}

	@Test
	public void cwdDirWithExistingRepoIsNotCleanForRelease() throws Exception {
		Repository repo = new RepositoryBuilder()
			.setWorkTree(new File(".").getCanonicalFile())
			.build();
		repo.create(false);

		cmd = new MdmReleaseInitCommand(repo);
		cmd.path = new File(".").getCanonicalPath();
		cmd.asSubmodule = cmd.isInRepoRoot();
		cmd.validate();
		exception.expect(MdmExitMessage.class);
		cmd.assertReleaseRepoAreaClean();
	}

	@Test
	public void dirWithObstructingFilesIsNotCleanForRelease() throws Exception {
		IOForge.saveFile("", new File("releases").getCanonicalFile());

		cmd = new MdmReleaseInitCommand(null);
		cmd.validate();
		cmd.path = "releases";
		exception.expect(MdmExitMessage.class);
		cmd.assertReleaseRepoAreaClean();
	}

	@Test
	public void createReleaseRepoWithoutExceptions() throws Exception {
		cmd = new MdmReleaseInitCommand(null);
		cmd.path = new File(".").getCanonicalPath();
		cmd.validate();

		releaseRepo = cmd.makeReleaseRepo();
		cmd.makeReleaseRepoFoundingCommit(releaseRepo);
		cmd.makeReleaseRepoInitBranch(releaseRepo);
	}

	@Test
	public void releaseRepoFirstCommitContainsReadmeFile() throws Exception {
		createReleaseRepoWithoutExceptions();

		ObjectId commitId = releaseRepo.resolve(Constants.HEAD);
		RevCommit revCommit = new RevWalk(releaseRepo).parseCommit(commitId);
		TreeWalk treeWalk = new TreeWalk(releaseRepo);
		treeWalk.reset(revCommit.getTree());
		assertTrue(treeWalk.next());
		assertEquals("README", treeWalk.getNameString());
		assertFalse(treeWalk.next());
	}

	@Test
	public void releaseRepoInitBranchContainsOneCommit() throws Exception {
		createReleaseRepoWithoutExceptions();

		ObjectId commitId = releaseRepo.resolve("mdm/init");
		RevCommit revCommit = new RevWalk(releaseRepo).parseCommit(commitId);
		assertEquals(0, revCommit.getParentCount());
	}

	@Test
	public void releaseRepoHeadReferencesMaster() throws Exception {
		createReleaseRepoWithoutExceptions();

		Ref headRef = releaseRepo.getRef(Constants.HEAD);
		assertEquals(releaseRepo.getRef(Constants.MASTER), headRef.getTarget());
	}

	@Test
	public void createReleaseRepoSubmoduleWithoutExceptions() throws Exception {
		parentRepo = new RepositoryBuilder()
			.setWorkTree(new File(".").getCanonicalFile())
			.build();
		parentRepo.create(false);

		cmd = new MdmReleaseInitCommand(parentRepo);
		// ach, I can't let validate() transform its realization that we're in submodule mode into selecting a default path, because it would be relative, and the surrealcwd problem appears again
		cmd.path = "releases";
		cmd.asSubmodule = true;
		cmd.validate();

		releaseRepo = cmd.makeReleaseRepo();
		cmd.makeReleaseRepoFoundingCommit(releaseRepo);
		cmd.makeReleaseRepoInitBranch(releaseRepo);

		cmd.writeParentGitmoduleConfig(parentRepo);
		cmd.writeReleaseRepoConfig(releaseRepo);
		cmd.makeParentRepoLinkCommit(parentRepo);
	}

	@Test
	public void releaseRepoSubmoduleInitBranchContainsOneCommit() throws Exception {
		createReleaseRepoSubmoduleWithoutExceptions();

		ObjectId commitId = releaseRepo.resolve("mdm/init");
		RevCommit revCommit = new RevWalk(releaseRepo).parseCommit(commitId);
		assertEquals(0, revCommit.getParentCount());
	}

	@Test
	public void releaseRepoSubmoduleParentContainsLink() throws Exception {
		createReleaseRepoSubmoduleWithoutExceptions();

		ObjectId commitId = parentRepo.resolve(Constants.HEAD);
		RevCommit revCommit = new RevWalk(parentRepo).parseCommit(commitId);
		TreeWalk treeWalk = new TreeWalk(parentRepo);
		treeWalk.reset(revCommit.getTree());
		// not completely sure why these should be consistently this order, but appears to be so
		assertTrue(treeWalk.next());
		assertEquals(".gitmodules", treeWalk.getNameString());
		assertTrue(treeWalk.next());
		assertEquals("releases", treeWalk.getNameString());
		assertEquals(FileMode.GITLINK, treeWalk.getFileMode(0));
		assertFalse(treeWalk.next());
	}
}
