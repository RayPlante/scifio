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

package io.scif.gui;

import java.awt.image.DataBuffer;

/**
 * DataBuffer that stores signed bytes.
 * 
 * @author Melissa Linkert
 */
public class SignedByteBuffer extends DataBuffer {

	private final byte[][] bankData;

	/** Construct a new buffer of signed bytes using the given byte array. */
	public SignedByteBuffer(final byte[] dataArray, final int size) {
		super(DataBuffer.TYPE_BYTE, size);
		bankData = new byte[1][];
		bankData[0] = dataArray;
	}

	/** Construct a new buffer of signed bytes using the given 2D byte array. */
	public SignedByteBuffer(final byte[][] dataArray, final int size) {
		super(DataBuffer.TYPE_BYTE, size);
		bankData = dataArray;
	}

	/* @see java.awt.image.DataBuffer.getData() */
	public byte[] getData() {
		return bankData[0];
	}

	/* @see java.awt.image.DataBuffer#getData(int) */
	public byte[] getData(final int bank) {
		return bankData[bank];
	}

	/* @see java.awt.image.DataBuffer#getElem(int) */
	@Override
	public int getElem(final int i) {
		return getElem(0, i);
	}

	/* @see java.awt.image.DataBuffer#getElem(int, int) */
	@Override
	public int getElem(final int bank, final int i) {
		return bankData[bank][i + getOffsets()[bank]];
	}

	/* @see java.awt.image.DataBuffer#setElem(int, int) */
	@Override
	public void setElem(final int i, final int val) {
		setElem(0, i, val);
	}

	/* @see java.awt.image.DataBuffer#setElem(int, int, int) */
	@Override
	public void setElem(final int bank, final int i, final int val) {
		bankData[bank][i + getOffsets()[bank]] = (byte) val;
	}

}
