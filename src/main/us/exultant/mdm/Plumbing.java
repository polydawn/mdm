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

package us.exultant.mdm;

import java.io.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.*;
import us.exultant.ahs.util.*;

public class Plumbing {
	public static void fetch(Repository repo, MdmModule module) throws MdmException {
		switch (module.getStatus().getType()) {
			case INITIALIZED:
				return;
			case MISSING:
				throw new MajorBug();
			case UNINITIALIZED:
				if (module.getRepo() == null)
					try {
						RepositoryBuilder builder = new RepositoryBuilder();
						builder.setWorkTree(new File(repo.getWorkTree()+"/"+module.getPath()));
						module.repo = builder.build();
						module.repo.create(false);
					} catch (IOException e) {
						throw new MdmException("failed to write data to submodule "+module.getHandle(), e);
					}

				try {
					initLocalConfig(repo, module);
					repo.getConfig().save();
				} catch (IOException e) {
					throw new MdmException("failed to save changes to local git configuration file", e);
				}
				try {
					setMdmRemote(module);
					module.getRepo().getConfig().save();
				} catch (IOException e) {
					throw new MdmException("failed to save changes to submodule git configuration file for "+module.getHandle(), e);
				}
			case REV_CHECKED_OUT:
				final String versionBranchName = "mdm/release/"+module.getVersionName();

				/* Fetch only the branch labelled with the version requested. */
				try {
					RefSpec ref = new RefSpec()
						.setForceUpdate(true)
						.setSource("refs/heads/"+versionBranchName)
						.setDestination("refs/heads/"+versionBranchName);
					new Git(module.getRepo()).fetch()
						.setRemote("origin")
						.setRefSpecs(ref)
						.call();
				} catch (InvalidRemoteException e) {
					throw new MdmException("could not find remote repository for module "+module.getHandle(), e);
				} catch (TransportException e) {
					throw new MdmException("transport failed!  check your connectivity and try again?", e);
				} catch (GitAPIException e) {
					throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
				}

				/* Drop the files into the working tree. */
				try {
					new Git(module.getRepo()).checkout()
						.setName(versionBranchName)
						.setForce(true)
						.call();
				} catch (RefAlreadyExistsException e) {
					/* I'm not creating a new branch, so this exception wouldn't even make sense. */
					throw new MajorBug(e);
				} catch (RefNotFoundException e) {
					/* I just got this branch, so we shouldn't have a problem here. */
					throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
				} catch (InvalidRefNameException e) {
					/* I just got this branch, so we shouldn't have a problem here. */
					throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
				} catch (CheckoutConflictException e) {
					/* I'm using force mode, so this shouldnt happen. */
					throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
				} catch (GitAPIException e) {
					throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
				}
		}
	}

	/**
	 * Similar to calling `git submodule init [module]`.  Also updates the MdmModule cache of values.
	 * @return true if repo.getConfig() has been modified and should be saved.
	 */
	public static boolean initLocalConfig(Repository repo, MdmModule module) {
		// Ignore entry if URL is already present in config file
		if (module.getUrlLocal() != null) return false;

		// Copy 'url' and 'update' fields to local repo config
		module.urlLocal = module.getUrlHistoric();
		if (isGithubHttpUrl(module.urlLocal) && !module.urlLocal.endsWith(".git")) {
			// Github 404's unknown user agents only from some urls, so in order to have jgit accept the same urls that cgit will accept, we rewrite to the url that always responds correctly.
			module.urlLocal += ".git";
		}
		//FIXME: this isn't always happening when you might wish it is... you think you're going to reset this by rm-rf'ing the submodule dir?  nooope, that url is saved in the parent .git/config as well as the submodule/.git/config.  and if nothing else, the condition above would *fail* to work as intended when trying to use mdmj on a repo that had already been using the old python mdm.
		repo.getConfig().setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, module.getPath(), ConfigConstants.CONFIG_KEY_URL, module.getUrlLocal());
		repo.getConfig().setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, module.getPath(), ConfigConstants.CONFIG_KEY_UPDATE, "none");
		return true;
	}

	/**
	 * Check if a url is an http(s) url for github. Github's http API will 404 user
	 * agents it doesn't recognize as git, and they don't recognize jgit.
	 */
	public static boolean isGithubHttpUrl(String url) {
		if (url.startsWith("http://github.com")) return true;
		if (url.startsWith("http://www.github.com")) return true;
		if (url.startsWith("https://github.com")) return true;
		if (url.startsWith("https://www.github.com")) return true;
		return false;
	}

	/**
	 * Equivalent of calling `git remote add -t mdm/init origin [url]` &mdash; set up
	 * the remote origin and use the "-t" option here to limit what can be
	 * automatically dragged down from the network by a `git pull` (this is necessary
	 * because even pulling in the parent project will recurse to fetching submodule
	 * content as well).
	 */
	public static void setMdmRemote(MdmModule module) {
		module.getRepo().getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", ConfigConstants.CONFIG_KEY_URL, module.getUrlLocal());
		module.getRepo().getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", "fetch", "+refs/heads/mdm/init:refs/remotes/origin/mdm/init");
	}
}
