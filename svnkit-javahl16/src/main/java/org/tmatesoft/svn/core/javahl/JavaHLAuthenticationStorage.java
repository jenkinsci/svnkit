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
package org.tmatesoft.svn.core.javahl;

import java.util.Hashtable;
import java.util.Map;

import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorage;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class JavaHLAuthenticationStorage implements ISVNAuthenticationStorage {

    private Map myStorage = new Hashtable();

    public void putData(String kind, String realm, Object data) {
        myStorage.put(kind + "$" + realm, data);
    }

    public Object getData(String kind, String realm) {
        return myStorage.get(kind + "$" + realm);
    }
    
    public void clear() {
        myStorage.clear();
    }

}
