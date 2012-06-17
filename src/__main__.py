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

import argparse;

__version__ = "0.1"
__project_url__ = "https://github.com/heavenlyhash/mdm"



#===============================================================================
# args parsing setup
#===============================================================================
def mdm_make_argsparser():
	parser = argparse.ArgumentParser(prog="mdm");
	parser.add_argument("--version", action="version", version=__version__);
	subparser = parser.add_subparsers(dest="subcommand");
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
		"--repo",
		help="specifies a local path to the releases repository for this project.  The new release snapshot repo will be added as raw data to this repository.  By default, it is assumed that the releases repo of this project is already a submodule in the ./releases/ directory, but using a path like '../projX-releases/' is also reasonable.",
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
# main
#===============================================================================
args = mdm_make_argsparser().parse_args();
print {
	 'depend': lambda args : args.subcommand + " not yet implemented",
	'release': lambda args : args.subcommand + " not yet implemented",
	 'update': lambda args : args.subcommand + " not yet implemented",
	  'clone': lambda args : args.subcommand + " not yet implemented",
}[args.subcommand](args);



#===============================================================================
# depend
#===============================================================================

# TODO



#===============================================================================
# release
#===============================================================================

# TODO



#===============================================================================
# update
#===============================================================================

# TODO



#===============================================================================
# clone
#===============================================================================

# TODO


