package us.exultant.mdm.commands;

import java.io.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.rules.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;
import us.exultant.mdm.*;
import us.exultant.mdm.test.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class TestMdmReleaseCommand extends TestCaseUsingRepository {
	@Rule
	public ExpectedException exception = ExpectedException.none();

	MdmReleaseCommand cmd;
	MdmModuleRelease relModule;
	Repository parentRepo;

	public void prepareUnparentedReleaseRepo() throws Exception {
		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null, null);
		cmd.path = new File(".").getCanonicalPath();
		cmd.validate();
		cmd.call();
	}

	public void prepareParentedReleaseRepo() throws Exception {
		parentRepo = new RepositoryBuilder()
			.setWorkTree(new File(".").getCanonicalFile())
			.build();
		parentRepo.create(false);

		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(parentRepo, null);
		cmd.path = "releases";
		cmd.asSubmodule = true;
		cmd.validate();
		cmd.call();
	}

	@Test
	public void emptyReleaseRepoCleanForRelease() throws Exception {
		prepareUnparentedReleaseRepo();

		cmd = new MdmReleaseCommand(null, null);
		cmd.relRepoPath = new File(".").getCanonicalPath();
		cmd.version = "v1";
		cmd.validate();

		relModule = cmd.loadReleaseModule();
		cmd.assertReleaseRepoClean(relModule);
	}

	@Test
	public void releaseRepoWithUncommittedFilesRejectedForRelease() throws Exception {
		prepareUnparentedReleaseRepo();

		cmd = new MdmReleaseCommand(null, null);
		cmd.relRepoPath = new File(".").getCanonicalPath();
		cmd.version = "v1";
		cmd.validate();

		IOForge.saveFile("", new File("./dirty").getCanonicalFile());

		relModule = cmd.loadReleaseModule();
		exception.expect(MdmExitMessage.class);
		cmd.assertReleaseRepoClean(relModule);
	}

	@Test
	public void emptyReleaseRepoHasNoExistingVersionConflict() throws Exception {
		prepareUnparentedReleaseRepo();

		cmd = new MdmReleaseCommand(null, null);
		cmd.relRepoPath = new File(".").getCanonicalPath();
		cmd.version = "v1";
		cmd.validate();

		relModule = cmd.loadReleaseModule();
		cmd.assertReleaseRepoDoesntAlreadyContain(relModule, cmd.version);
	}
}
