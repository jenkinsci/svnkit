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
package org.tmatesoft.svn.core.wc;

import java.util.Collection;
import java.io.File;

import org.tmatesoft.svn.core.SVNException;

/**
 * The <b>ISVNFileLocationsFinder</b> interface is used in {@link SVNDiffClient} merge operations
 * to detect copied or moved files.
 *
 * @author TMate Software Ltd.
 * @version 1.2.0
 * @since 1.2.0
 */
public interface ISVNFileLocationsFinder {

    /**
     * Returns a {@link Collection} of {@link File} the file was copied or moved (renamed) to.
     * The null result means that the file was never copied or moved, so merge operation will be processed normally on this file.
     * If file was copied the result Collection should contain it.
     * If file was moved (renamed) the result Collection should not contain this file.
     *
     * @param file     working copy path
     * @return         a {@link Collection} of {@link File} the file was copied or moved (renamed) to
     * @throws         SVNException
     */
    public Collection findLocations(File file) throws SVNException ;
}
