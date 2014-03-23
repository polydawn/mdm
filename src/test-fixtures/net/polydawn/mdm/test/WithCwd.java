package net.polydawn.mdm.test;

import java.io.*;
import java.util.*;
import us.exultant.ahs.iob.*;

public class WithCwd implements Closeable /*, Autocloseable */ {
	// if I ever put down the torch on backcompat with java6, this will immediately begin using the try-with-resources feature.
	// lambda-style behavior for scope would also work, but i'm just... my fingers are too tired to do that until java 8 hurries up and gets here.

	public WithCwd(String relPath) {
		this(new File(relPath));
	}

	public WithCwd(File relPath) {
		popDir = new File(System.getProperties().getProperty("user.dir"));
		pushedDir = relPath.isAbsolute() ? relPath : new File(popDir, relPath.toString());
		pushedDir.mkdirs();
		cd(pushedDir);
	}


	/**
	 * Creates a random temporary directory under {@link #tmp}.
	 */
	public static WithCwd temp() {
		try {
			return new WithCwd(createUniqueTestFolderPrefix().getCanonicalFile());
		} catch (IOException e) {
			throw new Error("cwd?", e);
		}
	}

	static File createUniqueTestFolderPrefix() {
		while (true) {
			File f = new File(tmp, "test-"+UUID.randomUUID().toString());
			if (f.mkdirs()) {
				tmpdirs.add(f);
				return f;
			}
		}
	}


	static final File tmp = new File(System.getProperty("java.io.tmpdir"), "mdm-test");
	private static final List<File> tmpdirs = new ArrayList<File>();
	private static final boolean keep = !System.getProperty("keep", "false").equals("false");
	static {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.gc();
				Iterator<File> itr = tmpdirs.iterator();
				while (itr.hasNext()) {
					delete(itr.next());
					itr.remove();
				}
			}
		});
	}

	final File pushedDir;
	final File popDir;

	private void cd(File dir) {
		System.getProperties().setProperty("user.dir", dir.toString());
	}

	public void close() {
		cd(popDir);
	}

	public void clear() {
		close();
		delete(pushedDir);
	}

	private static void delete(File f) {
		try {
			if (!keep) IOForge.delete(f);
		} catch (IOException e) {
			System.err.println("failed to delete tmpdir: "+e.getMessage());
		}
	}
}
