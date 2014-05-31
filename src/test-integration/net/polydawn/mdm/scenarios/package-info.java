/**
 * <p>
 * Tests that cover wide integration of the entire system when deployed over the lifespan
 * of a scenario.
 * </p>
 *
 * <p>
 * In contrast to the tests that cover individual commands, these are expected to use
 * effectively the same interface as the end-user CLI itself rather than break down into
 * command internal components, and these are expected to cover issues of statefulness
 * between long series of commands, as well as other repo changes (a prime example being
 * switching branches).
 * </p>
 *
 * @author Eric Myhre <tt>hash@exultant.us</tt>
 *
 */
package net.polydawn.mdm.scenarios;
