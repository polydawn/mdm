
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
		if (status == "-"):			# .git/config is not initialized
			git.submodule("init", subm);		# so initialize it.		#XXX: if something is initialized, but the update was superfluous, should that count as impacted?  I lean towards no, but it's also not a situation that should come up enough to be worth fretting over.
			git.submodule("update", subm);		# and update (clone) it.
		elif (status == "+"):			# submodule repo on the filesystem has a different commit hash checked out than index intentions
			# try to update it inplace (this will probably fail, but if someone did something clever with branches by all means let them enjoy it)
			#XXX later
			
			# read urls currently in .git/config, so that if someone repointed their repo to know about a more local copy, we can alter the prefix of what we're about to install to try to respect that.
			#XXX later
			
			# clear out the files and .git/config and reinit.  if we don't do the clear before updating, `git submodule init` doesn't change the url, and the url still pointing to the old version will cause `git submodule update` to clone it again but nothing will end up checked out and a hash error is reported.
			rm("-rf", subm);								# clear out the actual files
			rm("-rf", join(".git/modules",subm));						# if this is one of the newer version of git (specifically, 1.7.8 or newer) that stores the submodule's data in the parent projects .git dir, clear that out forcefully as well or else git does some very silly things (you end up with the url changed but it recreates the old files and doesn't change the object id like it should).
			try: git.config("-f", ".git/config", "--remove-section", "submodule."+subm);	# remove config lines for this submodule currently in .git/config.
			except: pass;									# errors because there was already no such config lines aren't really errors.
			git.submodule("init", subm);							# initialize again.
			
			# okay, now we can pull again
			git.submodule("update", subm);
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


