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

public class MdmStatusCommand extends MdmCommand<Void> {
	public MdmStatusCommand(Repository repo) {
		super(repo);
	}

	private PrintStream os = System.out;

	public void setPrintStream(PrintStream os) {
		this.os = os;
	}

	public Void call() throws IOException, ConfigInvalidException {
		MdmModuleSet moduleSet = new MdmModuleSet(repo);
		Map<String,MdmModule> modules = moduleSet.getDependencyModules();

		if (modules.size() == 0) {
			os.println(" --- no managed dependencies --- ");
			return null;
		}

		Collection<String> row1 = new ArrayList<>(modules.keySet());
		row1.add("dependency:");
		int width1 = Loco.chooseFieldWidth(row1);

		os.printf("%-"+width1+"s   \t %s\n", "dependency:", "version:");
		os.printf("%-"+width1+"s   \t %s\n", "-----------", "--------");

		for (MdmModule mod : modules.values()) {
			StatusTuple status = status(mod);
			os.printf("  %-"+width1+"s \t   %s\n", mod.getHandle(), status.version);
			for (String warning : status.warnings)
				os.printf("  %-"+width1+"s \t   %s\n", "", "  !! "+warning);
		}
		return null;
	}

	public StatusTuple status(MdmModule module) {
		StatusTuple s = new StatusTuple();

		if (!module.getHandle().equals(module.getPath()))
			s.errors.add("Handle and path are not the same.  This is very strange and may cause issues with other git tools.");
		if (module.getUrlHistoric() == null)
			s.errors.add("No url for remote repo is set in gitmodules.");

		if (module.getType() == MdmModuleType.DEPENDENCY) {
			if (module.getVersionName() == null)
				s.errors.add("Version name not specified in gitmodules file!");
			if (module.getHeadId() == null) {
				s.version = "-- uninitialized --";
			} else {
				s.version = (module.getVersionActual() == null) ? "__UNKNOWN_VERSION__" : module.getVersionActual();
				if (!module.getVersionActual().equals(module.getVersionName()))
					s.warnings.add("intended version is "+module.getVersionName()+", run `mdm update` to get it");
				if (!module.getIndexId().equals(module.getHeadId()))
					s.warnings.add("commit currently checked out does not match hash in parent project");
				if (module.hasDirtyFiles())
					s.warnings.add("there are uncommitted changes in this submodule");
			}
		}
		return s;
	}

	public class StatusTuple {
		public String version;
		/** Major notifications about the state of a module -- things that mean your build probably won't work. */
		public List<String> warnings = new ArrayList<>();
		/** Errors so bad that we can't even tell what's supposed to be going on with this module. */
		public List<String> errors = new ArrayList<>();
	}
}
