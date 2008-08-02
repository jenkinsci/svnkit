/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNURL;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public interface ISVNExternalsHandler {
    
    public static final ISVNExternalsHandler DEFAULT = new ISVNExternalsHandler() {
        public SVNRevision[] handleExternal(File externalPath, SVNURL externalURL, SVNRevision externalRevision, 
                SVNRevision externalPegRevision, String externalsDefinition, SVNRevision externalsWorkingRevision) {
            return new SVNRevision[] { externalRevision, externalPegRevision };
        }

    };
    
    /**
     * @param externalPath path of the external to be processed
     * @param externalURL URL of the external to be processed or null if external is about to be removed
     * @param revision default revision to checkout external at or to update to
     * @param pegRevision default peg revision to use for checkout our update of external
     * 
     * @return array of SVNRevision in form {revision, pegRevision} or null to skip processing 
     * of this external.
     */
    public SVNRevision[] handleExternal(File externalPath, SVNURL externalURL, SVNRevision externalRevision, 
            SVNRevision externalPegRevision, String externalsDefinition, SVNRevision externalsWorkingRevision);

}
