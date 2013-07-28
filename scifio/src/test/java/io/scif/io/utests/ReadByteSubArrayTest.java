/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package io.scif.io.utests;

import static org.testng.AssertJUnit.assertEquals;
import io.scif.io.IRandomAccess;
import io.scif.io.utests.providers.IRandomAccessProvider;
import io.scif.io.utests.providers.IRandomAccessProviderFactory;

import java.io.IOException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * Tests for reading bytes from a loci.common.IRandomAccess.
 * 
 * @see io.scif.io.IRandomAccess
 */
@Test(groups = "readTests")
public class ReadByteSubArrayTest {

	private static final byte[] PAGE = new byte[] { (byte) 0x01, (byte) 0x02,
		(byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
		(byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0x0C,
		(byte) 0x0D, (byte) 0x0E, (byte) 0xFF, (byte) 0xFE };

	private static final String MODE = "r";

	private static final int BUFFER_SIZE = 2;

	private IRandomAccess fileHandle;

	@Parameters({ "provider" })
	@BeforeMethod
	public void setUp(final String provider) throws IOException {
		final IRandomAccessProviderFactory factory =
			new IRandomAccessProviderFactory();
		final IRandomAccessProvider instance = factory.getInstance(provider);
		fileHandle = instance.createMock(PAGE, MODE, BUFFER_SIZE);
	}

	@Test
	public void testLength() throws IOException {
		assertEquals(16, fileHandle.length());
	}

	@Test
	public void testSequentialReadByte() throws IOException {
		final byte[] b = new byte[16];
		final int length = fileHandle.read(b, 0, 16);
		assertEquals(16, fileHandle.getFilePointer());
		assertEquals(16, length);
		assertEquals(0x01, b[0]);
		assertEquals(0x02, b[1]);
		assertEquals(0x03, b[2]);
		assertEquals(0x04, b[3]);
		assertEquals(0x05, b[4]);
		assertEquals(0x06, b[5]);
		assertEquals(0x07, b[6]);
		assertEquals(0x08, b[7]);
		assertEquals(0x09, b[8]);
		assertEquals(0x0A, b[9]);
		assertEquals(0x0B, b[10]);
		assertEquals(0x0C, b[11]);
		assertEquals(0x0D, b[12]);
		assertEquals(0x0E, b[13]);
		assertEquals((byte) 0xFF, b[14]);
		assertEquals((byte) 0xFE, b[15]);
	}

	@Test
	public void testSeekForwardReadByte() throws IOException {
		fileHandle.seek(7);
		final byte[] b = new byte[4];
		final int length = fileHandle.read(b, 1, 2);
		assertEquals(9, fileHandle.getFilePointer());
		assertEquals(2, length);
		assertEquals(0x00, b[0]);
		assertEquals(0x08, b[1]);
		assertEquals(0x09, b[2]);
		assertEquals(0x00, b[3]);
	}

	@Test
	public void testResetReadByte() throws IOException {
		byte[] b = new byte[4];
		int length = fileHandle.read(b, 1, 2);
		assertEquals(2, fileHandle.getFilePointer());
		assertEquals(0x02, length);
		assertEquals(0x00, b[0]);
		assertEquals(0x01, b[1]);
		assertEquals(0x02, b[2]);
		assertEquals(0x00, b[3]);
		fileHandle.seek(0);
		b = new byte[4];
		length = fileHandle.read(b, 1, 2);
		assertEquals(0x02, length);
		assertEquals(0x00, b[0]);
		assertEquals(0x01, b[1]);
		assertEquals(0x02, b[2]);
		assertEquals(0x00, b[3]);
	}

	@Test
	public void testSeekBackReadByte() throws IOException {
		fileHandle.seek(15);
		fileHandle.seek(7);
		final byte[] b = new byte[4];
		final int length = fileHandle.read(b, 1, 2);
		assertEquals(9, fileHandle.getFilePointer());
		assertEquals(2, length);
		assertEquals(0x00, b[0]);
		assertEquals(0x08, b[1]);
		assertEquals(0x09, b[2]);
		assertEquals(0x00, b[3]);
	}

	@Test
	public void testRandomAccessReadByte() throws IOException {
		testSeekForwardReadByte();
		testSeekBackReadByte();
		// The test relies on a "new" file or reset file pointer
		fileHandle.seek(0);
		testResetReadByte();
	}

}
