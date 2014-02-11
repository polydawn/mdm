package net.polydawn.mdm.test;

import java.io.*;

public class WithCwd implements Closeable /*, Autocloseable */ {
	// if I ever put down the torch on backcompat with java6, this will immediately begin using the try-with-resources feature.
	// lambda-style behavior for scope would also work, but i'm just... my fingers are too tired to do that until java 8 hurries up and gets here.

	public WithCwd(String relPath) {
		this(new File(relPath));
	}

	public WithCwd(File relPath) {
		popDir = new File(System.getProperties().getProperty("user.dir"));
		pushedDir = relPath.isAbsolute() ? relPath : new File(popDir, relPath.toString());
		cd(pushedDir);
	}

	final File pushedDir;
	final File popDir;

	private void cd(File dir) {
		System.getProperties().setProperty("user.dir", dir.toString());
	}

	public void close() throws IOException {
		cd(popDir);
	}
}
