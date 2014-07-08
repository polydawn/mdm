#!/bin/bash
set -e
set -v

#
# Demonstrate a merge between two branches with conflicting dependency changes.
#



# cleanup

rm -rf ./test-merge/
mkdir ./test-merge/
cd   ./test-merge/

# set up a demo project repo.

git init .

git commit --allow-empty -m "merge test repo"



# depend on some library.

mdm add http://mdm-releases.com/junit/junit-releases.git --version=4.7.mvn



# make some branches (starting from the same place).

git checkout -b alpha

git checkout -b beta



# altering a dep on one branch is easy.

git checkout alpha

mdm alter lib/junit --version=4.8.2.mvn



# altering a dep on another branch is easy.

git checkout beta

mdm alter lib/junit --version=4.9.mvn



# merging one branch with an altered dep is easy.

git checkout master

git merge --no-ff --no-edit alpha

# you still have to do an mdm update to pull it along though.
# otherwise the last time we moved the dependency submodule was on branch beta, and it's still on 4.9

mdm status

mdm update

mdm status


# merging the second... well...

git checkout master

(set +e ; git merge --no-ff --no-edit beta ; exit 0)

# the git-merge output looks something like this:
#
#	warning: Failed to merge submodule lib/junit (commits don't follow merge-base)
#	Auto-merging lib/junit
#	CONFLICT (submodule): Merge conflict in lib/junit
#	Auto-merging .gitmodules
#	CONFLICT (content): Merge conflict in .gitmodules
#	Automatic merge failed; fix conflicts and then commit the result.
#



git status

# the git-status output looks something like this:
#
#	# On branch master
#	# You have unmerged paths.
#	#   (fix conflicts and run "git commit")
#	#
#	# Unmerged paths:
#	#   (use "git add <file>..." to mark resolution)
#	#
#	#       both modified:      .gitmodules
#	#       both modified:      lib/junit
#	#



git diff

# the git-diff output looks something like this:
#
#	diff --cc .gitmodules
#	index 1d1e403,36ed635..0000000
#	--- a/.gitmodules
#	+++ b/.gitmodules
#	@@@ -2,5 -2,5 +2,11 @@@
#	        path = lib/junit
#	        url = http://mdm-releases.com/junit/junit-releases.git
#	        mdm = dependency
#	++<<<<<<< HEAD
#	 +      mdm-version = 4.8.2.mvn
#	++||||||| merged common ancestors
#	++      mdm-version = 4.7.mvn
#	++=======
#	+       mdm-version = 4.9.mvn
#	++>>>>>>> beta
#	        update = none
#	diff --cc lib/junit
#	index 1448485,2520ae6..0000000
#	--- a/lib/junit
#	+++ b/lib/junit
#
# (If you don't see the "merged common ancestors" thing it's not a big deal; that's a cool feature you can get by configuring 'merge.conflictstyle = diff3'.)



# now what do we do about it?
# resolve the same way as any other merge:

git checkout --theirs -- .gitmodules

# now use update again to put the submodule where you want it
# (or you could go into that directory and use git-checkout; same thing)

mdm update

# now add to staging, and commit the resolve

git add .
git commit --no-edit



# did it work?
# yes, and the commit looks perfectly reasonable.

git show --stat


# mdm status also thinks all is right with the world
# mdm update is calm, we're already there

mdm status

mdm update



set +v
#
# Tested on:
#  git version 1.8.3.2
#  git version 1.7.9.5
# Older versions of git may also work,
# but note that 1.7.9.5 is already over 2 years old as of the date of writing.
# More recent versions of git should as a rule be prefered;
# handling of submodules has generally improved as versions progress.
#


