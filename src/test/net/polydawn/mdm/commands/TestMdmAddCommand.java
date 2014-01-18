package net.polydawn.mdm.commands;

import java.io.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.rules.*;
import org.junit.runner.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class TestMdmAddCommand extends TestCaseUsingRepository {
	@Rule
	public ExpectedException exception = ExpectedException.none();

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
	MdmAddCommand cmd;

	@Before
	public void setUp() throws IOException {
		releaseRepo1 = setUpReleaseRepo(pathReleaseRepo1);
		releaseRepo2 = setUpReleaseRepo(pathReleaseRepo2);
		projectRepo1 = setUpPlainRepo(pathProjectRepo1);
		projectRepo2 = setUpPlainRepo(pathProjectRepo2);
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
	public void testsss() throws Exception {
		cmd = new MdmAddCommand(null, null);
		// cmd.path = new File(pathProjectRepo1).getCanonicalPath();
		// cmd.validate();
		// cmd.assertSomething();
	}
}
