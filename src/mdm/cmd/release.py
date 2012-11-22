
from mdm.imp import *;

def release(args):
	retreat = os.getcwd();			# mind you that this command can be run from anywhere; it is not limited to the root of your project repo (though that's probably where I would almost always do it from).
	snapdir = args.repo+"/"+args.version;	# args.repo may have been either a relative or absolute path... dodge the issue by always cd'ing back to retreat before cd'ing to this.
	
	# sanity check the releases-repo
	if (not cgw.isRepoRoot(args.repo)):					# check that releases-repo is a git repo at all
		return exitStatus(":(", "releases-repo directory '"+args.repo+"' doesn't look like a git repo!  (Maybe you forgot to set up with `mdm release-init` before making your first release?)");
	if (not cgw.isRepo(args.repo, ref="refs/heads/mdm/init")):		# check that the releases-repo has the branches we expect from an mdm releases repo
		return exitStatus(":'(", "releases-repo directory '"+args.repo+"' contains a git repo, but it doesn't look like something that's been set up for mdm releases.\n(There's no branch named \"mdm/init\", and there should be.)");
	if (not cgw.isRepo(args.repo, ref="refs/heads/master")):		# check that the releases-repo has the branches we expect from an mdm releases repo
		return exitStatus(":'(", "releases-repo directory '"+args.repo+"' contains a git repo, but it doesn't look like something that's been set up for mdm releases.\n(There's no branch named \"master\", and there should be.)");
	if (cgw.isRepo(args.repo, ref="refs/heads/mdm/release/"+args.version)):	# check that nothing is already in the place where this version will be placed
		return exitStatus(":'(", "the releases repo already has a release point labeled version "+args.version+" !");
	if ("/" in args.version):						# we could deal with these, certainly, but just... why?  even if mdm itself were to handle it smoothly, it would make life that much more annoying for any other scripts ever, and it makes the directory structure just a mess of irregular depth.
		return exitStatus(":(", "you can't use version names that have slashes in them, sorry.  it gets messy.");
	if (len(str(git("ls-tree", "master", "--name-only", args.version)))>0):	# make sure there's nothing in the version-named directory in master as well.  though really we're getting borderline facetious here: there's all sorts of problems that could well come up from having incomplete local state and then trying to push what turns out to be a coliding branch name, and so on.
		return exitStatus(":'(", "the releases repo already has files committed in the master branch where version "+args.version+" should go!");
	
	
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
	
	
	# create a branch for the release commit.  depending on whether or not infix mode is enabled, this is either branching from the infix branch, or it's founding a new root of history.
	cd(args.repo);
	infixMode = cgw.isRepo(".", ref="refs/heads/mdm/infix");
	if (infixMode):
		git.checkout("-b", "mdm/release/"+args.version, "mdm/infix");
	else:
		git.checkout("--orphan", "mdm/release/"+args.version);
		git.rm("-rf",".");
	
	
	# enumerate and copy in artifact files.
	unartifacts = os.listdir(".");
	try:
		cd(retreat);							# make sure we're back where the command started again, so any relative paths make sense
		cp("-nr", "--", glom, args.repo);				# copy, and recursively if applicable.  also, no-clobber mode because we'd hate to ever accidentally overwrite the .git structures.
		cd(args.repo);
	except Exception, e:							# if anything fails in building, we want to destroy any mess we left behind so it doesn't jam the works the next time we try to do a release.
		cd(args.repo);
		try: git.reset("--hard");
		except Exception, e: pass;					# we completely punt on errors during cleanup, because I can't imagine how to cleanup on the cleanup without making the final resting place of the whole thing just that much less transparent to the user.
		try: git.checkout("@{-1}"); git.branch("-D", "mdm/release/"+args.version);
		except Exception, e: pass;
		return exitStatus(":'(", "error during build: "+str(e));
	
	
	# remind me what we just did?  (we'll be referring to everything by its path in the repo from now on, so we can discard all path prefixes.)
	artifacts = filter(lambda x: not x in unartifacts, os.listdir("."));	# incidentally, note this means if your release artifacts went to paths that already had files from the mdm/infix branch, those are going to be ignored!  if you put a readme.txt in your mdm/infix branch, then you shouldn't put one in the release too; attempting to do so is unsupported.  other implementations were considered (namely, asking git to just add everything to the index, and then asking it again what that brings out), but these have their own potentially surprising behaviors, such as the `git mv` command coming up meaning that things added in teh mdm/init branch might appear to be merged into the master branch looking at the history graph after a release, but they wouldn't be at all where you'd expect them to be, and older version of the same files from the init branch might persist unchanged, and so on.  there's no unsurprising way to thread this needle.
	
	
	# add the artifact files to the index and make the "point" commit
	git.add("--", artifacts);						# add the artifacts to the repo
	if (infixMode):
		git.commit("-m","release version "+args.version);		# commit the artifacts to the repo
	else:
		git.commit("-m","release version "+args.version, _env=mdm.plumbing.convenv);	# commit the artifacts to the repo, using null headers
	git.tag("release/"+args.version);					# tag the release commit in the repo (this is arguably redundant since there's also a branch name we leave pointing at this place, but it's still a reasonable tag to make, and also as a sideeffect it causes github to offer really lovely tarballs.)		#TODO: there should be a signing option here.
	
	
	# generate an accumulation commit.  do this from the master branch, but don't submit it yet, because when we roll in the artifacts we want them in a subdirectory so that when master is checked out all the versions are splayed out in the working tree at once.
	git.checkout("master");
	git.merge("mdm/release/"+args.version, "--no-commit", "--no-ff");
	
	
	# move the artifact files into a version-named directory
	try:
		os.mkdir(args.version);
	except OSError, e:							# hopefully this doesn't come up to much, but there's three ways it could: 1) uncommitted files hanging out here, 2) concurrent modification of course, 3) there's an unfortunate edge case here if the release includes an artifact called "v2.0" and you're trying to make a release called "v2.0".
		if (e.errno != os.errno.EEXIST): raise e;			# what?
		return exitStatus(":'(", "couldn't make the directory named \""+args.version+"\" to put the releases into because there was already something there.");
	for a in artifacts:
		print a;
		git.mv("--", a, args.version+"/");
	
	
	# optionally, generate maven pom files and add them to the commit; this makes the mdm release repo backwards compatable as a maven repo.
	# TODO
	
	
	# now fire off the accumulation commit, and that commit now becomes head of the master branch.
	git.commit("-m","merge release version "+args.version+" to master");
	git.tag("mdm/master/"+args.version);					# tag the commit with the accumulated releases		#TODO: there should be a signing option here.
	
	
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


