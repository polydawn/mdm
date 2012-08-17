Alternatives to MDM
===================

```mdm``` is not the first attempt in the world to solve dependency management, nor will it be the last.
Here's some ways it stacks up with the competition.



Build/Release/Dependency Tools
------------------------------

(Incidentally, note that I'm lumping all three of those categories together.  I'd rather *not* do so.  To me, build should be quite distinctly separate from release and dependency.
```mdm``` of course is built that way, but many other previous approaches to the dependency management issue have conflated the issues.)


### Ant

Ant isn't really a dependency management tool.  It's just a build tool.  So really, it doesn't compare.  However, see also the sections on "Dodging the Problem" and "Ant+Ivy".


### Maven

Maven is a dependency manager and a build tool wrapped into one.

Maven refers to things by version numbers, and downloads artifacts from webservers.
It produces checksum hashes of release artifact files, but they live on the webserver next to the release files themselves; it doesn't support having the checksums kept within your project,
so there's nothing but convention and a prayer that keeps "projZ-3.2.3" referring to the same binary blob over time.
(In fact, if you've had an introduction to computer security, you might notice that there's nothing but a prayer that's keeping you from downloading a virus, really.
Since the textbook recommendations of maven repositories are over unencrypted http urls, and the hashes are on the remote server, you're bootstrapping your trust there from *absolutely nothing* &mdash; you're praying for the security of both all of the maven repositories you used, as well as putting 100% faith in every router on the internet between them and you.)  
**Contrast to ```mdm```**:
mdm's design means that hashes of the stuff you depend on ends up in your own repo, so as long as someone got a copy of your repo, they'll be able to be completely certain that all of the artifacts they download are exactly the ones you intended to sign off on.

Maven can resolve dependencies recursively.
This is both cool, but has been known to be a source of potentially weird behavior, such as when two third-party libraries depend on other third-party at different versions.
Project maintainers also tend to list everything that any branch of their project could ever need, and sometimes that's substantially more than what you yourself actually use.  
**Contrast to ```mdm```**:
mdm doesn't solve this problem for you; whether or not you consider that to be creating more manual work up front or to be dodging a bullet with regards to long term maintainability is up to your own personal interpretation.

Maven caches artifacts it downloads in your home directory.
That's kinda neat, since it can save you from redownloading artifacts when you work on more than one project that depends on the same stuff.
However, it also means the concept of a clean build can be a little odd, since ```mvn clean``` never causes maven to revalidate those caches.  
**Contrast to ```mdm```**:
as said before, mdm by design takes hashes very seriously, so the concept of an unclean artifact cache simply doesn't exist.
mdm does re-download things if you use the same dependencies in multiple projects, but you can "cache" by making a local clone of the dependency's releases repo.

Maven introduces network latency into pretty much every single thing it does.
It also updates its own internal components in the same way as it checks for updates to other artifacts,
which means that even when doing something as simple as asking it to delete a local directory can cause maven to contact the network and perform time consuming operations.  
**Contrast to ```mdm```**:
mdm contacts the network when you ask it to and *only* when you ask it to, so if you mdm together with a build tool that also doesn't have this problem, then you're golden; and if you use a build tool that hits the network whenever it feels like it, then you must really like waiting around.


### Ant+Ivy

Ivy is a dependency management system build to work with Ant.
By and large, it's pretty much Maven all over again (it uses the same release structure, version labelling, transports, and caches),
so all of the comments and comparisons to Maven's dependency handling apply here as well.


### Various language-specific (or language-emphatic?) solutions

PHP has [Composer](http://getcomposer.org/)+[Packagist](http://packagist.org/),
Node has [npm](https://npmjs.org/),
Python has [pip](http://www.pip-installer.org) and [easy_install](http://packages.python.org/distribute/easy_install.html) and [pypm](http://code.activestate.com/pypm/) and I-don't-even-know-how-many dependency thingies,
and similarly I'm not even sure I could compile an exhaustive list of package managers meant for use with Ruby and/or Rake.

Frankly, I haven't looked at any of these very much.  Two reasons:

I don't understand why a dependency system should know about or give a damn about the language anything is written in.  The build tool, sure;  the dependency manager, no.  
mdm doesn't know and doesn't care what language your artifacts are in or what the files look like, or even if they're entire directory trees versus individual files.
mdm is in use right now with python projects, java projects, php projects, and projects that have a mishmash of a dozen other languages and formats, and it works the same for all of them.

A lot of dependency systems also seem to degenerate into things that just download artifacts,
and then suggest that you either consider this good enough,
or that you then commit them to your repository if you want guaranteed repeatable builds.
In other words, this is back to either choosing between all of the problems with maven-style online resolution (i.e. zero-security and zero-consistency-guarantees),
or the "Dodging the Problem" solution (see below) with its painful bloating.  
mdm gives you strong versioned control over dependencies and exact hashes of artifacts, and doesn't suffer bloat.

There are several tools and approaches that work great as long as you're only ever worried about depending on things that look like source (i.e., it diffs well, so drawing it into your own repository history isn't a problem).
These typically revolve around subtree merging approaches or using submodules in simpler ways than mdm does.  
mdm is designed to accommodate the harder class of problems posed by binary release artifacts that don't diff well... so working with dependencies that are source-only turns out to be a degenerate case that mdm can handle easily, but if that's all you need ```mdm``` may also turn out to be overkill (then again, you may also just like the release strategy).



Dodging the Problem (a.k.a Commit All The Things)
-------------------------------------------------

Some projects resort to just checking in all the binaries for their dependencies and dodging the entire issue that way.
This is simple; it works; in some cases it's pretty painless.  I've done it before and sometimes I don't regret it.  (Sometimes I do regret it).

The issue with this is of course that if you update your dependencies, your repository history begins to bloat over time.
For some projects, this isn't a very serious practical concern.
However, if your project has large dependencies or updates them more than a handful of times in the project's lifetime, then the amount of disk space and network bandwidth you're wasting when someone does new clone of your project gets pretty big.
Whether or not you're using a DVCS (like git) changes whether these costs are heavier on a central server or on clients, but regardless of where you push the problem you're still accumulating waste somewhere.

On the other hand, if you regularly do need to check out very old versions of your project that use dependencies other than the modern ones, dodging the problem like this can actually work great,
since those binaries are available instantaneously and locally (assuming you use a DVCS like git).



Weak Points in ```mdm```
------------------------

```mdm``` assumes you use git (as you might have noticed by now).  That's a fairly serious assumption.
If your project doesn't use git, you're going to have a really hard time extracting any use out of ```mdm```.

```mdm``` also hopes that the projects you want to depend on have published their releases in a way ```mdm``` understands.
This isn't any more or less of an assumption than maven projects make, though, and the maven ecosystem has done just fine.
If a project doesn't support ```mdm```-style releases yet, just make a releases repo for that project yourself (or better yet, fork the original project and give them a pull request so that in the future there's a central authority for that project's ```mdm``` releases).

If you want to ```git checkout``` back and forth between two branches that use different versions of a dependency, it's a little messy.
Since you're effectively switching between two different snapshot repositories (which by design have no history in common), ```mdm``` is going to be a little confused.
In practice, this doesn't seem come up as a major issue very often in normal workflows, and if it does, there are two fairly simple ways to work around it:
you can just have two clones of your project repository,
or you can pull down the releases repository for your dependency to local (or just nearby) and thus make the copies cheap.  A local copy of the releases repository means that git will actually resort to hardlinks, which will be both extremely fast and not even cause duplicated disk space usage.

The use of git submodules introduces very tight coupling of a program to specific versions of its dependencies.  Some have argued this is a Bad Thing, and you should always aim to specify the widest possible range of acceptable dependencies.
Then again, since the onus of choosing the exact version of dependencies is given to every project with ```mdm``` (as opposed to systems that invoke automatic and recursive dependency resolution), the problem doesn't seem to be pronounced in quite the same way.



Alternatives to MDM's submodule patterns
========================================

```mdm```'s way of putting bare git data structures in a repository with the expectation of cloning them back out from the working tree over a dumb/raw transport is... admittedly a little indirect.
There are other interesting ways to put git to use to solve related problems; ```mdm```'s way was chosen over several considered alternatives as the most powerful and general way to solve the problem, but for some specific situations these other options may be of interest.

In particular, if you're working with a language where you never ever deal with compiled artifacts and only ever bring libraries in as source, then while ```mdm``` will still work for you, it may be overkill.



Just Using Submodules
---------------------

Just using git submodules directly is totally fine if your project can work by just bringing things in as source.
You might notice that mdm does this itself!  Since it's convenience to bring in the pbs library that mdm uses as a source thing, that's how it's done.
Where mdm shines is when you're dealing with large or binary artifacts, or some other situation where the releases don't diff well from one another.

Btw, note that running `git submodule update --init` on a freshly cloned repo will happily load up all of mdm dependencies as well as deal with normal submodules in one motion, which just has kind of a tingling joy of simplicity to it.



Using Branches instead of Sneaky Repos
--------------------------------------

I wish this worked!  Your heart might have given a quiver of hope when you noticed that there's a "-b" option for `git submodule add`.  Alas, it's not so simple.
Adding a submodule with a branch argument still clones all the branches, which means it wouldn't be possible to use branches to avoid bandwidth issues.
Furthermore, no mention of the branch ends up as even so much as a footnote in .submodules or in .git/config, which means more exotic configuration would have to be contrived and a project using mdm would lose the venerable ability to be built by a newcomer with nothing more than ```git submodule update --init```.


