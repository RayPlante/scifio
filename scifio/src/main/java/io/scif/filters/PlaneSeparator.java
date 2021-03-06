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

package io.scif.filters;

import io.scif.ByteArrayPlane;
import io.scif.FormatException;
import io.scif.Metadata;
import io.scif.Plane;
import io.scif.common.DataTools;
import io.scif.config.SCIFIOConfig;
import io.scif.io.RandomAccessInputStream;
import io.scif.util.FormatTools;
import io.scif.util.ImageTools;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import net.imglib2.meta.Axes;
import net.imglib2.meta.AxisType;
import net.imglib2.meta.CalibratedAxis;

import org.scijava.plugin.Plugin;

/**
 * Logic to automatically separate the channels in a file.
 */
@Plugin(type = Filter.class)
public class PlaneSeparator extends AbstractReaderFilter {

	// -- Fields --

	/** Last plane opened. */
	private Plane lastPlane = null;

	/** Index of last plane opened. */
	private long lastPlaneIndex = -1;

	/** Index of last plane opened. */
	private int lastImageIndex = -1;

	/** Offsets of last plane opened. */
	private long[] lastPlaneOffsets = null;

	/** Lengths of last plane opened. */
	private long[] lastPlaneLengths = null;

	// -- Constructor --

	public PlaneSeparator() {
		super(PlaneSeparatorMetadata.class);
	}

	// -- PlanarAxisSeparator API methods --

	/**
	 * Specify which AxisTypes should be separated.
	 */
	public void separate(final AxisType... types) {
		if (metaCheck()) {
			((PlaneSeparatorMetadata) getMetadata()).separate(types);
		}
	}

	/**
	 * Returns the image number in the original dataset that corresponds to the
	 * given image number. For instance, if the original dataset was a single RGB
	 * image and the given plane index is 2, the return value will be 0.
	 * 
	 * @param planeIndex is a plane number greater than or equal to 0 and less
	 *          than getPlaneCount()
	 * @return the corresponding plane number in the original (unseparated) data.
	 */
	public long getOriginalIndex(final int imageIndex, final long planeIndex) {
		final long planeCount = getPlaneCount(imageIndex);
		final long originalCount = getParent().getPlaneCount(imageIndex);

		if (planeCount == originalCount) return planeIndex;
		final long[] coords =
			FormatTools.rasterToPosition(imageIndex, planeIndex, this);
		int offset = 0;
		if (PlaneSeparatorMetadata.class.isAssignableFrom(getMetadata().getClass()))
		{
			offset = ((PlaneSeparatorMetadata) getMetadata()).offset();
		}
		final long[] originalCoords = new long[coords.length - offset];
		final long[] lengths = new long[coords.length - offset];
		for (int i = 0; i < originalCoords.length; i++) {
			originalCoords[i] = coords[i + offset];
			lengths[i] =
				getMetadata().get(imageIndex).getAxesLengthsNonPlanar()[i + offset];
		}
		return FormatTools.positionToRaster(lengths, originalCoords);
	}

	// -- AbstractReaderFilter API Methods --

	@Override
	public void setSource(final String source) throws IOException {
		cleanUp();
		super.setSource(source);
	}

	@Override
	public void setSource(final File file) throws IOException {
		cleanUp();
		super.setSource(file);
	}

	@Override
	public void setSource(final RandomAccessInputStream stream)
		throws IOException
	{
		cleanUp();
		super.setSource(stream);
	}

	@Override
	public long getPlaneCount(final int imageIndex) {
		return getMetadata().get(imageIndex).getPlaneCount();
	}

	@Override
	public Plane openPlane(final int imageIndex, final long planeIndex)
		throws FormatException, IOException
	{
		return openPlane(imageIndex, planeIndex, new SCIFIOConfig());
	}

	@Override
	public Plane openPlane(final int imageIndex, final long planeIndex,
		final Plane plane) throws FormatException, IOException
	{
		return openPlane(imageIndex, planeIndex, plane, new SCIFIOConfig());
	}

	@Override
	public Plane openPlane(final int imageIndex, final long planeIndex,
		final long[] offsets, final long[] lengths) throws FormatException,
		IOException
	{
		return openPlane(imageIndex, planeIndex, offsets, lengths,
			new SCIFIOConfig());
	}

	@Override
	public Plane openPlane(final int imageIndex, final long planeIndex,
		final Plane plane, final long[] offsets, final long[] lengths)
		throws FormatException, IOException
	{
		return openPlane(imageIndex, planeIndex, plane, offsets, lengths,
			new SCIFIOConfig());
	}

	@Override
	public Plane openPlane(final int imageIndex, final long planeIndex,
		final SCIFIOConfig config) throws FormatException, IOException
	{
		final int planarAxes = getMetadata().get(imageIndex).getPlanarAxisCount();
		return openPlane(imageIndex, planeIndex, new long[planarAxes],
			getMetadata().get(imageIndex).getAxesLengthsPlanar(), config);
	}

	@Override
	public Plane openPlane(final int imageIndex, final long planeIndex,
		final Plane plane, final SCIFIOConfig config) throws FormatException,
		IOException
	{
		final int planarAxes = getMetadata().get(imageIndex).getPlanarAxisCount();
		return openPlane(imageIndex, planeIndex, plane, new long[planarAxes],
			getMetadata().get(imageIndex).getAxesLengthsPlanar(), config);
	}

	@Override
	public Plane openPlane(final int imageIndex, final long planeIndex,
		final long[] planeMin, final long[] planeMax, final SCIFIOConfig config)
		throws FormatException, IOException
	{
		return openPlane(imageIndex, planeIndex, createPlane(getMetadata().get(
			imageIndex), planeMin, planeMax), planeMin, planeMax, config);
	}

	@Override
	public Plane openPlane(final int imageIndex, final long planeIndex,
		Plane plane, final long[] offsets, final long[] lengths,
		final SCIFIOConfig config) throws FormatException, IOException
	{
		FormatTools.assertId(getCurrentFile(), true, 2);
		FormatTools.checkPlaneNumber(getMetadata(), imageIndex, planeIndex);
		final Metadata meta = getMetadata();
		final Metadata parentMeta = getParentMeta();

		// Get the original index
		final long source = getOriginalIndex(imageIndex, planeIndex);
		// Get the position in the the current separated metadata
		final int splitOffset =
			metaCheck() ? ((PlaneSeparatorMetadata) meta).offset() : 0;
		final boolean interleaved =
			parentMeta.get(imageIndex).getInterleavedAxisCount() > 0;

		synchronized (this) {
			if (!parentMeta.get(imageIndex).isIndexed()) {
				// -> if one or more axes was split out...
				// dividing current split axis count by parent effective size c (actual
				// c
				// planes).. shouldn't need to do that any more
				// trying to determine the positions for each converted axis... should
				// just be raster to position on just the converted lengths

				// Get the position of the current plane
				final long[] completePosition =
					FormatTools.rasterToPosition(imageIndex, planeIndex, meta);
				// Isolate the position and lengths of the axis (axes) that have been
				// split
				final long[] separatedPosition =
					Arrays.copyOf(completePosition, splitOffset);
				final long[] separatedLengths =
					Arrays.copyOf(meta.get(imageIndex).getAxesLengthsNonPlanar(),
						splitOffset);
				final int bpp =
					FormatTools.getBytesPerPixel(meta.get(imageIndex).getPixelType());

				// Need a byte array plane to copy data into
				if (!ByteArrayPlane.class.isAssignableFrom(plane.getClass())) {
					plane =
						new ByteArrayPlane(getContext(), meta.get(imageIndex), offsets,
							lengths);
				}

				if (!haveCached(source, imageIndex, offsets, lengths)) {
					int strips = 1;

					// check how big the original image is; if it's larger than the
					// available memory, we will need to split it into strips (of the last
					// planar axis)

					final Runtime rt = Runtime.getRuntime();
					final long availableMemory = rt.freeMemory();
					final long planeSize = meta.get(imageIndex).getPlaneSize();
					// If we make strips, they will be of the Y axis
					final long h = lengths[meta.get(imageIndex).getAxisIndex(Axes.Y)];

					if (availableMemory < planeSize || planeSize > Integer.MAX_VALUE) {
						strips = (int) Math.sqrt(h);
					}

					// Compute strip height, and the height of the last strip (in case the
					// plane is not evenly divisible).
					final long stripHeight = h / strips;
					final long lastStripHeight =
						stripHeight + (h - (stripHeight * strips));
					byte[] strip =
						strips == 1 ? plane.getBytes() : new byte[(int) (stripHeight *
							DataTools.safeMultiply32(Arrays.copyOf(lengths,
								lengths.length - 1)) * bpp)];
					updateLastPlaneInfo(source, imageIndex, splitOffset, offsets, lengths);
					// Populate the strips
					for (int i = 0; i < strips; i++) {
						final int parentYIndex =
							parentMeta.get(imageIndex).getAxisIndex(Axes.Y);

						// Update length and offset for current strip
						lastPlaneOffsets[parentYIndex] =
							lastPlaneOffsets[parentYIndex] + (i * stripHeight);
						lastPlaneLengths[parentYIndex] =
							i == strips - 1 ? lastStripHeight : stripHeight;

						// Open the plane
						lastPlane =
							getParent().openPlane(imageIndex, (int) source, lastPlaneOffsets,
								lastPlaneLengths, config);
						// Store the last recorded offsets/lengths
						lastPlaneOffsets = offsets;
						lastPlaneLengths = lengths;

						// Adjust the strip array if this is the last strip, if needed
						if (strips != 1 && lastStripHeight != stripHeight &&
							i == strips - 1)
						{
							strip =
								new byte[(int) (lastStripHeight *
									DataTools.safeMultiply32(Arrays.copyOf(lengths,
										lengths.length - 1)) * bpp)];
						}

						// Extract the requested channel from the plane
						ImageTools.splitChannels(lastPlane.getBytes(), strip,
							separatedPosition, separatedLengths, bpp, false, interleaved,
							strips == 1 ? bpp * DataTools.safeMultiply32(lengths)
								: strip.length);
						if (strips != 1) {
							System.arraycopy(strip, 0, plane.getBytes(), (int) (i *
								stripHeight * DataTools.safeMultiply32(Arrays.copyOf(lengths,
								lengths.length - 1))) *
								bpp, strip.length);
						}
					}
				}
				else {
					// Have a cached instance of the plane containing the desired region
					ImageTools.splitChannels(lastPlane.getBytes(), plane.getBytes(),
						separatedPosition, separatedLengths, bpp, false, interleaved, bpp *
							DataTools.safeMultiply32(lengths));
				}

				return plane;
			}

			if (!haveCached(source, imageIndex, offsets, lengths)) {
				// Convert the current positional information to the format of the
				// parent
				updateLastPlaneInfo(source, imageIndex, splitOffset, offsets, lengths);
				// Delegate directly to the parent
				lastPlane =
					getParent().openPlane(imageIndex, planeIndex, plane,
						lastPlaneOffsets, lastPlaneLengths, config);
			}
		}
		return lastPlane;
	}

	@Override
	public Plane openThumbPlane(final int imageIndex, final long planeIndex)
		throws FormatException, IOException
	{
		FormatTools.assertId(getCurrentFile(), true, 2);

		final int source = (int) getOriginalIndex(imageIndex, planeIndex);
		final Plane thumb = getParent().openThumbPlane(imageIndex, source);

		ByteArrayPlane ret = null;

		if (ByteArrayPlane.class.isAssignableFrom(thumb.getClass())) {
			ret = (ByteArrayPlane) thumb;
		}
		else {
			ret = new ByteArrayPlane(thumb.getContext());
			ret.populate(thumb);
		}

		final int splitOffset = ((PlaneSeparatorMetadata) getMetadata()).offset();
		final long[] completePosition =
			FormatTools.rasterToPosition(getMetadata().get(imageIndex)
				.getAxesLengths(), planeIndex);
		final long[] maxLengths =
			Arrays.copyOf(getMetadata().get(imageIndex).getAxesLengthsNonPlanar(),
				((PlaneSeparatorMetadata) getMetadata()).offset());
		final long[] pos = Arrays.copyOf(completePosition, splitOffset);

		final int bpp =
			FormatTools
				.getBytesPerPixel(getMetadata().get(imageIndex).getPixelType());

		ret.setData(ImageTools.splitChannels(thumb.getBytes(), pos, maxLengths,
			bpp, false, false));
		return ret;
	}

	// -- Prioritized API --

	@Override
	public double getPriority() {
		return 2.0;
	}

	// -- Helper Methods --

	/**
	 * Converts the given plane information using the current metadata to a format
	 * usable by the wrapped reader, stored in the "lastPlane"... variables.
	 */
	private void updateLastPlaneInfo(final long source, final int imageIndex,
		final int splitOffset, final long[] offsets, final long[] lengths)
	{
		final Metadata meta = getMetadata();
		final Metadata parentMeta = getParentMeta();
		lastPlaneIndex = source;
		lastImageIndex = imageIndex;
		// create the plane offsets and lengths to match the underlying image
		lastPlaneOffsets = new long[offsets.length + splitOffset];
		lastPlaneLengths = new long[lengths.length + splitOffset];

		// Create the offset and length arrays to match the underlying,
		// unsplit dimensions. This is required to pass to the wrapped reader.
		// The unsplit plane will then have the appropriate region extracted.
		for (final CalibratedAxis axis : parentMeta.get(imageIndex).getAxesPlanar())
		{
			final int parentIndex =
				parentMeta.get(imageIndex).getAxisIndex(axis.type());
			final int currentIndex = meta.get(imageIndex).getAxisIndex(axis.type());
			// This axis is still a planar axis, so we can read it from the
			// current plane offsets/lengths
			if (meta.get(imageIndex).getAxisIndex(axis.type()) < meta.get(imageIndex)
				.getPlanarAxisCount())
			{
				lastPlaneOffsets[parentIndex] = offsets[currentIndex];
				lastPlaneLengths[parentIndex] = lengths[currentIndex];
			}
			// This axis is a planar axis in the underlying metadata that was
			// split out, so we will insert a [0,length] range
			else if (parentMeta.get(imageIndex).getAxisIndex(axis.type()) < parentMeta
				.get(imageIndex).getPlanarAxisCount())
			{
				lastPlaneOffsets[parentIndex] = 0;
				lastPlaneLengths[parentIndex] =
					parentMeta.get(imageIndex).getAxisLength(axis.type());
			}
		}
	}

	/**
	 * Returns true if we have a cached plane matching the given image and plane
	 * indices, with extents to cover the desired offsets and lengths
	 */
	private boolean haveCached(final long source, final int imageIndex,
		final long[] offsets, final long[] lengths)
	{
		boolean matches = source == lastPlaneIndex;
		matches = matches && (imageIndex == lastImageIndex);

		if (lastPlane != null && lastPlaneOffsets != null &&
			lastPlaneLengths != null)
		{
			for (int i = 0; i < offsets.length && matches; i++) {
				//TODO It would be nice to fix up this logic so that we can use cached
				// planes when requesting a sub-region of the cached plane.
				// See https://github.com/scifio/scifio/issues/155
				// Make sure we have the starting point in each axis
				matches = matches && offsets[i] == lastPlaneOffsets[i];
				// make sure we have the last positions in each axis
				matches =
					matches &&
						offsets[i] + lengths[i] == lastPlaneOffsets[i] +
							lastPlaneLengths[i];
			}
		}
		else {
			matches = false;
		}
		return matches;
	}

	/* Resets local fields. */
	@Override
	protected void cleanUp() throws IOException {
		super.cleanUp();
		lastPlane = null;
		lastPlaneIndex = -1;
		lastImageIndex = -1;
		lastPlaneOffsets = null;
		lastPlaneLengths = null;
	}
}
