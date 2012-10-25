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
import urlparse;
import argparse;
import re;
import urllib;
from contextlib import closing;
from glob import glob;
from pbs.sh import git, cd, ls, cp, rm, pwd;
from pbs.sh import ErrorReturnCode, ErrorReturnCode_1, ErrorReturnCode_2;
from distutils.version import LooseVersion as fn_version_sort;

from mdm.util import *;
import mdm.cgw as cgw;
import mdm.plumbing;

__version__ = "1.0.0"
__project_url__ = "https://github.com/heavenlyhash/mdm"



#===============================================================================
# args parsing setup
#===============================================================================
def mdm_makeArgsParser():
	parser = argparse.ArgumentParser(prog="mdm");
	parser.add_argument("--version", action="version", version=__version__);
	subparser = parser.add_subparsers(dest="subcommand", title="subcommands");
	mdm_makeArgsParser_status(subparser);
	mdm_makeArgsParser_update(subparser);
	mdm_makeArgsParser_add(subparser);
	mdm_makeArgsParser_alter(subparser);
	mdm_makeArgsParser_remove(subparser);
	mdm_makeArgsParser_release(subparser);
	mdm_makeArgsParser_releaseinit(subparser);
	return parser;

def mdm_makeArgsParser_status(subparser):
	parser_status = subparser.add_parser(
		"status",
		help="list dependencies managed by mdm, and their current status.",
	);

def mdm_makeArgsParser_update(subparser):
	parser_update = subparser.add_parser(
		"update",
		help="pull all dependencies up to date.  Run this after cloning a fresh repo, or pulling or checking out commits that change a dependency.",
	);

def mdm_makeArgsParser_add(subparser):
	parser_add = subparser.add_parser(
		"add",
		help="link a new dependency.",
	);
	parser_add.add_argument(
		"url",
		help="url pointing to the mdm-style releases repository that contains snapshots.  This url should provide direct access to the contents of the master branch of the releases repository (i.e. for a project that clones from https://github.com/heavenlyhash/mdm.git, what you want here is https://raw.github.com/heavenlyhash/mdm-releases/master ).",
	);	# these URLs are really troublingly unrelated to any other urls in use.  I can write a special parser that takes a normal repo url for github and parses it into the expected releases raw-readable url, and another one for redmine, and another one for gitweb, but it's all very gross.  I guess for the present I'm going to rely on people using mdm patterns to put the raw url in their project's readme and leave a manual step here.  But in the future, perhaps it would be pleasant to make a central repo site that could lookup names into release-repo raw-read urls.
	parser_add.add_argument(
		"--lib",
		help="specifies the directory which shall contain the dependency module.  (default: '%(default)s')",
		default="lib",
	);
	parser_add.add_argument(
		"--name",
		help="the name to give the new dependency module (if not specified, the url of the upstream will be parsed to determine the appropriate name, and if that fails, mdm will prompt you to choose one interactively).  Note that in the future, this dependency will be refered to by its path -- i.e., ${lib}/${name} ."
	);
	parser_add.add_argument(
		"--version",
		help="the version name of the dependency to set up.  If not provided, a mdm will try to obtain a list of available versions and prompt you to choose one interactively."
	);

def mdm_makeArgsParser_alter(subparser):
	parser_alter = subparser.add_parser(
		"alter",
		help="alter an existing dependency (i.e. switch to a new version).",
	);
	parser_alter.add_argument(
		"name",
		help="the name of the dependency module to operate on."
	);
	parser_alter.add_argument(
		"--version",
		help="the version name of the dependency to set up.  If not provided, mdm will search for a list of options."
	);

def mdm_makeArgsParser_remove(subparser):
	parser_remove = subparser.add_parser(
		"remove",
		help="remove an existing dependency.",
	);
	parser_remove.add_argument(
		"name",
		help="the name of the dependency module to operate on."
	);

def mdm_makeArgsParser_release(subparser):
	parser_release = subparser.add_parser(
		"release",
		help="generates a new release (creating and committing a new bare repo into your project's releases repo, then pulling that update and commiting it into the project's releases submodule).",
	);
	parser_release.add_argument(
		"--version",
		required=True,
		help="specifies the name/number of the release version to be made.  This will be used in creating tags, commit messages, and the name of the snapshot repository.",
	);
	parser_release.add_argument(
		"--files",
		required=True,
		help="specifies the directory to get artifact files from.  A single literal filename can be used, or basic shell globbing patterns (i.e. \"target/*\") can be used, or if a directory is provided, all files matching \"$files/*\" will be included.",
	);
	parser_release.add_argument(
		"--repo",
		help="specifies a local path to the releases repository for this project.  The new release snapshot repo will be added as raw data to this repository.  By default, it is assumed that the releases repo of this project is already a submodule in the ./releases/ directory, but using a path like '../projX-releases/' is also reasonable.  (default: '%(default)s')",
		default="releases",
	);

def mdm_makeArgsParser_releaseinit(subparser):
	parser_releaseinit = subparser.add_parser(
		"release-init",
		help="set up a releases repository for a new project.",
	);



#===============================================================================
# depend
#===============================================================================

def mdm_cmd_status(args):
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



def mdm_promptForVersion(releasesUrl):
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



def mdm_cmd_add(args):
	# check we're in a repo root.  `git submodule` insists that we must be at the top.
	if (not cgw.isRepoRoot(".")):
		return exitStatus(":(", "this command should be run from the top level folder of your git repo.");
	
	# git's behavior of assuming relative urls should be relative to the remote origin instead of relative to the local filesystem is almost certainly not what you want.
	if (args.url[:3] == "../" or args.url[:2] == "./"):
		return exitStatus(":(", "you can't use a relative url to point to a dependency.  sorry.");
	
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
	if (cgw.isSubmodule(path)):
		return exitStatus(":(", "there is already a submodule at "+path+" !");
	if (os.path.exists(path)):
		return exitStatus(":(", "there are already files at "+path+" ; can't create a submodule there.");
	
	# if a specific version name was given, we'll skip checking for a manifest and just go straight at it; otherwise we look for a manifest and present options interactively.
	version = None;
	if (args.version):	# well that was easy
		version = args.version;
	else:			# check the url.  whether or not we can get a version manifest determines its validity.
		version = mdm_promptForVersion(args.url)
		if (version is None):
			return exitStatus(":(", "no version_manifest could be found at the url you gave for a releases repository -- it doesn't look like releases that mdm understands are there.");
	
	# check that the remote path is actually looking like a git repo before we call submodule add.
	if (not cgw.isRepo(join(args.url, version+".git"),  "refs/tags/release/"+version)):
		return exitStatus(":'(", "failed to find a release snapshot repository where we looked for it in the releases repository.");
	
	# do the submodule/dependency adding
	mdm.plumbing.doDependencyAdd(path, args.url, version);
	
	# commit the changes
	git.commit("-m", "adding dependency on "+name+" at "+version+".");
	
	return exitStatus(":D", "added dependency on "+name+"-"+version+" successfully!");



def mdm_cmd_alter(args):
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
		version = mdm_promptForVersion(releasesUrl);
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



def mdm_cmd_remove(args):
	# parse gitmodules, check that the name we were asked to alter actually exist, and get its data.
	submodule = mdm.plumbing.getMdmSubmodules("dependency", args.name);
	if (submodule is None):
		return exitStatus(":I", "there is no mdm dependency by that name.");
	
	# kill it
	mdm.plumbing.doDependencyRemove(args.name);
	
	# commit the changes
	git.commit("-m", "removing dependency on "+args.name+".");
	
	return exitStatus(":D", "removed dependency on "+args.name+"!");



#===============================================================================
# release
#===============================================================================

def mdm_cmd_release(args):
	retreat = os.getcwd();			# mind you that this command can be run from anywhere; it is not limited to the root of your project repo (though that's probably where I would almost always do it from).
	snapdir = args.repo+"/"+args.version;	# args.repo may have been either a relative or absolute path... dodge the issue by always cd'ing back to retreat before cd'ing to this.
	
	# sanity check the releases-repo
	if (not cgw.isRepoRoot(args.repo)):	# check that releases-repo is already a git repo
		return exitStatus(":(", "releases-repo directory '"+args.repo+"' doesn't look like a git repo!  (Maybe you forgot to set up with `mdm init` before making your first release?)");
	cd(args.repo);				# enter releases-repo
	git.checkout("master");			# history of the releases-repo is supposed to be linear.  things get confusing to push if they're not, and in particular we want to make sure that if there's currently a detatched head because of submodule updating leaving the releases repo in that state, we don't start racking up commits in that unpleasant void.
	try:					# check that nothing is already in the place where this version will be placed
		ls(args.version);
		return exitStatus(":(", "something already exists at '"+snapdir+"' !  Can't release there.");
	except ErrorReturnCode_2:
		pass;	#good
	
	
	# select the artifact files that we'll be copying in
	cd(retreat);				# back out to the dir we were run from before going any further, in case the arguments used any relative paths.  that's by far the least confusing behavior.
	if (path.isfile(args.files)):		# if it's a file, we take it literally.
		glom = args.files;
	else:
		if (path.isdir(args.files)):	# if it's a dir, we glob everything within it (we don't really want to match dotfiles on the off chance someone tries to consider their entire repo to be snapshot-worthy, because then we'd grab the .git files, and that would be a mess).
			glom = glob(args.files+"/*");
		else:				# if it wasn't anything we can take literally, we'll just toss it to glob() directly and see what it can do with it.
			glom = glob(args.files);
		if (not glom):
			return exitStatus(":(", "no files were found at "+args.files+"\nrelease aborted.");
	
	# make the snapshot-repo
	git.init(snapdir);						# create new snapshot-repo inside the releases-repo
	try:								# if anything fails in building, we want to destroy the snapshot area so it's not a mess the next time we try to do a release.
		cp("-nr", glom, snapdir);				# copy, and recursively if applicable.  also, no-clobber mode because we'd hate to ever accidentally overwrite the .git structures.
	except Exception, e:
		cd(args.repo); rm("-rf", args.version);
		return exitStatus(":'(", "error during build: "+str(e));
	
	# commit the snapshot-repo
	cd(snapdir);
	git.add(".");							# add the artifacts to snapshot-repo
	convenv = {							# set up an env vars that will make the single commit in the snapshot-repo have uniform blob headers... this means if more than one person ever for example makes their own releases repo from artifacts they got somewhere else, their snapshots will actually converge to the same hash!
		    'GIT_AUTHOR_NAME' : "mdm",				 'GIT_COMMITTER_NAME' : "mdm",
		   'GIT_AUTHOR_EMAIL' : "",				'GIT_COMMITTER_EMAIL' : "",
		    'GIT_AUTHOR_DATE' : "Jan 01 1970 00:00 -0000",	 'GIT_COMMITTER_DATE' : "Jan 01 1970 00:00 -0000",
	}
	git.commit("-m","release snapshot version "+args.version, _env=convenv);	# commit the artifacts to snapshot-repo
	git.tag("release/"+args.version);				# tag the snapshot commit in this snapshot-repo		#TODO: there should be a signing option here.
	cd("..");							# back out to the releases-repo
	
	# clone the snapshot-repo data into the releases-repo in a way that can be added and commited
	git.clone(args.version, "--bare", args.version+".git");		# clone a bare mirror of the snapshot-repo
	rm("-r", args.version+".git/hooks");				# remove files from the bare snapshot-repo that are junk
	rm("-r", args.version+".git/info/exclude");			# remove files from the bare snapshot-repo that are junk
	rm("-r", args.version+".git/description");			# remove files from the bare snapshot-repo that are junk
	git.config("-f", args.version+".git/config", "--remove-section", "remote.origin");	# remove files from the bare snapshot-repo that are junk
	with open(args.version+".git/refs/heads/.gitignore", 'w') as f: f.write("");	# you don't wanna know.
	cd(args.version+".git");					# cd into the snapshot-repo so we can compact it
	git.gc("--aggressive");						# compact the snapshot-repo as hard as possible.  this also incidentally performs `git update-server-info`, which is necessary for raw/dumb http cloning to work.
	cd("..");							# back out to the releases-repo
	git.add(args.version+".git");					# add the raw data of the bare snapshot-repo to the releases-repo
	
	# add the snapshot-repo as a submodule to the releases-repo (this is part of how clients are later able to retrieve the list of available versions remotely)
	try: git.config("--get", "remote.origin.url");			# check the state of this repo for a remote origin.  trying to add a submodule with a relative repository url (as we're about to) will fail if that's not set.
	except ErrorReturnCode: git.config("--add", "remote.origin.url", ".");		#TODO: consider updating: this restriction actually appears to have been considered a bug in git, as it was removed in git v1.7.6.1.  mdm may want to remove this workaround behavior in the future.
	git.submodule("add", "./"+args.version+".git");			# add the new snapshot-repo as a submodule; this puts it in the submodules file so it's easily readable over plain http without cloning the whole releases repo (it also puts it in the index of the releases repo again, but that's fairly redundant and not the point).
	git.config("-f", ".gitmodules", "submodule."+args.version+".mdm", "release-snapshot");	# put a marker in the submodules config that this submodule is a release-snapshot managed by mdm.
	git.add(".gitmodules");						# and stage that version_manifest change we just made
	
	# commit the releases-repo changes
	git.commit("-m","release version "+args.version);		# commit the raw data of the bare snapshot-repo to the releases-repo
	git.tag("release/"+args.version);				# tag the snapshot commit in the releases-repo		#TODO: there should be a signing option here.
	
	# commit the new hash of the releases-repo into the project main repo (if we are operating in a canonically placed releases submodule)
	if (args.repo == "releases"):
		cd("..");						# back out to the proj-repo
		if (cgw.isRepoRoot(".") and cgw.isSubmodule("releases")):	# okay, it really does look like a canonically placed releases submodule.
			git.commit("releases","-m","release version "+args.version);
			git.tag("release/"+args.version);
	
	# we could push all the things...
	#  and that's tempting, because if someone pushes their project without pushing the releases submodule first, they will piss other people off.
	#  however, pushing makes things fairly unreversable; everything we've done so far is still local, and if something went fubar it's just one commit and one commit to reset.
	#  So, we're not going to push.  we're going to let the user get off and look around.
	#  (Perhaps we'll add switches for this later, though.)
	
	return exitStatus(":D", "release version "+args.version+" complete");



#===============================================================================
# update
#===============================================================================

def mdm_cmd_update(args):
	# check we're in a repo root.  `git submodule` insists that we must be at the top.
	if (not cgw.isRepoRoot(".")):
		return exitStatus(":(", "this command should be run from the top level folder of your git repo.");
	
	# load all of the submodules in the git index
	# load all the config that is for mdm dependencies from the submodules file
	# (we do both instead of just iterating on what we see in the submodules file because otherwise we'd need to write a check somewhere in the loop coming up that the submodule config actually has a matching data in the git object store as well; this way is the minimum forking.)
	submodules = cgw.getSubmodules();
	submConf = mdm.plumbing.getMdmSubmodules("dependency");
	
	# load all the mdm dependency submodules from gitmodules file, and only pay attention to the intersection of that and the git index
	# and then act on them based on the difference between the filesystem state and the git index intention
	impacted = [];
	unphased = 0;
	contorted = [];
	removed = [];
	for subm, status in submodules.items():
		if (not submConf): continue;		# this condition should really just go outside the loop
		if (not subm in submConf): continue;	# ignore things that don't look like mdm dependencies.
		if (status == " "):			# go ahead and ignore things where status indicates filesystem state already matches the index intention
			unphased += 1;
			continue;
		if (status == "-"):			# .git/config is not initialized
			git.submodule("init", subm);		# so initialize it.		#XXX: if something is initialized, but the update was superfluous, should that count as impacted?  I lean towards no, but it's also not a situation that should come up enough to be worth fretting over.
			git.submodule("update", subm);		# and update (clone) it.
		elif (status == "+"):			# submodule repo on the filesystem has a different commit hash checked out than index intentions
			# try to update it inplace (this will probably fail, but if someone did something clever with branches by all means let them enjoy it)
			#XXX later
			
			# read urls currently in .git/config, so that if someone repointed their repo to know about a more local copy, we can alter the prefix of what we're about to install to try to respect that.
			#XXX later
			
			# clear out the files and .git/config and reinit.  if we don't do the clear before updating, `git submodule init` doesn't change the url, and the url still pointing to the old version will cause `git submodule update` to clone it again but nothing will end up checked out and a hash error is reported.
			rm("-rf", subm);								# clear out the actual files
			try: git.config("-f", ".git/config", "--remove-section", "submodule."+subm);	# remove config lines for this submodule currently in .git/config.
			except: pass;									# errors because there was already no such config lines aren't really errors.
			git.submodule("init", subm);							# initialize again.
			
			# okay, now we can pull again
			git.submodule("update", subm);
		else:					# what?  could be a merge conflicted submodule or something, but you shouldn't have that with mdm dependency submodules unless you were doing something manual that was asking for trouble.
			contorted.append(subm);
			continue;
		#XXX: we have no special detection or handling for when submodule deletes are pulled from upstream.  what you end up with after that is just untracked files.  that's a little suprising, in my mind, but it's not exactly wrong, either.
		impacted.append(subm);
	
	# that's all.  compose a status string.
	status  = str(len(impacted)) + " changed, ";
	status += str(unphased) + " unaffected";
	if (len(contorted) > 0):
		status += ", "+str(len(contorted))+" contorted";
	if (len(removed) > 0):
		status += ", "+str(len(removed))+" removed";
	status2 = "";
	if (len(impacted) > 0):
		status2 += "\n  changed: "+str(impacted);
	if (len(contorted) > 0):
		status2 += "\n  contorted: "+str(contorted);
	if (len(removed) > 0):
		status2 += "\n  removed: "+str(removed);
	
	return exitStatus(":D", "mdm dependencies have been updated ("+status+")"+status2);



#===============================================================================
# release-init
#===============================================================================

def mdm_cmd_releaseinit(args):
	# am i at the root of a repo like I expect to be?		#XXX: i suppose we could do the first git-init as well if we're run in a void.  or better, with a name argument.
	if (not cgw.isRepoRoot(".")):
		return exitStatus(":(", "this command should be run from the top level folder of your git repo.");
	projname = os.getcwd().split("/")[-1];
	
	# check to make sure this repo has more than zero commits in it to avoid awkwardness.
	## I take it back; this doesn't appear to be as problematic as I at first worried.
	#try:
	#	git.log("-n 1");
	#except ErrorReturnCode:
	#	print >> stderr, "please make at least one commit before initializing your releases repo.  (git can behave surprisingly in repositories with no history.)\n:(";
	#	exit(3);
	
	# is the "releases" area free of clutter?  (we're not supporting other locations in this script, because if you want noncanonical here, you can go ahead and do it yourself.)
	if (cgw.isSubmodule("releases")):					#  if it's a submodule already, we give a different error message.
		return exitStatus(":I", "there's already a releases module!  No changes made.");
	try:
		ls("releases");
		return exitStatus(":(", "something already exists at the 'releases' location.  clear it out and try again.");
	except ErrorReturnCode_2:
		pass;	#good
	
	# check the state of this repo for a remote origin.  trying to add a submodule with a relative repository url (as we're about to) will fail if that's not set.
	try:
		remoteOrigin = git.config("--get", "remote.origin.url").strip();
	except ErrorReturnCode:
		remoteOrigin = pwd().strip();		#XXX: I don't like using pwd here, but "." doesn't work either since the semantically correct thing for actual remotes on say github is to have a "../" prefix in order to make things siblings... and if the remote.origin.url is just ".", `git submodule add` barfs at that prefix.  Everything about this behavior of relative submodule paths is terribly frustrating.
		git.config("--add", "remote.origin.url", remoteOrigin);
	
	# okay!  make the new releases-repo.  put a first commit it in to avoid awkwardness.
	git.init("releases");
	cd("releases");
	with open("README", 'w') as f: f.write("This is the releases repo for "+projname+".\n");
	git.add("README");
	git.commit("-m", "initialize releases repo for "+projname+".");
	cd("..");
	
	# add the new releases-repo as a submodule to the project repo.
	# using a relative url here means the author should be good to go with pushing, and others who clone the project with unauthenticated urls should also be fine.
	releasesRelUrl = "../"+projname+"-releases.git";
	git.submodule("add", releasesRelUrl, "releases");
	# set up the .git/config of the releases repo so that `cd releases && git push` just works (assuming that the relative upstream location is ready for us, which might require the user to do something like going into the github ui and setting up a repo for example).
	#  also, i think in older versions of git this was done for us already by the `git submodule add` part, but it's not true lately.
	cd("releases");
	git.remote("add", "origin", urlparse.urljoin(remoteOrigin+"/", releasesRelUrl));
	# you're still going to have to `git push -u origin master` the first time, sadly.  all of these steps that you'd think would fix that don't:
	#  git.config("branch.master.remote", "origin");
	#  git.config("branch.master.merge", "refs/heads/master");
	#  git.branch("--set-upstream", "master", "origin/master");	# only git >= v1.7.0
	cd("..");
	
	# put a marker in the submodules config that this submodule is a releases repo managed by mdm.
	git.config("-f", ".gitmodules", "submodule.releases.mdm", "releases");
	git.config("-f", ".gitmodules", "submodule.releases.update", "none");	# by default, most people probably won't need to download a releases repo unless they explicitly ask for it.
	git.add(".gitmodules");
	
	# commit the changes
	git.commit("-m", "initialize releases repo for "+projname+".");
	
	return exitStatus(":D", "releases repo and submodule initialized");



#===============================================================================
# main
#===============================================================================
try:
	args = mdm_makeArgsParser().parse_args();
	answer = {
		      'status': mdm_cmd_status,
		      'update': mdm_cmd_update,
		         'add': mdm_cmd_add,
		       'alter': mdm_cmd_alter,
		      'remove': mdm_cmd_remove,
		     'release': mdm_cmd_release,
		'release-init': mdm_cmd_releaseinit,
	}[args.subcommand](args);
	
	if (isinstance(answer, dict)):
		print >> stderr, answer['message'];
		print >> stderr, answer['happy'];
		exit(answer['code']);
	else:
		print answer;
		exit(0);
except KeyboardInterrupt: print >> stderr, "";	# I know what an interrupt is and I don't need a dozen lines of stack trace every time I do it, thanks.

