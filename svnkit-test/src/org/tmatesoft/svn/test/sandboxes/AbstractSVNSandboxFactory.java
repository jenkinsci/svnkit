/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class AbstractSVNSandboxFactory {

    private static Collection ourFactories = new LinkedList();

    protected static void registerSandboxFactory(AbstractSVNSandboxFactory factory) {
        ourFactories.add(factory);
    }

    public static Iterator create() {
        return create(null);
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

    private File myDefaultTMP;
    private File myDumpsDir;

    public File getDumpsDir() {
        return myDumpsDir;
    }

    public File getDefaultTMP() {
        return myDefaultTMP;
    }

    protected void init(ResourceBundle bundle) {
        myDefaultTMP = new File(bundle.getString("test.tmp.dir"));
        myDumpsDir = new File(bundle.getString("test.dumps.dir"));
    }

    protected abstract AbstractSVNSandbox createSandbox(File tmp) throws SVNException;
}
