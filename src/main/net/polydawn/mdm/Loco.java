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

package net.polydawn.mdm;

import java.io.*;
import java.util.*;
import net.polydawn.mdm.errors.*;
import us.exultant.ahs.util.*;

public class Loco {
	public static List<String> toHandles(List<MdmModule> modules) {
		List<String> v = new ArrayList<String>(modules.size());
		for (MdmModule module : modules)
			v.add(module.getHandle());
		return v;
	}

	public static String inputPrompt(PrintStream output, String prompt) {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		output.print(prompt+" ");
		String answer;
		try {
			answer = br.readLine();
		} catch (IOException e) {
			throw new MdmInputUnavailableException("stdin unavailable", e);
		}
		if (answer == null) throw new MdmInputUnavailableException("stdin unavailable");
		output.println(); // add a little breathing room.
		return answer;
	}

	public static String promptForVersion(PrintStream os, List<String> knownVersions) {
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
