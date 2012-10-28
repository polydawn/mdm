
from mdm.imp import *;

def status(args):
	# check we're in a repo somewhere.
	if (not cgw.cwdIsInRepo()):
		return exitStatus(":(", "this command should be run from within a git repo.");
	
	
	# load config for all mdm dependencies
	submodules = mdm.plumbing.getMdmSubmodules("dependency");
	
	
	# build a dict of all the key data points (stringify later, because we can transform this to either a machine-optimized or human-optimized format).
	submodules = _statusData(submodules);
	
	
	# generate a big string o' data
	if (submodules is None):
		return " --- no managed dependencies --- ";
	width1 = str(chooseFieldWidth(cons(submodules.keys(),"dependency:")));
	v  = ("%-"+width1+"s   \t %s\n") % ("dependency:", "version:");
	v += ("%-"+width1+"s   \t %s\n") % ("-----------", "--------");
	for modname, attribs in submodules.items():
		# choose our words for this row
		warnings = [];
		if (attribs['isCheckedOut'] is True):
			version = attribs['branchActual'];
			if (attribs['branchActual'] != attribs['mdm-version']):
				warnings.append("intended version is "+attribs['mdm-version']+", run `mdm update` to get it");
			if (attribs['isAtLinkedCommit'] is False):
				warnings.append("commit currently checked out does not match hash in parent project");
			if (attribs['isAtLinkedCommit'] is True and attribs['isContorted'] is True):
				warnings.append("there are uncommitted changes in this submodule");
		else:
			version =  "-- uninitialized --";
		
		# finally start stacking up some dang characters
		v += ("  %-"+width1+"s \t   %s\n") % (modname, version);
		for warn in warnings:
			v += ("  %-"+width1+"s \t   %s\n") % ("", "  !! "+warn);
	
	return v[:-1];



def _statusData(submodules):
	if (submodules is None):
		return None;
	if (len(submodules) is 0):
		return None;
	
	# load up the parent repo's submodules status info; it'll be looked up in the loop momentarily
	submStatus = cgw.getSubmodules();
	
	# ask the parent repo the same question in a different way to see if there's any untracked changes in any submodules.  (this does miss out on .gitignore'd things, but if your project is depending on the state of things in a gitignore file, then you've got bigger problems.)
	git.status("--porcelain", "-z", "--ignore-submodule=none", "--", map(lambda row: row[1]['path'], submodules.items()));
	#TODO: parse this and use it to answer part 2 of the isContorted question down in the loop
	
	# go across every mdm dependency submodule and parse all the data into salient info
	retreat = os.getcwd();
	for modname, attribs in submodules.items():
		# initialize default values for every trait we're about to consider
		attribs['isCheckedOut'] = False;
		attribs['isAtLinkedCommit'] = False;
		attribs['isContorted'] = False;					# 'contorted' means there's untracked content there, or a merge conflict, or anything at all where there's files there, but they're not pegged down.
		attribs['branchActual'] = None;
		
		# is there anything at this path at all, much less a repo?
		if (not path.lexists(attribs['path'])):
			continue;
		attribs['isCheckedOut'] = True;
		
		# what does parent repo think of the commit?
		if (submStatus[modname] == " "):				# the submodule is initialized in .git/config, and why yes indeedy it has exactly the commit checked out that the parent wants it pointing to.  huzzah!
			attribs['isAtLinkedCommit'] = True;
		elif (submStatus[modname] == "-"):				# .git/config is not initialized
			#XXX: i'm not entirely sure how much we care about this...?
			attribs['isCheckedOut'] = False;
			continue;
		elif (submStatus[modname] == "+"):				# submodule repo on the filesystem has a different commit hash checked out than index intentions
			attribs['isAtLinkedCommit'] = False;
		else:								# what?  could be a merge conflicted submodule or something, but you shouldn't have that with mdm dependency submodules unless you were doing something manual that was asking for trouble.
			attribs['isContorted'] = True;
		
		# what version are we actually on?  by branch
		#TODO: not actually clear if we should use the methodology of looking at the current branch or not.  `mdm` itself will always checkout the submodule by branch name, but it's not "wrong" per se to check out the same commit by hash name and have a detatched head state.  dealing with tags may also prove simpler and more stable, since those never get mussed around with any "origin/blahblah" prefixes.
		#TODO: `git describe` takes waaaaaaay too many liberties.  another alternative would be preferable.
		try:
			cd(attribs['path']);
			attribs['branchActual'] = str(git.describe("--tags")).strip().split("/");
			if (attribs['branchActual'][0] == "release"):		# if it's a release tag, parse out just the version name itself.
				attribs['branchActual'] = attribs['branchActual'][1:];
				attribs['branchActual'] = "/".join(attribs['branchActual']);
			else:							# ain't one of ours.
				attribs['branchActual'] = None;
		except ErrorReturnCode, e: pass;	# leave attribs['branchActual'] = None.
		finally: cd(retreat);
	
	return submodules;


