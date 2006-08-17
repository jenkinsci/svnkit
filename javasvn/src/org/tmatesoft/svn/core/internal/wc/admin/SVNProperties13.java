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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;

public class SVNProperties13 extends ISVNProperties {

    public SVNProperties13(Map properties) {
        super(properties);
    }

    public String getPropertyValue(String name) throws SVNException {
        Map props = loadProperties();
        if (!isEmpty()) {
            return (String)props.get(name); 
        }
        return null;
    }

    protected Map loadProperties() throws SVNException {
        if (myProperties == null) {
            myProperties = new HashMap();
        }
        return myProperties;
    }

    protected ISVNProperties wrap(Map properties) {
        return new SVNProperties13(properties);
    }
}
