package us.exultant.mdm;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.submodule.*;
import us.exultant.mdm.errors.*;

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
		try {
			List<Ref> tags = new Git(repo).tagList().call();
			for (Ref tag : tags) {
				if (tag.getObjectId().equals(getHeadId())) {
					String[] tagChunks = tag.getName().split("/");
					// for all tags, index 0 is 'refs', and 1 is 'tags'.
					if (tagChunks[2].equals("release") && tagChunks.length > 2) {
						versionActual = "";
						for (int i = 3; i < tagChunks.length; i++)
							versionActual += tagChunks[i];
						break;
					}
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
