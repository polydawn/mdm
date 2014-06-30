package net.polydawn.mdm.jgit;

import org.eclipse.jgit.transport.*;
import com.jcraft.jsch.*;

public class SshUserInfoHelper {
	public static void apply() {
		SshSessionFactory.setInstance(new JschConfigSessionFactory() {
			@Override
			protected void configure(OpenSshConfig.Host host, Session session) {
				session.setConfig("StrictHostKeyChecking", "ask");
				session.setUserInfo(new UserInfo() {
					public boolean promptPassword(String message) {
						if (System.console() == null) {
							System.err.println("no interactive terminal; not prompting for password");
							return false;
						}
						System.err.println(message+"? ");
						return true;
					}

					public String getPassword() {
						return new String(System.console().readPassword());
					}

					public boolean promptPassphrase(String message) {
						if (System.console() == null) {
							System.err.println("no interactive terminal; not prompting for key passphrase");
							return false;
						}
						System.err.println(message+"? ");
						return true;
					}

					public String getPassphrase() {
						return new String(System.console().readPassword());
					}

					public boolean promptYesNo(String message) {
						if (System.console() == null) {
							System.err.println("no interactive terminal; assuming \"no\" to question \""+message+"\"");
							return false;
						}
						System.err.println(message+" [y/n]? ");
						while (true) {
							String str = System.console().readLine();
							if ("y".equalsIgnoreCase(str) || "yes".equalsIgnoreCase(str)) return true;
							if ("n".equalsIgnoreCase(str) || "no".equalsIgnoreCase(str)) return false;
							// While it would be nice to respond after only once character,
							// that require dealing with the whole cooked/raw tty thing.  Later.
							//	int cha;
							//	try {
							//		cha = System.console().reader().read();
							//	} catch (IOException e) {
							//		return false;
							//	}
							//	if (cha == 89 || cha == 121) return true;
							//	if (cha == 78 || cha == 110) return false;
							System.err.println(message+" [y/n]? ");
						}
					}

					public void showMessage(String message) {
						System.err.println("jsch: "+message);
					}
				});
			}
		});
	}
}
