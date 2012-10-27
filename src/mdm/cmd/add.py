
from mdm.imp import *;
from mdm.cmd.helper import *;

def add(args):
	# check we're in a repo root.  `git submodule` insists that we must be at the top.
	if (not cgw.isRepoRoot(".")):
		return exitStatus(":(", "this command should be run from the top level folder of your git repo.");
	
	
	# git's behavior of assuming relative urls should be relative to the remote origin instead of relative to the local filesystem is almost certainly not what you want.
	if (args.url[:3] == "../" or args.url[:2] == "./"):
		print >> stderr, "hey, heads up: when you use a relative url to describe a submodule location, git assumes it's relative to the remote origin of the parent project (NOT relative to the project location on the local filesystem, which is what you might have expected).  this... works, but it's not recommended because of the potential it has to surprise.\n";
	
	
	# pick out the name.  if we can't find one yet, we'll prompt for it in a little bit (we try to check that something at least exists on the far side of the url before bothering with the name part).
	name = None;
	if (args.name):		# well that was easy
		name = args.name;
	else:			# look for a discernable project name in the url chunks
		urlchunks = args.url.split("/");
		urlchunks.reverse();
		for chunk in urlchunks:
			tehMatch = re.match(r"(.*)-releases", chunk);
			if (tehMatch):
				name = tehMatch.group(1);
				break;
		# prompt for a name if we don't have one picked yet.
		if (not name):
			name = raw_input("dependency name: ");
	
	
	# we shall normalize a bit of uri here into the local uri.
	#  it's rather disconcerting later if we don't (some commands will normalize it, and others won't, and that's just a mess), and we also want to check for local existance of a submodule here before getting too wild.
	path = relpath(join(args.lib, name));
	
	
	# check for presence of a submodule or other crap here already.  (`git submodule add` will also do this, but it's a more pleasant user experience to check this before popping up a prompt for version name.)
	#  there are actually many things you could check here: presence of files, presence of entries in .git/config, presence of entries in .gitmodules, presence of data in the index.  we're going to just use our default pattern from isSubmodule() and then check for plain files.
	if (cgw.isSubmodule(path)):
		return exitStatus(":(", "there is already a submodule at "+path+" !");
	if (os.path.exists(path)):
		return exitStatus(":(", "there are already files at "+path+" ; can't create a submodule there.");
	
	
	# if a specific version name was given, we'll skip checking for a manifest and just go straight at it; otherwise we look for a manifest and present options interactively.
	version = None;
	if (args.version):	# well that was easy
		version = args.version;
	else:			# check the url.  whether or not we can get a version manifest determines its validity.
		version = promptForVersion(args.url)
		if (version is None):
			return exitStatus(":(", "no version_manifest could be found at the url you gave for a releases repository -- it doesn't look like releases that mdm understands are there.");
	
	
	# check that the remote path is actually looking like a git repo before we call submodule add.
	if (not cgw.isRepo(join(args.url, version+".git"),  "refs/tags/release/"+version)):
		return exitStatus(":'(", "failed to find a release snapshot repository where we looked for it in the releases repository.");
	
	
	# do the submodule/dependency adding
	mdm.plumbing.doDependencyAdd(path, args.url, version);
	
	
	# commit the changes
	git.commit("-m", "adding dependency on "+name+" at "+version+".");
	
	
	return exitStatus(":D", "added dependency on "+name+"-"+version+" successfully!");


