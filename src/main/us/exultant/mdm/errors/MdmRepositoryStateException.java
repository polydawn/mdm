/*
 * Copyright 2012, 2013 Eric Myhre <http://exultant.us>
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

package us.exultant.mdm.errors;

import org.eclipse.jgit.api.errors.*;
import us.exultant.mdm.*;

/**
 * A superclass-that-should-have-been for grouping several exceptions in the JGit API that
 * are all regarding operations that fail because the repository is in an invalid state.
 */
public class MdmRepositoryStateException extends MdmException {
	public MdmRepositoryStateException(String tryingTo, String path, UnmergedPathsException cause) {
		this(tryingTo, path, (Exception)cause);
	}

	public MdmRepositoryStateException(String tryingTo, String path, NoHeadException cause) {
		this(tryingTo, path, (Exception)cause);
	}

	public MdmRepositoryStateException(String tryingTo, String path, WrongRepositoryStateException cause) {
		this(tryingTo, path, (Exception)cause);
	}

	public MdmRepositoryStateException(String tryingTo, String path, RefAlreadyExistsException cause) {
		this(tryingTo, path, (Exception)cause);
	}

	public MdmRepositoryStateException(String tryingTo, String path, RefNotFoundException cause) {
		this(tryingTo, path, (Exception)cause);
	}

	public MdmRepositoryStateException(String tryingTo, String path, InvalidRemoteException cause) {
		this(tryingTo, path, (Exception)cause);
	}

	private MdmRepositoryStateException(String tryingTo, String path, Exception cause) {
		super("mdm failed while trying to "+tryingTo+" at "+path+"; "+cause.getMessage(), cause);
	}
}
