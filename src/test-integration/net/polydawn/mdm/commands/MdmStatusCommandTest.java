package net.polydawn.mdm.commands;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
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
			assertEquals(pg.toString(), " --- no managed dependencies --- \n");
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
			assertEquals(pg.toString(), Strings.join(Arrays.asList(
				"dependency:        	 version:",
				"-----------        	 --------",
				"  lib/alpha        	   v1",
				"  lib/beta         	   v1.0"
				), "\n") + "\n");
		}
		wd.close();
	}

	@Test
	public void testDescribeFreshClonedRepo() throws Exception {}

	@Test
	public void testDescribeDesyncedDependency() throws Exception {}
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
