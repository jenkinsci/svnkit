/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svnlook;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookPropListCommand extends SVNLookCommand {

    protected SVNLookPropListCommand() {
        super("proplist", new String[] { "plist", "pl" });
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        options.add(SVNLookOption.VERBOSE);
        options.add(SVNLookOption.REVPROP);
        options.add(SVNLookOption.XML);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        if (!environment.isRevProp() && environment.getFirstArgument() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                    "Missing repository path argument");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        
        SVNLookClient client = environment.getClientManager().getLookClient();
        SVNProperties props = null;
        if (environment.isRevision()) {
            if (environment.isRevProp()) {
                props = client.doGetRevisionProperties(environment.getRepositoryFile(), 
                        getRevisionObject());
            } else {
                props = client.doGetProperties(environment.getRepositoryFile(), environment.getFirstArgument(), 
                        getRevisionObject());
            }
        } else {
            if (environment.isRevProp()) {
                props = client.doGetRevisionProperties(environment.getRepositoryFile(), 
                        environment.getTransaction());
            } else {
                props = client.doGetProperties(environment.getRepositoryFile(), environment.getFirstArgument(), 
                        environment.getTransaction());
            }
        }

        if (props != null) {
            for (Iterator propNamesIter = props.nameSet().iterator(); propNamesIter.hasNext();) {
                String propName = (String) propNamesIter.next();
                SVNPropertyData propData = new SVNPropertyData(propName, props.getSVNPropertyValue(propName), 
                        client.getOptions());
                SVNPropertyValue propValue = propData.getValue();
                if (environment.isVerbose()) {
                    environment.getOut().print("  " + propName + " : ");
                    if (propValue.isString()) {
                        environment.getOut().println(propValue.getString());
                    } else {
                        environment.getOut().println(SVNPropertyValue.getPropertyAsString(propValue));
                    }
                } else {
                    environment.getOut().println("  " + propName);
                }
            }
        }
    }

}
