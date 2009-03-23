/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTreeConflictUtil {
    public static int getTreeConflictIndex(List conflicts, File path) {
        for (int i = 0; i < conflicts.size(); i++) {
            SVNTreeConflictDescription conflict = (SVNTreeConflictDescription) conflicts.get(i);
            File conflictPath = conflict.getPath().getAbsoluteFile();
            if (conflictPath.equals(path)) {
                return i;
            }
        }
        return -1;
    }

    public static List readTreeConflicts(File dirPath, String conflictData) throws SVNException {
        if (conflictData == null) {
            return new ArrayList();
        }
        
        byte[] data;
        try {
            data = conflictData.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            data = conflictData.getBytes();
        }
        return readTreeConflicts(dirPath, data);
    }

    public static List readTreeConflicts(File dirPath, byte[] conflictData) throws SVNException {
        List conflicts = new ArrayList();
        if (conflictData == null) {
            return conflicts;
        }
        SVNSkel skel = SVNSkel.parse(conflictData);
        if (skel == null || skel.isAtom()) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Error parsing tree conflict skel");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        for (Iterator iterator = skel.getList().iterator(); iterator.hasNext();) {
            SVNSkel conflictSkel = (SVNSkel) iterator.next();
            SVNTreeConflictDescription conflict = readSingleTreeConflict(conflictSkel, dirPath);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }
        return conflicts;
    }

    private static SVNTreeConflictDescription readSingleTreeConflict(SVNSkel skel, File dirPath) throws SVNException {
        if (!isValidConflict(skel)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid conflict info in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        if (skel.getChild(1).getData().length == 0) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Empty \'victim\' field in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        String victimBasename = skel.getChild(1).getValue();

        SVNNodeKind kind = getNodeKind(skel.getChild(2).getValue());
        if (kind != SVNNodeKind.FILE && kind != SVNNodeKind.DIR) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid \'node_kind\' field in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }

        SVNOperation operation = getOperation(skel.getChild(3).getValue());
        SVNConflictAction action = getAction(skel.getChild(4).getValue());
        SVNConflictReason reason = getConflictReason(skel.getChild(5).getValue());
        SVNConflictVersion srcLeftVersion = readConflictVersion(skel.getChild(6));
        SVNConflictVersion srcRightVersion = readConflictVersion(skel.getChild(7));

        return new SVNTreeConflictDescription(new File(dirPath, victimBasename), kind, action, reason, operation, srcLeftVersion, srcRightVersion);
    }

    private static SVNConflictVersion readConflictVersion(SVNSkel skel) throws SVNException {
        if (!isValidVersionInfo(skel)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid version info in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        String repoURLString = skel.getChild(1).getValue();
        SVNURL repoURL = repoURLString.length() == 0 ? null : SVNURL.parseURIEncoded(repoURLString);
        long pegRevision = Long.parseLong(skel.getChild(2).getValue());
        String path = skel.getChild(3).getValue();
        path = path.length() == 0 ? null : path;
        SVNNodeKind kind = getNodeKind(skel.getChild(4).getValue());
        return new SVNConflictVersion(repoURL, path, pegRevision, kind);
    }

    private static boolean isValidVersionInfo(SVNSkel skel) throws SVNException {
        if (skel.getListSize() != 5 || !skel.getChild(0).contentEquals("version")) {
            return false;
        }
        return skel.containsAtomsOnly();
    }

    private static boolean isValidConflict(SVNSkel skel) throws SVNException {
        if (skel.getListSize() != 8 || !skel.getChild(0).contentEquals("conflict")) {
            return false;
        }
        for (int i = 1; i < 6; i++) {
            SVNSkel element = skel.getChild(i);
            if (!element.isAtom()) {
                return false;
            }
        }
        return isValidVersionInfo(skel.getChild(6)) && isValidVersionInfo(skel.getChild(7));
    }

    public static String getTreeConflictData(List conflicts) throws SVNException {
        byte[] rawData = getTreeConflictRawData(conflicts);
        String conflictsData;
        try {
            conflictsData = new String(rawData, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            conflictsData = new String(rawData);
        }
        return conflictsData;
    }

    public static byte[] getTreeConflictRawData(List conflicts) throws SVNException {
        SVNConflictVersion nullVersion = new SVNConflictVersion(null, null, SVNRepository.INVALID_REVISION, SVNNodeKind.UNKNOWN);
        SVNSkel skel = SVNSkel.createEmptyList();
        for (int i = conflicts.size() - 1; i >= 0; i--) {
            SVNTreeConflictDescription conflict = (SVNTreeConflictDescription) conflicts.get(i);
            SVNSkel conflictSkel = SVNSkel.createEmptyList();

            SVNConflictVersion sourceRightVersion = conflict.getSourceRightVersion();
            sourceRightVersion = sourceRightVersion == null ? nullVersion : sourceRightVersion;
            prependVersionInfo(conflictSkel, sourceRightVersion);

            SVNConflictVersion sourceLeftVersion = conflict.getSourceRightVersion();
            sourceLeftVersion = sourceLeftVersion == null ? nullVersion : sourceLeftVersion;
            prependVersionInfo(conflictSkel, sourceLeftVersion);

            conflictSkel.addChild(SVNSkel.createAtom(conflict.getConflictReason().toString()));
            conflictSkel.addChild(SVNSkel.createAtom(conflict.getConflictAction().toString()));
            conflictSkel.addChild(SVNSkel.createAtom(conflict.getOperation().toString()));

            if (conflict.getNodeKind() != SVNNodeKind.DIR && conflict.getNodeKind() != SVNNodeKind.FILE) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid \'node_kind\' field in tree conflict description");
                SVNErrorManager.error(error, SVNLogType.WC);
            }
            conflictSkel.addChild(SVNSkel.createAtom(conflict.getNodeKind().toString()));

            String path = conflict.getPath().getName();
            if (path.length() == 0) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Empty path basename in tree conflict description");
                SVNErrorManager.error(error, SVNLogType.WC);
            }
            conflictSkel.addChild(SVNSkel.createAtom(path));
            conflictSkel.addChild(SVNSkel.createAtom("conflict"));

            if (!isValidConflict(conflictSkel)) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Failed to create valid conflict description skel: ''{0}''", skel.toString());
                SVNErrorManager.error(error, SVNLogType.WC);
            }
            skel.addChild(conflictSkel);
        }
        return skel.unparse();
    }

    private static SVNSkel prependVersionInfo(SVNSkel parent, SVNConflictVersion versionInfo) throws SVNException {
        parent = parent == null ? SVNSkel.createEmptyList() : parent;
        SVNSkel skel = SVNSkel.createEmptyList();
        skel.addChild(SVNSkel.createAtom(versionInfo.getKind().toString()));
        String path = versionInfo.getPath() == null ? "" : versionInfo.getPath();
        skel.addChild(SVNSkel.createAtom(path));
        skel.addChild(SVNSkel.createAtom(String.valueOf(versionInfo.getPegRevision())));
        String repoURLString = versionInfo.getRepositoryRoot() == null ? "" : versionInfo.getRepositoryRoot().toString();
        skel.addChild(SVNSkel.createAtom(repoURLString));
        skel.addChild(SVNSkel.createAtom("version"));
        if (!isValidVersionInfo(skel)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Failed to create valid conflict version skel: ''{0}''", skel.toString());
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        return parent;
    }

    private static SVNNodeKind getNodeKind(String name) throws SVNException {
        if ("".equals(name)) {
            return SVNNodeKind.UNKNOWN;
        }
        SVNNodeKind kind = SVNNodeKind.parseKind(name);
        if (kind == SVNNodeKind.UNKNOWN) {
            mappingError("node kind");
        }
        return kind;
    }

    private static SVNOperation getOperation(String name) throws SVNException {
        SVNOperation operation = SVNOperation.fromString(name);
        if (operation == null) {
            mappingError("operation");
        }
        return operation;
    }

    private static SVNConflictAction getAction(String name) throws SVNException {
        SVNConflictAction action = SVNConflictAction.fromString(name);
        if (action == null) {
            mappingError("conflict action");
        }
        return action;
    }

    private static SVNConflictReason getConflictReason(String name) throws SVNException {
        SVNConflictReason reason;
        if (SVNConflictReason.UNVERSIONED.getName().equals(name)) {
            reason = null;
        } else {
            reason = SVNConflictReason.fromString(name);
        }
        if (reason == null) {
            mappingError("conflict reason");
        }
        return reason;
    }

    private static void mappingError(String type) throws SVNException {
        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unknown {0} value in tree conflict description", type);
        SVNErrorManager.error(error, SVNLogType.WC);
    }
}
