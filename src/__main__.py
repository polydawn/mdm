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

from mdm.imp import *;
import argparse;
import mdm.cmd;

__version__ = "2.0.0a"
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
# main
#===============================================================================
try:
	args = mdm_makeArgsParser().parse_args();
	answer = {
		      'status': mdm.cmd.status,
		      'update': mdm.cmd.update,
		         'add': mdm.cmd.add,
		       'alter': mdm.cmd.alter,
		      'remove': mdm.cmd.remove,
		     'release': mdm.cmd.release,
		'release-init': mdm.cmd.releaseinit,
	}[args.subcommand](args);
	
	if (isinstance(answer, dict)):
		print >> stderr, answer['message'];
		print >> stderr, answer['happy'];
		exit(answer['code']);
	else:
		print answer;
		exit(0);
except KeyboardInterrupt: print >> stderr, "";	# I know what an interrupt is and I don't need a dozen lines of stack trace every time I do it, thanks.

