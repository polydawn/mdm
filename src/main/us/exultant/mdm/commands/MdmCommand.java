package us.exultant.mdm.commands;

import java.util.concurrent.*;
import org.eclipse.jgit.lib.*;

public abstract class MdmCommand<T> implements Callable<T> {
	/** The repository this command is working with */
	final protected Repository repo;

	protected MdmCommand(Repository repo) {
		this.repo = repo;
	}

	public Repository getRepository() {
		return repo;
	}
}
