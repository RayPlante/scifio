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

package io.scif.formats;

import io.scif.AbstractFormat;
import io.scif.AbstractTranslator;
import io.scif.AbstractWriter;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.HasColorTable;
import io.scif.ImageMetadata;
import io.scif.MetaTable;
import io.scif.MetadataLevel;
import io.scif.Plane;
import io.scif.Translator;
import io.scif.codec.CompressionType;
import io.scif.common.Constants;
import io.scif.common.DataTools;
import io.scif.common.DateTools;
import io.scif.config.SCIFIOConfig;
import io.scif.formats.tiff.IFD;
import io.scif.formats.tiff.IFDList;
import io.scif.formats.tiff.PhotoInterp;
import io.scif.formats.tiff.TiffCompression;
import io.scif.formats.tiff.TiffParser;
import io.scif.formats.tiff.TiffRational;
import io.scif.formats.tiff.TiffSaver;
import io.scif.gui.AWTImageTools;
import io.scif.io.Location;
import io.scif.io.RandomAccessInputStream;
import io.scif.io.RandomAccessOutputStream;
import io.scif.util.FormatTools;
import io.scif.xml.XMLService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;

import net.imglib2.display.ColorTable;
import net.imglib2.meta.Axes;
import net.imglib2.meta.CalibratedAxis;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Format.class, priority = TIFFFormat.PRIORITY)
public class TIFFFormat extends AbstractFormat {

	// -- Constants --

	public static final double PRIORITY = MinimalTIFFFormat.PRIORITY + 1;
	public static final String[] COMPANION_SUFFIXES = { "xml", "txt" };
	public static final String[] TIFF_SUFFIXES = { "tif", "tiff", "tf2", "tf8",
		"btf" };

	// -- Format API methods --

	@Override
	public String getFormatName() {
		return "Tagged Image File Format";
	}

	// -- AbstractFormat Methods --

	@Override
	protected String[] makeSuffixArray() {
		return TIFF_SUFFIXES;
	}

	// -- Nested classes --

	/**
	 * @author Mark Hiner
	 */
	public static class Metadata extends MinimalTIFFFormat.Metadata {

		// -- Fields --

		private boolean populateImageMetadata = true;

		// FIXME: these are duplicating metadata store information..
		private String creationDate;
		private String experimenterFirstName;
		private String experimenterLastName;
		private String experimenterEmail;
		private String imageDescription;

		private String companionFile;
		private String description;
		private String calibrationUnit;
		private Double timeIncrement;
		private Integer xOrigin, yOrigin;

		// -- TIFFMetadata getters and setters --

		public String getCompanionFile() {
			return companionFile;
		}

		public void setCompanionFile(final String companionFile) {
			this.companionFile = companionFile;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(final String description) {
			this.description = description;
		}

		public String getCalibrationUnit() {
			return calibrationUnit;
		}

		public void setCalibrationUnit(final String calibrationUnit) {
			this.calibrationUnit = calibrationUnit;
		}

		public Double getTimeIncrement() {
			return timeIncrement == null ? 1.0 : timeIncrement;
		}

		public void setTimeIncrement(final Double timeIncrement) {
			this.timeIncrement = timeIncrement;
		}

		public Integer getxOrigin() {
			return xOrigin;
		}

		public void setxOrigin(final Integer xOrigin) {
			this.xOrigin = xOrigin;
		}

		public Integer getyOrigin() {
			return yOrigin;
		}

		public void setyOrigin(final Integer yOrigin) {
			this.yOrigin = yOrigin;
		}

		public String getCreationDate() {
			return creationDate;
		}

		public void setCreationDate(final String creationDate) {
			this.creationDate = creationDate;
		}

		public String getExperimenterFirstName() {
			return experimenterFirstName;
		}

		public void setExperimenterFirstName(final String experimenterFirstName) {
			this.experimenterFirstName = experimenterFirstName;
		}

		public String getExperimenterLastName() {
			return experimenterLastName;
		}

		public void setExperimenterLastName(final String experimenterLastName) {
			this.experimenterLastName = experimenterLastName;
		}

		public String getExperimenterEmail() {
			return experimenterEmail;
		}

		public void setExperimenterEmail(final String experimenterEmail) {
			this.experimenterEmail = experimenterEmail;
		}

		public String getImageDescription() {
			return imageDescription;
		}

		public void setImageDescription(final String imageDescription) {
			this.imageDescription = imageDescription;
		}

		// -- Metadata API Methods --

		@Override
		public void createImageMetadata(final int imageCount) {
			populateImageMetadata = true;
			super.createImageMetadata(imageCount);
		}

		@Override
		public void populateImageMetadata() {
			if (populateImageMetadata) super.populateImageMetadata();

			final ImageMetadata m = get(0);

			if (getIfds().size() > 1) m.setOrderCertain(false);
			// set the X and Y pixel dimensions

			try {
				final double pixX = getIfds().get(0).getXResolution();
				final double pixY = getIfds().get(0).getYResolution();

				if (pixX > 0 && pixX < Double.POSITIVE_INFINITY) {
					FormatTools.calibrate(m.getAxis(Axes.X), pixX, 0);
				}
				else {
					log().warn("Expected positive value for PhysicalSizeX; got " + pixX);
				}
				if (pixY > 0 && pixX < Double.POSITIVE_INFINITY) {
					FormatTools.calibrate(m.getAxis(Axes.Y), pixY, 0);
				}
				else {
					log().warn("Expected positive value for PhysicalSizeY; got " + pixY);
				}
			}
			catch (final FormatException e) {
				log().error("Failed to get x, y pixel sizes", e);
			}
		}

		@Override
		public void close(final boolean fileOnly) throws IOException {
			super.close(fileOnly);
			if (!fileOnly) {
				companionFile = null;
				description = null;
				calibrationUnit = null;
				timeIncrement = null;
				xOrigin = null;
				yOrigin = null;
			}
		}

	}

	/**
	 * @author Mark Hiner
	 */
	public static class Parser extends BaseTIFFParser {

		// -- Constants --

		public static final int IMAGEJ_TAG = 50839;

		// -- Fields --

		@Parameter
		private XMLService xmlService;

		// -- Parser API Methods --

		@Override
		public String[] getImageUsedFiles(final int ImageIndex,
			final boolean noPixels)
		{
			if (noPixels) {
				return getMetadata().getCompanionFile() == null ? null
					: new String[] { getMetadata().getCompanionFile() };
			}
			if (getMetadata().getCompanionFile() != null) return new String[] {
				getMetadata().getCompanionFile(), getSource().getFileName() };
			return new String[] { getSource().getFileName() };
		}

		// -- BaseTIFFParser API Methods

		@Override
		protected void initMetadata(final Metadata meta, final SCIFIOConfig config)
			throws FormatException, IOException
		{
			final IFDList ifds = meta.getIfds();
			final String comment = ifds.get(0).getComment();
			final MetaTable table = meta.getTable();

			log().info("Checking comment style");

			// check for reusable proprietary tags (65000-65535),
			// which may contain additional metadata

			final MetadataLevel level = config.parserGetLevel();
			if (level != MetadataLevel.MINIMUM) {
				final Integer[] tags = ifds.get(0).keySet().toArray(new Integer[0]);
				for (final Integer tag : tags) {
					if (tag.intValue() >= 65000) {
						final Object value = ifds.get(0).get(tag);
						if (value instanceof short[]) {
							final short[] s = (short[]) value;
							final byte[] b = new byte[s.length];
							for (int i = 0; i < b.length; i++) {
								b[i] = (byte) s[i];
							}
							String metadata =
								DataTools.stripString(new String(b, Constants.ENCODING));
							if (metadata.indexOf("xml") != -1) {
								metadata = metadata.substring(metadata.indexOf("<"));
								metadata =
									"<root>" + xmlService.sanitizeXML(metadata) + "</root>";
								try {
									final Hashtable<String, String> xmlMetadata =
										xmlService.parseXML(metadata);
									for (final String key : xmlMetadata.keySet()) {
										meta.getTable().put(key, xmlMetadata.get(key));
									}
								}
								catch (final IOException e) {}
							}
							else {
								meta.getTable().put(tag.toString(), metadata);
							}
						}
					}
				}
			}

			// check for ImageJ-style TIFF comment
			final boolean ij = checkCommentImageJ(comment);
			if (ij) parseCommentImageJ(meta, comment);

			// check for MetaMorph-style TIFF comment
			final boolean metamorph = checkCommentMetamorph(meta, comment);
			if (metamorph && level != MetadataLevel.MINIMUM) {
				parseCommentMetamorph(meta, comment);
			}
			table.put("MetaMorph", metamorph ? "yes" : "no");

			// check for other INI-style comment
			if (!ij && !metamorph && level != MetadataLevel.MINIMUM) {
				parseCommentGeneric(meta, comment);
			}

			// check for another file with the same name

			if (config.groupableIsGroupFiles()) {
				final Location currentFile =
					new Location(getContext(), getSource().getFileName())
						.getAbsoluteFile();
				final String currentName = currentFile.getName();
				final Location directory = currentFile.getParentFile();
				final String[] files = directory.list(true);
				if (files != null) {
					for (final String file : files) {
						String name = file;
						if (name.indexOf(".") != -1) {
							name = name.substring(0, name.indexOf("."));
						}

						if (currentName.startsWith(name) &&
							FormatTools.checkSuffix(name, COMPANION_SUFFIXES))
						{
							meta.setCompanionFile(new Location(getContext(), directory, file)
								.getAbsolutePath());
							break;
						}
					}
				}
			}

			// TODO : parse companion file once loci.parsers package is in place

//    MetadataStore store = makeFilterMetadata();
//    if (meta.getDescription() != null) {
//      store.setImageDescription(description, 0);
//    }
			super.initMetadata(meta, config);
		}

		// -- Helper methods --

		private boolean checkCommentImageJ(final String comment) {
			return comment != null && comment.startsWith("ImageJ=");
		}

		private boolean checkCommentMetamorph(final Metadata meta,
			final String comment)
		{
			final String software =
				meta.getIfds().get(0).getIFDTextValue(IFD.SOFTWARE);
			return comment != null && software != null &&
				software.indexOf("MetaMorph") != -1;
		}

		private void parseCommentImageJ(final Metadata meta, String comment)
			throws FormatException, IOException
		{

			meta.populateImageMetadata();
			meta.populateImageMetadata = false;
			final MetaTable table = meta.getTable();

			final int nl = comment.indexOf("\n");
			table.put("ImageJ", nl < 0 ? comment.substring(7) : comment.substring(7,
				nl));
			meta.getTable().remove("Comment");
			meta.setDescription("");

			int z = 1, t = 1;
			int c = (int) meta.get(0).getAxisLength(Axes.CHANNEL);

			IFDList ifds = meta.getIfds();

			if (ifds.get(0).containsKey(IMAGEJ_TAG)) {
				comment += "\n" + ifds.get(0).getIFDTextValue(IMAGEJ_TAG);
			}

			// parse ImageJ metadata (ZCT sizes, calibration units, etc.)
			final StringTokenizer st = new StringTokenizer(comment, "\n");
			while (st.hasMoreTokens()) {
				final String token = st.nextToken();
				String value = null;
				final int eq = token.indexOf("=");
				if (eq >= 0) value = token.substring(eq + 1);

				if (token.startsWith("channels=")) c = parseInt(value);
				else if (token.startsWith("slices=")) z = parseInt(value);
				else if (token.startsWith("frames=")) t = parseInt(value);
				else if (token.startsWith("mode=")) {
					table.put("Color mode", value);
				}
				else if (token.startsWith("unit=")) {
					meta.setCalibrationUnit(value);
					table.put("Unit", meta.getCalibrationUnit());
				}
				else if (token.startsWith("finterval=")) {
					meta.setTimeIncrement(parseDouble(value));
					table.put("Frame Interval", meta.getTimeIncrement());
				}
				else if (token.startsWith("spacing=")) {
					final double physicalSizeZ = parseDouble(value);
					if (physicalSizeZ >= 0) {
						if (meta.get(0).getAxis(Axes.Z) == null) {
							meta.get(0).addAxis(Axes.Z, 1);
						}

						FormatTools
							.calibrate(meta.get(0).getAxis(Axes.Z), physicalSizeZ, 0);
					}
					table.put("Spacing", physicalSizeZ);
				}
				else if (token.startsWith("xorigin=")) {
					meta.setxOrigin(parseInt(value));
					table.put("X Origin", meta.getxOrigin());
				}
				else if (token.startsWith("yorigin=")) {
					meta.setyOrigin(parseInt(value));
					table.put("Y Origin", meta.getyOrigin());
				}
				else if (eq > 0) {
					table.put(token.substring(0, eq).trim(), value);
				}
			}
			if (z * c * t == c && meta.get(0).isMultichannel()) {
				t = (int) meta.get(0).getPlaneCount();
			}

			final ImageMetadata m = meta.get(0);

			m.setAxisTypes(Axes.X, Axes.Y, Axes.CHANNEL, Axes.Z, Axes.TIME);

			if (z * t * (m.isMultichannel() ? 1 : c) == ifds.size()) {
				m.setAxisLength(Axes.Z, z);
				m.setAxisLength(Axes.TIME, t);
				if (!m.isMultichannel()) {
					m.setAxisLength(Axes.CHANNEL, c);
				}
			}
			else if (z * c * t == ifds.size() && m.isMultichannel()) {
				m.setAxisLength(Axes.Z, z);
				m.setAxisLength(Axes.TIME, t);
				m.setAxisLength(Axes.CHANNEL, m.getAxisLength(Axes.CHANNEL) * c);
			}
			else if (ifds.size() == 1 && z * t > ifds.size() &&
				ifds.get(0).getCompression() == TiffCompression.UNCOMPRESSED)
			{
				// file is likely corrupt (missing end IFDs)
				//
				// ImageJ writes TIFF files like this:
				// IFD #0
				// comment
				// all pixel data
				// IFD #1
				// IFD #2
				// ...
				//
				// since we know where the pixel data is, we can create fake
				// IFDs in an attempt to read the rest of the pixels

				final IFD firstIFD = ifds.get(0);

				final int planeSize =
					(int) (m.getAxisLength(Axes.X) * m.getAxisLength(Axes.Y) *
						m.getAxisLength(Axes.CHANNEL) * FormatTools.getBytesPerPixel(m
						.getPixelType()));
				final long[] stripOffsets = firstIFD.getStripOffsets();
				final long[] stripByteCounts = firstIFD.getStripByteCounts();

				final long endOfFirstPlane =
					stripOffsets[stripOffsets.length - 1] +
						stripByteCounts[stripByteCounts.length - 1];
				final long totalBytes = getSource().length() - endOfFirstPlane;
				final int totalPlanes = (int) (totalBytes / planeSize) + 1;

				ifds = new IFDList();
				ifds.add(firstIFD);
				for (int i = 1; i < totalPlanes; i++) {
					final IFD ifd = new IFD(firstIFD, log());
					ifds.add(ifd);
					final long[] prevOffsets = ifds.get(i - 1).getStripOffsets();
					final long[] offsets = new long[stripOffsets.length];
					offsets[0] =
						prevOffsets[prevOffsets.length - 1] +
							stripByteCounts[stripByteCounts.length - 1];
					for (int j = 1; j < offsets.length; j++) {
						offsets[j] = offsets[j - 1] + stripByteCounts[j - 1];
					}
					ifd.putIFDValue(IFD.STRIP_OFFSETS, offsets);
				}

				if (z * c * t == ifds.size()) {
					m.setAxisLength(Axes.Z, z);
					m.setAxisLength(Axes.TIME, t);
					m.setAxisLength(Axes.CHANNEL, c);
				}
				else if (z * t == ifds.size()) {
					m.setAxisLength(Axes.Z, z);
					m.setAxisLength(Axes.TIME, t);
				}
				else m.setAxisLength(Axes.Z, ifds.size());
			}
			else {
				m.setAxisLength(Axes.TIME, ifds.size());
			}

			// Clean up length 1 axes
			final ArrayList<CalibratedAxis> validAxes =
				new ArrayList<CalibratedAxis>();

			for (final CalibratedAxis axis : m.getAxes()) {
				if (m.getAxisLength(axis) > 1) {
					validAxes.add(axis);
				}
			}

			m.setAxes(validAxes.toArray(new CalibratedAxis[validAxes.size()]));
		}

		private void
			parseCommentMetamorph(final Metadata meta, final String comment)
		{
			// parse key/value pairs
			final StringTokenizer st = new StringTokenizer(comment, "\n");
			while (st.hasMoreTokens()) {
				final String line = st.nextToken();
				final int colon = line.indexOf(":");
				if (colon < 0) {
					meta.getTable().put("Comment", line);
					meta.setDescription(line);
					continue;
				}
				final String key = line.substring(0, colon);
				final String value = line.substring(colon + 1);
				meta.getTable().put(key, value);
			}
		}

		private void parseCommentGeneric(final Metadata meta, String comment) {
			if (comment == null) return;
			final String[] lines = comment.split("\n");
			if (lines.length > 1) {
				comment = "";
				for (final String line : lines) {
					final int eq = line.indexOf("=");
					if (eq != -1) {
						final String key = line.substring(0, eq).trim();
						final String value = line.substring(eq + 1).trim();
						meta.getTable().put(key, value);
					}
					else if (!line.startsWith("[")) {
						comment += line + "\n";
					}
				}
				meta.getTable().put("Comment", comment);
				meta.setDescription(comment);
			}
		}

		private int parseInt(final String s) {
			try {
				return Integer.parseInt(s);
			}
			catch (final NumberFormatException e) {
				log().debug("Failed to parse integer value", e);
			}
			return 0;
		}

		private double parseDouble(final String s) {
			try {
				return Double.parseDouble(s);
			}
			catch (final NumberFormatException e) {
				log().debug("Failed to parse floating point value", e);
			}
			return 0;
		}
	}

	/**
	 * BaseTiffReader is the superclass for file format readers compatible with or
	 * derived from the TIFF 6.0 file format.
	 * 
	 * @author Curtis Rueden
	 * @author Melissa Linkert
	 */
	public static abstract class BaseTIFFParser extends
		MinimalTIFFFormat.Parser<Metadata>
	{

		// -- Constants --

		public static final String[] DATE_FORMATS = { "yyyy:MM:dd HH:mm:ss",
			"dd/MM/yyyy HH:mm:ss.SS", "MM/dd/yyyy hh:mm:ss.SSS aa",
			"yyyyMMdd HH:mm:ss.SSS", "yyyy/MM/dd HH:mm:ss" };

		// -- Parser API Methods --

		@Override
		protected void typedParse(final RandomAccessInputStream stream,
			final Metadata meta, final SCIFIOConfig config) throws IOException,
			FormatException
		{

			super.typedParse(stream, meta, config);
			initMetadata(meta, config);
		}

		// -- Internal BaseTiffReader API methods --

		/** Populates the metadata hashtable and metadata store. */
		protected void initMetadata(final Metadata meta, final SCIFIOConfig config)
			throws FormatException, IOException
		{
			if (config.parserGetLevel() == MetadataLevel.MINIMUM) {
				return;
			}

			final IFDList ifds = meta.getIfds();
			final MetaTable table = meta.getTable();

			for (int i = 0; i < ifds.size(); i++) {
				put(table, "PageName #" + i, ifds.get(i), IFD.PAGE_NAME);
			}

			final IFD firstIFD = ifds.get(0);
			put(table, "ImageWidth", firstIFD, IFD.IMAGE_WIDTH);
			put(table, "ImageLength", firstIFD, IFD.IMAGE_LENGTH);
			put(table, "BitsPerSample", firstIFD, IFD.BITS_PER_SAMPLE);

			// retrieve EXIF values, if available

			if (ifds.get(0).containsKey(IFD.EXIF)) {
				final IFDList exifIFDs = meta.getTiffParser().getExifIFDs();
				if (exifIFDs.size() > 0) {
					final IFD exif = exifIFDs.get(0);
					for (final Integer key : exif.keySet()) {
						final int k = key.intValue();
						meta.getTable().put(getExifTagName(k), exif.get(key));
					}
				}
			}

			final TiffCompression comp = firstIFD.getCompression();
			table.put("Compression", comp.getCodecName());

			final PhotoInterp photo = firstIFD.getPhotometricInterpretation();
			final String photoInterp = photo.getName();
			final String metaDataPhotoInterp = photo.getMetadataType();
			table.put("PhotometricInterpretation", photoInterp);
			table.put("MetaDataPhotometricInterpretation", metaDataPhotoInterp);

			putInt(table, "CellWidth", firstIFD, IFD.CELL_WIDTH);
			putInt(table, "CellLength", firstIFD, IFD.CELL_LENGTH);

			final int or = firstIFD.getIFDIntValue(IFD.ORIENTATION);

			// adjust the width and height if necessary
			if (or == 8) {
				put(table, "ImageWidth", firstIFD, IFD.IMAGE_LENGTH);
				put(table, "ImageLength", firstIFD, IFD.IMAGE_WIDTH);
			}

			String orientation = null;
			// there is no case 0
			switch (or) {
				case 1:
					orientation = "1st row -> top; 1st column -> left";
					break;
				case 2:
					orientation = "1st row -> top; 1st column -> right";
					break;
				case 3:
					orientation = "1st row -> bottom; 1st column -> right";
					break;
				case 4:
					orientation = "1st row -> bottom; 1st column -> left";
					break;
				case 5:
					orientation = "1st row -> left; 1st column -> top";
					break;
				case 6:
					orientation = "1st row -> right; 1st column -> top";
					break;
				case 7:
					orientation = "1st row -> right; 1st column -> bottom";
					break;
				case 8:
					orientation = "1st row -> left; 1st column -> bottom";
					break;
			}
			table.put("Orientation", orientation);
			putInt(table, "SamplesPerPixel", firstIFD, IFD.SAMPLES_PER_PIXEL);

			put(table, "Software", firstIFD, IFD.SOFTWARE);
			put(table, "Instrument Make", firstIFD, IFD.MAKE);
			put(table, "Instrument Model", firstIFD, IFD.MODEL);
			put(table, "Document Name", firstIFD, IFD.DOCUMENT_NAME);
			put(table, "DateTime", firstIFD, IFD.DATE_TIME);
			put(table, "Artist", firstIFD, IFD.ARTIST);

			put(table, "HostComputer", firstIFD, IFD.HOST_COMPUTER);
			put(table, "Copyright", firstIFD, IFD.COPYRIGHT);

			put(table, "NewSubfileType", firstIFD, IFD.NEW_SUBFILE_TYPE);

			final int thresh = firstIFD.getIFDIntValue(IFD.THRESHHOLDING);
			String threshholding = null;
			switch (thresh) {
				case 1:
					threshholding = "No dithering or halftoning";
					break;
				case 2:
					threshholding = "Ordered dithering or halftoning";
					break;
				case 3:
					threshholding = "Randomized error diffusion";
					break;
			}
			table.put("Threshholding", threshholding);

			final int fill = firstIFD.getIFDIntValue(IFD.FILL_ORDER);
			String fillOrder = null;
			switch (fill) {
				case 1:
					fillOrder =
						"Pixels with lower column values are stored "
							+ "in the higher order bits of a byte";
					break;
				case 2:
					fillOrder =
						"Pixels with lower column values are stored "
							+ "in the lower order bits of a byte";
					break;
			}
			table.put("FillOrder", fillOrder);

			putInt(table, "Make", firstIFD, IFD.MAKE);
			putInt(table, "Model", firstIFD, IFD.MODEL);
			putInt(table, "MinSampleValue", firstIFD, IFD.MIN_SAMPLE_VALUE);
			putInt(table, "MaxSampleValue", firstIFD, IFD.MAX_SAMPLE_VALUE);
			putInt(table, "XResolution", firstIFD, IFD.X_RESOLUTION);
			putInt(table, "YResolution", firstIFD, IFD.Y_RESOLUTION);

			final int planar = firstIFD.getIFDIntValue(IFD.PLANAR_CONFIGURATION);
			String planarConfig = null;
			switch (planar) {
				case 1:
					planarConfig = "Chunky";
					break;
				case 2:
					planarConfig = "Planar";
					break;
			}
			table.put("PlanarConfiguration", planarConfig);

			putInt(table, "XPosition", firstIFD, IFD.X_POSITION);
			putInt(table, "YPosition", firstIFD, IFD.Y_POSITION);
			putInt(table, "FreeOffsets", firstIFD, IFD.FREE_OFFSETS);
			putInt(table, "FreeByteCounts", firstIFD, IFD.FREE_BYTE_COUNTS);
			putInt(table, "GrayResponseUnit", firstIFD, IFD.GRAY_RESPONSE_UNIT);
			putInt(table, "GrayResponseCurve", firstIFD, IFD.GRAY_RESPONSE_CURVE);
			putInt(table, "T4Options", firstIFD, IFD.T4_OPTIONS);
			putInt(table, "T6Options", firstIFD, IFD.T6_OPTIONS);

			final int res = firstIFD.getIFDIntValue(IFD.RESOLUTION_UNIT);
			String resUnit = null;
			switch (res) {
				case 1:
					resUnit = "None";
					break;
				case 2:
					resUnit = "Inch";
					break;
				case 3:
					resUnit = "Centimeter";
					break;
			}
			table.put("ResolutionUnit", resUnit);

			putInt(table, "PageNumber", firstIFD, IFD.PAGE_NUMBER);
			putInt(table, "TransferFunction", firstIFD, IFD.TRANSFER_FUNCTION);

			final int predict = firstIFD.getIFDIntValue(IFD.PREDICTOR);
			String predictor = null;
			switch (predict) {
				case 1:
					predictor = "No prediction scheme";
					break;
				case 2:
					predictor = "Horizontal differencing";
					break;
			}
			table.put("Predictor", predictor);

			putInt(table, "WhitePoint", firstIFD, IFD.WHITE_POINT);
			putInt(table, "PrimaryChromacities", firstIFD, IFD.PRIMARY_CHROMATICITIES);

			putInt(table, "HalftoneHints", firstIFD, IFD.HALFTONE_HINTS);
			putInt(table, "TileWidth", firstIFD, IFD.TILE_WIDTH);
			putInt(table, "TileLength", firstIFD, IFD.TILE_LENGTH);
			putInt(table, "TileOffsets", firstIFD, IFD.TILE_OFFSETS);
			putInt(table, "TileByteCounts", firstIFD, IFD.TILE_BYTE_COUNTS);

			final int ink = firstIFD.getIFDIntValue(IFD.INK_SET);
			String inkSet = null;
			switch (ink) {
				case 1:
					inkSet = "CMYK";
					break;
				case 2:
					inkSet = "Other";
					break;
			}
			table.put("InkSet", inkSet);

			putInt(table, "InkNames", firstIFD, IFD.INK_NAMES);
			putInt(table, "NumberOfInks", firstIFD, IFD.NUMBER_OF_INKS);
			putInt(table, "DotRange", firstIFD, IFD.DOT_RANGE);
			put(table, "TargetPrinter", firstIFD, IFD.TARGET_PRINTER);
			putInt(table, "ExtraSamples", firstIFD, IFD.EXTRA_SAMPLES);

			final int fmt = firstIFD.getIFDIntValue(IFD.SAMPLE_FORMAT);
			String sampleFormat = null;
			switch (fmt) {
				case 1:
					sampleFormat = "unsigned integer";
					break;
				case 2:
					sampleFormat = "two's complement signed integer";
					break;
				case 3:
					sampleFormat = "IEEE floating point";
					break;
				case 4:
					sampleFormat = "undefined";
					break;
			}
			table.put("SampleFormat", sampleFormat);

			putInt(table, "SMinSampleValue", firstIFD, IFD.S_MIN_SAMPLE_VALUE);
			putInt(table, "SMaxSampleValue", firstIFD, IFD.S_MAX_SAMPLE_VALUE);
			putInt(table, "TransferRange", firstIFD, IFD.TRANSFER_RANGE);

			final int jpeg = firstIFD.getIFDIntValue(IFD.JPEG_PROC);
			String jpegProc = null;
			switch (jpeg) {
				case 1:
					jpegProc = "baseline sequential process";
					break;
				case 14:
					jpegProc = "lossless process with Huffman coding";
					break;
			}
			table.put("JPEGProc", jpegProc);

			putInt(table, "JPEGInterchangeFormat", firstIFD,
				IFD.JPEG_INTERCHANGE_FORMAT);
			putInt(table, "JPEGRestartInterval", firstIFD, IFD.JPEG_RESTART_INTERVAL);

			putInt(table, "JPEGLosslessPredictors", firstIFD,
				IFD.JPEG_LOSSLESS_PREDICTORS);
			putInt(table, "JPEGPointTransforms", firstIFD, IFD.JPEG_POINT_TRANSFORMS);
			putInt(table, "JPEGQTables", firstIFD, IFD.JPEG_Q_TABLES);
			putInt(table, "JPEGDCTables", firstIFD, IFD.JPEG_DC_TABLES);
			putInt(table, "JPEGACTables", firstIFD, IFD.JPEG_AC_TABLES);
			putInt(table, "YCbCrCoefficients", firstIFD, IFD.Y_CB_CR_COEFFICIENTS);

			final int ycbcr = firstIFD.getIFDIntValue(IFD.Y_CB_CR_SUB_SAMPLING);
			String subSampling = null;
			switch (ycbcr) {
				case 1:
					subSampling = "chroma image dimensions = luma image dimensions";
					break;
				case 2:
					subSampling =
						"chroma image dimensions are " + "half the luma image dimensions";
					break;
				case 4:
					subSampling =
						"chroma image dimensions are " + "1/4 the luma image dimensions";
					break;
			}
			table.put("YCbCrSubSampling", subSampling);

			putInt(table, "YCbCrPositioning", firstIFD, IFD.Y_CB_CR_POSITIONING);
			putInt(table, "ReferenceBlackWhite", firstIFD, IFD.REFERENCE_BLACK_WHITE);

			// bits per sample and number of channels
			final int[] q = firstIFD.getBitsPerSample();
			final int bps = q[0];
			int numC = q.length;

			// numC isn't set properly if we have an indexed color image, so we need
			// to reset it here

			if (photo == PhotoInterp.RGB_PALETTE || photo == PhotoInterp.CFA_ARRAY) {
				numC = 3;
			}

			table.put("BitsPerSample", bps);
			table.put("NumberOfChannels", numC);

			// format the creation date to ISO 8601

			final String creationDate = getImageCreationDate(meta);
			final String date = DateTools.formatDate(creationDate, DATE_FORMATS);
			if (creationDate != null && date == null) {
				log().warn("unknown creation date format: " + creationDate);
			}

			meta.setCreationDate(date);

			// populate Experimenter
			final String artist = firstIFD.getIFDTextValue(IFD.ARTIST);

			if (artist != null) {
				String firstName = null, lastName = null;
				final int ndx = artist.indexOf(" ");
				if (ndx < 0) lastName = artist;
				else {
					firstName = artist.substring(0, ndx);
					lastName = artist.substring(ndx + 1);
				}
				final String email = firstIFD.getIFDStringValue(IFD.HOST_COMPUTER);
				meta.setExperimenterFirstName(firstName);
				meta.setExperimenterLastName(lastName);
				meta.setExperimenterEmail(email);
			}

			meta.setImageDescription(firstIFD.getComment());
		}

		/**
		 * Retrieves the image creation date.
		 * 
		 * @return the image creation date.
		 */
		protected String getImageCreationDate(final Metadata meta) {
			final Object o = meta.getIfds().get(0).getIFDValue(IFD.DATE_TIME);
			if (o instanceof String) return (String) o;
			if (o instanceof String[]) return ((String[]) o)[0];
			return null;
		}

		// -- Internal FormatReader API methods - metadata convenience --

		// TODO : the 'put' methods that accept primitive types could probably be
		// removed, as there are now 'addGlobalMeta' methods that accept
		// primitive types

		protected void put(final MetaTable table, final String key, final IFD ifd,
			final int tag)
		{
			table.put(key, ifd.getIFDValue(tag));
		}

		protected void putInt(final MetaTable table, final String key,
			final IFD ifd, final int tag)
		{
			table.put(key, ifd.getIFDIntValue(tag));
		}

		// -- Helper methods --

		public static String getExifTagName(final int tag) {
			return IFD.getIFDTagName(tag);
		}
	}

	/**
	 * TiffReader is the file format reader for regular TIFF files, not of any
	 * specific TIFF variant.
	 * 
	 * @author Curtis Rueden
	 * @author Melissa Linkert
	 */
	public static class Reader<M extends Metadata> extends
		MinimalTIFFFormat.Reader<M>
	{

	}

	/**
	 * TiffWriter is the file format writer for TIFF files.
	 * <p>
	 * NB: BigTIFF writing can be controlled via the {@link #setBigTiff(boolean)}
	 * method, or by passing a {@link SCIFIOConfig} with a key of
	 * {@link Writer#BIG_TIFF_KEY} paired to the desired value. If not explicitly
	 * turned on or off, BigTIFF will be written if the output dataset is larger
	 * than 2GB in size.
	 * </p>
	 */
	public static class Writer<M extends Metadata> extends AbstractWriter<M> {

		// -- Constants --

		public static final String COMPRESSION_UNCOMPRESSED =
			CompressionType.UNCOMPRESSED.getCompression();
		public static final String COMPRESSION_LZW = CompressionType.LZW
			.getCompression();
		public static final String COMPRESSION_J2K = CompressionType.J2K
			.getCompression();
		public static final String COMPRESSION_J2K_LOSSY =
			CompressionType.J2K_LOSSY.getCompression();
		public static final String COMPRESSION_JPEG = CompressionType.JPEG
			.getCompression();
		public static final String BIG_TIFF_KEY = "WRITE_BIG_TIFF";

		// -- Fields --

		/** Whether or not the output file is a BigTIFF file. */
		private Boolean isBigTIFF = null;

		/** The TiffSaver that will do most of the writing. */
		private TiffSaver tiffSaver;

		/** Input stream to use when overwriting data. */
		private RandomAccessInputStream in;

		/** Whether or not to check the parameters passed to saveBytes. */
		private final boolean checkParams = true;

		// -- AbstractWriter Methods --

		@Override
		protected String[] makeCompressionTypes() {
			return new String[] { COMPRESSION_UNCOMPRESSED, COMPRESSION_LZW,
				COMPRESSION_J2K, COMPRESSION_J2K_LOSSY, COMPRESSION_JPEG };
		}

		// -- TIFFWriter API Methods --

		/**
		 * Sets whether or not BigTIFF files should be written. This flag is not
		 * reset when close() is called.
		 */
		public void setBigTiff(final boolean bigTiff) {
			isBigTIFF = bigTiff;
		}

		/**
		 * @return Whether or not this Writer is configured to write BigTIFF data.
		 */
		public boolean isBigTiff() {
			return isBigTIFF == null ? false : isBigTIFF;
		}

		/**
		 * Saves the given image to the specified series in the current file. The
		 * IFD hashtable allows specification of TIFF parameters such as bit depth,
		 * compression and units.
		 */
		public void savePlane(final int imageIndex, final long planeIndex,
			final Plane plane, IFD ifd, final long[] planeMin, final long[] planeMax)
			throws IOException, FormatException
		{
			final byte[] buf = plane.getBytes();
			if (checkParams) checkParams(imageIndex, planeIndex, buf, planeMin,
				planeMax);
			final int xAxis = getMetadata().get(imageIndex).getAxisIndex(Axes.X);
			final int yAxis = getMetadata().get(imageIndex).getAxisIndex(Axes.Y);
			final int x = (int) planeMin[xAxis], y = (int) planeMin[yAxis], w =
				(int) planeMax[xAxis], h = (int) planeMax[yAxis];
			if (ifd == null) ifd = new IFD(log());
			final int type = getMetadata().get(imageIndex).getPixelType();
			final long index = planeIndex;
			// This operation is synchronized
			synchronized (this) {
				// This operation is synchronized against the TIFF saver.
				synchronized (tiffSaver) {
					prepareToWritePlane(imageIndex, planeIndex, plane, ifd, x, y, w, h);
				}
			}

			tiffSaver.writeImage(buf, ifd, index, type, x, y, w, h,
				planeIndex == getMetadata().get(imageIndex).getPlaneCount() - 1 &&
					imageIndex == getMetadata().getImageCount() - 1);
		}

		// -- AbstractWriter Methods --

		@Override
		protected void initialize(final int imageIndex, final long planeIndex,
			final long[] planeMin, final long[] planeMax) throws FormatException,
			IOException
		{
			// Ensure that no more than one thread manipulated the initialized array
			// at one time.
			synchronized (this) {
				if (!isInitialized(imageIndex, (int) planeIndex)) {

					final RandomAccessInputStream tmp =
						new RandomAccessInputStream(getContext(), getMetadata()
							.getDatasetName());
					if (tmp.length() == 0) {
						synchronized (this) {
							// write TIFF header
							tiffSaver.writeHeader();
						}
					}
					tmp.close();
				}
			}
		}

		// -- Writer API Methods --

		@Override
		public void setDest(final RandomAccessOutputStream dest,
			final int imageIndex, final SCIFIOConfig config) throws FormatException,
			IOException
		{
			super.setDest(dest, imageIndex, config);
			synchronized (this) {
				setupTiffSaver(dest, imageIndex);
			}

			// Check if a bigTIFF setting was requested
			isBigTIFF = null;
			if (config.containsKey(BIG_TIFF_KEY)) {
				Object o = config.get(BIG_TIFF_KEY);
				if (o instanceof Boolean) {
					isBigTIFF = (Boolean)o;
				}
				else {
					String v = String.valueOf(o).toLowerCase();
					if (v.startsWith("t")) {
						isBigTIFF = true;
					}
					else if (v.startsWith("f")) {
						isBigTIFF = false;
					}
				}
			}

			// if isBigTIFF is not explicitly set and the dataset is > 2GB, write
			// bigTIFF to be safe.
			if (isBigTIFF == null && getMetadata().getDatasetSize() > 2147483648L) {
				isBigTIFF = true;
			}
		}

		@Override
		public void writePlane(final int imageIndex, final long planeIndex,
			final Plane plane, final long[] planeMin, final long[] planeMax)
			throws FormatException, IOException
		{
			IFD ifd = new IFD(log());
			if (!writeSequential()) {
				final TiffParser parser =
					new TiffParser(getContext(), getMetadata().getDatasetName());
				try {
					final long[] ifdOffsets = parser.getIFDOffsets();
					if (planeIndex < ifdOffsets.length) {
						ifd = parser.getIFD(ifdOffsets[(int) planeIndex]);
					}
				}
				finally {
					final RandomAccessInputStream tiffParserStream = parser.getStream();
					if (tiffParserStream != null) {
						tiffParserStream.close();
					}
				}
			}

			savePlane(imageIndex, planeIndex, plane, ifd, planeMin, planeMax);
		}

		@Override
		public boolean canDoStacks() {
			return true;
		}

		@Override
		public int[] getPixelTypes(final String codec) {
			if (codec != null && codec.equals(COMPRESSION_JPEG)) {
				return new int[] { FormatTools.INT8, FormatTools.UINT8,
					FormatTools.INT16, FormatTools.UINT16 };
			}
			else if (codec != null && codec.equals(COMPRESSION_J2K)) {
				return new int[] { FormatTools.INT8, FormatTools.UINT8,
					FormatTools.INT16, FormatTools.UINT16, FormatTools.INT32,
					FormatTools.UINT32, FormatTools.FLOAT };
			}
			return new int[] { FormatTools.INT8, FormatTools.UINT8,
				FormatTools.INT16, FormatTools.UINT16, FormatTools.INT32,
				FormatTools.UINT32, FormatTools.FLOAT, FormatTools.DOUBLE };
		}

		@Override
		public void close() throws IOException {
			super.close();
			if (in != null) {
				in.close();
			}
		}

		// -- Helper methods --

		/**
		 * Sets the compression code for the specified IFD.
		 * 
		 * @param ifd The IFD table to handle.
		 */
		private void formatCompression(final IFD ifd) {
			TiffCompression compressType = TiffCompression.UNCOMPRESSED;
			if (getCompression().equals(COMPRESSION_LZW)) {
				compressType = TiffCompression.LZW;
			}
			else if (getCompression().equals(COMPRESSION_J2K)) {
				compressType = TiffCompression.JPEG_2000;
			}
			else if (getCompression().equals(COMPRESSION_J2K_LOSSY)) {
				compressType = TiffCompression.JPEG_2000_LOSSY;
			}
			else if (getCompression().equals(COMPRESSION_JPEG)) {
				compressType = TiffCompression.JPEG;
			}
			final Object v = ifd.get(new Integer(IFD.COMPRESSION));
			if (v == null) ifd.put(new Integer(IFD.COMPRESSION), compressType
				.getCode());
		}

		/**
		 * Performs the preparation for work prior to the usage of the TIFF saver.
		 * This method is factored out from <code>saveBytes()</code> in an attempt
		 * to ensure thread safety.
		 */
		private long prepareToWritePlane(final int imageIndex,
			final long planeIndex, final Plane plane, final IFD ifd, final int x,
			final int y, final int w, final int h) throws IOException,
			FormatException
		{
			final byte[] buf = plane.getBytes();
			final Metadata meta = getMetadata();
			final Boolean bigEndian = !meta.get(imageIndex).isLittleEndian();
			final boolean littleEndian = !bigEndian.booleanValue();
			final boolean interleaved =
				meta.get(imageIndex).getInterleavedAxisCount() > 0;

			final int type = meta.get(imageIndex).getPixelType();
			int c = (int) meta.get(imageIndex).getAxisLength(Axes.CHANNEL);
			final int bytesPerPixel = FormatTools.getBytesPerPixel(type);

			final int blockSize = w * h * c * bytesPerPixel;
			if (blockSize > buf.length) {
				c = buf.length / (w * h * bytesPerPixel);
			}

			// FIXME no indication of why this logic is necessary. Seems over complex
			// and fragile due to the modification of the initialized array. If this
			// is truly necessary, let's find a different way to do it.
//			if (bytesPerPixel > 1 && c != 1 && c != 3) {
//				// split channels
//				checkParams = false;
//
//				if (planeIndex == 0) {
//					initialized[imageIndex] =
//						new boolean[initialized[imageIndex].length * c];
//				}
//				final long[] planeMin = new long[] { x, y };
//				final long[] planeMax = new long[] { w, h };
//				final long[] cIndex = new long[1];
//				final long[] cLength = new long[] { c };
//
//				for (int i = 0; i < c; i++) {
//					cIndex[0] = i;
//					final byte[] b =
//						ImageTools.splitChannels(buf, cIndex, cLength, bytesPerPixel,
//							false, interleaved);
//
//					final ByteArrayPlane bp = new ByteArrayPlane(getContext());
//					bp.populate(getMetadata().get(imageIndex), b, planeMin, planeMax);
//
//					savePlane(imageIndex, planeIndex * c + i, bp, (IFD) ifd.clone(),
//						planeMin, planeMax);
//				}
//				checkParams = true;
//				return -1;
//			}

			formatCompression(ifd);
			final byte[][] lut = AWTImageTools.get8BitLookupTable(getColorModel());
			if (lut != null) {
				final int[] colorMap = new int[lut.length * lut[0].length];
				for (int i = 0; i < lut.length; i++) {
					for (int j = 0; j < lut[0].length; j++) {
						colorMap[i * lut[0].length + j] = (lut[i][j] & 0xff) << 8;
					}
				}
				ifd.putIFDValue(IFD.COLOR_MAP, colorMap);
			}

			final int width = (int) meta.get(imageIndex).getAxisLength(Axes.X);
			final int height = (int) meta.get(imageIndex).getAxisLength(Axes.Y);
			ifd.put(new Integer(IFD.IMAGE_WIDTH), new Long(width));
			ifd.put(new Integer(IFD.IMAGE_LENGTH), new Long(height));

			Double physicalSizeX = meta.get(0).getAxis(Axes.X).averageScale(0, 1);
			if (physicalSizeX == null || physicalSizeX.doubleValue() == 0) {
				physicalSizeX = 0d;
			}
			else physicalSizeX = 1d / physicalSizeX;

			Double physicalSizeY = meta.get(0).getAxis(Axes.Y).averageScale(0, 1);
			if (physicalSizeY == null || physicalSizeY.doubleValue() == 0) {
				physicalSizeY = 0d;
			}
			else physicalSizeY = 1d / physicalSizeY;

			ifd.put(IFD.RESOLUTION_UNIT, 3);
			ifd.put(IFD.X_RESOLUTION, new TiffRational(
				(long) (physicalSizeX * 1000 * 10000), 1000));
			ifd.put(IFD.Y_RESOLUTION, new TiffRational(
				(long) (physicalSizeY * 1000 * 10000), 1000));

			if (!isBigTiff()) {
				isBigTIFF =
					(getStream().length() + 2 * (width * height * c * bytesPerPixel)) >= 4294967296L;
				if (isBigTiff()) {
					throw new FormatException(
						"File is too large for 32-bit TIFF but BigTIFF support was " +
						"disabled. Please enable by using setBigTiff(true) or passing a " +
						"SCIFIOConfig object with the appropriate BIG_TIFF_KEY,true pair.");
				}
			}

			// write the image
			ifd.put(new Integer(IFD.LITTLE_ENDIAN), new Boolean(littleEndian));
			if (!ifd.containsKey(IFD.REUSE)) {
				ifd.put(IFD.REUSE, getStream().length());
				getStream().seek(getStream().length());
			}
			else {
				getStream().seek((Long) ifd.get(IFD.REUSE));
			}

			ifd.putIFDValue(IFD.PLANAR_CONFIGURATION, interleaved ||
				meta.get(imageIndex).getAxisLength(Axes.CHANNEL) == 1 ? 1 : 2);

			int sampleFormat = 1;
			if (FormatTools.isSigned(type)) sampleFormat = 2;
			if (FormatTools.isFloatingPoint(type)) sampleFormat = 3;
			ifd.putIFDValue(IFD.SAMPLE_FORMAT, sampleFormat);

			long index = planeIndex;
			final int realSeries = imageIndex;
			for (int i = 0; i < realSeries; i++) {
				index += meta.get(i).getPlaneCount();
			}
			return index;
		}

		private void setupTiffSaver(final RandomAccessOutputStream stream,
			final int imageIndex) throws IOException
		{
			final Metadata meta = getMetadata();
			// FIXME this seems unnecessary.. but maybe there's a reason to
			// reconstruct the stream?
//			out = new RandomAccessOutputStream(getContext(), meta.getDatasetName());
			tiffSaver = new TiffSaver(getContext(), stream, meta.getDatasetName());

			final Boolean bigEndian = !meta.get(imageIndex).isLittleEndian();
			final boolean littleEndian = !bigEndian.booleanValue();

			tiffSaver.setWritingSequentially(writeSequential());
			tiffSaver.setLittleEndian(littleEndian);
			tiffSaver.setBigTiff(isBigTiff());
			tiffSaver.setCodecOptions(getCodecOptions());
		}

	}

	/**
	 * This class can be used for translating any io.scif.Metadata to Metadata for
	 * writing TIFF. files.
	 * <p>
	 * Note that Metadata translated from Core is only write-safe.
	 * </p>
	 * <p>
	 * If trying to read, there should already exist an originally-parsed TIFF
	 * Metadata object which can be used.
	 * </p>
	 * <p>
	 * Note also that any TIFF image written must be reparsed, as the Metadata
	 * used to write it can not be guaranteed valid.
	 * </p>
	 */
	@Plugin(type = Translator.class, priority = TIFFFormat.PRIORITY)
	public static class TIFFTranslator extends
		AbstractTranslator<io.scif.Metadata, Metadata>
	{

		// -- Translator API Methods --

		@Override
		public Class<? extends io.scif.Metadata> source() {
			return io.scif.Metadata.class;
		}

		@Override
		public Class<? extends io.scif.Metadata> dest() {
			return Metadata.class;
		}

		@Override
		public void translateImageMetadata(final List<ImageMetadata> source,
			final Metadata dest)
		{
			final IFDList ifds = new IFDList();
			dest.setIfds(ifds);

			final ImageMetadata m = source.get(0);

			long planeCount = m.getPlaneCount();
			// if Axes.CHANNEL isn't part of the planar axes, we have
			// to manually coerce it to be an RGB tiff, as that's how
			// TIFF expects additional channels
			if (m.getAxisIndex(Axes.CHANNEL) >= m.getPlanarAxisCount()) {
				planeCount /= m.getAxisLength(Axes.CHANNEL);
			}

			for (int i = 0; i < planeCount; i++)
				ifds.add(new IFD(log()));

			final IFD firstIFD = ifds.get(0);

			// Determine pixel type. Decoding logic is in IFD#getPixelType
			int sampleFormat;
			if (FormatTools.isFloatingPoint(m.getPixelType())) {
				sampleFormat = 3;
			}
			else if (FormatTools.isSigned(m.getPixelType())) {
				sampleFormat = 2;
			}
			else {
				sampleFormat = 1;
			}

			firstIFD.putIFDValue(IFD.BITS_PER_SAMPLE,
				new int[] { m.getBitsPerPixel() });
			firstIFD.putIFDValue(IFD.SAMPLE_FORMAT, sampleFormat);
			firstIFD.putIFDValue(IFD.LITTLE_ENDIAN, m.isLittleEndian());
			firstIFD.putIFDValue(IFD.IMAGE_WIDTH, m.getAxisLength(Axes.X));
			firstIFD.putIFDValue(IFD.IMAGE_LENGTH, m.getAxisLength(Axes.Y));
			firstIFD
				.putIFDValue(IFD.SAMPLES_PER_PIXEL, m.getAxisLength(Axes.CHANNEL));

			firstIFD.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION,
				PhotoInterp.BLACK_IS_ZERO);
			if (m.isMultichannel()) firstIFD.putIFDValue(
				IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.RGB);
			if (m.isIndexed() &&
				HasColorTable.class.isAssignableFrom(source.getClass()))
			{
				firstIFD.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION,
					PhotoInterp.RGB_PALETTE);

				final ColorTable table = ((HasColorTable) source).getColorTable(0, 0);
				final int[] flattenedTable =
					new int[table.getComponentCount() * table.getLength()];

				for (int i = 0; i < table.getComponentCount(); i++) {
					for (int j = 0; j < table.getLength(); j++) {
						flattenedTable[(i * table.getLength()) + j] = table.get(i, j);
					}
				}

				firstIFD.putIFDValue(IFD.COLOR_MAP, flattenedTable);
			}
		}
	}
}
