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

package org.tmatesoft.svn.core.io;

import org.tmatesoft.svn.core.SVNException;

/**
 * The <b>ISVNWorkspaceMediator</b> interface is used for temporary 
 * data storage (mainly instructions and new text data for deltas) as well
 * as for caching and getting some kind of wcprops.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see		SVNRepository#getCommitEditor(String, Map, boolean, ISVNWorkspaceMediator)
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 * 
 */
public interface ISVNWorkspaceMediator {

    /**
     * Retrieves an item's WC property from a <code>".svn/wcprops"</code> administrative 
     * subdirectory. 
     * 
     * @param  path 		a WC item's path
     * @param  name 		a propery name
     * @return 				the value for the property
     * @throws SVNException
     * @see					#setWorkspaceProperty(String, String, String)
     */
    public String getWorkspaceProperty(String path, String name) throws SVNException;
    
    /**
     * Sets a new value for an item's WC property in a <code>".svn/wcprops"</code> 
     * administrative subdirectory.
     * 
     * @param  path 			a WC item's path
     * @param  name 			a propery name
     * @param  value			a value for the property
     * @throws SVNException
     * @see						#getWorkspaceProperty(String, String)
     */
    public void setWorkspaceProperty(String path, String name, String value) throws SVNException ;
}
