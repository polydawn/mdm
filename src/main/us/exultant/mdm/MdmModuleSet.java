package us.exultant.mdm;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.submodule.*;

public class MdmModuleSet {
	public MdmModuleSet(Repository repo) throws IOException, ConfigInvalidException {
		gitmodulesCfg = new FileBasedConfig(new File(repo.getWorkTree(), Constants.DOT_GIT_MODULES), repo.getFS());
		gitmodulesCfg.load();

		/*
		 * To maximize similarity with how the `git submodule` command behaves, we treat the SubmoduleWalk as canonical and the content of the .gitmodules file as tertiary.
		 * However, that may change.  There's a lot about MdmModule that doesn't work without a .gitmodules entry anyway, so if it's faster to start from that list, we might as well.
		 */

		SubmoduleWalk mw = new SubmoduleWalk(repo);
		mw.setModulesConfig(gitmodulesCfg);

		SubmoduleWalk generator = SubmoduleWalk.forIndex(repo);
		while (generator.next()) {
			try {
				MdmModule status = new MdmModule(generator, gitmodulesCfg);
				allModules.put(status.getHandle(), status);
				switch (status.getType()) {
					case DEPENDENCY:
						dependencyModules.put(status.getHandle(), status);
						break;
					case RELEASES:
						releasesModules.put(status.getHandle(), status);
						break;
				}
			} catch (MdmModule.IsntOne e) {}
		}
	}

	/** Must load and keep this reference ourself rather than let {@link SubmoduleWalk} do it for us because we intend to read unusual values and mutate it in unusual ways. */
	private final StoredConfig gitmodulesCfg;

	private final Map<String,MdmModule> allModules = new HashMap<>();
	private final Map<String,MdmModule> dependencyModules = new HashMap<>();
	private final Map<String,MdmModule> releasesModules = new HashMap<>();

	public Map<String,MdmModule> getAllModules() {
		return this.allModules;
	}

	public Map<String,MdmModule> getDependencyModules() {
		return this.dependencyModules;
	}

	public Map<String,MdmModule> getReleasesModules() {
		return this.releasesModules;
	}
}
