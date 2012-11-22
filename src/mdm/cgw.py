
""" Cheesy Git Wrapper """

from mdm.imp import *;



def isRepoRoot(dirname):
	"""
	Returns True if the given dirname is the root directory of a git repo, False otherwise.
	"""
	if (not path.isdir(dirname)):
		return False;
	retreat = os.getcwd();
	cd(dirname);
	try:
		return git("rev-parse", "--show-toplevel") == pwd("-P");
	except:
		return False;
	finally:
		cd(retreat);



def cwdIsInRepo():
	try: git("rev-parse"); return True;
	except: return False;



def isRepo(url, ref="refs/heads/master"):
	"""
	Unlike isRepoRoot(dirname), this function works for remote urls (!).
	
	Checks validity by looking for a ref string (by default, the usual name for the master branch), since I've seen urls that `git ls-remote` will not tell you is not a git repo, but will return many lines of nonsense.  
	This same functionality can also be used if you expect a certain tag to be in a repository, since then it can be a preliminary check that you're looking at the repo you want as opposed to just any old random repo.
	"""
	try:
		return len(str(git("ls-remote", url, ref))) > 0;
	except ErrorReturnCode:
		return False;



def isSubmodule(path):
	"""
	Test if the given path is a submodule root for the git repo that the cwd is currently within.
	"""
	retreat = os.getcwd();
	try :
		cd(git("rev-parse", "--show-toplevel").strip());
		submodstr = git.submodule("status", path);
		if (len(submodstr) == 0): return False;
		submodstr = submodstr.split("\n")[0].split(" ");
		return path == submodstr[2 if (submodstr[0] == "") else 1];
	finally:
		cd(retreat);



def getSubmodules():
	"""
	Reports an dict: keys in the returned dict are submodule names, and values are a string describing the status.
	Uses the first git repo up from the cwd.
	
	Note that there's several possible and not necessarily consistent ways to define what's a git submodule.
	This function reads the git object store for a gitlink (a pointer to git commit hash in another tree).
	One could also define git submodules of a repo by the information in the .git/config file, or in the .gitmodules file.
	"""
	retreat = os.getcwd();
	try :
		cd(git("rev-parse", "--show-toplevel").strip());
		subs = {};
		submodstr = git.submodule("status");
		if (len(submodstr) == 0): return subs;
		for submline in submodstr.split("\n"):
			if (not submline): continue;
			frags = submline.split(" ");
			if (frags[0] == ""):
				subs[frags[2]] = " ";
			else:
				subs[frags[1]] = frags[0][0];
		return subs;
	finally:
		cd(retreat);



def getConfig(filename):
	"""
	Return a ragged multidimentional array of configuration values from the given file.  Matching the forms expressable by git config files, the key depth is either two or three.
	
	Note about possible ambiguities in parsing this: dots in the final of the three keys are not allowed by git.  Dots in the first and second keys ARE allowed (yes, both of them), so I don't know how to parse that correctly from the output of this command (though it's possible from the file itself of course).  I'm just going to assume that there are no dots in the first key; if there are, you're weird and a bastard.
	"""
	try:
		if (isinstance(filename, tuple)):	# this is the (admittedly awkward and terrible) way I chose to indicate that I want a string passed as a file via a /dev/fd/0 hack.  I opened an issue about named pipes upstream in pbs; hopefully a better way to address this comes from there and I'll be able to throw this away.
			gmlines = str(git.config("-f", "/dev/fd/0", "-lz", _in=filename[0]));
		else:					# it's a normal filename, chill out.
			gmlines = str(git.config("-f", filename, "-lz"));
	except ErrorReturnCode:
		return None;
	v = {};
	for line in gmlines.split("\0"):
		if (not line): continue;
		keys, value = tuple(line.split("\n", 1));
		d1 = keys.index(".");
		d2 = keys.rindex(".");
		key1 = keys[:d1];
		if (d1 == d2):
			key2 = keys[d1+1:];
			mdaa(v, (key1, key2), value);
		else:
			key2 = keys[d1+1:d2];
			key3 = keys[d2+1:];
			mdaa(v, (key1, key2, key3), value);
	return v;


