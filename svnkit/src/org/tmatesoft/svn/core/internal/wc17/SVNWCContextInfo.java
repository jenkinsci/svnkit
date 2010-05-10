/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.Map;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWCContextInfo {

    private String targetName;
    private Map newExternals;
    private String anchorAbsPath;
    private String dirAbsPath;
    private String targetBaseName;
    private String dir;
    private String targetAbsPath;
    private File targetAbsFile;
    private String anchorRelPath;

    public String getAnchorAbsPath() {
        return anchorAbsPath;
    }

    public String getAnchorRelPath() {
        return anchorRelPath;
    }

    public String getDir() {
        return dir;
    }

    public String getDirAbsPath() {
        return dirAbsPath;
    }

    public Map getNewExternals() {
        return newExternals;
    }

    public File getTargetAbsFile() {
        return targetAbsFile;
    }

    public String getTargetAbsPath() {
        return targetAbsPath;
    }

    public String getTargetBaseName() {
        return targetBaseName;
    }

    public String getTargetName() {
        return targetName;
    }
    
    public void setAnchorAbsPath(String anchorAbsPath) {
        this.anchorAbsPath = anchorAbsPath;
    }
    
    public void setAnchorRelPath(String anchorRelPath) {
        this.anchorRelPath = anchorRelPath;
    }
    
    public void setDir(String dir) {
        this.dir = dir;
    }

    public void setDirAbsPath(String dirAbsPath) {
        this.dirAbsPath = dirAbsPath;        
    }

    public void setTargetAbsPath(String targetAbsPath) {
        this.targetAbsPath = targetAbsPath;
        this.targetAbsFile = new File(targetAbsPath);
    }

    public void setTargetBaseName(String targetBaseName) {
        this.targetBaseName = targetBaseName;
    }

}
