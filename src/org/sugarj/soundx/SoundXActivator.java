/*
 * Copyright (c) 2015, TU Berlin
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 * - Neither the name of the TU Berlin nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sugarj.soundx;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.sugarj.AbstractBaseLanguage;
import org.sugarj.BaseLanguageRegistry;
import org.sugarj.common.path.AbsolutePath;
import org.sugarj.common.path.Path;

/**
 * Eclipse plugin activator for an extensible language defined with SoundX.
 * The base language definition is processed at plugin load time if necessary.
 *
 * @author Florian Lorenzen <florian.lorenzen@tu-berlin.de>
 */
public class SoundXActivator extends AbstractUIPlugin {
	private static SoundXActivator plugin;

	public SoundXActivator(final String bldFilename) {
		BaseLanguageRegistry.getInstance().registerBaseLanguageLazy(new Future<AbstractBaseLanguage>() {

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return false;
			}

			@Override
			public boolean isCancelled() {
				return false;
			}

			@Override
			public boolean isDone() {
				return false;
			}

			@Override
			public AbstractBaseLanguage get() throws InterruptedException, ExecutionException {
				Path pluginDirectory = getPluginDirectory();
				SoundXBaseLanguage instance = new SoundXBaseLanguage();
				instance.processBaseLanguageDefinition(bldFilename, pluginDirectory);
				return instance;
			}

			@Override
			public AbstractBaseLanguage get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				return get();
			}
		});
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	public static SoundXActivator getDefault() {
		return plugin;
	}

	private final Path getPluginDirectory() {
		String thisClassPath = this.getClass().getName().replace(".", "/")
				+ ".class";
		URL thisClassURL = this.getClass().getClassLoader()
				.getResource(thisClassPath);

		if (thisClassURL.getProtocol().equals("bundleresource"))
			try {
				thisClassURL = FileLocator.resolve(thisClassURL);
			} catch (IOException e) {
				e.printStackTrace();
			}
		
		String classPath;
		try {
			classPath = new File(thisClassURL.toURI()).getAbsolutePath();
		} catch (URISyntaxException e) {
			classPath = new File(thisClassURL.getPath()).getAbsolutePath();
		}

		String binPath = classPath.substring(0, classPath.length()
				- thisClassPath.length());

		if(binPath.endsWith("/bin/")) {
			String pluginPath = binPath.substring(0, binPath.length()-"bin/".length());
			return new AbsolutePath(pluginPath);
		}
		else if(binPath.endsWith("/target/classes/")) {
			String pluginPath = binPath.substring(0, binPath.length()-"target/classes/".length());
			return new AbsolutePath(pluginPath);
		}
		
		throw new RuntimeException("Expected bin path ending with bin/ or target/classes/ but was " + binPath);
	}
}
