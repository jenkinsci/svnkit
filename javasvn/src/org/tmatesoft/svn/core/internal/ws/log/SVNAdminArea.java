/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class SVNAdminArea implements ISVNAdminArea {
    
    private static final String DOT_SVN = ".svn";
    private static final String LOCK = "lock";
    
    private ISVNAdminArea myParent;
    private ISVNAdminArea myOwner;    
    private ISVNAdminArea myWorkingCopy;
    private File myRoot;
    
    private Map myChildren;
    
    public SVNAdminArea(File root, ISVNAdminArea parent, ISVNAdminArea owner) {
        myRoot = root;
    }

    public void apply(ISVNAdminArea copy) {
    }

    public void delete() {
    }

    public void dispose() {
        if (myWorkingCopy != null) {
            myWorkingCopy.dispose();
            myWorkingCopy = null;
        }
        if (myChildren != null) {
            myChildren = null;
        }
    }

    public void lock() throws IOException {
        new File(getAdminRoot(), LOCK).createNewFile();
    }

    public boolean isLocked() {
        return new File(getAdminRoot(), LOCK).exists();
    }

    public void unlock() {
        new File(getAdminRoot(), LOCK).delete();
    }

    public ISVNLogHandler getLogHandler() {
        return null;
    }

    public ISVNEntries getEntries() {
        return null;
    }

    public void runLog(ISVNLogHandler handler) {
    }

    public String[] getChildNames() {
        File[] children = myRoot.listFiles();
        if (children == null || children.length == 0) {
            return new String[0];
        }
        Collection childNames = new ArrayList(children.length);
        for (int i = 0; i < children.length; i++) {
            if (children[i].exists() && children[i].isDirectory()) {
                if (!DOT_SVN.equals(children[i].getName())) {
                    childNames.add(children[i].getName());
                }
            }
        }
        return (String[]) childNames.toArray(new String[childNames.size()]);
    }
    
    public File getTextFile(String name) {
        return new File(myRoot, name);
    }

    public File getBaseFile(String name) {
        return new File(myRoot, "text-base" + File.separator + name);
    }

    public File getTmpBaseFile(String name) {
        return new File(myRoot, "tmp" + File.separator + "text-base" + File.separator + name);
    }

    public ISVNAdminArea getChild(String name) {
        if (myChildren != null && myChildren.containsKey(name)) {
            return (ISVNAdminArea) myChildren.get(name);
        }
        if (myChildren == null) {
            myChildren = new HashMap();            
        }
        File child = new File(myRoot, name);
        ISVNAdminArea area = null;
        if (child.exists() && child.isDirectory()) {
            area = new SVNAdminArea(child, this, null);
            myChildren.put(name, area);
        }        
        return area;
    }

    public ISVNAdminArea getWorkingCopy() {
        if (myOwner != null) {
            return null;
        }
        if (myWorkingCopy == null) {
            myWorkingCopy = new SVNAdminArea(myRoot, myParent, this);
        }
        return myWorkingCopy;
    }

    public ISVNAdminArea getParent() {
        return myParent;
    }

    public ISVNAdminArea getOwner() {
        return myOwner;
    }
    
    private File getAdminRoot() {
        File root = new File(myRoot, DOT_SVN);
        if (myOwner != null) {
            root = new File(root, "tmp");
        }
        return root;
    }
}
