/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNAdminAreaInfo {
    
    private String myTargetName;
    private SVNAdminArea myTarget;
    private SVNAdminArea myAnchor;
    private SVNWCAccess myAccess;
    private Map myExternals;

    protected SVNAdminAreaInfo(SVNWCAccess access, SVNAdminArea anchor, SVNAdminArea target, String targetName) {
        myAccess = access;
        myAnchor = anchor;
        myTarget = target;
        myTargetName = targetName;
    }
    
    public SVNAdminArea getAnchor() {
        return myAnchor;
    }
    
    public SVNAdminArea getTarget() {
        return myTarget;
    }
    
    public String getTargetName() {
        return myTargetName;
    }

    public SVNWCAccess getWCAccess() {
        return myAccess;
    }
    
    public SVNExternalInfo[] addExternals(SVNAdminArea adminArea, String externals) {
        if (externals == null) {
            return null;
        }
        Collection result = new ArrayList();
        
        String relPath = adminArea.getRelativePath(myAnchor);
        SVNExternalInfo[] parsed = parseExternals(relPath, externals);
        for (int i = 0; i < parsed.length; i++) {
            SVNExternalInfo info = addExternal(adminArea, parsed[i].getPath(), parsed[i].getOldURL(), parsed[i].getOldRevision());
            result.add(info);
        }
        // get existing externals and update all that are not in result but in
        // this dir.
        for (Iterator exts = externals(); exts.hasNext();) {
            SVNExternalInfo info = (SVNExternalInfo) exts.next();
            if (!result.contains(info) && relPath.equals(info.getOwnerPath())) {
                info.setNewExternal(null, -1);
            }
        }
        return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
    }

    public static SVNExternalInfo[] parseExternals(String rootPath, String externals) {
        Collection result = new ArrayList();
        if (externals == null) {
            return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
        }

        for (StringTokenizer lines = new StringTokenizer(externals, "\n\r"); lines.hasMoreTokens();) {
            String line = lines.nextToken().trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String url = null;
            String path;
            long rev = -1;
            List parts = new ArrayList(4);
            for (StringTokenizer tokens = new StringTokenizer(line, " \t"); tokens
                    .hasMoreTokens();) {
                String token = tokens.nextToken().trim();
                parts.add(token);
            }
            if (parts.size() < 2) {
                continue;
            }
            path = SVNPathUtil.append(rootPath, (String) parts.get(0));
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (parts.size() == 2) {
                url = (String) parts.get(1);
            } else if (parts.size() == 3 && parts.get(1).toString().startsWith("-r")) {
                String revStr = parts.get(1).toString();
                revStr = revStr.substring("-r".length());
                if (!"HEAD".equals(revStr)) {
                    try {
                        rev = Long.parseLong(revStr);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                }
                url = (String) parts.get(2);
            } else if (parts.size() == 4 && "-r".equals(parts.get(1))) {
                String revStr = parts.get(2).toString();
                if (!"HEAD".equals(revStr)) {
                    try {
                        rev = Long.parseLong(revStr);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                }
                url = (String) parts.get(3);
            }
            if (path != null && url != null) {
                if ("".equals(rootPath) && ((String) parts.get(0)).startsWith("/")) {
                    path = "/" + path;
                }
                try {
                    url = SVNURL.parseURIEncoded(url).toString();
                } catch (SVNException e) {
                    continue;
                }
                
                try {
                    SVNExternalInfo info = new SVNExternalInfo("", null, path, SVNURL.parseURIEncoded(url), rev);
                    result.add(info);
                } catch (SVNException e) {
                }
            }
        }
        return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
    }

    private SVNExternalInfo addExternal(SVNAdminArea adminArea, String path, SVNURL url, long revision) {
        if (myExternals == null) {
            myExternals = new TreeMap();
        }

        SVNExternalInfo info = (SVNExternalInfo) myExternals.get(path);
        if (info == null) {
            info = new SVNExternalInfo(adminArea.getRelativePath(myAnchor), new File(getAnchor().getRoot(), path), path, null, -1);
            myExternals.put(path, info);
        } 
        info.setNewExternal(url, revision);
        return info;
    }

    public Iterator externals() {
        if (myExternals == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        return myExternals.values().iterator();
    }
    
}
