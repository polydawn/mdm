CHANGELOG
=========

v2.xx.x (unreleased)
--------------------

- `mdm update` learned a `--strict` option, which causes it to exit with a non-zero status code in the event that fetching a library version my name resulted in a hash not matching the one committed in the project.  (As before, this scenario will always generate warnings, but without the `--strict` option it will exit with 0/success.)
- `mdm update` will now remove dangling dependencies from your working tree, such as after switching to a branch that does not link a dependency.  (Git will not remove such dangling repos because git is -- quite reasonably -- paranoid about deleting other git repos.  Previously, mdm agreed with this stance.)
  - This will refrain from removing an unlinked dependency directory from your working tree if it has uncommitted changes, and issue a notice instead.
  - Only submodules managed by mdm are subject to this policy.  Any other submodules or plain git repos sitting in your working tree will be untouched.
  - Upgrade note: new project clones will recieve this benefit automatically; dependencies first fetched by previous versions of mdm may not be recognized, and will not be deleted.
- Fix incorrect warning about hash-mismatches issued when using `mdm update` during an ongoing merge -- previously, mdm would check against the hash on the *incoming* branch, which would give false warnings when intentionally choosing the dependency version from the current branch.  mdm will now consider a hash from any of the merging branches to be valid.
- Asking `mdm add` to place a dependency in a gitignored directory now works.  (Though I don't particularly know why you'd do that.)
- Improve rejection of invalid version names in the `mdm release` command.  (Previously, a ref could have been created, but release would still fail when assertions were made later in the process, which would leave a ref which native git would never have admitted; this no longer occurs.)



v2.17.4
-------

- Add support for ssh transport to use interactive prompts for passwords.
- Add support for ssh transport to use encrypted private keys, using interactive prompts for the encryption passphrase.  (See the [jsch readme](http://jcraft.com/jsch/README) for a full list of supported ciphers.)
- Add support for ssh transport to contact unknown hosts if confirmed at interactive prompt.
  - The exception will not be permanently added to a system known_hosts file by mdm.  But you will see the key fingerprint, so you can validate the system yourself and take appropriate steps.
  - In previous releases, mdm would simply fail to establish ssh transport if the hostkey was unrecognized.
- All of the above prompts will be skipped if there is not attached interactive terminal; the (unanswered) prompt will still now be logged however (so you can easily see if your jenkins job is failing because of an unrecognized host key, for example).



v2.17.3
-------

- Windows support!
  - Consider this preliminary, but all basic issues with path uniformity are now addressed, enough to enable successfully using `mdm update`, `mdm alter`, etc, in a Windows environment.  So if additional issues are found, you should have all the tools necessary to make contributions from Windows ;)
  - Use of absolute local filesystem paths in any git configuration is not recommended; significantly remapped filesystems may also pose challenges (namely, cygwin's translation layer between '/cygdrive/c/' and 'C:/' is likely to create amusing issues when programs from a cygwin context versus the native windows context look at config files they share).



v2.17.2
-------

- Fix regression in v2.17.1 where asking mdm for '--help' or '--version' outside of a git repo would result in a demand for a git repo.
- Detect the presense of a terminal and format output accordingly: `mdm update` will use the fancy terse progress output in the presense of a terminal, and fall back to plain line-after-line printing if a terminal is not detected.



v2.17.1
-------

- Exit with success code when requesting alteration of a depedency version to what you already have.
- Calmly report and exit when altering a version encounters dirty files in a dependency's working tree.
- Bugfix for crash when removing a dependency's checked out files and then running `mdm update` again (mdm would try to 'create' the submodule repo, even though it would still exist in the parent repo's .git/ data dir) introduced by v2.17.0.
  - If you need to work around this in v2.17.0, you can simply remove the files under the parent repo's `.git/modules/{the-dep-path}` directory -- or, as with most things, just reclone for an aggressively clean slate.
- Fix for handling operation inside a repository that is itself a submodule (or otherwise has an unusually advanced configuration with a relocated git data dir) (also a new concern from v2.17.0's separation of git dir from working tree).
  - If you need to work around this in v2.17.0, you can do so by taking the n-1'th layer of submodule (what mdm would normally consider "the project dir"), and making sure its `.git` path is a real directory (one way to do this is by doing a git-init/git-clone into place directly instead of using the git-submodule-update mechanics, which as of git>=1.7.8 generate relocated git dirs the same way mdm does).



v2.17.0
-------

- The `mdm update` command now reports each of the dependencies as it proceeds.  When doing an update on a project with a lot of dependencies, there's now a pulse visible during the work.
- Git data directories are now stored in the parent project's git data directory!
  - This is a significant improvement in that your entire lib/* directory can be blown away, and yet the cache of locally available repositories and their commits remains available.
  - In particular, this means after switching to a branch without a dependency, you can `git clean -xdff`, yet when switching back and "fetching" the dependency again, no fetch is necessary; all the data is still locally available.
- Added enhanced support around use of URL "insteadof" git config: `url.[...].insteadof` and `url.[...].pushinsteadof` git config chunks in the parent repo's config will now be copied into a dependency module's config.
  - This means it's easy to configure url replacements on a project and have `mdm update` pick up the replacement on the very first use.
  - Additionally, git config parameters under "`url.[...]`" from the system and user gitconfigs are now accepted by mdm.
  - If you're not familiar with this feature of git: play around with `git config url."file:///my/local/cache/".insteadof "http://mdm-releases.com/"`.
  - Note that "insteadof" config per repository was already supported by mdm previously; the change here is merely the convenience of injecting config from the parent into submodules to smooth initialization.
- Improved deployment: binaries from v2.15.0 and later were sometimes not considered as valid executables in some contexts on POSIX (i.e. linux, mac) systems; this is now fixed.
  - Specifically, if executing mdm via the execlp, execvp, or execvpe syscalls, everything would be fine; when using the execl, execle, or execv syscalls an error would be encountered.  Practically speaking, exec'ing in java doesn't encounter problems, nor of course does the shell; I did however find that exec'ing in golang would hit the stricter behavior.  See `man exec` for additional entertainment.
- Upgrade jsch dependency to to version '0.1.51' (from '0.1.49').



v2.16.1
-------

- Fix exceptions when attempting to use ssh transports.
  - Configure proguard's minimization of release builds to explicitly retain jsch (the ssh client library) classes (this library is referred to by reflection, and so is needed in more situations than proguard can statically detect).



v2.16.0
-------

- Give a meaningful error messages in case of parse error in a .gitmodules file.
- Emit a warning if running `mdm update` leaves a submodule checked out on a different hash than the parent repo expects.  `mdm status` would already report the mismatch after the fact, but if it occurs during an update, it most likely indicates either a misconfiguration, or shenanigans upstream.
- When a dependency already has a version name locally, do not engage transport at all.  (Previously the remote origin would still be contacted; near zero work would be performed, but it was still Some Network where it should be None Network.)
- Show messages from problems encountered by `mdm update` while manipulating dependency repos instead of just a nondescript count of problems.



v2.15.0
-------

- Improve detection of mdm release repositories (accept an mdm/init branch from the origin remote as valid; this removes an extra setup step between cloning a release repo and being able to perform new releases).
- Update to release process: we now ship a single executable.  Linux/mac environments should be able to execute this file directly.  (A `java` command on the system path is still required.)
  - This removes the shell script previously included for use on linux/mac environments.
- Upgrade jgit dependency to version '3.3.0.201403021825-r'.



v2.14.1
-------

- Fix issue where fetching from packfiles containing unrequested objects could create unnecessary refs in the local repo.
  - This issue could previously appear when using a 'dumb' git transport on an aggressively packed remote repo (it was otherwise unlikely to cause problems since smart transports repack during transport), and could cause use of unnecessary repository space on disk.
- Upgrade jgit dependency to version '3.3.0.201403021825-r'.
- Additional testing; see git log for details.

(Side note on git transports: using mdm with an aggressively packed remote repo over a 'dumb' git transport is inadvisable, as it is likely to use unnecessary amounts of bandwidth; this is the nature of git dumb transports, not of an issue of mdm.
Use of smart transports is recommended for most cases; fortunately this is what almost all currently popular git hosting services provide.
Use of dumb transports is ironically recommended for advanced users only, and interested parties should read the man pages for git repack and git fetch carefully.)



v2.14.0
-------

- Perceive the version of a dependency module from branch name.  Previously this was parsed from tags on release commits; these tags are now ignored (though they are still created).
- Fix problems operating on repositories with a releases repo link but missing a fully constructed releases repo.
- Various bugfixes; additional testing; see git log for details.



v2.13.0
-------

- Executable permission bits are now preserved and committed during `mdm release`.
- Empty directories are no longer carried along when copying files into a releases repo during `mdm release`.  This leaves the release repo in a state more consistent with fresh clones.
- Hidden files and directories are now carried along when `mdm release` is used on a directory.
- Releases of mdm are now packed with ProGuard, resulting in smaller binaries.
- `mdm release` command can now be used without being located in a repository root.
- `mdm release` command now rejects repositories with any uncommitted changes.
- Fix username not being picked up from the system for use in commit messages.
- Various bugfixes; additional testing; see git log for details.



v2.12.0
-------

- Releases of mdm now include two files: 'mdm.jar', and a shell script called 'mdm'.  (Previously, only the jar file was included in the release, and it was named 'mdm'.  This worked well on systems where the default binfmt recognized jars, but was irritating on other systems, and thus we now include a wrapper shell script in the release.)
- Fix bug in handling submodules that aren't managed by mdm.



v2.11.0
-------

- Upgrade jgit dependency from an unofficial build to version '3.0.0.201306101825-r'.
- `mdm release-init` now accepts existing empty directories as clean target locations for creating a releases repo.
- Fix bug when updating project repo's link to release repo if the path is non-default.
- Unexpected errors are now logged to a file instead of vomiting stack traces at the end user.
- Various bugfixes; significant refactoring to module handling; additional testing; see git log for details.



v2.10.0
-------

- Complete rewrite of mdm.
  - mdm is now implemented in java, and internally uses jgit.
  - mdm no longer performs any exec'ing of a system install of git, which dramatically increases its reliability, and also means it can be distributed statically without any further dependencies aside from a jvm (version 1.6 is sufficient).
  - mdm is now cross platform.

Note that despite being a complete rewrite of mdm, the major version number is *not* incremented.
The mdm release repository layout is *completely unchanged*.
The python implementation of mdm may continue to be used; in fact the java and python implementations may be used interchangeably, side by side.



v2.1.2
------

- Final released version of the python implementation of mdm.
- The fog of history grows thick here: consult the git log for detailed information about this and earlier versions.


