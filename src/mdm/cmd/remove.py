
from mdm.imp import *;

def remove(args):
	# parse gitmodules, check that the name we were asked to alter actually exist, and get its data.
	submodule = mdm.plumbing.getMdmSubmodules("dependency", args.name);
	if (submodule is None):
		return exitStatus(":I", "there is no mdm dependency by that name.");
	
	# kill it
	mdm.plumbing.doDependencyRemove(args.name);
	
	# commit the changes
	git.commit("-m", "removing dependency on "+args.name+".");
	
	return exitStatus(":D", "removed dependency on "+args.name+"!");


