Modern Dependency Management
============================

`mdm` is a a distributed, repeatable, and secure management system for your dependencies.

`mdm` helps makes hard guarantees about the repeatability of your builds, because every dependency is tracked by hash as well as the semantic version name.
`mdm` weaves a distributed dependency management system, leveraging git features for distributed tracking, content-addressible storage, effective deduplicaton, and a wide range of supported transports.

Practically speaking, that means `mdm` goes fast because it spends most of its time working totally offline.
It also means `mdm` is immune to accidentally [getting cat memes](http://blog.ontoillogical.com/blog/2014/07/28/how-to-take-over-any-java-developer/) in your build :)

Now in bullet points:

* Strongly versioned and guaranteed repeatable builds!
* No repository bloat or gigantic vendor commits!
* Dependency fetching is automatically optimized to ship the smallest download possible!


full manual
-----------

Jump to [docs](doc/), with chapters covering:

1. [Getting Started](doc/1-getting-started.md)
2. [Syncing Dependencies](doc/2-using-to-develop.md)
3. [Making Releases](doc/3-using-to-release.md)
4. [Advanced Topics & Theory](doc/4-advanced-topics-and-theorycraft.md)
5. [Errata & Compatibility](doc/5-errata-and-compatibility.md)




### TL;DR How It Works

Your project is a git repo.

Each of your dependencies lives in a little git repo, all by itself.

Your project tracks a link to each of these little git repos.  It remembers the hash of their contents, and it remembers where to clone them from if they aren't here yet.



### Wanna see it?

Clone this project repo.  Run `ant`.  Observe :)



### Hosting mdm Releases

Any git host can be an mdm host.  Github, Stash, gitolite, gitosis, git-daemon, Gitblit, Gitbucket, even a flat static http server.

Anything.

##### Mirroring an mdm Release Repository

Try `git clone --mirror http://mdm-releases.com/junit/junit-releases.git` :)

##### The mdm Central Repository

A central repository of software packaged as mdm releases, ready for your consumption:

**[http://mdm-releases.com/](http://mdm-releases.com/)**

(natch, this is not quite as expansive as some other central package repository sites yet :) working on it!)



### What do I need this "security" stuff for, anyway?  I use HTTPS.

SSL can mitigate the thread of direct MITM attacks, but it's not a full solution either, for either security or more mundane repeatability guarantees.

SSL is transport layer security -- you still fully trust the remote server not to give you cat memes.  (Or, put less nefariously, you still fully trust the remote server to never let anyone publish a "quick fixup re-release" that might quietly break your production deploys.)

Embedding the hash of the dependencies we need in our projects directly gives us end-to-end confidence that we've got the right stuff.



### Say, doesn't this sound like git submod--

Shh!  Yes.

These git repos that track each of your dependencies in isolation are really just git submodules in fancy clothes.
`mdm` adds some tricks that makes it scalable with binaries, and, well, the fancy clothes.

We get all of the benefits of working with git (rapid verification, `git status` helpfully reports changes (!), familiar semantics for distributed mirrors, tons of transports and authorization systems supported out of the box, caching different versions of a dependency can compress and dedup, and on and on).
All of the messy parts of managing these submodules is handled for you by mdm commands.

The `mdm` commands translate your high-level intentions (that is, "depend on junit, version 4.10!")...

...Into the mechanics ("remember to fetch tag 'release/4.10' from 'http://mdm-releases.com/junit-releases.git', and it should have hash 6e4860d64a2912c7e10639c87f48a93e1748f797").

(If you're wonderring what `mdm` is doing that leverages git while handling binaries scalably, you can check out the documentation on [Anatomy of a Releases Repo History](doc/4.4-anatomy-of-releases-repo-history.md).)



### Language and Build-Tool Agnostic

Everything about mdm is build-tool agnostic!
You can use ant, maven, makefiles, rakefiles, hoefiles, whatever you want;
the dependency storage planning will work the same for any kind of artifact files.








mdm command reference
=====================

"```mdm```" will guide you, automagically performing any of the steps in the lifecycle of releasing, creating dependency links and managing versions of dependencies, and syncing down dependencies into a new project clone.

Each major task is a subcommand of `mdm`.
You can see all subcommands of mdm by running `mdm -h`.
You can add a `-h` to any of these commands and get specific help and a full list of all available options, including descriptions of behaviors and default values --
so for example, `mdm add -h` will list all possible options of the `add` subcommand in detail.

This is the total set of available tasks:

 - mdm update
 - mdm status
 - mdm add
 - mdm alter
 - mdm remove
 - mdm release
 - mdm release-init


### Usage: Syncing all Dependencies:

```mdm update``` asks mdm to pull the correct versions of all dependencies into the current project.

Run this command whenever you clone a new repo, or pull changes that add or remove or alter dependencies, or whenever you switch between branches that have different dependencies.


### Usage: Looking at Dependencies:

```mdm status``` will list the all of the dependencies managed by mdm in the current project, as well as what's currently checked out in the working tree.

If there are any dependencies that are out of sync with where your current branch wants them to be, warnings to that effect will also be displayed.


### Usage: Adding a Dependency:

```mdm add [URL]``` adds a new hash and submodule config to your project, linking a dependency provided from the remote URL.

Specifying a version is optional, because ```mdm``` will look for the available versions and interactively prompt you to choose one.
A local name and local path for the dependency can optionally be specified as well.


### Usage: Changing a Dependency Version:

```mdm alter [NAME]``` looks at the releases repository for something you already depend on and lets you switch which version your project specifies.


### Usage: Dropping a Dependency:

```mdm remove [NAME]``` removes a dependency from the repo's submodule config and the git hash tree, and tosses that repo.


### Usage: Initializing a release system:

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


### Usage: Releasing:

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





Getting MDM
===========


Downloading a release
---------------------

The latest version is v2.18.0, available here:

http://mdm-releases.net/net.polydawn/mdm-releases/v2.18.0/mdm

Or, alternatively, mirrored here:

https://raw.githubusercontent.com/mdm-releases/mdm-releases/master/v2.18.0/mdm

You can browse the whole releases repo on github!

https://github.com/mdm-releases/mdm-releases/


Building from source
--------------------

If downloading a binary release isn't your cup of tea, [Section 4.1 -- building from source](doc/4.1-building-from-source.md) documents your way.


Portability
-----------

See [portability](doc/5.1-portability.md).



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

`mdm` does not require a transitive dependency resolution system at runtime because
any repository clone already has the hard choices etched into stone with no runtime resolution needed.

It would be possible to create a transitive dependency resolution system and use it to drive `mdm [add/alter/delete]` commands.
This would hit a sweet spot where dependencies are still firmly pegged, but both new project setup and later updates can be specified with a convenient handwave.
(This might come out looking something like the distinction between a "Gemfile" and "Gemfile.lock" -- mdm currently only provides the "lock".)

PRs welcome on this front :)

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


