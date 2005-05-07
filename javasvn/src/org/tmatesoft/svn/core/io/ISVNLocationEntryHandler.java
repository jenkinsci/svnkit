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

package org.tmatesoft.svn.core.io;

/**
 * <p>
 * This public interface should be implemented for using within 
 * {@link SVNRepository#getLocations(String, long, long[], ISVNLocationEntryHandler) 
 * SVNRepository.getLocations(String, long, long[], ISVNLocationEntryHandler)}. The
 * mentioned  method retrieves file locations for interested revisions and uses an
 * implementation of <code>ISVNLocationEntry</code> to handle them. 
 * </p>
 * @version 1.0
 * @author TMate Software Ltd.
 * @see SVNLocationEntry 
 */
public interface ISVNLocationEntryHandler {
    /**
     * <p>
     * To be implemented for location entries handling.
     * </p>
     * @param locationEntry a location entry
     * @see SVNLocationEntry
     */
    public void handleLocationEntry(SVNLocationEntry locationEntry);

}
