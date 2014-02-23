package net.polydawn.mdm;

import java.io.*;
import java.util.*;
import net.polydawn.mdm.errors.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.submodule.*;

public final class MdmModuleDependency extends MdmModule {
	public static MdmModuleDependency load(Repository parent, String handle, Config gitmodulesCfg) throws MdmRepositoryIOException, MdmModuleTypeException {
		try {
			return new MdmModuleDependency(SubmoduleWalk.getSubmoduleRepository(parent, handle), handle, parent, gitmodulesCfg, null);
		} catch (IOException e) {
			throw new MdmRepositoryIOException(false, handle, e);
		}
	}

	public static MdmModuleDependency load(Repository parent, SubmoduleWalk generator, Config gitmodulesCfg) throws MdmRepositoryIOException, MdmModuleTypeException {
		try {
			return new MdmModuleDependency(SubmoduleWalk.getSubmoduleRepository(parent, generator.getPath()), generator.getPath(), parent, gitmodulesCfg, generator.getObjectId());
		} catch (IOException e) {
			throw new MdmRepositoryIOException(false, generator.getPath(), e);
		}
	}

	private MdmModuleDependency(Repository repo, String handle, Repository parentRepo, Config gitmodulesCfg, ObjectId indexId) throws MdmRepositoryIOException, MdmModuleTypeException {
		super(repo, handle, parentRepo, gitmodulesCfg, indexId);

		versionName = gitmodulesCfg.getString(ConfigConstants.CONFIG_SUBMODULE_SECTION, getHandle(), MdmConfigConstants.Module.DEPENDENCY_VERSION.toString());

		String versionActual = null;
		if (repo != null)
			try {
				List<Ref> tags = new Git(repo).branchList().call();
				for (Ref tag : tags) {
					if (tag.getObjectId().equals(getHeadId())) {
						String[] tagChunks = tag.getName().split("/");
						// An example release branch name is "refs/heads/mdm/release/v1".
						// All release branch names must have at least those four prefix chunks (and thus at minimum five chunks total).
						// For all branches, index 0 is 'refs', and 1 is 'heads', so we skip that check (branchList() already did that).
						if (tagChunks.length < 5) continue;
						if (!tagChunks[2].equals("mdm")) continue;
						if (!tagChunks[3].equals("release")) continue;
						// Found a release branch pattern.  Take the rest as version name.
						StringBuilder vab = new StringBuilder();
						for (int i = 4; i < tagChunks.length; i++)
							// Note that we're trying to be nice to version names with slashes in them here, but really, that's not a
							// generally supported feature (and trying to create a release with such a name is rejected by `mdm release`).
							vab.append(tagChunks[i]).append('/');
						versionActual = vab.substring(0, vab.length()-1);
						break;
					}
				}
			} catch (GitAPIException e) {}
		this.versionActual = versionActual;
	}

	/** The version named in the .gitmodules file. */
	private final String versionName;

	/** A version name parsed from the tags of the currently checked out head of the module. or null if we failed to parse one out. */
	private final String versionActual;

	public MdmModuleType getType() {
		return MdmModuleType.DEPENDENCY;
	}

	public String getVersionName() {
		return this.versionName;
	}

	public String getVersionActual() {
		return this.versionActual;
	}
}
