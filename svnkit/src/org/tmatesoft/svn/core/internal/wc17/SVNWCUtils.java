/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.util.Iterator;

import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNDate;


/**
 * @author  TMate Software Ltd.
 */
public class SVNWCUtils {

    public static SVNDate readDate(long date) {
        long time = date / 1000;
        return new SVNDate(time, (int) (date - time * 1000));
    }

    public static SVNProperties propDiffs(SVNProperties targetProps, SVNProperties sourceProps) {
        SVNProperties propdiffs = new SVNProperties();
        for (Iterator i = sourceProps.nameSet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            String propVal1 = sourceProps.getStringValue(key);
            String propVal2 = targetProps.getStringValue(key);
            if (propVal2 == null) {
                SVNPropertyValue p = SVNPropertyValue.create(null);
                propdiffs.put(key, p);
            } else if (!propVal1.equals(propVal2)) {
                SVNPropertyValue p = SVNPropertyValue.create(propVal2);
                propdiffs.put(key, p);
            }
        }
        for (Iterator i = targetProps.nameSet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            String propVal = targetProps.getStringValue(key);
            if (null == sourceProps.getStringValue(key)) {
                SVNPropertyValue p = SVNPropertyValue.create(propVal);
                propdiffs.put(key, p);
            }
        }
        return propdiffs;
    }

}
