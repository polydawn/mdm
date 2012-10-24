
def mdaa(dct, tup, val):
	""" merge a value into a multidimentional ragged array.  I feel like this is something there certainly ought to be a more pythonic convenient syntax for, but if so I haven't found it yet. """
	for k in tup[:-1]:
		if (not k in dct):
			dct[k] = {};
		dct = dct[k];
	dct[tup[-1]] = val;


