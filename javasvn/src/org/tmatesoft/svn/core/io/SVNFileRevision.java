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

import java.util.Map;

/**
 * This is a class that represents information on what path a file is located at a 
 * definite revision and what its revision properties and file properties delta
 * for that revision are.
 * 
 * <p>
 * A file properties delta is provided against a previous revision of this file 
 * (in comparison with its current one that can be obtained via the
 * <code>getRevision()</code> method). So, if there's no any delya between these 
 * adjacent revisions - then the <code>getPropertiesDelta()</code> an empty 
 * <code>Map</code> collection, otherwise it will contain the delta for all changed
 * file properties.
 * 
 *  
 * @version	1.0
 * @author 	TMate Software Ltd.
 * @see		ISVNFileRevisionHandler
 * @see		SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)
 * @see		SVNRepository#getFileRevisions(String, Collection, long, long)
 * @see		SVNRevisionProperty
 */
public class SVNFileRevision implements Comparable {
    
    private String myPath;
    private long myRevision;
    
    /**
     * Constructs an instance of <code>SVNFileRevision</code> given a file path,
     * revision, revision properties and possible file properties delta.
     * 
     * @param path				a file path relative to a repository location
     * 							(a <code>URL</code> used to create an 
     * 							<code>SVNRepository</code> to access the repository)
     * @param revision			a revision of the file
     * @param properties		file revision properties
     * @param propertiesDelta	file properties delta for the <code>revision</code>
     * 							and the one just before the <code>revision</code>
     * @see						SVNRevisionProperty
     */
    public SVNFileRevision(String path, long revision, Map properties, Map propertiesDelta) {
        myPath = path;
        myRevision = revision;
        myProperties = properties;
        myPropertiesDelta = propertiesDelta;
    }
    
    /**
     * Gets the file path (relative to a repository location - the <code>URL</code>
     * used to create an <code>SVNRepository</code> to access the repository).
     *  
     * @return	the path of the file
     */
    public String getPath() {
        return myPath;
    }
    
    /**
     * Gets revision properties of the file.
     * 
     * @return	a <code>Map</code> which keys are revision property names and values
     * 			are their values (both are strings)
     */
    public Map getProperties() {
        return myProperties;
    }
    
    /**
     * Gets a file properties delta between the current file revision and the privious
     * one. If there's no such - an empty <code>Map</code>.
     * 
     * @return		a <code>Map</code>  where each key is a versioned
     * 				file property name and the value for the key is a delta between the 
     * 				current revision property value and the property value of the previous
     * 				revision 
     */
    public Map getPropertiesDelta() {
        return myPropertiesDelta;
    }
    
    /**
     * Gets the file revision.
     *  
     * @return	the revision number of the file
     */
    public long getRevision() {
        return myRevision;
    }

    public int compareTo(Object o) {
        if (o == null || o.getClass() != SVNFileRevision.class) {
            return 1;
        }
        SVNFileRevision rev = (SVNFileRevision) o;
        long number = rev.getRevision();
        return myRevision == number ? 0 : myRevision > number ? 1 : -1;
    }


    private Map myProperties;
    private Map myPropertiesDelta;

}
