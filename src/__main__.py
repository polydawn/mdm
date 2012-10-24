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
from pbs import git, cd, ls, cp, rm, pwd;
from pbs import ErrorReturnCode, ErrorReturnCode_1, ErrorReturnCode_2;
from distutils.version import LooseVersion as fn_version_sort;

from util import *;
import cgw;

__version__ = "0.6.0"
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
		help="specifies the directory to get artifact files from.  A single literal filename can be used, or basic shell globbing patterns (i.e. \"target/*\") can be used, or if a directory is provided, all files matching \"$files/*\" will be included.",
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

def mdm_make_argsparser_initsc(subparser):
	parser_clone = subparser.add_parser(
		"init",
		help="set up a releases repository for a new project.",
	);



#===============================================================================
# helpers
#===============================================================================

def getMdmSubmodules(kind=None, name=None, gmFilename=None):
	if (not gmFilename):
		try: gmFilename = git("rev-parse", "--show-toplevel").strip()+"/.gitmodules";
		except ErrorReturnCode: return None;
	dConf = cgw.getConfig(gmFilename);
	if (dConf is None): return None;
	if (not 'submodule' in dConf): return None;
	dSubm = dConf['submodule'];
	if (name):
		if (not name in dSubm): return None;
		subm = dSubm[name];
		if (not 'mdm' in subm): return None;
		if (kind and not subm['mdm'] == kind): return None;
		return subm;
	else:
		for submName, submDat in dSubm.items():
			if (not 'mdm' in submDat): del dSubm[submName]; continue;
			if (kind and not submDat['mdm'] == kind): del dSubm[submName]; continue;
		return dSubm;

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
	# grab the gitmodules file (which may be either local, or remote over raw http transport!) and store as string
	try:
		with closing(urllib.urlopen(releasesUrl+"/.gitmodules")) as f:
			remoteModulesStr = f.read();
	except:
		return None;
	
	# hand the gitmodules contents through `git-config` (via cgw.getConfig via getMdmSubmodules), get a proper dict of the conf back
	dConf = getMdmSubmodules("release-snapshot", None, (remoteModulesStr,));
	
	# we only really need an array of the names back
	return sorted(filter(lambda pythonYUNoHaveATrueFunctionAlready : True, dConf), key=fn_version_sort);

def mdm_doDependencyAdd(name, url, version):
	git.submodule("add", join(url, version+".git"), name);				# add us a submodule for great good!
	git.submodule("init", name);							# i would've thought `git submodule add` would have already done this, but it seems sometimes it does not.  anyway, at worst, this is a redunant no-op.
	git.config("-f", ".gitmodules", "submodule."+name+".mdm", "dependency");	# put a marker in the submodules config that this submodule is a dependency managed by mdm.
	# git.config("-f", ".gitmodules", "submodule."+name+".mdm-version", version);	# we could add another marker to make the version name an explicit property, but what would be the point?  our purposes are served well enough by making the pathname have an extremely explicit connection to the version name.
	git.add(".gitmodules");								# have to `git add` the gitmodules file again since otherwise the marker we just appended doesn't get staged
	pass;

def mdm_doDependencyRemove(name):
	try: git.config("-f", ".gitmodules", "--remove-section", "submodule."+name);	# remove config lines for this submodule currently in gitmodules file.  also, note i'm assuming we're already at the pwd of the repo top here.
	except: pass;									# errors because there was already no such config lines aren't really errors.
	git.add(".gitmodules");								# stage the gitmodule file change into the index.
	git.rm("--cached", name);							# mark submodule for removal in the index.  have to use the cached option and rm-rf it ourselves or git has a beef, seems silly to me but eh.
	rm("-rf", name);								# clear out the actual files
	rm("-rf", join(".git/modules",name));						# if this is one of the newer version of git (specifically, 1.7.8 or newer) that stores the submodule's data in the parent projects .git dir, clear that out forcefully as well or else git does some very silly things (you end up with the url changed but it recreates the old files and doesn't change the object id like it should).
	try: git.config("-f", ".git/config", "--remove-section", "submodule."+name);	# remove conflig lines for this submodule currently in .git/config.	# environmental $GIT_DIR is not supported.	# i'm a little unhappy about doing this before trying to commit anything else for smooth error recovery reasons... but on the other hand, we want to use this function to compose with other things in the same commit, so.
	except: pass;									# errors because there was already no such config lines aren't really errors.
	pass;



#===============================================================================
# depend
#===============================================================================

def mdm_depend_status(args):
	# check we're in a repo somewhere.
	if (not cgw.cwdIsInRepo()):
		return mdm_status(":(", "this command should be run from within a git repo.");
	
	# load config for all mdm dependencies
	submodules = getMdmSubmodules("dependency");
	
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
	versions = mdm_get_versionmanifest(releasesUrl);
	if (versions is None): return None;
	print "available versions: "+str(versions);
	version = None;
	while (not version):
		version = raw_input("select a version: ");
		if (not version in versions):
			print "\""+version+"\" is not in the list of available versions; double check your typing.";
			version = None;
	return version;



def mdm_depend_add(args):
	# check we're in a repo root.  `git submodule` insists that we must be at the top.
	if (not cgw.isRepoRoot(".")):
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
	if (cgw.isSubmodule(path)):
		return mdm_status(":(", "there is already a submodule at "+path+" !");
	if (os.path.exists(path)):
		return mdm_status(":(", "there are already files at "+path+" ; can't create a submodule there.");
	
	# if a specific version name was given, we'll skip checking for a manifest and just go straight at it; otherwise we look for a manifest and present options interactively.
	version = None;
	if (args.version):	# well that was easy
		version = args.version;
	else:			# check the url.  whether or not we can get a version manifest determines its validity.
		version = mdm_promptForVersion(args.url)
		if (version is None):
			return mdm_status(":(", "no version_manifest could be found at the url you gave for a releases repository -- it doesn't look like releases that mdm understands are there.");
	
	# check that the remote path is actually looking like a git repo before we call submodule add.
	if (not cgw.isRepo(join(args.url, version+".git"),  "refs/tags/release/"+version)):
		return mdm_status(":'(", "failed to find a release snapshot repository where we looked for it in the releases repository.");
	
	# do the submodule/dependency adding
	mdm_doDependencyAdd(path, args.url, version);
	
	# commit the changes
	git.commit("-m", "adding dependency on "+name+" at "+version+".");
	
	return mdm_status(":D", "added dependency on "+name+"-"+version+" successfully!");



def mdm_depend_alter(args):
	# parse gitmodules, check that the name we were asked to alter actually exist, and get its data.
	submodule = getMdmSubmodules("dependency", args.name);
	if (submodule is None):
		return mdm_status(":(", "there is no mdm dependency by that name.");
	
	# parse the url pointing to the current snapshot repo and drop the last part off of it; if things are canonical, this should be the releases repo.
	releasesUrl = submodule['url'][:submodule['url'].rindex("/")];
	
	# decide what version we're switching to
	version = None;
	if (args.version):	# well that was easy
		version = args.version;
	else:			# look for a version manifest and prompt for choices
		version = mdm_promptForVersion(releasesUrl);
		if (version is None):
			return mdm_status(":'(", "no version_manifest could be found where we expected a releases repository to be for the existing dependency.  maybe it has moved, or this dependency has an unusual/manual release structure, or the internet broke?");
	
	# check that the remote path is actually looking like a git repo before we call submodule add
	if (not cgw.isRepo(join(releasesUrl, version+".git"),  "refs/tags/release/"+version)):
		return mdm_status(":'(", "failed to find a release snapshot repository where we looked for it in the releases repository.");
	
	# do the submodule/dependency dancing
	mdm_doDependencyRemove(args.name);
	mdm_doDependencyAdd(args.name, releasesUrl, version);
	
	# commit the changes
	git.commit("-m", "shifting dependency on "+args.name+" to version "+version+".");
	
	return mdm_status(":D", "altered dependency on "+args.name+" to version "+version+" successfully!");



def mdm_depend_remove(args):
	# parse gitmodules, check that the name we were asked to alter actually exist, and get its data.
	submodule = getMdmSubmodules("dependency", args.name);
	if (submodule is None):
		return mdm_status(":I", "there is no mdm dependency by that name.");
	
	# kill it
	mdm_doDependencyRemove(args.name);
	
	# commit the changes
	git.commit("-m", "removing dependency on "+args.name+".");
	
	return mdm_status(":D", "removed dependency on "+args.name+"!");



#===============================================================================
# release
#===============================================================================

def mdm_release(args):
	retreat = os.getcwd();			# mind you that this command can be run from anywhere; it is not limited to the root of your project repo (though that's probably where I would almost always do it from).
	snapdir = args.repo+"/"+args.version;	# args.repo may have been either a relative or absolute path... dodge the issue by always cd'ing back to retreat before cd'ing to this.
	
	# sanity check the releases-repo
	if (not cgw.isRepoRoot(args.repo)):	# check that releases-repo is already a git repo
		return mdm_status(":(", "releases-repo directory '"+args.repo+"' doesn't look like a git repo!  (Maybe you forgot to set up with `mdm init` before making your first release?)");
	cd(args.repo);				# enter releases-repo
	git.checkout("master");			# history of the releases-repo is supposed to be linear.  things get confusing to push if they're not, and in particular we want to make sure that if there's currently a detatched head because of submodule updating leaving the releases repo in that state, we don't start racking up commits in that unpleasant void.
	try:					# check that nothing is already in the place where this version will be placed
		ls(args.version);
		return mdm_status(":(", "something already exists at '"+snapdir+"' !  Can't release there.");
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
			return mdm_status(":(", "no files were found at "+args.files+"\nrelease aborted.");
	
	# make the snapshot-repo
	git.init(snapdir);						# create new snapshot-repo inside the releases-repo
	try:								# if anything fails in building, we want to destroy the snapshot area so it's not a mess the next time we try to do a release.
		cp("-nr", glom, snapdir);				# copy, and recursively if applicable.  also, no-clobber mode because we'd hate to ever accidentally overwrite the .git structures.
	except Exception, e:
		cd(args.repo); rm("-rf", args.version);
		return mdm_status(":'(", "error during build: "+str(e));
	
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
	
	return mdm_status(":D", "release version "+args.version+" complete");



#===============================================================================
# update
#===============================================================================

def mdm_update(args):
	# check we're in a repo root.  `git submodule` insists that we must be at the top.
	if (not cgw.isRepoRoot(".")):
		return mdm_status(":(", "this command should be run from the top level folder of your git repo.");
	
	# load all of the submodules in the git index
	# load all the config that is for mdm dependencies from the submodules file
	# (we do both instead of just iterating on what we see in the submodules file because otherwise we'd need to write a check somewhere in the loop coming up that the submodule config actually has a matching data in the git object store as well; this way is the minimum forking.)
	submodules = cgw.getSubmodules();
	submConf = getMdmSubmodules("dependency");
	
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
	
	return mdm_status(":D", "mdm dependencies have been updated ("+status+")"+status2);



#===============================================================================
# init
#===============================================================================

def mdm_init(args):
	# am i at the root of a repo like I expect to be?		#XXX: i suppose we could do the first git-init as well if we're run in a void.  or better, with a name argument.
	if (not cgw.isRepoRoot(".")):
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
	if (cgw.isSubmodule("releases")):					#  if it's a submodule already, we give a different error message.
		return mdm_status(":I", "there's already a releases module!  No changes made.");
	try:
		ls("releases");
		return mdm_status(":(", "something already exists at the 'releases' location.  clear it out and try again.");
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
	
	return mdm_status(":D", "releases repo and submodule initialized");



#===============================================================================
# main
#===============================================================================
try:
	args = mdm_make_argsparser().parse_args();
	answer = {
		 'depend': lambda args : {
				'status': mdm_depend_status,
				   'add': mdm_depend_add,
				 'alter': mdm_depend_alter,
				'remove': mdm_depend_remove,
			}[args.subcommand_depend](args),
		'release': mdm_release,
		 'update': mdm_update,
		   'init': mdm_init,
	}[args.subcommand](args);
	
	if (isinstance(answer, dict)):
		print >> stderr, answer['message'];
		print >> stderr, answer['happy'];
		exit(answer['code']);
	else:
		print answer;
		exit(0);
except KeyboardInterrupt: print >> stderr, "";	# I know what an interrupt is and I don't need a dozen lines of stack trace every time I do it, thanks.

