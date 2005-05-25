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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <code>ISVNWorkspaceMediator</code> is a custom interface for managing a workspace.
 * A workspace is notion that represents a user's disk area where his working copies
 * are checked out to and stored. This mediator helps to manage administrative 
 * directories (also as known as <i>.svn</i> folders) as well as working copy 
 * properties. 
 * 
 * @version	1.0
 * @author 	TMate Software Ltd.
 * @see		SVNRepository#getCommitEditor(String, Map, boolean, ISVNWorkspaceMediator)
 */
public interface ISVNWorkspaceMediator {

    /**
     * Gets a working copy property. The working copy is located in a workspace. 
     * 
     * @param  path 		a working copy path within a workspace
     * @param  name 		a propery name
     * @return 				the value for the property
     * @throws SVNException
     * @see					#setWorkspaceProperty(String, String, String)
     */
    public String getWorkspaceProperty(String path, String name) throws SVNException;
    
    /**
     * Immediately sets a new value for a working copy property. The working copy is 
     * located in a workspace.
     * 
     * @param  path 			a working copy path within a workspace
     * @param  name 			a propery name
     * @param  value			a value for the property
     * @throws SVNException
     * @see						#getWorkspaceProperty(String, String)
     */
    public void setWorkspaceProperty(String path, String name, String value) throws SVNException ;
    
    /**
     * Creates a temporary file in a <code>path</code>/.svn/tmp folder and specifies
     * an <code>id</code> for it.
     * 
     * @param  path  		a path containing an <i>.svn</i> folder	
     * @param  id			an id for the created temporary file 
     * @return				an <code>OutputStream</code> to write into the file
     * @throws IOException
     * @see					#getTemporaryLocation(Object)
     */
    public OutputStream createTemporaryLocation(String path, Object id) throws IOException;
    
    /**
     * Gets an <code>InputStream</code> for a temporary file identified by its
     * <code>id</code>.
     * 
     * @param id			the id of a temporary file
     * @return				an <code>InputStream</code> to read file contents from 
     * @throws IOException
     */
    public InputStream getTemporaryLocation(Object id) throws IOException;
    
    /**
     * Gets the length of a temporary file identified by its <code>id</code>.
     * 
     * @param id			the id of a temporary file
     * @return				a temporary file length in bytes
     * @throws IOException
     * @see					#createTemporaryLocation(String, Object)
     * @see					#getTemporaryLocation(Object)
     */
    public long getLength(Object id) throws IOException;
    
    /**
     * Removes a temporary file identified by its <code>id</code>.
     * 
     * @param id	the id of a temporary file
     */
    public void deleteTemporaryLocation(Object id);
    
    /**
     * Removes an administrative directory (<i>.svn</i> folder) and 
     * all its contents.
     * 
     * @param path	a path to a directory which contains an <i>.svn</i> folder
     * 				to be entirely removed
     */
    public void deleteAdminFiles(String path);
}
