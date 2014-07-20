package net.polydawn.mdm.commands;

import org.eclipse.jgit.lib.*;
import net.polydawn.mdm.*;
import net.sourceforge.argparse4j.inf.*;

public class MdmYankCommand extends MdmCommand {
	public MdmYankCommand(Repository repo) {
		super(repo);
	}

	public void parse(Namespace args) {}

	public void validate() throws MdmExitMessage {}

	public MdmExitMessage call() throws Exception {
		// 'yank' command could forcibly import and sync the gitlink hash for all versions named in .gitmodules.
		// this would be useful for starting a new project with a list of deps (copy&pasta gitmodules lines instead of scripting add commands), and also for resolving merge conflicts.

		// doing this well might be a fairly huge project, but would also likely result in
		// dramatically improving the composability of add/alter/remove/update, and probably fetch itself.

		// scripting *deletes* might be hard, because of that "dragging the world behind the config... that dissappeared" generic problem.
		// we have the option of looking at parent commits when doing a merge resolve, at least.  not sure if that works well in all scenarios, might need some logic to handle not mussing submodules that aren't mdm's to boss around.

		return null;
	}
}
