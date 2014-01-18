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
import net.polydawn.mdm.util.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.treewalk.filter.*;
import us.exultant.ahs.util.*;

public class MdmReleaseCommand extends MdmCommand {
	public MdmReleaseCommand(Repository repo, Namespace args) {
		super(repo, args);
	}

	public void parse(Namespace args) {
		relRepoPath = args.getString("repo");
		version = args.getString("version");
		snapshotPath = relRepoPath+"/"+version;
		inputPath = args.getString("files");
	}

	public void validate() throws MdmExitMessage {
		// Reject version names with slashes.  It's physically possible to deal with these, but just... why?  Even if mdm itself were to handle it smoothly, it would make life that much more annoying for any other scripts ever, and it would make the directory structure on the master branch just a mess of irregular depth.
		if (version.contains("/"))
			throw new MdmExitMessage(":(", "you can't use version names that have slashes in them, sorry.  it gets messy.");
	}

	public static final Pattern RELEASE_URL_NAME_EXTRACT = Pattern.compile("^(.*)-releases(?:.git)?$");

	String relRepoPath;
	String version;
	String snapshotPath;
	String inputPath;

	public MdmExitMessage call() throws IOException, ConfigInvalidException, MdmException, MdmExitMessage {
		MdmModuleRelease relModule = loadReleaseModule();
		Repository relRepo = relModule.getRepo();

		assertReleaseRepoDoesntAlreadyContain(relModule, version);
		assertReleaseRepoClean(relModule);

		List<String> inputFiles = selectInputFiles();

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
		File inputBase = new File(inputPath).getCanonicalFile();
		if (inputBase.isFile()) inputBase = inputBase.getParentFile();
		File relRepoFile = new File(relRepoPath).getCanonicalFile();
		for (String input : inputFiles) {
			File inputFull = new File(inputBase, input);
			File dest = new File(relRepoFile, input);
			if (inputFull.isDirectory())
				FileUtils.copyDirectory(inputFull, dest, new FileFilter() {
					public boolean accept(File file) {
						return !(file.isDirectory() && file.listFiles().length == 0);
					}
				}, true, false);
			else
				FileUtils.copyFile(inputFull, dest, true, false);
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
				.setAnnotated(false)
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

		for (String input : inputFiles)
			 new File(relRepoFile, input).renameTo(new File(artifactDestFile, input));

		// now fire off the accumulation commit, and that commit now becomes head of the master branch.
		try {
			RmCommand rmc = new Git(relRepo).rm();
			for (String input : inputFiles)
				rmc.addFilepattern(input);
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
				.setAnnotated(false)
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
					.setAnnotated(false)
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

	MdmModuleRelease loadReleaseModule() {
		return MdmModuleRelease.load(relRepoPath);
	}

	/**
	 * Check that the releases area free of clutter.
	 *
	 * @throws MdmExitMessage
	 *                 if the releases repo has uncommitted changes.
	 */
	void assertReleaseRepoClean(MdmModuleRelease relModule) throws MdmExitMessage {
		if (relModule.hasDirtyFiles())
			throw new MdmExitMessage(":(", "there is uncommitted changes in the release repo.  cannot release.");
	}

	/**
	 * Check that nothing that would get in the way of a version name is present in
	 * the repository.
	 * <p>
	 * Checks performed include tags, branches, and paths committed to the master
	 * branch. This is good coverage against local conflicts, but it's worth noting
	 * that there's all sorts of problems that could well come up from having
	 * incomplete local state and then trying to push what turns out to be a coliding
	 * branch name, and so on.
	 *
	 * @param relModule
	 * @param version
	 * @throws MdmExitMessage
	 * @throws IOException
	 */
	static void assertReleaseRepoDoesntAlreadyContain(MdmModuleRelease relModule, String version) throws MdmExitMessage, IOException {
		Repository relRepo = relModule.getRepo();

		// part 1: check branch for version name doesn't already exist
		if (relRepo.getRef("refs/heads/mdm/release/"+version) != null)
			throw new MdmExitMessage(":'(", "the releases repo already has a release point branch labeled version "+version+" !");

		// part 2: check tag for version name doesn't already exist
		if (relRepo.getRef("refs/tags/release/"+version) != null)
			throw new MdmExitMessage(":'(", "the releases repo already has a release point tag labeled version "+version+" !");

		// part 3: make sure there's nothing in the version-named directory in master.
		RevTree tree = new RevWalk(relRepo).parseCommit(relRepo.resolve("refs/heads/master")).getTree();
		TreeWalk treeWalk = new TreeWalk(relRepo);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		treeWalk.setFilter(PathFilter.create(version));
		if (treeWalk.next())
			throw new MdmExitMessage(":'(", "the releases repo already has files committed in the master branch where version "+version+" should go!");
	}

	List<String> selectInputFiles() throws MdmExitMessage, IOException {
		// select the artifact files that we'll be copying in
		File inputFile = new File(inputPath).getCanonicalFile();
		List<String> inputFilenames = null;
		if (inputFile.isFile())	{		// if it's a file, we take it literally.
			inputFilenames = new ArrayList<String>(1);
			inputFilenames.add(inputFile.getName());
		} else if (inputFile.isDirectory()) {	// if it's a dir, we grab everything within it.
			File[] inputFiles = inputFile.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return !file.getName().equals(".git");
				}
			});
			inputFilenames = new ArrayList<String>(inputFiles.length);
			for (File f : inputFiles)
				inputFilenames.add(f.getName());
		}

		if (inputFilenames == null || inputFilenames.size() == 0)
			throw new MdmExitMessage(":(", "no files were found at "+inputPath+"\nrelease aborted.");

		Collections.sort(inputFilenames);
		return inputFilenames;
	}
}
