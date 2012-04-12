/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.javahl.SVNClientImpl;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class JavaHLPropertyData extends PropertyData {

    private SVNClientImpl myClientImpl;

    JavaHLPropertyData(SVNClientImpl clientImpl, SVNClient cl, String p, String n, String v, byte[] d) {
        super(cl, p, n, v, d);
        myClientImpl = clientImpl;
    }

    public void remove(boolean recurse) throws ClientException {
        if (myClientImpl != null) {
            myClientImpl.propertyRemove(getPath(), getName(), recurse);
        } else {
            super.remove(recurse);
        }
    }

    public void setValue(byte[] newValue, boolean recurse) throws ClientException {
        if (myClientImpl != null) {
            myClientImpl.propertySet(getPath(), getName(), newValue, recurse);
        } else {
            super.setValue(newValue, recurse);
        }
    }

    public void setValue(String newValue, boolean recurse) throws ClientException {
        if (myClientImpl != null) {
            myClientImpl.propertySet(getPath(), getName(), newValue, recurse);
        } else {
            super.setValue(newValue, recurse);
        }
    }
}
