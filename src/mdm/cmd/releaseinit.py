
from mdm.imp import *;

def releaseinit(args):
	# am i at the root of a repo like I expect to be?		#XXX: i suppose we could do the first git-init as well if we're run in a void.  or better, with a name argument.
	if (not cgw.isRepoRoot(".")):
		return exitStatus(":(", "this command should be run from the top level folder of your git repo.");
	projname = os.getcwd().split("/")[-1];
	
	
	# check to make sure this repo has more than zero commits in it to avoid awkwardness.
	## I take it back; this doesn't appear to be as problematic as I at first worried.
	#try:
	#	git.log("-n 1");
	#except ErrorReturnCode:
	#	print >> stderr, "please make at least one commit before initializing your releases repo.  (git can behave surprisingly in repositories with no history.)\n:(";
	#	exit(3);
	
	
	# is the "releases" area free of clutter?  (we're not supporting other locations in this script, because if you want noncanonical here, you can go ahead and do it yourself.)
	if (cgw.isSubmodule("releases")):					#  if it's a submodule already, we give a different error message.
		return exitStatus(":I", "there's already a releases module!  No changes made.");
	if (path.lexists("releases")):
		return exitStatus(":(", "something already exists at the 'releases' location.  clear it out and try again.");
	
	
	# check the state of this repo for a remote origin.  trying to add a submodule with a relative repository url (as we're about to) will fail if that's not set.
	try:
		remoteOrigin = git.config("--get", "remote.origin.url").strip();
	except ErrorReturnCode:
		remoteOrigin = pwd().strip();		#XXX: I don't like using pwd here, but "." doesn't work either since the semantically correct thing for actual remotes on say github is to have a "../" prefix in order to make things siblings... and if the remote.origin.url is just ".", `git submodule add` barfs at that prefix.  Everything about this behavior of relative submodule paths is terribly frustrating.
		git.config("--add", "remote.origin.url", remoteOrigin);
	
	
	# okay!  make the new releases-repo.  put a first commit it in to avoid awkwardness.
	git.init("releases");
	cd("releases");
	with open("README", 'w') as f: f.write("This is the releases repo for "+projname+".\n");
	git.add("README");
	git.commit("-m", "initialize releases repo for "+projname+".");
	
	# label this root commit as a branch, because all releases in the future will come back to branch off of this again.
	git.checkout("-b", "mdm/init");
	cd("..");
	
	
	# add the new releases-repo as a submodule to the project repo.
	# using a relative url here means the author should be good to go with pushing, and others who clone the project with unauthenticated urls should also be fine.
	releasesRelUrl = "../"+projname+"-releases.git";
	git.submodule("add", releasesRelUrl, "releases");
	# set up the .git/config of the releases repo so that `cd releases && git push` just works (assuming that the relative upstream location is ready for us, which might require the user to do something like going into the github ui and setting up a repo for example).
	#  also, i think in older versions of git this was done for us already by the `git submodule add` part, but it's not true lately.
	cd("releases");
	git.remote("add", "origin", urlparse.urljoin(remoteOrigin+"/", releasesRelUrl));
	# you're still going to have to `git push -u origin master` the first time, sadly.  all of these steps that you'd think would fix that don't:
	#  git.config("branch.master.remote", "origin");
	#  git.config("branch.master.merge", "refs/heads/master");
	#  git.branch("--set-upstream", "master", "origin/master");	# only git >= v1.7.0
	cd("..");
	
	
	# put a marker in the submodules config that this submodule is a releases repo managed by mdm.
	git.config("-f", ".gitmodules", "submodule.releases.mdm", "releases");
	git.config("-f", ".gitmodules", "submodule.releases.update", "none");	# by default, most people probably won't need to download a releases repo unless they explicitly ask for it.
	git.add(".gitmodules");
	
	
	# commit the changes
	git.commit("-m", "initialize releases repo for "+projname+".");
	
	
	return exitStatus(":D", "releases repo and submodule initialized");


