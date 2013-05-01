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
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import us.exultant.mdm.*;
import static us.exultant.mdm.Loco.*;
import static us.exultant.ahs.util.Strings.join;

public class MdmUpdateCommand extends MdmCommand {
	public MdmUpdateCommand(Repository repo) {
		super(repo);
	}

	public MdmExitMessage call() throws IOException, ConfigInvalidException {
		MdmModuleSet moduleSet = new MdmModuleSet(repo);
		Map<String,MdmModule> modules = moduleSet.getDependencyModules();

		// Go over every module and do what we can to it, keeping a list of who each kind of operation was performed on for summary output later.
		List<MdmModule> impacted = new ArrayList<>();
		List<MdmModule> unphased = new ArrayList<>();
		List<MdmModule> contorted = new ArrayList<>();
		for (MdmModule module : modules.values()) {
			try {
				if (Plumbing.fetch(repo, module))
					impacted.add(module);
				else
					unphased.add(module);
			} catch (MdmException e) {
				contorted.add(module);
			}
			//rm("-rf", join(".git/modules",subm));	# if this is one of the newer version of git (specifically, 1.7.8 or newer) that stores the submodule's data in the parent projects .git dir, clear that out forcefully as well or else git does some very silly things (you end up with the url changed but it recreates the old files and doesn't change the object id like it should).
			//XXX: we have no special detection or handling for when submodule deletes are pulled from upstream.  what you end up with after that is just untracked files.  that's a little suprising, in my mind, but it's not exactly wrong, either.
		}

		// That's all.  Compose a status string.
		StringBuilder status = new StringBuilder();
		status.append("mdm dependencies have been updated (");
		status.append(impacted.size()).append(" changed, ");
		status.append(unphased.size()).append(" unaffected");
		if (contorted.size() > 0)
			status.append(", ").append(contorted.size()).append(" contorted");
		status.append(")");
		if (impacted.size() > 0)
			status.append("\n  changed: \t").append(join(toHandles(impacted), "\n\t\t"));
		if (contorted.size() > 0)
			status.append("\n  contorted: \t").append(join(toHandles(contorted), "\n\t\t"));

		return new MdmExitMessage(contorted.size() > 0 ? ":(" : ":D", status.toString());
	}
}
