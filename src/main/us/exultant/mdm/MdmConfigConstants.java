package us.exultant.mdm;

public interface MdmConfigConstants {
	public static enum Module implements MdmConfigConstants {
		MODULE_TYPE ("mdm"),
		DEPENDENCY_VERSION ("mdm-version");

		Module(String value) { this.value = value; }
		private final String value;
		public String toString() { return value; }
	}
}
