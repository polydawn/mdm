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
import java.util.concurrent.*;
import net.polydawn.mdm.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.lib.*;

public abstract class MdmCommand implements Callable<MdmExitMessage> {
	/**
	 * The repository this command is working with.
	 * <p>
	 * Or perhaps more accurately, a repository that was most immediately discovered
	 * to contain this command's working directory. May be null (for example
	 * {@link MdmReleaseInitCommand} clearly doesn't need an existing repository; and
	 * {@link MdmReleaseCommand} may behave slightly differently if in a repository,
	 * but is actually concerned with a repo that may be on a different path
	 * entirely).
	 */
	final protected Repository repo;

	/**
	 * @Deprecated we do not want to retain this pointer; convert to using
	 *             {@link #parse(Namespace)} and extracting values upfront instead.
	 */
	@Deprecated
	final protected Namespace args;

	final protected PrintStream os;

	protected MdmCommand(Repository repo) {
		this(repo, null, null);
	}

	/**
	 * @Deprecated the {@code Namespace args} argument should not be used; convert to
	 *             using {@link #parse(Namespace)} instead.
	 */
	@Deprecated
	protected MdmCommand(Repository repo, Namespace args) {
		this(repo, args, null);
	}

	protected MdmCommand(Repository repo, PrintStream os) {
		this(repo, null, os);
	}

	/**
	 * @Deprecated the {@code Namespace args} argument should not be used; convert to
	 *             using {@link #parse(Namespace)} instead.
	 */
	@Deprecated
	protected MdmCommand(Repository repo, Namespace args, PrintStream os) {
		this.repo = repo;
		this.args = args;
		this.os = (os == null) ? System.err : os;
	}

	/**
	 * Ask the command to set up its parameters by parsing the argument.
	 * <p>
	 * This method is the entry point from the main method, but subclasses expose
	 * their parameter fields as default visibility for test access, so tests need not
	 * construct a full args parsing system or use this method.
	 * <p>
	 * A {@link #parse(Namespace)} call does not imply a {@link #validate()} call; the
	 * caller is responsible for this.
	 *
	 * @param args
	 */
	// an MdmCommand is kind of its own config and parser factory.  this is messy.
	public abstract void parse(Namespace args);

	/**
	 * Apply this before invoking {@link #call()} to give the command implementation a
	 * chance to verify all its parameters are provided and valid.
	 *
	 * @throws MdmExitMessage
	 *                 for missing or invalid parameters.
	 */
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
