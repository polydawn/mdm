Roadmap
=======

The mdm v2.x series is already a stable format.  It will remain stable for the foreseeable future.

The mdm v3.x series is roughly planned.  Key features will be improved support for deleting releases (a feature which should *not*, it's worth mentioning, be particularly useful in a stable release flow.  Nonetheless, as an administrative option, the mdm v3.x series will make it well-supported).

The mdm v3.x series will *maintain the same format of releases* in terms of the content-addressability and essential format as the v2.x series.  That is, all released data currently tagged in the mdm v2.x format will *remain accessible* and *will not require migration strategies* to fit the v3.x format.  Hashes on release commits will explicitly remain unchanged, and the "convergence" property will also remain identical across both the v2.x and v3.x series.  Other metadata however, such as the current layout of the 'master' branch in a release repo, is not guaranteed.  (Disruption expected from this is minimal, since at no point has mdm explicitly relied on the format of the master branch).  mdm v3.x is also expected to add a variety of new metadata which the v2.x series is not required to understand.

The current plan is to navigate a smooth transition by adding planned v3.x features to the upcoming v2.x builds as options that can be enabled with special flags.  Additionally, a backwards-compatibility filter for making v3.x series release repos visible as v2.x series repos may be provided.



Supporting Release Retraction
-----------------------------

Supporting retraction (i.e., deletion) of a release is a key feature for mdm v3.x.

### Component 1: command support

### Component 2: disconnection from history graph

### Component 3: tag retraction

### Component 4: communication assistance

Releases shouldn't disappear without warning -- even with support for this, the commands will generate many extremely loud warnings.  For "releases" that are known up front to be temporary (i.e., mdm is being used as a convenient placeholder for other binary file transport), some bit of metadata should be able to clearly convey this to consumers.

### Component 5: migration strategy

While this feature will not disrupt the existing content-addressability scheme for release commits, it does substantially change the shame of the master branch (as dictated by Component 2).  A migration strategy may be required, if any releases predating these changes are to be removed from history, and such a migration strategy would be officially destructive, since by definition it has to remove refs that point to the old history graph.

Decisions still need to be made here.  It's possible that a migration strategy can simply be ignored, since at no point was permanent history discard promised as a supported feature in prior versions of mdm; but let's not take the high road.  A gentle hybrid option might include adding an 'mdm/legacy-master' branch ref, which keeps all the old master commits reachable in order to placate references to release repo master branches embedded in project source repos.



Slimming the 'master' Branch
----------------------------

### Component 1: drop creation of 'mdm/master/*' refs

We don't need these.  Just drop them, flat out.

### Component 2: drop creation of '{releaseversion}/' folders

In existing versions, these folders are created on the master branch, and then contain the full set of release files.  This means that 'ls' is enough to see the list of versions available, and having a static downloads site is as simple as checking out the master branch... but the downside is that the master branch becomes unlimited in size on disk.  The costs are outweighing the benefits; we're going to remove this.

Note that this is related to a component of supporting release retraction (namely, that release commits shouldn't merge into master).

This should also be an easy migration.  No version of mdm has ever programmatically depended on these contents of the master branch in any significant way.

### Component 3: new metadata channels

Something somewhere still has to appear to record changes.  It should chronicle the author and date of changes (like git commits normally do) and should contain the mapping of ref name to commit hash.  The latter seems redundant (and so it is), but provides insulation against release retraction actually permanently discarding data.

Decisions still need to be made here.  We need to strike a balance between recording enough information to be a useful audit log, but on the other hand redundant datastructures that go unchecked may not be desirable because of the usability issues if they diverge.

### Open Questions

- should the master branch be handled at all?
- should a different branch (i.e., 'mdm/metadata') be used to contain mdm forms?
- while we're reconsidering the concept of branches... should we invade a different section of the refs spectrum entirely?  Github does this to keep PRs out of sight of regular git clones, for example; it's a good idea.
  - migrating from existing strategies would require care.  If we don't make this change, there's actually not a thing about the release commits and their ref names that makes a major breaking change.
  - some research needed: I'm not actually sure how (indeed, if) this concept applies to tags.


