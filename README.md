Modern Dependency Management
============================

Core concept is this: use git submodules in a planned and effective way, and you can get dependencies set up in a way that gives you:

* strongly versioned and guaranteed repeatable builds!
* project cloning that gives you exactly everything you need to do a build!
* a repository that doesn't bloat over time, and fresh clones that ship you the bare minimum number of bytes that you need!


### Example Layout

```
	projX/                          <-- GIT PROJECT ROOT.  this project is something projA depends on.
	projX/src/                      <-- and so on as normal
	projX/releases/                 <-- SUBMODULE!  points to projX-releases.  update the version this points to every time you put a release out.  (this isn't strictly necessary, and no one using this project needs to check this submodule out in order to get work done, but it just seems reasonable to bind it here.)
	
	projX-releases/                 <-- GIT PROJECT ROOT.  this repo contains other repos, each of which contains a snapshot of one release.
	projX-releases/v0.1.3.git/      <-- SNEAKY REPO!  this repo only contains one commit, which contains all the files output by the build of projX v0.1.3.  it's a bare git repo with its raw data actually checked in to the projX-releases repo.  it's possible to clone this repo without cloning the entire projX-releases repo by going over raw http!
	projX-releases/v0.2.1.git/      <-- SNEAKY REPO!  this repo also only contains one commit, same idea.
	projX-releases/snapshots        <-- SUBMODULE!  optional pattern: you could actually have a repo here that has 'nightlies' or whathaveyou in it, and go ahead and keep advancing the artifacts in this one.  it might be handy to be able to reference this in another project's dev branch, but you'd probably never point to it for a release or stable branch just because it'd be so big.  (alternately: just lots and lots more single-commit point-release submodules, but we put them in a subdirectory to make prettier browsing of the important ones.)
	
	projA/                          <-- GIT PROJECT ROOT.  this project depends on projX as a library.
	projX/src/                      <-- and so on as normal
	projA/lib/                      <-- still a normal folder
	projA/lib/projX/                <-- SUBMODULE!  points to one of the snapshot repos inside projX-releases (for example, projX-releases/v0.1.3.git).  When you want to get a new release number, you actually commit a change to the url in the .gitmodules file for projA.
```

### Why submodules like that?

Using submodules in ```projA/lib/``` for the specific release of ```projX``` means that when ```projA``` upgrades to newer releases of ```projX``` over time,
```projA```'s repo doesn't grow in size and drag along baggage for the rest of its future history.
In other words, even though we're more or less committing a binary, which would normally be a cardinal sin of version control since it won't diff/compact well,
using the abstraction of the git submodule means that what actually ends up in your permanent git history is a reference to the release and a hash of exactly what we want,
which of course is tiny (no baggage) AND even diffs in a way that's actually perfectly semantic and human-readable.

### Why a releases repo?

Having all of the individual release repos for a project gather into one repo like ```projX-releases``` has a couple of purposes:
* Perhaps most importantly, it's a excellent way to be able to stay up to date.  (What did you think we'd do, resort to an RSS feed or email spam on future releases?)
* It makes it extremely easy for someone to grab all of them at once.
 * This is useful for a paranoid workgroup, because ```projA``` needs someplace to be able to get ```projX```'s releases from every time someone makes a fresh clone of ```projA```,
and that means you want to be safe and secure in case the central/blessed server for ```projX-releases``` goes down.
 * This is useful for just plain efficiency in any environment, since it's easy to make a copy that's in your group's LAN if not on your own local disk.
* This is also handy from the point of view of the folks who publish releases of a library, since they don't have to create new repos willy-nilly everywhere;
   they can happily just keep pushing to the same place.

### Relationship to Build tools

Notice how everything about this system is build-tool agnostic!  You can use ant, maven, makefiles, rakefiles, hoefiles, whatever you want; the dependency storage planning
will work the same for any kind of artifact files.

### Getting it Done

None of the ideas here are particularly radical.  The key is just being organized and having a plan that leverages the existing tools out there to their full potential.
However, admittedly, the steps involved in performing a release with this process are not completely obvious when looking at the man page for "```git submodule```",
and even with the full pattern laid out before you, it's a lot of commands to issue in a particular sequence.  Solution?  Automate it, of course.



*mdm*, Automated!
=================

"```mdm```" is a script that will guide you / automagically perform any of the steps in the lifecycle of releasing.
It also covers updating dependency specifications when releasing.
And finally, ```mdm``` can be used as shorthand when making a fresh clone or performing a pull that changes dependency versions
(though these actions can also be easily performed by stringing some git commands together; ```mdm``` is just being a bit of porcelain when used this way).


Usage: Releasing:
-----------------

```
	mdm release --version v0.1.3 --files ./target/
```

That's about it.  Substitute in the values that make sense for you.

Typically you'll want to run your build process right before the release command.
You can do that manually if you like, but it's probably even easier to integrate it with your build system;
that way you don't have to repeat yourself with the version number or the path to the produced files.
With a typical Ant setup, that might look like this:

```
	<target name="release" depends="clean, dist">
		<exec executable="mdm">
			<arg value="release" />
			<arg value="--version=${version}" />
			<arg value="--files=${dist}" />
		</exec>
	</target>
```

Alternatively, if you're the cautious type, you may wish to perform your build, then inspect the produced files at your leisure, and then call ```mdm release``` only when you're satisfied.


Usage: Initialing a release system:
-------------------------------------------------------

When you're setting up a project to perform releases with with ```mdm```, you need to not only create the git repository for that project, but also a repository for its releases.
```mdm``` will happily automate this too:

```
	mdm init
```

This automatically creates a new repository for releases, and adds it as a submodule to your current project in the ```./releases/``` dir.
The release-repo url defauls to ```./${yourproj}-releases.git``` --
if your project is published at ```https://github.com/username/mdm.git```, the releases repository will be set up to publish to ```https://github.com/username/mdm-releases.git```

Setup of the publishing site for the release-repo is still up to you though -- so for example if you use github, you still have to sign in and click "new repository".  Don't blow your spine out with the strain.

Note that all of this init business is totally optional and you can refuse to do so.
If you want to do some sort of noncanonical setup, the rest of mdm will play nice;
doing releases for example just requires that you run ```mdm``` with an extra argument, a la ```mdm release --repo=../my/weird/path/releases-repo```.

