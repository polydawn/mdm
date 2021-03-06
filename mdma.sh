#!/bin/bash -e

cnblack="$(echo -ne "\E[0;30m")"
clblack="$(echo -ne "\E[1;30m")"
cngreen="$(echo -ne "\E[0;32m")"
clgreen="$(echo -ne "\E[1;32m")"
cnbrown="$(echo -ne "\E[0;33m")"
clbrown="$(echo -ne "\E[1;33m")"
cnblue="$(echo -ne "\E[0;34m")"
clblue="$(echo -ne "\E[1;34m")"
cnone="$(echo -ne "\E[0m")"

cat <<EOF
${clgreen}\
################################################################################
#
#  Running mdm through its paces!
#
#  The mdm executable must either be on your \$PATH (i.e. we'll get it from
#  \`which\`), or in an env var called \$MDM.
#
#  git must also be available on your \$PATH.  (mdm doesn't use it, but this
#  demo script will use it for setting up the demonstration data, and also to
#  show that normal git completely understands what mdm does to your repos.)
#
#  By default, every major step will pause after completion so you can take a
#  look around.  If that behavior isn't desired and you just wanna shoot
#  straight through as a sanity test, give the "-t" argument.
#
#  A new directory in the current working directory called "mdm-demo" will be
#  created and the system tests run inside there.
#
################################################################################
${cnone}
EOF

if [ -z $MDM ]; then MDM=`which mdm`; fi;
if [ "$1" != "-t" ]; then straight=true; fi; export straight;
demodir="mdm-demo";

awaitack() {
	[ "$straight" != true ] && return;
	echo -ne "${cnbrown}waiting for ye to hit enter, afore the voyage heave up anchor and make headway${cnone}"
	read -es && echo -ne "\E[F\E[2K\r"
}

mkdir -p "$demodir" && cd "$demodir" && demodir="$(pwd)"



echo "${clblue}#  Set up some git project repositories.${cnone}"
git init projUpstream1
git init projUpstream2
git init projAlpha
echo -e "${clblue} ----------------------------${cnone}\n\n"


echo "${clblue}#  Fill project repositories with files and make initial commits.${cnone}"
(cd projUpstream1 &&
 echo "koalas are cool!" > proj1.txt &&
 git add proj1.txt &&
 git commit proj1.txt -m "version one file in demo project projUpsteam1."
)
echo
(cd projUpstream2 &&
 echo "all bananas are clones!" > proj2.txt &&
 git add proj2.txt &&
 git commit proj2.txt -m "version one file in demo project projUpsteam2."
)
echo
(cd projAlpha &&
 echo "zealous zebras!" > projAlpha.txt &&
 git add projAlpha.txt &&
 git commit projAlpha.txt -m "file in demo project alpha."
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Push bare mirrors of a projects to a 'hub' site.${cnone}"
mkdir "hub";
git init --bare hub/projUpstream1.git
(cd projUpstream1 &&
 git remote add origin $demodir/hub/projUpstream1.git
 git push -u origin master
)
echo
git init --bare hub/projUpstream2.git
(cd projUpstream2 &&
 git remote add origin $demodir/hub/projUpstream2.git
 git push -u origin master
)
echo
git init --bare hub/projAlpha.git
(cd projAlpha &&
 git remote add origin $demodir/hub/projAlpha.git
 git push -u origin master
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Clone projects again from the 'hub' site into new repos.${cnone}"
echo "${clblue}#   We'll use these for the rest of the test to demonstrate${cnone}"
echo "${clblue}#    that dependency and release updates propagate safely.${cnone}"
mkdir "clone"
git clone hub/projUpstream1.git clone/projUpstream1
git clone hub/projUpstream2.git clone/projUpstream2
git clone hub/projAlpha.git     clone/projAlpha
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Initialize mdm release repositories for the upstream projects.${cnone}"
(cd projUpstream1 &&
 $MDM release-init --use-defaults &&
 echo "${clblack}# in the project repo history: ${cnone}" && git log --oneline releases &&
 echo "${clblack}# in the release repo history: ${cnone}" && (cd releases && git log --oneline)
)
echo
(cd projUpstream2 &&
 $MDM release-init --use-defaults &&
 echo "${clblack}# in the project repo history: ${cnone}" && git log --oneline releases &&
 echo "${clblack}# in the release repo history: ${cnone}" && (cd releases && git log --oneline)
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Push the newly initialized releases repos to the 'hub' site.${cnone}"
echo "${clblue}#   The urls have already been generated, defaulting to a location${cnone}"
echo "${clblue}#    relative to the project's origin repo location.${cnone}"
git init hub/projUpstream1-releases.git
(cd hub/projUpstream1-releases.git && git config --local --ad receive.denyCurrentBranch ignore && echo -e "#!/bin/sh\ncd ..\nenv -i git reset --hard" > .git/hooks/post-receive && chmod +x .git/hooks/post-receive)
echo "${clblack}# push projUpstream1/releases: ${cnone}"
(cd projUpstream1/releases && git push -u origin master)
echo "${clblack}# push projUpstream1: ${cnone}"
(cd projUpstream1/ && git push)
echo
git init hub/projUpstream2-releases.git
(cd hub/projUpstream2-releases.git && git config --local --ad receive.denyCurrentBranch ignore && echo -e "#!/bin/sh\ncd ..\nenv -i git reset --hard" > .git/hooks/post-receive && chmod +x .git/hooks/post-receive)
echo "${clblack}# push projUpstream2/releases: ${cnone}"
(cd projUpstream2/releases && git push -u origin master)
echo "${clblack}# push projUpstream2: ${cnone}"
(cd projUpstream2/ && git push)
# `git push --recurse-submodules` would also work nicely here as of git v1.7.11
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Alright, let's make some releases in our upstream projects:${cnone}"
echo "${clblack}# make a release of projUpstream1's files: ${cnone}"
(cd projUpstream1/ && 
 $MDM release --version=v1.0 --files=proj1.txt
)
echo
echo "${clblack}# make a release of projUpstream2's files: ${cnone}"
(cd projUpstream2/ && 
 $MDM release --version=v1.0 --files=proj2.txt
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Check it out, we can push those release snapshots to our hub repos.${cnone}"
(cd projUpstream1 && 
 (cd releases && git push --all && git push --tags) &&
 git push && git push --tags
)
(cd projUpstream2 && 
 (cd releases && git push --all && git push --tags) &&
 git push && git push --tags
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Now before we go on... what does \`mdm status\` have to say about projAlpha?${cnone}"
(cd projAlpha
 $MDM status
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Moment of truth: we can now use mdm to pull those releases into another project.${cnone}"
(cd projAlpha
 echo "${clblack}# now to add the first project, we do \`mdm add \$demodir/hub/projUpstream1-releases.git\`: ${cnone}"
 $MDM add $demodir/hub/projUpstream1-releases.git --version=v1.0
 echo
 echo "${clblack}# same thing to depend on another project: \`mdm add \$demodir/hub/projUpstream2-releases.git\`: ${cnone}"
 $MDM add $demodir/hub/projUpstream2-releases.git --version=v1.0
 echo
 echo "${clblack}# we gave a --version argument to mdm here as well to keep the demo script flying along,"
 echo "#  but of course you can not give the version argument and will recieve an interactive prompt.${cnone}"
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  So did it work?${cnone}"
(cd projAlpha
 echo "${clblack}# now here's what \`mdm status\` thinks of project alpha: ${cnone}"
 $MDM status
 echo
)
echo "${clblack}# and we can see the files from the dependency release in place right where we asked: ${cnone}"
head projAlpha/lib/*/*
echo
echo "${clblack}# and just for following along, here's what the git logs in our repos are right now: ${cnone}"
for repo in $demodir/proj* $demodir/proj*/releases/; do (cd $repo && echo "repo=$repo:" && git log --oneline && echo); done
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  And if you look at how much history projAlpha pulled down for those dependencies:${cnone}"
for repo in $demodir/projAlpha/lib/*; do (cd $repo && echo "repo=$repo:" && git log --oneline); done
echo "${clblack}# it's not much!  Just that one commit with what you need.${cnone}"
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Now let's publish the dependencies we added to projAlpha to our hub repo.${cnone}"
(cd projAlpha && git push)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Behold, the clones of the project can pull all this:${cnone}"
(cd clone/projAlpha
 echo "${clblack}# \`mdm status\` in the clone repo doesn't show anything until we pull, of course.${cnone}"
 $MDM status
 echo
 echo "${clblack}# pulling the commits to projAlpha that added the dependencies...${cnone}"
 git pull
 echo
 echo "${clblack}# \`mdm status\` should now show that we do have managed dependencies${cnone}"
 echo "${clblack}#  (but they aren't checked out here yet): ${cnone}"
 $MDM status
 echo
 echo "${clblack}# we run \`mdm update\` to fetch the dependencies: ${cnone}"
 $MDM update
 echo
 echo "${clblack}# huzzah, \`mdm status\` now shows us happy little dependencies! ${cnone}"
 $MDM status
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Let's release a few more versions of an upstream project.${cnone}"
echo "${clblack}# make some updates to the upstream project's source files: ${cnone}"
(cd projUpstream1 &&
 echo "koalas are BASTARDS!" > proj1.txt &&
 git add proj1.txt &&
 git commit proj1.txt -m "updated data file." &&
 git show
)
echo
echo "${clblack}# make a release of projUpstream1's files: ${cnone}"
(cd projUpstream1/ &&
 $MDM release --version=v2.0 --files=proj1.txt
)
echo
echo "${clblack}# and publish that release to the hub repos.${cnone}"
(cd projUpstream1 &&
 (cd releases && git push --all && git push --tags) &&
 git push && git push --tags
)
echo
sleep 1
echo "${clblack}# ...okay, one more for fun (and to see a bigger graph):${cnone}"
(cd projUpstream1 &&
 echo "One ate my hat!" >> proj1.txt &&
 git add proj1.txt &&
 git commit proj1.txt -m "updated data file (more backstory)." &&
 git show
)
echo
echo "${clblack}# make a release of projUpstream1's files: ${cnone}"
(cd projUpstream1/ &&
 $MDM release --version=v2.1 --files=proj1.txt
)
echo

echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Releases can also contain an entire directory structure:${cnone}"
echo "${clblack}# refactor the project to put release artifacts in a directory: ${cnone}"
(cd projUpstream1 &&
 mkdir target &&
 git mv *.txt target &&
 git commit -m "moved files to directory" &&
 git show
)
echo
echo "${clblack}# add some more files and directories: ${cnone}"
(cd projUpstream1 &&
 mkdir target/bin &&
 echo "echo herro" > target/bin/tickle.sh &&
 chmod +x target/bin/tickle.sh &&
 mkdir -p target/data/assets &&
 echo -e "\[\033\[\00" > target/data/assets/koala.jpg &&
 git add target &&
 git commit -m "added script" &&
 git show
)
echo
echo "${clblack}# make a release of projUpstream1's files: ${cnone}"
(cd projUpstream1/ &&
 $MDM release --version=v3.0 --files=target
)
echo
echo "${clblack}# full trees per version also exist in the merged master branch: ${cnone}"
(cd projUpstream1/releases/ &&
 (cd v3.0/ && ls -l . */ */*/)
)
echo
echo "${clblack}# and publish that release to the hub repos.${cnone}"
(cd projUpstream1 &&
 (cd releases && git push --all && git push --tags) &&
 git push && git push --tags
)
echo

echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Moment of truth, Part II: we can now use \`mdm alter\` on projAlpha${cnone}"
echo "${clblue}#   to switch it to using new release versions of the upstream project.${cnone}"
(cd projAlpha
 echo "${clblack}# now to add the first project, we do \`mdm add \$demodir/hub/projUpstream1.git\`: ${cnone}"
 $MDM alter lib/projUpstream1 --version=v3.0
 echo
 echo "${clblack}# \`mdm status\` should show us the change: ${cnone}"
 $MDM status
 echo
 echo "${clblack}# \`ls\` show us the files: ${cnone}"
 (cd lib/projUpstream1/ && ls -l . */ */*/)
 echo
 echo "${clblack}# \`git push\` the dependency version change to the hub repo: ${cnone}"
 git push
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  Moment of truth, Part III: projAlpha's clone can pull the new changes${cnone}"
echo "${clblue}#   from the hub repo, and smoothly switch to the new dependency version.${cnone}"
(cd clone/projAlpha
 echo "${clblack}# we haven't pulled yet, so \`mdm status\` should show all clear: ${cnone}"
 $MDM status
 echo
 echo "${clblack}# \`git pull\`: ${cnone}"
 git pull
 echo
 echo "${clblack}# Now \`mdm status\` should tell us that our last pull demands a dependency update: ${cnone}"
 $MDM status
 echo
 echo "${clblack}# And we can do that.  \`mdm update\`: ${cnone}"
 $MDM update
 echo
 echo "${clblack}# Huzzah?  Huzzah! ${cnone}"
 $MDM status
 echo
 (cd lib/projUpstream1/ && ls -l . */ */*/)
 echo
)
echo -e "${clblue} ----------------------------${cnone}\n\n"

awaitack;



echo "${clblue}#  That's all!  Neat, eh?${cnone}"


