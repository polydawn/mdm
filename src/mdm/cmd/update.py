
from mdm.imp import *;

def update(args):
	# check we're in a repo root.  `git submodule` insists that we must be at the top.
	if (not cgw.isRepoRoot(".")):
		return exitStatus(":(", "this command should be run from the top level folder of your git repo.");
	
	
	# load all of the submodules in the git index
	# load all the config that is for mdm dependencies from the submodules file
	# (we do both instead of just iterating on what we see in the submodules file because otherwise we'd need to write a check somewhere in the loop coming up that the submodule config actually has a matching data in the git object store as well; this way is the minimum forking.)
	submodules = cgw.getSubmodules();
	submConf = mdm.plumbing.getMdmSubmodules("dependency");
	
	
	# load all the mdm dependency submodules from gitmodules file, and only pay attention to the intersection of that and the git index
	# and then act on them based on the difference between the filesystem state and the git index intention
	impacted = [];
	unphased = 0;
	contorted = [];
	removed = [];
	for subm, status in submodules.items():
		if (not submConf): continue;		# this condition should really just go outside the loop
		if (not subm in submConf): continue;	# ignore things that don't look like mdm dependencies.
		if (status == " "):			# go ahead and ignore things where status indicates filesystem state already matches the index intention
			unphased += 1;
			continue;
		if (status == "-"):			# .git/config is not initialized, so initialize it.  then call out to the plumbing 'load' function.
			git.submodule("init", subm);
			mdm.plumbing.doDependencyLoad(submConf[subm]['path'], submConf[subm]['mdm-version'], submConf[subm]['url']);
		elif (status == "+"):			# submodule repo on the filesystem has a different commit hash checked out than index intentions.  call out to the plumbing 'load' function.
			mdm.plumbing.doDependencyLoad(submConf[subm]['path'], submConf[subm]['mdm-version']);
		else:					# what?  could be a merge conflicted submodule or something, but you shouldn't have that with mdm dependency submodules unless you were doing something manual that was asking for trouble.
			contorted.append(subm);
			continue;
		#XXX: we have no special detection or handling for when submodule deletes are pulled from upstream.  what you end up with after that is just untracked files.  that's a little suprising, in my mind, but it's not exactly wrong, either.
		impacted.append(subm);
	
	
	# that's all.  compose a status string.
	status  = str(len(impacted)) + " changed, ";
	status += str(unphased) + " unaffected";
	if (len(contorted) > 0):
		status += ", "+str(len(contorted))+" contorted";
	if (len(removed) > 0):
		status += ", "+str(len(removed))+" removed";
	status2 = "";
	if (len(impacted) > 0):
		status2 += "\n  changed: "+str(impacted);
	if (len(contorted) > 0):
		status2 += "\n  contorted: "+str(contorted);
	if (len(removed) > 0):
		status2 += "\n  removed: "+str(removed);
	
	
	return exitStatus(":D", "mdm dependencies have been updated ("+status+")"+status2);


