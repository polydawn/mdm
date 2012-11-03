Modern Dependency Management
============================

Core concept is this: use git submodules in a planned and effective way, and you can get dependencies set up in a way that gives you:

* strongly versioned and guaranteed repeatable builds!
* project cloning that gives you exactly everything you need to do a build!
* a repository that doesn't bloat over time, and fresh clones that ship you the bare minimum number of bytes that you need!

These properties sound basic when stated clearly, but it's a trifecta that's frankly not well provided by any of the popular dependency management solutions to date.


### The TL;DR version of How It Works

Say you have a software project called myproject, already in its own git repository.
You want your software to use some stuff from someone else's project; we'll call it projUpstream1.

projUpstream1 has a releases repo, where for every version of projUpstream1 released, there is a branch.
That branch contains just one commit, and that commit contains everything made by a build of projUpstream1.

You use the ```mdm``` tool to set up myproject with a reference to a projUpstream1 release.
Magic happens:

```mdm``` fetches just one branch from the projUpstream1 releases repo.
It goes into a folder at myproject/lib/projUpstream1.
This branch from the projUpstream1 releases repo contains the single commit with all the build artifacts for a specific version of projUpstream1,
so you now have all the files you need for your work in myproject.

This commit that ```mdm``` fetched is set up as up a submodule in myproject.
This means that the hash naming that commit is now itself committed to myproject's history, along with some metadata that can tell anyone who clones this project where to fetch that commit from themselves.

Huzzah!
Anyone who clones myproject can now fetch all myproject's dependencies based on git metadata.
And because the hash of the projUpstream1 release artifacts is now embedded in myproject's history,
it's impossible for anyone to be hoodwinked into getting anything other than the *exact* versions of myproject's dependencies that the author intended.


### Anatomy of a Releases Repo History

```

.               <-- branch="master"
.                       The master branch keeps going, and every time a new
.                       release is made, those files are merged in to master.
|
*               <-- commit=07  tag="mdm/master/v2.1"
|\                      moved artifact.jar -> v2.1/artifact.jar
| \   .                 moved artifact.so -> v2.1/artifact.so
|  |  .
|  *  .         <-- commit=06  branch="mdm/release/v2.1"
|  |  |                 added artifact.jar
|  |  |                 added artifact.so
|   \ |
*    \|         <-- commit=05  tag="mdm/master/v2.0"
|\    |                 moved artifact.jar -> v2.0/artifact.jar
| \   |                 moved artifact.so -> v2.0/artifact.so
|  |  |
|  *  |         <-- commit=04  branch="mdm/release/v2.0"
|  |  |                 added artifact.jar
|  |  |                 added artifact.so
|   \ |
*    \|         <-- commit=03  tag="mdm/master/v1.0"
|\    |                 moved artifact.jar -> v1.0/artifact.jar
| \   |                 moved artifact.so -> v1.0/artifact.so
|  |  |
|  *  |         <-- commit=02  branch="mdm/release/v1.0"
|  |  |                 added artifact.jar
|  | /                  added artifact.so
|  |/
|  /
| /
|/
*               <-- commit=01  branch="mdm/init"
                        Nothing really to see here.  This is just the commit
                        created to inaugurate the releases repository.
```

There are two key features of this graph:

1. the release branches don't accumulate each other's history, so you can fetch them independently, without needing to pull down any data from the other branches.
1. the master branch does accumulate history from each of the release branches, so by fetching the master branch, you get *all* of the release branches, which makes it easy to make a local cache for your workgroup or to take backups of things you depend on.
1. when a release branch merges into the master branch, the actual artifact files from the release are moved to a subfolder, so it's easy to have every release ever checked out in one working tree (which in turns makes it dang handy to just throw the whole release repo up on an http server to make direct downloads available).

### submodules keep your history clean

Using submodules in ```myproject/lib/``` for the specific release of ```projUpstream1``` means that when ```myproject``` upgrades to newer releases of ```projUpstream1``` over time,
```myproject```'s repo doesn't grow in size and drag along baggage for the rest of its future history.
In other words, even though we're more or less committing a binary, which would normally be a cardinal sin of version control since it won't diff/compact well,
using the abstraction of the git submodule means that what actually ends up in your permanent git history is a reference to the release and a hash of exactly what we want,
which of course is tiny (no baggage) AND even diffs in a way that's actually perfectly semantic and human-readable.

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
```mdm``` also doesn't actually push any of the commits it creates, so you can manipulate or reset the release commits if something goes wrong, then push when everything is perfect.


Usage: Initializing a release system:
-------------------------------------------------------

When you're setting up a project to perform releases with ```mdm```, you need to not only create the git repository for that project, but also a repository for its releases.
```mdm``` will happily automate this too:

```
	mdm release-init
```

This automatically creates a new repository for releases, and adds it as a submodule to your current project in the ```./releases/``` dir.
The release-repo url defauls to ```./${yourproj}-releases.git``` --
if your project is published at ```https://github.com/username/mdm.git```, the releases repository will be set up to publish to ```https://github.com/username/mdm-releases.git```

Setup of the publishing site for the release-repo is still up to you though -- so for example if you use github, you still have to sign in and click "new repository".  Don't blow your spine out with the strain.

If you're not a fan of github, hosting your own releases is easy and just like hosting any other git repository; you can do it over git://, https://, ssh:// or whatever other protocols git already supports.

Note that all of this init business is totally optional and you can refuse to do so.
If you want to do some sort of non-canonical setup, the rest of mdm will play nice;
doing releases for example just requires that you run ```mdm``` with an extra argument, a la ```mdm release --repo=../my/weird/path/releases-repo```.


Usage: Adding a Dependency:
---------------------------

```mdm add [URL]``` is the general form.
Specifying a version is optional, because ```mdm``` will look for the available versions and interactively prompt you to choose one.
A local name and local path for the dependency can optionally be specified as well.


Usage: Updating all Dependencies:
---------------------------------

```mdm update``` asks mdm to pull the correct versions of all dependencies into the current project.

Run this command whenever you clone a new repo, or pull changes that add or remove or alter dependencies, or whenever you switch between branches that have different dependencies.


Usage: Looking at Dependencies:
-------------------------------

```mdm status``` will list the all of the dependencies managed by mdm in the current project, as well as what's currently checked out in the working tree.
If there are any dependencies that are out of sync with where your current branch wants them to be, warnings to that effect will also be displayed.


Usage: Changing a Dependency Version:
-------------------------------------

```mdm alter [NAME]``` looks at the releases repository for something you already depend on and lets you switch which version your project specifies.


Usage: Dropping a Dependency:
-----------------------------

```mdm remove [NAME]``` removes a dependency from the repo's submodule config and the git hash tree, and tosses that repo.


Usage: just in general...
-------------------------

You can add a ```-h``` to any of these commands and get specific help and a full list of all available options, including descriptions of behaviors and default values.
So for example, ```mdm -h``` will tell you all of the subcommands of mdm; ```mdm add -h``` will list all possible options of the ```add``` subcommand in detail.

Most of mdm commands (and this really shouldn't be surprising!) generate git commits when you ask them to do something.
So, there's a couple of implications of that:

1. you should probably only do things in a relatively clean working tree.  Like, if you're in the middle of doing changes to submodules, you probably shouldn't also use ```mdm alter```, because it's going to commit the .gitmodules file.
1. notice I said "commit" and not "push"!  That means anything mdm does is actually totally easy to back out of if it doesn't quite go according to plan.



Getting MDM
===========

Building from Source
--------------------

Clone this repo, pull down submodules, and then call ```ant``` to build.  In other words:

```bash
git clone https://github.com/heavenlyhash/mdm
cd mdm
git submodule update --init
ant
```

The freshly built ```mdm``` binary will now be located at ```dist/mdm```, ready to go.


Downloading a release
---------------------

Browse the releases repo!

https://raw.github.com/heavenlyhash-releases/mdm-releases/master/



Other Notes
===========

### Artifact File Names

I recommend NOT including the version number of a release in its artifact file name.
I.e., name your release file ```projX.tar.gz```,  not ```projX-1.4.5-SNAPSHOT.tar.gz```.
Why?

- *The* ***version*** *is handled by the* ***version control*** *system.*
- The version number belongs somewhere *inside* the release anyway; it shouldn't be lost if the release file is renamed.
  (Being redundant in this situation is fine, but redundancy is redundant.)
- It's vastly easier to script things, configure IDEs, and so on when you don't have to fiddle with that filename changing.

### Relationship to Dependency Resolution

A person could combine this with automatic dependency resolution.
I'm personally not going to do so at this stage.  There's relatively little point that I can see to automating that,
since once you've adopted this kind project organization suddenly everything about dependencies has become a one-time setup in the lifetime of the project,
and any repository clone already has the hard choices etched into the repository's own structure with no runtime resolution needed.

### Dependencies of Dependencies of Dependencies of...

Dependencies are all being put in a flat "lib" dir in the example.
What if there's a situation with a myproject -> projX -> projY dependency, you ask?
Keep It Simple, Smartass.  Paths like ```myproject/lib/projX``` and ```myproject/lib/projY``` still do the job just fine.

You may have a fleeting thought that it would be super cool if including one library submodule would also include all the library submodules that it may in turn depend on.
This is a bad idea.  Quash that thought now.
Why?  Two reasons:

1. Because software dependencies are a graph.  A filesystem, and in this case git submodules, are a tree.
   Trees are a graph, but a graph is not always a tree -- in other words, there's a fundamental mismatch between what you want to describe and what the medium is capable of.
1. At runtime, are dependencies resolved as a graph?
   No, they're not.  Things are pretty much thrown in a heap on the includepath/classpath and after that it's up to a language's namespacing to work it out.
   Given that, there's no sense to trying to store your dependencies in a graph, since you're just fibbing to yourself anyway.


