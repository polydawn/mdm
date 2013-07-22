package us.exultant.mdm.util;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import org.apache.commons.io.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;
import us.exultant.mdm.test.*;


/**
 * Experiments with wildcard matching on filenames.
 *
 * This was originally considered for use in the `release` command for the 'files'
 * argument. However, I've come to think that a single wildcard argument isn't all that
 * helpful; in the future there should be multiarg support, or there will be a argument
 * that takes a single file that lists what to include (which could be a tempfile, or just
 * a `<(find -name "redirect\*")` bash substitution sort of thing) much like javac's
 * "@filename" behavior.
 *
 * And at the end of the day, I still think almost any sane build process puts all
 * materials of a release in a single dir anyway, and even if it doesn't users are likely
 * to find it more straightforward to simply do so than explore advanced options of mdm,
 * which makes the whole thing moot.
 */
@RunWith(OrderedJUnit4ClassRunner.class)
public class PathFilteringTest extends TestCaseUsingRepository {
	public static File trimLeadingPwd(File f) throws IOException {
		String nonsense = new File(".").getCanonicalPath();
		if (f.getPath().startsWith(nonsense))
			return new File(f.getPath().substring(nonsense.length()+1));
		return f;
	}

	/**
	 * {@link FileUtils#listFilesAndDirs} conflates the issues of directory filtering
	 * and subdirectory traversal. This doesn't; it always recurses.
	 */
	private static Collection<File> selectInputFiles_recursive(File directory, FileFilter filter, Collection<File> files) {
		for (String s : directory.list()) {
			File f = new File(directory, s);
			if (filter.accept(f))
				files.add(f);
			if (f.isDirectory())
				selectInputFiles_recursive(f, filter, files);
	        }
		return files;
	}

	@Test
	public void anythingWorksAtAllEvenSlightly() throws Exception {
		FileFilter filter = new WildcardPathFilter(new File(".").getCanonicalPath()+"/*");
		IOForge.saveFile("alpha", new File("a").getCanonicalFile());
		IOForge.saveFile("beta",  new File("b").getCanonicalFile());

		List<File> files = new ArrayList<File>(
			selectInputFiles_recursive(
				new File(".").getCanonicalFile(),
				filter,
				new ArrayList<File>()
			)
		);

		for (int i = 0; i < files.size(); i++)
			files.set(i, trimLeadingPwd(files.get(i)));
		Collections.sort(files);

		assertEquals(Arrays.asList(new File[] {
			new File("a"),
			new File("b"),
		}), files);
	}

	@Test
	public void globMatchesDeepFiles() throws Exception {
		String input = "dir/*";

		new File("dir").getCanonicalFile().mkdir();
		IOForge.saveFile("alpha", new File("dir/a").getCanonicalFile());
		IOForge.saveFile("beta",  new File("dir/b").getCanonicalFile());
		IOForge.saveFile("nope", new File("n1").getCanonicalFile());
		IOForge.saveFile("nope", new File("n2").getCanonicalFile());

		List<File> files = new ArrayList<File>(
			selectInputFiles_recursive(
				new File(".").getCanonicalFile(),
				new WildcardPathFilter(new File(".").getCanonicalPath()+"/"+input),
				new ArrayList<File>()
			)
		);

		for (int i = 0; i < files.size(); i++)
			files.set(i, trimLeadingPwd(files.get(i)));
		Collections.sort(files);

		assertEquals(Arrays.asList(new File[] {
			new File("dir/a"),
			new File("dir/b"),
		}), files);
	}

	@Test
	public void globMatchesDeepFilesWithSuffixes() throws Exception {
		String input = "dir/*.txt";

		new File("dir").getCanonicalFile().mkdir();
		IOForge.saveFile("alpha", new File("dir/a.txt").getCanonicalFile());
		IOForge.saveFile("beta",  new File("dir/b").getCanonicalFile());
		IOForge.saveFile("nope", new File("n1").getCanonicalFile());
		IOForge.saveFile("nope", new File("n2").getCanonicalFile());

		List<File> files = new ArrayList<File>(
			selectInputFiles_recursive(
				new File(".").getCanonicalFile(),
				new WildcardPathFilter(new File(".").getCanonicalPath()+"/"+input),
				new ArrayList<File>()
			)
		);

		for (int i = 0; i < files.size(); i++)
			files.set(i, trimLeadingPwd(files.get(i)));
		Collections.sort(files);

		assertEquals(Arrays.asList(new File[] {
			new File("dir/a.txt"),
		}), files);
	}

	/**
	 * Fails.
	 *
	 * To do this you'd have to canonicalize the wildcard string part that is a path
	 * (which isn't trivial to distinguish; '*' is a legitimate name for a directory,
	 * after all), and start your traversal far enough up from the pwd to cover how
	 * far away the input string leapt. That way might lie madness.
	 *
	 * The git commands that deal with files do allow a glob that was escaped past the
	 * shell to be treated semantically, and have a special case that treats the glob
	 * literally if there is a file with a name that might otherwise be a glob... so
	 * there is a precedent for dealing with that kind of (rare) ambiguity, at least.
	 *
	 * Ooh. There's an idea. We could literally git-add. We could have an arg that takes
	 * a file where every line is fed to git-add. There'd be a sort of crazed elegance
	 * to that.
	 */
	@Test
	public void matchingWithUpone() throws Exception {
		String input = "../*";

		new File("dir").getCanonicalFile().mkdir();
		IOForge.saveFile("alpha", new File("dir/a").getCanonicalFile());
		IOForge.saveFile("beta",  new File("dir/b").getCanonicalFile());
		IOForge.saveFile("nope", new File("n1").getCanonicalFile());
		IOForge.saveFile("nope", new File("n2").getCanonicalFile());
		new File("dir2").getCanonicalFile().mkdir();

		System.getProperties().setProperty("user.dir", new File(new File(".").getCanonicalFile(), "dir2").getCanonicalPath());

		List<File> files = new ArrayList<File>(
			selectInputFiles_recursive(
				new File(".").getCanonicalFile(),
				new WildcardPathFilter(new File(".", input).getCanonicalPath()),
				new ArrayList<File>()
			)
		);

		for (int i = 0; i < files.size(); i++)
			files.set(i, trimLeadingPwd(files.get(i)));
		Collections.sort(files);

		assertEquals(Arrays.asList(new File[] {
			new File("dir/a"),
			new File("dir/b"),
		}), files);
	}
}
