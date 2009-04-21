/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.sandboxes;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ResourceBundle;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.test.SVNTestScheme;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class AbstractSVNSandboxFactory {

    private static final Collection ourFactories = new LinkedList();

    protected static void registerSandboxFactory(AbstractSVNSandboxFactory factory) {
        synchronized (ourFactories) {
            ourFactories.add(factory);
        }
    }

    public static Iterator create() {
        return create((File) null);
    }

    public static Iterator create(final File tmp) {
        final Iterator factories = ourFactories.iterator();
        return new Iterator() {

            public boolean hasNext() {
                return factories.hasNext();
            }

            public Object next() {
                AbstractSVNSandboxFactory factory = (AbstractSVNSandboxFactory) factories.next();
                try {
                    return factory.createSandbox(tmp);
                } catch (SVNException e) {
                    return null;
                }
            }

            public void remove() {
            }
        };
    }

    public static AbstractSVNSandbox create(final File tmp, SVNTestScheme scheme) throws SVNException {
        Iterator iterator = create(tmp);
        while (iterator.hasNext()) {
            AbstractSVNSandboxFactory factory = (AbstractSVNSandboxFactory) iterator.next();
            if (factory.supports(scheme)) {
                return factory.createSandbox(tmp);
            }
        }
        return null;
    }

    public static AbstractSVNSandbox create(SVNTestScheme scheme) throws SVNException {
        return create(null, scheme);
    }

    private File myDefaultTMP;
    private File myDumpsDir;
    private SVNTestScheme myScheme;

    public File getDumpsDir() {
        return myDumpsDir;
    }

    public File getDefaultTMP() {
        return new File(myDefaultTMP, getScheme().toString());
    }

    protected void init(ResourceBundle bundle) {
        myDefaultTMP = new File(bundle.getString("test.tmp.dir"));
        myDumpsDir = new File(bundle.getString("test.dumps.dir"));
    }

    protected SVNTestScheme getScheme() {
        return myScheme;
    }

    protected void setScheme(SVNTestScheme scheme) {
        myScheme = scheme;
    }

    protected boolean supports(SVNTestScheme scheme) {
        return scheme != null && scheme.equals(getScheme());
    }

    protected abstract AbstractSVNSandbox createSandbox(File tmp) throws SVNException;
}
