
from mdm.imp import *;
from mdm.cmd.helper import *;

def alter(args):
	# parse gitmodules, check that the name we were asked to alter actually exist, and get its data.
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
	mdm.plumbing.doDependencyRemove(args.name);				#TODO: this is a little more aggressive than necessary.  we could improve this so that it doesn't reorder the gitmodules file unnecessarly, and also there should be options for whether or not to discard history that will be extraneous after the alter.
	mdm.plumbing.doDependencyAdd(args.name, submodule['url'], version);
	
	
	# commit the changes
	git.commit("-m", "shifting dependency on "+args.name+" to version "+version+".");
	
	
	return exitStatus(":D", "altered dependency on "+args.name+" to version "+version+" successfully!");


