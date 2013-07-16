package us.exultant.mdm.util;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import org.apache.commons.io.*;
import org.junit.*;
import org.junit.runner.*;
import us.exultant.ahs.iob.*;
import us.exultant.mdm.test.*;

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
}
