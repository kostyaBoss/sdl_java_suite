/*
 * Copyright (c) 2019 Livio, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of the Livio Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.smartdevicelink.managers.screen.menu;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DynamicMenuUpdatesModeTests extends TestCase {

	/**
	 * Verifies that the enum values are not null upon valid assignment.
	 */
	public void testValidEnums() {
		String example = "FORCE_ON";
		DynamicMenuUpdatesMode forceOn = DynamicMenuUpdatesMode.valueForString(example);
		example = "FORCE_OFF";
		DynamicMenuUpdatesMode forceOff = DynamicMenuUpdatesMode.valueForString(example);
		example = "ON_WITH_COMPAT_MODE";
		DynamicMenuUpdatesMode onWithCompatMode = DynamicMenuUpdatesMode.valueForString(example);

		assertNotNull("FORCE_ON returned null", forceOn);
		assertNotNull("FORCE_OFF returned null", forceOff);
		assertNotNull("ON_WITH_COMPAT_MODE returned null", onWithCompatMode);
	}

	/**
	 * Verifies that an invalid assignment is null.
	 */
	public void testInvalidEnum() {
		String example = "deFaUlt";
		try {
			DynamicMenuUpdatesMode temp = DynamicMenuUpdatesMode.valueForString(example);
			assertNull("Result of valueForString should be null.", temp);
		} catch (IllegalArgumentException exception) {
			fail("Invalid enum throws IllegalArgumentException.");
		}
	}

	/**
	 * Verifies that a null assignment is invalid.
	 */
	public void testNullEnum() {
		String example = null;
		try {
			DynamicMenuUpdatesMode temp = DynamicMenuUpdatesMode.valueForString(example);
			assertNull("Result of valueForString should be null.", temp);
		} catch (NullPointerException exception) {
			fail("Null string throws NullPointerException.");
		}
	}

	/**
	 * Verifies the possible enum values of DynamicMenuUpdatesMode.
	 */
	public void testListEnum() {
		List<DynamicMenuUpdatesMode> enumValueList = Arrays.asList(DynamicMenuUpdatesMode.values());

		List<DynamicMenuUpdatesMode> enumTestList = new ArrayList<>();
		enumTestList.add(DynamicMenuUpdatesMode.FORCE_ON);
		enumTestList.add(DynamicMenuUpdatesMode.FORCE_OFF);
		enumTestList.add(DynamicMenuUpdatesMode.ON_WITH_COMPAT_MODE);

		assertTrue("Enum value list does not match enum class list",
				enumValueList.containsAll(enumTestList) && enumTestList.containsAll(enumValueList));
	}
}
