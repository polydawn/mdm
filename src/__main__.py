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

__version__ = "2.1.2"
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
		help="url pointing to an mdm-style releases repository.  Any kind of url that `git clone` understands will fly here too.",
	);
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
		help="the version name of the dependency to set up.  If not provided, a mdm will obtain a list of available versions and prompt you to choose one interactively."
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
		help="the version name of the dependency to set up.  If not provided, a mdm will obtain a list of available versions and prompt you to choose one interactively."
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
		help="generates a new release (adding commits to a releases repository; then, if this command was issued from inside a project's repository and the releases repository is a submodule in the canonical location, the new head commit of the master branch of the releases repo will be committed to the project repo along with a release tag).",
	);
	parser_release.add_argument(
		"--version",
		required=True,
		help="specifies the name/number of the release version to be made.  This will be used in naming tags/branches, and also mentioned in commit messages.",
	);
	parser_release.add_argument(
		"--files",
		required=True,
		help="specifies the artifact files to commit in the release.  A single literal filename can be used, or basic shell globbing patterns (i.e. \"target/*\") can be used, or if a directory is provided, all files matching \"$files/*\" will be included.",
	);
	parser_release.add_argument(
		"--repo",
		help="specifies a local path to the releases repository for this project.  The new release commits will be added to this repository.  By default, it is assumed that the releases repo of this project is already a submodule in the ./releases/ directory, but using a path like '../projX-releases/' is also reasonable.  (default: '%(default)s')",
		default="releases",
	);

def mdm_makeArgsParser_releaseinit(subparser):
	parser_releaseinit = subparser.add_parser(
		"release-init",
		help="set up a releases repository for a new project.  Future invocations of `mdm release` will generate commits into this repository.",
	);
	parser_releaseinit.add_argument(
		"--name",
		help="the name of the project.  If not provided, either the path of the parent project will be parsed to determine the appropriate name, or failing that, mdm will prompt you to choose one interactively."
	);
	parser_releaseinit.add_argument(
		"--repo",
		help="specifies a path where the releases repository should be created.  If not provided, the default varies depending on if this command is issued from the root of an existing git repo: if so, it is assumed the releases repo should be a submodule in the \"./releases/\" directory; otherwise, the default behavior is to initialize the release repo in the current directory.",
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

