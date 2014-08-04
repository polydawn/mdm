package net.polydawn.mdm.jgit;

import static org.junit.Assert.*;
import net.polydawn.mdm.fixture.*;
import net.polydawn.mdm.test.*;
import org.eclipse.jgit.dircache.*;
import org.eclipse.jgit.errors.*;
import org.junit.*;
import org.junit.runner.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class DircacheLockSanityTest extends TestCaseUsingRepository {
	@Test
	public void acquiring_dircache_lock_twice_fails() throws Exception {
		Fixture project = new ProjectAlpha("projectAlpha");

		DirCache dc1 = project.getRepo().lockDirCache();

		try {
			DirCache dc2 = project.getRepo().lockDirCache();
			fail("expected lock fail exception");
		} catch (LockFailedException expected) {
			/* yayy */
		}
	}
}
