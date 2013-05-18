/*
 * Copyright 2012, 2013 Eric Myhre <http://exultant.us>
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

package us.exultant.mdm.commands;

import java.io.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import us.exultant.ahs.iob.*;
import us.exultant.ahs.util.*;
import us.exultant.mdm.*;

public class MdmRemoveCommand extends MdmCommand {
	public MdmRemoveCommand(Repository repo, Namespace args) {
		super(repo, args);
	}

	public MdmExitMessage call() throws IOException, ConfigInvalidException, MdmException {
		try {
			assertInRepoRoot();
		} catch (MdmExitMessage e) { return e; }

		// touch up args a tad.  tab completion in the terminal tends to suggest *almost* what you want, but with a trailing slash because it's a directory, and git doesn't like that slash.  so, we'll sand down that sharp corner a bit.
		String name = args.getString("name");
		if (name.endsWith("/")) name = name.substring(0, name.length()-1);

		// load up config
		StoredConfig gitmodulesCfg = new FileBasedConfig(new File(repo.getWorkTree(), Constants.DOT_GIT_MODULES), repo.getFS());
		gitmodulesCfg.load();

		// if there's no module there, we haven't got much to do
		try {
			MdmModule module = new MdmModule(repo, name, gitmodulesCfg);
		} catch (MdmModule.IsntOne _) {
			return new MdmExitMessage(":I", "there is no mdm dependency by that name.");
		}

		// stage the remove and blow away the repo dirs
		try {
			new Git(repo).rm()
				.setCached(true)
				.addFilepattern(name)
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		IOForge.delete(new File(repo.getWorkTree(), name));
		IOForge.delete(new File(repo.getDirectory(), "modules/"+name));	// if this is one of the newer version of git (specifically, 1.7.8 or newer) that stores the submodule's data in the parent projects .git dir, clear that out forcefully as well.

		// blow away gitmodule config section
		gitmodulesCfg.unsetSection(ConfigConstants.CONFIG_SUBMODULE_SECTION, name);
		gitmodulesCfg.save();

		// commit the changes
		try {
			new Git(repo).add()
				.addFilepattern(name)
				.addFilepattern(Constants.DOT_GIT_MODULES)
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		try {
			new Git(repo).commit()
				.setOnly(name)
				.setOnly(Constants.DOT_GIT_MODULES)
				.setMessage("removing dependency on "+name+".")
				.call();
		} catch (NoHeadException e) {
			throw new MdmException("your repository is in an invalid state!", e);
		} catch (NoMessageException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (UnmergedPathsException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (ConcurrentRefUpdateException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (WrongRepositoryStateException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}

		// clear out local git config
		StoredConfig localConfig = repo.getConfig();
		localConfig.unsetSection(ConfigConstants.CONFIG_SUBMODULE_SECTION, name);
		localConfig.save();

		return new MdmExitMessage(":D", "removed dependency on "+name+"!");
	}
}
