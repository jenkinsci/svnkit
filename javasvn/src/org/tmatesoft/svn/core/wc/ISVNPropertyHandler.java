/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * The <b>ISVNPropertyHandler</b> interface should be implemented
 * to be further provided to <b>SVNWCClient</b>'s property managing
 * methods for handling properties. Those methods that receive a 
 * developer's property handler as a parameter call one of the handler's
 * <b>handleProperty()</b> methods on every successful setting or
 * getting a property. 
 * 
 * <p>
 * If the aim is to get a property (the following behaviour is the same for versioned and unversioned):
 * <ul><li>
 * the property obtained is wrapped into an <b>SVNPropertyData</b>
 * object and passed to an appropriate <b>handleProperty()</b> method   
 * where it's up to a developer how to process it.    
 * </li></ul>
 * If the aim is to set a property (the following behaviour is the same for versioned and unversioned):
 * <ul><li>
 * the property successfully set is also wrapped into an <b>SVNPropertyData</b>
 * object and passed to an appropriate <b>handleProperty()</b> method   
 * to notify a developer.    
 * </li></ul>
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNPropertyHandler {

    public static ISVNPropertyHandler NULL = new ISVNPropertyHandler() {
        public void handleProperty(File path, SVNPropertyData property) {
        }

        public void handleProperty(SVNURL url, SVNPropertyData property) {
        }

        public void handleProperty(long revision, SVNPropertyData property) {
        }
    };

    public void handleProperty(File path, SVNPropertyData property) throws SVNException;

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException;

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException;
}
