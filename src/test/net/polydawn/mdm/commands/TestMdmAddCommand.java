package net.polydawn.mdm.commands;

import java.io.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.jgit.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.rules.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class TestMdmAddCommand extends TestCaseUsingRepository {
	@Rule
	public ExpectedException exception = ExpectedException.none();

	static {
		// apply fixes for questionable jgit behavior
		SystemReaderFilteringProxy.apply();
	}

	static final String pathReleaseRepo1 = "releaseRepo1";
	static final String pathReleaseRepo2 = "releaseRepo2";
	static final String pathProjectRepo1 = "projectRepo1";
	static final String pathProjectRepo2 = "projectRepo2";
	// release repos for two entirely unreleated "libraries", and
	// two project repos, one of which is a clone and pulls from the other.
	// XXX: actually, maybe that second project repo is a little unnecessarily unless we're an update or status command.
	//   .... noooope, very necessary.  add should error reasonably when someone else added, you pulled, and you didn't update yet.
	Repository releaseRepo1;
	Repository releaseRepo2;
	Repository projectRepo1;
	Repository projectRepo2;

	@Before
	public void setUp2() throws IOException, MdmExitMessage, ConfigInvalidException, MdmException {
		releaseRepo1 = setUpReleaseRepo(pathReleaseRepo1);
		releaseRepo2 = setUpReleaseRepo(pathReleaseRepo2);
		projectRepo1 = setUpPlainRepo(pathProjectRepo1);
		projectRepo2 = setUpPlainRepo(pathProjectRepo2);

		IOForge.saveFile("alpha", new File("./a").getCanonicalFile());
		MdmReleaseCommand cmd = new MdmReleaseCommand(null);
		cmd.relRepoPath = new File(pathReleaseRepo1).getCanonicalPath();
		cmd.version = "v1";
		cmd.inputPath = "a";
		cmd.validate();
		cmd.call();
	}

	private static Repository setUpPlainRepo(String path) throws IOException {
		Repository repo = new RepositoryBuilder()
			.setWorkTree(new File(path).getCanonicalFile())
			.build();
		repo.create(false);
		return repo;
	}

	private static Repository setUpReleaseRepo(String path) throws IOException {
		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null);
		cmd.path = new File(path).getCanonicalPath();
		cmd.validate();
		Repository repo = cmd.makeReleaseRepo();
		cmd.makeReleaseRepoFoundingCommit(repo);
		cmd.makeReleaseRepoInitBranch(repo);
		return repo;
	}

	@Test
	public void testAddFromLocalRelrepWithSingleVersion() throws Exception {
		 // FIXME java's hostility to the concept of "setCwd" is making this painfully complicated again
		MdmAddCommand cmd = new MdmAddCommand(projectRepo1);
		cmd.url = pathReleaseRepo1;
		cmd.name = "depname";
		cmd.pathLibs = new File("lib").getCanonicalFile();
		cmd.version = "v1";
		cmd.validate();
		assertJoy(cmd.call());
	}
}
