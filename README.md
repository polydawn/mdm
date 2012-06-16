Modern Dependency Management
============================

Core concept is this: use git submodules in a planned and effective way, and you can get dependencies set up in a way that is:
* strongly versioned and guaranteed repeatable builds!
* checking out a project gives you exactly everything you need to do a build!
* your repository doesn't bloat over time and doing a fresh clone ships you the bare minimum number of bytes that you need!

```
	projX/				<-- GIT PROJECT ROOT.  this project is something projA depends on.
	projX/src/			<-- and so on as normal
	projX/releases/			<-- SUBMODULE!  points to projX-releases.  update the version this points to every time you put a release out.  (this isn't strictly necessary, and no one using this project needs to check this submodule out in order to get work done, but it just seems reasonable to bind it here.)
	
	projX-releases/			<-- GIT PROJECT ROOT.  this repo contains other repos, each of which contains a snapshot of one release.
	projX-releases/v0.1.3.git/	<-- SNEAKY REPO!  this repo only contains one commit, which contains all the files output by the build of projX v0.0.1.  it's a bare git repo with its raw data actually checked in to the projX-releases repo.  it's possible to clone this repo without cloning the entire projX-releases repo by going over raw http!
	projX-releases/v0.2.1.git/	<-- SNEAKY REPO!  this repo also only contains one commit, same idea.
	projX-releases/snapshots	<-- SUBMODULE!  optional pattern: you could actually have a repo here that has 'nightlies' or whathaveyou in it, and go ahead and keep advancing the artifacts in this one.  it might be handy to be able to reference this in another project's dev branch, but you'd probably never point to it for a release or stable branch just because it'd be so big.  (alternately: just lots and lots more single-commit point-release submodules, but we put them in a subdirectory to make prettier browsing of the important ones.)
	
	projA/				<-- GIT PROJECT ROOT.  this project depends on projX as a library.
	projX/src/			<-- and so on as normal
	projA/lib/			<-- still a normal folder
	projA/lib/projX/		<-- SUBMODULE!  points to one of the snapshot repos inside projX-releases (for example, projX-releases/v0.1.3.git).  When you want to get a new release number, you actually commit a change to the url in the .gitmodules file for projA.
```

Using submodules in ```projA/lib/``` for the specific release of ```projX``` means that when ```projA``` upgrades to newer releases of ```projX``` over time,
```projA```'s repo doesn't grow in size and drag along baggage for the rest of its future history.
In other words, even though we're more or less commiting a binary, which would normally be a cardinal sin of version control since it won't diff/compact well,
using the abstraction of the git submodule means that what actually ends up in your permanent git history is a reference to the release and a hash of exactly what we want,
which of course is tiny (no baggage) AND even diffs in a way that's actually perfectly semantic and human-readable.

Having all of the individual release repos for a project gather into one repo like ```projX-releases``` has a couple of purposes:
* Perhaps most importantly, it's a excellent way to be able to stay up to date.  (What did you think we'd do, resort to an RSS feed or email spam on future releases?)
* It makes it extremely easy for someone to grab all of them at once.
 * This is useful for a paranoid workgroup, because ```projA``` needs someplace to be able to get ```projX```'s releases from every time someone makes a fresh clone of ```projA```,
and that means you want to be safe and secure in case the central/blessed server for ```projX-releases``` goes down.
 * This is useful for just plain efficiency in any environment, since it's easy to make a copy that's in your group's LAN if not on your own local disk.
* This is also handy from the point of view of the folks who publish releases of a library, since they don't have to create new repos willy-nilly everywhere;
   they can happily just keep pushing to the same place.

Notice how everything about this system is build-tool agnostic!  You can use ant, maven, makefiles, rakefiles, hoefiles, whatever you want; the dependency storage planning
will work the same for any kind of artifact files.

