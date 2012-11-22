
from mdm.imp import *;
from mdm.cmd.helper import *;

def alter(args):
	# parse gitmodules, check that the name we were asked to alter actually exist, and get its data.
	if (args.name[-1] == "/"): args.name = args.name[:-1];			# tab completion in the terminal tends to suggest what you want, but with a trailing slash because it's a directory, and git doesn't like that slash.  so, we'll sand down that sharp corner a bit.
	submodule = mdm.plumbing.getMdmSubmodules("dependency", args.name);
	if (submodule is None):
		return exitStatus(":(", "there is no mdm dependency by that name.");
	
	
	# give a look at the remote path and see what versions are physically available.
	versions = mdm.plumbing.getVersionManifest(submodule['url']);
	if (not versions):							# blow up if there's nothing there.
		return exitStatus(":(", "could be found where we expected a releases repository to be for the existing dependency.  maybe it has moved, or the internet broke?");
	
	
	# decide what version we're switching to
	if (args.version):	# well that was easy
		version = args.version;
	else:			# prompt the user for a choice from the versions we found available from the remote.
		version = promptForVersion(versions);
	
	
	# do the submodule/dependency dancing
	git.config("-f", ".gitmodules", "--replace-all", "submodule."+args.name+".mdm-version", version);
	mdm.plumbing.doDependencyLoad(args.name, version);
	
	
	# commit the changes
	git.commit("-m", "shifting dependency on "+args.name+" to version "+version+".", "--", ".gitmodules", args.name);
	
	
	return exitStatus(":D", "altered dependency on "+args.name+" to version "+version+" successfully!");


