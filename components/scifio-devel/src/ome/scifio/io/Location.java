/*
 * #%L
 * OME SCIFIO package for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2005 - 2013 Open Microscopy Environment:
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

package ome.scifio.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import ome.scifio.AbstractHasSCIFIO;
import ome.scifio.common.Constants;

import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// HACK: for scan-deps.pl: The following packages are not actually "optional":
// optional org.apache.log4j, optional org.slf4j.impl

/**
 * Pseudo-extension of java.io.File that supports reading over HTTP.
 * It is strongly recommended that you use this instead of java.io.File.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/common/src/loci/common/Location.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/common/src/loci/common/Location.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class Location extends AbstractHasSCIFIO {

  // -- Constants --

  private static final Logger LOGGER = LoggerFactory.getLogger(Location.class);

  // -- Fields --

  private boolean isURL = true;
  private URL url;
  private File file;


  // -- Constructors --
  
  public Location(Context context) {
    setContext(context);
  }

  public Location(Context context, String pathname) {
    this(context);
    LOGGER.trace("Location({})", pathname);
    try {
      url = new URL(scifio().locations().getMappedId(pathname));
    }
    catch (MalformedURLException e) {
      LOGGER.trace("Location is not a URL", e);
      isURL = false;
    }
    if (!isURL) file = 
        new File(scifio().locations().getMappedId(pathname));
  }

  public Location(Context context, File file) {
    this(context);
    LOGGER.trace("Location({})", file);
    isURL = false;
    this.file = file;
  }

  public Location(Context context, String parent, String child) {
    this(context, parent + File.separator + child);
  }

  public Location(Context context, Location parent, String child) {
    this(context, parent.getAbsolutePath(), child);
  }

  /**
   * Return a list of all of the files in this directory.  If 'noHiddenFiles' is
   * set to true, then hidden files are omitted.
   *
   * @see java.io.File#list()
   */
  public String[] list(boolean noHiddenFiles) {
    String key = getAbsolutePath() + Boolean.toString(noHiddenFiles);
    String [] result = null;

    result = scifio().locations().getCachedListing(key);
    if (result != null) return result;
    
    ArrayList<String> files = new ArrayList<String>();
    if (isURL) {
      try {
        URLConnection c = url.openConnection();
        InputStream is = c.getInputStream();
        boolean foundEnd = false;

        while (!foundEnd) {
          byte[] b = new byte[is.available()];
          is.read(b);
          String s = new String(b, Constants.ENCODING);
          if (s.toLowerCase().indexOf("</html>") != -1) foundEnd = true;

          while (s.indexOf("a href") != -1) {
            int ndx = s.indexOf("a href") + 8;
            int idx = s.indexOf("\"", ndx);
            if (idx < 0) break;
            String f = s.substring(ndx, idx);
            if (files.size() > 0 && f.startsWith("/")) {
              return null;
            }
            s = s.substring(idx + 1);
            if (f.startsWith("?")) continue;
            Location check = new Location(getContext(), getAbsolutePath(), f);
            if (check.exists() && (!noHiddenFiles || !check.isHidden())) {
              files.add(check.getName());
            }
          }
        }
      }
      catch (IOException e) {
        LOGGER.trace("Could not retrieve directory listing", e);
        return null;
      }
    }
    else {
      if (file == null) return null;
      String[] f = file.list();
      if (f == null) return null;
      for (String name : f) {
        if (!noHiddenFiles || !(name.startsWith(".") ||
          new Location(getContext(), file.getAbsolutePath(), name).isHidden()))
        {
          files.add(name);
        }
      }
    }
    result = files.toArray(new String[files.size()]);
    
    scifio().locations().putCachedListing(key, result);
    
    return result;
  }

  // -- File API methods --

  /**
   * If the underlying location is a URL, this method will return true if
   * the URL exists.
   * Otherwise, it will return true iff the file exists and is readable.
   *
   * @see java.io.File#canRead()
   */
  public boolean canRead() {
    return isURL ? (isDirectory() || isFile()) : file.canRead();
  }

  /**
   * If the underlying location is a URL, this method will always return false.
   * Otherwise, it will return true iff the file exists and is writable.
   *
   * @see java.io.File#canWrite()
   */
  public boolean canWrite() {
    return isURL ? false : file.canWrite();
  }

  /**
   * Creates a new empty file named by this Location's path name iff a file
   * with this name does not already exist.  Note that this operation is
   * only supported if the path name can be interpreted as a path to a file on
   * disk (i.e. is not a URL).
   *
   * @return true if the file was created successfully
   * @throws IOException if an I/O error occurred, or the
   *   abstract pathname is a URL
   * @see java.io.File#createNewFile()
   */
  public boolean createNewFile() throws IOException {
    if (isURL) throw new IOException("Unimplemented");
    return file.createNewFile();
  }

  /**
   * Deletes this file.  If {@link #isDirectory()} returns true, then the
   * directory must be empty in order to be deleted.  URLs cannot be deleted.
   *
   * @return true if the file was successfully deleted
   * @see java.io.File#delete()
   */
  public boolean delete() {
    return isURL ? false : file.delete();
  }

  /**
   * Request that this file be deleted when the JVM terminates.
   * This method will do nothing if the pathname represents a URL.
   *
   * @see java.io.File#deleteOnExit()
   */
  public void deleteOnExit() {
    if (!isURL) file.deleteOnExit();
  }

  /**
   * @see java.io.File#equals(Object)
   * @see java.net.URL#equals(Object)
   */
  public boolean equals(Object obj) {
    String absPath = getAbsolutePath();
    String thatPath = null;

    if (obj instanceof Location) {
      thatPath = ((Location) obj).getAbsolutePath();
    }
    else {
      thatPath = obj.toString();
    }

    return absPath.equals(thatPath);
  }

  public int hashCode() {
    return getAbsolutePath().hashCode();
  }

  /**
   * Returns whether or not the pathname exists.
   * If the pathname is a URL, then existence is determined based on whether
   * or not we can successfully read content from the URL.
   *
   * @see java.io.File#exists()
   */
  public boolean exists() {
    if (isURL) {
      try {
        url.getContent();
        return true;
      }
      catch (IOException e) {
        LOGGER.trace("Failed to retrieve content from URL", e);
        return false;
      }
    }
    if (file.exists()) return true;
    if (scifio().locations().getMappedFile(file.getPath()) != null) return true;

    String mappedId = scifio().locations().getMappedId(file.getPath());
    return mappedId != null && new File(mappedId).exists();
  }

  /* @see java.io.File#getAbsoluteFile() */
  public Location getAbsoluteFile() {
    return new Location(getContext(), getAbsolutePath());
  }

  /* @see java.io.File#getAbsolutePath() */
  public String getAbsolutePath() {
    return isURL ? url.toExternalForm() : file.getAbsolutePath();
  }

  /* @see java.io.File#getCanonicalFile() */
  public Location getCanonicalFile() throws IOException {
    return isURL ? getAbsoluteFile() : new Location(getContext(), file.getCanonicalFile());
  }

  /**
   * Returns the canonical path to this file.
   * If the file is a URL, then the canonical path is equivalent to the
   * absolute path ({@link #getAbsolutePath()}).  Otherwise, this method
   * will delegate to {@link java.io.File#getCanonicalPath()}.
   */
  public String getCanonicalPath() throws IOException {
    return isURL ? getAbsolutePath() : file.getCanonicalPath();
  }

  /**
   * Returns the name of this file, i.e. the last name in the path name
   * sequence.
   *
   * @see java.io.File#getName()
   */
  public String getName() {
    if (isURL) {
      String name = url.getFile();
      name = name.substring(name.lastIndexOf("/") + 1);
      return name;
    }
    return file.getName();
  }

  /**
   * Returns the name of this file's parent directory, i.e. the path name prefix
   * and every name in the path name sequence except for the last.
   * If this file does not have a parent directory, then null is returned.
   *
   * @see java.io.File#getParent()
   */
  public String getParent() {
    if (isURL) {
      String absPath = getAbsolutePath();
      absPath = absPath.substring(0, absPath.lastIndexOf("/"));
      return absPath;
    }
    return file.getParent();
  }

  /* @see java.io.File#getParentFile() */
  public Location getParentFile() {
    return new Location(getContext(), getParent());
  }

  /* @see java.io.File#getPath() */
  public String getPath() {
    return isURL ? url.getHost() + url.getPath() : file.getPath();
  }

  /**
   * Tests whether or not this path name is absolute.
   * If the path name is a URL, this method will always return true.
   *
   * @see java.io.File#isAbsolute()
   */
  public boolean isAbsolute() {
    return isURL ? true : file.isAbsolute();
  }

  /**
   * Returns true if this pathname exists and represents a directory.
   *
   * @see java.io.File#isDirectory()
   */
  public boolean isDirectory() {
    if (isURL) {
      String[] list = list();
      return list != null;
    }
    return file.isDirectory();
  }

  /**
   * Returns true if this pathname exists and represents a regular file.
   *
   * @see java.io.File#exists()
   */
  public boolean isFile() {
    return isURL ? (!isDirectory() && exists()) : file.isFile();
  }

  /**
   * Returns true if the pathname is 'hidden'.  This method will always
   * return false if the pathname corresponds to a URL.
   *
   * @see java.io.File#isHidden()
   */
  public boolean isHidden() {
    return isURL ? false : file.isHidden();
  }

  /**
   * Return the last modification time of this file, in milliseconds since
   * the UNIX epoch.
   * If the file does not exist, 0 is returned.
   *
   * @see java.io.File#lastModified()
   * @see java.net.URLConnection#getLastModified()
   */
  public long lastModified() {
    if (isURL) {
      try {
        return url.openConnection().getLastModified();
      }
      catch (IOException e) {
        LOGGER.trace("Could not determine URL's last modification time", e);
        return 0;
      }
    }
    return file.lastModified();
  }

  /**
   * @see java.io.File#length()
   * @see java.net.URLConnection#getContentLength()
   */
  public long length() {
    if (isURL) {
      try {
        return url.openConnection().getContentLength();
      }
      catch (IOException e) {
        LOGGER.trace("Could not determine URL's content length", e);
        return 0;
      }
    }
    return file.length();
  }

  /**
   * Return a list of file names in this directory.  Hidden files will be
   * included in the list.
   * If this is not a directory, return null.
   */
  public String[] list() {
    return list(false);
  }

  /**
   * Return a list of absolute files in this directory.  Hidden files will
   * be included in the list.
   * If this is not a directory, return null.
   */
  public Location[] listFiles() {
    String[] s = list();
    if (s == null) return null;
    Location[] f = new Location[s.length];
    for (int i=0; i<f.length; i++) {
      f[i] = new Location(getContext(), getAbsolutePath(), s[i]);
      f[i] = f[i].getAbsoluteFile();
    }
    return f;
  }

  /**
   * Return the URL corresponding to this pathname.
   *
   * @see java.io.File#toURL()
   */
  public URL toURL() throws MalformedURLException {
    return isURL ? url : file.toURI().toURL();
  }

  /**
   * @see java.io.File#toString()
   * @see java.net.URL#toString()
   */
  public String toString() {
    return isURL ? url.toString() : file.toString();
  }

}
