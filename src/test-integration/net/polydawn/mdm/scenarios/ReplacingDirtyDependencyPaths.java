/*
 * Copyright 2012 - 2014 Eric Myhre <http://exultant.us>
 *
 * This file is part of mdm <https://github.com/heavenlyhash/mdm/>.
 *
 * mdm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package net.polydawn.mdm.scenarios;

import net.polydawn.mdm.test.*;
import org.junit.*;
import org.junit.runner.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class ReplacingDirtyDependencyPaths extends TestCaseUsingRepository {
	void set_up_dep_with_uncommitted_changes() {

	}

	void set_up_dep_then_replace_with_dirty_file() {
	}

	@Test
	public void alter_should_abort_if_junk_in_path() throws Exception {
		set_up_dep_then_replace_with_dirty_file();
		//TODO
	}

	@Test
	public void alter_should_operate_if_forcing_while_junk_in_path() throws Exception {
		set_up_dep_then_replace_with_dirty_file();
		//TODO
	}

	@Test
	public void alter_should_abort_if_module_unclean() throws Exception {
		set_up_dep_with_uncommitted_changes();
		//TODO
	}

	@Test
	public void alter_should_operate_if_forcing_while_module_unclean() throws Exception {
		set_up_dep_with_uncommitted_changes();
		//TODO
	}

	// most of these don't apply to the mdm-add subcommand, because it makes sense for that one to always flat out abort when there's files in the way.

	// todo: actually this set of tests looks like it's going to come out having a lot in command with material for the mdm-update subcommand
}
