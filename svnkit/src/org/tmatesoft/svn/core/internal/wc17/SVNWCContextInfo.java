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
import java.util.Collections;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNWCContextInfo {

    private String targetName;
    private String anchorAbsPath;
    private String dirAbsPath;
    private String targetBaseName;
    private String dir;
    private String targetAbsPath;
    private File targetAbsFile;
    private String anchorRelPath;

    private Map newExternals;
    private Map oldExternals;
    private Map depths;
    private File dirAbsFile;

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
        this.dirAbsFile = new File(dirAbsPath);
    }

    public File getDirAbsFile() {
        return dirAbsFile;
    }

    public void setTargetAbsPath(String targetAbsPath) {
        this.targetAbsPath = targetAbsPath;
        this.targetAbsFile = new File(targetAbsPath);
    }

    public void setTargetBaseName(String targetBaseName) {
        this.targetBaseName = targetBaseName;
    }

    public void addOldExternal(String path, String oldValue) {
        if (oldExternals == null) {
            oldExternals = new SVNHashMap();
        }
        oldExternals.put(path, oldValue);
    }

    public void addNewExternal(String path, String newValue) {
        if (newExternals == null) {
            newExternals = new SVNHashMap();
        }
        newExternals.put(path, newValue);
    }

    public void addExternal(String path, String oldValue, String newValue) {
        addNewExternal(path, newValue);
        addOldExternal(path, oldValue);
    }

    public void addDepth(String path, SVNDepth depth) {
        if (depths == null) {
            depths = new SVNHashMap();
        }
        depths.put(path, depth);
    }

    public void removeDepth(String path) {
        if (depths != null) {
            depths.remove(path);
        }
    }

    public void removeExternal(String path) {
        if (newExternals != null) {
            newExternals.remove(path);
        }
        if (oldExternals != null) {
            oldExternals.remove(path);
        }
    }

    public Map getNewExternals() {
        return newExternals == null ? Collections.EMPTY_MAP : newExternals;
    }

    public Map getOldExternals() {
        return oldExternals == null ? Collections.EMPTY_MAP : oldExternals;
    }

    public Map getDepths() {
        return depths == null ? Collections.EMPTY_MAP : depths;
    }

}
