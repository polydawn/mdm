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
	 * Throw using this constructor if a {@link NoHeadException} is thrown on a repo
	 * we just created or have already inspected. (If this was an existing repository,
	 * wrapping the {@link NoHeadException} in a {@link MdmRepositoryStateException}
	 * instead is probably more appropriate.)
	 */
	public MdmConcurrentException(NoHeadException e) {
		super("mdm failed because a ref was changed in a repository as we were in the middle of working", e);
	}
}
