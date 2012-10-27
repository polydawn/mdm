
from mdm.imp import *;

def status(args):
	# check we're in a repo somewhere.
	if (not cgw.cwdIsInRepo()):
		return exitStatus(":(", "this command should be run from within a git repo.");
	
	
	# load config for all mdm dependencies
	submodules = mdm.plumbing.getMdmSubmodules("dependency");
	
	
	# generate a big string o' data
	if (not submodules or len(submodules) is 0):
		return " --- no managed dependencies --- ";
	width1 = 0;
	for modname, vals in submodules.items():
		width1 = max(width1, len(modname));
	width1 = (width1/8)*8+8;
	width1 = str(width1);	# this line does not appease me.
	v  = ("%-"+width1+"s   \t %s\n") % ("dependency:", "version:");
	v += ("%-"+width1+"s   \t %s\n") % ("-----------", "--------");
	for modname, vals in submodules.items():
		v += ("  %-"+width1+"s \t   %s\n") % (modname, vals['url'].split("/")[-1]);	#FIXME: i'm not sure this is the ideal place to get the version string from.  this is the version intended by gitmodule config, but not the actual reality of what's checked out!  it's not clear where would be best to get ground truth from; perhaps the tag name checked out in the submodule?  that can break if there are multiple tags on the same commit, but a release manager shouldn't be doing crap like that.
	
	
	return v[:-1];


