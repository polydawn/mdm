package us.exultant.mdm.test;

import java.util.*;
import org.junit.runners.*;
import org.junit.runners.model.*;

public class OrderedJUnit4ClassRunner extends BlockJUnit4ClassRunner {
	public OrderedJUnit4ClassRunner(Class<?> aClass) throws InitializationError {
		super(aClass);
	}

	@Override
	protected List<FrameworkMethod> computeTestMethods() {
		final List<FrameworkMethod> list = super.computeTestMethods();
		try {
			final List<FrameworkMethod> copy = new ArrayList<FrameworkMethod>(list);
			Collections.sort(copy, new SourceOrderMethodComparator());
			return copy;
		} catch (Throwable throwable) {
			return list;
		}
	}
}
