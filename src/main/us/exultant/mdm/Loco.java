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

import static java.lang.Math.max;
import java.util.*;

public class Loco {
	/** See {@link #chooseFieldWidth(Collection, int)} &mdash; this is exactly as calling that method with a policy of 8. */
	public static int chooseFieldWidth(Collection<String> samples) {
		return chooseFieldWidth(samples, 8);
	}

	/**
	 * Return the smallest number of characters that a printf should reserve when
	 * printing a table with a columnn containing the given sample of values, if the
	 * next field must start aligned to the given 'policy' multiple of characters.
	 */
	public static int chooseFieldWidth(Collection<String> samples, int policy) {
		int width = 0;
		for (String val : samples)
			width = max(width, val.length());
		// divide into blocks of size 'policy', then turn that back into characters and add one more block.
		return (width / policy) * policy + policy;
	}
}
