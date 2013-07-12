package us.exultant.mdm.errors;

public class MdmInputUnavailableException extends MdmRuntimeException {
	public MdmInputUnavailableException() { super(); }
	public MdmInputUnavailableException(String message, Throwable cause) { super(message, cause); }
	public MdmInputUnavailableException(String message) { super(message); }
	public MdmInputUnavailableException(Throwable cause) { super(cause); }
}
