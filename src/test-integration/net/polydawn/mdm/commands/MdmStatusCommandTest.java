package net.polydawn.mdm.commands;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.junit.*;
import org.junit.rules.*;
import org.junit.runner.*;
import us.exultant.ahs.util.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class MdmStatusCommandTest extends TestCaseUsingRepository {
	@Rule public ExpectedException exception = ExpectedException.none();

	@Test
	public void testDescribeEmptyRepo() throws Exception {
		Fixture project = new ProjectAlpha("projectRepo");

		WithCwd wd = new WithCwd(project.getRepo().getWorkTree());
		{
			PrintGatherer pg = new PrintGatherer();
			MdmStatusCommand cmd = new MdmStatusCommand(project.getRepo(), pg.printer());
			cmd.validate();
			assertJoy(cmd.call());
			assertEquals(" --- no managed dependencies --- \n", pg.toString());
		}
		wd.close();
	}

	@Test
	public void testDescribeNormalRepo() throws Exception {
		Fixture project = new ProjectDelta("projectRepo");

		WithCwd wd = new WithCwd(project.getRepo().getWorkTree());
		{
			PrintGatherer pg = new PrintGatherer();
			MdmStatusCommand cmd = new MdmStatusCommand(project.getRepo(), pg.printer());
			cmd.validate();
			assertJoy(cmd.call());
			assertEquals(Strings.join(Arrays.asList(
				"dependency:        	 version:",
				"-----------        	 --------",
				"  lib/alpha        	   v1",
				"  lib/beta         	   v1.0"
				), "\n") + "\n",
				pg.toString());
		}
		wd.close();
	}

	@Test
	public void testDescribeFreshClonedRepo() throws Exception {
		Fixture upstream = new ProjectDelta("upstreamRepo");

		new File("cloneRepo").getCanonicalFile().mkdir();
		Repository clone = Git.cloneRepository()
			.setURI(upstream.getRepo().getWorkTree().getPath())
			.setDirectory(new File("cloneRepo").getCanonicalFile())
			.call().getRepository();

		WithCwd wd = new WithCwd(clone.getWorkTree());
		{
			PrintGatherer pg = new PrintGatherer();
			MdmStatusCommand cmd = new MdmStatusCommand(clone, pg.printer());
			cmd.validate();
			assertJoy(cmd.call());
			assertEquals(Strings.join(Arrays.asList(
				"dependency:        	 version:",
				"-----------        	 --------",
				"  lib/alpha        	   -- uninitialized --",
				"  lib/beta         	   -- uninitialized --"
				), "\n") + "\n",
				pg.toString());
		}
		wd.close();
	}

	@Test
	public void testDescribeDesyncedDependency() throws Exception {
		ProjectDelta project = new ProjectDelta("projectRepo");
		// hilariously, remake a beta releases fixture, because we through it away already.
		// consider it a charming test of... uh.  nothing, actually; this doesn't really exercise release hash determinism.
		Fixture betaReleases = new ProjectBetaReleases("beta-releases");

		WithCwd wd = new WithCwd(project.getRepo().getWorkTree());
		{
			// add another commit with a new version of the beta dep
			MdmAlterCommand cmd = new MdmAlterCommand(project.getRepo(), new Namespace(new HashMap<String,Object>() {
				{
					put("name", "lib/beta");
					put("version", "v1.1");
				}
			}));
			cmd.validate();
			cmd.call();
			// checkout back up one
			new Git(project.getRepo()).checkout()
				.setName(project.getRepo().resolve("HEAD^").getName())
				.call();
		}
		{
			// now try status: it should see the newer version in place but the older version spec'd/
			PrintGatherer pg = new PrintGatherer();
			MdmStatusCommand cmd = new MdmStatusCommand(project.getRepo(), pg.printer());
			cmd.validate();
			assertJoy(cmd.call());
			assertEquals(Strings.join(Arrays.asList(
				"dependency:        	 version:",
				"-----------        	 --------",
				"  lib/alpha        	   v1",
				"  lib/beta         	   v1.1",
				"                   	     !! intended version is v1.0, run `mdm update` to get it",
				"                   	     !! commit currently checked out does not match hash in parent project"
				), "\n") + "\n",
				pg.toString());
		}
		wd.close();
	}

	@Test
	public void testDescribeNamedDep() throws Exception {
		Fixture project = new ProjectDelta("projectRepo");

		WithCwd wd = new WithCwd(project.getRepo().getWorkTree());
		{
			PrintGatherer pg = new PrintGatherer();
			MdmStatusCommand cmd = new MdmStatusCommand(project.getRepo(), pg.printer());
			cmd.depName = "lib/alpha";
			cmd.validate();
			assertJoy(cmd.call());
			assertEquals(Strings.join(Arrays.asList(
				"dependency:        	 version:",
				"-----------        	 --------",
				"  lib/alpha        	   v1"
				// there's another here, but in this mode it goes unreported.
				), "\n") + "\n",
				pg.toString());
		}
		wd.close();
	}

	@Test
	public void testDescribeNamedDepNotThere() throws Exception {
		Fixture project = new ProjectAlpha("projectRepo");

		WithCwd wd = new WithCwd(project.getRepo().getWorkTree());
		{
			PrintGatherer pg = new PrintGatherer();
			MdmStatusCommand cmd = new MdmStatusCommand(project.getRepo(), pg.printer());
			cmd.depName = "lib/alpha";
			cmd.validate();
			assertJoy(cmd.call()); // REVIEW: maybe this should actually be an error?
			assertEquals(" --- no managed dependencies --- \n", pg.toString());
		}
		wd.close();
	}

	@Test
	public void testDescribeNamedDepWithShortFormate() throws Exception {
		Fixture project = new ProjectDelta("projectRepo");

		WithCwd wd = new WithCwd(project.getRepo().getWorkTree());
		{
			PrintGatherer pg = new PrintGatherer();
			MdmStatusCommand cmd = new MdmStatusCommand(project.getRepo(), pg.printer());
			cmd.depName = "lib/alpha";
			cmd.formatName = "versionCheckedOut";
			cmd.validate();
			assertJoy(cmd.call());
			assertEquals("v1\n", pg.toString());
		}
		wd.close();
	}
}



// Abandon all hope, ye trapped souls of java;
// Come to Golang, where bytes flow freely!
class PrintGatherer {
	// Hilariously, it would have been eye-tearing trouble to have `PrintGatherer extends PrintStream`,
	//  because in attempting to hand up the `baos` to the super constructor, you get mired in
	//   on of those completely fucking ridiculous "cannot refer to instance field while explicitly invoking constructor" shitwomps.
	private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

	public PrintStream printer() {
		return new PrintStream(baos);
	}

	public String toString() {
		return baos.toString();
	}
}
