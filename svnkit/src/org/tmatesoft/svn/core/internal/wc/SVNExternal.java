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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNExternal {
    
    private SVNRevision myRevision;
    private SVNRevision myPegRevision;
    private SVNURL myURL;
    private String myPath;

    private SVNExternal() {
        myRevision = SVNRevision.UNDEFINED;
        myPegRevision = SVNRevision.UNDEFINED;
    }
    
    public SVNRevision getRevision() {
        return myRevision;
    }
    
    public SVNRevision getPegRevision() {
        return myPegRevision;
    }
    
    public SVNURL getURL() {
        return myURL;
    }
    
    public String getPath() {
        return myPath;
    }

    public static SVNExternal[] parseExternals(String owner, String description) throws SVNException {
        String[] lines = description.split("\r\n");
        Collection externals = new ArrayList();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if ("".equals(line) || line.startsWith("#")) {
                continue;
            }
            String[] lineParts = line.split(" \t");
            List tokens = new ArrayList(Arrays.asList(lineParts));
            if (tokens.size() < 2 || tokens.size() > 4) {
                reportParsingError(owner, line);
            }
            SVNExternal external = new SVNExternal();
            int revisionToken = fetchRevision(external, owner, line, tokens);
            String token0 = (String) tokens.get(0);
            String token1 = (String) tokens.get(1);
            boolean token0isURL = SVNPathUtil.isURL(token0); 
            boolean token1isURL = SVNPathUtil.isURL(token1);
            
            if (revisionToken == 0 || token0isURL || token1isURL) {
                external.myPath = token1;
                SVNPath path = new SVNPath(token0, true);
                external.myURL = path.getURL();
                external.myPegRevision = path.getPegRevision();
            } else {
                external.myPath = token0;
                external.myURL = SVNURL.parseURIEncoded(token1);
                external.myPegRevision = external.myRevision;
            }
            if (external.myPegRevision == SVNRevision.UNDEFINED) {
                external.myPegRevision = SVNRevision.HEAD;
            }
            if (external.myRevision == SVNRevision.UNDEFINED) {
                external.myRevision = external.myPegRevision;
            }
            external.myPath = SVNPathUtil.canonicalizePath(external.myPath.replace(File.separatorChar, '/'));
            if (external.myPath.length() == 0 || external.myPath.startsWith("/") || external.myPath.indexOf("/../") > 0 || external.myPath.endsWith("/..")) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_EXTERNALS_DESCRIPTION,
                        "Invalid {0} property on ''{1}'': target ''{2}'' is an absolute path or involves ''..''", new Object[] {SVNProperty.EXTERNALS, owner, external.myPath});
                SVNErrorManager.error(err);
            }
            externals.add(external);
        }
        return (SVNExternal[]) externals.toArray(new SVNExternal[externals.size()]);
    }
    
    private static int fetchRevision(SVNExternal external, String owner, String line, List tokens) throws SVNException {
        for (int i = 0; i < tokens.size() && i < 2; i++) {
            String token = (String) tokens.get(i);
            String revisionStr = null;
            if (token.length() >= 2 && 
                    token.charAt(0) == '-' && token.charAt(1) == 'r') {
                if (token.length() == 2 && tokens.size() == 4) {
                    revisionStr = (String) tokens.get(i + 1);
                    // remove separate '-r' token.
                    tokens.remove(i);
                } else if (tokens.size() == 3) {
                    revisionStr = ((String) tokens.get(i)).substring(2); 
                }
                if (revisionStr == null) {
                    reportParsingError(owner, line);
                }
                long revNumber = -1;
                try {
                    revNumber = Long.parseLong(revisionStr);
                } catch (NumberFormatException nfe) {
                    reportParsingError(owner, line);
                }
                external.myRevision = SVNRevision.create(revNumber);
                tokens.remove(i);
                return i;
            }
        }
        if (tokens.size() == 2) {
            return -1;
        }
        reportParsingError(owner, line);
        return -1;
    }
    
    private static void reportParsingError(String owner, String line) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_EXTERNALS_DESCRIPTION,
                "Error parsing {0} property on ''{1}'': ''{2}''", new Object[] {SVNProperty.EXTERNALS, owner, line});
        SVNErrorManager.error(err);
    }

}
