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
		List<FrameworkMethod> list = super.computeTestMethods();
		Collections.sort(list, new SourceOrderMethodComparator());
		return list;
	}
}
