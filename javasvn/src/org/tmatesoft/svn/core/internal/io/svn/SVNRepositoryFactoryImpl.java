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

package org.tmatesoft.svn.core.internal.io.svn;

import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @author Alexander Kitaev
 */
public final class SVNRepositoryFactoryImpl extends SVNRepositoryFactory {

    private static ISVNConnectorFactory ourConnectorFactory;

    public static void setup() {
        setup(null);
    }

    public static void setup(ISVNConnectorFactory connectorFactory) {
        ourConnectorFactory = connectorFactory == null ? ISVNConnectorFactory.DEFAULT : connectorFactory;
        SVNRepositoryFactory.registerRepositoryFactory("^svn(\\+ssh)?://.*$", new SVNRepositoryFactoryImpl());
    }

    public SVNRepository createRepositoryImpl(SVNRepositoryLocation location) {
        return new SVNAuthRepository(location, new SVNRepositoryImpl(location));
    }

    static ISVNConnectorFactory getConnectorFactory() {
        return ourConnectorFactory;
    }
}
