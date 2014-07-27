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
import net.polydawn.mdm.*;
import net.polydawn.mdm.errors.*;
import net.polydawn.mdm.jgit.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.*;
import us.exultant.ahs.iob.*;
import static net.polydawn.mdm.Loco.*;
import static us.exultant.ahs.util.Strings.join;

public class MdmUpdateCommand extends MdmCommand {
	public MdmUpdateCommand(Repository repo) {
		super(repo);
	}

	public void parse(Namespace args) {
		treatHashMismatchAsError = args.getBoolean("strict") == Boolean.TRUE;
	}

	private boolean treatHashMismatchAsError = false;

	public void validate() throws MdmExitMessage {}

	public MdmExitMessage call() throws ConfigInvalidException, IOException, GitAPIException {
		try {
			assertInRepo();
		} catch (MdmExitMessage e) { return e; }

		MdmModuleSet moduleSet;
		try {
			moduleSet = new MdmModuleSet(repo);
		} catch (ConfigInvalidException e) {
			throw new MdmExitInvalidConfig(Constants.DOT_GIT_MODULES);
		}
		Map<String,MdmModuleDependency> modules = moduleSet.getDependencyModules();

		// Go over every module and do what we can to it, keeping a list of who each kind of operation was performed on for summary output later.
		List<MdmModule> impacted = new ArrayList<MdmModule>();
		List<MdmModule> unphased = new ArrayList<MdmModule>();
		List<MdmModule> contorted = new ArrayList<MdmModule>();
		List<String> removed = new ArrayList<String>();
		int hashMismatchWarnings = 0;
		int i = 0;
		boolean fancy = System.console() != null;
		for (MdmModuleDependency module : modules.values()) {
			i++;
			try {
				os.print((fancy ? "\033[2K\r" : "") + "updating module "+i+" of "+modules.size()+": "+module.getHandle() +" ..." + (fancy ? "" : "\n"));
				if (Plumbing.fetch(repo, module)) {
					impacted.add(module);
					ObjectId moduleHeadId = module.getRepo().resolve(Constants.HEAD);
					List<ObjectId> mergeHeads = repo.readMergeHeads();
					if (mergeHeads == null) {
						// if there are no merges in progress, we check if the dependency version we fetched by name has the hash that's expected by the parent repo.
						if (!moduleHeadId.equals(module.getIndexId())) {
							// in putting the module to the version named in .gitmodules, we made it disagree with the parent index.
							// this probably indicates oddness.
							hashMismatchWarnings++;
							os.println((fancy ? "\033[2K\r" : "") + "notice: in updating "+module.getHandle()+" to version "+module.getVersionName()+", mdm left the submodule with a different hash checked out than the parent repo expected.");
						}
					} else {
						// if we're in the middle of a merge, we skip the normal hash validity check, because
						// module.getIndexId() contains the index's hash for the submodule only, and that can set off false alarms.
						// there's actually a valid *set* of hashes here, one from each merge head, and we consider them all.
						boolean acceptable = false;
						mergeHeads.add(repo.resolve(Constants.HEAD));
						for (ObjectId incomingHead : mergeHeads) {
							if (acceptable) break;
							RevTree incomingHeadRootTree = new RevWalk(repo).parseCommit(incomingHead).getTree();
							ObjectId incomingModuleHeadId = TreeWalk.forPath(repo, module.getPath(), incomingHeadRootTree).getObjectId(0);
							acceptable |= moduleHeadId.equals(incomingModuleHeadId);
						}
						if (!acceptable) {
							// in putting the module to the version named in .gitmodules, we didn't get a hash that agreed with *any* of the parent indexes.
							hashMismatchWarnings++;
							os.println((fancy ? "\033[2K\r" : "") + "notice: in updating "+module.getHandle()+" to version "+module.getVersionName()+", mdm left the submodule with a different hash checked out than *any* of the merging branches.  This is only expected if you changed the version name to a value also not present on any of the merging branches.");
							// TODO: we could get even smarter and parse the gitmodules file from every merge head and use that to give a better qualification of the notice message.
						}
					}
				} else
					unphased.add(module);
			} catch (MdmException e) {
				os.println((fancy ? "\033[2K\r" : "") + "error: in updating "+module.getHandle()+" to version "+module.getVersionName()+", "+e);
				contorted.add(module);
			}
		}
		os.print((fancy ? "\033[2K\r" : ""));

		// look for other repositories that *aren't* currently linked as submodules.  if they were created by mdm, we should sweep up.
		SubrepoWalk subrepos = new SubrepoWalk(repo);
		while (subrepos.next()) {
			String subrepoPath = subrepos.getPathString();

			// if it's something we're currently managing, of course we should pass that over
			if (modules.get(subrepoPath) != null)
				continue;

			Repository subrepo = subrepos.getRepo();

			// if it didn't actually manifest enough config to look like a real git repo, it's something odd and we'll leave it alone.
			if (subrepo == null)
				continue;

			// if it's not been flagged as our demense, weapons hold
			if (!MdmModuleType.DEPENDENCY.toString().equals(subrepo.getConfig().getString("mdm", null, "mdm")))
				continue;

			// if it's clean, weapons free.  otherwise, report frustration.
			try {
				if (new Git(subrepo).status().call().isClean()) {
					removed.add(subrepoPath);
					IOForge.delete(subrepo.getWorkTree());
				} else {
					os.println((fancy ? "\033[2K\r" : "") + "notice: not removing unlinked dependency at "+subrepoPath+" because it contains uncommitted changes.");
				}
			} catch (NoWorkTreeException e) {
				/* we literally wouldn't be here if this was the case.  or, our job is already done by a race, I guess. */
			}
		}

		// explain notices about hash mismatches, if any occured.
		if (hashMismatchWarnings > 0) {
			os.println();
			os.println("Warnings about submodule checkouts not matching the hash expected by the parent");
			os.println("repo may indicate a problem which you should investigate immediately to make");
			os.println("sure your dependencies are repeatable to others.");
			os.println();
			os.println("This issue may be because the repository you are fetching from has moved what");
			os.println("commit the version branch points to (which is cause for concern), or it may be");
			os.println("a local misconfiguration (did you resolve a merge conflict recently?  audit");
			os.println("your log; the version name in gitmodules config must move at the same time as");
			os.println("the submodule hash).");
			os.println();
		} else if (contorted.size() > 0) {
			os.println();
		}


		// That's all.  Compose a status string.
		StringBuilder status = new StringBuilder();
		status.append("mdm dependencies have been updated (");
		status.append(impacted.size()).append(" changed, ");
		status.append(unphased.size()).append(" unaffected");
		if (contorted.size() > 0)
			status.append(", ").append(contorted.size()).append(" contorted");
		if (removed.size() > 0)
			status.append(", ").append(removed.size()).append(" removed");
		status.append(")");
		if (impacted.size() > 0)
			status.append("\n  changed: \t").append(join(toHandles(impacted), "\n\t\t"));
		if (contorted.size() > 0)
			status.append("\n  contorted: \t").append(join(toHandles(contorted), "\n\t\t"));
		if (removed.size() > 0)
			status.append("\n  removed: \t").append(join(removed, "\n\t\t"));

		// the exit code depends on whether or not strict mode was enabled
		if (contorted.size() > 0)
			return new MdmExitMessage(":(", status.toString());
		else if (treatHashMismatchAsError && hashMismatchWarnings > 0)
			return new MdmExitMessage(":(", status.toString());
		else
			return new MdmExitMessage(":D", status.toString());
	}
}
