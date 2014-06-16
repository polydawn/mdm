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

import net.polydawn.mdm.*;
import net.polydawn.mdm.test.*;
import org.junit.*;
import org.junit.runner.*;

@RunWith(OrderedJUnit4ClassRunner.class)
public class CommandsInEmptyDirTest extends TestCaseUsingRepository {
	@Test
	public void provideHelpInEmptyDir() throws Exception {
		assertJoy(Mdm.run(
			"--help"
		));
	}

	@Test
	public void provideVersionInEmptyDir() throws Exception {
		assertJoy(Mdm.run(
			"--version"
		));
	}
}
