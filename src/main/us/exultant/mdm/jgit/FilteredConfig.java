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
		for (String section : original.getSections()) {
			for (String subsection : original.getSubsections(section))
				for (String key : original.getNames(section, subsection))
					if (whitelist.getBoolean(section, subsection, key, false))
						this.setStringList(section, subsection, key, Arrays.asList(original.getStringList(section, subsection, key)));
			for (String key : original.getNames(section))
				this.setStringList(section, null, key, Arrays.asList(original.getStringList(section, null, key)));
		}
	}

	@Override
	public void load() throws IOException, ConfigInvalidException {
		/* do nothing */
	}

	@Override
	public boolean isOutdated() {
		return false;
	}
}
