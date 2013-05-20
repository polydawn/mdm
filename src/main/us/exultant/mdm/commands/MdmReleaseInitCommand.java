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

import static us.exultant.mdm.Loco.inputPrompt;
import java.io.*;
import java.net.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.submodule.*;
import org.eclipse.jgit.treewalk.filter.*;
import us.exultant.ahs.iob.*;
import us.exultant.ahs.util.*;
import us.exultant.mdm.*;
import us.exultant.mdm.MdmModule.IsntOne;

public class MdmReleaseInitCommand extends MdmCommand {
	public MdmReleaseInitCommand(Repository repo, Namespace args) {
		super(repo, args);
	}

	public void parse(Namespace args) {
		name = args.getString("name");
		path = args.getString("repo");

		// check if we're in a repo root.  we'll suggest slightly different default values if we are. take it's dirname as a default
		asSubmodule = isInRepoRoot();

		// pick out the name, if not given.
		if (name == null) {
			String prompt = "what's the name of this project";
			String[] pwdchunks = System.getProperty("user.dir").split("/");
			String nameSuggest = pwdchunks[pwdchunks.length-1];
			if (args.getBoolean("use_defaults"))
				name = nameSuggest;
			else if (asSubmodule)
				prompt += " [default: " + nameSuggest + "] ";
			else
				nameSuggest = null;

			while (name == null) {
				name = inputPrompt(os, prompt+"?");
				if (name.equals("") && nameSuggest != null) name = nameSuggest;
				name = name.trim();
				if (name.equals("")) name = null;
			}
		}
	}

	public void validate() {
		// pick out path, if not given.
		if (path == null)
			if (asSubmodule)	// if we are initializating the releases repo as a submodule, the default location to put that submodule is "./releases"
				path = "releases";
			else			// if we're not a submodule, then the default is to make use of the current directory.
				path = ".";

		// normalize the path if necessary.  (jgit commit trips over relative paths.  and it's pretty weird for a submodule handle, too.)
		if (asSubmodule && path.startsWith("./")) path = path.substring(2);
	}

	String name;
	String path;
	boolean asSubmodule;

	public MdmExitMessage call() throws IOException, ConfigInvalidException, MdmException {
		parse(args);
		validate();

		// is the releases area free of clutter?
		if (asSubmodule && SubmoduleWalk.forIndex(repo).setFilter(PathFilter.create(path)).next())
			return new MdmExitMessage(":I", "there's already a releases module!  No changes made.");
		if (!path.equals(".") && new File(path).exists())
			return new MdmExitMessage(":(", "something already exists at the location we want to initialize the releases repo.  clear it out and try again.");

		// okay!  make the new releases-repo.  put a first commit it in to avoid awkwardness.
		Repository releaserepo;
		try {
			RepositoryBuilder builder = new RepositoryBuilder();
			builder.setWorkTree(new File(path));
			releaserepo = builder.build();
			releaserepo.create(false);
		} catch (IOException e) {
			throw new MdmException("failed to write data while trying to create release repo at "+path, e);
		}
		Git git = new Git(releaserepo);
		IOForge.saveFile("This is the releases repo for "+name+".\n", new File(path+"/README"));
		try {
			git.add()
				.addFilepattern("README")
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		try {
			git.commit()
				.setOnly("README")
				.setMessage("initialize releases repo for "+name+".")
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

		// label this root commit in order to declare this repo as a valid mdm releases repo.
		// note: considered changing this to a tag instead of a branch, but you can't actually do that.  there's some crap with the fetching where you needed an essentially empty branch, and we need this init branch of that.  init is clearly more appropriate for that than infix, since infix doesn't necessarily even exist.
		try {
			git.branchCreate()
				.setName("mdm/init")
				.call();
		} catch (RefAlreadyExistsException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);	// i just made this repo, so this shouldn't be a problem outside of TOCTOU madness
		} catch (RefNotFoundException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (InvalidRefNameException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		//if (args.infix):
		//	git.checkout("-b", "mdm/infix");

		// if we're not a submodule, we're now done here, otherwise, the rest of the work revolves around the parent repo.
		if (!asSubmodule)
			return new MdmExitMessage(":D", "releases repo initialized");

		// ask for remote url.
		String remotePublicUrl = args.getString("remote_url");
		if (remotePublicUrl == null)
			if (args.getBoolean("use_defaults")) {
				String parentRemote = repo.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", ConfigConstants.CONFIG_KEY_URL);
				if (parentRemote == null) parentRemote = System.getProperty("user.dir");
				remotePublicUrl = "../"+name+"-releases.git";
				try {
					remotePublicUrl = new URI(parentRemote+"/").resolve(remotePublicUrl).toString();
				} catch (URISyntaxException e) {}
			} else
				remotePublicUrl = inputPrompt(os,
						"Configure a remote url where this repo will be accessible?\n"
						+"This will be committed to the project's .gitmodules file, and so should be a publicly accessible url.\n"
						+"remote url: "
				);
		remotePublicUrl = remotePublicUrl.trim();

		// and another.
		String remotePublishUrl = args.getString("remote_publish_url");
		if (remotePublishUrl == null)
			if (args.getBoolean("use_defaults"))
				remotePublishUrl = remotePublicUrl;
			else
				remotePublishUrl = inputPrompt(os,
						"Configure a remote url you'll use to push this repo when making releases?\n"
						+"This will not be committed to the project; just set in your local config.\n"
						+"remote url [leave blank to use the same public url]: "
				);
		remotePublishUrl = remotePublishUrl.trim();
		if (remotePublishUrl.equals("")) remotePublishUrl = remotePublicUrl;

		// add the new releases-repo as a submodule to the project repo.

		// write gitmodule config for the new submodule
		StoredConfig gitmodulesCfg = new FileBasedConfig(new File(repo.getWorkTree(), Constants.DOT_GIT_MODULES), repo.getFS());
		gitmodulesCfg.load();
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path, ConfigConstants.CONFIG_KEY_PATH, path);
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path, ConfigConstants.CONFIG_KEY_URL, remotePublicUrl);
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path, MdmConfigConstants.Module.MODULE_TYPE.toString(), MdmModuleType.RELEASES.toString());
		gitmodulesCfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path, ConfigConstants.CONFIG_KEY_UPDATE, "none");
		gitmodulesCfg.save();

		// initialize local parent repo config for the submodule
		MdmModule module;
		try {
			module = new MdmModule(repo, path, gitmodulesCfg);
		} catch (IsntOne e) {
			throw new MajorBug(e);
		}
		Plumbing.initLocalConfig(repo, module);
		repo.getConfig().save();

		// initialize the submodule remote config
		module.getRepo().getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", ConfigConstants.CONFIG_KEY_URL, remotePublishUrl);
		module.getRepo().getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
		module.getRepo().getConfig().save();

		// commit the changes
		try {
			new Git(repo).add()
				.addFilepattern("releases")
				.addFilepattern(Constants.DOT_GIT_MODULES)
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		try {
			new Git(repo).commit()
				.setOnly("releases/")
				.setOnly(Constants.DOT_GIT_MODULES)
				.setMessage("initialize releases repo for "+name+".")
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

		return new MdmExitMessage(":D", "releases repo and submodule initialized");
	}
}
