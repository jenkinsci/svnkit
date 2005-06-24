/*
 * Created on 31.05.2005
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;

import java.io.File;

public class SVNWCUtil {

    public static String getURL(File versionedFile) {
        SVNWCAccess wcAccess;
        try {
            wcAccess = SVNWCAccess.create(versionedFile);
            return wcAccess.getTargetEntryProperty(SVNProperty.URL);
        } catch (SVNException e) {
            //
        }
        return null;
    }

    public static File getDefaultConfigurationDirectory() {
        if (SVNFileUtil.isWindows) {
            return new File(System.getProperty("user.home"), "Application Data/Subversion");
        }
        return new File(System.getProperty("user.home"), ".subversion");
    }

    public static ISVNOptions createDefaultOptions(File dir, boolean readonly) {
        return new SVNOptions(dir, !readonly);
    }

    public static ISVNOptions createDefaultOptions(boolean readonly) {
        return new SVNOptions(null, !readonly);
    }

    public static boolean isVersionedDirectory(File dir) {
        return SVNWCAccess.isVersionedDirectory(dir);
    }

    public static boolean isBinaryMimetype(String mimetype) {
        return mimetype != null && !mimetype.startsWith("text/");
    }

    public static boolean isWorkingCopyRoot(final File versionedDir, final boolean considerExternalAsRoot) {
        if (versionedDir == null || !SVNWCAccess.isVersionedDirectory(versionedDir)) {
            // unversioned.
            return false;
        }
        // call status of parent instead:
        if (versionedDir.getParentFile() == null) {
            return true;
        }
        SVNStatusClient stClient = new SVNStatusClient(null, null);
        final boolean[] isRoot = new boolean[] {true}; // if no status is reporter, this may be ignored dir in parent's folder.
        try {
            stClient.doStatus(versionedDir.getParentFile(), false, false, true, true, new ISVNStatusHandler() {
                public void handleStatus(SVNStatus status) {
                    if (versionedDir.equals(status.getFile())) {
                        isRoot[0] = false;
                        if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED ||
                                status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                                status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL) {
                            if (status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL && !considerExternalAsRoot) {
                                return;
                            }
                            isRoot[0] = true;
                        } 
                    }
                }
            });
        } catch (SVNException e) {
            return true;
        }
        return isRoot[0];
    }

    public static File getWorkingCopyRoot(File versionedDir, boolean stopOnExtenrals) {
        versionedDir = versionedDir.getAbsoluteFile();
        if (versionedDir == null ||
                (!SVNWCAccess.isVersionedDirectory(versionedDir) && !SVNWCAccess.isVersionedDirectory(versionedDir.getParentFile()))) {
            // both this dir and its parent are not versioned.
            return null;
        }

        if (isWorkingCopyRoot(versionedDir, true)) {
            // this is root.
            if (stopOnExtenrals) {
                return versionedDir;
            }
            File parent = versionedDir.getParentFile();
            File parentRoot = getWorkingCopyRoot(parent, stopOnExtenrals);
            if (parentRoot == null) {
                // if parent is not versioned return this dir.
                return versionedDir;
            }
            // parent is versioned. we have to check if it contains externals definition for this dir.

            while(parent != null) {
                try {
                    SVNWCAccess parentAccess = SVNWCAccess.create(parent);
                    SVNProperties props = parentAccess.getTarget().getProperties("", false);
                    SVNExternalInfo[] externals = SVNWCAccess.parseExternals("", props.getPropertyValue(SVNProperty.EXTERNALS));
                    // now externals could point to our dir.
                    for (int i = 0; i < externals.length; i++) {
                        SVNExternalInfo external = externals[i];
                        File externalFile = new File(parent, external.getPath());
                        if (externalFile.equals(versionedDir)) {
                            return parentRoot;
                        }
                    }
                } catch (SVNException e) {
                    //
                }
                if (parent.equals(parentRoot)) {
                    break;
                }
                parent = parent.getParentFile();
            }
            return versionedDir;
        } else {
            // if dir is not a root -> just recurse till root, the call get root again.
            if (versionedDir.getParentFile() != null) {
                return getWorkingCopyRoot(versionedDir.getParentFile(), stopOnExtenrals);
            }
            return versionedDir;
        }
    }
}