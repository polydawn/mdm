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

package us.exultant.mdm;

import java.io.*;
import java.util.*;
import us.exultant.ahs.util.*;

public class Loco {
	public static List<String> toHandles(List<MdmModule> modules) {
		List<String> v = new ArrayList<>(modules.size());
		for (MdmModule module : modules)
			v.add(module.getHandle());
		return v;
	}

	public static String inputPrompt(PrintStream output, String prompt) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		output.print(prompt+" ");
		String answer = br.readLine();
		if (answer == null) throw new IOException("failed to read line from stdin");
		return answer;
	}

	public static String promptForVersion(PrintStream os, List<String> knownVersions) throws IOException {
		os.println("available versions:");
		os.println(Strings.join(knownVersions, "\n\t", "\t", ""));
		String version = null;
		while (version == null) {
			version = inputPrompt(os, "select a version: ");
			if (!knownVersions.contains(version)) {
				os.println("\""+version+"\" is not in the list of available versions; double check your typing.");
				version = null;
			}
		}
		return version;
	}

}
