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

package us.exultant.mdm.jgit;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;

public class FilteredConfig extends FileBasedConfig {
	// I really don't like that this extends FileBasedConfig, but SystemReader insists.
	public FilteredConfig(Config original, Config whitelist) {
		super(null, null);
		this.original = original;
		this.whitelist = whitelist;
		refilter(whitelist);
	}

	private Config original;
	private Config whitelist;

	protected void refilter(Config whitelist) {
		this.clear();

		for (String section : original.getSections()) {
			for (String subsection : original.getSubsections(section))
				for (String key : original.getNames(section, subsection))
					if (whitelist.getBoolean(section, subsection, key, false))
						this.setStringList(section, subsection, key, Arrays.asList(original.getStringList(section, subsection, key)));
			for (String key : original.getNames(section))
				if (whitelist.getBoolean(section, null, key, false))
					this.setStringList(section, null, key, Arrays.asList(original.getStringList(section, null, key)));
		}
	}

	@Override
	public void load() throws IOException, ConfigInvalidException {
		if (original instanceof FileBasedConfig) {
			((FileBasedConfig)original).load();
			refilter(whitelist);
		}
	}

	@Override
	public boolean isOutdated() {
		return false;
	}
}
