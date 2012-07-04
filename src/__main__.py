#===============================================================================
# Copyright 2012 Eric Myhre <http://exultant.us>
# 
# This file is part of mdm <https://github.com/heavenlyhash/mdm/>.
# 
# mdm is free software: you can redistribute it and/or modify
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
from os.path import join, relpath;
import argparse;
import re;
import urllib;
from contextlib import closing;
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
	mdm_make_argsparser_initsc(subparser);
	return parser;

def mdm_make_argsparser_dependsc(subparser):
	parser_depend = subparser.add_parser(
		"depend",
		help="set up or modify a dependency.",
	);
	parser_depend_subparser = parser_depend.add_subparsers(dest="subcommand_depend", title="depend-subcommand");
	
	parser_depend_status = parser_depend_subparser.add_parser(
		"status",
		help="list dependencies managed by mdm.",
	);
	
	parser_depend_add = parser_depend_subparser.add_parser(
		"add",
		help="link a new dependency.",
	);
	parser_depend_add.add_argument(
		"url",
		help="url pointing to the mdm-style releases repository that contains snapshots.  This url should provide direct access to the contents of the master branch of the releases repository (i.e. for a project that clones from https://github.com/heavenlyhash/mdm.git, what you want here is https://raw.github.com/heavenlyhash/mdm-releases/master ).",
	);	# these URLs are really troublingly unrelated to any other urls in use.  I can write a special parser that takes a normal repo url for github and parses it into the expected releases raw-readable url, and another one for redmine, and another one for gitweb, but it's all very gross.  I guess for the present I'm going to rely on people using mdm patterns to put the raw url in their project's readme and leave a manual step here.  But in the future, perhaps it would be pleasant to make a central repo site that could lookup names into release-repo raw-read urls.
	parser_depend_add.add_argument(
		"--lib",
		help="specifies the directory which shall contain the dependency module.  (default: '%(default)s')",
		default="lib",
	);
	parser_depend_add.add_argument(
		"--name",
		help="the name to give the new dependency module (if not specified, the url of the upstream will be parsed to determine the appropriate name, and if that fails, mdm will prompt you to choose one interactively).  Note that in the future, this dependency will be refered to by its path -- i.e., ${lib}/${name} ."
	);
	parser_depend_add.add_argument(
		"--version",
		help="the version name of the dependency to set up.  If not provided, a mdm will try to obtain a list of available versions and prompt you to choose one interactively."
	);
	
	parser_depend_alter = parser_depend_subparser.add_parser(
		"alter",
		help="alter an existing dependency (i.e. switch to a new version).",
	);
	parser_depend_alter.add_argument(
		"name",
		help="the name of the dependency module to operate on."
	);
	parser_depend_alter.add_argument(
		"--version",
		help="the version name of the dependency to set up.  If not provided, mdm will search for a list of options."
	);
	
	parser_depend_remove = parser_depend_subparser.add_parser(
		"remove",
		help="remove an existing dependency.",
	);
	parser_depend_remove.add_argument(
		"name",
		help="the name of the dependency module to operate on."
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

def mdm_make_argsparser_initsc(subparser):
	parser_clone = subparser.add_parser(
		"init",
		help="set up a releases repository for a new project.",
	);



#===============================================================================
# helpers
#===============================================================================

def mdaa(dct, tup, val):
	# merge a value into a multidimentional ragged array.  I feel like this is something there certainly ought to be a more pythonic convenient syntax for, but if so I haven't found it yet.
	for k in tup[:-1]:
		if (not k in dct):
			dct[k] = {};
		dct = dct[k];
	dct[tup[-1]] = val;

def isGitRepoRoot(dirname):
	if (not path.isdir(dirname)):
		return False;
	retreat = os.getcwd();
	cd(dirname);
	try:
		return git("rev-parse", "--show-toplevel") == pwd("-P");
	finally:
		cd(retreat);

def isSubmodule(path):
	retreat = os.getcwd();
	try :
		cd(git("rev-parse", "--show-toplevel").strip());
		submodstr = git.submodule("status", path);
		if (len(submodstr) == 0): return False;
		submodstr = submodstr.split("\n")[0].split(" ");
		return path == submodstr[2 if (submodstr[0] == "") else 1];
	finally:
		cd(retreat);

def getGitConfig(filename):
	# note about parsing this: dots in the final of the three keys are not allowed.  dots in the first and second keys ARE allowed (yes, both of them), so I don't know how to parse that correctly from the output of this command (though it's possible from the file itself of course).  I'm just going to assume that there are no dots in the first key; if there are, you're weird and a bastard.
	try:
		gmlines = str(git.config("-f", filename, "-lz"));
	except ErrorReturnCode:
		return None;
	v = {};
	for line in gmlines.split("\0"):
		if (not line): continue;
		keys, value = tuple(line.split("\n", 1));
		d1 = keys.index(".");
		d2 = keys.rindex(".");
		key1 = keys[:d1];
		if (d1 == d2):
			key2 = keys[d1+1:];
			mdaa(v, (key1, key2), value);
		else:
			key2 = keys[d1+1:d2];
			key3 = keys[d2+1:];
			mdaa(v, (key1, key2, key3), value);
	return v;

def mdm_status(happy, message):
	try:
		code = {
			 ":D": 0,	# happy face is appropriate for great success
			     # 1 is a catchall for general/unexpected errors.
			     # 2 is for "misuse of shell builtins" in Bash.
			 ":(": 3,	# sadness is appropriate for a misconfigured project or bad args or something
			":'(": 4,	# tears is appropriate for major exceptions or subprocesses not doing well
			 ":I": 0,	# cramped face is appropriate for when we sat on our hands because the request was awkward but the goal state is satisfied anyway
		}[happy];
	except: code = 128;
	return {'happy':happy, 'message':message, 'code':code};

def mdm_get_versionmanifest(releasesUrl):
	try:
		with closing(urllib.urlopen(releasesUrl+"/version_manifest")) as f:
			return f.read();
	except:
		return None;



#===============================================================================
# depend
#===============================================================================

def mdm_depend_status(args):
	# get the submodules config.  we use this instead of what's actually inside the .git/config file because our custom mdm properties aren't copied in to the .git/config file by git when `git submodule init`.
	try:
		gmpath = git("rev-parse", "--show-toplevel").strip()+"/.gitmodules";
	except ErrorReturnCode:
		return mdm_status(":(", "this command should be run within a git repo.");	
	confdict = getGitConfig(gmpath);
	
	# if there's no gitmodules file, then we just don't have any dependencies
	if (confdict is None):
		return " --- no managed dependencies --- ";
	submodules = confdict['submodule'];
	
	# filter out submodules that dont have the mdm dependency tag
	for modname, vals in submodules.items():
		if (not 'mdm' in vals or not vals['mdm'] == "dependency"):
			del submodules[modname];
	
	# generate a big string o' data
	if (len(submodules) is 0):
		return " --- no managed dependencies --- ";
	width1 = 0;
	for modname, vals in submodules.items():
		width1 = max(width1, len(modname));
	width1 = (width1/8)*8+8;
	width1 = str(width1);	# this line does not appease me.
	v  = ("%-"+width1+"s   \t %s\n") % ("dependency:", "version:");
	v += ("%-"+width1+"s   \t %s\n") % ("-----------", "--------");
	for modname, vals in submodules.items():
		v += ("  %-"+width1+"s \t   %s\n") % (modname, vals['url'].split("/")[-1]);
	
	return v[:-1];



def mdm_depend_add(args):
	# check we're in a repo top
	if (not isGitRepoRoot(".")):
		return mdm_status(":(", "this command should be run from the top level folder of your git repo.");
	
	# git's behavior of assuming relative urls should be relative to the remote origin instead of relative to the local filesystem is almost certainly not what you want.
	if (args.url[:3] == "../" or args.url[:2] == "./"):
		return mdm_status(":(", "you can't use a relative url to point to a dependency.  sorry.");
	
	# pick out the name.  if we can't find one yet, we'll prompt for it in a little bit (we try to check that something at least exists on the far side of the url before bothering with the name part).
	name = None;
	if (args.name):		# well that was easy
		name = args.name;
	else:			# look for a discernable project name in the url chunks
		urlchunks = args.url.split("/");
		urlchunks.reverse();
		for chunk in urlchunks:
			tehMatch = re.match(r"(.*)-releases", chunk);
			if (tehMatch):
				name = tehMatch.group(1);
				break;
		# prompt for a name if we don't have one picked yet.
		if (not name):
			name = raw_input("dependency name: ");
	
	# we shall normalize a bit of uri here into the local uri.
	#  it's rather disconcerting later if we don't (some commands will normalize it, and others won't, and that's just a mess), and we also want to check for local existance of a submodule here before getting too wild.
	path = relpath(join(args.lib, name));
	
	# check for presence of a submodule or other crap here already.  (`git submodule add` will also do this, but it's a more pleasant user experience to check this before popping up a prompt for version name.)
	#  there are actually many things you could check here: presence of files, presence of entries in .git/config, presence of entries in .gitmodules, presence of data in the index.  we're going to just use our default pattern from isSubmodule() and then check for plain files.
	if (isSubmodule(path)):
		return mdm_status(":(", "there is already a submodule at "+path+" !");
	if (os.path.exists(path)):
		return mdm_status(":(", "there are already files at "+path+" ; can't create a submodule there.");
	
	# if a specific version name was given, we'll skip checking for a manifest and just go straight at it; otherwise we look for a manifest and present options interactively.
	version = None;
	if (args.version):	# well that was easy
		version = args.version;
	else:			# check the url.  whether or not we can get a version manifest determines its validity.
		versionmf = mdm_get_versionmanifest(args.url);
		if (versionmf == None):
			return mdm_status(":(", "no version_manifest could be found at the url you gave for a releases repository -- it doesn't look like releases that mdm understands are there.");
		versions = versionmf.split();
		print "available versions: "+str(versions);
		while (not version):
			version = raw_input("select a version: ");
			if (not version in versions):
				print "\""+version+"\" is not in the list of available versions; double check your typing.";
				version = None;
	
	# add us a submodule for great good!
	print >> stderr, "adding "+name+"-"+version+" to "+args.lib+" from "+args.url;
	git.submodule("add", args.url+"/"+version, path);
	
	# put a marker in the submodules config that this submodule is a dependency managed by mdm.  mostly a silent mutation, but possibly convenient for our bookkeeping sometimes.
	git.config("-f", ".gitmodules", "submodule."+path+".mdm", "dependency");
	git.add(".gitmodules");
	
	# commit the changes
	git.commit("-m", "adding dependency on "+name+" at "+version+".");
	
	return mdm_status(":D", "added dependency on "+name+"-"+version+" successfully!");


def mdm_depend_alter(args):
	return mdm_status("DX", "not implemented");


def mdm_depend_remove(args):
	return mdm_status("DX", "not implemented");



#===============================================================================
# release
#===============================================================================

def mdm_release(args):
	retreat = os.getcwd();			# mind you that this command can be run from anywhere; it is not limited to the root of your project repo (though that's probably where I would almost always do it from).
	snapdir = args.repo+"/"+args.version;	# args.repo may have been either a relative or absolute path... dodge the issue by always cd'ing back to retreat before cd'ing to this.
	
	# sanity check the releases-repo
	if (not isGitRepoRoot(args.repo)):	# check that releases-repo is already a git repo
		return mdm_status(":(", "releases-repo directory '"+args.repo+"' doesn't look like a git repo!");
	cd(args.repo);				# enter releases-repo
	try:					# check that nothing is already in the place where this version will be placed
		ls(args.version);
		return mdm_status(":(", "something already exists at '"+snapdir+"' !  Can't release there.");
	except ErrorReturnCode_2:
		pass;	#good
	
	# make the snapshot-repo
	git.init(args.version);						# create new snapshot-repo
	
	# do the build / copy in the artifacts
	cd(retreat);							# back out to the dir we were run from.  that's by far the least confusing behavior.
	try:	# if anything fails in building, we want to destroy the snapshot area so it's not a mess the next time we try to do a release.
		glom = glob(args.files+"/*");				# select artifacts via glob (we don't really want to match dotfiles on the off chance someone tries to consider their entire repo to be snapshot-worthy, because then we'd grab the .git files, and that would be a mess.)
		if (glom == args.files+"/*"):				# if the glob string comes back unchanged, that means it didn't match anything, and that's a problem.
			return mdm_status(":(", "no files were found at "+args.files+"/");
		cp("-r", glom, snapdir);				# copy, and recursively if applicable.
	except Exception, e:
		cd(args.repo); rm("-r", args.version);
		return mdm_status(":'(", "error during build: "+str(e));
	
	# commit the snapshot-repo
	cd(snapdir);
	git.add(".");							# add the artifacts to snapshot-repo
	git.commit("-m","release snapshot version "+args.version);	# commit the artifacts to snapshot-repo
	git.tag(args.version);						# tag the snapshot commit in this snapshot-repo		#TODO: there should be a signing option here.
	cd("..");							# back out to the releases-repo
	
	# commit the snapshot-repo into the releases-repo
	git.clone(args.version, "--bare", args.version+".git");		# clone a bare mirror of the snapshot-repo
	rm("-r", args.version+".git/hooks");				# remove files from the bare snapshot-repo that are junk
	with open(args.version+".git/refs/heads/.gitignore", 'w') as f: f.write("");	# you don't wanna know.
	git.add(args.version+".git");					# add the raw data of the bare snapshot-repo to the releases-repo
	with open("version_manifest", 'a') as f:			# add the new snapshot-repo to the plaintext manifest file so it's easily readable over plain http without cloning the whole releases repo
		f.write(args.version+"\n");
	git.add("version_manifest");					# and stage that version_manifest change we just made
	with open(".gitignore", 'a') as f: f.write(args.version+"\n");	# add the new snapshot-repo to the .gitignore so we can have it be expanded later without there being noise
	git.add(".gitignore");						# and stage that .gitignore change we just made
	git.commit("-m","release snapshot version "+args.version);	# commit the raw data of the bare snapshot-repo to the releases-repo
	git.tag(args.version);						# tag the snapshot commit in the releases-repo		#TODO: there should be a signing option here.
	
	# commit the new hash of the releases-repo into the project main repo (if we are operating in a canonically placed releases submodule)
	if (args.repo == "releases"):
		cd("..");						# back out to the proj-repo
		if (isGitRepoRoot(".") and isSubmodule("releases")):	# okay, it really does look like a canonically placed releases submodule.
			git.commit("releases","-m","release snapshot version "+args.version);
			git.tag(args.version);
	
	# we could push all the things...
	#  and that's tempting, because if someone pushes their project without pushing the releases submodule first, they will piss other people off.
	#  however, pushing makes things fairly unreversable; everything we've done so far is still local, and if something went fubar it's just one commit and one commit to reset.
	#  So, we're not going to push.  we're going to let the user get off and look around.
	#  (Perhaps we'll add switches for this later, though.)
	
	return mdm_status(":D", "release version "+args.version+" complete");



#===============================================================================
# update
#===============================================================================

# TODO



#===============================================================================
# clone
#===============================================================================

# TODO



#===============================================================================
# init
#===============================================================================

def mdm_init(args):
	# am i at the root of a repo like I expect to be?		#XXX: i suppose we could do the first git-init as well if we're run in a void.  or better, with a name argument.
	if (not isGitRepoRoot(".")):
		return mdm_status(":(", "this command should be run from the top level folder of your git repo.");
	projname = os.getcwd().split("/")[-1];
	
	# check to make sure this repo has more than zero commits in it to avoid awkwardness.
	## I take it back; this doesn't appear to be as problematic as I at first worried.
	#try:
	#	git.log("-n 1");
	#except ErrorReturnCode:
	#	print >> stderr, "please make at least one commit before initializing your releases repo.  (git can behave surprisingly in repositories with no history.)\n:(";
	#	exit(3);
	
	# is the "releases" area free of clutter?  (we're not supporting other locations in this script, because if you want noncanonical here, you can go ahead and do it yourself.)
	if (isSubmodule("releases")):					#  if it's a submodule already, we give a different error message.
		return mdm_status(":I", "there's already a releases module!  No changes made.");
	try:
		ls("releases");
		return mdm_status(":(", "something already exists at the 'releases' location.  clear it out and try again.");
	except ErrorReturnCode_2:
		pass;	#good
	
	# check the state of this repo for a remote origin.  trying to add a submodule with a relative repository url (as we're about to) will fail if that's not set.
	try: git.config("--get", "remote.origin.url");
	except ErrorReturnCode:
		git.config("--add", "remote.origin.url", ".");		#XXX: I haven't done a lot of testing about the long-term health of this.  push and pull work, amusingly.  Still, it might be more sane to just abort and complain about the lack of remote.
	
	# okay!  make the new releases-repo.  put a first commit it in to avoid awkwardness.
	git.init("releases");
	cd("releases");
	with open("README", 'w') as f: f.write("This is the releases repo for "+projname+".\n");
	git.add("README");
	git.commit("-m", "initialize releases repo for "+projname+".");
	cd("..");
	
	# add the new releases-repo as a submodule to the project repo.
	# using a relative url here means the author should be good to go with pushing, and others who clone the project with unauthenticated urls should also be fine.
	git.submodule("add", "./"+projname+"-releases.git", "releases");
	git.commit("-m", "initialize releases repo for "+projname+".");
	
	return mdm_status(":D", "releases repo and submodule initialized");



#===============================================================================
# main
#===============================================================================
args = mdm_make_argsparser().parse_args();
answer = {
	 'depend': lambda args : {
			'status': mdm_depend_status,
			   'add': mdm_depend_add,
			 'alter': mdm_depend_alter,
			'remove': mdm_depend_remove,
		}[args.subcommand_depend](args),
	'release': mdm_release,
	 'update': lambda args : args.subcommand + " not yet implemented",
	  'clone': lambda args : args.subcommand + " not yet implemented",
	   'init': mdm_init,
}[args.subcommand](args);

if (isinstance(answer, dict)):
	print >> stderr, answer['message'];
	print >> stderr, answer['happy'];
	exit(answer['code']);
else:
	print answer;
	exit(0);

