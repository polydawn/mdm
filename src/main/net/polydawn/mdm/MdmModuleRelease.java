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
import net.polydawn.mdm.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.submodule.*;

public class MdmModuleRelease extends MdmModule {
	/**
	 * Open the repo at given path, throw if there's no repo or if it's missing the
	 * telltales of being an mdm release repo.
	 *
	 * @param relRepoPath path to repository to load.  Doubles as name.
	 * @return a repository that smells like a proper mdm release repo.
	 * @throws MdmRepositoryNonexistant if there's no repository there
	 * @throws MdmRepositoryIOException if there were errors reading the repository
	 * @throws MdmModuleTypeException if the repository doesn't look like an {@link MdmModuleRelease}.
	 */
	public static MdmModuleRelease load(String relRepoPath) throws MdmRepositoryNonexistant, MdmRepositoryIOException, MdmModuleTypeException {
		Repository relRepo;
		try {
			relRepo = new FileRepositoryBuilder()
				.setWorkTree(new File(relRepoPath).getCanonicalFile())	// must use getCanonicalFile to work around https://bugs.eclipse.org/bugs/show_bug.cgi?id=407478
				.build();
		} catch (IOException e) {
			throw new MdmRepositoryIOException(false, relRepoPath, e);
		}
		if (relRepo == null)						// check that releases-repo is a git repo at all
			throw new MdmRepositoryNonexistant(relRepoPath);
		return new MdmModuleRelease(relRepo, relRepoPath, null, null, null);
	}

	public static MdmModuleRelease load(Repository parent, SubmoduleWalk generator, Config gitmodulesCfg) throws MdmRepositoryNonexistant, MdmRepositoryIOException, MdmModuleTypeException {
		try {
			return new MdmModuleRelease(generator.getRepository(), generator.getPath(), parent, gitmodulesCfg, generator.getObjectId());
		} catch (IOException e) {
			throw new MdmRepositoryIOException(false, generator.getPath(), e);
		}
	}

	public static MdmModuleRelease load(Repository parent, String path, Config gitmodulesCfg) throws MdmRepositoryNonexistant, MdmRepositoryIOException, MdmModuleTypeException {
		try {
			Repository repo = SubmoduleWalk.getSubmoduleRepository(parent, path);
			return new MdmModuleRelease(repo, path, parent, gitmodulesCfg, null);
		} catch (IOException e) {
			throw new MdmRepositoryIOException(false, path, e);
		}
	}

	public static class MdmModuleReleaseNeedsBranch extends MdmModuleTypeException {
		public MdmModuleReleaseNeedsBranch(String relRepoPath, String branchName) {
			super("releases-repo directory '"+relRepoPath+"' contains a git repo, but it doesn't look like something that's been set up for mdm releases.\n(There's no branch named \""+branchName+"\", and there should be.)");
		}
	}

	private MdmModuleRelease(Repository repo, String handle, Repository parentRepo, Config gitmodulesCfg, ObjectId indexId) throws MdmModuleTypeException, MdmRepositoryIOException {
		super(repo, handle, parentRepo, gitmodulesCfg, indexId);
	}

	public MdmModuleType getType() {
		return MdmModuleType.RELEASES;
	}

	/**
	 * Check branch structure to see if the repo currently presents itself as a releases repository.
	 */
	public void assertPresentsAsReleaseRepo() {
		try {
			// we can have a gitlink committed in parent, but no repo currently present.
			if (repo == null)
				throw new MdmRepositoryNonexistant(getHandle());
			// check that the releases-repo has the branches we expect from an mdm releases repo
			if (repo.getRef("refs/heads/mdm/init") == null &&
			    repo.getRef("refs/remotes/origin/mdm/init") == null)
				throw new MdmModuleReleaseNeedsBranch(getHandle(), "mdm/init");
			if (repo.getRef("refs/heads/master") == null)
				throw new MdmModuleReleaseNeedsBranch(getHandle(), "master");
		} catch (IOException e) {
			throw new MdmRepositoryIOException(false, getHandle(), e);
		}
	}
}
