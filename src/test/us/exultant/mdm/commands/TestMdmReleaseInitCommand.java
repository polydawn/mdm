package us.exultant.mdm.commands;

import static org.junit.Assert.*;
import java.io.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.*;
import org.junit.*;
import org.junit.rules.*;
import us.exultant.ahs.iob.*;
import us.exultant.mdm.test.*;
import us.exultant.mdm.*;

public class TestMdmReleaseInitCommand extends TestCaseUsingRepository {
	@Rule
	public ExpectedException exception = ExpectedException.none();

	MdmReleaseInitCommand cmd;
	Repository releaseRepo;

	@Test
	public void emptyCwdDirIsCleanForRelease() throws Exception {
		cmd = new MdmReleaseInitCommand(null, null);
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

		cmd = new MdmReleaseInitCommand(repo, null);
		cmd.path = new File(".").getCanonicalPath();
		cmd.asSubmodule = cmd.isInRepoRoot();
		cmd.validate();
		exception.expect(MdmExitMessage.class);
		cmd.assertReleaseRepoAreaClean();
	}

	@Test
	public void dirWithObstructingFilesIsNotCleanForRelease() throws Exception {
		IOForge.saveFile("", new File("releases"));

		cmd = new MdmReleaseInitCommand(null, null);
		cmd.validate();
		cmd.path = "releases";
		exception.expect(MdmExitMessage.class);
		cmd.assertReleaseRepoAreaClean();
	}

	@Test
	public void createReleaseRepoWithoutExceptions() throws Exception {
		cmd = new MdmReleaseInitCommand(null, null);
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
}
