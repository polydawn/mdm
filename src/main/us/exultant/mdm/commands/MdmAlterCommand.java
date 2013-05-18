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
import java.util.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import us.exultant.ahs.util.*;
import us.exultant.mdm.*;

public class MdmAlterCommand extends MdmCommand {
	public MdmAlterCommand(Repository repo, Namespace args) {
		super(repo, args);
	}

	public MdmExitMessage call() throws IOException, ConfigInvalidException, MdmException {
		try {
			assertInRepoRoot();
		} catch (MdmExitMessage e) { return e; }

		// touch up args a tad.  tab completion in the terminal tends to suggest *almost* what you want, but with a trailing slash because it's a directory, and git doesn't like that slash.  so, we'll sand down that sharp corner a bit.
		String name = args.getString("name");
		if (name.endsWith("/")) name = name.substring(0, name.length()-1);

		// load current module state
		StoredConfig gitmodulesCfg = new FileBasedConfig(new File(repo.getWorkTree(), Constants.DOT_GIT_MODULES), repo.getFS());
		gitmodulesCfg.load();
		MdmModule module;
		try {
			module = new MdmModule(repo, name, gitmodulesCfg);
		} catch (MdmModule.IsntOne _) {
			return new MdmExitMessage(":(", "there is no mdm dependency by that name.");
		}

		// give a look at the remote path and see what versions are physically available.
		List<String> versions;
		try {
			//XXX: here the triplicate-and-then-some configuration is a tanglefuck again.  do we use the origin, or the url in the submodule config, or the url that's initialized in the parent .git/config, or the url in the .gitmodules file, or some complicated fallback pattern that covers all of them, or initialize the ones that aren't yet, or...??  Original mdm took the value from .gitmodules, which is the least likely to be uninitialized, but also not the most correct.
			if (module.getRepo() == null)
				versions = Plumbing.getVersionManifest(repo, module.getUrlHistoric());
			else
				versions = Plumbing.getVersionManifest(module.getRepo(), "origin");
		} catch (InvalidRemoteException e) {
			return new MdmExitMessage(":(", "the submodule remote origin url isn't initialized.  maybe run `mdm update` first so there's something in place before we alter?");
		} catch (TransportException e) {
			return new MdmExitMessage(":'(", "transport failed!  check that the submodule remote origin url is correct and reachable and try again?\n  (error message: "+e.getMessage()+")");
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		if (versions.size() == 0)
			return new MdmExitMessage(":(", "no releases could be found at the submodule remote origin url -- it doesn't look like releases that mdm understands are there.\ncheck the origin url in the submodule's config.  if this url worked in the past, maybe the project maintainers moved their releases repo?");

		// if a specific version name was given, we'll just go straight at it; otherwise we present options interactively from the manifest of versions the remote reported.
		String version;
		if (args.getString("version") != null) {
			version = args.getString("version");
			if (!versions.contains(version))
				return new MdmExitMessage(":(", "no version labelled "+version+" available from the provided remote url.");
		} else {
			version = Loco.promptForVersion(os, versions);
		}


		// do the submodule/dependency dancing
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, module.getHandle(), MdmConfigConstants.Module.DEPENDENCY_VERSION.toString(), version);
		try {
			// reload the MdmModule completely because it's not yet implmented intelligently enough to be able to refresh a bunch of its cached state
			module = new MdmModule(repo, module.getPath(), gitmodulesCfg);
			Plumbing.fetch(repo, module);
		} catch (MdmModule.IsntOne e) {
			throw new MajorBug(e);
		}
		gitmodulesCfg.save();	// don't do this save until after the fetch: if the fetch blows up, it's better that we don't have this mutated, because that leaves you with slightly stranger output from your next `mdm status` query.

		// commit the changes
		try {
			new Git(repo).add()
				.addFilepattern(module.getPath())
				.addFilepattern(Constants.DOT_GIT_MODULES)
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		try {
			new Git(repo).commit()
				.setOnly(module.getPath())
				.setOnly(Constants.DOT_GIT_MODULES)
				.setMessage("shifting dependency on "+name+" to version "+version+".")
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

		return new MdmExitMessage(":D", "altered dependency on "+name+" to version "+version+" successfully!");
	}
}
