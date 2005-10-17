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

import org.tmatesoft.svn.core.SVNException;

/**
 * <code>ISVNWorkspaceMediator</code> is an interface mainly used for temporary 
 * data storage (such as new text data and instructions data for deltas) as well
 * as for accessing some kind of WC properties.
 * 
 * @version	1.0
 * @author 	TMate Software Ltd.
 * @see		SVNRepository#getCommitEditor(String, Map, boolean, ISVNWorkspaceMediator)
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
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
    
    /**
     * Creates a temporary data storage for writing and maps the given 
     * <code>id</code> object to this storage, so that later the storage will be 
     * available for reading through a call to {@link #getTemporaryLocation(Object)},
     * which receives the <code>id</code>. 
     * 
     * <p>
     * This is used for constructing diff windows and mapping them to temporary 
     * storages that contain instructions and new text data for the windows. 
     * 
     * @param  path  		an item's relative path 	
     * @param  id			an id for the created temporary  
     * @return				an output stream to write data to the allocated 
     *                      storage
     * @throws IOException  if an output stream can not be created
     * @see					#getTemporaryLocation(Object)
     */
    public OutputStream createTemporaryLocation(String path, Object id) throws IOException;
    
    /**
     * Retrieves an input stream to read data from the temporary storage mapped
     * against the <code>id</code> object.
     * 
     * @param id			an id as a key to the temporary storage
     * @return				an input stream to read data from the temporary 
     *                      storage 
     * @throws IOException  if an input stream can not be created
     * @see                 #createTemporaryLocation(String, Object)
     */
    public InputStream getTemporaryLocation(Object id) throws IOException;
    
    /**
     * Gets the size of a temporary data storage mapped against the given
     * <code>id</code>.
     * 
     * @param id			an id as a key to a data storage
     * @return				the data storage size in bytes
     * @throws IOException
     * @see					#createTemporaryLocation(String, Object)
     * @see					#getTemporaryLocation(Object)
     */
    public long getLength(Object id) throws IOException;
    
    /**
     * Disposes a temporary data storage mapped against the given 
     * <code>id</code>.
     * 
     * @param id	an id as a key to a temporary data storage
     * @see                 #createTemporaryLocation(String, Object)
     * @see                 #getTemporaryLocation(Object)
     */
    public void deleteTemporaryLocation(Object id);
}
