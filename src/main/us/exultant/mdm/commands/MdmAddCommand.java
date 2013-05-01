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
import java.util.regex.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.submodule.*;
import org.eclipse.jgit.treewalk.filter.*;
import us.exultant.ahs.util.*;
import us.exultant.mdm.*;

public class MdmAddCommand extends MdmCommand {
	public MdmAddCommand(Repository repo, Namespace args) {
		super(repo, args);
	}

	public static final Pattern RELEASE_URL_NAME_EXTRACT = Pattern.compile("^(.*)-releases(?:.git)?$");

	public MdmExitMessage call() throws IOException, ConfigInvalidException, MdmException {
		try {
			assertInRepoRoot();
		} catch (MdmExitMessage e) { return e; }

		// git's behavior of assuming relative urls should be relative to the remote origin instead of relative to the local filesystem is almost certainly not what you want.
		String url = args.getString("url");
		if (url.startsWith("../") || url.startsWith("./"))
			os.println("hey, heads up: when you use a relative url to describe a submodule location, git assumes it's relative to the remote origin of the parent project (NOT relative to the project location on the local filesystem, which is what you might have expected).  this... works, but it's not recommended because of the potential it has to surprise.");

		// pick out the name.  if we can't find one yet, we'll prompt for it in a little bit (we try to check that something at least exists on the far side of the url before bothering with the name part).
		String name = null;
		if (args.getString("name") != null) {	// well that was easy
			name = args.getString("name");
		} else {				// look for a discernable project name in the url chunks
			String[] urlchunks = url.split("/");
			Matcher tehMatch = RELEASE_URL_NAME_EXTRACT.matcher(urlchunks[urlchunks.length-1]);
			if (tehMatch.find()) {
				name = tehMatch.group(1);
			} else {			// prompt for a name if we don't have one picked yet.
				os.print("dependency name: ");
				name = new BufferedReader(new InputStreamReader(System.in)).readLine();
				if (name == null) throw new IOException("failed to read line from stdin");
			}
		}

		File path = new File(args.getString("lib"), name);

		// check for presence of other crap here already.  (`git submodule add` will also do this, but it's a more pleasant user experience to check this before popping up a prompt for version name.)
		if (path.exists())
			return new MdmExitMessage(":'(", "there are already files at "+path+" !\nWe can't pull down a dependency there until this conflict is cleared away.");
		if (SubmoduleWalk.forIndex(repo).setFilter(PathFilter.create(path.getPath())).next())
			return new MdmExitMessage(":'(", "there is already a submodule in the git index at "+path+" !\nWe can't pull down a dependency there until this conflict is cleared away.");

		// give a look at the remote path and see what versions are physically available.
		List<String> versions;
		try {
			versions = Plumbing.getVersionManifest(repo, url);
		} catch (InvalidRemoteException e) {
			return new MdmExitMessage(":(", "the provided url doesn't parse like a url!");
		} catch (TransportException e) {
			return new MdmExitMessage(":'(", "transport failed!  check that your url is correct and reachable and try again?\n  (error message: "+e.getMessage()+")");
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		if (versions.size() == 0)
			return new MdmExitMessage(":(", "no releases could be found at the url you gave for a releases repository -- it doesn't look like releases that mdm understands are there.\nare you sure this is the releases repo?  keep in mind that the release repo and the source repo isn't the same for most projects -- check the project readme for the location of their release repo.");

		// if a specific version name was given, we'll just go straight at it; otherwise we present options interactively from the manifest of versions the remote reported.
		String version;
		if (args.getString("version") != null) {
			version = args.getString("version");
			if (!versions.contains(version))
				return new MdmExitMessage(":(", "no version labelled "+version+" available from the provided remote url.");
		} else {
			version = Loco.promptForVersion(os, versions);
		}

		// finally, let's actually do the submodule/dependency adding
		doAdd(name, path, version, url);

		// commit the changes
		try {
			new Git(repo).add()
				.addFilepattern(path.getPath())
				.addFilepattern(Constants.DOT_GIT_MODULES)
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		try {
			new Git(repo).commit()
				.setOnly(path.getPath())
				.setOnly(Constants.DOT_GIT_MODULES)
				.setMessage("adding dependency on "+name+" at "+version+".")
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

		return new MdmExitMessage(":D", "added dependency on "+name+"-"+version+" successfully!");
	}

	private void doAdd(String name, File path, String version, String url) throws IOException, MdmException, ConfigInvalidException {
		// write gitmodule config for the new submodule
		StoredConfig gitmodulesCfg = new FileBasedConfig(new File(repo.getWorkTree(), Constants.DOT_GIT_MODULES), repo.getFS());
		gitmodulesCfg.load();
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), ConfigConstants.CONFIG_KEY_PATH, path.getPath());
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), ConfigConstants.CONFIG_KEY_URL, url);
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), MdmConfigConstants.Module.MODULE_TYPE.toString(), MdmModuleType.DEPENDENCY.toString());
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), MdmConfigConstants.Module.DEPENDENCY_VERSION.toString(), version);
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), ConfigConstants.CONFIG_KEY_UPDATE, "none"); // since almost all git commands by default will pull down waaaay too much data if they operate naively on our dependencies, we tell them to ignore all dependencies by default.  And of course, commands like `git pull` just steamroll right ahead and ignore this anyway, so those require even more drastic counters.
		gitmodulesCfg.save();

		// fetch the release data to our local submodule repo
		try {
			MdmModule module = new MdmModule(repo, path.getPath(), gitmodulesCfg);
			Plumbing.fetch(repo, module);
		} catch (MdmModule.IsntOne e) {
			throw new MajorBug(e);
		}
	}
}
