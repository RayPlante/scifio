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

package io.scif.img.cell.loaders;

import io.scif.Metadata;
import io.scif.Reader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.imglib2.img.basictypeaccess.array.DoubleArray;

/**
 * {@link SCIFIOArrayLoader} implementation for {@link DoubleArray} types.
 * 
 * @author Mark Hiner hinerm at gmail.com
 */
public class DoubleArrayLoader extends AbstractArrayLoader<DoubleArray> {

	public DoubleArrayLoader(final Reader reader) {
		super(reader);
	}

	@Override
	public void convertBytes(final DoubleArray data, final byte[] bytes,
		final int planesRead)
	{
		final Metadata meta = reader().getMetadata();

		final int bpp = meta.getBitsPerPixel(0) / 8;
		final int offset = planesRead * (bytes.length / bpp);

		final ByteBuffer bb = ByteBuffer.wrap(bytes);

		bb.order(reader().getMetadata().isLittleEndian(0) ? ByteOrder.LITTLE_ENDIAN
			: ByteOrder.BIG_ENDIAN);
		bb.asDoubleBuffer().get(data.getCurrentStorageArray(), offset,
			bytes.length / bpp);
	}

	public DoubleArray emptyArray(final int[] dimensions) {
		return new DoubleArray(countEntities(dimensions));
	}

	public int getBitsPerElement() {
		return 64;
	}
}
