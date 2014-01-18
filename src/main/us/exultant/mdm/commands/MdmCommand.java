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

package us.exultant.mdm.commands;

import java.io.*;
import java.util.concurrent.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.lib.*;
import us.exultant.mdm.*;

public abstract class MdmCommand implements Callable<MdmExitMessage> {
	/** The repository this command is working with */
	final protected Repository repo;
	final protected Namespace args;
	final protected PrintStream os;

	protected MdmCommand(Repository repo) {
		this(repo, null, null);
	}

	protected MdmCommand(Repository repo, Namespace args) {
		this(repo, args, null);
	}

	protected MdmCommand(Repository repo, PrintStream os) {
		this(repo, null, os);
	}

	protected MdmCommand(Repository repo, Namespace args, PrintStream os) {
		this.repo = repo;
		this.args = args;
		this.os = (os == null) ? System.err : os;
	}

	public abstract void parse(Namespace args);
	public abstract void validate() throws MdmExitMessage;



	public Repository getRepository() {
		return repo;
	}

	/**
	 * Check we're in a repo.
	 */
	protected void assertInRepo() throws MdmExitMessage {
		if (repo == null)
			throw new MdmExitMessage(":(", "this command should be run from inside your git repo.");
	}

	protected boolean isInRepoRoot() {
		return (repo != null && repo.getWorkTree().equals(new File(System.getProperty("user.dir"))));
	}

	/**
	 * Check we're in a repo root. `git submodule` insists on similar behavior. This
	 * seems generally reasonable for several commands in order to avoid the
	 * possibilities for confusion regarding relative paths, since submodule names
	 * typically look quite exactly like relative paths.
	 */
	protected void assertInRepoRoot() throws MdmExitMessage {
		if (!isInRepoRoot())
			throw new MdmExitMessage(":(", "this command should be run from the top level folder of your git repo.");
	}
}
