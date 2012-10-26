
from mdm.imp import *;
from mdm.cmd.helper import *;

def alter(args):
	# parse gitmodules, check that the name we were asked to alter actually exist, and get its data.
	submodule = mdm.plumbing.getMdmSubmodules("dependency", args.name);
	if (submodule is None):
		return exitStatus(":(", "there is no mdm dependency by that name.");
	
	# parse the url pointing to the current snapshot repo and drop the last part off of it; if things are canonical, this should be the releases repo.
	releasesUrl = submodule['url'][:submodule['url'].rindex("/")];
	
	# decide what version we're switching to
	version = None;
	if (args.version):	# well that was easy
		version = args.version;
	else:			# look for a version manifest and prompt for choices
		version = promptForVersion(releasesUrl);
		if (version is None):
			return exitStatus(":'(", "no version_manifest could be found where we expected a releases repository to be for the existing dependency.  maybe it has moved, or this dependency has an unusual/manual release structure, or the internet broke?");
	
	# check that the remote path is actually looking like a git repo before we call submodule add
	if (not cgw.isRepo(join(releasesUrl, version+".git"),  "refs/tags/release/"+version)):
		return exitStatus(":'(", "failed to find a release snapshot repository where we looked for it in the releases repository.");
	
	# do the submodule/dependency dancing
	mdm.plumbing.doDependencyRemove(args.name);
	mdm.plumbing.doDependencyAdd(args.name, releasesUrl, version);
	
	# commit the changes
	git.commit("-m", "shifting dependency on "+args.name+" to version "+version+".");
	
	return exitStatus(":D", "altered dependency on "+args.name+" to version "+version+" successfully!");


