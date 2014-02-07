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
import io.scif.util.FormatTools;

import java.io.IOException;

/**
 * Abstract superclass of all SCIFIO {@link io.scif.Checker} implementations.
 * 
 * @see io.scif.Checker
 * @see io.scif.HasFormat
 * @author Mark Hiner
 */
public abstract class AbstractChecker extends AbstractHasFormat implements
	Checker
{

	// -- Checker API Methods --

	@Override
	public boolean suffixNecessary() {
		return true;
	}

	@Override
	public boolean suffixSufficient() {
		return true;
	}

	@Override
	public boolean matchesSuffix(final String name) {
		return FormatTools.checkSuffix(name, getFormat().getSuffixes());
	}

	@Override
	public CheckResult matchesFormat(final String name) {
		if (suffixNecessary() || suffixSufficient()) {
			// it's worth checking the file extension
			final boolean suffixMatch = matchesSuffix(name);

			// if suffix match is required but it doesn't match, failure
			if (suffixNecessary() && !suffixMatch) return new CheckResult(false);

			// if suffix matches and that's all we need, green light it
			if (suffixMatch && suffixSufficient()) return new CheckResult(true);
		}

		// suffix matching was inconclusive; we need to analyze the file contents
		try {
			final RandomAccessInputStream stream =
				new RandomAccessInputStream(getContext(), name);
			final boolean isFormat = matchesFormat(stream);
			stream.close();
			return new CheckResult(true, isFormat);
		}
		catch (final IOException exc) {
			log().debug("", exc);
			return new CheckResult(false);
		}
	}

	@Override
	public boolean matchesFormat(final RandomAccessInputStream stream) {
		try {
			return readFormatSignature(stream);
		}
		catch (IOException e) {
			log().error(e);
		}
		return false;
	}

	@Override
	public boolean checkHeader(final byte[] block) {
		try {
			final RandomAccessInputStream stream =
				new RandomAccessInputStream(getContext(), block);
			final boolean isFormat = matchesFormat(stream);
			stream.close();
			return isFormat;
		}
		catch (final IOException e) {
			log().debug("", e);
		}
		return false;
	}

	// -- AbstractChecker methods --

	/**
	 * Helper method to perform the work for
	 * {@link #matchesFormat(RandomAccessInputStream)}.
	 */
	protected abstract boolean readFormatSignature(
		final RandomAccessInputStream stream) throws IOException;
}
