#===============================================================================
# Copyright 2012 Eric Myhre <http://exultant.us>
# 
# This file is part of mdm <https://github.com/heavenlyhash/mdm/>.
# 
# PSAP is free software: you can redistribute it and/or modify
# it under the terms of the GNU Lesser General Public License as published by
# the Free Software Foundation, version 3 of the License, or
# (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Lesser General Public License for more details.
# 
# You should have received a copy of the GNU Lesser General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#===============================================================================

from sys import stderr;
import os;
from os import path;
import argparse;
from pbs import git, xargs, cd, ls, cp, rm, pwd, glob;
from pbs import ErrorReturnCode, ErrorReturnCode_1, ErrorReturnCode_2;

__version__ = "0.1"
__project_url__ = "https://github.com/heavenlyhash/mdm"



#===============================================================================
# args parsing setup
#===============================================================================
def mdm_make_argsparser():
	parser = argparse.ArgumentParser(prog="mdm");
	parser.add_argument("--version", action="version", version=__version__);
	subparser = parser.add_subparsers(dest="subcommand", title="subcommands");
	mdm_make_argsparser_dependsc(subparser);
	mdm_make_argsparser_releasesc(subparser);
	mdm_make_argsparser_updatesc(subparser);
	mdm_make_argsparser_clonesc(subparser);
	return parser;

def mdm_make_argsparser_dependsc(subparser):
	parser_depend = subparser.add_parser(
		"depend",
		help="set up or modify a dependency.",
	);
	parser_depend.add_argument(
		"--lib",
		help="specifies the directory which shall contain the dependency module.",
		default="lib",
	);
	parser_depend.add_argument(
		"--name",
		help="override the default name of the dependency module (if not specified, the url of the upstream will be parsed to determine the appropriate name).",
		default=None,
	);

def mdm_make_argsparser_releasesc(subparser):
	parser_makerelease = subparser.add_parser(
		"release",
		help="generates a new release (creating and committing a new bare repo into your project's releases repo, then pulling that update and commiting it into the project's releases submodule).",
	);
	parser_makerelease.add_argument(
		"--version",
		required=True,				# this appears to be broken and does not cause correct doc generation in my version of python.  so that's disappointing.
		help="specifies the name/number of the release version to be made.  This will be used in creating tags, commit messages, and the name of the snapshot repository.",
	);
	parser_makerelease.add_argument(
		"--files",
		required=True,
		help="specifies the directory to get artifact files from.",
	);
	parser_makerelease.add_argument(
		"--repo",
		help="specifies a local path to the releases repository for this project.  The new release snapshot repo will be added as raw data to this repository.  By default, it is assumed that the releases repo of this project is already a submodule in the ./releases/ directory, but using a path like '../projX-releases/' is also reasonable.  (default: '%(default)s')",
		default="releases",
	);

def mdm_make_argsparser_updatesc(subparser):
	parser_update = subparser.add_parser(
		"update",
		help="pull all dependencies up to date.  Run this after cloning a fresh repo, or pulling or checking out commits that change a dependency.",
	);

def mdm_make_argsparser_clonesc(subparser):
	parser_clone = subparser.add_parser(
		"clone",
		help="performs a normal git-clone, then automatically pulls all dependencies.",
	);



#===============================================================================
# helpers
#===============================================================================

def isGitRepoRoot(dirname):
	if (not path.isdir(dirname)):
		return False;
	retreat = os.getcwd();
	cd(dirname);
	gitbase = git("rev-parse", "--show-toplevel");
	wheream = pwd("-P");
	cd(retreat);
	return gitbase == wheream;



#===============================================================================
# depend
#===============================================================================

# TODO



#===============================================================================
# release
#===============================================================================

def mdm_release(args):
	# sanity check the releases-repo
	if (not isGitRepoRoot(args.repo)):	# check that releases-repo is already a git repo
		print >> stderr, "repo directory '"+args.repo+"' doesn't look like a git repo!\n:(";
		exit(3);
	cd(args.repo);				# enter releases-repo
	try:					# check that nothing is already in the place where this version will be placed
		ls(args.version);
		print >> stderr, "something already exists at '"+args.repo+"/"+args.version+"' !  Can't release there.\n:(";
		exit(3);
	except ErrorReturnCode_2:
		pass;	#good
	
	# make the snapshot-repo
	git.init(args.version);						# create new snapshot-repo
	cd(args.version);						# enter snapshot-repo
	
	# do the build / copy in the artifacts
	try:	# if anything fails in building, we want to destroy the snapshot area so it's not a mess the next time we try to do a release.
		cp(glob(args.files+"/*"), ".");				# copy in artifacts via glob (we don't really want to match dotfiles on the off chance someone considers their entire repo to be snapshot-worthy, because then we'd grab the .git files, and that would be a mess.)
	except:
		print >> stderr, "error during build!\n:'(";
		cd(".."); rm("-r", args.version);
		raise;
	
	# commit the snapshot-repo
	git.add(".");							# add the artifacts to snapshot-repo
	git.commit("-m","release snapshot version "+args.version);	# commit the artifacts to snapshot-repo
	git.tag(args.version);						# tag the snapshot commit in this snapshot-repo		#TODO: there should be a signing option here.
	cd("..");							# back out to the releases-repo
	
	# commit the snapshot-repo into the releases-repo
	git.clone(args.version, "--bare", args.version+".git");		# clone a bare mirror of the snapshot-repo
	rm("-r", args.version+".git/hooks");				# remove files from the bare snapshot-repo that are junk
	with open(args.version+".git/refs/heads/.gitignore", 'w') as f: f.write("");	# you don't wanna know.
	git.add(args.version+".git");					# add the raw data of the bare snapshot-repo to the releases-repo
	with open(".gitignore", 'a') as f: f.write(args.version+"\n");	# add the new snapshot-repo to the .gitignore so we can have it be expanded later without there being noise
	git.add(".gitignore");						# and stage that .gitignore change we just made
	git.commit("-m","release snapshot version "+args.version);	# commit the raw data of the bare snapshot-repo to the releases-repo
	git.tag(args.version);						# tag the snapshot commit in the releases-repo		#TODO: there should be a signing option here.
	
	# push all the things
	#  this we may not want to do without letting the user get off and look around, because it makes things fairly unreversable.
	# TODO					# push the releases-repo
	# TODO					# back out to the proj-repo
	# TODO					# commit the new version of the release-repo submodule
	
	return "release version "+args.version+" complete\n:D";



#===============================================================================
# update
#===============================================================================

# TODO



#===============================================================================
# clone
#===============================================================================

# TODO



#===============================================================================
# main
#===============================================================================
args = mdm_make_argsparser().parse_args();
print {
	 'depend': lambda args : args.subcommand + " not yet implemented",
	'release': mdm_release,
	 'update': lambda args : args.subcommand + " not yet implemented",
	  'clone': lambda args : args.subcommand + " not yet implemented",
}[args.subcommand](args);


