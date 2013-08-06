package org.apache.subversion.javahl.types;

import org.apache.subversion.javahl.types.NodeKind;

public class JavaHLTypesObjectFactory {
    
    public static ConflictVersion createConflictVersion(
            String reposURL, String reposUUID,
            long pegRevision, String pathInRepos,
            NodeKind nodeKind) {
        
        return new ConflictVersion(reposURL, reposUUID, pegRevision, pathInRepos, nodeKind);
    }
}
