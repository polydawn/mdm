
from mdm.imp import *;

def releaseinit(args):
	# first we must decide two things:
	#   - whether we're currently in a project and creating a releases repo that is a submodule, or just creating a new standalone release repo
	#   - what the project name shall be, so we can include it in the default readme.
	relRepoPath = args.repo;
	projname = args.name;
	retreat = os.getcwd();
	asSubmodule = cgw.isRepoRoot(".");
	if (projname is None):
		if (asSubmodule):	# if we are initializating the releases repo as a submodule, we presume the folder name of that repo is the name of the project.
			projname = os.getcwd().split("/")[-1];
		else:			# if we're not a submodule, we're going to have a prompt for a name.
			projname = raw_input("what's the name of this project? ");
	if (relRepoPath is None):
		if (asSubmodule):	# if we are initializating the releases repo as a submodule, the default location to put that submodule is "./releases"
			relRepoPath = "./releases";
		else:			# if we're not a submodule, then the default is to make use of the current directory.
			relRepoPath = ".";
	
	
	# is the releases area free of clutter?
	if (asSubmodule):
		if (cgw.isSubmodule(relRepoPath)):
			return exitStatus(":I", "there's already a releases module!  No changes made.");
	if (not relRepoPath == "." and path.lexists(relRepoPath)):
		return exitStatus(":(", "something already exists at the location we want to initialize the releases repo.  clear it out and try again.");
	
	
	# okay!  make the new releases-repo.  put a first commit it in to avoid awkwardness.
	git.init(relRepoPath);
	cd(relRepoPath);
	with open("README", 'w') as f: f.write("This is the releases repo for "+projname+".\n");
	git.add("README");
	git.commit("-m", "initialize releases repo for "+projname+".");
	
	
	# label this root commit in order to declare this repo as a valid mdm releases repo.
	git.checkout("-b", "mdm/init");		# considered changing this to a tag instead of a branch, but you can't actually do that.  there's some crap with the fetching where you needed an essentially empty branch, and we need this init branch of that.  init is clearly more appropriate for that than infix, since infix doesn't necessarily even exist.
	# if infix mode was required, check out this root commit as the infix branch.  this will cause all future releases to be branched from here instead of being roots of their own new (short) history trees, and also cause those commits to use the current date instead of a null date.
	#  actually, just don't.  the user can do this themselves if desired.  it's a single git command that can be issued at absolutely any time.
	#if (args.infix):
	#	git.checkout("-b", "mdm/infix");
	cd(retreat);
	
	
	# if we're not a submodule, we're now done here, otherwise, the rest of the work revolves around the parent repo.
	if (not asSubmodule):
		return exitStatus(":D", "releases repo initialized");
	
	
	# check the state of this project repo for a remote origin.  trying to add a submodule with a relative repository url (as we're about to) will fail if that's not set.
	try:
		remoteOrigin = git.config("--get", "remote.origin.url").strip();
	except ErrorReturnCode:
		remoteOrigin = pwd().strip();		#XXX: I don't like using pwd here, but "." doesn't work either since the semantically correct thing for actual remotes on say github is to have a "../" prefix in order to make things siblings... and if the remote.origin.url is just ".", `git submodule add` barfs at that prefix.  Everything about this behavior of relative submodule paths is terribly frustrating.
		git.config("--add", "remote.origin.url", remoteOrigin);
	
	
	# add the new releases-repo as a submodule to the project repo.
	# using a relative url here means the author should be good to go with pushing, and others who clone the project with unauthenticated urls should also be fine.
	releasesRelUrl = "../"+projname+"-releases.git";
	git.submodule("add", releasesRelUrl, "releases");
	# set up the .git/config of the releases repo so that `cd releases && git push` just works (assuming that the relative upstream location is ready for us, which might require the user to do something like going into the github ui and setting up a repo for example).
	#  also, i think in older versions of git this was done for us already by the `git submodule add` part, but it's not true lately.
	cd(relRepoPath);
	git.remote("add", "origin", urlparse.urljoin(remoteOrigin+"/", releasesRelUrl));
	# you're still going to have to `git push -u origin master` the first time, sadly.  all of these steps that you'd think would fix that don't:
	#  git.config("branch.master.remote", "origin");
	#  git.config("branch.master.merge", "refs/heads/master");
	#  git.branch("--set-upstream", "master", "origin/master");	# only git >= v1.7.0
	cd(retreat);
	
	
	# put a marker in the submodules config that this submodule is a releases repo managed by mdm.
	git.config("-f", ".gitmodules", "submodule.releases.mdm", "releases");
	git.config("-f", ".gitmodules", "submodule.releases.update", "none");	# by default, most people probably won't need to download a releases repo unless they explicitly ask for it.
	git.add(".gitmodules");
	
	
	# commit the changes
	git.commit("-m", "initialize releases repo for "+projname+".");
	
	
	# if someone intends to make more commits before starting releasing... well, the first release will leave the master branch checked out, so let's make sure to do the same here so as not to be surprising.
	git.checkout("master");
	
	
	return exitStatus(":D", "releases repo and submodule initialized");


