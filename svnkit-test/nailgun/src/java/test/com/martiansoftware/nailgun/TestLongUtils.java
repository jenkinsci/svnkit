/*   

  Copyright 2004, Martian Software, Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

package com.martiansoftware.nailgun;

import junit.framework.TestCase;

/**
 * 
 * @author <a href="http://www.martiansoftware.com/contact.html">Marty Lamb</a>
 */
public class TestLongUtils extends TestCase {

	private void testToFromArray(long l, byte b0, byte b1, byte b2, byte b3) {
		byte[] buf = new byte[4];
		LongUtils.toArray(l, buf, 0);
		assertEquals(b0, buf[0]);
		assertEquals(b1, buf[1]);
		assertEquals(b2, buf[2]);
		assertEquals(b3, buf[3]);
		assertEquals(l, LongUtils.fromArray(buf, 0));
	}
	
	public void testLongUtils() {
		testToFromArray(0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
		testToFromArray(4294967295l, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff);
		testToFromArray(305419896l, (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78);
	}

}
