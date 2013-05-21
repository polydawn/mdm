package us.exultant.mdm.errors;

import org.eclipse.jgit.api.errors.*;

public class MdmConcurrentException extends MdmRuntimeException {
	/**
	 * Throw using this constructor if a {@link ConcurrentRefUpdateException} is
	 * thrown on a repo we just created or have already inspected.
	 */
	public MdmConcurrentException(ConcurrentRefUpdateException e) {
		super("mdm failed because a ref was changed in a repository as we were in the middle of working", e);
	}

	/**
	 * Wrap {@link MdmRepositoryStateException} in this exception if the problem was
	 * caused from a repo we just created or have already inspected.
	 */
	public MdmConcurrentException(MdmRepositoryStateException e) {
		super(e.getMessage()+"\nthis should not normally occur.  other processes may have been modifying the repo concurrently.", e);
	}
}
