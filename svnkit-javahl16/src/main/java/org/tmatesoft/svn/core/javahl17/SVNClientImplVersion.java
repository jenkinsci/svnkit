package org.tmatesoft.svn.core.javahl17;

import org.apache.subversion.javahl.types.Version;

class SVNClientImplVersion extends Version {

    //TODO: Version class contains more methods, not all of them are present in org.tmatesoft.svn.util.Version

    private static SVNClientImplVersion instance;

    public int getMajor() {
        return SVNClientImpl.versionMajor();
    }

    public int getMinor() {
        return SVNClientImpl.versionMinor();
    }

    public int getPatch() {
        return SVNClientImpl.versionMicro();
    }

    public long getRevisionNumber() {
        return SVNClientImpl.versionRevisionNumber();
    }

    public String toString() {
        String revision = getRevisionNumber() < 0 ? org.tmatesoft.svn.util.Version.getRevisionString() : Long.toString(getRevisionNumber());
        return "SVNKit v" + getMajor() + "." + getMinor() + "." + getPatch() + "." + revision;
    }

    public static Version getInstance() {
        if (instance == null) {
            instance = new SVNClientImplVersion();
        }
        return instance;
    }

}