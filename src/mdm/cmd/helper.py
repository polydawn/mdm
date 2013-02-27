
from mdm.imp import *;


def promptForVersion(versions):
	print "available versions: "+"\n\t"+"\n\t".join(versions);
	version = None;
	while (not version):
		version = raw_input("select a version: ");
		if (not version in versions):
			print "\""+version+"\" is not in the list of available versions; double check your typing.";
			version = None;
	return version;


