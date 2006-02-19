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
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.File;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class PropertyHandler implements ISVNPropertyHandler {
    Map myProps;
    
    public PropertyHandler(Map props){
        myProps = props;
    }
    
    public void handleProperty(File path, SVNPropertyData property) throws SVNException{
        myProps.put(property.getName(), property.getValue());
    }
    
    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException{
    }
    
    public void handleProperty(long revision, SVNPropertyData property) throws SVNException{
    }

}
