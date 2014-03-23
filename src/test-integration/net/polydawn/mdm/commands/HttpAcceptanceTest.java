package net.polydawn.mdm.commands;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;

/**
 * This test requires that you be on the internet on planet earth in a reasonable year
 * with no active DNS or BGP snafus, remote services up and in a reasonable state that's
 * totally out of your control, etc.
 */
@RunWith(OrderedJUnit4ClassRunner.class)
public class HttpAcceptanceTest extends TestCaseUsingRepository {
	@Test
	public void testAddFromHttpRepoWithMultipleVersions() throws Exception {
		Fixture project = new ProjectAlpha("projectRepo");

		WithCwd wd = new WithCwd(project.getRepo().getWorkTree()); {
			MdmAddCommand cmd = new MdmAddCommand(project.getRepo());
			cmd.url = "http://mdm-releases.com/io.netty/netty-all-releases.git";
			cmd.name = "netty";
			cmd.pathLibs = new File("lib");
			cmd.version = "4.0.4.Final.mvn";
			cmd.validate();
			assertJoy(cmd.call());
		} wd.close();

		File depPath = new File(project.getRepo().getWorkTree()+"/lib/netty").getCanonicalFile();

		// i do hope there's a filesystem there now
		assertTrue("dependency module path exists on fs", depPath.exists());
		assertTrue("dependency module path is dir", depPath.isDirectory());

		// assert on the refs in the release module we added to the project repo
		Collection<Ref> refs = new Git(project.getRepo()).lsRemote()
				.setRemote(depPath.toString())
				.call();
		List<String> refNames = new ArrayList<String>(refs.size());
		for (Ref r : refs) refNames.add(r.getName());
		assertTrue("head ref present in dependency module", refNames.contains("HEAD"));
		assertTrue("release branch present in dependency module", refNames.contains("refs/heads/mdm/release/4.0.4.Final.mvn"));
		assertTrue("release tag present in dependency module", refNames.contains("refs/tags/release/4.0.4.Final.mvn"));
		assertEquals("exactly these three refs present in dependency module", 3, refNames.size());

		// check the actual desired artifacts are inside the release module location
		String[] artifacts = depPath.list();
		Arrays.sort(artifacts);
		assertEquals("expected list of artifacts obtained",
			Arrays.asList(new String[] {
				".git",
				"netty-all-javadoc.jar",
				"netty-all-javadoc.jar.asc",
				"netty-all-sources.jar",
				"netty-all-sources.jar.asc",
				"netty-all.jar",
				"netty-all.jar.asc",
				"netty-all.pom",
				"netty-all.pom.asc"
			}),
			Arrays.asList(artifacts)
		);
		assertEquals("content of artifact is correct",
			  "-----BEGIN PGP SIGNATURE-----\n"
			+ "Version: GnuPG/MacGPG2 v2.0.18 (Darwin)\n"
			+ "Comment: GPGTools - http://gpgtools.org\n"
			+ "\n"
			+ "iEYEABECAAYFAlHuQ0kACgkQBWrKdNRgAL/BzwCZAf8WbPts4Gw32JwfBS+NnNOM\n"
			+ "NKoAn2CyvOmShPT8vsg9wIsAQQ044EPC\n"
			+ "=h1u4\n"
			+ "-----END PGP SIGNATURE-----\n",
			IOForge.readFileAsString(new File(depPath, "netty-all.jar.asc"))
		);
	}
}
