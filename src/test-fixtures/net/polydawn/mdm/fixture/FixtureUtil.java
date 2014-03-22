package net.polydawn.mdm.fixture;

import java.io.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.commands.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;

public class FixtureUtil {
	public static Repository setUpPlainRepo(String path) throws IOException {
		Repository repo = new RepositoryBuilder()
			.setWorkTree(new File(path).getCanonicalFile())
			.build();
		repo.create(false);
		return repo;
	}

	public static Repository setUpReleaseRepo(String path) throws IOException, ConfigInvalidException, MdmException {
		MdmReleaseInitCommand cmd = new MdmReleaseInitCommand(null);
		cmd.path = new File(path).getCanonicalPath();
		cmd.validate();
		cmd.call();
		Repository releaserepo = new RepositoryBuilder()
			.setWorkTree(new File(path).getCanonicalFile())
			.build();
		return releaserepo;
	}
}
