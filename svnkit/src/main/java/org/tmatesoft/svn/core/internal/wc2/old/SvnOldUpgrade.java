package org.tmatesoft.svn.core.internal.wc2.old;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbUpgradeData;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbOpenMode;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpgrade;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnOldUpgrade extends SvnOldRunner<SvnWcGeneration, SvnUpgrade> {
	
	/* WC-1.0 administrative area extensions */
	private static final String SVN_WC__BASE_EXT =      ".svn-base"; /* for text and prop bases */
	private static final String SVN_WC__WORK_EXT =      ".svn-work"; /* for working propfiles */
	private static final String SVN_WC__REVERT_EXT =    ".svn-revert"; /* for reverting a replaced
	                                               file */

	/* Old locations for storing "wcprops" (aka "dav cache").  */
	private static final String WCPROPS_SUBDIR_FOR_FILES = "wcprops";
	private static final String WCPROPS_FNAME_FOR_DIR = "dir-wcprops";
	private static final String WCPROPS_ALL_DATA = "all-wcprops";

	/* Old property locations. */
	private static final String PROPS_SUBDIR = "props";
	private static final String PROP_BASE_SUBDIR = "prop-base";
	private static final String PROP_BASE_FOR_DIR = "dir-prop-base";
	private static final String PROP_REVERT_FOR_DIR = "dir-prop-revert";
	private static final String PROP_WORKING_FOR_DIR = "dir-props";

	/* Old textbase location. */
	private static final String TEXT_BASE_SUBDIR = "text-base";

	private static final String TEMP_DIR = "tmp";

	/* Old data files that we no longer need/use.  */
	private static final String ADM_README = "README.txt";
	private static final String ADM_EMPTY_FILE = "empty-file";
	private static final String ADM_LOG = "log";
	private static final String ADM_LOCK = "lock";

	/* New pristine location */
	private static final String PRISTINE_STORAGE_RELPATH = "pristine";
	private static final String PRISTINE_STORAGE_EXT = ".svn-base";
	/* Number of characters in a pristine file basename, in WC format <= 28. */
	private static final long PRISTINE_BASENAME_OLD_LEN = 40;
	private static final String SDB_FILE = "wc.db";
	
	
	
	private class RepositoryInfo {
		public SVNURL repositoryRootUrl = null;
		public String UUID = null;
	}
	
	private SVNWCAccess access = null;
	
	private SVNWCAccess getWCAccess() {
		if (access == null) {
			access = SVNWCAccess.newInstance(getOperation().getEventHandler());
		    access.setOptions(getOperation().getOptions());
		}
		return access;
	}

    @Override
    protected SvnWcGeneration run() throws SVNException {
        
        if (getOperation().getFirstTarget().isURL()) {
        	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET,
        			"'{0}' is not a local path", getOperation().getFirstTarget().getURL());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        File localAbsPath = getOperation().getFirstTarget().getFile().getAbsoluteFile();
        RepositoryInfo reposInfo = new RepositoryInfo();
        wcUpgrade(localAbsPath, reposInfo);
        
        /* Now it's time to upgrade the externals too. We do it after the wc
        upgrade to avoid that errors in the externals causes the wc upgrade to
        fail. Thanks to caching the performance penalty of walking the wc a
        second time shouldn't be too severe */
        
        final ArrayList<SvnTarget> externals = new ArrayList<SvnTarget>();
        SvnGetProperties getProperties = getOperation().getOperationFactory().createGetProperties();
        getProperties.addTarget(getOperation().getFirstTarget());
        getProperties.setDepth(SVNDepth.INFINITY);
        getProperties.setReceiver(new ISvnObjectReceiver<SVNProperties>() {
            public void receive(SvnTarget target, SVNProperties object) throws SVNException {
                if (object.containsName(SVNProperty.EXTERNALS)) {
                	externals.add(target);
                } 
            }
        });
        getProperties.run();
        
        for (SvnTarget target : externals) {
        	if (SVNFileType.getType(target.getFile()) == SVNFileType.DIRECTORY) {
        			wcUpgrade(target.getFile(), reposInfo);
        		}
        }
        return null;
    }
    
    private void checkIsOldWCRoot(File localAbsPath) throws SVNException {
    	SVNWCAccess wcAccess = getWCAccess();
    	try {
    		wcAccess.probeOpen(localAbsPath, false, 0);
	    	if (wcAccess.isWCRoot(localAbsPath))
	    		return;
	    }
	    catch (SVNException e) {
	    	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD,
	    			"Can't upgrade '{0}' as it is not a pre-1.7 working copy directory", localAbsPath);
	        SVNErrorManager.error(err, SVNLogType.WC);
	    }
	    finally {
	    	wcAccess.close();
	    }
	    	
	    File parentAbsPath = SVNFileUtil.getParentFile(localAbsPath);
	    SVNEntry entry = null;
	    try {
	    	try {
	    		wcAccess.probeOpen(parentAbsPath, false, 0);
	    	}
		   	catch (SVNException e) {
		   		return;
		    }	
	    	entry = wcAccess.getEntry(localAbsPath, false);
	    }
		finally {
			wcAccess.close();
		}
	    	
	    if (entry == null || entry.isAbsent() || (entry.isDeleted() && entry.getSchedule() != SVNProperty.SCHEDULE_ADD)
	    	|| entry.getDepth() == SVNDepth.EXCLUDE) 
	    	return;
	    		    	
	    File childAbsPath;
	    while (!wcAccess.isWCRoot(parentAbsPath)) {
	    	childAbsPath = parentAbsPath;
	    	parentAbsPath = SVNFileUtil.getParentFile(parentAbsPath);
	    	try {
	    		try {
	    			wcAccess.probeOpen(parentAbsPath, false, 0);
	    		}
		   		catch (SVNException e) {
		   			parentAbsPath = childAbsPath;
		   			break;
		   		}
	    		entry = wcAccess.getEntry(localAbsPath, false);
	    	}
			finally {
				wcAccess.close();
			}
	    		
	    	if (entry == null || entry.isAbsent() || (entry.isDeleted() && entry.getSchedule() != SVNProperty.SCHEDULE_ADD)
	       		|| entry.getDepth() == SVNDepth.EXCLUDE) {
        		parentAbsPath = childAbsPath;
        		break;
        	}
    	}
    	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD,
    			"Can't upgrade '{0}' as it is not a pre-1.7 working copy root, the root is '%s'", localAbsPath, parentAbsPath);
        SVNErrorManager.error(err, SVNLogType.WC);
    }
    
    private void fetchReposInfo(SVNEntry entry, RepositoryInfo lastRepositoryInfo) throws SVNException {
    	  /* The same info is likely to retrieved multiple times (e.g. externals) */
    	 if (lastRepositoryInfo.repositoryRootUrl != null && SVNURLUtil.isAncestor(lastRepositoryInfo.repositoryRootUrl , entry.getSVNURL()))
    	 {
    		 entry.setRepositoryRootURL(lastRepositoryInfo.repositoryRootUrl);
    		 entry.setUUID(lastRepositoryInfo.UUID);
    		 return;
    	 }
    	 
    	 SvnRepositoryAccess repAccess = new SvnOldRepositoryAccess(getOperation());
    	 SVNRepository repository = repAccess.createRepository(entry.getSVNURL(), null, true);
    	 entry.setRepositoryRootURL(repository.getRepositoryRoot(false));
    	 entry.setUUID(repository.getRepositoryUUID(false));
    	 
    	 lastRepositoryInfo.repositoryRootUrl = entry.getRepositoryRootURL();
    	 lastRepositoryInfo.UUID = entry.getUUID();
    }
    
    private void ensureReposInfo(SVNEntry entry, File localAbsPath, RepositoryInfo lastRepositoryInfo, SVNHashMap reposCache) throws SVNException {
    	if (entry.getRepositoryRootURL() != null && entry.getUUID() != null)
    		return;
    	
    	if ((entry.getRepositoryRootURL() == null || entry.getUUID() == null) && entry.getSVNURL() != null) {
    		for (Iterator<SVNURL> items = reposCache.keySet().iterator(); items.hasNext();) {
    			SVNURL reposRootUrl = items.next();
    			if (SVNURLUtil.isAncestor(reposRootUrl, entry.getSVNURL())){
    				if (entry.getRepositoryRootURL() == null)
    					entry.setRepositoryRootURL(reposRootUrl);
    				if (entry.getUUID() == null)
    					entry.setUUID((String)reposCache.get(reposRootUrl));
    				return;
    			}
    		}
    	}
    	
    	if (entry.getSVNURL() == null) {
    		SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT,
    				"Working copy '{0}' can't be upgraded because it doesn't have a url", localAbsPath);
	        SVNErrorManager.error(err, SVNLogType.WC);
    	}
    	
    	fetchReposInfo(entry, lastRepositoryInfo);
    }
    
    
    private void wcUpgrade(File localAbsPath, RepositoryInfo reposInfo) throws SVNException {
    	WCDbUpgradeData upgradeData = new WCDbUpgradeData();
    	
    	checkIsOldWCRoot(localAbsPath);
    	
        /* Given a pre-wcng root some/wc we create a temporary wcng in
        some/wc/.svn/tmp/wcng/wc.db and copy the metadata from one to the
        other, then the temporary wc.db file gets moved into the original
        root.  Until the wc.db file is moved the original working copy
        remains a pre-wcng and 'cleanup' with an old client will remove
        the partial upgrade.  Moving the wc.db file creates a wcng, and
        'cleanup' with a new client will complete any outstanding
        upgrade. */
    	
    	SVNWCDb db = new SVNWCDb();
    	db.open(SVNWCDbOpenMode.ReadWrite, (ISVNOptions)null, false, false);
    	SVNWCContext wcContext = new SVNWCContext(db, getOperation().getEventHandler());
    	
    	SVNWCAccess wcAccess = getWCAccess();
    	SVNEntry thisDir = null;
    	try {
    		wcAccess.probeOpen(localAbsPath, false, 0);
    		thisDir = wcAccess.getEntry(localAbsPath, false);
    	}
		finally {
			wcAccess.close();
		}
	        	
	    SVNHashMap reposCache = new SVNHashMap();
	   	ensureReposInfo(thisDir, localAbsPath, reposInfo, reposCache);
	    	
	   	/* Cache repos UUID pairs for when a subdir doesn't have this information */
	   	if (!reposCache.containsKey(thisDir.getRepositoryRootURL()))
	   		reposCache.put(thisDir.getRepositoryRootURL(), thisDir.getUUID());
    	/* Create the new DB in the temporary root wc/.svn/tmp/wcng/.svn */
	   	
    	upgradeData.rootAbsPath = SVNFileUtil.createFilePath(SVNWCUtils.admChild(localAbsPath, "tmp"),"wcng"); 
    	File rootAdmAbsPath = SVNWCUtils.admChild(upgradeData.rootAbsPath, "tmp");
    	
    	try {
	    	
	    	SVNFileUtil.deleteAll(rootAdmAbsPath, true);
	    	SVNFileUtil.ensureDirectoryExists(rootAdmAbsPath);
	    	    	
	    	/* Create an empty sqlite database for this directory and store it in DB. */
	    	db.upgradeBegin(upgradeData.rootAbsPath, upgradeData, thisDir.getRepositoryRootURL(), thisDir.getUUID());
	    		
	    	/* Migrate the entries over to the new database.
	    	  ### We need to think about atomicity here.
	
	    	  entries_write_new() writes in current format rather than
	    	  f12. Thus, this function bumps a working copy all the way to
	    	  current.  */
		    	
	    	db.obtainWCLock(upgradeData.rootAbsPath, 0, false);
	    	
	    	upgradeData.sDb.beginTransaction(SqlJetTransactionMode.WRITE);
	    	try {
	    		upgradeWorkingCopy(null, db, localAbsPath, upgradeData, reposCache, reposInfo);
	        } catch (SVNException e) {
	        	upgradeData.sDb.rollback();
	        } finally {
	        	upgradeData.sDb.commit();
	        }
	    	
	    	
	    	
	    	/* A workqueue item to move the pristine dir into place */
		    	
	    	File pristineFrom = SVNFileUtil.createFilePath(rootAdmAbsPath, PRISTINE_STORAGE_RELPATH);
	    	File pristineTo = SVNWCUtils.admChild(localAbsPath, PRISTINE_STORAGE_RELPATH);
	    	SVNFileUtil.ensureDirectoryExists(pristineFrom);
	    	
	    	SVNSkel workItems = null;
	    	SVNSkel workItem = wcContext.wqBuildFileMove(localAbsPath, pristineFrom, pristineTo);
	    	workItems = wcContext.wqMerge(workItems, workItem);
	    	
	    	/* A workqueue item to remove pre-wcng metadata */
	    	workItem = wcContext.wqBuildPostUpgrade();
	    	workItems = wcContext.wqMerge(workItems, workItem);
		    	
	    	db.addWorkQueue(upgradeData.rootAbsPath, workItems);
	    	
	    	db.releaseWCLock(upgradeData.rootAbsPath);
	    	db.close();
		    	
	    	/* Renaming the db file is what makes the pre-wcng into a wcng */
	    	File dbFrom = SVNFileUtil.createFilePath(rootAdmAbsPath, SDB_FILE);
	    	File dbTo = SVNWCUtils.admChild(localAbsPath, SDB_FILE);
	    	SVNFileUtil.rename(dbFrom, dbTo);
		    	
	    	db.open(SVNWCDbOpenMode.ReadWrite, (ISVNOptions)null, false, false);
	    	wcContext = new SVNWCContext(db, getOperation().getEventHandler());
	    	wcContext.wqRun(localAbsPath);
	    	db.close();
    	} finally {
    		SVNFileUtil.deleteAll(upgradeData.rootAbsPath, true);
    	}
	    			
    }
    
    private void upgradeWorkingCopy(String parentDirBaton, ISVNWCDb db, File dirAbsPath, WCDbUpgradeData data, SVNHashMap reposCache, RepositoryInfo reposInfo)  throws SVNException {
    	
    	String dirBaton = null;
    		
    	if (getOperation().getEventHandler() != null)
    		getOperation().getEventHandler().checkCancelled();
    	int oldFormat = db.getFormatTemp(dirAbsPath);
    	//int oldFormat = 1;
    	      	  
    	if (oldFormat >= SVNWCContext.WC_NG_VERSION) {
    		if (getOperation().getEventHandler() != null) {
    			SVNEvent event = SVNEventFactory.createSVNEvent(dirAbsPath, SVNNodeKind.DIR, null, -1, SVNEventAction.SKIP, null, null, null);
    			getOperation().getEventHandler().handleEvent(event, -1);
    		}
    		return;
    	}
    	
    	SVNHashSet children = new SVNHashSet();
    	try {
    		getVesionedSubdirs(dirAbsPath, children, false, false);
    	}
    	catch (SVNException ex) {
    		if (getOperation().getEventHandler() != null) {
    			SVNEvent event = SVNEventFactory.createSVNEvent(dirAbsPath, SVNNodeKind.DIR, null, -1, SVNEventAction.SKIP, null, null, null);
    			getOperation().getEventHandler().handleEvent(event, -1);
    		}
    		return;
    	}
    	
    	dirBaton = upgradeToWcng(dirBaton, db, dirAbsPath, oldFormat, data, reposCache, reposInfo);
    	
    	if (getOperation().getEventHandler() != null) {
			SVNEvent event = SVNEventFactory.createSVNEvent(dirAbsPath, SVNNodeKind.DIR, null, -1, SVNEventAction.UPGRADE, null, null, null);
			getOperation().getEventHandler().handleEvent(event, -1);
		}

    	for (Iterator<File> dirs = children.iterator(); dirs.hasNext();) {
			File childAbsPath = dirs.next();
			upgradeWorkingCopy(dirBaton, db, childAbsPath, data, reposCache, reposInfo);
		} 
    }
    	
    private String upgradeToWcng(String parentDirBaton, ISVNWCDb db, File dirAbsPath, int oldFormat, WCDbUpgradeData data, SVNHashMap reposCache, RepositoryInfo reposInfo) throws SVNException {
    	String result = "";
    	File logFilePath = SVNWCUtils.admChild(dirAbsPath, ADM_LOG);
    	 
    	/* Don't try to mess with the WC if there are old log files left. */
		
		/* Is the (first) log file present?  */
		SVNNodeKind logFileKind = SVNFileType.getNodeKind(SVNFileType.getType(logFilePath));
		if (logFileKind == SVNNodeKind.FILE) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT,
        			"Cannot upgrade with existing logs; run a cleanup operation on this working copy using " + 
					"a client version which is compatible with this working copy's format (such as the version " +
					"you are upgrading from), then retry the upgrade with the current version");
            SVNErrorManager.error(err, SVNLogType.WC);
		}
		
		/* Lock this working copy directory, or steal an existing lock. Do this
		BEFORE we read the entries. We don't want another process to modify the
		entries after we've read them into memory.  */
		createPhysicalLock(dirAbsPath);
		
		/* What's going on here?
		*
		* We're attempting to upgrade an older working copy to the new wc-ng format.
		* The semantics and storage mechanisms between the two are vastly different,
		* so it's going to be a bit painful.  Here's a plan for the operation:
		*
		* 1) Read the old 'entries' using the old-format reader.
		*
		* 2) Create the new DB if it hasn't already been created.
		*
		* 3) Use our compatibility code for writing entries to fill out the (new)
		*    DB state.  Use the remembered checksums, since an entry has only the
		*    MD5 not the SHA1 checksum, and in the case of a revert-base doesn't
		*    even have that.
		*
		* 4) Convert wcprop to the wc-ng format
		*
		* 5) Migrate regular properties to the WC-NG DB.
		*/
		
		/***** ENTRIES - READ *****/
		SVNWCAccess access = getWCAccess();
    	Map<String, SVNEntry> entries = null;
    	try {
    		SVNAdminArea area = access.probeOpen(dirAbsPath, false, 0);
    		entries = area.getEntries();
    			
	    	SVNEntry thisDir = entries.get("");
			ensureReposInfo(thisDir, dirAbsPath, reposInfo, reposCache);
			/* Cache repos UUID pairs for when a subdir doesn't have this information */
			if (!reposCache.containsKey(thisDir.getRepositoryRootURL())) {
				reposCache.put(thisDir.getRepositoryRootURL(), thisDir.getUUID());
			}
			
			String dirAbsPathString = dirAbsPath.getAbsolutePath().replace(File.separatorChar, '/');
	        String rootAbsPathString = data.rootAbsPath.getAbsolutePath().replace(File.separatorChar, '/'); 
			String oldWcRootAbsPath = SVNPathUtil.getCommonPathAncestor(dirAbsPathString, rootAbsPathString);
			File dirRelPath = new File(SVNPathUtil.getRelativePath(oldWcRootAbsPath, dirAbsPathString));
			
			
			/***** TEXT BASES *****/
			//migrateTextBases();
			/*
			SVN_ERR(migrate_text_bases(&text_bases_info, dir_abspath, data->root_abspath,
			data->sdb, scratch_pool, scratch_pool));
			*/
			
			/***** ENTRIES - WRITE *****/
			/*
			result = svn_wc__write_upgraded_entries(dir_baton, parent_baton, db, data->sdb,
			        data->repos_id, data->wc_id,
			        dir_abspath, data->root_abspath,
			        entries, text_bases_info,
			        result_pool, scratch_pool);
			if (err && err->apr_err == SVN_ERR_WC_CORRUPT)
				return svn_error_quick_wrap(err, "This working copy is corrupt and cannot be upgraded. Please check out a new working copy.");
			else
				SVN_ERR(err);
			*/
			
			/***** WC PROPS *****/
			/* If we don't know precisely where the wcprops are, ignore them.  */
			if (oldFormat != SVNWCContext.WC_WCPROPS_LOST)
			{
				PropFetchHandler propGetHandler = new PropFetchHandler();
		    	access.walkEntries(dirAbsPath, propGetHandler, false, SVNDepth.FILES);
		    				
		    	SVNHashMap allProps = propGetHandler.getAllProps();
		    	
		    	int i = 1;
				/*
				if (oldFormat <= SVNWCContext.WC_WCPROPS_MANY_FILES_VERSION)
					allProps = readManyWcProps(dirAbsPath);
				else
					allProps = readWcProps(dirAbsPath);
				*/
				
				
				/*
				SVN_ERR(svn_wc__db_upgrade_apply_dav_cache(data->sdb, dir_relpath,
			                  allProps, scratch_pool));
			    */
			}
			
			/* Upgrade all the properties (including "this dir").
			
			Note: this must come AFTER the entries have been migrated into the
			database. The upgrade process needs the children in BASE_NODE and
			WORKING_NODE, and to examine the resultant WORKING state.  */
			
			/*
			SVN_ERR(migrate_props(dir_abspath, data->root_abspath, data->sdb, old_format,
			wc_id, scratch_pool));
			*/
		
    	}
		finally {
			access.close();
		}
		
		return result;
    }
    
    /* Create a physical lock file in the admin directory for ABSPATH.  */
    private void createPhysicalLock(File absPath) throws SVNException {
    	
    	File lockAbsPath = buildLockfilePath(absPath);
    	if (lockAbsPath.isFile()) {
    		/* Congratulations, we just stole a physical lock from somebody */
            return;
    	}
    	SVNFileUtil.createEmptyFile(lockAbsPath);
    }
    
    /* Read the properties from the file at propfileAbsPath, returning them
    If the propfile is NOT present, then NULL will be returned  */
    /*
    private SVNHashMap readPropFile(File propfileAbsPath) throws SVNException {
    	return null;
    }
    */
    
    /* Read the wcprops from all the files in the admin area of dirAbsPath */
    /*
    private SVNHashMap readManyWcProps(File dirAbsPath, SVNAdminArea area) throws SVNException  {
    	File propsFileAbsPath = SVNWCUtils.admChild(dirAbsPath, WCPROPS_FNAME_FOR_DIR);
    	
    	SVNHashMap allProps = new SVNHashMap();
    			
    	SVNHashMap props = readPropFile(propsFileAbsPath);
    	if (props != null) {
    		allProps.put("SVN_WC_ENTRY_THIS_DIR", props);
    	}
    	
    	File propsDirAbsPath = SVNWCUtils.admChild(dirAbsPath, WCPROPS_SUBDIR_FOR_FILES);
    	File[] files = SVNFileListUtil.listFiles(propsDirAbsPath);
    	for (File file : files) {
    		props = readPropFile(file);
    		assert(props != null);
    		allProps.put(file.getAbsolutePath(), props);
    	}
    	return allProps;
	}
	*/
    
    private class PropFetchHandler implements ISVNEntryHandler {
    	private SVNHashMap allProps = new SVNHashMap();
    	
    	public SVNHashMap getAllProps() {
    		return allProps;
    	}
    	
        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            SVNAdminArea adminArea = entry.getAdminArea();
            if (entry.isDirectory() && !entry.isThisDir()) {
                return;
            }
            SVNProperties props = adminArea.getBaseProperties(entry.getName()).asMap();
            props.putAll(adminArea.getProperties(entry.getName()).asMap());
            props.putAll(adminArea.getRevertProperties(entry.getName()).asMap());
            if (props.size() > 0)
            	allProps.put(path, props);
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }

    
    /* For wcprops stored in a single file in this working copy */
    private SVNHashMap readWcProps(File dirAbsPath) {
    	return null;
    }
	 
	/* Return in CHILDREN, the list of all 1.6 versioned subdirectories
    which also exist on disk as directories.

    If DELETE_DIR is not NULL set *DELETE_DIR to TRUE if the directory
    should be deleted after migrating to WC-NG, otherwise to FALSE.

    If SKIP_MISSING is TRUE, don't add missing or obstructed subdirectories
    to the list of children.
    */
    public static boolean getVesionedSubdirs(File localAbsPath, SVNHashSet children, boolean isCalculateDoDeleteDir, boolean isSkipMissing) throws SVNException {
    	boolean isDoDeleteDir = false;
    	
    	SVNWCAccess access = SVNWCAccess.newInstance(null);
    	Map<String, SVNEntry> entries = null;
    	
    	try {
    		SVNAdminArea area = access.probeOpen(localAbsPath, false, 0);
    		entries = area.getEntries();
    	}
		finally {
			access.close();
		}
    	    	
    	SVNEntry thisDir = null;
    	for (Iterator<String> names = entries.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            SVNEntry entry = (SVNEntry) entries.get(name);
            
            /* skip "this dir"  */
            if ("".equals(name)) {
            	thisDir = entry;
            	continue;
            }
            else if (entry == null || entry.getKind() != SVNNodeKind.DIR) {
            	continue;
            }
            
            File childAbsPath =  SVNFileUtil.createFilePath(localAbsPath, name);
            
            if (isSkipMissing)
            {
              SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(childAbsPath));
              if (kind != SVNNodeKind.DIR)
                continue;
            }
            
            children.add(childAbsPath);
        }
    	
    	if (isCalculateDoDeleteDir) {
    		isDoDeleteDir = (thisDir != null && thisDir.getSchedule() == SVNProperty.SCHEDULE_DELETE && !thisDir.isKeepLocal());
    	}
    	
    	return isDoDeleteDir;
    }
    
    public static void wipePostUpgrade(SVNWCContext ctx, File dirAbsPath, boolean isWholeAdmin) throws SVNException {
    	ctx.checkCancelled();
		
		SVNHashSet subDirs = new SVNHashSet();
		boolean isDoDeleteDir = false;
		try {
			getVesionedSubdirs(dirAbsPath, subDirs, true, true);
		} catch (SVNException ex) {
			return;
		}
		
		for (Iterator<File> dirs = subDirs.iterator(); dirs.hasNext();) {
			File childAbsPath = dirs.next();
			wipePostUpgrade(ctx, childAbsPath, true);
		}
				
		/* ### Should we really be ignoring errors here? */
        if (isWholeAdmin)
        	SVNFileUtil.deleteAll(SVNFileUtil.createFilePath(dirAbsPath, SVNFileUtil.getAdminDirectoryName()), true);
        else {
        	wipeObsoleteFiles(dirAbsPath);
        }
        	
        if (isDoDeleteDir) {
        	/* If this was a WC-NG single database copy, this directory wouldn't
            	be here (unless it was deleted with --keep-local)

                If the directory is empty, we can just delete it; if not we
                keep it.
        	 */
        	SVNFileUtil.deleteAll(dirAbsPath, true);
        }
    }
    
    public static void wipeObsoleteFiles(File dirAbsPath) throws SVNException {
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, SVNWCContext.WC_ADM_FORMAT), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, SVNWCContext.WC_ADM_ENTRIES), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, ADM_EMPTY_FILE), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, ADM_README), true);
    	
    	/* For formats <= SVN_WC__WCPROPS_MANY_FILES_VERSION, we toss the wcprops
	     for the directory itself, and then all the wcprops for the files.  */
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, WCPROPS_FNAME_FOR_DIR), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, WCPROPS_SUBDIR_FOR_FILES), true);
    	
    	/* And for later formats, they are aggregated into one file.  */
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, WCPROPS_ALL_DATA), true);
    	
    	/* Remove the old text-base directory and the old text-base files. */
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, TEXT_BASE_SUBDIR), true);
    	
    	/* Remove the old properties files... whole directories at a time.  */
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, PROPS_SUBDIR), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, PROP_BASE_SUBDIR), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, PROP_WORKING_FOR_DIR), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, PROP_BASE_FOR_DIR), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, PROP_REVERT_FOR_DIR), true);
    	
    	/*
    	 #if 0
    	  * ### this checks for a write-lock, and we are not (always) taking out
    	     ### a write lock in all callers.  *
    	  	SVN_ERR(svn_wc__adm_cleanup_tmp_area(db, wcroot_abspath, iterpool));
    	 #endif
    	 */
    	
    	/* Remove the old-style lock file LAST.  */
    	SVNFileUtil.deleteAll(buildLockfilePath(dirAbsPath), true);
    }
    
    /* Return the path of the old-school administrative lock file
    associated with LOCAL_DIR_ABSPATH, allocated from RESULT_POOL. */
	private static File buildLockfilePath(File dirAbsPath) {
		return SVNWCUtils.admChild(dirAbsPath, ADM_LOCK);
	 }
 }
