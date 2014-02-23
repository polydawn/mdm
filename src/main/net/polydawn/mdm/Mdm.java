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

package net.polydawn.mdm;

import java.io.*;
import java.util.*;
import net.polydawn.mdm.commands.*;
import net.polydawn.mdm.errors.*;
import net.polydawn.mdm.jgit.*;
import net.sourceforge.argparse4j.inf.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import us.exultant.ahs.iob.*;
import us.exultant.ahs.util.*;

public class Mdm {
	static {
		// apply fixes for questionable jgit behavior
		SystemReaderFilteringProxy.apply();
	}

	public static void main(String[] args) {
		MdmExitMessage answer = main(args, null);
		answer.print(System.err);
		answer.exit();
	}

	public static MdmExitMessage main(String[] args, Repository repo) {
		ArgumentParser parser = new MdmArgumentParser().parser;

		if (repo == null) try {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();
			builder.findGitDir();
			if (builder.getGitDir() != null)
				repo = builder.build();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		Namespace parsedArgs = null;
		try {
			parsedArgs = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
		try {
			MdmCommand cmd = getCommand(parsedArgs.getString("subcommand"), repo, parsedArgs);
			cmd.parse(parsedArgs);
			cmd.validate();
			return cmd.call();
		} catch (MdmExitMessage e) {
			return e;
		} catch (MdmRuntimeException e) {
			return new MdmExitMessage(":'(", e.getMessage());
		} catch (MdmException e) {
			return new MdmExitMessage(":'(", e.getMessage());
		} catch (MdmUnrecognizedError e) {
			return dealUnexpected(e);
		} catch (Exception e) {
			return dealUnexpected(e);
		}
	}

	public static MdmCommand getCommand(String name, final Repository repo, final Namespace args) {
		return new HashMap<String,MdmCommand>() {{
			put("status",           new MdmStatusCommand(repo, System.out));
			put("update",           new MdmUpdateCommand(repo));
			put("add",              new MdmAddCommand(repo));
			put("alter",            new MdmAlterCommand(repo, args));
			put("remove",           new MdmRemoveCommand(repo, args));
			put("release",          new MdmReleaseCommand(repo));
			put("release-init",     new MdmReleaseInitCommand(repo));
		}}.get(name);
	}

	@SuppressWarnings("finally")
	private static MdmExitMessage dealUnexpected(Throwable e) {
		File stackSave = null;
		try {
			stackSave = saveStackDump(e);
		} finally {
			return new MdmExitMessage(":'(",
					"An unexpected error occurred!  please file a bug report to help fix the problem."
					+"\na stack trace "
					+(stackSave == null ? "follows" : "has been saved to "+stackSave)
					+"; please include it in the report."
					+(stackSave == null ? "\n\n"+X.toString(e) : "")
			);
		}
	}

	private static File saveStackDump(Throwable e) throws IOException {
		File f = new File("mdm-error-"+UUID.randomUUID().toString()+".log");
		IOForge.saveFile(X.toString(e), f);
		return f;
	}
}
