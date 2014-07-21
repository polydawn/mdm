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

package net.polydawn.mdm.jgit;

import java.io.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.submodule.*;
import org.eclipse.jgit.treewalk.*;

/**
 * <p>
 * Like {@link SubmoduleWalk}, but displays any git repos in the *working* tree, and does
 * not concern itself with gitlinks.
 * </p>
 *
 * <p>
 * This can be used for detecting things that need special handling (because git by
 * default often operates specially or very cautiously around other git repos), but should
 * be used conservatively because often walking the filesystem is noticably more expensive
 * than asking the index about something. (TODO: also seriously consider how this ought to
 * respond to gitignores, including the special flags in the index.)
 * </p>
 *
 * @author Eric Myhre <tt>hash@exultant.us</tt>
 *
 */
public class SubrepoWalk {

	public SubrepoWalk(Repository repo) throws IOException {
		walk = new TreeWalk(repo);
		walk.addTree(new FileTreeIterator(repo));
		walk.setRecursive(true);
	}

	private final TreeWalk walk;

	public String next() throws IOException {
		while (walk.next()) {
			// if one wanted to handle gitignores it would look something like this:
			// if (walk.getTree(0, DirCacheIterator.class) == null && f.isEntryIgnored()) {}

			// interestingly enough, a FileTreeIterator appears to consider something a gitlink
			//  if it so much as has a .git file or directory -- it doesn't have to be in the dircache.
			if (walk.getFileMode(0) == FileMode.GITLINK)
				return walk.getPathString();
		}
		return null;
	}
}
