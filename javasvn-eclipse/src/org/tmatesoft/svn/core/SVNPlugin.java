/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * The main plugin class to be used in the desktop.
 */
public class SVNPlugin extends Plugin {
	
	private static final String RA_EXTENSION_POINT_ID = "org.tmatesoft.svn.core.ra";
	
	private static final String URL_TAG = "url";
    private static final String REPOSITORY_FACTORY_TAG = "repositoryFactory";
	private static final String NAME_ATTR = "name";
	private static final String CLASS_ATTR = "class";
	

	private static SVNPlugin plugin;
	
	public SVNPlugin() {
		plugin = this;
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);
		IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(RA_EXTENSION_POINT_ID);
		for (int i = 0; elements != null && i < elements.length; i++) {
			IConfigurationElement raElement = elements[i];
			IConfigurationElement[] raFactory = raElement.getChildren(REPOSITORY_FACTORY_TAG);

			if (raFactory == null || raFactory.length < 1) {
				continue;
			}

			SVNRepositoryFactory factory = new DelegatingRepositoryFactory(raFactory[0]);
			IConfigurationElement[] urls = raElement.getChildren(URL_TAG);

			for (int j = 0; j < urls.length; j++) {
				IConfigurationElement urlElement = urls[j];
				String regexp = urlElement.getAttribute(NAME_ATTR);
				if (regexp == null || regexp.trim().length() == 0) {
					continue;
				}
				DelegatingRepositoryFactory.registerImplementation(regexp, factory);
			}
		}
	}

	public void stop(BundleContext context) throws Exception {
		super.stop(context);
	}

	public static SVNPlugin getDefault() {
		return plugin;
	}
	
	private static class DelegatingRepositoryFactory extends SVNRepositoryFactory {
		
		private SVNRepositoryFactory myDelegate;
		private final IConfigurationElement myConfigElement;

		public DelegatingRepositoryFactory(IConfigurationElement configElement) {
			myConfigElement = configElement;			
		}

		public SVNRepository createRepositoryImpl(SVNRepositoryLocation loation) {
			if (myDelegate == null) {
				try {
					myDelegate = (SVNRepositoryFactory) myConfigElement.createExecutableExtension(CLASS_ATTR);
				} catch (CoreException e) {
				}
			}
			return myDelegate.createRepositoryImpl(loation);
		}
		
		public static void registerImplementation(String p, SVNRepositoryFactory f) {
			SVNRepositoryFactory.registerRepositoryFactory(p, f);
			
		}
		
	}
}
