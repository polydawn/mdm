package us.exultant.mdm;

import java.util.*;

public class MdmModuleStatus {
	public String version;
	/** Major notifications about the state of a module -- things that mean your build probably won't work. */
	public List<String> warnings = new ArrayList<>();
	/** Errors so bad that we can't even tell what's supposed to be going on with this module. */
	public List<String> errors = new ArrayList<>();

	public String toString() {
		return new StringBuilder()
			.append("MdmModuleStatus{")
			.append("\n   version=").append(this.version)
			.append("\n  warnings=").append(this.warnings)
			.append("\n    errors=").append(this.errors)
			.append("\n}").toString();
	}
}
