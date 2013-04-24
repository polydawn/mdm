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
