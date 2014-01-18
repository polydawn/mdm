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
/*
 * Contents of this file are derived from the Apache Commons IO library,
 * and since to the best of my understanding it is possible to relicense
 * Version 2.0 Apache Licensed content as GPL v3, I have done so in the interest
 * of maintaining a single license for the whole of mdm code.
 *
 * The only significant alteration is the addition of "preserveFileExecutablity"
 * support.
 */

package net.polydawn.mdm.util;

import java.io.*;
import java.nio.channels.*;
import java.util.*;

public class FileUtils {
	public static void copyDirectory(final File srcDir, final File destDir,
			final FileFilter filter, final boolean preserveFileExecutablity, final boolean preserveFileDate) throws IOException {
		if (srcDir == null) { throw new NullPointerException("Source must not be null"); }
		if (destDir == null) { throw new NullPointerException("Destination must not be null"); }
		if (srcDir.exists() == false) { throw new FileNotFoundException("Source '" + srcDir + "' does not exist"); }
		if (srcDir.isDirectory() == false) { throw new IOException("Source '" + srcDir + "' exists but is not a directory"); }
		if (srcDir.getCanonicalPath().equals(destDir.getCanonicalPath())) { throw new IOException("Source '" + srcDir + "' and destination '" + destDir + "' are the same"); }

		// Cater for destination being directory within the source directory (see IO-141)
		List<String> exclusionList = null;
		if (destDir.getCanonicalPath().startsWith(srcDir.getCanonicalPath())) {
			final File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
			if (srcFiles != null && srcFiles.length > 0) {
				exclusionList = new ArrayList<String>(srcFiles.length);
				for (final File srcFile : srcFiles) {
					final File copiedFile = new File(destDir, srcFile.getName());
					exclusionList.add(copiedFile.getCanonicalPath());
				}
			}
		}
		doCopyDirectory(srcDir, destDir, filter, preserveFileExecutablity, preserveFileDate, exclusionList);
	}

	public static void copyFile(final File srcFile, final File destFile,
			final boolean preserveFileExecutablity, final boolean preserveFileDate) throws IOException {
		if (srcFile == null) { throw new NullPointerException("Source must not be null"); }
		if (destFile == null) { throw new NullPointerException("Destination must not be null"); }
		if (srcFile.exists() == false) { throw new FileNotFoundException("Source '" + srcFile + "' does not exist"); }
		if (srcFile.isDirectory()) { throw new IOException("Source '" + srcFile + "' exists but is a directory"); }
		if (srcFile.getCanonicalPath().equals(destFile.getCanonicalPath())) { throw new IOException("Source '" + srcFile + "' and destination '" + destFile + "' are the same"); }
		final File parentFile = destFile.getParentFile();
		if (parentFile != null) {
			if (!parentFile.mkdirs() && !parentFile.isDirectory()) { throw new IOException("Destination '" + parentFile + "' directory cannot be created"); }
		}
		if (destFile.exists() && destFile.canWrite() == false) { throw new IOException("Destination '" + destFile + "' exists but is read-only"); }
		doCopyFile(srcFile, destFile, preserveFileExecutablity, preserveFileDate);
	}

	private static void doCopyDirectory(final File srcDir, final File destDir, final FileFilter filter,
			final boolean preserveFileExecutablity, final boolean preserveFileDate, final List<String> exclusionList) throws IOException {
		// recurse
		final File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
		if (srcFiles == null) {  // null if abstract pathname does not denote a directory, or if an I/O error occurs
			throw new IOException("Failed to list contents of " + srcDir);
		}
		if (destDir.exists()) {
			if (destDir.isDirectory() == false) { throw new IOException("Destination '" + destDir + "' exists but is not a directory"); }
		} else {
			if (!destDir.mkdirs() && !destDir.isDirectory()) { throw new IOException("Destination '" + destDir + "' directory cannot be created"); }
		}
		if (destDir.canWrite() == false) { throw new IOException("Destination '" + destDir + "' cannot be written to"); }
		for (final File srcFile : srcFiles) {
			final File dstFile = new File(destDir, srcFile.getName());
			if (exclusionList == null || !exclusionList.contains(srcFile.getCanonicalPath())) {
				if (srcFile.isDirectory()) {
					doCopyDirectory(srcFile, dstFile, filter, preserveFileExecutablity, preserveFileDate, exclusionList);
				} else {
					doCopyFile(srcFile, dstFile, preserveFileExecutablity, preserveFileDate);
				}
			}
		}

		// Do this last, as the above has probably affected directory metadata
		if (preserveFileDate) {
			destDir.setLastModified(srcDir.lastModified());
		}
	}

	public static final long ONE_KB = 1024;
	public static final long ONE_MB = ONE_KB * ONE_KB;
	private static final long FILE_COPY_BUFFER_SIZE = ONE_MB * 30;

	private static void doCopyFile(final File srcFile, final File destFile, final boolean preserveFileExecutablity, final boolean preserveFileDate) throws IOException {
		if (destFile.exists() && destFile.isDirectory()) { throw new IOException("Destination '" + destFile + "' exists but is a directory"); }

		FileInputStream fis = null;
		FileOutputStream fos = null;
		FileChannel input = null;
		FileChannel output = null;
		try {
			fis = new FileInputStream(srcFile);
			fos = new FileOutputStream(destFile);
			input = fis.getChannel();
			output = fos.getChannel();
			final long size = input.size();
			long pos = 0;
			long count = 0;
			while (pos < size) {
				count = size - pos > FILE_COPY_BUFFER_SIZE ? FILE_COPY_BUFFER_SIZE : size - pos;
				pos += output.transferFrom(input, pos, count);
			}
		} finally {
			closeQuietly(output);
			closeQuietly(fos);
			closeQuietly(input);
			closeQuietly(fis);
		}

		if (srcFile.length() != destFile.length()) { throw new IOException("Failed to copy full contents from '" +
				srcFile + "' to '" + destFile + "'"); }
		if (preserveFileExecutablity)
			destFile.setExecutable(srcFile.canExecute(), false);
		if (preserveFileDate) {
			destFile.setLastModified(srcFile.lastModified());
		}
	}

	public static void closeQuietly(final Closeable closeable) {
		try {
			if (closeable != null)
				closeable.close();
		} catch (final IOException e) {}
	}
}
