/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2014 Board of Regents of the University of
 * Wisconsin-Madison
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
 * #L%
 */

package io.scif.io.utests;

import static org.testng.AssertJUnit.assertEquals;
import io.scif.io.IRandomAccess;
import io.scif.io.utests.providers.IRandomAccessProvider;
import io.scif.io.utests.providers.IRandomAccessProviderFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * Tests for reading bytes from a loci.common.IRandomAccess.
 * 
 * @see io.scif.io.IRandomAccess
 */
@Test(groups = "readTests")
public class ReadByteBufferTest {

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
		final ByteBuffer b = ByteBuffer.allocate(16);
		final int length = fileHandle.read(b);
		b.position(0);
		assertEquals(16, fileHandle.getFilePointer());
		assertEquals(16, length);
		assertEquals(0x01, b.get());
		assertEquals(0x02, b.get());
		assertEquals(0x03, b.get());
		assertEquals(0x04, b.get());
		assertEquals(0x05, b.get());
		assertEquals(0x06, b.get());
		assertEquals(0x07, b.get());
		assertEquals(0x08, b.get());
		assertEquals(0x09, b.get());
		assertEquals(0x0A, b.get());
		assertEquals(0x0B, b.get());
		assertEquals(0x0C, b.get());
		assertEquals(0x0D, b.get());
		assertEquals(0x0E, b.get());
		assertEquals((byte) 0xFF, b.get());
		assertEquals((byte) 0xFE, b.get());
	}

	@Test
	public void testSeekForwardReadByte() throws IOException {
		fileHandle.seek(7);
		final ByteBuffer b = ByteBuffer.allocate(2);
		final int length = fileHandle.read(b);
		b.position(0);
		assertEquals(9, fileHandle.getFilePointer());
		assertEquals(2, length);
		assertEquals(0x08, b.get());
		assertEquals(0x09, b.get());
	}

	@Test
	public void testResetReadByte() throws IOException {
		final ByteBuffer b = ByteBuffer.allocate(2);
		int length = fileHandle.read(b);
		b.position(0);
		assertEquals(2, fileHandle.getFilePointer());
		assertEquals(0x02, length);
		assertEquals(0x01, b.get());
		assertEquals(0x02, b.get());
		fileHandle.seek(0);
		b.position(0);
		length = fileHandle.read(b);
		b.position(0);
		assertEquals(0x02, length);
		assertEquals(0x01, b.get());
		assertEquals(0x02, b.get());
	}

	@Test
	public void testSeekBackReadByte() throws IOException {
		fileHandle.seek(15);
		fileHandle.seek(7);
		final ByteBuffer b = ByteBuffer.allocate(2);
		final int length = fileHandle.read(b);
		b.position(0);
		assertEquals(9, fileHandle.getFilePointer());
		assertEquals(2, length);
		assertEquals(0x08, b.get());
		assertEquals(0x09, b.get());
	}

	@Test
	public void testRandomAccessReadByte() throws IOException {
		testSeekForwardReadByte();
		testSeekBackReadByte();
		// The test relies on a "new" file or reset file pointer
		fileHandle.seek(0);
		testResetReadByte();
	}

	@AfterMethod
	public void tearDown() throws IOException {
		fileHandle.close();
	}
}
