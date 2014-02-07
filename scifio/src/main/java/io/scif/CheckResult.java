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

/**
 * This class is used as a return value for the {@link Checker#matchesFormat}
 * methods. It allows a deeper level of information to be conveyed by
 * {@code matchesFormat}, as a simple boolean return value would not convey
 * partial (that is, suffix) matches - mandating the use of both
 * {@link Checker#matchesFormat} and {@link Checker#matchesSuffix(String)}.
 * 
 * @see io.scif.Checker
 * @author Mark Hiner
 */
public class CheckResult {

	// -- Fields --

	private final boolean matchedSuffix;
	private final boolean matchedFormat;

	// -- Constructor --

	public CheckResult(final boolean matched) {
		this(matched, matched);
	}

	public CheckResult(final boolean suffixMatch, final boolean formatMatch) {
		matchedSuffix = suffixMatch;
		matchedFormat = formatMatch;
	}

	// -- Accessors --

	/**
	 * @return True if the Format matched at least the provided suffix.
	 */
	public boolean partial() {
		return matchedSuffix || matchedFormat;
	}

	/**
	 * @return True if Format completely matched the provided input.
	 */
	public boolean complete() {
		return matchedFormat;
	}

}
