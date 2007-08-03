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
package org.tmatesoft.svn.cli2.command;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.cli2.SVNXMLCommand;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNPropGetCommand extends SVNXMLCommand {

    public SVNPropGetCommand() {
        super("propget", new String[] {"pget", "pg"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new ArrayList();
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.REVISION);
        options.add(SVNOption.REVPROP);
        options.add(SVNOption.STRICT);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.XML);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        String propertyName = getEnvironment().popArgument();
        if (propertyName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err);
        }

        Collection targets = new ArrayList(); 
        if (getEnvironment().getChangelist() != null) {
            getEnvironment().setOperatingPath("", new File("").getAbsoluteFile());
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(getEnvironment().getOperatingFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        targets = getEnvironment().combineTargets(targets);
        if (targets.isEmpty()) {
            targets.add("");
        }

        if (getEnvironment().isRevprop()) {
            SVNURL url = getRevpropURL(getEnvironment().getStartRevision(), targets);
            SVNWCClient wcClient = getEnvironment().getClientManager().getWCClient();
            final SVNPropertyData[] propertyValue = new SVNPropertyData[1]; 
            long rev = wcClient.doGetRevisionProperty(url, propertyName, getEnvironment().getStartRevision(), new ISVNPropertyHandler() {
                public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                }
                public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                }
                public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                    propertyValue[0] = property;
                }
            });
            if (propertyValue[0] != null) {
                if (getEnvironment().isXML()) {
                    printXMLHeader("properties");
                    StringBuffer buffer = openXMLTag("revprops", XML_STYLE_NORMAL, "rev", Long.toString(rev), null);
                    buffer = openXMLTag("property", XML_STYLE_PROTECT_PCDATA, "name", SVNEncodingUtil.xmlEncodeAttr(propertyName), buffer);
                    buffer.append(SVNEncodingUtil.xmlEncodeCDATA(propertyValue[0].getValue()));
                    buffer = closeXMLTag("property", buffer);
                    buffer = closeXMLTag("revprops", buffer);
                    getEnvironment().getOut().print(buffer);
                    printXMLFooter("properties");
                } else {
                    getEnvironment().getOut().print(propertyValue[0].getValue());
                    if (!getEnvironment().isStrict()) {
                        getEnvironment().getOut().println();
                    }
                }
            }
        } else {
            
        }
    }
    
    protected SVNURL getRevpropURL(SVNRevision revision, Collection targets) throws SVNException {
        if (revision != SVNRevision.HEAD && revision.getDate() == null && revision.getNumber() < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Must specify revision as a number, a date or 'HEAD' when operating on revision property");
            SVNErrorManager.error(err);
        }
        if (targets.size() != 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                "Wrong number of targets specified");
            SVNErrorManager.error(err);
        }
        String target = (String) targets.iterator().next();
        return getEnvironment().getURLFromTarget(target);
    }

}
