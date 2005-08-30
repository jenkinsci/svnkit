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
package org.tmatesoft.svn.core.internal.io.dav.commons;

import org.apache.commons.httpclient.methods.EntityEnclosingMethod;

public class CommonsHTTPMethod extends EntityEnclosingMethod {

    String myName;

    public CommonsHTTPMethod(String name, String uri) {
        super(uri);
        myName = name;
    }

    public String getName() {
        return myName;
    }
    
    
}