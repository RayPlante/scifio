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

package io.scif;

import io.scif.io.RandomAccessInputStream;

/**
 * Interface for all SCIFIO Checker components.
 * <p>
 * {@code Checker} components are used to determine if the {@code Format} they
 * are associated with is compatibile with a given image. This is accomplished
 * via the {@link #matchesFormat} methods. For a more superficial check, the
 * {@link #matchesSuffix(String)} method is provided.
 * </p>
 * 
 * @see io.scif.Format
 * @see io.scif.HasFormat
 * @see io.scif.CheckResult
 * @author Mark Hiner
 */
public interface Checker extends HasFormat {

	// -- Checker API methods --

	/**
	 * Whether the file extension matching one of the format's suffixes is
	 * necessary to identify the file as a source compatible with this format.
	 */
	boolean suffixNecessary();

	/**
	 * Whether the file extension matching one of the format's suffixes is
	 * sufficient to identify the file as a source compatible with this format.
	 * <p>
	 * If false, the source will have to be read to determine compatibility.
	 * </p>
	 */
	boolean suffixSufficient();

	/**
	 * Checks if the provided image source is compatible with this {@code Format}
	 * based purely on suffix matching. Will not open the source during this
	 * process.
	 * <p>
	 * NB: use {@link #matchesFormat(String)} if the result of
	 * {@link #matchesFormat(RandomAccessInputStream)} is also desired.
	 * </p>
	 * 
	 * @param name path to the image source to check.
	 * @return True if the image source is compatible with this {@code Format}.
	 */
	boolean matchesSuffix(String name);

	/**
	 * Checks if the indicated path is to an image source supported by this
	 * {@code Format}. If {@link #suffixSufficient()} is false, the dataset will
	 * be opened and read via {@link #matchesFormat(RandomAccessInputStream)} to
	 * determine compatibility.
	 * <p>
	 * Through the use of a {@link CheckResult} return type, this method can
	 * convey the information of both
	 * {@link #matchesFormat(RandomAccessInputStream)} and
	 * {@link #matchesSuffix(String)}.
	 * </p>
	 * 
	 * @param name path to the image source to check.
	 * @return A {@link CheckResult} object with compatibility information for
	 *         this Format.
	 */
	CheckResult matchesFormat(String name);

	/**
	 * Checks if the given stream is a valid image source for this {@code Format}.
	 * If this format does not have any identifying embedded signatures, this
	 * method should return false.
	 * <p>
	 * NB: use {@link #matchesFormat(String)} if the result of
	 * {@link #matchesSuffix(String)} is also desired.
	 * </p>
	 * 
	 * @param stream the image source to check.
	 * @return A {@link CheckResult} object with compatibility information for
	 *         this Format.
	 */
	boolean matchesFormat(RandomAccessInputStream stream);

	/**
	 * Checks if the given bytes are a valid header for this {@code Format}.
	 * 
	 * @param block the byte array to check.
	 * @return True if {@code block} is compatible with this {@code Format}.
	 */
	boolean checkHeader(byte[] block);
}
