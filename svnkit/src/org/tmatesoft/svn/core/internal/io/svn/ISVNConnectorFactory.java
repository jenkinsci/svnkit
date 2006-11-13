/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.svn;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNConnectorFactory {

    public static final ISVNConnectorFactory DEFAULT = new ISVNConnectorFactory() {

        public ISVNConnector createConnector(SVNRepository repository) throws SVNException {
            SVNURL location = repository.getLocation();
            if ("svn+ssh".equals(location.getProtocol())) {
                return new SVNGanymedConnector();
            } else if (location.getProtocol().startsWith("svn+")) {
                String name = location.getProtocol().substring("svn+".length());
                if (repository.getTunnelProvider() != null) {
                    String tunnel = repository.getTunnelProvider().getTunnelDefinition(name);
                    if (tunnel != null) {
                        return new SVNTunnelConnector(name, tunnel);
                    }
                }
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.EXTERNAL_PROGRAM, "Cannot find tunnel specification for ''{0}''", name);
                SVNErrorManager.error(err);
            }
            return new SVNPlainConnector();
        }
    };

    public ISVNConnector createConnector(SVNRepository repository) throws SVNException;

}