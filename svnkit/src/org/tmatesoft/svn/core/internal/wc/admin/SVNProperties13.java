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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNProperties13 extends SVNVersionedProperties {

    public SVNProperties13(Map properties) {
        super(properties);
    }

    public boolean containsProperty(String name) throws SVNException {
        if (!isEmpty()) {
            Map props = loadProperties();
            return props.containsKey(name);
        }
        return false;
    }

    public String getPropertyValue(String name) throws SVNException {
        if (getPropertiesMap() != null && getPropertiesMap().containsKey(name)) {
            return (String) getPropertiesMap().get(name);
        }
        if (!isEmpty()) {
            Map props = loadProperties();
            return (String)props.get(name); 
        }
        return null;
    }

    protected Map loadProperties() throws SVNException {
        Map props = getPropertiesMap();
        if (props == null) {
            props = new HashMap();
            setPropertiesMap(props);
        }
        return props;
    }

    protected SVNVersionedProperties wrap(Map properties) {
        return new SVNProperties13(properties);
    }
}
