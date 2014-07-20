#!/bin/bash

 mkdir arena
    cd arena
rm -rf devlink
 mkdir devlink
    cd devlink

function explain {
	cat <(echo -ne "\E[1;32m") - <(echo -ne "\E[0m")
}


explain <<-'EOF'
#
#  We're going to create a project, add a dependency...
#  And then try to replace it with a symlink to nonsense, without disrupting git status.
#
#  I'm not saying this is a *good* idea, but it might be an interesting way to have a "dev" mode.
#
EOF

set -v
(
	git init project
	cd project
	touch somescript.sh
	git add .
	git commit -m "a demo project"
	mdm add http://mdm-releases.com/junit/junit-releases.git --version 4.11.mvn
	mdm status
)
set +v

echo
echo



explain <<-'EOF'
#
#  `git status` should be clear, and we should see a library there.
#
EOF

set -v
(
	cd project
	git status
	ls -l
	ls -l lib/junit/
)
set +v

echo
echo



explain <<-'EOF'
#
#  Now suppose we have some development-mode resources lying elsewhere.
#
#  What if we want to refer to those instead, and we don't want to publish releases?
#
#  (Obviously, this means we'd be putting our workspace in a state that
#     *no one else can reproduce*.
#  ...But let's suppose we're fine with that, for the moment.)
#
EOF

set -v
(
	mkdir dev-junit
	touch dev-junit/junit.jar # just pretend
)
set +v

echo
echo



explain <<-'EOF'
#
#  We can just overwrite the mdm dependency location with a symlink.
#
#  This will work (sorta), but:
#  - `git status` will report a typechange
#  - `mdm update` is honestly a little confused
#
EOF

set -v
(
	cd project
	rm -rf lib/junit
	ln -s ../../dev-junit lib/junit

	git status
	ls -l lib/
	ls -l lib/junit/
	mdm status
)
set +v

echo
echo



explain <<-'EOF'
#
#  What if we just ignore it?
#
#  Plain old '.gitignore' patterns won't work -- the lib/junit path is already tracked.
#
#  Git has another way: we can just ask git to always pretend this path is unchanged.
#
EOF

set -v
(
	cd project
	git update-index --assume-unchanged lib/junit

	git status
	ls -l lib/
	mdm status
)
set +v

echo
echo



explain <<-'EOF'
#
#  Going back to "prod" mode, with the dependency as linked by mdm, is easy:
#  - remove the symlink
#  - `mdm update` as usual
#
#  Note that this *is* a little weird.  The assume-unchanged feature interferes very brutally:
#  Even completely absent files aren't marked as deleted or missing.
#  It's really up to you to remember that you may be in a state that's out of sync
#  with what you and others are pushing and pull.
#
EOF

set -v
(
	cd project
	rm lib/junit
	mdm update

	ls -l lib/junit
	mdm status
)
set +v

echo
echo


