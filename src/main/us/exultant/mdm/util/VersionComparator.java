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

package us.exultant.mdm.util;

import java.util.*;
import java.util.regex.*;

/**
 * You'd wish googling for "java versionsort comparator implementation" you'd find some
 * fairly canonical implementation of this somewhere, wouldn't you? Noooo. So, here's a
 * quick one that mostly follows python distutil's LooseVersion.
 */
public class VersionComparator implements Comparator<String> {
	public int compare(String o1, String o2) {
		String[] a1 = split(COMPONENT_PATTERN,o1);
		String[] a2 = split(COMPONENT_PATTERN,o2);
		int m = Math.min(a1.length, a2.length);
		for (int i = 0; i < m; i++) {
			int c;
			try {
				c = Integer.parseInt(a1[i]) - Integer.parseInt(a2[i]);
			} catch (NumberFormatException _) {
				c = a1[i].compareTo(a2[i]);
			}
			if (c != 0)
				return c;
		}
		return a1.length - a2.length;
	}

	private static final Pattern COMPONENT_PATTERN = Pattern.compile("(\\d+|[A-Za-z]+|\\.)");

	/**
	 * Retains capture groups when splitting, like python re's split method.
	 * <pre>
	 * >>> re.compile(r'a(b*)c').split('abc')
	 * ['', 'b', '']
	 * >>> re.compile(r'a(b*)c').split('abcabc')
	 * ['', 'b', '', 'b', '']
	 * >>> re.compile(r'a(b*)c').split('abczabc')
	 * ['', 'b', 'z', 'b', '']
	 * >>> re.compile(r'a(b*)(c)').split('abczabc')
	 * ['', 'b', 'c', 'z', 'b', 'c', '']
	 * >>> re.compile(r'a(b*)(c)').split('abczac')
	 * ['', 'b', 'c', 'z', '', 'c', '']
	 * >>> re.compile(r'a(b*)(c)').split('abczacz')
	 * ['', 'b', 'c', 'z', '', 'c', 'z']
	 * </pre>
	 */
	static String[] split(Pattern p, CharSequence input) {
	        int index = 0;
	        List<String> matchList = new ArrayList<String>();
	        Matcher m = p.matcher(input);

	        while (m.find()) {
		        // Add segments before each match found
			String nonmatch = input.subSequence(index, m.start()).toString();
			matchList.add(nonmatch);
			index = m.end();

			// Add capture groups, if present
			for (int i = 1; i <= m.groupCount(); i++)
				matchList.add(m.group(i));
	        }

	        // If no match was found, return this
	        if (index == 0)
	            return new String[] {input.toString()};

	        // Add remaining segment
	        matchList.add(input.subSequence(index, input.length()).toString());

	        // Construct result
	        return matchList.toArray(new String[matchList.size()]);
	}
}
