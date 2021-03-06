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

package io.scif;

import io.scif.filters.MetadataWrapper;
import io.scif.util.SCIFIOMetadataTools;

import java.util.List;

/**
 * Abstract superclass of all SCIFIO {@link io.scif.Translator} components.
 * <p>
 * NB: this class provides a special abstract method:
 * {@code typedTranslate(Metadata, Metadata)}. That is the method which should
 * be overridden when extending this class, and not
 * {@link #translate(Metadata, Metadata)} - which has been set up to translate
 * the type-general Metadata information, delegate to {@code typedTranslate},
 * and then populate the ImageMetadata of the destination.
 * </p>
 * 
 * @see io.scif.Translator
 * @see io.scif.services.TranslatorService
 * @see io.scif.Metadata
 * @author Mark Hiner
 * @param <M> - The source Metadata type required by this Translator
 * @param <N> - The destination Metadata type required by this Translator
 */
public abstract class AbstractTranslator<M extends Metadata, N extends Metadata>
	extends AbstractSCIFIOPlugin implements Translator
{

	// -- Translator API --

	@Override
	public void translate(final Metadata source, final Metadata dest) {
		translate(source, source.getAll(), dest);
	}

	@Override
	public void translate(final Metadata source,
		final List<ImageMetadata> sourceImgMeta, final Metadata dest)
	{
		Metadata trueSource = source;

		// Unwrap MetadataWrappers to get to the actual format-specific metadata
		while (trueSource instanceof MetadataWrapper) {
			trueSource = ((MetadataWrapper) trueSource).unwrap();
		}

		// Cast the parameters to typed Metadata
		final M typedSource = SCIFIOMetadataTools.<M> castMeta(trueSource);
		final N typedDest = SCIFIOMetadataTools.<N> castMeta(dest);

		// Boilerplate for common Metadata fields
		dest.setSource(source.getSource());
		dest.setFiltered(source.isFiltered());
		dest.setDatasetName(source.getDatasetName());

		// Type-dependent translation
		translateFormatMetadata(typedSource, typedDest);
		translateImageMetadata(sourceImgMeta, typedDest);

		// -- Post-translation hook --
		// Update the source's ImageMetadata based on the translation results
		dest.populateImageMetadata();
	}

	/**
	 * Use format-agnostic metadata ({@link ImageMetadata} to populate the
	 * destination's format-specific metadata.
	 * <p>
	 * This method must be implemented by every AbstractTranslator subclass,
	 * as it covers the general case - agnostic of the source.
	 * </p>
	 * 
	 * @param source
	 * @param dest
	 */
	protected abstract void translateImageMetadata(
		final List<ImageMetadata> source, final N dest);

	/**
	 * Use format-specific metadata from the source to populate the destination's
	 * format-specific metadata.
	 * <p>
	 * NB: Override this method when writing a translator from a concrete Metadata
	 * type.
	 * </p>
	 * 
	 * @param source
	 * @param dest
	 */
	protected void translateFormatMetadata(final M source, final N dest) {
		// Nothing to do
	}
}
