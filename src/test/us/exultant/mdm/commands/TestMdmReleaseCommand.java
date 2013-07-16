package us.exultant.mdm.commands;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
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

	public void prepareUnparentedReleaseRepo(String path) throws Exception {
		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null, null);
		cmd.path = new File(path).getCanonicalPath();
		cmd.validate();
		cmd.call();
	}

	public void prepareParentedReleaseRepo(String path) throws Exception {
		parentRepo = new RepositoryBuilder()
			.setWorkTree(new File(path).getCanonicalFile())
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
		prepareUnparentedReleaseRepo(".");

		cmd = new MdmReleaseCommand(null, null);
		cmd.relRepoPath = new File(".").getCanonicalPath();
		cmd.version = "v1";
		cmd.validate();

		relModule = cmd.loadReleaseModule();
		cmd.assertReleaseRepoClean(relModule);
	}

	@Test
	public void releaseRepoWithUncommittedFilesRejectedForRelease() throws Exception {
		prepareUnparentedReleaseRepo(".");

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
		prepareUnparentedReleaseRepo(".");

		cmd = new MdmReleaseCommand(null, null);
		cmd.relRepoPath = new File(".").getCanonicalPath();
		cmd.version = "v1";
		cmd.validate();

		relModule = cmd.loadReleaseModule();
		cmd.assertReleaseRepoDoesntAlreadyContain(relModule, cmd.version);
	}

	@Test
	public void selectExplicitFile() throws Exception {
		prepareUnparentedReleaseRepo("rel");

		cmd = new MdmReleaseCommand(null, null);
		cmd.relRepoPath = new File("rel").getCanonicalPath();
		cmd.version = "v1";
		cmd.inputPath = "a";
		cmd.validate();

		IOForge.saveFile("alpha", new File("./a").getCanonicalFile());
		IOForge.saveFile("beta",  new File("./b").getCanonicalFile());

		relModule = cmd.loadReleaseModule();
		List<File> files = cmd.selectInputFiles();

		assertEquals(1, files.size());
		assertEquals("a", files.get(0).getName());
	}

	@Test
	public void selectDirectoryContainingFiles() throws Exception {
		prepareUnparentedReleaseRepo("rel");

		cmd = new MdmReleaseCommand(null, null);
		cmd.relRepoPath = new File("rel").getCanonicalPath();
		cmd.version = "v1";
		cmd.inputPath = ".";
		cmd.validate();

		IOForge.saveFile("alpha", new File("./a").getCanonicalFile());
		IOForge.saveFile("beta",  new File("./b").getCanonicalFile());

		relModule = cmd.loadReleaseModule();
		List<File> files = cmd.selectInputFiles();

		assertEquals(2, files.size());
		assertEquals("a", files.get(0).getName());
		assertEquals("b", files.get(1).getName());
	}

	@Test
	public void selectDirectoryContainingDirectories() throws Exception {
		prepareUnparentedReleaseRepo("rel");

		cmd = new MdmReleaseCommand(null, null);
		cmd.relRepoPath = new File("rel").getCanonicalPath();
		cmd.version = "v1";
		cmd.inputPath = "dir";
		cmd.validate();

		new File("dir").getCanonicalFile().mkdir();
		IOForge.saveFile("alpha", new File("./dir/a").getCanonicalFile());
		IOForge.saveFile("beta",  new File("./dir/b").getCanonicalFile());
		new File("dir/d").getCanonicalFile().mkdir();	// ... not sure if want ignore?  but if we do copy it, git won't notice empty dirs anyway, and we can't assume an application's release semantics are fine with getting .gitignore files strewn about either.

		relModule = cmd.loadReleaseModule();
		List<File> files = cmd.selectInputFiles();

		assertEquals(2, files.size());
		assertEquals("a", files.get(0).getName());
		assertEquals("b", files.get(1).getName());
	}
}
