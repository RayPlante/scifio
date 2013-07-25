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

package io.scif.formats;

import io.scif.AbstractChecker;
import io.scif.AbstractFormat;
import io.scif.AbstractMetadata;
import io.scif.AbstractParser;
import io.scif.AbstractTranslator;
import io.scif.AbstractWriter;
import io.scif.ByteArrayPlane;
import io.scif.ByteArrayReader;
import io.scif.FormatException;
import io.scif.ImageMetadata;
import io.scif.Plane;
import io.scif.Translator;
import io.scif.UnsupportedCompressionException;
import io.scif.codec.CodecOptions;
import io.scif.codec.CompressionType;
import io.scif.codec.JPEGCodec;
import io.scif.codec.MJPBCodec;
import io.scif.codec.MJPBCodecOptions;
import io.scif.codec.QTRLECodec;
import io.scif.codec.RPZACodec;
import io.scif.codec.ZlibCodec;
import io.scif.gui.LegacyQTTools;
import io.scif.io.Location;
import io.scif.io.RandomAccessInputStream;
import io.scif.io.RandomAccessOutputStream;
import io.scif.util.FormatTools;
import io.scif.util.SCIFIOMetadataTools;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import net.imglib2.meta.Axes;

import org.scijava.Priority;
import org.scijava.plugin.Attr;
import org.scijava.plugin.Plugin;

/**
 * NativeQTReader is the file format reader for QuickTime movie files.
 * It does not require any external libraries to be installed.
 *
 * Video codecs currently supported: raw, rle, jpeg, mjpb, rpza.
 * Additional video codecs will be added as time permits.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/in/NativeQTReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/in/NativeQTReader.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author Melissa Linkert melissa at glencoesoftware.com
 */
@Plugin(type = NativeQTFormat.class, priority = Priority.NORMAL_PRIORITY)
public class NativeQTFormat extends AbstractFormat {

  // -- Constants --

  /** List of identifiers for each container atom. */
  private static final String[] CONTAINER_TYPES = {
    "moov", "trak", "udta", "tref", "imap", "mdia", "minf", "stbl", "edts",
    "mdra", "rmra", "imag", "vnrp", "dinf"
  };

  // -- Format API Methods --

  /*
   * @see io.scif.Format#getFormatName()
   */
  public String getFormatName() {
    return "QuickTime";
  }

  /*
   * @see io.scif.Format#getSuffixes()
   */
  public String[] getSuffixes() {
    return new String[]{"mov"};
  }

  // -- Nested classes --

  /**
   * @author Mark Hiner hinerm at gmail.com
   *
   */
  public static class Metadata extends AbstractMetadata {
    // -- Constants --

    public static final String CNAME = "io.scif.formats.NativeQTFormat$Metadata";

    // -- Fields --

    /** Offset to start of pixel data. */
    private long pixelOffset;

    /** Total number of bytes of pixel data. */
    private long pixelBytes;

    /** Pixel depth. */
    private int bitsPerPixel;

    /** Raw plane size, in bytes. */
    private int rawSize;

    /** Offsets to each plane's pixel data. */
    private Vector<Integer> offsets;

    /** Pixel data for the previous image plane. */
    private byte[] prevPixels;

    /** Previous plane number. */
    private int prevPlane;

    /** Flag indicating whether we can safely use prevPixels. */
    private boolean canUsePrevious;

    /** Video codec used by this movie. */
    private String codec;

    /** Some movies use two video codecs -- this is the second codec. */
    private String altCodec;

    /** Number of frames that use the alternate codec. */
    private int altPlanes;

    /** Amount to subtract from each offset. */
    private int scale;

    /** Number of bytes in each plane. */
    private Vector<Integer> chunkSizes;

    /** Set to true if the scanlines in a plane are interlaced (mjpb only). */
    private boolean interlaced;

    /** Flag indicating whether the resource and data fork are separated. */
    private boolean spork;

    private boolean flip;

    // -- NativeQTMetadata getters and setters --

    public long getPixelOffset() {
      return pixelOffset;
    }

    public void setPixelOffset(long pixelOffset) {
      this.pixelOffset = pixelOffset;
    }

    public long getPixelBytes() {
      return pixelBytes;
    }

    public void setPixelBytes(long pixelBytes) {
      this.pixelBytes = pixelBytes;
    }

    public int getBitsPerPixel() {
      return bitsPerPixel;
    }

    public void setBitsPerPixel(int bitsPerPixel) {
      this.bitsPerPixel = bitsPerPixel;
    }

    public int getRawSize() {
      return rawSize;
    }

    public void setRawSize(int rawSize) {
      this.rawSize = rawSize;
    }

    public Vector<Integer> getOffsets() {
      return offsets;
    }

    public void setOffsets(Vector<Integer> offsets) {
      this.offsets = offsets;
    }

    public byte[] getPrevPixels() {
      return prevPixels;
    }

    public void setPrevPixels(byte[] prevPixels) {
      this.prevPixels = prevPixels;
    }

    public int getPrevPlane() {
      return prevPlane;
    }

    public void setPrevPlane(int prevPlane) {
      this.prevPlane = prevPlane;
    }

    public boolean isCanUsePrevious() {
      return canUsePrevious;
    }

    public void setCanUsePrevious(boolean canUsePrevious) {
      this.canUsePrevious = canUsePrevious;
    }

    public String getCodec() {
      return codec;
    }

    public void setCodec(String codec) {
      this.codec = codec;
    }

    public String getAltCodec() {
      return altCodec;
    }

    public void setAltCodec(String altCodec) {
      this.altCodec = altCodec;
    }

    public int getAltPlanes() {
      return altPlanes;
    }

    public void setAltPlanes(int altPlanes) {
      this.altPlanes = altPlanes;
    }

    public int getScale() {
      return scale;
    }

    public void setScale(int scale) {
      this.scale = scale;
    }

    public Vector<Integer> getChunkSizes() {
      return chunkSizes;
    }

    public void setChunkSizes(Vector<Integer> chunkSizes) {
      this.chunkSizes = chunkSizes;
    }

    public boolean isInterlaced() {
      return interlaced;
    }

    public void setInterlaced(boolean interlaced) {
      this.interlaced = interlaced;
    }

    public boolean isSpork() {
      return spork;
    }

    public void setSpork(boolean spork) {
      this.spork = spork;
    }

    public boolean isFlip() {
      return flip;
    }

    public void setFlip(boolean flip) {
      this.flip = flip;
    }

    // -- Metadata API Methods --

    /*
     * @see io.scif.Metadata#populateImageMetadata()
     */
    public void populateImageMetadata() {
      ImageMetadata iMeta = get(0);

      iMeta.setRGB(getBitsPerPixel() < 40);
      iMeta.setAxisLength(Axes.CHANNEL, iMeta.isRGB() ? 3 : 1);
      iMeta.setInterleaved(iMeta.isRGB());
      iMeta.setAxisLength(Axes.Z, 1);
      iMeta.setAxisLength(Axes.TIME, iMeta.getPlaneCount());

      int bytes = (getBitsPerPixel() / 8) % 4;
      iMeta.setPixelType(bytes == 2 ? FormatTools.UINT16 : FormatTools.UINT8);
      iMeta.setBitsPerPixel(FormatTools.getBitsPerPixel(iMeta.getPixelType()));

      iMeta.setLittleEndian(false);
      iMeta.setMetadataComplete(true);
      iMeta.setIndexed(false);
      iMeta.setFalseColor(false);
    }

    @Override
    public void close(boolean fileOnly) throws IOException {
      super.close(fileOnly);
      if (!fileOnly) {
        offsets = null;
        prevPixels = null;
        codec = altCodec = null;
        pixelOffset = pixelBytes = bitsPerPixel = rawSize = 0;
        prevPlane = altPlanes = 0;
        canUsePrevious = false;
        scale = 0;
        chunkSizes = null;
        interlaced = spork = flip = false;
      }
    }
  }

  /**
   * @author Mark Hiner hinerm at gmail.com
   *
   */
  public static class Checker extends AbstractChecker {

    // -- Constructor --

    public Checker() {
      suffixNecessary = false;

    }

    // -- Checker API Methods --

    @Override
    public boolean isFormat(RandomAccessInputStream stream) throws IOException {
      final int blockLen = 64;
      if (!FormatTools.validStream(stream, blockLen, false)) return false;
      // use a crappy hack for now
      String s = stream.readString(blockLen);
      for (int i=0; i<CONTAINER_TYPES.length; i++) {
        if (s.indexOf(CONTAINER_TYPES[i]) >= 0 &&
          !CONTAINER_TYPES[i].equals("imag"))
        {
          return true;
        }
      }
      return s.indexOf("wide") >= 0 ||
        s.indexOf("mdat") >= 0 || s.indexOf("ftypqt") >= 0;
    }
  }

  /**
   * @author Mark Hiner hinerm at gmail.com
   *
   */
  public static class Parser extends AbstractParser<Metadata> {

    // -- Parser API Methods --

    @Override
    protected void typedParse(RandomAccessInputStream stream, Metadata meta)
        throws IOException, FormatException {

      meta.setSpork(true);
      Vector<Integer> offsets = new Vector<Integer>();
      Vector<Integer> chunkSizes = new Vector<Integer>();
      RandomAccessInputStream cachedStream = null;

      meta.setOffsets(offsets);
      meta.setChunkSizes(chunkSizes);
      meta.createImageMetadata(1);
      LOGGER.info("Parsing tags");

      NativeQTUtils.parse(stream, meta, 0, 0, stream.length());

      ImageMetadata iMeta = meta.get(0);

      iMeta.setPlaneCount(offsets.size());

      if (chunkSizes.size() < iMeta.getPlaneCount() && chunkSizes.size() > 0) {
        iMeta.setPlaneCount(chunkSizes.size());
      }

      LOGGER.info("Populating metadata");
      String id = stream.getFileName();

      // this handles the case where the data and resource forks have been
      // separated
      if (meta.isSpork()) {
        // first we want to check if there is a resource fork present
        // the resource fork will generally have the same name as the data fork,
        // but will have either the prefix "._" or the suffix ".qtr"
        // (or <filename>/rsrc on a Mac)

        String base = null;
        if (id.indexOf(".") != -1) {
          base = id.substring(0, id.lastIndexOf("."));
        }
        else base = id;

        Location f = new Location(getContext(), base + ".qtr");
        LOGGER.debug("Searching for research fork:");
        if (f.exists()) {
          LOGGER.debug("\t Found: {}", f);
          if (in != null) in.close();
          in = new RandomAccessInputStream(getContext(), f.getAbsolutePath());

          NativeQTUtils.stripHeader(stream);
          NativeQTUtils.parse(stream, meta, 0, 0, in.length());
          meta.get(0).setPlaneCount(offsets.size());
        }
        else {
          LOGGER.debug("\tAbsent: {}", f);
          f = new Location(getContext(), id.substring(0,
            id.lastIndexOf(File.separator) + 1) + "._" +
            id.substring(base.lastIndexOf(File.separator) + 1));
          if (f.exists()) {
            LOGGER.debug("\t Found: {}", f);
            cachedStream = stream;
            stream = new RandomAccessInputStream(getContext(), f.getAbsolutePath());
            NativeQTUtils.stripHeader(stream);
            NativeQTUtils.parse(stream, meta, 0, stream.getFilePointer(), stream.length());
            meta.get(0).setPlaneCount(offsets.size());
          }
          else {
            LOGGER.debug("\tAbsent: {}", f);
            f = new Location(getContext(), id + "/..namedfork/rsrc");
            if (f.exists()) {
              LOGGER.debug("\t Found: {}", f);
              cachedStream = stream;
              stream = new RandomAccessInputStream(getContext(), f.getAbsolutePath());
              NativeQTUtils.stripHeader(stream);
              NativeQTUtils.parse(stream, meta, 0, stream.getFilePointer(), stream.length());
              meta.get(0).setPlaneCount(offsets.size());
            }
            else {
              LOGGER.debug("\tAbsent: {}", f);
              throw new FormatException("QuickTime resource fork not found. " +
                " To avoid this issue, please flatten your QuickTime movies " +
                "before importing with Bio-Formats.");
            }
          }
        }

        // reset the stream, otherwise openBytes will try to read pixels
        // from the resource fork
        if (cachedStream != null) stream.close();
      }
    }
  }

  /**
   * @author Mark Hiner hinerm at gmail.com
   *
   */
  public static class Reader extends ByteArrayReader<Metadata> {

    // -- Constructor --

    public Reader() {
      domains = new String[] {FormatTools.GRAPHICS_DOMAIN};
    }

    // -- Reader API Methods --

    public ByteArrayPlane openPlane(int imageIndex, int planeIndex,
      ByteArrayPlane plane, int x, int y, int w, int h)
      throws FormatException, IOException
    {

      Metadata meta = getMetadata();
      byte[] buf = plane.getData();
      FormatTools.checkPlaneParameters(this, imageIndex, planeIndex, buf.length, x, y, w, h);

      String code = meta.getCodec();
      if (planeIndex >= meta.getPlaneCount(imageIndex) - meta.getAltPlanes()) code = meta.altCodec;

      int offset = meta.getOffsets().get(planeIndex).intValue();
      int nextOffset = (int) meta.getPixelBytes();

      meta.setScale(meta.getOffsets().get(0).intValue());
      offset -= meta.getScale();

      if (planeIndex < meta.getOffsets().size() - 1) {
        nextOffset = meta.getOffsets().get(planeIndex + 1).intValue() - meta.getScale();
      }

      if ((nextOffset - offset) < 0) {
        int temp = offset;
        offset = nextOffset;
        nextOffset = temp;
      }

      byte[] pixs = new byte[nextOffset - offset];

      getStream().seek(meta.getPixelOffset() + offset);
      getStream().read(pixs);

      meta.setCanUsePrevious((meta.getPrevPixels() != null) && (meta.getPrevPlane() == planeIndex - 1) &&
        !code.equals(meta.getAltCodec()));

      byte[] t = meta.getPrevPlane() == planeIndex && meta.getPrevPixels() != null && !code.equals(meta.getAltCodec()) ?
        meta.getPrevPixels() : NativeQTUtils.uncompress(pixs, code, meta);
      if (code.equals("rpza")) {
        for (int i=0; i<t.length; i++) {
          t[i] = (byte) (255 - t[i]);
        }
        meta.setPrevPlane(planeIndex);
        return plane;
      }

      // on rare occassions, we need to trim the data
      if (meta.isCanUsePrevious() && (meta.getPrevPixels().length < t.length)) {
        byte[] temp = t;
        t = new byte[meta.getPrevPixels().length];
        System.arraycopy(temp, 0, t, 0, t.length);
      }

      meta.setPrevPixels(t);
      meta.setPrevPlane(planeIndex);

      // determine whether we need to strip out any padding bytes

      int bytes = meta.getBitsPerPixel() < 40 ? meta.getBitsPerPixel() / 8 : (meta.getBitsPerPixel() - 32) / 8;
      int pad = (4 - (meta.getAxisLength(imageIndex, Axes.X) % 4)) % 4;
      if (meta.getCodec().equals("mjpb")) pad = 0;

      int expectedSize = FormatTools.getPlaneSize(this, imageIndex);

      if (meta.getPrevPixels().length == expectedSize ||
        (meta.getBitsPerPixel() == 32 && (3 * (meta.getPrevPixels().length / 4)) == expectedSize))
      {
        pad = 0;
      }

      if (pad > 0) {
        t = new byte[meta.getPrevPixels().length - meta.getAxisLength(imageIndex, Axes.Y)*pad];

        for (int row=0; row<meta.getAxisLength(imageIndex, Axes.Y); row++) {
          System.arraycopy(meta.getPrevPixels(), row * (bytes * meta.getAxisLength(imageIndex, Axes.X) + pad), t,
            row * meta.getAxisLength(imageIndex, Axes.X) * bytes, meta.getAxisLength(imageIndex, Axes.X) * bytes);
        }
      }

      int bpp = FormatTools.getBytesPerPixel(meta.getPixelType(imageIndex));
      int srcRowLen = meta.getAxisLength(imageIndex, Axes.X) * bpp * meta.getAxisLength(imageIndex, Axes.CHANNEL);
      int destRowLen = w * bpp * meta.getAxisLength(imageIndex, Axes.CHANNEL);
      for (int row=0; row<h; row++) {
        if (meta.getBitsPerPixel() == 32) {
          for (int col=0; col<w; col++) {
            int src = (row + y) * meta.getAxisLength(imageIndex, Axes.X) * bpp * 4 + (x + col) * bpp * 4 + 1;
            int dst = row * destRowLen + col * bpp * 3;
            if (src + 3 <= t.length && dst + 3 <= buf.length) {
              System.arraycopy(t, src, buf, dst, 3);
            }
          }
        }
        else {
          System.arraycopy(t, row*srcRowLen + x*bpp*meta.getAxisLength(imageIndex, Axes.CHANNEL), buf,
            row*destRowLen, destRowLen);
        }
      }

      if ((meta.getBitsPerPixel() == 40 || meta.getBitsPerPixel() == 8) && !code.equals("mjpb")) {
        // invert the pixels
        for (int i=0; i<buf.length; i++) {
          buf[i] = (byte) (255 - buf[i]);
        }
      }
      return plane;
    }
  }

  /**
   * @author Mark Hiner hinerm at gmail.com
   *
   */
  public static class Writer extends AbstractWriter<Metadata> {

    // -- Constants --

    // NB: Writing to Motion JPEG-B with QTJava seems to be broken.
    /** Value indicating Motion JPEG-B codec. */
    public static final int CODEC_MOTION_JPEG_B = 1835692130;

    /** Value indicating Cinepak codec. */
    public static final int CODEC_CINEPAK = 1668704612;

    /** Value indicating Animation codec. */
    public static final int CODEC_ANIMATION = 1919706400;

    /** Value indicating H.263 codec. */
    public static final int CODEC_H_263 = 1748121139;

    /** Value indicating Sorenson codec. */
    public static final int CODEC_SORENSON = 1398165809;

    /** Value indicating Sorenson 3 codec. */
    public static final int CODEC_SORENSON_3 = 0x53565133;

    /** Value indicating MPEG-4 codec. */
    public static final int CODEC_MPEG_4 = 0x6d703476;

    /** Value indicating Raw codec. */
    public static final int CODEC_RAW = 0;

    /** Value indicating Low quality. */
    public static final int QUALITY_LOW = 256;

    /** Value indicating Normal quality. */
    public static final int QUALITY_NORMAL = 512;

    /** Value indicating High quality. */
    public static final int QUALITY_HIGH = 768;

    /** Value indicating Maximum quality. */
    public static final int QUALITY_MAXIMUM = 1023;

    /** Seek to this offset to update the total number of pixel bytes. */
    private static final long BYTE_COUNT_OFFSET = 8;

    // -- Fields --

    /** The codec to use. */
    protected int codec = CODEC_RAW;

    /** The quality to use. */
    protected int quality = QUALITY_NORMAL;

    /** Total number of pixel bytes. */
    protected int numBytes;

    /** Vector of plane offsets. */
    protected Vector<Integer> offsets;

    /** Time the file was created. */
    protected int created;

    /** Number of padding bytes in each row. */
    protected int pad;

    /** Whether we need the legacy writer. */
    protected boolean needLegacy = false;

    private LegacyQTFormat.Writer legacy;

    private int numWritten = 0;

    // -- Constructor --

    public Writer() {
      LegacyQTTools tools = new LegacyQTTools();
      if (tools.canDoQT()) {
        compressionTypes = new String[] {
            CompressionType.UNCOMPRESSED.getCompression(),
          // NB: Writing to Motion JPEG-B with QTJava seems to be broken.
          /*"Motion JPEG-B",*/
            CompressionType.CINEPAK.getCompression(),
            CompressionType.ANIMATION.getCompression(),
            CompressionType.H_263.getCompression(),
            CompressionType.SORENSON.getCompression(),
            CompressionType.SORENSON_3.getCompression(),
            CompressionType.MPEG_4.getCompression()
        };
      }
      else compressionTypes = new String[] {
          CompressionType.UNCOMPRESSED.getCompression()};
    }

    // -- QTWriter API methods --

    /**
     * Sets the encoded movie's codec.
     * @param codec Codec value:<ul>
     *   <li>QTWriterCODEC_CINEPAK</li>
     *   <li>QTWriter.CODEC_ANIMATION</li>
     *   <li>QTWriter.CODEC_H_263</li>
     *   <li>QTWriter.CODEC_SORENSON</li>
     *   <li>QTWriter.CODEC_SORENSON_3</li>
     *   <li>QTWriter.CODEC_MPEG_4</li>
     *   <li>QTWriter.CODEC_RAW</li>
     * </ul>
     */
    public void setCodec(int codec) { this.codec = codec; }

    /**
     * Sets the quality of the encoded movie.
     * @param quality Quality value:<ul>
     *   <li>QTWriter.QUALITY_LOW</li>
     *   <li>QTWriter.QUALITY_MEDIUM</li>
     *   <li>QTWriter.QUALITY_HIGH</li>
     *   <li>QTWriter.QUALITY_MAXIMUM</li>
     * </ul>
     */
    public void setQuality(int quality) { this.quality = quality; }

    // -- Writer API methods --

    public void savePlane(int imageIndex, int planeIndex, Plane plane, int x,
      int y, int w, int h) throws FormatException, IOException
    {
      byte[] buf = plane.getBytes();
      checkParams(imageIndex, planeIndex, buf, x, y, w, h);
      if (needLegacy) {
        legacy.savePlane(imageIndex, planeIndex, plane, x, y, w, h);
        return;
      }

      Metadata meta = getMetadata();

      // get the width and height of the image
      int width = meta.getAxisLength(imageIndex, Axes.X);
      int height = meta.getAxisLength(imageIndex, Axes.Y);

      // need to check if the width is a multiple of 8
      // if it is, great; if not, we need to pad each scanline with enough
      // bytes to make the width a multiple of 8

      int nChannels = meta.getRGBChannelCount(imageIndex);
      int planeSize = width * height * nChannels;

      if (!initialized[imageIndex][planeIndex]) {
        initialized[imageIndex][planeIndex] = true;
        setCodec();
        if (codec != CODEC_RAW) {
          needLegacy = true;
          legacy.setDest(out);
          legacy.savePlane(planeIndex, imageIndex, plane, x, y, w, h);
          return;
        }

        // update the number of pixel bytes written
        int planeOffset = numBytes;
        numBytes += (planeSize + pad * height);
        out.seek(BYTE_COUNT_OFFSET);
        out.writeInt(numBytes + 8);

        out.seek(offsets.get(planeIndex));

        if (!isFullPlane(imageIndex, x, y, w, h)) {
          out.skipBytes(planeSize + pad * height);
        }
      }

      out.seek(offsets.get(planeIndex) + y * (nChannels * width + pad));

      // invert each pixel
      // this will makes the colors look right in other readers (e.g. xine),
      // but needs to be reversed in QTReader

      byte[] tmp = new byte[buf.length];
      if (nChannels == 1 && !needLegacy) {
        for (int i=0; i<buf.length; i++) {
          tmp[i] = (byte) (255 - buf[i]);
        }
      }
      else System.arraycopy(buf, 0, tmp, 0, buf.length);

      if (!interleaved) {
        // need to write interleaved data
        byte[] tmp2 = new byte[tmp.length];
        System.arraycopy(tmp, 0, tmp2, 0, tmp.length);
        for (int i=0; i<tmp.length; i++) {
          int c = i / (w * h);
          int index = i % (w * h);
          tmp[index * nChannels + c] = tmp2[i];
        }
      }

      int rowLen = tmp.length / h;
      for (int row=0; row<h; row++) {
        out.skipBytes(nChannels * x);
        out.write(tmp, row * rowLen, rowLen);
        for (int i=0; i<pad; i++) {
          out.writeByte(0);
        }
        if (row < h - 1) {
          out.skipBytes(nChannels * (width - w - x));
        }
      }
      numWritten++;
    }

    @Override
    public boolean canDoStacks() { return true; }

    @Override
    public int[] getPixelTypes(String codec) {
      return new int[] {FormatTools.UINT8};
    }

    @Override
    public void close() throws IOException {
      if (out != null) writeFooter();
      super.close();
      numBytes = 0;
      created = 0;
      offsets = null;
      pad = 0;
      numWritten = 0;
    }

    /**
     * Sets the source that will be written to during {@link #savePlane} calls.
     * 
     * @param stream The image source to write to.
     * @param imageIndex The index of the source to write to (default = 0)
     */
    public void setDest(RandomAccessOutputStream stream, int imageIndex)
      throws FormatException, IOException
    {
      super.setDest(stream, imageIndex);
      Metadata meta = getMetadata();
      SCIFIOMetadataTools.verifyMinimumPopulated(meta, stream);

      int width = meta.getAxisLength(imageIndex, Axes.X);
      int height = meta.getAxisLength(imageIndex, Axes.Y);
      int nChannels = meta.getRGBChannelCount(imageIndex);
      int planeSize = width * height * nChannels;

      pad = nChannels > 1 ? 0 : (4 - (width % 4)) % 4;

      if (legacy == null) {
        LegacyQTFormat legacyFormat = scifio().format().getFormatFromClass(LegacyQTFormat.class);
        legacy = (LegacyQTFormat.Writer)legacyFormat.createWriter();
        io.scif.Metadata legacyMeta = legacyFormat.createMetadata();
        scifio().translator().translate(meta, legacyMeta, false);

        legacy.setMetadata(legacyMeta);

        legacy.setCodec(codec);
      }
      offsets = new Vector<Integer>();
      created = (int) System.currentTimeMillis();
      numBytes = 0;

      if (out.length() == 0) {
        // -- write the first header --

        writeAtom(8, "wide");
        writeAtom(numBytes + 8, "mdat");
      }
      else {
        out.seek(BYTE_COUNT_OFFSET);

        RandomAccessInputStream in = new RandomAccessInputStream(getContext(), meta.getDatasetName());
        in.seek(BYTE_COUNT_OFFSET);
        numBytes = in.readInt() - 8;
        in.close();
      }

      for (int i=0; i<meta.get(0).getPlaneCount(); i++) {
        offsets.add(16 + i * (planeSize + pad * height));
      }
    }
    // -- Helper methods --

    private void setCodec() {
      if (compression == null) return;
      if (compression.equals("Uncompressed")) codec = CODEC_RAW;
      // NB: Writing to Motion JPEG-B with QTJava seems to be broken.
      else if (compression.equals("Motion JPEG-B")) codec = CODEC_MOTION_JPEG_B;
      else if (compression.equals("Cinepak")) codec = CODEC_CINEPAK;
      else if (compression.equals("Animation")) codec = CODEC_ANIMATION;
      else if (compression.equals("H.263")) codec = CODEC_H_263;
      else if (compression.equals("Sorenson")) codec = CODEC_SORENSON;
      else if (compression.equals("Sorenson 3")) codec = CODEC_SORENSON_3;
      else if (compression.equals("MPEG 4")) codec = CODEC_MPEG_4;
    }

    private void writeFooter() throws IOException {
      out.seek(out.length());
      Metadata meta = getMetadata();
      int width = meta.getAxisLength(0, Axes.X);
      int height = meta.getAxisLength(0, Axes.Y);
      int nChannels = meta.getRGBChannelCount(0);

      int timeScale = 1000;
      int duration = (int) (numWritten * ((double) timeScale / fps));
      int bitsPerPixel = (nChannels > 1) ? 24 : 40;
      int channels = (bitsPerPixel >= 40) ? 1 : 3;

      // -- write moov atom --

      int atomLength = 685 + 8*numWritten;
      writeAtom(atomLength, "moov");

      // -- write mvhd atom --

      writeAtom(108, "mvhd");
      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeInt(created); // creation time
      out.writeInt((int) System.currentTimeMillis());
      out.writeInt(timeScale); // time scale
      out.writeInt(duration); // duration
      out.write(new byte[] {0, 1, 0, 0});  // preferred rate & volume
      out.write(new byte[] {0, -1, 0, 0, 0, 0, 0, 0, 0, 0}); // reserved

      writeRotationMatrix();

      out.writeShort(0); // not sure what this is
      out.writeInt(0); // preview duration
      out.writeInt(0); // preview time
      out.writeInt(0); // poster time
      out.writeInt(0); // selection time
      out.writeInt(0); // selection duration
      out.writeInt(0); // current time
      out.writeInt(2); // next track's id

      // -- write trak atom --

      atomLength -= 116;
      writeAtom(atomLength, "trak");

      // -- write tkhd atom --

      writeAtom(92, "tkhd");
      out.writeShort(0); // version
      out.writeShort(15); // flags

      out.writeInt(created); // creation time
      out.writeInt((int) System.currentTimeMillis());
      out.writeInt(1); // track id
      out.writeInt(0); // reserved

      out.writeInt(duration); // duration
      out.writeInt(0); // reserved
      out.writeInt(0); // reserved
      out.writeShort(0); // reserved
      out.writeInt(0); // unknown

      writeRotationMatrix();

      out.writeInt(width); // image width
      out.writeInt(height); // image height
      out.writeShort(0); // reserved

      // -- write edts atom --

      writeAtom(36, "edts");

      // -- write elst atom --

      writeAtom(28, "elst");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeInt(1); // number of entries in the table
      out.writeInt(duration); // duration
      out.writeShort(0); // time
      out.writeInt(1); // rate
      out.writeShort(0); // unknown

      // -- write mdia atom --

      atomLength -= 136;
      writeAtom(atomLength, "mdia");

      // -- write mdhd atom --

      writeAtom(32, "mdhd");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeInt(created); // creation time
      out.writeInt((int) System.currentTimeMillis());
      out.writeInt(timeScale); // time scale
      out.writeInt(duration); // duration
      out.writeShort(0); // language
      out.writeShort(0); // quality

      // -- write hdlr atom --

      writeAtom(58, "hdlr");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeBytes("mhlr");
      out.writeBytes("vide");
      out.writeBytes("appl");
      out.write(new byte[] {16, 0, 0, 0, 0, 1, 1, 11, 25});
      out.writeBytes("Apple Video Media Handler");

      // -- write minf atom --

      atomLength -= 98;
      writeAtom(atomLength, "minf");

      // -- write vmhd atom --

      writeAtom(20, "vmhd");

      out.writeShort(0); // version
      out.writeShort(1); // flags
      out.writeShort(64); // graphics mode
      out.writeShort(32768);  // opcolor 1
      out.writeShort(32768);  // opcolor 2
      out.writeShort(32768);  // opcolor 3

      // -- write hdlr atom --

      writeAtom(57, "hdlr");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeBytes("dhlr");
      out.writeBytes("alis");
      out.writeBytes("appl");
      out.write(new byte[] {16, 0, 0, 1, 0, 1, 1, 31, 24});
      out.writeBytes("Apple Alias Data Handler");

      // -- write dinf atom --

      writeAtom(36, "dinf");

      // -- write dref atom --

      writeAtom(28, "dref");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeShort(0); // version 2
      out.writeShort(1); // flags 2
      out.write(new byte[] {0, 0, 0, 12});
      out.writeBytes("alis");
      out.writeShort(0); // version 3
      out.writeShort(1); // flags 3

      // -- write stbl atom --

      atomLength -= 121;
      writeAtom(atomLength, "stbl");

      // -- write stsd atom --

      writeAtom(118, "stsd");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeInt(1); // number of entries in the table
      out.write(new byte[] {0, 0, 0, 102});
      out.writeBytes("raw "); // codec
      out.write(new byte[] {0, 0, 0, 0, 0, 0});  // reserved
      out.writeShort(1); // data reference
      out.writeShort(1); // version
      out.writeShort(1); // revision
      out.writeBytes("appl");
      out.writeInt(0); // temporal quality
      out.writeInt(768); // spatial quality
      out.writeShort(width); // image width
      out.writeShort(height); // image height
      byte[] dpi = new byte[] {0, 72, 0, 0};
      out.write(dpi); // horizontal dpi
      out.write(dpi); // vertical dpi
      out.writeInt(0); // data size
      out.writeShort(1); // frames per sample
      out.writeShort(12); // length of compressor name
      out.writeBytes("Uncompressed"); // compressor name
      out.writeInt(bitsPerPixel); // unknown
      out.writeInt(bitsPerPixel); // unknown
      out.writeInt(bitsPerPixel); // unknown
      out.writeInt(bitsPerPixel); // unknown
      out.writeInt(bitsPerPixel); // unknown
      out.writeShort(bitsPerPixel); // bits per pixel
      out.writeInt(65535); // ctab ID
      out.write(new byte[] {12, 103, 97, 108}); // gamma
      out.write(new byte[] {97, 1, -52, -52, 0, 0, 0, 0}); // unknown

      // -- write stts atom --

      writeAtom(24, "stts");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeInt(1); // number of entries in the table
      out.writeInt(numWritten); // number of planes
      out.writeInt((int) ((double) timeScale / fps)); // milliseconds per frame

      // -- write stsc atom --

      writeAtom(28, "stsc");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeInt(1); // number of entries in the table
      out.writeInt(1); // chunk
      out.writeInt(1); // samples
      out.writeInt(1); // id

      // -- write stsz atom --

      writeAtom(20 + 4 * numWritten, "stsz");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeInt(0); // sample size
      out.writeInt(numWritten); // number of planes
      for (int i=0; i<numWritten; i++) {
        // sample size
        out.writeInt(channels * height * (width + pad));
      }

      // -- write stco atom --

      writeAtom(16 + 4 * numWritten, "stco");

      out.writeShort(0); // version
      out.writeShort(0); // flags
      out.writeInt(numWritten); // number of planes
      for (int i=0; i<numWritten; i++) {
        // write the plane offset
        out.writeInt(offsets.get(i));
      }
    }

    /** Write the 3x3 matrix that describes how to rotate the image. */
    private void writeRotationMatrix() throws IOException {
      out.writeInt(1);
      out.writeInt(0);
      out.writeInt(0);
      out.writeInt(0);
      out.writeInt(1);
      out.writeInt(0);
      out.writeInt(0);
      out.writeInt(0);
      out.writeInt(16384);
    }

    /** Write the atom length and type. */
    private void writeAtom(int length, String type) throws IOException {
      out.writeInt(length);
      out.writeBytes(type);
    }
  }

  /**
   * @author Mark Hiner hinerm at gmail.com
   *
   */
  @Plugin(type = Translator.class, attrs =
    {@Attr(name = NativeQTTranslator.SOURCE, value = io.scif.Metadata.CNAME),
     @Attr(name = NativeQTTranslator.DEST, value = Metadata.CNAME)},
    priority = Priority.LOW_PRIORITY)
  public static class NativeQTTranslator
    extends AbstractTranslator<io.scif.Metadata, Metadata>
  {
    // -- Translator API Methods --

    public void typedTranslate(io.scif.Metadata source, Metadata dest) {
      dest.createImageMetadata(1);
      dest.get(0).setPlaneCount(source.getPlaneCount(0));
      dest.setAxisLength(0, Axes.X, source.getAxisLength(0, Axes.X));
      dest.setAxisLength(0, Axes.Y, source.getAxisLength(0, Axes.Y));

      // *** HACK *** the Metadata bitsPerPixel field doesn't really matter if we're translating to this format.
      // But it is used to determine RGB status.
      int bpp = FormatTools.getBitsPerPixel(source.getPixelType(0)) == 8 ? 8 : 16;

      dest.setBitsPerPixel(source.isRGB(0) ? bpp : (bpp * 5));
    }
  }

  // -- Helper class --

  private static class NativeQTUtils {

    /** Parse all of the atoms in the file. */
    private static void parse(RandomAccessInputStream stream, Metadata meta, int depth, long offset, long length)
      throws FormatException, IOException
    {
      while (offset < length) {
        stream.seek(offset);

        // first 4 bytes are the atom size
        long atomSize = stream.readInt() & 0xffffffffL;

        // read the atom type
        String atomType = stream.readString(4);

        // if atomSize is 1, then there is an 8 byte extended size
        if (atomSize == 1) {
          atomSize = stream.readLong();
        }

        if (atomSize < 0) {
          LOGGER.warn("QTReader: invalid atom size: {}", atomSize);
        }

        LOGGER.debug("Seeking to {}; atomType={}; atomSize={}",
          new Object[] {offset, atomType, atomSize});

        // if this is a container atom, parse the children
        if (isContainer(atomType)) {
          parse(stream, meta, depth++, stream.getFilePointer(), offset + atomSize);
        }
        else {
          if (atomSize == 0) atomSize = stream.length();
          long oldpos = stream.getFilePointer();

          if (atomType.equals("mdat")) {
            // we've found the pixel data
            meta.setPixelOffset(stream.getFilePointer());
            meta.setPixelBytes(atomSize);

            if (meta.getPixelBytes() > (stream.length() - meta.getPixelOffset())) {
              meta.setPixelBytes(stream.length() - meta.getPixelOffset());
            }
          }
          else if (atomType.equals("tkhd")) {
            // we've found the dimensions

            stream.skipBytes(38);
            int[][] matrix = new int[3][3];

            for (int i=0; i<matrix.length; i++) {
              for (int j=0; j<matrix[0].length; j++) {
                matrix[i][j] = stream.readInt();
              }
            }

            // The contents of the matrix we just read determine whether or not
            // we should flip the width and height.  We can check the first two
            // rows of the matrix - they should correspond to the first two rows
            // of an identity matrix.

            // TODO : adapt to use the value of flip
            meta.setFlip(matrix[0][0] == 0 && matrix[1][0] != 0);

            if (meta.getAxisIndex(0, Axes.X) == -1) meta.setAxisLength(0, Axes.X, stream.readInt());
            if (meta.getAxisIndex(0, Axes.Y) == -1) meta.setAxisLength(0, Axes.Y, stream.readInt());
          }
          else if (atomType.equals("cmov")) {
            stream.skipBytes(8);
            if ("zlib".equals(stream.readString(4))) {
              atomSize = stream.readInt();
              stream.skipBytes(4);
              int uncompressedSize = stream.readInt();

              byte[] b = new byte[(int) (atomSize - 12)];
              stream.read(b);

              byte[] output = new ZlibCodec().decompress(b, null);

              RandomAccessInputStream oldIn = stream;
              stream = new RandomAccessInputStream(meta.getContext(), output);
              parse(stream, meta, 0, 0, output.length);
              stream.close();
              stream = oldIn;
            }
            else {
              throw new UnsupportedCompressionException(
                "Compressed header not supported.");
            }
          }
          else if (atomType.equals("stco")) {
            // we've found the plane offsets

            if (meta.getOffsets().size() > 0) break;
            meta.setSpork(false);
            stream.skipBytes(4);
            int numPlanes = stream.readInt();
            if (numPlanes != meta.getPlaneCount(0)) {
              stream.seek(stream.getFilePointer() - 4);
              int off = stream.readInt();
              meta.getOffsets().add(new Integer(off));
              for (int i=1; i<meta.getPlaneCount(0); i++) {
                if ((meta.getChunkSizes().size() > 0) && (i < meta.getChunkSizes().size())) {
                  meta.setRawSize(meta.getChunkSizes().get(i).intValue());
                }
                else i = meta.getPlaneCount(0);
                off += meta.getRawSize();
                meta.getOffsets().add(new Integer(off));
              }
            }
            else {
              for (int i=0; i<numPlanes; i++) {
                meta.getOffsets().add(new Integer(stream.readInt()));
              }
            }
          }
          else if (atomType.equals("stsd")) {
            // found video codec and pixel depth information

            stream.skipBytes(4);
            int numEntries = stream.readInt();
            stream.skipBytes(4);

            for (int i=0; i<numEntries; i++) {
              if (i == 0) {
                meta.setCodec(stream.readString(4));

                if (!meta.getCodec().equals("raw ") && !meta.getCodec().equals("rle ") &&
                  !meta.getCodec().equals("rpza") && !meta.getCodec().equals("mjpb") &&
                  !meta.getCodec().equals("jpeg"))
                {
                  throw new UnsupportedCompressionException(
                    "Unsupported codec: " + meta.getCodec());
                }

                stream.skipBytes(16);
                if (stream.readShort() == 0) {
                  stream.skipBytes(56);

                  meta.setBitsPerPixel(stream.readShort());
                  if (meta.getCodec().equals("rpza")) meta.setBitsPerPixel(8);
                  stream.skipBytes(10);
                  meta.setInterlaced(stream.read() == 2);
                  meta.getTable().put("Codec", meta.getCodec());
                  meta.getTable().put("Bits per pixel", meta.getBitsPerPixel());
                  stream.skipBytes(9);
                }
              }
              else {
                meta.setAltCodec(stream.readString(4));
                meta.getTable().put("Second codec", meta.getAltCodec());
              }
            }
          }
          else if (atomType.equals("stsz")) {
            // found the number of planes
            stream.skipBytes(4);
            meta.setRawSize(stream.readInt());
            meta.get(0).setPlaneCount(stream.readInt());

            if (meta.getRawSize() == 0) {
              stream.seek(stream.getFilePointer() - 4);
              for (int b=0; b<meta.getPlaneCount(0); b++) {
                meta.getChunkSizes().add(new Integer(stream.readInt()));
              }
            }
          }
          else if (atomType.equals("stsc")) {
            stream.skipBytes(4);

            int numChunks = stream.readInt();

            if (meta.getAltCodec() != null) {
              int prevChunk = 0;
              for (int i=0; i<numChunks; i++) {
                int chunk = stream.readInt();
                int planesPerChunk = stream.readInt();
                int id = stream.readInt();

                if (id == 2) meta.setAltPlanes(meta.getAltPlanes() +
                    planesPerChunk * (chunk - prevChunk));

                prevChunk = chunk;
              }
            }
          }
          else if (atomType.equals("stts")) {
            stream.skipBytes(12);
            int fps = stream.readInt();
            meta.getTable().put("Frames per second", fps);
          }
          if (oldpos + atomSize < stream.length()) {
            stream.seek(oldpos + atomSize);
          }
          else break;
        }

        if (atomSize == 0) offset = stream.length();
        else offset += atomSize;

        // if a 'udta' atom, skip ahead 4 bytes
        if (atomType.equals("udta")) offset += 4;
        print(depth, atomSize, atomType);
      }
    }

    /** Checks if the given String is a container atom type. */
    private static boolean isContainer(String type) {
      for (int i=0; i<CONTAINER_TYPES.length; i++) {
        if (type.equals(CONTAINER_TYPES[i])) return true;
      }
      return false;
    }

    /** Debugging method; prints information on an atom. */
    private static void print(int depth, long size, String type) {
      StringBuffer sb = new StringBuffer();
      for (int i=0; i<depth; i++) sb.append(" ");
      sb.append(type + " : [" + size + "]");
      LOGGER.debug(sb.toString());
    }

    /** Uncompresses an image plane according to the the codec identifier. */
    private static byte[] uncompress(byte[] pixs, String code, Metadata meta)
      throws FormatException, IOException
    {
      CodecOptions options = new MJPBCodecOptions();
      options.width = meta.getAxisLength(0, Axes.X);
      options.height = meta.getAxisLength(0, Axes.Y);
      options.bitsPerSample = meta.getBitsPerPixel();
      options.channels = meta.getBitsPerPixel() < 40 ? meta.getBitsPerPixel() / 8 :
        (meta.getBitsPerPixel() - 32) / 8;
      options.previousImage = meta.isCanUsePrevious() ? meta.getPrevPixels() : null;
      options.littleEndian = meta.isLittleEndian(0);
      options.interleaved = meta.isRGB(0);

      if (code.equals("raw ")) return pixs;
      else if (code.equals("rle ")) {
        return new QTRLECodec().decompress(pixs, options);
      }
      else if (code.equals("rpza")) {
        return new RPZACodec().decompress(pixs, options);
      }
      else if (code.equals("mjpb")) {
        ((MJPBCodecOptions) options).interlaced = meta.isInterlaced();
        return new MJPBCodec().decompress(pixs, options);
      }
      else if (code.equals("jpeg")) {
        return new JPEGCodec().decompress(pixs, options);
      }
      else {
        throw new UnsupportedCompressionException("Unsupported codec : " + code);
      }
    }

    /** Cut off header bytes from a resource fork file. */
    private static void stripHeader(RandomAccessInputStream stream) throws IOException {
      stream.seek(0);
      while (!stream.readString(4).equals("moov")) {
        stream.seek(stream.getFilePointer() - 2);
      }
      stream.seek(stream.getFilePointer() - 8);
    }

  }
}
