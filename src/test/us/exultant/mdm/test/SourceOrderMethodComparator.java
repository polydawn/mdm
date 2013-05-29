package us.exultant.mdm.test;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runners.model.*;

public class SourceOrderMethodComparator implements Comparator<FrameworkMethod> {
	public SourceOrderMethodComparator() {}

	private static final char[] METHOD_SEPARATORS = { 1, 7 };
	private final Map<Method, MethodPosition> cache = new HashMap<Method, MethodPosition>();

	public int compare(Method o1, Method o2) {
		final MethodPosition methodPosition1 = this.getIndexOfMethodPosition(o1);
		final MethodPosition methodPosition2 = this.getIndexOfMethodPosition(o2);
		return methodPosition1.compareTo(methodPosition2);
	}

	public int compare(FrameworkMethod o1, FrameworkMethod o2) {
		final MethodPosition methodPosition1 = this.getIndexOfMethodPosition(o1);
		final MethodPosition methodPosition2 = this.getIndexOfMethodPosition(o2);
		return methodPosition1.compareTo(methodPosition2);
	}

	private MethodPosition getIndexOfMethodPosition(final FrameworkMethod frameworkMethod) {
		return getIndexOfMethodPosition(frameworkMethod.getMethod());
	}

	private MethodPosition getIndexOfMethodPosition(final Method method) {
		MethodPosition cached = cache.get(method);
		if (cached != null) return cached;
		final Class<?> aClass = method.getDeclaringClass();
		cached = (method.getAnnotation(Ignore.class) == null) ?
			getIndexOfMethodPosition(aClass, method.getName()) :
			new NullMethodPosition("getIndexOfMethodPosition(): Method " + method.getName() + " annotated as Ignored in class " + aClass.getCanonicalName());
		cache.put(method, cached);
		return cached;
	}

	private MethodPosition getIndexOfMethodPosition(final Class<?> aClass, final String methodName) {
		MethodPosition methodPosition;
		for (final char methodSeparator : METHOD_SEPARATORS) {
			methodPosition = getIndexOfMethodPosition(aClass, methodName, methodSeparator);
			if (methodPosition instanceof NullMethodPosition) {
				//System.err.println("getIndexOfMethodPosition(): Failed when trying method separator " + methodSeparator + " for method: " + methodName + "; " + ((NullMethodPosition)methodPosition).getReason());
			} else {
				return methodPosition;
			}
		}
		return new NullMethodPosition();
	}

	private MethodPosition getIndexOfMethodPosition(final Class<?> aClass, final String methodName, final char methodSeparator) {
		final InputStream inputStream = aClass.getResourceAsStream(aClass.getSimpleName() + ".class");
		final LineNumberReader lineNumberReader = new LineNumberReader(new InputStreamReader(inputStream));
		final String methodNameWithSeparator = methodName + methodSeparator;
		try {
			try {
				String line;
				while ((line = lineNumberReader.readLine()) != null)
					if (line.contains(methodNameWithSeparator))
						return new MethodPosition(lineNumberReader.getLineNumber(), line.indexOf(methodNameWithSeparator));
			} finally {
				lineNumberReader.close();
			}
		} catch (IOException e) {
			return new NullMethodPosition("Error while reading byte code of class " + aClass.getCanonicalName() + "; " + e.getMessage());
		}
		return new NullMethodPosition("Can't find method " + methodName + " in byte code of class " + aClass.getCanonicalName());
	}



	private static class MethodPosition implements Comparable<MethodPosition> {
		public MethodPosition(int lineNumber, int indexInLine) {
			this.lineNumber = lineNumber;
			this.indexInLine = indexInLine;
		}

		private final Integer lineNumber;
		private final Integer indexInLine;

		public int compareTo(MethodPosition o) {
			int lineCompare = lineNumber.compareTo(o.lineNumber);
			if (lineCompare != 0) return lineCompare;
			return indexInLine.compareTo(o.indexInLine);
		}
	}



	private static class NullMethodPosition extends MethodPosition {
		public NullMethodPosition() {
			this(null);
		}

		public NullMethodPosition(String reason) {
			super(-1, -1);
			this.reason = reason;
		}

		private final String reason;

		public String getReason() {
			return reason;
		}
	}
}
