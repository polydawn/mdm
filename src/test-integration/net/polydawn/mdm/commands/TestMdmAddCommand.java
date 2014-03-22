package net.polydawn.mdm.commands;

import static net.polydawn.mdm.fixture.FixtureUtil.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.jgit.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.api.*;
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

	@Test
	public void testAddFromLocalRelrepWithSingleVersion() throws Exception {
		WithCwd wd = new WithCwd(pathProjectRepo1); {
			MdmAddCommand cmd = new MdmAddCommand(projectRepo1);
			cmd.url = new File(".", "../"+pathReleaseRepo1).getCanonicalFile().toString(); // this one just because git doesn't much care for relative urls
			cmd.name = "depname";
			cmd.pathLibs = new File("lib");
			cmd.version = "v1";
			cmd.validate();
			assertJoy(cmd.call());
		} wd.close();

		File depPath = new File(pathProjectRepo1+"/lib/depname").getCanonicalFile();

		// i do hope there's a filesystem there now
		assertTrue("dependency module path exists on fs", depPath.exists());
		assertTrue("dependency module path is dir", depPath.isDirectory());

		// assert on the refs in the release module we added to the project repo
		Collection<Ref> refs = new Git(projectRepo1).lsRemote()
				.setRemote(depPath.toString())
				.call();
		List<String> refNames = new ArrayList<String>(refs.size());
		for (Ref r : refs) refNames.add(r.getName());
		assertTrue("head ref present in dependency module", refNames.contains("HEAD"));
		assertTrue("release branch present in dependency module", refNames.contains("refs/heads/mdm/release/v1"));
		assertTrue("release tag present in dependency module", refNames.contains("refs/tags/release/v1"));
		assertEquals("exactly these three refs present in dependency module", 3, refNames.size());

		// check the actual desired artifacts are inside the release module location
		assertEquals("exactly two files exist (.git and the arifact)", 2, depPath.listFiles().length);
		assertEquals("content of artifact is correct", "alpha", IOForge.readFileAsString(new File(depPath, "a")));
	}
}
