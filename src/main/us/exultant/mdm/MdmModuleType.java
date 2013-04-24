package us.exultant.mdm;

public enum MdmModuleType {
	DEPENDENCY,
	RELEASES;

	public String toString() {
		return super.toString().toLowerCase();
	}

	public static MdmModuleType fromString(String value) {
		if (value == null) return null;
		switch (value) {
			case "dependency": return DEPENDENCY;
			case "releases": return RELEASES;
			default: return null;
		}
	}
}
