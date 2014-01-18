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

package us.exultant.mdm;

import java.io.*;
import java.util.*;

public class MdmExitMessage extends RuntimeException {
	public MdmExitMessage(String happy, String message) {
		super(message, null);
		this.happy = happy;
		this.code = happyref.containsKey(happy) ? happyref.get(happy) : 128;
	}

	public MdmExitMessage(int code, String message) {
		super(message, null);
		this.happy = null;
		this.code = code;
	}

	public MdmExitMessage(int code) {
		super(null, null);
		this.happy = null;
		this.code = code;
	}

	private static final Map<String,Integer> happyref = new HashMap<String,Integer>() {{
			put( ":D", 0);	/* happy face is appropriate for great success */
				/* 1 is a catchall for general/unexpected errors. */
				/* 2 is for "misuse of shell builtins" in Bash. */
			put( ":(", 3);	/* sadness is appropriate for a misconfigured project or bad args or something */
			put(":'(", 4);	/* tears is appropriate for major exceptions or subprocesses not doing well */
			put( ":I", 0);	/* cramped face is appropriate for when we sat on our hands because the request was awkward but the goal state is satisfied anyway */
	}};
	public final String happy;
	public final int code;

	public void print(PrintStream os) {
		if (getMessage() != null) os.println(getMessage());
		if (happy != null) os.println(happy);
	}

	public void exit() {
		System.exit(code);
	}
}
