package us.exultant.mdm.errors;

/**
 * Supertype for any exception (many of which are containers for exceptions from other
 * libraries) that is considered well-known to mdm and can be presented as an intelligible
 * message to the end-user (stacks are generally uninterested).
 */
public class MdmRuntimeException extends RuntimeException {
	public MdmRuntimeException() { super(); }
	public MdmRuntimeException(String message, Throwable cause) { super(message, cause); }
	public MdmRuntimeException(String message) { super(message); }
	public MdmRuntimeException(Throwable cause) { super(cause); }
}
