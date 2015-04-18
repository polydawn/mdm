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
import org.eclipse.jgit.storage.file.FileBasedConfig;

import us.exultant.ahs.util.*;

public class MdmStatusCommand extends MdmCommand {
	public MdmStatusCommand(Repository repo, PrintStream os) {
		super(repo, os);
	}

	public MdmStatusCommand(Repository repo) {
		super(repo);
	}

	private String depName;
	private String formatName;

	public void parse(Namespace args) {
		this.depName = args.getString("name");
		this.formatName = args.getString("format");
	}

	private Formatter formatter;

	private static Map<String,Formatter> formatters = new HashMap<String,Formatter>() {{
		put("default", new FormatDefault());
		put("versionCheckedOut", new FormatVersionCheckedOut());
		put("versionSpecified", new FormatVersionSpecified());
	}};

	public void validate() throws MdmExitMessage {
		if (this.formatName == null) {
			this.formatter = new FormatDefault();
		} else {
			this.formatter = formatters.get(formatName);
			if (this.formatter == null) {
				throw new MdmExitMessage(":(", "invalid argument: no formatter of name \""+this.formatName+"\"");
			}
		}
	}

	public MdmExitMessage call() throws IOException {
		try {
			assertInRepo();
		} catch (MdmExitMessage e) {
			return e;
		}

		if (depName != null) {
			// if a specific dep name was specified, just load that one individually
			StoredConfig gitmodulesCfg = new FileBasedConfig(new File(repo.getWorkTree(), Constants.DOT_GIT_MODULES), repo.getFS());
			try {
				gitmodulesCfg.load();
			} catch (ConfigInvalidException e) {
				throw new MdmExitInvalidConfig(Constants.DOT_GIT_MODULES);
			}

			MdmModuleDependency module = MdmModuleDependency.load(repo, depName, gitmodulesCfg);
			this.formatter.fprintf(os, Arrays.asList(module));

			return new MdmExitMessage(0);
		} else {
			// scan all modules and do a report on all of them
			MdmModuleSet moduleSet;
			try {
				moduleSet = new MdmModuleSet(repo);
			} catch (ConfigInvalidException e) {
				throw new MdmExitInvalidConfig(Constants.DOT_GIT_MODULES);
			}
			Map<String,MdmModuleDependency> modules = moduleSet.getDependencyModules();

			this.formatter.fprintf(os, modules.values());

			return new MdmExitMessage(0);
		}
	}

	private static StatusTuple status(MdmModule module) {
		StatusTuple s = new StatusTuple();

		if (!module.getHandle().equals(module.getPath()))
			s.errors.add("Handle and path are not the same.  This is very strange and may cause issues with other git tools.");
		if (module.getUrlHistoric() == null)
			s.errors.add("Incomplete configuration: No url for remote repo is set in gitmodules.");
		if (module.getIndexId() == null)
			s.errors.add("Incomplete configuration: No committed gitlink in history matches this gitmodules entry.");	// we could let `mdm update` mention these as well and accept an argument to pull and commit the named versions.

		if (module.getType() == MdmModuleType.DEPENDENCY) {
			MdmModuleDependency dependency = (MdmModuleDependency) module;
			if (dependency.getVersionName() == null)
				s.errors.add("Version name not specified in gitmodules file!");
			if (dependency.getHeadId() == null) {
				s.version = "-- uninitialized --";
			} else {
				s.version = (dependency.getVersionActual() == null) ? "__UNKNOWN_VERSION__" : dependency.getVersionActual();
				if (dependency.getVersionName() != null && !dependency.getVersionName().equals(dependency.getVersionActual()))
					s.warnings.add("intended version is " + dependency.getVersionName() + ", run `mdm update` to get it");
				if (!dependency.getIndexId().equals(dependency.getHeadId()))
					s.warnings.add("commit currently checked out does not match hash in parent project");
				if (dependency.hasDirtyFiles())
					s.warnings.add("there are uncommitted changes in this submodule");
			}
		}
		return s;
	}



	public static class StatusTuple {
		public String version;
		/**
		 * Major notifications about the state of a module -- things that mean
		 * your build probably won't work.
		 */
		public List<String> warnings = new ArrayList<String>();
		/**
		 * Errors so bad that we can't even tell what's supposed to be going on
		 * with this module.
		 */
		public List<String> errors = new ArrayList<String>();
	}



	interface Formatter {
		public void fprintf(PrintStream f, Collection<MdmModuleDependency> modules);
	}



	private static class FormatDefault implements Formatter {
		public void fprintf(PrintStream f, Collection<MdmModuleDependency> modules) {
			if (modules.size() == 0) {
				f.println(" --- no managed dependencies --- ");
				return;
			}

			Collection<String> row1 = new ArrayList<String>(modules.size()+1);
			row1.add("dependency:");
			for (MdmModuleDependency mod : modules) {
				row1.add(mod.getHandle());
			}
			int width1 = Strings.chooseFieldWidth(row1);

			f.printf("%-" + width1 + "s   \t %s\n", "dependency:", "version:");
			f.printf("%-" + width1 + "s   \t %s\n", "-----------", "--------");

			for (MdmModuleDependency mod : modules) {
				StatusTuple status = status(mod);
				f.printf("  %-" + width1 + "s \t   %s\n", mod.getHandle(), status.version);
				for (String warning : status.warnings)
					f.printf("  %-" + width1 + "s \t   %s\n", "", "  !! " + warning);
			}
		}
	}



	private static class FormatVersionCheckedOut implements Formatter {
		public void fprintf(PrintStream f, Collection<MdmModuleDependency> modules) {
			for (MdmModuleDependency mod : modules) {
				f.println(mod.getVersionActual());
			}
		}
	}



	private static class FormatVersionSpecified implements Formatter {
		public void fprintf(PrintStream f, Collection<MdmModuleDependency> modules) {
			for (MdmModuleDependency mod : modules) {
				f.println(mod.getVersionName());
			}
		}
	}
}
