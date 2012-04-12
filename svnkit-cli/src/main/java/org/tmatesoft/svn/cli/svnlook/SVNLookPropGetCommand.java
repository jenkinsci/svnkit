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
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookPropGetCommand extends SVNLookCommand {

    protected SVNLookPropGetCommand() {
        super("propget", new String[] { "pget", "pg" });
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        options.add(SVNLookOption.REVPROP);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 

        if (environment.getFirstArgument() == null) {
            SVNErrorMessage err = null;
            if (environment.isRevProp()) {
                err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing propname argument");
            } else {
                err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                        "Missing propname and repository path arguments");
            }
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } else if (!environment.isRevProp() && environment.getSecondArgument() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                    "Missing propname or repository path argument");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        
        String propName = environment.getFirstArgument();
        SVNPropertyValue propValue = null;
        SVNLookClient client = environment.getClientManager().getLookClient();
        if (environment.isRevision()) {
            if (environment.isRevProp()) {
                propValue = client.doGetRevisionProperty(environment.getRepositoryFile(), 
                        propName, getRevisionObject());
            } else {
                propValue = client.doGetProperty(environment.getRepositoryFile(), propName, 
                        environment.getSecondArgument(), getRevisionObject());
            }
        } else {
            if (environment.isRevProp()) {
                propValue = client.doGetRevisionProperty(environment.getRepositoryFile(), 
                        propName, environment.getTransaction());
            } else {
                propValue = client.doGetProperty(environment.getRepositoryFile(), propName, 
                        environment.getSecondArgument(), environment.getTransaction());
            }
        }
        
        if (propValue == null) {
            SVNErrorMessage err = null;
            if (environment.isRevProp()) {
                if (environment.isRevision()) {
                    err = SVNErrorMessage.create(SVNErrorCode.PROPERTY_NOT_FOUND, 
                            "Property ''{0}'' not found on revision {1}", new Object[] { propName, 
                            String.valueOf(environment.getRevision()) } ); 
                } else {
                    err = SVNErrorMessage.create(SVNErrorCode.PROPERTY_NOT_FOUND, 
                            "Property ''{0}'' not found in transaction {1}", new Object[] { propName, 
                            String.valueOf(environment.getTransaction()) } ); 
                }
            } else {
                if (environment.isRevision()) {
                    err = SVNErrorMessage.create(SVNErrorCode.PROPERTY_NOT_FOUND, 
                            "Property ''{0}'' not found on path ''{1}'' in revision {2}", 
                            new Object[] { propName, environment.getSecondArgument(), 
                            SVNRevision.create(environment.getRevision()) } ); 
                } else {
                    err = SVNErrorMessage.create(SVNErrorCode.PROPERTY_NOT_FOUND, 
                            "Property ''{0}'' not found on path ''{1}'' in transaction {2}", 
                            new Object[] { propName, environment.getSecondArgument(),
                            environment.getTransaction() } ); 
                    
                }
            }
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        
        if (propValue.isString()) {
            environment.getOut().print(propValue.getString());
        } else {
            environment.getOut().print(propValue.getBytes());
        }
    }

}
