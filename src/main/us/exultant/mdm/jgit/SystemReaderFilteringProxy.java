package us.exultant.mdm.jgit;

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
