/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2013 Stephan Preibisch, Tobias Pietzsch, Barry DeZonia,
 * Stephan Saalfeld, Albert Cardona, Curtis Rueden, Christian Dietz, Jean-Yves
 * Tinevez, Johannes Schindelin, Lee Kamentsky, Larry Lindsey, Grant Harris,
 * Mark Hiner, Aivar Grislis, Martin Horn, Nick Perry, Michael Zinsmaier,
 * Steffen Jaensch, Jan Funke, Mark Longair, and Dimiter Prodanov.
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

import io.scif.io.img.ImgIOException;
import io.scif.io.img.ImgSaver;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.scijava.Context;

import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.ImgPlus;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.meta.Axes;
import net.imglib2.meta.AxisType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class WriteImage {
	/**
	 * This could be turned into a JUnit test.
	 * @throws IOException 
	 * @throws IncompatibleTypeException 
	 * @throws ImgIOException 
	 */
	public File writeTestImg() throws IOException, ImgIOException, IncompatibleTypeException {
		final PlanarImgFactory<UnsignedByteType> factory = new PlanarImgFactory<UnsignedByteType>();
		final PlanarImg<UnsignedByteType, ?> img = factory.create(new long[] { 64,  64, 3 }, new UnsignedByteType());
		for (int i = 0; i < 3; i++) {
			final ByteArray array = (ByteArray) img.getPlane(i);
			final byte[] pixels = (byte[])array.getCurrentStorageArray();
			Arrays.fill(pixels, (byte)i);
		}
		final ImgSaver saver = new ImgSaver();
		// this should be unnecessary
		saver.setContext(new Context());
		final File tmp = File.createTempFile("writeTest", ".tif");
		// this does not work at the moment
		//saver.saveImg(tmp.getAbsolutePath(), img);
		// a work-around is to use this instead:
		final ImgPlus<UnsignedByteType> imgPlus =
				new ImgPlus<UnsignedByteType>(img, "dummy", new AxisType[] { Axes.X, Axes.Y, Axes.TIME });
		saver.saveImg(tmp.getAbsolutePath(), imgPlus);
		return tmp;
	}

	public static void main(final String... args) throws ImgIOException, IncompatibleTypeException, IOException {
		System.err.println("wrote: " + new WriteImage().writeTestImg());
	}

}
