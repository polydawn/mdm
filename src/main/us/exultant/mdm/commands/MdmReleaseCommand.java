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
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.treewalk.filter.*;
import us.exultant.ahs.iob.*;
import us.exultant.ahs.util.*;
import us.exultant.mdm.*;

public class MdmReleaseCommand extends MdmCommand {
	public MdmReleaseCommand(Repository repo, Namespace args) {
		super(repo, args);
	}

	public static final Pattern RELEASE_URL_NAME_EXTRACT = Pattern.compile("^(.*)-releases(?:.git)?$");

	public MdmExitMessage call() throws IOException, ConfigInvalidException, MdmException {
		try {
			assertInRepoRoot();
		} catch (MdmExitMessage e) { return e; }

		String relRepoPath = args.getString("repo");
		String version = args.getString("version");
		String snapshotPath = relRepoPath+"/"+version;

		Repository relRepo = new FileRepositoryBuilder()
			.setWorkTree(new File(relRepoPath))
			.build();

		// sanity check the releases-repo
		if (relRepo == null)						// check that releases-repo is a git repo at all
			return new MdmExitMessage(":(", "releases-repo directory '"+relRepoPath+"' doesn't look like a git repo!  (Maybe you forgot to set up with `mdm release-init` before making your first release?)");
		if (relRepo.getRef("refs/heads/mdm/init") == null)		// check that the releases-repo has the branches we expect from an mdm releases repo
			return new MdmExitMessage(":'(", "releases-repo directory '"+relRepoPath+"' contains a git repo, but it doesn't look like something that's been set up for mdm releases.\n(There's no branch named \"mdm/init\", and there should be.)");
		if (relRepo.getRef("refs/heads/master") == null)		// check that the releases-repo has the branches we expect from an mdm releases repo
			return new MdmExitMessage(":'(", "releases-repo directory '"+relRepoPath+"' contains a git repo, but it doesn't look like something that's been set up for mdm releases.\n(There's no branch named \"master\", and there should be.)");
		if (relRepo.getRef("refs/heads/mdm/release/"+version) != null)	// check that nothing is already in the place where this version will be placed
			return new MdmExitMessage(":'(", "the releases repo already has a release point labeled version "+version+" !");
		if (version.contains("/"))					// we could deal with these, certainly, but just... why?  even if mdm itself were to handle it smoothly, it would make life that much more annoying for any other scripts ever, and it makes the directory structure just a mess of irregular depth.
			return new MdmExitMessage(":(", "you can't use version names that have slashes in them, sorry.  it gets messy.");

		// sanity check part 2: make sure there's nothing in the version-named directory in master as well.  though really we're getting borderline facetious here: there's all sorts of problems that could well come up from having incomplete local state and then trying to push what turns out to be a coliding branch name, and so on.
		//  i'm just weeping for my lost time now, but in python this was one line: (len(str(git("ls-tree", "master", "--name-only", args.version)))>0)
		RevTree tree = new RevWalk(relRepo).parseCommit(relRepo.resolve("refs/heads/master")).getTree();
		TreeWalk treeWalk = new TreeWalk(relRepo);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		treeWalk.setFilter(PathFilter.create(version));
		if (treeWalk.next())
			return new MdmExitMessage(":'(", "the releases repo already has files committed in the master branch where version "+version+" should go!");


		// select the artifact files that we'll be copying in
		String inputPath = args.getString("files");
		File inputFile = new File(inputPath);
		File[] inputFiles = new File[0];
		if (inputFile.isFile())			// if it's a file, we take it literally.
			inputFiles = new File[] { inputFile };
		else if (inputFile.isDirectory()) {	// if it's a dir, we grab everything within it minus hiddens (we don't really want to match dotfiles on the off chance someone tries to consider their entire repo to be snapshot-worthy, because then we'd grab the .git files, and that would be a mess).
			inputFiles = inputFile.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return !(file.isHidden() || file.getName().startsWith(".") || file.isDirectory());
				}
			});
		}
		// in python it was easy to do globs; in java it's not available in the standard libraries until 1.7, which i'm trying to avoid depending on.  we may get some library for it later.
		if (inputFiles.length == 0)
			return new MdmExitMessage(":(", "no files were found at "+inputPath+"\nrelease aborted.");

		// create a branch for the release commit.  depending on whether or not infix mode is enabled, this is either branching from the infix branch, or it's founding a new root of history.
		boolean infixMode = relRepo.getRef("refs/heads/mdm/infix") != null;
		if (infixMode)
			try {
				new Git(relRepo).checkout()
					.setCreateBranch(true)
					.setStartPoint("mdm/infix")
					.setName("mdm/release/"+version)
					.call();
			} catch (RefAlreadyExistsException e) {
				return new MdmExitMessage(":'(", "the releases repo already has a release point labeled version "+version+" !");
			} catch (RefNotFoundException e) {
				throw new MdmException("aborted due to concurrent modification of repo");
			} catch (InvalidRefNameException e) {
				return new MdmExitMessage(":(", "you can't use version names that git rejects as branch names.");
			} catch (CheckoutConflictException e) {
				throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
			} catch (GitAPIException e) {
				throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
			}
		else {
			Plumbing.createOrphanBranch(relRepo, "mdm/release/"+version);
			try {
				new Git(relRepo).checkout()
					.setName("mdm/release/"+version)
					.call();
			} catch (GitAPIException e) {
				throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
			}
		}

		// enumerate and copy in artifact files.
		File relRepoFile = new File(relRepoPath);
		for (File input : inputFiles) {
			IOForge.copyFile(input, new File(relRepoFile, input.getName()));
			X.saye("copying "+input+" to "+new File(relRepoFile, input.getName()));
		}

		// commit the changes
		try {
			new Git(relRepo).add()
				.addFilepattern(".")
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		try {
			CommitCommand commit = new Git(relRepo).commit()
				.setMessage("release version "+version);
			if (!infixMode) {
				commit.setAmend(true);	// because our mechanism for creating an orphan branch starts us with an empty commit.
				PersonIdent convergenceIdent = new PersonIdent("mdm", "", new Date(0), TimeZone.getTimeZone("GMT"));
				commit.setAuthor(convergenceIdent);
				commit.setCommitter(convergenceIdent);
			}
			commit.call();
			new Git(relRepo).tag()
				.setName("release/"+version)
				.call();
		} catch (NoHeadException e) {
			throw new MdmException("your repository is in an invalid state!", e);
		} catch (ConcurrentRefUpdateException e) {
			throw new MdmException("aborted due to concurrent modification of repo");
		} catch (NoMessageException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (UnmergedPathsException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (WrongRepositoryStateException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}


		// generate an accumulation commit.  do this from the master branch, but don't submit it yet, because when we roll in the artifacts we want them in a subdirectory so that when master is checked out all the versions are splayed out in the working tree at once.
		try {
			new Git(relRepo).checkout()
				.setName("master")
				.call();
		} catch (RefAlreadyExistsException e) {
			throw new MajorBug(e); // not even valid unless we're creating a new branch, which we aren't.
		} catch (RefNotFoundException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (InvalidRefNameException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (CheckoutConflictException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		try {
			new Git(relRepo).merge()
				.include(relRepo.getRef("mdm/release/"+version))
				.setFastForward(FastForwardMode.NO_FF)
				.setCommit(false)
				.call();
		} catch (NoHeadException e) {
			throw new MdmException("your repository is in an invalid state!", e);
		} catch (ConcurrentRefUpdateException e) {
			throw new MdmException("aborted due to concurrent modification of repo");
		} catch (CheckoutConflictException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (InvalidMergeHeadsException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (WrongRepositoryStateException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (NoMessageException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?  also, we're not even making a commit here.
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}

		// move the artifact files into a version-named directory
		File artifactDestFile = new File(relRepoFile, version);
		if (!artifactDestFile.mkdir())
			return new MdmExitMessage(":'(", "couldn't make the directory named \""+version+"\" to put the releases into because there was already something there.");

		for (File input : inputFiles)
			 new File(relRepoFile, input.getName()).renameTo(new File(artifactDestFile, input.getName()));

		// now fire off the accumulation commit, and that commit now becomes head of the master branch.
		try {
			RmCommand rmc = new Git(relRepo).rm();
			for (File input : inputFiles)
				rmc.addFilepattern(input.getName());
			rmc.call();
			new Git(relRepo).add()
				.addFilepattern(version)
				.call();
		} catch (NoFilepatternException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}
		try {
			new Git(relRepo).commit()
				.setMessage("merge release version "+version+" to master")
				.call();
			new Git(relRepo).tag()
				.setName("mdm/master/"+version)
				.call();
		} catch (NoHeadException e) {
			throw new MdmException("your repository is in an invalid state!", e);
		} catch (ConcurrentRefUpdateException e) {
			throw new MdmException("aborted due to concurrent modification of repo");
		} catch (NoMessageException e) {
			throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
		} catch (UnmergedPathsException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (WrongRepositoryStateException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		} catch (GitAPIException e) {
			throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
		}

		// commit the new hash of the releases-repo into the project main repo (if we are operating in a canonically placed releases submodule)
		if (isInRepoRoot() && relRepoPath.equals("releases") && Plumbing.isCommitedGitlink(repo, "releases")) {
			try {
				new Git(repo).commit()
					.setOnly("releases")
					.setMessage("release version "+version)
					.call();
				new Git(repo).tag()
					.setName("release/"+version)
					.call();
			} catch (NoHeadException e) {
				throw new MdmException("your repository is in an invalid state!", e);
			} catch (ConcurrentRefUpdateException e) {
				throw new MdmException("aborted due to concurrent modification of repo");
			} catch (NoMessageException e) {
				throw new MajorBug(e); // why would an api throw exceptions like this *checked*?
			} catch (UnmergedPathsException e) {
				throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
			} catch (WrongRepositoryStateException e) {
				throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
			} catch (GitAPIException e) {
				throw new MajorBug("an unrecognized problem occurred.  please file a bug report.", e);
			}
		}

		return new MdmExitMessage(":D", "release version "+version+" complete");
	}
}
