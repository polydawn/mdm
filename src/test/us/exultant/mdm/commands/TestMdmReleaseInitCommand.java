package us.exultant.mdm.commands;

import java.io.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.rules.*;
import us.exultant.ahs.iob.*;
import us.exultant.mdm.test.*;
import us.exultant.mdm.*;

public class TestMdmReleaseInitCommand extends TestCaseUsingRepository {
	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void emptyCwdDirIsCleanForRelease() throws Exception {
		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null, null);
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

		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(repo, null);
		cmd.path = new File(".").getCanonicalPath();
		cmd.asSubmodule = cmd.isInRepoRoot();
		cmd.validate();
		exception.expect(MdmExitMessage.class);
		cmd.assertReleaseRepoAreaClean();
	}

	@Test
	public void dirWithObstructingFilesIsNotCleanForRelease() throws Exception {
		IOForge.saveFile("", new File("releases"));

		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null, null);
		cmd.validate();
		cmd.path = "releases";
		exception.expect(MdmExitMessage.class);
		cmd.assertReleaseRepoAreaClean();
	}

	@Test
	public void createReleaseRepoWithoutExceptions() throws Exception {
		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null, null);
		cmd.path = new File(".").getCanonicalPath();
		cmd.validate();

		Repository releaserepo = cmd.makeReleaseRepo();
	}

	@Test
	public void createReleaseRepoFirstCommitWithoutExceptions() throws Exception {
		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null, null);
		cmd.path = new File(".").getCanonicalPath();
		cmd.validate();

		Repository releaserepo = cmd.makeReleaseRepo();
		cmd.makeReleaseRepoFoundingCommit(releaserepo);
	}

	@Test
	public void createReleaseRepoInitBranchWithoutExceptions() throws Exception {
		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null, null);
		cmd.path = new File(".").getCanonicalPath();
		cmd.validate();

		Repository releaserepo = cmd.makeReleaseRepo();
		cmd.makeReleaseRepoFoundingCommit(releaserepo);
		cmd.makeReleaseRepoInitBranch(releaserepo);
	}
}
