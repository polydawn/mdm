
def mdaa(dct, tup, val):
	""" merge a value into a multidimentional ragged array.  I feel like this is something there certainly ought to be a more pythonic convenient syntax for, but if so I haven't found it yet. """
	for k in tup[:-1]:
		if (not k in dct):
			dct[k] = {};
		dct = dct[k];
	dct[tup[-1]] = val;



def cons(lst, moar):
	""" like list.append, but less annoying because it returns the same damn list instead of returning None. """
	lst.append(moar);
	return lst;



def chooseFieldWidth(samples, policy=8):
	"""
	Return the smallest number of characters that a printf should reserve when printing a table
	with a columnn containing the given sample of values, if the next field must start aligned 
	to the given 'policy' multiple of characters.
	"""
	width = 0;
	for val in samples:
		width = max(width, len(val));
	return (width/policy)*policy+policy;	# divide into blocks of size 'policy', then turn that back into characters and add one more block.



def exitStatus(happy, message):
	try:
		code = {
			 ":D": 0,	# happy face is appropriate for great success
			     # 1 is a catchall for general/unexpected errors.
			     # 2 is for "misuse of shell builtins" in Bash.
			 ":(": 3,	# sadness is appropriate for a misconfigured project or bad args or something
			":'(": 4,	# tears is appropriate for major exceptions or subprocesses not doing well
			 ":I": 0,	# cramped face is appropriate for when we sat on our hands because the request was awkward but the goal state is satisfied anyway
		}[happy];
	except: code = 128;
	return {'happy':happy, 'message':message, 'code':code};


