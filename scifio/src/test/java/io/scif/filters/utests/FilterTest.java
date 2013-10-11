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

package io.scif.filters.utests;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import io.scif.FormatException;
import io.scif.Reader;
import io.scif.SCIFIO;
import io.scif.filters.AbstractReaderFilter;
import io.scif.filters.ChannelSeparator;
import io.scif.filters.Filter;
import io.scif.filters.ReaderFilter;

import java.io.IOException;

import net.imglib2.meta.Axes;

import org.scijava.InstantiableException;
import org.scijava.plugin.Attr;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.junit.After;
import org.junit.Test;

/**
 * Tests for the {@link Filter} classes.
 * 
 * @author Mark Hiner
 */
public class FilterTest {

	private final SCIFIO scifio = new SCIFIO();
	private final String id =
		"testImg&sizeX=512&sizeY=512&sizeT=5&dimOrder=XYTZC.fake";
	private Reader readerFilter;

	@After
	public void tearDown() throws IOException {
		readerFilter.close();
	}

	/**
	 * Verifies that opening a file with a filter enabled, then reusing the reader
	 * with another file works as intended (and does not re-opene the original
	 * file).
	 */
	@Test
	public void testSetSource() throws FormatException, IOException {
		final String id2 =
			"testImg&sizeX=256&sizeY=128&sizeT=5&dimOrder=XYTZC.fake";

		readerFilter = scifio.initializer().initializeReader(id, true);

		try {
			((ReaderFilter) readerFilter).enable(ChannelSeparator.class);
		}
		catch (InstantiableException e) {
			throw new FormatException(e);
		}

		int x = readerFilter.getMetadata().getAxisLength(0, Axes.X);
		int y = readerFilter.getMetadata().getAxisLength(0, Axes.Y);
		assertEquals(512, x);
		assertEquals(512, y);

		readerFilter.setSource(id2);

		x = readerFilter.getMetadata().getAxisLength(0, Axes.X);
		y = readerFilter.getMetadata().getAxisLength(0, Axes.Y);
		assertEquals(256, x);
		assertEquals(128, y);

	}

	@Test
	public void testDefaultEnabled() throws FormatException, IOException {
		readerFilter = scifio.initializer().initializeReader(id);

		boolean enabledProperly = false;
		boolean disabledProperly = true;

		// Make sure that our enabled plugin is found, and the disabled one isn't
		while (Filter.class.isAssignableFrom(readerFilter.getClass())) {
			if (EnabledFilter.class.isAssignableFrom(readerFilter.getClass())) enabledProperly =
				true;

			if (DisabledFilter.class.isAssignableFrom(readerFilter.getClass())) disabledProperly =
				false;

			readerFilter = (Reader) ((Filter) readerFilter).getParent();
		}

		assertTrue(enabledProperly);
		assertTrue(disabledProperly);

	}

	@Test
	public void testFilterOrder() throws FormatException, IOException,
		InstantiableException
	{
		readerFilter = scifio.initializer().initializeReader(id);

		// enable all plugins
		for (final PluginInfo<Filter> pi : scifio.getContext().getPluginIndex()
			.getPlugins(Filter.class))
		{
			((ReaderFilter) readerFilter).enable(pi.getPluginClass());
		}

		double lastPriority = Double.MAX_VALUE;

		// Skip the actual ReaderFilter class
		if (Filter.class.isAssignableFrom(readerFilter.getClass())) readerFilter =
			(Reader) ((Filter) readerFilter).getParent();

		while (Filter.class.isAssignableFrom(readerFilter.getClass())) {
			final double priority = ((Filter) readerFilter).getPriority();

			// Verify all plugins are in proper order
			assertTrue(priority <= lastPriority);
			lastPriority = priority;

			readerFilter = (Reader) ((Filter) readerFilter).getParent();
		}

	}

	// Sample plugin class known to be enabled by default
	@Plugin(
		type = Filter.class,
		attrs = {
			@Attr(name = EnabledFilter.FILTER_KEY, value = EnabledFilter.FILTER_VALUE),
			@Attr(name = EnabledFilter.ENABLED_KEY,
				value = EnabledFilter.ENABLED_VAULE) })
	public static class EnabledFilter extends AbstractReaderFilter {

		public static final String FILTER_VALUE = "io.scif.Reader";
		public static final String ENABLED_VAULE = "true";
	}

	// Sample plugin class known to be disabled by default
	@Plugin(type = Filter.class,
		attrs = {
			@Attr(name = DisabledFilter.FILTER_KEY,
				value = DisabledFilter.FILTER_VALUE),
			@Attr(name = DisabledFilter.ENABLED_KEY,
				value = DisabledFilter.ENABLED_VAULE) })
	public static class DisabledFilter extends AbstractReaderFilter {

		public static final String FILTER_VALUE = "io.scif.Reader";
		public static final String ENABLED_VAULE = "false";
	}
}
