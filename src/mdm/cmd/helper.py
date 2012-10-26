
from mdm.imp import *;


def _promptForVersion(releasesUrl):
	versions = mdm.plumbing.getVersionManifest(releasesUrl);
	if (versions is None): return None;
	print "available versions: "+str(versions);
	version = None;
	while (not version):
		version = raw_input("select a version: ");
		if (not version in versions):
			print "\""+version+"\" is not in the list of available versions; double check your typing.";
			version = None;
	return version;


