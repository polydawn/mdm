/*
 * Copyright 2012 - 2014 Eric Myhre <http://exultant.us>
 *
 * This file is part of mdm <https://github.com/heavenlyhash/mdm/>.
 *
 * mdm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package net.polydawn.mdm;

import java.io.*;
import java.util.*;
import net.polydawn.mdm.errors.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.submodule.*;
import us.exultant.ahs.util.*;

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
		// generator.loadModulesConfig() might cause this to do a more efficient walk on large repos by just taking the gitmodules file's word for it?  not sure; read that source more and also ask if trusting that file would create other consistency issues.
		while (generator.next()) {
			try {
				// get the handle.  which we presume to be rather like the path, but git config always uses forward slashes.
				// (the MdmModule constructor will also enforce this, but here we have to walk config ourselves before we get that far.)
				String handle = (File.separatorChar != '/') ? generator.getPath().replace(File.separatorChar, '/') : generator.getPath();

				// get submodule.[handle].mdm config value from gitmodules config file
				String type_configured_string = gitmodulesCfg.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, handle, MdmConfigConstants.Module.MODULE_TYPE.toString());
				MdmModuleType type_configured = MdmModuleType.fromString(type_configured_string);

				// if the submodule.[handle].mdm config value was unrecognized or missing, ignore; it's not ours.
				if (type_configured == null)
					continue;

				// load whichever type of mdm module it is
				switch (type_configured) {
					case DEPENDENCY:
						MdmModuleDependency modDep = MdmModuleDependency.load(repo, generator, gitmodulesCfg);
						dependencyModules.put(modDep.getHandle(), modDep);
						allModules.put(modDep.getHandle(), modDep);
						break;
					case RELEASES:
						MdmModuleRelease modRel = MdmModuleRelease.load(repo, generator, gitmodulesCfg);
						releasesModules.put(modRel.getHandle(), modRel);
						allModules.put(modRel.getHandle(), modRel);
						break;
				}
			} catch (MdmModuleTypeException e) {
				throw new MajorBug(e);
			} catch (MdmRepositoryNonexistant e) {
				throw e;
			} catch (MdmRepositoryIOException e) {
				throw e;
			}
		}
	}

	/** Must load and keep this reference ourself rather than let {@link SubmoduleWalk} do it for us because we intend to read unusual values and mutate it in unusual ways. */
	private final StoredConfig gitmodulesCfg;

	private final Map<String,MdmModule> allModules = new TreeMap<String,MdmModule>();
	private final Map<String,MdmModuleDependency> dependencyModules = new TreeMap<String,MdmModuleDependency>();
	private final Map<String,MdmModuleRelease> releasesModules = new TreeMap<String,MdmModuleRelease>();

	public Map<String,MdmModule> getAllModules() {
		return this.allModules;
	}

	public Map<String,MdmModuleDependency> getDependencyModules() {
		return this.dependencyModules;
	}

	public Map<String,MdmModuleRelease> getReleasesModules() {
		return this.releasesModules;
	}
}
