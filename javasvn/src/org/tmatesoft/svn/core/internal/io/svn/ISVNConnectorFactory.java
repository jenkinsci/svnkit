/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public interface ISVNConnectorFactory {

    public static final ISVNConnectorFactory DEFAULT = new ISVNConnectorFactory() {
        public ISVNConnector createConnector(SVNRepository repository) {
            if ("svn+ssh".equals(repository.getLocation().getProtocol())) {
                return new SVNGanymedConnector();
            }
            return new SVNPlainConnector();
        }
    };

    public ISVNConnector createConnector(SVNRepository repository);

}