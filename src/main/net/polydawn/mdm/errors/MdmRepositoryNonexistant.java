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

package net.polydawn.mdm.errors;

import java.io.*;

//XXX: possible that this should be replaced by RepositoryNotFoundException from jgit
public class MdmRepositoryNonexistant extends MdmRepositoryIOException {
	public MdmRepositoryNonexistant(String path) {
		super("mdm couldn't see a repository at '"+path+"'");
	}

	public MdmRepositoryNonexistant(String path, IOException cause) {
		super("mdm couldn't see a repository at '"+path+"'", cause);
	}
}
