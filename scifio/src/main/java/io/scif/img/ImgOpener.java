/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2014 Open Microscopy Environment:
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
 * #L%
 */

package io.scif.img;

import io.scif.FormatException;
import io.scif.Metadata;
import io.scif.Plane;
import io.scif.Reader;
import io.scif.config.SCIFIOConfig;
import io.scif.filters.ChannelFiller;
import io.scif.filters.MinMaxFilter;
import io.scif.filters.PlaneSeparator;
import io.scif.filters.ReaderFilter;
import io.scif.img.cell.SCIFIOCellImgFactory;
import io.scif.img.converters.PlaneConverter;
import io.scif.img.converters.PlaneConverterService;
import io.scif.services.InitializeService;
import io.scif.util.FormatTools;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.basictypeaccess.PlanarAccess;
import net.imglib2.img.cell.AbstractCellImgFactory;
import net.imglib2.meta.Axes;
import net.imglib2.meta.AxisType;
import net.imglib2.meta.CalibratedAxis;
import net.imglib2.meta.ImgPlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;

import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;

/**
 * Reads in an {@link ImgPlus} using SCIFIO.
 * 
 * @author Curtis Rueden
 * @author Mark Hiner
 * @author Stephan Preibisch
 * @author Stephan Saalfeld
 */
public class ImgOpener extends AbstractImgIOComponent {

	@Parameter
	private StatusService statusService;

	@Parameter
	private PlaneConverterService pcService;

	@Parameter
	private InitializeService initializeService;

	// -- Constructors --

	public ImgOpener() {
		super();
	}

	public ImgOpener(final Context ctx) {
		super(ctx);
	}

	// -- ImgOpener methods --

	/**
	 * Reads in an {@link ImgPlus} from the first image of the given source.
	 * 
	 * @param source - the location of the dataset to open
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	public SCIFIOImgPlus<?> openImg(final String source) throws ImgIOException {
		return openImg(source, new SCIFIOConfig());
	}

	/**
	 * Reads in an {@link ImgPlus} from the first image of the given source.
	 * 
	 * @param source - the location of the dataset to open
	 * @param type - The {@link Type} T of the output {@link ImgPlus}.
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	public <T extends RealType<T> & NativeType<T>> SCIFIOImgPlus<T> openImg(
		final String source, final T type) throws ImgIOException
	{
		return openImg(source, type, new SCIFIOConfig());
	}

	/**
	 * Reads in an {@link ImgPlus} from the specified index of the given source.
	 * Can specify a variety of options via {@link SCIFIOConfig}.
	 * 
	 * @param source - the location of the dataset to open
	 * @param config - {@link SCIFIOConfig} to use when opening this dataset
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	public SCIFIOImgPlus<?>
		openImg(final String source, final SCIFIOConfig config)
			throws ImgIOException
	{
		final Reader r = createReader(source, config);
		return openImg(r, config);
	}

	/**
	 * Reads in an {@link ImgPlus} from the specified index of the given source.
	 * Can specify the Type that should be opened.
	 * 
	 * @param source - the location of the dataset to open
	 * @param type - The {@link Type} T of the output {@link ImgPlus}.
	 * @param config - {@link SCIFIOConfig} to use when opening this dataset
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	public <T extends RealType<T> & NativeType<T>> SCIFIOImgPlus<T> openImg(
		final String source, final T type, final SCIFIOConfig config)
		throws ImgIOException
	{
		final Reader r = createReader(source, config);

		return openImg(r, type, config);
	}

	/**
	 * @param source - the location of the dataset to open
	 * @param imgFactory - The {@link ImgFactory} to use for creating the
	 *          resultant {@link ImgPlus}.
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	@SuppressWarnings("rawtypes")
	public SCIFIOImgPlus<?> openImg(final String source,
		final ImgFactory imgFactory) throws ImgIOException
	{
		return openImg(source, imgFactory, new SCIFIOConfig()
			.imgOpenerSetComputeMinMax(true));
	}

	/**
	 * @param source - the location of the dataset to open
	 * @param imgFactory - The {@link ImgFactory} to use for creating the
	 *          resultant {@link ImgPlus}.
	 * @param config - {@link SCIFIOConfig} to use when opening this dataset
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SCIFIOImgPlus<?> openImg(final String source,
		final ImgFactory imgFactory, final SCIFIOConfig config)
		throws ImgIOException
	{
		final Reader r = createReader(source, config);
		final RealType t = getType(r, config);
		return openImg(r, t, imgFactory, config);
	}

	/**
	 * @param source - the location of the dataset to open
	 * @param imgFactory - The {@link ImgFactory} to use for creating the
	 *          resultant {@link ImgPlus}.
	 * @param type - The {@link Type} T of the output {@link ImgPlus}, which must
	 *          match the typing of the {@link ImgFactory}.
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	public <T extends RealType<T>> SCIFIOImgPlus<T> openImg(final String source,
		final ImgFactory<T> imgFactory, final T type) throws ImgIOException
	{

		final SCIFIOConfig config =
			new SCIFIOConfig().imgOpenerSetComputeMinMax(true);

		final Reader r = createReader(source, config);

		return openImg(r, type, imgFactory, config);
	}

	/**
	 * @param reader - An initialized {@link Reader} to use for reading image
	 *          data.
	 * @param config - {@link SCIFIOConfig} to use when opening this dataset
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SCIFIOImgPlus<?>
		openImg(final Reader reader, final SCIFIOConfig config)
			throws ImgIOException
	{
		final RealType t = getType(reader, config);

		final ImgFactoryHeuristic heuristic = getHeuristic(config);

		ImgFactory imgFactory;
		try {
			if (NativeType.class.isAssignableFrom(t.getClass())) {
				imgFactory =
					heuristic.createFactory(reader.getMetadata(), config
						.imgOpenerGetImgModes(), (NativeType) t);
			}
			else return null;
		}
		catch (final IncompatibleTypeException e) {
			throw new ImgIOException(e);
		}

		return openImg(reader, t, imgFactory, config);
	}

	/**
	 * @param reader - An initialized {@link Reader} to use for reading image
	 *          data.
	 * @param type - The {@link Type} T of the output {@link ImgPlus}, which must
	 *          match the typing of the {@link ImgFactory}.
	 * @param config - {@link SCIFIOConfig} to use when opening this dataset
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	public <T extends RealType<T> & NativeType<T>> SCIFIOImgPlus<T> openImg(
		final Reader reader, final T type, final SCIFIOConfig config)
		throws ImgIOException
	{
		final ImgFactoryHeuristic heuristic = getHeuristic(config);

		ImgFactory<T> imgFactory;
		try {
			imgFactory =
				heuristic.createFactory(reader.getMetadata(), config
					.imgOpenerGetImgModes(), type);
		}
		catch (final IncompatibleTypeException e) {
			throw new ImgIOException(e);
		}

		return openImg(reader, type, imgFactory, config);
	}

	/**
	 * Reads in an {@link ImgPlus} from the given initialized {@link Reader},
	 * using the given {@link ImgFactory} to construct the {@link Img}. The
	 * {@link Type} T to read is defined by the third parameter.
	 * <p>
	 * NB: Any Reader provided must be wrapped by a {@link PlaneSeparator} filter.
	 * </p>
	 * 
	 * @param reader - An initialized {@link Reader} to use for reading image
	 *          data.
	 * @param imgFactory - The {@link ImgFactory} to use for creating the
	 *          resultant {@link ImgPlus}.
	 * @param type - The {@link Type} T of the output {@link ImgPlus}, which must
	 *          match the typing of the {@link ImgFactory}.
	 * @param config - {@link SCIFIOConfig} to use when opening this dataset
	 * @return - the {@link ImgPlus} or null
	 * @throws ImgIOException if there is a problem reading the image data.
	 */
	public <T extends RealType<T>> SCIFIOImgPlus<T> openImg(final Reader reader,
		final T type, final ImgFactory<T> imgFactory, final SCIFIOConfig config)
		throws ImgIOException
	{
		final int imageIndex = config.imgOpenerGetIndex();

		// create image and read metadata
		final long[] dimLengths =
			utils().getConstrainedLengths(reader.getMetadata(), config);
		if (SCIFIOCellImgFactory.class.isAssignableFrom(imgFactory.getClass())) {
			((SCIFIOCellImgFactory<?>) imgFactory).setReader(reader);
			((SCIFIOCellImgFactory<?>) imgFactory).setSubRegion(config
				.imgOpenerGetRegion());
		}
		final Img<T> img = imgFactory.create(dimLengths, type);
		final SCIFIOImgPlus<T> imgPlus = makeImgPlus(img, reader, config);

		final String id = reader.getCurrentFile();
		imgPlus.setSource(id);
		imgPlus.initializeColorTables((int) reader.getPlaneCount(imageIndex));

		// If we have a planar img, read the planes now. Otherwise they
		// will be read on demand.
		if (AbstractCellImgFactory.class.isAssignableFrom(imgFactory.getClass())) {
			statusService.showStatus("Created CellImg for dynamic loading");
		}
		else {
			final float startTime = System.currentTimeMillis();
			final long planeCount = reader.getPlaneCount(imageIndex);
			try {
				readPlanes(reader, imageIndex, type, imgPlus, config);
				reader.close();
			}
			catch (final FormatException e) {
				throw new ImgIOException(e);
			}
			catch (final IOException e) {
				throw new ImgIOException(e);
			}
			final long endTime = System.currentTimeMillis();
			final float time = (endTime - startTime) / 1000f;
			statusService.showStatus(id + ": read " + planeCount + " planes in " +
				time + "s");
		}

		return imgPlus;
	}

	// -- Helper methods --

	@SuppressWarnings("rawtypes")
	private RealType getType(final Reader r, final SCIFIOConfig config) {
		int imageIndex = 0;
		if (config != null) imageIndex = config.imgOpenerGetIndex();

		return utils().makeType(r.getMetadata().get(imageIndex).getPixelType());
	}

	private ImgFactoryHeuristic getHeuristic(final SCIFIOConfig imgOptions) {
		ImgFactoryHeuristic heuristic =
			imgOptions.imgOpenerGetImgFactoryHeuristic();

		if (heuristic == null) heuristic = new DefaultImgFactoryHeuristic();

		return heuristic;
	}

	/**
	 * @param source - Dataset source to open
	 * @param config - Options object for opening this dataset
	 * @return A Reader initialized to open the specified id
	 */
	private Reader createReader(final String source, final SCIFIOConfig config)
		throws ImgIOException
	{

		final boolean computeMinMax = config.imgOpenerIsComputeMinMax();
		statusService.showStatus("Initializing " + source);

		ReaderFilter r = null;
		try {
			r = initializeService.initializeReader(source, config);
			r.enable(ChannelFiller.class);
			r.enable(PlaneSeparator.class).separate(axesToSplit(r));
			if (computeMinMax) r.enable(MinMaxFilter.class);
		}
		catch (final FormatException e) {
			throw new ImgIOException(e);
		}
		catch (final IOException e) {
			throw new ImgIOException(e);
		}
		return r;
	}

	/**
	 * Returns a list of all AxisTypes that should be split out. This is a list of
	 * all non-X,Y planar axes. Always tries to split {@link Axes#CHANNEL}.
	 */
	private AxisType[] axesToSplit(final ReaderFilter r) {
		final Set<AxisType> axes = new HashSet<AxisType>();
		final Metadata meta = r.getTail().getMetadata();
		// Split any non-X,Y axis
		for (final CalibratedAxis t : meta.get(0).getAxesPlanar()) {
			final AxisType type = t.type();
			if (!(type == Axes.X || type == Axes.Y)) {
				axes.add(type);
			}
		}
		// Ensure channel is attempted to be split
		axes.add(Axes.CHANNEL);
		return axes.toArray(new AxisType[axes.size()]);
	}

	private AxisType[] getAxisTypes(final int imageIndex, final Metadata m) {
		final AxisType[] types = new AxisType[m.get(imageIndex).getAxes().size()];
		for (int i = 0; i < types.length; i++) {
			types[i] = m.get(imageIndex).getAxis(i).type();
		}
		return types;
	}

	/** Compiles an N-dimensional list of calibration values. */
	private double[] getCalibration(final int imageIndex, final Metadata m) {

		final double[] calibration = new double[m.get(imageIndex).getAxes().size()];
		for (int i = 0; i < calibration.length; i++) {
			calibration[i] =
				FormatTools
					.getScale(m, imageIndex, m.get(imageIndex).getAxis(i).type());
		}

		return calibration;
	}

	/**
	 * Wraps the given {@link Img} in an {@link ImgPlus} with metadata
	 * corresponding to the specified initialized {@link Reader}.
	 */
	private <T extends RealType<T>> SCIFIOImgPlus<T> makeImgPlus(
		final Img<T> img, final Reader r, final SCIFIOConfig config)
	{
		final String id = r.getCurrentFile();
		final File idFile = new File(id);
		final String name = idFile.exists() ? idFile.getName() : id;

		final double[] cal =
			getCalibration(config.imgOpenerGetIndex(), r.getMetadata());
		final AxisType[] dimTypes =
			getAxisTypes(config.imgOpenerGetIndex(), r.getMetadata());

		final Reader base;
		base = unwrap(r);

		final Metadata meta = r.getMetadata();
		int rgbChannelCount =
			base.getMetadata().get(0).isMultichannel() ? (int) base.getMetadata()
				.get(0).getAxisLength(Axes.CHANNEL) : 1;
		if (base.getMetadata().get(0).isIndexed()) rgbChannelCount = 3;
		final int validBits = meta.get(0).getBitsPerPixel();

		final SCIFIOImgPlus<T> imgPlus =
			new SCIFIOImgPlus<T>(img, name, dimTypes, cal);
		imgPlus.setMetadata(meta);
		imgPlus.setValidBits(validBits);

		int compositeChannelCount = rgbChannelCount;
		if (rgbChannelCount == 1) {
			// HACK: Support ImageJ color mode embedded in TIFF files.
			final String colorMode = (String) meta.getTable().get("Color mode");
			if ("composite".equals(colorMode)) {
				compositeChannelCount = (int) meta.get(0).getAxisLength(Axes.CHANNEL);
			}
		}
		imgPlus.setCompositeChannelCount(compositeChannelCount);

		return imgPlus;
	}

	/**
	 * Finds the lowest level wrapped reader, preferably a {@link ChannelFiller},
	 * but otherwise the base reader. This is useful for determining whether the
	 * input data is intended to be viewed with multiple channels composited
	 * together.
	 */
	private Reader unwrap(final Reader r) {
		if (!(r instanceof ReaderFilter)) return r;
		final ReaderFilter rf = (ReaderFilter) r;
		return rf.getTail();
	}

	/**
	 * Reads planes from the given initialized {@link Reader} into the specified
	 * {@link Img}.
	 */
	private <T extends RealType<T>> void readPlanes(final Reader r,
		final int imageIndex, final T type, final ImgPlus<T> imgPlus,
		final SCIFIOConfig config) throws FormatException, IOException
	{
		// TODO - create better container types; either:
		// 1) an array container type using one byte array per plane
		// 2) as #1, but with an Reader reference reading planes on demand
		// 3) as PlanarRandomAccess, but with an Reader reference
		// reading planes on demand

		// PlanarRandomAccess is useful for efficient access to pixels in ImageJ
		// (e.g., getPixels)
		// #1 is useful for efficient SCIFIO import, and useful for tools
		// needing byte arrays (e.g., BufferedImage Java3D texturing by reference)
		// #2 is useful for efficient memory use for tools wanting matching
		// primitive arrays (e.g., virtual stacks in ImageJ)
		// #3 is useful for efficient memory use

		// get container
		final PlanarAccess<?> planarAccess = utils().getPlanarAccess(imgPlus);
		@SuppressWarnings("rawtypes")
		final RealType inputType =
			utils().makeType(r.getMetadata().get(0).getPixelType());
		final T outputType = type;
		final boolean compatibleTypes =
			outputType.getClass().isAssignableFrom(inputType.getClass());

		// populate planes
		final boolean isPlanar = planarAccess != null && compatibleTypes;
		final boolean isArray =
			utils().getArrayAccess(imgPlus) != null && compatibleTypes;

		final SubRegion region = config.imgOpenerGetRegion();

		final Metadata m = r.getMetadata();

		// Starting indices for the planar dimensions
		final long[] planarMin = new long[m.get(imageIndex).getAxesPlanar().size()];
		// Lengths in the planar dimensions
		final long[] planarLength =
			new long[m.get(imageIndex).getAxesPlanar().size()];
		// Non-planar indices to open
		final DimRange[] npRanges =
			new DimRange[m.get(imageIndex).getAxesNonPlanar().size()];
		final long[] npIndices = new long[npRanges.length];

		// populate plane dimensions
		int index = 0;
		for (final CalibratedAxis planarAxis : m.get(imageIndex).getAxesPlanar()) {
			if (region != null && region.hasRange(planarAxis.type())) {
				planarMin[index] = region.getRange(planarAxis.type()).head();
				planarLength[index] =
					region.getRange(planarAxis.type()).tail() - planarMin[index] + 1;
			}
			else {
				planarMin[index] = 0;
				planarLength[index] = m.get(imageIndex).getAxisLength(planarAxis);
			}
			index++;
		}

		// determine non-planar indices to open
		index = 0;
		for (final CalibratedAxis npAxis : m.get(imageIndex).getAxesNonPlanar()) {
			if (region != null && region.hasRange(npAxis.type())) {
				npRanges[index++] = region.getRange(npAxis.type());
			}
			else {
				npRanges[index++] =
					new DimRange(0l, m.get(imageIndex).getAxisLength(npAxis.type()) - 1);
			}
		}

		PlaneConverter converter = config.imgOpenerGetPlaneConverter();

		if (converter == null) {
			// if we have a PlanarAccess we can use a PlanarAccess converter,
			// otherwise we can use a more general RandomAccess approach
			if (isArray) {
				converter = pcService.getArrayConverter();
			}
			else if (isPlanar) {
				converter = pcService.getPlanarConverter();
			}
			else converter = pcService.getDefaultConverter();
		}

		read(imageIndex, imgPlus, r, config, converter, planarMin, planarLength,
			npRanges, npIndices);

		if (config.imgOpenerIsComputeMinMax()) populateMinMax(r, imgPlus,
			imageIndex);
	}

	@SuppressWarnings("rawtypes")
	private void read(final int imageIndex, final ImgPlus imgPlus,
		final Reader r, final SCIFIOConfig config, final PlaneConverter converter,
		final long[] planarMin, final long[] planarLength,
		final DimRange[] npRanges, final long[] npIndices) throws FormatException,
		IOException
	{
		read(imageIndex, imgPlus, r, config, converter, null, planarMin,
			planarLength, npRanges, npIndices, 0, new int[] { 0 });
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Plane read(final int imageIndex, final ImgPlus imgPlus,
		final Reader r, final SCIFIOConfig config, final PlaneConverter converter,
		Plane tmpPlane, final long[] planarMin, final long[] planarLength,
		final DimRange[] npRanges, final long[] npIndices, final int depth,
		final int[] planeCount) throws FormatException, IOException
	{
		if (depth < npRanges.length) {
			// We need to invert the depth index to get the current non-planar
			// axis index, to ensure axes are iteratead in fastest to slowest order
			final int npPosition = npRanges.length - 1 - depth;
			// Recursive step. Sets the non-planar indices
			for (int i = 0; i < npRanges[npPosition].indices().size(); i++) {
				npIndices[npPosition] = npRanges[npPosition].indices().get(i);
				tmpPlane =
					read(imageIndex, imgPlus, r, config, converter, tmpPlane, planarMin,
						planarLength, npRanges, npIndices, depth + 1, planeCount);
			}
		}
		else {
			// Terminal step. Reads the plane at the rasterized index, given the
			// non-planar indices
			final int planeIndex =
				(int) FormatTools.positionToRaster(0, r, npIndices);

			if (config.imgOpenerIsComputeMinMax()) {
				populateMinMax(r, imgPlus, imageIndex);
			}
			if (tmpPlane == null) {
				tmpPlane =
					r.openPlane(config.imgOpenerGetIndex(), planeIndex, planarMin,
						planarLength);
			}
			else {
				tmpPlane =
					r.openPlane(config.imgOpenerGetIndex(), planeIndex, tmpPlane,
						planarMin, planarLength, config);
			}

			// copy the data to the ImgPlus
			converter.populatePlane(r, imageIndex, planeCount[0],
				tmpPlane.getBytes(), imgPlus, config);

			// store color table
			imgPlus.setColorTable(tmpPlane.getColorTable(), planeCount[0]);

			// Update plane count
			planeCount[0]++;
		}

		return tmpPlane;
	}

	private void populateMinMax(final Reader r, final ImgPlus<?> imgPlus,
		final int imageIndex)
	{
		final int sizeC =
			(int) r.getMetadata().get(imageIndex).getAxisLength(Axes.CHANNEL);
		final ReaderFilter rf = (ReaderFilter) r;
		final MinMaxFilter minMax = rf.enable(MinMaxFilter.class);
		for (int c = 0; c < sizeC; c++) {
			final Double min =
				minMax.getAxisKnownMinimum(imageIndex, Axes.CHANNEL, c);
			final Double max =
				minMax.getAxisKnownMinimum(imageIndex, Axes.CHANNEL, c);
			imgPlus.setChannelMinimum(c, min == null ? Double.NaN : min);
			imgPlus.setChannelMaximum(c, max == null ? Double.NaN : max);
		}
	}
}
