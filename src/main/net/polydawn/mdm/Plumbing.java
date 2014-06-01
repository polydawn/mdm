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
import java.net.*;
import java.text.*;
import java.util.*;
import net.polydawn.mdm.errors.*;
import net.polydawn.mdm.util.*;
import org.apache.commons.lang.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ResetCommand.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.submodule.*;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.treewalk.filter.*;
import us.exultant.ahs.iob.*;
import us.exultant.ahs.util.*;

public class Plumbing {
	// this method is getting to be quite a misnomer, it enforces a lot more state than just fetching
	public static boolean fetch(Repository repo, MdmModuleDependency module) throws ConfigInvalidException, MdmRepositoryIOException, MdmRepositoryStateException, MdmException, IOException {
		switch (module.getStatus().getType()) {
			case MISSING:
				throw new MajorBug();
			case UNINITIALIZED:
				if (module.getRepo() == null)
					try {
						RepositoryBuilder builder = new RepositoryBuilder();
						builder.setWorkTree(new File(repo.getWorkTree()+"/"+module.getPath()));
						builder.setGitDir(new File(repo.getDirectory()+"/modules/"+module.getPath()));
						module.repo = builder.build();

						// we actually *might* not have to make the repo from zero.
						// this getRepo gets its effective data from SubmoduleWalk.getSubmoduleRepository...
						// which does its job by looking in the working tree of the parent repo.
						// meaning if it finds nothing, it certainly won't find any gitdir indirections.
						// so, even if this is null, we might well have a gitdir cached that we still have to go find.
						final FileBasedConfig cfg = (FileBasedConfig) module.repo.getConfig();
						if (!cfg.getFile().exists()) {	// though seemly messy, this is the same question the jgit create() function asks, and it's not exposed to us, so.
							module.repo.create(false);
						} else {
							// do something crazy, because... i think the user's expectation after blowing away their submodule working tree is likely wanting a clean state of index and such here
							try {
								new Git(module.getRepo()).reset().setMode(ResetType.HARD).call();
							} catch (CheckoutConflictException e) {
								/* Can a hard reset even have a conflict? */
								throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
							} catch (GitAPIException e) {
								throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
							}
						}

						// set up a working tree which points to the gitdir in the parent repo:

						// handling paths in java, god forbid relative paths, is such an unbelievable backwater.  someday please make a whole library that actually disambiguates pathnames from filedescriptors properly
						int ups = StringUtils.countMatches(module.getPath(), "/");
						// look up the path between the `repo` and its possible git dir location.  if the gitdir is relocated, we have adjust our own relocations to compensate.
						String parentGitPointerStr = ".git/";
						String parentWorktreePointerStr = "../";
						// load it ourselves because we explicitly want the unresolved path, not what jgit would give us back from `repo.getDirectory().toString()`.
						File parentGitPointer = new File(repo.getWorkTree(), ".git");
						if (parentGitPointer.isFile()) {
							// this shouldn't have to be recursive fortunately (that recursion is done implicitly by the chaining of each guy at each stage).
							// this does however feel fairly fragile.  it's considerable that perhaps we should try to heuristically determine when paths are just to crazy to deal with.  but, on the off chance that check was overzealous, it would be very irritating, so let's not.
							// frankly, if you're doing deeply nested submodules, or other advanced gitdir relocations, at some point you're taking it upon yourself to deal with the inevitably complex outcomes and edge case limitations.
							parentGitPointerStr = IOForge.readFileAsString(parentGitPointer);
							if (!"gitdir:".equals(parentGitPointerStr.substring(0, 7)))
								throw new ConfigInvalidException("cannot understand location of parent project git directory");
							parentGitPointerStr = parentGitPointerStr.substring(7).trim() + "/";
							parentWorktreePointerStr = repo.getConfig().getString("core", null, "worktree") + "/";
						}
						// jgit does not appear to create the .git file correctly here :/
						// nor even consider it to be jgit's job to create the worktree yet, apparently, so do that
						module.repo.getWorkTree().mkdirs();
						// need modules/[module]/config to contain 'core.worktree' = appropriate
						String submoduleWorkTreeRelativeToGitDir = StringUtils.repeat("../", ups+2)+parentWorktreePointerStr+module.getPath();
						StoredConfig cnf = module.repo.getConfig();
						cnf.setString("core", null, "worktree", submoduleWorkTreeRelativeToGitDir);
						cnf.save();
						// need [module]/.git to contain 'gitdir: appropriate' (which appears to not be normal gitconfig)
						String submoduleGitDirRelativeToWorkTree = StringUtils.repeat("../", ups+1)+parentGitPointerStr+"modules/"+module.getPath();
						IOForge.saveFile("gitdir: "+submoduleGitDirRelativeToWorkTree+"\n", new File(module.repo.getWorkTree(), ".git"));
					} catch (IOException e) {
						throw new MdmRepositoryIOException("create a new submodule", true, module.getHandle(), e);
					}

				try {
					if (initLocalConfig(repo, module))
						repo.getConfig().save();
				} catch (IOException e) {
					throw new MdmRepositoryIOException("save changes", true, "the local git configuration file", e);
				}
				try {
					setMdmRemote(module);
					module.getRepo().getConfig().save();
				} catch (IOException e) {
					throw new MdmRepositoryIOException("save changes", true, "the git configuration file for submodule "+module.getHandle(), e);
				}
			case INITIALIZED:
				if (module.getVersionName() == null || module.getVersionName().equals(module.getVersionActual()))
					return false;
			case REV_CHECKED_OUT:
				try {
					if (initModuleConfig(repo, module))
						module.getRepo().getConfig().save();
				} catch (IOException e) {
					throw new MdmRepositoryIOException("save changes", true, "the git configuration file for submodule "+module.getHandle(), e);
				}

				final String versionBranchName = "refs/heads/mdm/release/"+module.getVersionName();
				final String versionTagName = "refs/tags/release/"+module.getVersionName();

				/* Fetch only the branch labelled with the version requested. */
				if (module.getRepo().getRef(versionBranchName) == null) try {
					RefSpec releaseBranchRef = new RefSpec()
						.setForceUpdate(true)
						.setSource(versionBranchName)
						.setDestination(versionBranchName);
					RefSpec releaseTagRef = new RefSpec()
						.setForceUpdate(true)
						.setSource(versionTagName)
						.setDestination(versionTagName);
					new Git(module.getRepo()).fetch()
						.setRemote("origin")
						.setRefSpecs(releaseBranchRef, releaseTagRef)
						.setTagOpt(TagOpt.NO_TAGS)
						.call();
				} catch (InvalidRemoteException e) {
					throw new MdmRepositoryStateException("find a valid remote origin in the config for the submodule", module.getHandle(), e);
				} catch (TransportException e) {
					URIish remote = null;
					try {	//XXX: if we went through all the work to resolve the remote like the fetch command does, we could just as well do it and hand the resolved uri to fetch for better consistency.
						remote = new RemoteConfig(module.getRepo().getConfig(), "origin").getURIs().get(0);
					} catch (URISyntaxException e1) {}
					throw new MdmRepositoryIOException("fetch from a remote", false, remote.toASCIIString(), e).setAdditionalMessage("check your connectivity and try again?");
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
					// this one is just a perfectly reasonable message with a list of files in conflict; we'll take it.
					throw new MdmRepositoryStateException(module.getHandle(), e); // this currently gets translated to a :'( exception and it's probably more like a :(
				} catch (GitAPIException e) {
					throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
				}
				return true;
			default:
				throw new MajorBug();
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
		repo.getConfig().setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, module.getPath(), ConfigConstants.CONFIG_KEY_URL, module.getUrlLocal());
		repo.getConfig().setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, module.getPath(), ConfigConstants.CONFIG_KEY_UPDATE, "none");
		return true;
	}

	/**
	 * Copy in `url` git config keys from the parent repo config into the submodule config.
	 * This allows for easily having per-project 'insteadof' url rewrites which apply even
	 * when mdm is doing the creation of new repos (which is otherwise a tad hard to get at with git submodules).
	 * @return true if module.getRepo().getConfig() has been modified and should be saved.
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public static boolean initModuleConfig(Repository repo, MdmModule module) throws IOException, ConfigInvalidException {
		Config moduleConfig = module.getRepo().getConfig();
		// have to explicitly load the parent repo config in isolate, because `repo.getConfig` includes views of the system and user gitconfig, which we won't want to proxy here.
		FileBasedConfig parentConfig = new FileBasedConfig(new File(repo.getDirectory(), "config"), repo.getFS());
		try {
			parentConfig.load();
		} catch (IOException e) {
			throw new MdmRepositoryIOException(false, "the local git configuration file", e);
		}

		// copy any url_insteadof patterns from the parent repo's git config into the module's git config.
		// note that we do not strip out any additional insteadof's the module may have; if you've added those, it's none of our business (though at this point, we do overwrite).
		// see org.eclipse.jgit.transport.RemoteConfig for how these actually get used.
		for (String url : parentConfig.getSubsections(ConfigConstants.CONFIG_KEY_URL))
			for (String insteadOf : parentConfig.getStringList(ConfigConstants.CONFIG_KEY_URL, url, "insteadof"))
				moduleConfig.setString(ConfigConstants.CONFIG_KEY_URL, url, "insteadof", insteadOf);
		for (String url : parentConfig.getSubsections(ConfigConstants.CONFIG_KEY_URL))
			for (String insteadOf : parentConfig.getStringList(ConfigConstants.CONFIG_KEY_URL, url, "pushinsteadof"))
				moduleConfig.setString(ConfigConstants.CONFIG_KEY_URL, url, "pushinsteadof", insteadOf);
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
	 * <p>
	 * The url is taken from the parent repo's local .git/config entry for this
	 * submodule (which should have already been initialized by a call to
	 * {@link #initLocalConfig(Repository, MdmModule)}), and may be subject to some
	 * transformations before it is saved to the submodule's .git/config (namely,
	 * github urls may be altered to make sure we don't hit their user-agent-sensitive
	 * http API).
	 */
	public static void setMdmRemote(MdmModule module) {
		String url = module.getUrlLocal();
		if (isGithubHttpUrl(url) && !url.endsWith(".git")) {
			// Github 404's unknown user agents only from some urls, so in order to have jgit accept the same urls that cgit will accept, we rewrite to the url that always responds correctly.
			url += ".git";
		}
		module.getRepo().getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", ConfigConstants.CONFIG_KEY_URL, url);
		module.getRepo().getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", "fetch", "+refs/heads/mdm/init:refs/remotes/origin/mdm/init");
	}

	/**
	 * wield `git ls-remote` to get a list of branches matching the labelling pattern
	 * mdm releases use. works locally or remote over any transport git itself
	 * supports.
	 *
	 * @param repo
	 *                this argument is completely stupid and should not be required.
	 *                The jgit api for ls-remote is wrong. Go ahead and use the parent
	 *                repo.
	 * @throws GitAPIException
	 * @throws TransportException
	 * @throws InvalidRemoteException
	 */
	public static List<String> getVersionManifest(Repository repo, String releasesUrl) throws InvalidRemoteException, TransportException, GitAPIException {
		Collection<Ref> refs = new Git(repo).lsRemote()
			.setRemote(releasesUrl)
			.call();
		final String mdmReleaseRefPrefix = "refs/heads/mdm/release/";
		List<String> v = new ArrayList<String>();
		for (Ref ref : refs) {
			if (ref.getName().startsWith(mdmReleaseRefPrefix))
				v.add(ref.getName().substring(mdmReleaseRefPrefix.length()));
		}
		Collections.sort(v, new VersionComparator());
		return v;
	}

	public static boolean isCommitedGitlink(Repository repo, String path) throws IOException {
		return SubmoduleWalk.forIndex(repo).setFilter(PathFilter.create(path)).next();
	}


	/**
	 * Create a new "empty" commit in a new branch. If the branch name already exists,
	 * a forced update will be performed.
	 *
	 * @return result of the branch update.
	 * @throws IOException
	 */
	public static RefUpdate.Result createOrphanBranch(Repository repo, String branchName) throws IOException {
		ObjectInserter odi = repo.newObjectInserter();
		try {
			// Create an (empty) tree object to reference from a commit.
			TreeFormatter tree = new TreeFormatter();
			ObjectId treeId = odi.insert(tree);

			// Create a commit object... most data is nulls or silly placeholders; I expect you'll amend this commit.
			CommitBuilder commit = new CommitBuilder();
			PersonIdent author = new PersonIdent("mdm", "");
			commit.setAuthor(author);
			commit.setCommitter(author);
			commit.setMessage("");
			commit.setTreeId(treeId);

			// Insert the commit into the repository.
			ObjectId commitId = odi.insert(commit);
			odi.flush();

			// (Re)extract the commit we just flushed, and update a new branch ref to point to it.
			RevWalk revWalk = new RevWalk(repo);
			try {
				RevCommit revCommit = revWalk.parseCommit(commitId);
				if (!branchName.startsWith("refs/"))
					branchName = "refs/heads/" + branchName;
				RefUpdate ru = repo.updateRef(branchName);
				ru.setNewObjectId(commitId);
				ru.setRefLogMessage("commit: " + revCommit.getShortMessage(), false);
				return ru.forceUpdate();
			} finally {
				revWalk.release();
			}
		} finally {
			odi.release();
		}
	}
}
