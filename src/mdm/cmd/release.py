
from mdm.imp import *;

def release(args):
	retreat = os.getcwd();			# mind you that this command can be run from anywhere; it is not limited to the root of your project repo (though that's probably where I would almost always do it from).
	snapdir = args.repo+"/"+args.version;	# args.repo may have been either a relative or absolute path... dodge the issue by always cd'ing back to retreat before cd'ing to this.
	
	# sanity check the releases-repo
	if (not cgw.isRepoRoot(args.repo)):	# check that releases-repo is already a git repo
		return exitStatus(":(", "releases-repo directory '"+args.repo+"' doesn't look like a git repo!  (Maybe you forgot to set up with `mdm release-init` before making your first release?)");
	cd(args.repo);				# enter releases-repo
	git.checkout("master");			# history of the releases-repo is supposed to be linear.  things get confusing to push if they're not, and in particular we want to make sure that if there's currently a detatched head because of submodule updating leaving the releases repo in that state, we don't start racking up commits in that unpleasant void.
	try:					# check that nothing is already in the place where this version will be placed
		ls(args.version);
		return exitStatus(":(", "something already exists at '"+snapdir+"' !  Can't release there.");
	except ErrorReturnCode_2:
		pass;	#good
	
	
	# select the artifact files that we'll be copying in
	cd(retreat);				# back out to the dir we were run from before going any further, in case the arguments used any relative paths.  that's by far the least confusing behavior.
	if (path.isfile(args.files)):		# if it's a file, we take it literally.
		glom = args.files;
	else:
		if (path.isdir(args.files)):	# if it's a dir, we glob everything within it (we don't really want to match dotfiles on the off chance someone tries to consider their entire repo to be snapshot-worthy, because then we'd grab the .git files, and that would be a mess).
			glom = glob(args.files+"/*");
		else:				# if it wasn't anything we can take literally, we'll just toss it to glob() directly and see what it can do with it.
			glom = glob(args.files);
		if (not glom):
			return exitStatus(":(", "no files were found at "+args.files+"\nrelease aborted.");
	
	# make the snapshot-repo
	git.init(snapdir);						# create new snapshot-repo inside the releases-repo
	try:								# if anything fails in building, we want to destroy the snapshot area so it's not a mess the next time we try to do a release.
		cp("-nr", glom, snapdir);				# copy, and recursively if applicable.  also, no-clobber mode because we'd hate to ever accidentally overwrite the .git structures.
	except Exception, e:
		cd(args.repo); rm("-rf", args.version);
		return exitStatus(":'(", "error during build: "+str(e));
	
	# commit the snapshot-repo
	cd(snapdir);
	git.add(".");							# add the artifacts to snapshot-repo
	convenv = {							# set up an env vars that will make the single commit in the snapshot-repo have uniform blob headers... this means if more than one person ever for example makes their own releases repo from artifacts they got somewhere else, their snapshots will actually converge to the same hash!
		    'GIT_AUTHOR_NAME' : "mdm",				 'GIT_COMMITTER_NAME' : "mdm",
		   'GIT_AUTHOR_EMAIL' : "",				'GIT_COMMITTER_EMAIL' : "",
		    'GIT_AUTHOR_DATE' : "Jan 01 1970 00:00 -0000",	 'GIT_COMMITTER_DATE' : "Jan 01 1970 00:00 -0000",
	}
	git.commit("-m","release snapshot version "+args.version, _env=convenv);	# commit the artifacts to snapshot-repo
	git.tag("release/"+args.version);				# tag the snapshot commit in this snapshot-repo		#TODO: there should be a signing option here.
	cd("..");							# back out to the releases-repo
	
	# clone the snapshot-repo data into the releases-repo in a way that can be added and commited
	git.clone(args.version, "--bare", args.version+".git");		# clone a bare mirror of the snapshot-repo
	rm("-r", args.version+".git/hooks");				# remove files from the bare snapshot-repo that are junk
	rm("-r", args.version+".git/info/exclude");			# remove files from the bare snapshot-repo that are junk
	rm("-r", args.version+".git/description");			# remove files from the bare snapshot-repo that are junk
	git.config("-f", args.version+".git/config", "--remove-section", "remote.origin");	# remove files from the bare snapshot-repo that are junk
	with open(args.version+".git/refs/heads/.gitignore", 'w') as f: f.write("");	# you don't wanna know.
	cd(args.version+".git");					# cd into the snapshot-repo so we can compact it
	git.gc("--aggressive");						# compact the snapshot-repo as hard as possible.  this also incidentally performs `git update-server-info`, which is necessary for raw/dumb http cloning to work.
	cd("..");							# back out to the releases-repo
	git.add(args.version+".git");					# add the raw data of the bare snapshot-repo to the releases-repo
	
	# add the snapshot-repo as a submodule to the releases-repo (this is part of how clients are later able to retrieve the list of available versions remotely)
	try: git.config("--get", "remote.origin.url");			# check the state of this repo for a remote origin.  trying to add a submodule with a relative repository url (as we're about to) will fail if that's not set.
	except ErrorReturnCode: git.config("--add", "remote.origin.url", ".");		#TODO: consider updating: this restriction actually appears to have been considered a bug in git, as it was removed in git v1.7.6.1.  mdm may want to remove this workaround behavior in the future.
	git.submodule("add", "./"+args.version+".git");			# add the new snapshot-repo as a submodule; this puts it in the submodules file so it's easily readable over plain http without cloning the whole releases repo (it also puts it in the index of the releases repo again, but that's fairly redundant and not the point).
	git.config("-f", ".gitmodules", "submodule."+args.version+".mdm", "release-snapshot");	# put a marker in the submodules config that this submodule is a release-snapshot managed by mdm.
	git.add(".gitmodules");						# and stage that version_manifest change we just made
	
	# commit the releases-repo changes
	git.commit("-m","release version "+args.version);		# commit the raw data of the bare snapshot-repo to the releases-repo
	git.tag("release/"+args.version);				# tag the snapshot commit in the releases-repo		#TODO: there should be a signing option here.
	
	# commit the new hash of the releases-repo into the project main repo (if we are operating in a canonically placed releases submodule)
	if (args.repo == "releases"):
		cd("..");						# back out to the proj-repo
		if (cgw.isRepoRoot(".") and cgw.isSubmodule("releases")):	# okay, it really does look like a canonically placed releases submodule.
			git.commit("releases","-m","release version "+args.version);
			git.tag("release/"+args.version);
	
	# we could push all the things...
	#  and that's tempting, because if someone pushes their project without pushing the releases submodule first, they will piss other people off.
	#  however, pushing makes things fairly unreversable; everything we've done so far is still local, and if something went fubar it's just one commit and one commit to reset.
	#  So, we're not going to push.  we're going to let the user get off and look around.
	#  (Perhaps we'll add switches for this later, though.)
	
	return exitStatus(":D", "release version "+args.version+" complete");


