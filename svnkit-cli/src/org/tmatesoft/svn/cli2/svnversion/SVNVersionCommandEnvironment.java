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
package org.tmatesoft.svn.cli2.svnversion;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Iterator;

import org.tmatesoft.svn.cli2.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli2.AbstractSVNOption;
import org.tmatesoft.svn.cli2.SVNCommandLine;
import org.tmatesoft.svn.cli2.SVNOptionValue;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNVersionCommandEnvironment extends AbstractSVNCommandEnvironment {

    private boolean myIsHelp;
    private boolean myIsVersion;
    private boolean myIsCommitted;
    private boolean myIsNoNewLine;

    public SVNVersionCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
    }
    
    public boolean isHelp() {
        return myIsHelp;
    }
    
    public boolean isVersion() {
        return myIsVersion;
    }
    
    public boolean isCommitted() {
        return myIsCommitted;
    }
    
    public boolean isNoNewLine() {
        return myIsNoNewLine;
    }

    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        return SVNWCUtil.createDefaultAuthenticationManager();
    }

    protected ISVNOptions createClientOptions() {
        return SVNWCUtil.createDefaultOptions(true);
    }

    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        AbstractSVNOption option = optionValue.getOption();
        if (option == SVNVersionOption.COMMITTED) {
            myIsCommitted = true;
        } else if (option == SVNVersionOption.NO_NEWLINE) {
            myIsNoNewLine = true;
        } else if (option == SVNVersionOption.HELP) {
            myIsHelp = true;
        } else if (option == SVNVersionOption.VERSION) {
            myIsVersion = true;
        }
    }

    protected String refineCommandName(String commandName, SVNCommandLine commandLine) throws SVNException {
        for (Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            AbstractSVNOption option = optionValue.getOption();
            if (option == SVNVersionOption.HELP) {
                myIsHelp = true;                
            } else if (option == SVNVersionOption.VERSION) {
                myIsVersion = true;
            }
        }
        
        if (myIsHelp || myIsVersion) {
            return "help";
        }
        return "";
    }
}
