package net.polydawn.mdm.errors;

import net.polydawn.mdm.*;

public class MdmExitInvalidConfig extends MdmExitMessage {
	public MdmExitInvalidConfig(String problemFile) {
		super(":(", "your "+problemFile+" config file can't be parsed.  sorry, we're flying blind.");
	}
}
