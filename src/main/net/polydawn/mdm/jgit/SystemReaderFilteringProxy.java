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

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import org.eclipse.jgit.util.*;

public class SystemReaderFilteringProxy extends SystemReader {
	public static void apply() {
		Config whitelist = new Config();
		whitelist.setBoolean("user", null, "name", true);
		whitelist.setBoolean("user", null, "email", true);
		SystemReader proxy = new SystemReaderFilteringProxy(SystemReader.getInstance(), whitelist);
		SystemReader.setInstance(proxy);
	}

	public SystemReaderFilteringProxy(SystemReader original, Config whitelist) {
		this.original = original;
		this.whitelist = whitelist;
	}

	private final SystemReader original;
	private final Config whitelist;

	public FileBasedConfig openUserConfig(Config parent, FS fs) {
		return new FilteredConfig(original.openUserConfig(parent, fs), whitelist);
	}

	public FileBasedConfig openSystemConfig(Config parent, FS fs) {
		return new FilteredConfig(original.openSystemConfig(parent, fs), whitelist);
	}

	//
	// all direct proxying here on down
	//

	public String getHostname() {
		return original.getHostname();
	}

	public String getenv(String variable) {
		return original.getenv(variable);
	}

	public String getProperty(String key) {
		return original.getProperty(key);
	}

	public long getCurrentTime() {
		return original.getCurrentTime();
	}

	public int getTimezone(long when) {
		return original.getTimezone(when);
	}
}
