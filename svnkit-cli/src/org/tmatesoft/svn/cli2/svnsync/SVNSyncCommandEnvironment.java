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
package org.tmatesoft.svn.cli2.svnsync;

import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli2.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli2.SVNOptionValue;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNSyncCommandEnvironment extends AbstractSVNCommandEnvironment {

    public SVNSyncCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
    }

    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        return null;
    }

    protected ISVNOptions createClientOptions() {
        return null;
    }

    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        
    }

    protected String refineCommandName(String commandName) throws SVNException {
        return null;
    }

}
