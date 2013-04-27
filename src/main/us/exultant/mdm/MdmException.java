package us.exultant.mdm;

public class MdmException extends Exception {
	public MdmException() { super(); }
	public MdmException(String message, Throwable cause) { super(message, cause); }
	public MdmException(String message) { super(message); }
	public MdmException(Throwable cause) { super(cause); }
}
