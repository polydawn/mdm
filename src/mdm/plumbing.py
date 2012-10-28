
from mdm.imp import *;


def getMdmSubmodules(kind=None, name=None, gmFilename=None):
	"""
	Generate a dict of data about submodules listed in the .gitmodules file of the cwd's repo (or some other file if specified).
	Otherwise returns None if there are no submodules, or if a specific submodule is asked for and there's no data for a submodule by that name.
	
	For a submodule to be reported, there must be a field attached to that submodule keyed by "mdm"; this field is to report that this submodule is managed by mdm, and the value is what kind of submodule mdm considers it.
	
	Asking for a specific kind of submodule will return only submodules where the "mdm" key matches the requsted kind.
	
	One submodule may be asked for by name, in which case all the returned dict will be one level shallower since there's only one submodule worth of data to return;
	 otherwise the same conditions as above apply (i.e., if there is one submodule by that name, but it's either not labled an mdm module of any kind, or a specific kind was asked for and this isn't it, then None will be returned).
	
	If "gmFilename" argument is provided, the cgw.getConfig() function is used internally to resolve that, so all the same rules for that function's argument apply to the gmFilename argument.
	"""
	if (not gmFilename):
		try: gmFilename = git("rev-parse", "--show-toplevel").strip()+"/.gitmodules";
		except ErrorReturnCode: return None;
	dConf = cgw.getConfig(gmFilename);
	if (dConf is None): return None;
	if (not 'submodule' in dConf): return None;
	dSubm = dConf['submodule'];
	if (name):
		if (not name in dSubm): return None;
		subm = dSubm[name];
		if (not 'mdm' in subm): return None;
		if (kind and not subm['mdm'] == kind): return None;
		return subm;
	else:
		for submName, submDat in dSubm.items():
			if (not 'mdm' in submDat): del dSubm[submName]; continue;
			if (kind and not submDat['mdm'] == kind): del dSubm[submName]; continue;
		return dSubm;



def doDependencyAdd(name, url, version):
	git.submodule("add", join(url, version+".git"), name);				# add us a submodule for great good!
	git.submodule("init", name);							# i would've thought `git submodule add` would have already done this, but it seems sometimes it does not.  anyway, at worst, this is a redunant no-op.
	git.config("-f", ".gitmodules", "submodule."+name+".mdm", "dependency");	# put a marker in the submodules config that this submodule is a dependency managed by mdm.
	# git.config("-f", ".gitmodules", "submodule."+name+".mdm-version", version);	# we could add another marker to make the version name an explicit property, but what would be the point?  it wouldn't have any real binding to what objects are pointed at by the git index nor what's actually checked out.
	git.add(".gitmodules");								# have to `git add` the gitmodules file again since otherwise the marker we just appended doesn't get staged
	pass;



def doDependencyRemove(name):
	try: git.config("-f", ".gitmodules", "--remove-section", "submodule."+name);	# remove config lines for this submodule currently in gitmodules file.  also, note i'm assuming we're already at the pwd of the repo top here.
	except: pass;									# errors because there was already no such config lines aren't really errors.
	git.add(".gitmodules");								# stage the gitmodule file change into the index.
	git.rm("--cached", name);							# mark submodule for removal in the index.  have to use the cached option and rm-rf it ourselves or git has a beef, seems silly to me but eh.
	rm("-rf", name);								# clear out the actual files
	rm("-rf", join(".git/modules",name));						# if this is one of the newer version of git (specifically, 1.7.8 or newer) that stores the submodule's data in the parent projects .git dir, clear that out forcefully as well or else git does some very silly things (you end up with the url changed but it recreates the old files and doesn't change the object id like it should).
	try: git.config("-f", ".git/config", "--remove-section", "submodule."+name);	# remove conflig lines for this submodule currently in .git/config.	# environmental $GIT_DIR is not supported.	# i'm a little unhappy about doing this before trying to commit anything else for smooth error recovery reasons... but on the other hand, we want to use this function to compose with other things in the same commit, so.
	except: pass;									# errors because there was already no such config lines aren't really errors.
	pass;



def getVersionManifest(releasesUrl):
	# wield `git ls-remote` to get a list of branches matching the labelling pattern mdm releases use.  works local or remote over any transport git itself supports.
	try:
		return sorted(map(lambda x: x.split()[1][23:], git("ls-remote", releasesUrl, "-h", "refs/heads/mdm/release/*").split("\n")[:-1]), key=fn_version_sort);
	except:
		return None;



convenv = {	# set up an env var that will make a git commit have uniform blob headers... if the history graph leading up to this is also controlled and consistent, and two different people running different repos commit the same artifacts, then the commits will actually converge to the same hash.
		 'GIT_AUTHOR_NAME' : "mdm",				 'GIT_COMMITTER_NAME' : "mdm",
		'GIT_AUTHOR_EMAIL' : "",				'GIT_COMMITTER_EMAIL' : "",
		 'GIT_AUTHOR_DATE' : "Jan 01 1970 00:00 -0000",		 'GIT_COMMITTER_DATE' : "Jan 01 1970 00:00 -0000",
}


