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
			MdmModuleStatus status = mod.status();
			os.printf("  %-"+width1+"s \t   %s\n", mod.getHandle(), status.version);
			for (String warning : status.warnings)
				os.printf("  %-"+width1+"s \t   %s\n", "", "  !! "+warning);
		}
		return null;
	}
}
