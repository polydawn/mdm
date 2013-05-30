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
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.submodule.*;
import org.eclipse.jgit.treewalk.filter.*;
import us.exultant.mdm.errors.*;

public abstract class MdmModule {
	/**
	 * Construct an MdmModule referring to an existing repository that stands alone
	 * (not a submodule / no parent repo).
	 *
	 * (Currently only an option for {@link MdmModuleRelease};
	 * {@link MdmModuleDependency} don't make sense without data in a parent repo).
	 *
	 * @param repo
	 * @param handle
	 */
	protected MdmModule(Repository repo, String handle) {
		this(repo, handle, null, null, null);
	}

	/**
	 * Construct an MdmModule referring to an existing repository that is a submodule.
	 * @param repo
	 * @param handle
	 * @param parentRepo
	 * @param type handed up from the subclass; we will sanity check against the gitmodulesCfg and throw if nonsense.
	 * @param gitmodulesCfg
	 * @param indexId the commit hash known to the parent repo index for this submodule (or null if it's not handy; we'll load it in that case).
	 * @throws MdmModuleTypeException if the {@code gitmodulesCfg} entries for {@code handle} don't concur with the {@code type} expected.
	 */
	protected MdmModule(Repository repo, String handle, MdmModuleType type, Repository parentRepo, Config gitmodulesCfg, ObjectId indexId) throws MdmModuleTypeException {
		this(repo, handle, parentRepo, gitmodulesCfg, indexId);

		MdmModuleType type_configured = MdmModuleType.fromString(gitmodulesCfg.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path, MdmConfigConstants.Module.MODULE_TYPE.toString()));
		if (type == null)
			throw new MdmModuleTypeException("expected module of type "+type+" for repository "+handle+", but gitmodules file has no known type for this module.");
		if (type != type_configured)
			throw new MdmModuleTypeException("expected module of type "+type+" for repository "+handle+", but gitmodules file states this is a "+type_configured+" module.");
	}

	private MdmModule(Repository repo, String handle, Repository parent, Config gitmodulesCfg, ObjectId indexId) throws MdmRepositoryIOException {
		this.handle = handle;

		if (repo == null) {
			this.headId = null;
			this.dirtyFiles = false;
		} else {
			try {
				this.headId = repo.resolve(Constants.HEAD);
			} catch (IOException e) {
				throw new MdmRepositoryIOException(false, handle, e);
			}

			boolean dirtyFiles;
			try {
				dirtyFiles = !new Git(repo).status().call().isClean();
			} catch (NoWorkTreeException e) {
				throw new RuntimeException("wat", e);
			} catch (GitAPIException e) {
				dirtyFiles = false;
			}
			this.dirtyFiles = dirtyFiles;
		}

		if (parent != null) {
			this.path = gitmodulesCfg.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, handle, ConfigConstants.CONFIG_KEY_PATH);

			this.urlHistoric = gitmodulesCfg.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, handle, ConfigConstants.CONFIG_KEY_URL);
			this.urlLocal = parent.getConfig().getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, handle, ConfigConstants.CONFIG_KEY_URL);

			if (indexId != null)
				this.indexId = indexId;
			else
				try {
					SubmoduleWalk generator = SubmoduleWalk.forIndex(parent).setFilter(PathFilter.create(path));
					this.indexId = generator.next() ? generator.getObjectId() : null;
				} catch (IOException e) {
					throw new MdmRepositoryIOException(false, parent.getWorkTree().getPath(), e);
				}

			SubmoduleStatusType statusType;
			if (path == null)
				// jgit report SubmoduleStatusType.MISSING if no path in .gitmodules file, but I don't even want to deal with that.
				throw new MdmModuleTypeException("no path for module "+handle+" listed in gitmodules file.");
			else if (urlLocal == null)
				// Report uninitialized if no URL in config file
				statusType = SubmoduleStatusType.UNINITIALIZED;
			else if (repo == null)
				// Report uninitialized if no submodule repository
				statusType = SubmoduleStatusType.UNINITIALIZED;
			else if (headId == null)
				// Report uninitialized if no HEAD commit in submodule repository
				statusType = SubmoduleStatusType.UNINITIALIZED;
			else if (!headId.equals(indexId))
				// Report checked out if HEAD commit is different than index commit
				statusType = SubmoduleStatusType.REV_CHECKED_OUT;
			else
				// Report initialized if HEAD commit is the same as the index commit
				statusType = SubmoduleStatusType.INITIALIZED;
			this.status = new SubmoduleStatus(statusType, path, indexId, headId);
		} else {
			this.path = handle;
			this.indexId = null;
			this.urlHistoric = null;
			this.urlLocal = null;
			this.status = null;
		}
	}

	// properties that exist for any module:

	/**
	 * Module's name in the config. Almost certainly ought to be the same as path for
	 * simplicity. And in fact even the jgit API seems to be a little confused about
	 * the distinction. And in fact even the cgit commands seem a little confused
	 * about the disinction! Still. We'll maintain it here, just in case the ground
	 * ever changes.
	 *
	 * Also, if not a submodule, may just be the name we're currently handling this
	 * module by; typically a relative path and not substantially changed from the
	 * user's arguments.
	 */
	private final String handle;

	/** 'path' key in config. */
	private final String path;

	/** The submodule repository. */
	Repository repo;

	/** The ID the module repo is actually at. */
	private final ObjectId headId;

	/** Are there uncommitted for changed files in the module? */
	private final boolean dirtyFiles;

	// properties that only make sense if a submodule:

	/** If a submodule, The ID the parent repo says this submodule should be at. */
	private final ObjectId indexId;

	/** If a submodule, Remote url as listed in the parent's present .gitmodules file. */
	private final String urlHistoric;

	/** If a submodule, Remote url as listed in the parent's .git/config file. */
	String urlLocal;

	private final SubmoduleStatus status;

	public String getHandle() {
		return this.handle;
	}

	public String getPath() {
		return this.path;
	}

	public SubmoduleStatus getStatus() {
		return status;
	}

	public Repository getRepo() {
		return this.repo;
	}

	public abstract MdmModuleType getType();

	public ObjectId getIndexId() {
		return this.indexId;
	}

	public ObjectId getHeadId() {
		return this.headId;
	}

	public String getUrlHistoric() {
		return this.urlHistoric;
	}

	public String getUrlLocal() {
		return this.urlLocal;
	}

	public boolean hasDirtyFiles() {
		return this.dirtyFiles;
	}
}
