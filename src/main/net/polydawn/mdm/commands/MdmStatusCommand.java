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
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import us.exultant.ahs.util.*;

public class MdmStatusCommand extends MdmCommand {
	public MdmStatusCommand(Repository repo, PrintStream os) {
		super(repo, os);
	}

	public MdmStatusCommand(Repository repo) {
		super(repo);
	}

	private PrintStream os = System.out;

	public void setPrintStream(PrintStream os) {
		this.os = os;
	}

	public void parse(Namespace args) {}

	public void validate() throws MdmExitMessage {}

	public MdmExitMessage call() throws IOException {
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

		if (modules.size() == 0) {
			os.println(" --- no managed dependencies --- ");
			return new MdmExitMessage(0);
		}

		Collection<String> row1 = new ArrayList<String>(modules.keySet());
		row1.add("dependency:");
		int width1 = Strings.chooseFieldWidth(row1);

		os.printf("%-"+width1+"s   \t %s\n", "dependency:", "version:");
		os.printf("%-"+width1+"s   \t %s\n", "-----------", "--------");

		for (MdmModuleDependency mod : modules.values()) {
			StatusTuple status = status(mod);
			os.printf("  %-"+width1+"s \t   %s\n", mod.getHandle(), status.version);
			for (String warning : status.warnings)
				os.printf("  %-"+width1+"s \t   %s\n", "", "  !! "+warning);
		}
		return new MdmExitMessage(0);
	}

	public StatusTuple status(MdmModule module) {
		StatusTuple s = new StatusTuple();

		if (!module.getHandle().equals(module.getPath()))
			s.errors.add("Handle and path are not the same.  This is very strange and may cause issues with other git tools.");
		if (module.getUrlHistoric() == null)
			s.errors.add("Incomplete configuration: No url for remote repo is set in gitmodules.");
		if (module.getIndexId() == null)
			s.errors.add("Incomplete configuration: No committed gitlink in history matches this gitmodules entry.");	// we could let `mdm update` mention these as well and accept an argument to pull and commit the named versions.

		if (module.getType() == MdmModuleType.DEPENDENCY) {
			MdmModuleDependency dependency = (MdmModuleDependency)module;
			if (dependency.getVersionName() == null)
				s.errors.add("Version name not specified in gitmodules file!");
			if (dependency.getHeadId() == null) {
				s.version = "-- uninitialized --";
			} else {
				s.version = (dependency.getVersionActual() == null) ? "__UNKNOWN_VERSION__" : dependency.getVersionActual();
				if (dependency.getVersionName() != null && !dependency.getVersionName().equals(dependency.getVersionActual()))
					s.warnings.add("intended version is "+dependency.getVersionName()+", run `mdm update` to get it");
				if (!dependency.getIndexId().equals(dependency.getHeadId()))
					s.warnings.add("commit currently checked out does not match hash in parent project");
				if (dependency.hasDirtyFiles())
					s.warnings.add("there are uncommitted changes in this submodule");
			}
		}
		return s;
	}

	public class StatusTuple {
		public String version;
		/** Major notifications about the state of a module -- things that mean your build probably won't work. */
		public List<String> warnings = new ArrayList<String>();
		/** Errors so bad that we can't even tell what's supposed to be going on with this module. */
		public List<String> errors = new ArrayList<String>();
	}
}
