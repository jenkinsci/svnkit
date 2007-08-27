/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVDatedRevisionRequest extends DAVReportRequest {

    Date myDate;

    protected DAVDatedRevisionRequest(Map properties) throws SVNException {
        super(DATED_REVISIONS_REPORT, properties);

        myDate = null;

        initialize();
    }

    public Date getDate() {
        return myDate;
    }

    private void setDate(Date date) {
        myDate = date;
    }

    protected void initialize() throws SVNException {
        String dateString = getProperty(getProperties(), DAVElement.CREATION_DATE).getFirstValue();
        setDate(SVNTimeUtil.parseDate(dateString));
    }
}
