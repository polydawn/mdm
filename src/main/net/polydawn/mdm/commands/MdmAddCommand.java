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

package net.polydawn.mdm.commands;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import net.polydawn.mdm.*;
import net.polydawn.mdm.errors.*;
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
import static net.polydawn.mdm.Loco.*;

public class MdmAddCommand extends MdmCommand {
	public MdmAddCommand(Repository repo) {
		super(repo);
	}

	public static final Pattern RELEASE_URL_NAME_EXTRACT = Pattern.compile("^(.*)-releases(?:.git)?$");

	public void parse(Namespace args) {
		// pick url.  (this one's pretty cut and dry.)
		url = args.getString("url");

		// pick out the name.  or at least try -- if we can't find one, we'll prompt for it later.
		if (args.getString("name") != null) {
			name = args.getString("name");
		} else {
			// look for a discernable project name in the url chunks
			String[] urlchunks = url.split("/");
			Matcher tehMatch = RELEASE_URL_NAME_EXTRACT.matcher(urlchunks[urlchunks.length-1]);
			if (tehMatch.find()) {
				name = tehMatch.group(1);
			} else {
				// we'll have prompt for a name later if we don't have one picked yet.
				name = null;
			}
		}

		// pick the library path.
		pathLibs = new File(args.getString("lib"));

		// pick out the version requested.
		// may be null, as with local name, we'll prompt for it later.
		version = args.getString("version");
	}

	public void validate() throws MdmExitMessage {

	}

	String url;

	String name;

	/** Path to the library folder.  The submodule target location will be "{@link #pathLibs}/{@link #name}".
	 * Can *not* be an absolute path; must be relative to the cwd (which in turn must be the repo root). */
	File pathLibs;

	String version;

	public MdmExitMessage call() throws IOException, ConfigInvalidException, MdmException {
		assertInRepoRoot();

		// git's behavior of assuming relative urls should be relative to the remote origin instead of relative to the local filesystem is almost certainly not what you want.
		if (url.startsWith("../") || url.startsWith("./"))
			os.println("hey, heads up: when you use a relative url to describe a submodule location, git assumes it's relative to the remote origin of the parent project (NOT relative to the project location on the local filesystem, which is what you might have expected).  this... works, but it's not recommended because of the potential it has to surprise.");

		// give a look at the remote url and see what versions are physically available.
		List<String> versions = fetchVersions();
		if (versions.size() == 0)
			throw new MdmExitMessage(":(", "no releases could be found at the url you gave for a releases repository -- it doesn't look like releases that mdm understands are there.\nare you sure this is the releases repo?  keep in mind that the release repo and the source repo isn't the same for most projects -- check the project readme for the location of their release repo.");

		// if we didn't get a name argument yet, prompt for one.
		// note that this is *after* we tried to check that something at least exists on the far side of the url, in order to minimize bother.
		if (name == null)
			name = inputPrompt(os, "dependency name: ");

		File path = new File(pathLibs, name);

		// check for presence of other crap here already.  (`git submodule add` will also do this, but it's a more pleasant user experience to check this before popping up a prompt for version name.)
		if (path.exists())
			throw new MdmExitMessage(":'(", "there are already files at "+path+" !\nWe can't pull down a dependency there until this conflict is cleared away.");
		if (SubmoduleWalk.forIndex(repo).setFilter(PathFilter.create(path.getPath())).next())
			throw new MdmExitMessage(":'(", "there is already a submodule in the git index at "+path+" !\nWe can't pull down a dependency there until this conflict is cleared away.");

		// if a specific version name was given, we'll just go straight at it; otherwise we present options interactively from the manifest of versions the remote reported.
		if (version == null)
			version = Loco.promptForVersion(os, versions);

		// check yourself before you wreck yourself
		if (!versions.contains(version))
			throw new MdmExitMessage(":(", "no version labelled "+version+" available from the provided remote url.");

		// finally, let's actually do the submodule/dependency adding
		Config config = doSubmoduleConfig(path);
		doSubmoduleFetch(path, config);

		// commit the changes
		doGitStage(path);
		doGitCommit(path);

		return new MdmExitMessage(":D", "added dependency on "+name+"-"+version+" successfully!");
	}

	List<String> fetchVersions() throws MdmExitMessage {
		try {
			return Plumbing.getVersionManifest(repo, url);
		} catch (InvalidRemoteException e) {
			throw new MdmExitMessage(":(", "the provided url doesn't parse like a url!");
		} catch (TransportException e) {
			throw new MdmExitMessage(":'(", "transport failed!  check that your url is correct and reachable and try again?\n  (error message: "+e.getMessage()+")");
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
	}

	Config doSubmoduleConfig(File path) throws ConfigInvalidException, IOException {
		// write gitmodule config for the new submodule
		StoredConfig gitmodulesCfg = new FileBasedConfig(new File(repo.getWorkTree(), Constants.DOT_GIT_MODULES), repo.getFS());
		gitmodulesCfg.load();
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), ConfigConstants.CONFIG_KEY_PATH, path.getPath());
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), ConfigConstants.CONFIG_KEY_URL, url);
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), MdmConfigConstants.Module.MODULE_TYPE.toString(), MdmModuleType.DEPENDENCY.toString());
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), MdmConfigConstants.Module.DEPENDENCY_VERSION.toString(), version);
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path.getPath(), ConfigConstants.CONFIG_KEY_UPDATE, "none"); // since almost all git commands by default will pull down waaaay too much data if they operate naively on our dependencies, we tell them to ignore all dependencies by default.  And of course, commands like `git pull` just steamroll right ahead and ignore this anyway, so those require even more drastic counters.
		gitmodulesCfg.save();
		return gitmodulesCfg;
	}

	void doSubmoduleFetch(File path, Config gitmodulesCfg) throws MdmRepositoryIOException, MdmRepositoryStateException, MdmException {
		// fetch the release data to our local submodule repo
		MdmModuleDependency module = MdmModuleDependency.load(repo, path.getPath(), gitmodulesCfg);
		Plumbing.fetch(repo, module);
	}

	void doGitStage(File path) {
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
	}

	void doGitCommit(File path) throws MdmRepositoryStateException {
		String currentAction = "commit a link to the new dependency repo into the project repo";
		try {
			new Git(repo).commit()
				.setOnly(path.getPath())
				.setOnly(Constants.DOT_GIT_MODULES)
				.setMessage("adding dependency on "+name+" at "+version+".")
				.call();
		} catch (NoHeadException e) {
			throw new MdmRepositoryStateException(currentAction, repo.getWorkTree().toString(), e);
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
	}
}
