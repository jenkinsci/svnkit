package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgPropertiesManager {

    public static Collection<String> getGlobalIgnores(ISVNOptions options) {
        Collection<String> allPatterns = new HashSet<String>();
        String[] ignores = options.getIgnorePatterns();
        for (int i = 0; ignores != null && i < ignores.length; i++) {
            allPatterns.add(ignores[i]);
        }
        return allPatterns;
        
    }
    
    public static Collection<String> getEffectiveIgnores(SVNWCContext context, File absPath, Collection<String> globalIgnores) {
        Collection<String> allPatterns = new HashSet<String>();
        if (globalIgnores != null) {
            allPatterns.addAll(globalIgnores);
        } else {
            allPatterns.addAll(getGlobalIgnores(context.getOptions()));
        }
        
        if (context != null && absPath != null) {
            try {
                String ignoreProperty = context.getProperty(absPath, SVNProperty.IGNORE);
                if (ignoreProperty != null) {
                    for (StringTokenizer tokens = new StringTokenizer(ignoreProperty, "\r\n"); tokens.hasMoreTokens();) {
                        String token = tokens.nextToken().trim();
                        if (token.length() > 0) {
                            allPatterns.add(token);
                        }
                    }
                }
            } catch (SVNException e) {
            }
        }
        return allPatterns;
    }

    public static boolean isIgnored(String name, Collection<String> patterns) {
        if (patterns == null) {
            return false;
        }
        for (Iterator<String> ps = patterns.iterator(); ps.hasNext();) {
            String pattern = (String) ps.next();
            if (DefaultSVNOptions.matches(pattern, name)) {
                return true;
            }
        }
        return false;
    }

    public static void setProperty(final SVNWCContext context, File path, final String propertyName, final SVNPropertyValue propertyValue, SVNDepth depth, final boolean skipChecks, 
            final ISVNEventHandler eventHandler, Collection<String> changelists) throws SVNException {
        if (SVNProperty.isEntryProperty(propertyName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROP_KIND, "Property ''{0}'' is an entry property", propertyName);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (SVNProperty.isWorkingCopyProperty(propertyName)) {
            // TODO set DAV property
            return;
        } 
        SVNNodeKind kind = context.readKind(path, true);
        File dirPath;
        if (kind == SVNNodeKind.DIR) {
            dirPath = path;
        } else {
            dirPath = SVNFileUtil.getParentFile(path);
        }
        context.writeCheck(dirPath);
        if (depth == SVNDepth.EMPTY) {
            if (!context.matchesChangelist(path, changelists)) {
                return;
            }
            setProperty(context, path, kind, propertyName, propertyValue, skipChecks, eventHandler);
        } else {
            context.nodeWalkChildren(path, new SVNWCContext.ISVNWCNodeHandler() {
                public void nodeFound(File localAbspath, SVNWCDbKind kind) throws SVNException {
                    try {
                        setProperty(context, localAbspath, kind.toNodeKind(), propertyName, propertyValue, skipChecks, eventHandler);
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.ILLEGAL_TARGET ||
                                e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_INVALID_SCHEDULE) {
                            return;
                        }
                        throw e;
                    }
                }
            }, false, depth, changelists);
        }
    }

    private static void setProperty(SVNWCContext context, File path, SVNNodeKind kind, String propertyName, SVNPropertyValue value, boolean skipChecks,
            ISVNEventHandler eventHandler) throws SVNException {
        Structure<NodeInfo> nodeInfo = context.getDb().readInfo(path, NodeInfo.status);
        ISVNWCDb.SVNWCDbStatus status = nodeInfo.get(NodeInfo.status);
        if (status != SVNWCDbStatus.Normal && status != SVNWCDbStatus.Added && status != SVNWCDbStatus.Incomplete) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE,
                    "Can''t set properties on ''{0}'': invalid status for updating properties.",
                    path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (value != null && SVNProperty.isSVNProperty(propertyName)) {
            // TODO content fetcher.
            SVNPropertyValue pv = SVNPropertiesManager.validatePropertyValue(path, kind, propertyName, value, skipChecks, context.getOptions(), null);
            value = pv;
        }
        SVNSkel workItems = null;
        if (kind == SVNNodeKind.FILE && (SVNProperty.EXECUTABLE.equals(propertyName) || SVNProperty.NEEDS_LOCK.equals(propertyName))) {
            workItems = context.wqBuildSyncFileFlags(path);
        }
        
        
        SVNProperties properties = new SVNProperties(); 
        try {
            properties = context.getDb().readProperties(path);
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage().wrap("Failed to load current properties");
            SVNErrorManager.error(err, e, SVNLogType.WC);
        }

        boolean clearRecordedInfo = false;
        if (kind == SVNNodeKind.FILE && SVNProperty.KEYWORDS.equals(propertyName)) {
            String oldValue = properties.getStringValue(SVNProperty.KEYWORDS);
            @SuppressWarnings("unchecked")
            Map<String, String> keywords = oldValue != null ? context.getKeyWords(path, oldValue) : new HashMap<String, String>();
            @SuppressWarnings("unchecked")
            Map<String, String> newKeywords = value != null ? context.getKeyWords(path, value.getString()) : new HashMap<String, String>();
            if (!keywords.equals(newKeywords)) {
                clearRecordedInfo = true;
            }
        } else if (kind == SVNNodeKind.FILE && SVNProperty.EOL_STYLE.equals(propertyName)) {
            String oldValue = properties.getStringValue(SVNProperty.EOL_STYLE);
            if (oldValue == null || value == null) {
                clearRecordedInfo = (oldValue != null && value == null) || (oldValue == null && value != null);
            } else {
                clearRecordedInfo = !SVNPropertyValue.create(oldValue).equals(value);
            }
        }

        SVNEventAction action;
        if (!properties.containsName(propertyName)) {
            action = value == null ? SVNEventAction.PROPERTY_DELETE_NONEXISTENT : SVNEventAction.PROPERTY_ADD; 
        } else {
            action = value == null ? SVNEventAction.PROPERTY_DELETE : SVNEventAction.PROPERTY_MODIFY; 
        }            
        if (value != null) {
            properties.put(propertyName, value);
        } else {
            properties.remove(propertyName);
        }
        context.getDb().opSetProps(path, properties, null, clearRecordedInfo, workItems);
        if (workItems != null) {
            context.wqRun(path);
        }
        if (eventHandler != null) {
            SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.NONE, 
                    null, -1, action, action, null, null, 1, 1);
            eventHandler.handleEvent(event, -1);
        }
    }

}
