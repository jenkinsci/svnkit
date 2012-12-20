package org.tmatesoft.svn.core.test;

public class GitObjectId {
    private final String objectId;

    public GitObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String asString() {
        return objectId;
    }

    @Override
    public String toString() {
        return asString();
    }
}
