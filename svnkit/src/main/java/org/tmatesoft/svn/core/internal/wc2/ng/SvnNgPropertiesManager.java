package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.wc.ISVNOptions;
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

    public static void setProperty(SVNWCContext context, File path, String propertyName, String value, SVNDepth depth, boolean skipChecks, Collection<String> changelists) throws SVNException {
        if (SVNProperty.isEntryProperty(propertyName)) {
            // error
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
            // TODO changelists
            setProperty(context, path, kind, propertyName, value, skipChecks);
        } else {
            // TODO recursive propset
        }
    }

    private static void setProperty(SVNWCContext context, File path, SVNNodeKind kind, String propertyName, String value, boolean skipChecks) throws SVNException {
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
            SVNPropertyValue pv = SVNPropertiesManager.validatePropertyValue(path, kind, propertyName, SVNPropertyValue.create(value), skipChecks, context.getOptions(), null);
            value = pv != null ? pv.getString() : null;
        }
        SVNSkel workItems = null;
        if (kind == SVNNodeKind.FILE && (SVNProperty.EXECUTABLE.equals(propertyName) || SVNProperty.NEEDS_LOCK.equals(propertyName))) {
            workItems = context.wqBuildSyncFileFlags(path);
        }
        
        SVNProperties properties = context.getDb().readProperties(path);
        properties.put(propertyName, value);
        
        // TODO clear recorded info when changing eol or keywords
        context.getDb().opSetProps(path, properties, null, workItems);
        if (workItems != null) {
            context.wqRun(path);
        }
        
        // TODO fire events.
    }

}
