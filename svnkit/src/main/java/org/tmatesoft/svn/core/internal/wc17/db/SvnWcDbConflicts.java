package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCConflictDescription17;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.DirParsedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.ACTUAL_NODE__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnWcDbConflicts extends SvnWcDbShared {

    private static final String CONFLICT_OP_UPDATE = "update";

    public enum ConflictInfo {
        conflictOperation,
        locations,
        textConflicted,
        propConflicted,
        treeConflicted,
    }
    
    public enum ConflictKind {
        text, prop, tree, reject, obstructed;
    }
    
    public enum PropertyConflictInfo {
        markerAbspath, 
        mineProps,
        theirOldProps,
        theirProps,
        conflictedPropNames,
    }
    
    public enum TextConflictInfo {
        mineAbsPath,
        theirOldAbsPath,
        theirAbsPath,
    }
    
    public enum TreeConflictInfo {
        localChange,
        incomingChange,
        moveSrcOpRootAbsPath,
    }
    
    public enum ConflictStatus {
        conflicted,
        conflictIgnored,
        
        textConflicted,
        propConflicted,
        treeConflicted
    }
    
    public static SVNSkel createConflictSkel() throws SVNException {
        final SVNSkel skel = SVNSkel.createEmptyList();
        skel.prepend(SVNSkel.createEmptyList());
        skel.prepend(SVNSkel.createEmptyList());
        return skel;
    }
    
    public static boolean isConflictSkelComplete(SVNSkel skel) throws SVNException {
        if (skel == null || skel.getListSize() < 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Not a conflict skel");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (skel.first().getListSize() < 2) {
            return false;
        } else if (skel.first().next().getListSize() == 0) {
            return false;
        }
        return true;
    }
    
    public static void prependLocation(SVNSkel skel, SVNConflictVersion location) throws SVNException {
        SVNSkel loc = SVNSkel.createEmptyList();
        if (location == null) {
            skel.prepend(loc);
            return;
        }
        
        loc.prepend(SVNSkel.createAtom(location.getKind().toString()));
        loc.prepend(SVNSkel.createAtom(Long.toString(location.getPegRevision())));
        loc.prepend(SVNSkel.createAtom(location.getPath()));
        // TODO UUID
        loc.prepend(SVNSkel.createEmptyList());
        loc.prepend(SVNSkel.createAtom(location.getRepositoryRoot().toString()));
        loc.prepend(SVNSkel.createAtom("subversion"));
    }
    
    public static void setConflictOperation(SVNSkel skel, SVNOperation operation, SVNConflictVersion original, SVNConflictVersion target) throws SVNException {
        final SVNSkel why = skel.first();
        final SVNSkel origins = SVNSkel.createEmptyList();
               
        prependLocation(origins, target);
        prependLocation(origins, original);
        why.prepend(origins);
        why.prepend(SVNSkel.createAtom(operation.getName()));
    }
    
    public static void addTextConflict(SVNSkel skel, ISVNWCDb db, File wriAbsPath, File mineAbsPath, File theirOldAbsPath, File theirAbsPath) throws SVNException {
        final SVNSkel textConflict = SVNSkel.createEmptyList();
        final SVNSkel markers = SVNSkel.createEmptyList();
        
        if (theirAbsPath != null) {
            final File theirRelPath = db.toRelPath(theirAbsPath);
            markers.prepend(SVNSkel.createAtom(SVNFileUtil.getFilePath(theirRelPath)));
        } else {
            markers.prepend(SVNSkel.createEmptyList());
        }
        if (mineAbsPath != null) {
            final File mineRelPath = db.toRelPath(mineAbsPath);
            markers.prepend(SVNSkel.createAtom(SVNFileUtil.getFilePath(mineRelPath)));
        } else {
            markers.prepend(SVNSkel.createEmptyList());
        }
        if (theirOldAbsPath != null) {
            final File theirOldRelPath = db.toRelPath(theirOldAbsPath);
            markers.prepend(SVNSkel.createAtom(SVNFileUtil.getFilePath(theirOldRelPath)));
        } else {
            markers.prepend(SVNSkel.createEmptyList());
        }
        textConflict.prepend(markers);
        textConflict.prepend(SVNSkel.createAtom(ConflictKind.text.toString()));

        skel.first().next().prepend(textConflict);
    }

    public static void addPropConflict(SVNSkel skel, ISVNWCDb db, File wriAbsPath, File markerAbsPath,
            SVNProperties mineProps, 
            SVNProperties theirOldProps, 
            SVNProperties theirProps,
            Collection<String> conflictedPropNames) throws SVNException {
        
        final SVNSkel propConflict = SVNSkel.createEmptyList();
        
        if (theirProps != null) {
            propConflict.prepend(SVNSkel.createPropList(theirProps.asMap()));            
        } else {
            propConflict.prepend(SVNSkel.createAtom(""));
        }
        if (mineProps != null) {
            propConflict.prepend(SVNSkel.createPropList(mineProps.asMap()));            
        } else {
            propConflict.prepend(SVNSkel.createAtom(""));
        }
        if (theirOldProps != null) {
            propConflict.prepend(SVNSkel.createPropList(theirOldProps.asMap()));            
        } else {
            propConflict.prepend(SVNSkel.createAtom(""));
        }
        final SVNSkel conflictNames = SVNSkel.createEmptyList();
        for (String propertyName : conflictedPropNames) {
            conflictNames.prepend(SVNSkel.createAtom(propertyName));
        }
        propConflict.prepend(conflictNames);
        final SVNSkel markers = SVNSkel.createEmptyList();
        if (markerAbsPath != null) {
            final File markerRelPath = db.toRelPath(markerAbsPath);
            markers.prepend(SVNSkel.createAtom(SVNFileUtil.getFilePath(markerRelPath)));
        }
        propConflict.prepend(markers);
        propConflict.prepend(SVNSkel.createAtom(ConflictKind.prop.toString()));
        
        skel.first().next().prepend(propConflict);
    }

    public static void addTreeConflict(SVNSkel skel, SVNWCDb db, File wriAbsPath, 
            SVNConflictReason localChange,
            SVNConflictAction incomingChange,
            File moveSrcOpRootAbsPath) throws SVNException {
        final SVNSkel treeConflict = SVNSkel.createEmptyList();
        
        if (localChange == SVNConflictReason.MOVED_AWAY && moveSrcOpRootAbsPath != null) {
            final File moveSrcOpRootRelPath = db.toRelPath(moveSrcOpRootAbsPath);
            treeConflict.prepend(SVNSkel.createAtom(SVNFileUtil.getFilePath(moveSrcOpRootRelPath)));
        }
        treeConflict.prepend(SVNSkel.createAtom(incomingChange.getName()));
        treeConflict.prepend(SVNSkel.createAtom(localChange.getName()));
        
        final SVNSkel markers = SVNSkel.createEmptyList();
        treeConflict.prepend(markers);
        treeConflict.prepend(SVNSkel.createAtom(ConflictKind.tree.toString()));
        
        skel.first().next().prepend(treeConflict);
    }
            

    public static SVNSkel readConflict(SVNWCDb db, File localAbspath) throws SVNException {
        final DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        final SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        final long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        final String localRelPathStr = dirInfo.localRelPath.getPath().replace(File.separatorChar, '/');
        
        final SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
        try {
            stmt.bindf("is", wcId, localRelPathStr);
            if (!stmt.next()) {
                final SVNSqlJetStatement stmtNode = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
                try {
                    stmtNode.bindf("is", wcId, localRelPathStr);
                    if (stmtNode.next()) {
                        return null;
                    }
                } finally {
                    reset(stmtNode);
                }
                final SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbspath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            final byte[] conflictData = stmt.getColumnBlob(ACTUAL_NODE__Fields.conflict_data);
            if (conflictData != null) {
                return SVNSkel.parse(conflictData);
            }
        } finally {
            reset(stmt);
        }
        return null;
    }

    public static Structure<ConflictInfo> readConflictInfo(SVNSkel conflictSkel) throws SVNException {
        final Structure<ConflictInfo> result = Structure.obtain(ConflictInfo.class);
        SVNSkel c;
        final SVNSkel operation = SvnWcDbConflicts.readConflictOperation(conflictSkel);
        if (operation == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Not a completed conflict skel");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        // operation
        c = operation.first();
        result.set(ConflictInfo.conflictOperation, SVNOperation.fromString(c.getValue()));
        
        // location
        c = c.next();
        final Collection<SVNConflictVersion> locations = new ArrayList<SVNConflictVersion>();
        result.set(ConflictInfo.locations, locations);
        for(int i = 0; i < c.getListSize(); i++) {
            final SVNConflictVersion location = SvnWcDbConflicts.readConflictLocation(c.getChild(i));
            if (location != null) {
                locations.add(location);
            }
        }
        result.set(ConflictInfo.textConflicted, SvnWcDbConflicts.hasConflictKind(conflictSkel, ConflictKind.text));
        result.set(ConflictInfo.propConflicted, SvnWcDbConflicts.hasConflictKind(conflictSkel, ConflictKind.prop));
        result.set(ConflictInfo.treeConflicted, SvnWcDbConflicts.hasConflictKind(conflictSkel, ConflictKind.tree));
        return result;
    }

    public static void readPropertyConflicts(List<SVNWCConflictDescription17> target, 
            SVNWCDb db, 
            File localAbsPath, 
            SVNSkel conflictSkel, 
            boolean createTempFiles,
            SVNOperation operation,
            SVNConflictVersion leftVersion,
            SVNConflictVersion rightVersion) throws SVNException {
        
        final Structure<PropertyConflictInfo> propertyConflictInfo = readPropertyConflict(db, localAbsPath, conflictSkel);
        final Set<String> conflictedProps = propertyConflictInfo.get(PropertyConflictInfo.conflictedPropNames);
        if (!createTempFiles || conflictedProps.isEmpty()) {
            final SVNWCConflictDescription17 description = SVNWCConflictDescription17.createProp(localAbsPath, SVNNodeKind.UNKNOWN, "");
            description.setTheirFile((File) propertyConflictInfo.get(PropertyConflictInfo.markerAbspath));
            description.setOperation(operation);
            description.setSrcLeftVersion(leftVersion);
            description.setSrcRightVersion(rightVersion);
            target.add(description);
            return;
        }
        //
        final File tmpFileRoot = db.getWCRootTempDir(localAbsPath); 
        for(String propertyName : conflictedProps) {
            final SVNWCConflictDescription17 description = SVNWCConflictDescription17.createProp(localAbsPath, SVNNodeKind.UNKNOWN, propertyName);
            description.setOperation(operation);
            description.setSrcLeftVersion(leftVersion);
            description.setSrcRightVersion(rightVersion);
            description.setPropertyName(propertyName);
            
            final Map<String, byte[]> mineProps = propertyConflictInfo.get(PropertyConflictInfo.mineProps); 
            final Map<String, byte[]> theirProps = propertyConflictInfo.get(PropertyConflictInfo.theirProps); 
            final Map<String, byte[]> oldProps = propertyConflictInfo.get(PropertyConflictInfo.theirOldProps); 
            
            final byte[] mineValue = mineProps.get(propertyName);
            final byte[] theirValue = theirProps.get(propertyName);
            final byte[] oldValue = oldProps.get(propertyName);
            if (theirValue == null) {
                description.setAction(SVNConflictAction.DELETE);
            } else if (mineValue == null) {
                description.setAction(SVNConflictAction.ADD);
            } else {
                description.setAction(SVNConflictAction.EDIT);
            }
            
            if (mineValue == null) {
                description.setReason(SVNConflictReason.DELETED);
            } else if (theirValue == null) {
                description.setReason(SVNConflictReason.ADDED);
            } else {
                description.setReason(SVNConflictReason.EDITED);
            }
            
            description.setTheirFile((File) propertyConflictInfo.get(PropertyConflictInfo.markerAbspath));
            if (mineValue != null) {
                final File tempFile = SVNFileUtil.createUniqueFile(tmpFileRoot, "svn.", ".prop.tmp", false);
                description.setMyFile(tempFile);
                SVNFileUtil.writeToFile(tempFile, mineValue);
            }
            if (theirValue != null) {
                final File tempFile = SVNFileUtil.createUniqueFile(tmpFileRoot, "svn.", ".prop.tmp", false);
                description.setMergedFile(tempFile);
                SVNFileUtil.writeToFile(tempFile, theirValue);
            }
            if (oldValue != null) {
                final File tempFile = SVNFileUtil.createUniqueFile(tmpFileRoot, "svn.", ".prop.tmp", false);
                description.setBaseFile(tempFile);
                SVNFileUtil.writeToFile(tempFile, oldValue);
            }
            target.add(description);
         }
    }

    public static Structure<PropertyConflictInfo> readPropertyConflict(SVNWCDb db, File wriAbsPath, SVNSkel conflictSkel) throws SVNException {
        final SVNSkel propConflict = SvnWcDbConflicts.getConflict(conflictSkel, ConflictKind.prop);
        if (propConflict == null) {
            final SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "Conflict not set");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNSkel c;
        final Structure<PropertyConflictInfo> result = Structure.obtain(PropertyConflictInfo.class);
        c = propConflict.first().next();
        if (c.first() != null && c.first().isAtom()) {
            result.set(PropertyConflictInfo.markerAbspath, new File(c.first().getValue()));
        }
        c = c.next();
        final Set<String> conflictedPropertyNames = new HashSet<String>();
        for(int i = 0; i < c.getListSize(); i++) {
            conflictedPropertyNames.add(c.getChild(i).getValue());
        }
        result.set(PropertyConflictInfo.conflictedPropNames, conflictedPropertyNames);
        c = c.next();
        if (c.isValidPropList()) {
            result.set(PropertyConflictInfo.theirOldProps, c.parsePropList());
        } else {
            result.set(PropertyConflictInfo.theirOldProps, new HashMap<String, byte[]>());
        }
        c = c.next();
        if (c.isValidPropList()) {
            result.set(PropertyConflictInfo.mineProps, c.parsePropList());
        } else {
            result.set(PropertyConflictInfo.mineProps, new HashMap<String, byte[]>());
        }
        c = c.next();
        if (c.isValidPropList()) {
            result.set(PropertyConflictInfo.theirProps, c.parsePropList());
        } else {
            result.set(PropertyConflictInfo.theirProps, new HashMap<String, byte[]>());
        }
    
        return result;
    }

    public static Structure<TextConflictInfo> readTextConflict(SVNWCDb db, File wriAbsPath, SVNSkel conflictSkel) throws SVNException {
        final SVNSkel textConflict = SvnWcDbConflicts.getConflict(conflictSkel, ConflictKind.text);
        if (textConflict == null) {
            final SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "Conflict not set");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        final Structure<TextConflictInfo> result = Structure.obtain(TextConflictInfo.class);
        SVNSkel m = textConflict.first().next().first();
        if (m.isAtom()) {
            final File path = db.fromRelPath(db.getWCRoot(wriAbsPath), new File(m.getValue()));
            result.set(TextConflictInfo.theirOldAbsPath, path);
        }
        m = m.next();
        if (m.isAtom()) {
            final File path = db.fromRelPath(db.getWCRoot(wriAbsPath), new File(m.getValue()));
            result.set(TextConflictInfo.mineAbsPath, path);
        }
        m = m.next();
        if (m.isAtom()) {
            final File path = db.fromRelPath(db.getWCRoot(wriAbsPath), new File(m.getValue()));
            result.set(TextConflictInfo.theirAbsPath, path);
        }
        
        return result;
    }

    public static Structure<TreeConflictInfo> readTreeConflict(SVNWCDb db, File wriAbsPath, SVNSkel conflictSkel) throws SVNException {
        final SVNSkel treeConflict = SvnWcDbConflicts.getConflict(conflictSkel, ConflictKind.tree);
        if (treeConflict == null) {
            final SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "Conflict not set");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        final Structure<TreeConflictInfo> result = Structure.obtain(TreeConflictInfo.class);
        SVNSkel c = treeConflict.first().next().next();
        
        SVNConflictReason reason = SVNConflictReason.fromString(c.getValue());
        if (reason == null) {
            reason = SVNConflictReason.EDITED;
        }
        boolean movedAway = reason == SVNConflictReason.MOVED_AWAY;
        result.set(TreeConflictInfo.localChange, reason);
        c = c.next();
        
        SVNConflictAction incoming = SVNConflictAction.fromString(c.getValue());
        if (incoming == null) {
            incoming = SVNConflictAction.EDIT;
        }
        result.set(TreeConflictInfo.incomingChange, incoming);
        c = c.next();
        if (c != null && movedAway) {
            result.set(TreeConflictInfo.moveSrcOpRootAbsPath, db.fromRelPath(db.getWCRoot(wriAbsPath), new File(c.getValue())));
        }
        return result;
    }
    
    public static Structure<ConflictStatus> getConflictStatusForUpdate(SVNWCDb db, File localAbsPath, boolean treeConflictOnly) throws SVNException {
        final Structure<ConflictStatus> result = getConflictStatus(db, localAbsPath);
        if (treeConflictOnly) {
            result.set(ConflictStatus.conflicted, result.is(ConflictStatus.treeConflicted));
        } else {
            result.set(ConflictStatus.conflicted, result.is(ConflictStatus.treeConflicted) || result.is(ConflictStatus.textConflicted) || result.is(ConflictStatus.propConflicted));
        }
        return result;
    }
    
    private static Structure<ConflictStatus> getConflictStatus(SVNWCDb db, File localAbsPath) throws SVNException {
        final Structure<ConflictStatus> result = Structure.obtain(ConflictStatus.class);
        final SVNSkel conflicts = readConflict(db, localAbsPath);
        if (conflicts == null) {
            return result;
        }
        boolean resolvedText = false;
        boolean resolvedProps = false;
        
        final Structure<ConflictInfo> conflictsInfo = readConflictInfo(conflicts);
        if (conflictsInfo.is(ConflictInfo.textConflicted)) {
            final Structure<TextConflictInfo> tc = readTextConflict(db, localAbsPath, conflicts);
            
            final File mineAbsPath = tc.get(TextConflictInfo.mineAbsPath);
            final File theirAbsPath = tc.get(TextConflictInfo.theirAbsPath);
            final File theirOldAbsPath = tc.get(TextConflictInfo.theirOldAbsPath);
            boolean done = false;
            
            if (mineAbsPath != null) {
                result.set(ConflictStatus.textConflicted, SVNFileType.getType(mineAbsPath) == SVNFileType.FILE);
                done = result.is(ConflictStatus.textConflicted);
            }
            if (!done && theirAbsPath != null) {
                result.set(ConflictStatus.textConflicted, SVNFileType.getType(theirAbsPath) == SVNFileType.FILE);
                done = result.is(ConflictStatus.textConflicted);
            }
            if (!done && theirOldAbsPath != null) {
                result.set(ConflictStatus.textConflicted, SVNFileType.getType(theirOldAbsPath) == SVNFileType.FILE);
                done = result.is(ConflictStatus.textConflicted);
            }
            
            if (!done && (mineAbsPath != null || theirAbsPath != null || theirOldAbsPath != null)) {
                resolvedText = false;
            }
        }
        if (conflictsInfo.is(ConflictInfo.propConflicted)) {
            final Structure<PropertyConflictInfo> pc = readPropertyConflict(db, localAbsPath, conflicts);
            final File propRejectPath = pc.get(PropertyConflictInfo.markerAbspath);
            if (propRejectPath != null) {
                result.set(ConflictStatus.propConflicted, SVNFileType.getType(propRejectPath) == SVNFileType.FILE);
            }
            if (!result.is(ConflictStatus.propConflicted)) {
                resolvedProps = true;
            }
        }
        if (conflictsInfo.is(ConflictInfo.treeConflicted)) {
            final Structure<TreeConflictInfo> pc = readTreeConflict(db, localAbsPath, conflicts);
            final SVNConflictReason reason = pc.get(TreeConflictInfo.incomingChange);
            final SVNConflictAction action = pc.get(TreeConflictInfo.localChange);
            
            if (reason == SVNConflictReason.MOVED_AWAY && action == SVNConflictAction.EDIT) {
                result.set(ConflictStatus.treeConflicted, false);
                result.set(ConflictStatus.conflictIgnored, true);
            }
        }
        if (resolvedProps || resolvedText) {
            if (db.isWCLockOwns(localAbsPath, false)) {
                db.opMarkResolved(localAbsPath, resolvedText, resolvedProps, false);
            }
        }
        return result;
    }

    private static SVNSkel readConflictOperation(SVNSkel conflictSkel) {
        return conflictSkel.first();
    }

    private static boolean hasConflictKind(SVNSkel conflictSkel, ConflictKind kind) {
        return SvnWcDbConflicts.getConflict(conflictSkel, kind) != null;
    }

    public static SVNSkel getConflict(SVNSkel conflictSkel, ConflictKind kind) {
        SVNSkel c = conflictSkel.first().next().first();
        while(c != null) {
            if (kind.name().equalsIgnoreCase(c.first().getValue())) {
                return c;
            }
            c = c.next();
        }
        return null;
    }

    private static SVNConflictVersion readConflictLocation(SVNSkel locationSkel) throws SVNException {
        SVNSkel c = locationSkel.first();
        if (c == null || !c.contentEquals("subversion")) {
            return null;
        }
        c = c.next();
        final SVNURL repositoryRootURL = SVNURL.parseURIEncoded(c.getValue());
        c = c.next();
        // TODO UUID
        c = c.next();
        final String reposRelPath = c.getValue();
        c = c.next();
        final long revision = Long.parseLong(c.getValue());
        c = c.next();
        final SVNNodeKind nodeKind = SVNNodeKind.parseKind(c.getValue());
        return new SVNConflictVersion(repositoryRootURL, reposRelPath, revision, nodeKind);
    }

    public static void conflictSkelOpUpdate(SVNSkel conflictSkel, SVNConflictVersion original, SVNConflictVersion target) throws SVNException {
        assert conflictSkel != null && conflictSkel.getListSize() >= 2 && conflictSkel.getChild(1) != null && conflictSkel.getChild(1).isAtom();

        SVNSkel why = getOperation(conflictSkel);

        assert why == null;

        why = conflictSkel.getChild(0);

        SVNSkel origins = SVNSkel.createEmptyList();
        prependLocation(origins, target);
        prependLocation(origins, original);
        why.prepend(origins);
        why.prependString(CONFLICT_OP_UPDATE);
    }

    private static SVNSkel getOperation(SVNSkel conflictSkel) throws SVNException {
        assert conflictSkel != null && conflictSkel.getListSize() >= 2 && conflictSkel.getChild(1) != null && conflictSkel.getChild(1).isAtom();
        return conflictSkel.getListSize() > 0 ? conflictSkel.getChild(0) : null;
    }

    public void addPropConflict(SVNSkel skel, String propName, SVNPropertyValue baseVal, SVNPropertyValue mineVal, SVNPropertyValue toVal, SVNPropertyValue fromVal) throws SVNException {
        SVNSkel propSkel = SVNSkel.createEmptyList();
        prependPropValue(fromVal, propSkel);
        prependPropValue(toVal, propSkel);
        prependPropValue(mineVal, propSkel);
        prependPropValue(baseVal, propSkel);

        propSkel.prependString(propName);
        propSkel.prependString(ConflictKind.prop.toString());
        skel.appendChild(propSkel);
    }


    public static void prependPropValue(SVNPropertyValue fromVal, SVNSkel skel) throws SVNException {
        SVNSkel valueSkel = SVNSkel.createEmptyList();
        if (fromVal != null && (fromVal.getBytes() != null || fromVal.getString() != null)) {
            valueSkel.prependPropertyValue(fromVal);
        }
        skel.prepend(valueSkel);
    }

}
