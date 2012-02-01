package org.tmatesoft.svn.core.internal.wc2.old;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
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
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbLock;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbUpgradeData;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbOpenMode;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDbRoot;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbPristines;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnChecksum.Kind;
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
	    
	    if (entry == null || entry.isAbsent() || (entry.isDeleted() && !entry.isScheduledForAddition())
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
	    		
	    	if (entry == null || entry.isAbsent() || (entry.isDeleted() && !entry.isScheduledForAddition())
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
    	SVNWCDbUpgradeData upgradeData = new SVNWCDbUpgradeData();
    	
    	checkIsOldWCRoot(localAbsPath);
    	
        /* Given a pre-wcng root some/wc we create a temporary wcng in some/wc/.svn/tmp/wcng/wc.db and copy the metadata from one to the
        other, then the temporary wc.db file gets moved into the original root.  Until the wc.db file is moved the original working copy
        remains a pre-wcng and 'cleanup' with an old client will remove the partial upgrade.  Moving the wc.db file creates a wcng, and
        'cleanup' with a new client will complete any outstanding upgrade. */
    	
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
	
	    	  entries_write_new() writes in current format rather than f12. Thus, this function bumps a working copy all the way to
	    	  current.  */
		    	
	    	db.obtainWCLock(upgradeData.rootAbsPath, 0, false);
	    	
	    	upgradeData.root.getSDb().beginTransaction(SqlJetTransactionMode.WRITE);
	    	try {
	    		upgradeWorkingCopy(null, db, localAbsPath, upgradeData, reposCache, reposInfo);
	        } catch (SVNException ex) {
	        	upgradeData.root.getSDb().rollback();
	        	throw ex;
	        } finally {
	        	upgradeData.root.getSDb().commit();
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
	    	/*!!!	
	    	db.open(SVNWCDbOpenMode.ReadWrite, (ISVNOptions)null, false, false);
	    	wcContext = new SVNWCContext(db, getOperation().getEventHandler());
	    	wcContext.wqRun(localAbsPath);
	    	*/
	    	db.close();
    	} finally {
    		SVNFileUtil.deleteAll(upgradeData.rootAbsPath, true);
    	}
	    			
    }
    
    private void upgradeWorkingCopy(WriteBaton parentDirBaton, SVNWCDb db, File dirAbsPath, SVNWCDbUpgradeData data, SVNHashMap reposCache, RepositoryInfo reposInfo)  throws SVNException {
    	
    	WriteBaton dirBaton = null;
    		
    	if (getOperation().getEventHandler() != null)
    		getOperation().getEventHandler().checkCancelled();
    	int oldFormat = db.getFormatTemp(dirAbsPath);
    	      	  
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
    	
    	dirBaton = upgradeToWcng(parentDirBaton, db, dirAbsPath, oldFormat, data, reposCache, reposInfo);
    	
    	if (getOperation().getEventHandler() != null) {
			SVNEvent event = SVNEventFactory.createSVNEvent(dirAbsPath, SVNNodeKind.DIR, null, -1, SVNEventAction.UPGRADE, null, null, null);
			getOperation().getEventHandler().handleEvent(event, -1);
		}

    	for (Iterator<File> dirs = children.iterator(); dirs.hasNext();) {
			File childAbsPath = dirs.next();
			upgradeWorkingCopy(dirBaton, db, childAbsPath, data, reposCache, reposInfo);
		} 
    }
    	
    private WriteBaton upgradeToWcng(WriteBaton parentDirBaton, SVNWCDb db, File dirAbsPath, int oldFormat, SVNWCDbUpgradeData data, SVNHashMap reposCache, RepositoryInfo reposInfo) throws SVNException {
    	WriteBaton dirBaton = null;
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
			SVNHashMap textBases = migrateTextBases(dirAbsPath, data.rootAbsPath, data.root);
			
			
			/***** ENTRIES - WRITE *****/
			try {
				dirBaton = writeUpgradedEntries(parentDirBaton, db, data, dirAbsPath, entries, textBases);
			}
			catch (SVNException ex) {
				if (ex.getErrorMessage().getErrorCode() == SVNErrorCode.WC_CORRUPT) {
					SVNErrorMessage err = ex.getErrorMessage().wrap("This working copy is corrupt and cannot be upgraded. Please check out a new working copy.");
					SVNErrorManager.error(err, SVNLogType.WC);
				}
			}
			
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
								
				/*!!!
				SVN_ERR(svn_wc__db_upgrade_apply_dav_cache(data->sdb, dir_relpath,
			                  allProps, scratch_pool));
			    */
			}
			
			/* Upgrade all the properties (including "this dir").
			
			Note: this must come AFTER the entries have been migrated into the
			database. The upgrade process needs the children in BASE_NODE and
			WORKING_NODE, and to examine the resultant WORKING state.  */
			
			/*!!!
			SVN_ERR(migrate_props(dir_abspath, data->root_abspath, data->sdb, old_format,
			wc_id, scratch_pool));
			*/
		
    	}
		finally {
			access.close();
		}
		
		return dirBaton;
    }
    
    /* The checksums of one pre-1.7 text-base file.  If the text-base file exists, both checksums are filled in, otherwise both fields are NULL. */
    private class TextBaseFileInfo
    {
      public SvnChecksum sha1Checksum;
      public SvnChecksum md5Checksum;
    }

    /* The text-base checksums of the normal base and/or the revert-base of one pre-1.7 versioned text file. */
    private class TextBaseInfo
    {
    	public TextBaseFileInfo normalBase;
    	public TextBaseFileInfo revertBase;
    } 

    
    /* Copy all the text-base files from the administrative area of WC directory 
     * dirAbsPath into the pristine store of SDB which is located in directory newWcRootAbsPath.
	 * Returns SVNHashMap that maps name of the versioned file to (svn_wc__text_base_info_t *) information about the pristine text. */
    private SVNHashMap migrateTextBases(File dirAbsPath, File newWcRootAbsPath, SVNWCDbRoot root) throws SVNException {
    	SVNHashMap textBasesInfo = new SVNHashMap();
    	File textBaseDir = SVNWCUtils.admChild(dirAbsPath, TEXT_BASE_SUBDIR);
    	File[] files = SVNFileListUtil.listFiles(textBaseDir);
    	for (File textBasePath : files) {
    		SvnChecksum md5Checksum = null;
    		SvnChecksum sha1Checksum = null;
    		File pristinePath;
    		    	
    	  	/* Calculate its checksums and copy it to the pristine store */
    		{
	    	    File tempPath = SVNFileUtil.createUniqueFile(newWcRootAbsPath, "upgrade", ".tmp", false);
	    	    
	    	    InputStream readStream = SVNFileUtil.openFileForReading(textBasePath);
	    	   
		    	SVNChecksumInputStream readChecksummedIS = null;
		        try {
		        	readChecksummedIS = new SVNChecksumInputStream(readStream, "SHA1");
		        } finally {
		            SVNFileUtil.closeFile(readChecksummedIS);
		            SVNFileUtil.closeFile(readStream);
		        }        
		        sha1Checksum = readChecksummedIS != null ? new SvnChecksum(Kind.sha1, readChecksummedIS.getDigest()) : null;
		        readStream = SVNFileUtil.openFileForReading(textBasePath);
		        try {
		        	readChecksummedIS = new SVNChecksumInputStream(readStream, SVNChecksumInputStream.MD5_ALGORITHM);
		        } finally {
		        	SVNFileUtil.closeFile(readChecksummedIS);
		        	SVNFileUtil.closeFile(readStream);
		        }        
		        md5Checksum = readChecksummedIS != null ? new SvnChecksum(Kind.md5, readChecksummedIS.getDigest()) : null;
	    	    
	    	                
	            SVNFileUtil.copyFile(textBasePath, tempPath, true);
	            
	            /* Insert a row into the pristine table. */
	            SVNSqlJetStatement stmt = root.getSDb().getStatement(SVNWCDbStatements.INSERT_OR_IGNORE_PRISTINE);
	            stmt.bindChecksum(1, sha1Checksum);
	            stmt.bindChecksum(2, md5Checksum);
	            stmt.bindLong(3, textBasePath.length());
	            stmt.exec();
	            
	            pristinePath = SvnWcDbPristines.getPristineFuturePath(root, sha1Checksum);
	                        
	            /* Ensure any sharding directories exist. */
	    	    SVNFileUtil.ensureDirectoryExists(SVNFileUtil.getFileDir(pristinePath));
	    	    	    	    
	    	    /* Now move the file into the pristine store, overwriting existing files with the same checksum. */
	    	    SVNFileUtil.rename(tempPath, pristinePath);
    	    }
    	    
    	    /* Add the checksums for this text-base to *TEXT_BASES_INFO. */
    	    {
    	    	boolean isRevertBase;
    	    	File versionedFile = removeSuffix(textBasePath, SVN_WC__REVERT_EXT);
    	    	if (versionedFile != null) {
    	    		isRevertBase = true;
    	    	} 
    	    	else {
    	    		versionedFile = removeSuffix(textBasePath, SVN_WC__BASE_EXT);
    	    		isRevertBase = false;
    	    	}
    	    	
    	    	if (versionedFile == null) {
  	             /* Some file that doesn't end with .svn-base or .svn-revert. 
  	                No idea why that would be in our administrative area, but we shouldn't segfault on this case.
  	                Note that we already copied this file in the pristine store, but the next cleanup will take care of that.
  	              */
    	    		continue;
  	          	}
    	    	
    	    	String versionedFileName = SVNFileUtil.getFileName(versionedFile);
    	    	
    	    	/* Create a new info for this versioned file, or fill in the existing one if this is the second text-base we've found for it. */
    	    	TextBaseInfo info = (TextBaseInfo)textBasesInfo.get(versionedFileName);
    	    	if (info == null)
    	          info = new TextBaseInfo();
    	    	TextBaseFileInfo fileInfo = new TextBaseFileInfo();
    	    	fileInfo.sha1Checksum = sha1Checksum;
    	    	fileInfo.md5Checksum = md5Checksum;
    	    	if (isRevertBase)
    	    		info.revertBase = fileInfo;
    	    	else
    	    		info.normalBase = fileInfo;
    	    	textBasesInfo.put(versionedFileName, info);
    	    }

    	}
    	return textBasesInfo;   
    	
    }
    
    /* If File name ends with SUFFIX and is longer than SUFFIX, return the part of STR that comes before SUFFIX; else return NULL. */
    private File removeSuffix(File file, String suffix) {
    	String fileName = SVNPathUtil.getAbsolutePath(file.getPath());
    	if (fileName.length() > suffix.length() && fileName.endsWith(suffix)) {
    		return SVNFileUtil.createFilePath(fileName.substring(0, fileName.length() - suffix.length()));
    	}
    	return null;
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
    
    /* Read the properties from the file at propfileAbsPath, returning them If the propfile is NOT present, then NULL will be returned  */
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
    
    /* For wcprops stored in a single file in this working copy */
    /*
    private SVNHashMap readWcProps(File dirAbsPath) {
    	return null;
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
   
	 
	/* Return in CHILDREN, the list of all 1.6 versioned subdirectories which also exist on disk as directories.

    If DELETE_DIR is not NULL set *DELETE_DIR to TRUE if the directory should be deleted after migrating to WC-NG, otherwise to FALSE.

    If SKIP_MISSING is TRUE, don't add missing or obstructed subdirectories to the list of children. 
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
    		isDoDeleteDir = (thisDir != null && thisDir.isScheduledForDeletion() && !thisDir.isKeepLocal());
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
        	/* If this was a WC-NG single database copy, this directory wouldn't be here (unless it was deleted with --keep-local)

               If the directory is empty, we can just delete it; if not we keep it.
        	 */
        	SVNFileUtil.deleteAll(dirAbsPath, true);
        }
    }
    
    public static void wipeObsoleteFiles(File dirAbsPath) throws SVNException {
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, SVNWCContext.WC_ADM_FORMAT), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, SVNWCContext.WC_ADM_ENTRIES), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, ADM_EMPTY_FILE), true);
    	SVNFileUtil.deleteAll(SVNWCUtils.admChild(dirAbsPath, ADM_README), true);
    	
    	/* For formats <= SVN_WC__WCPROPS_MANY_FILES_VERSION, we toss the wcprops for the directory itself, and then all the wcprops for the files.  */
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
    
    /* Return the path of the old-school administrative lock file associated with LOCAL_DIR_ABSPATH, allocated from RESULT_POOL. */
	private static File buildLockfilePath(File dirAbsPath) {
		return SVNWCUtils.admChild(dirAbsPath, ADM_LOCK);
	}
	
	private WriteBaton writeUpgradedEntries(WriteBaton parentNode, SVNWCDb db,  SVNWCDbUpgradeData upgradeData, File dirAbsPath, 
			Map<String, SVNEntry> entries, SVNHashMap textBases) throws SVNException {
		WriteBaton dirNode = new WriteBaton();
		
		SVNEntry thisDir = entries.get("");
		/* If there is no "this dir" entry, something is wrong. */
		if (thisDir == null) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No default entry in directory '{0}'", dirAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);
		}
		File oldRootAbsPath = SVNFileUtil.createFilePath(SVNPathUtil.getCommonPathAncestor(
				SVNPathUtil.getAbsolutePath(dirAbsPath.getAbsolutePath()), SVNPathUtil.getAbsolutePath(upgradeData.rootAbsPath.getAbsolutePath())));
		
		assert(oldRootAbsPath != null);
		File dirRelPath = SVNWCUtils.skipAncestor(oldRootAbsPath, dirAbsPath);
		
		/* Write out "this dir" */
		dirNode = writeEntry(true, parentNode, db, upgradeData, thisDir, null, dirRelPath, 
				SVNFileUtil.createFilePath(upgradeData.rootAbsPath, dirRelPath), oldRootAbsPath, thisDir, false);
				
		for (Iterator<String> names = entries.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            SVNEntry entry = (SVNEntry) entries.get(name);
            TextBaseInfo info = (TextBaseInfo)textBases.get(name);
            if ("".equals(name)) 
            	continue;
            
            /* Write the entry. Pass TRUE for create locks, because we still use this function for upgrading old working copies. */
            File childAbsPath =  SVNFileUtil.createFilePath(dirAbsPath, name);
            File childRelPath = SVNWCUtils.skipAncestor(oldRootAbsPath, childAbsPath);
            writeEntry(false, dirNode, db, upgradeData, entry, info, childRelPath, 
            		SVNFileUtil.createFilePath(upgradeData.rootAbsPath, childRelPath), oldRootAbsPath, thisDir, true);
		}
		
		if (dirNode.treeConflicts != null) {
			writeActualOnlyEntries(dirNode.treeConflicts, upgradeData.root.getSDb(), upgradeData.workingCopyId, SVNFileUtil.getFilePath(dirRelPath));
		}
	
		return dirNode;
	}
	
	private class DbNode {
		long wcId;
		String localRelPath;
		long opDepth;
		long reposId;
		String reposRelPath;
		String parentRelPath;
		SVNWCDbStatus presence = SVNWCDbStatus.Normal;
		long revision;
		SVNNodeKind kind;  /* ### should switch to svn_wc__db_kind_t */
		SvnChecksum checksum;
		long translatedSize;
		long changedRev;
		SVNDate changedDate;
		String changedAuthor;
		SVNDepth depth;
		SVNDate lastModTime;
		SVNProperties properties;
		boolean isFileExternal;
	};
	
	private class DbActualNode {
		long wcId;
		String localRelPath;
		String parentRelPath;
		SVNProperties properties;
		String conflictOld;
		String conflictNew;
		String conflictWorking;
		String propReject;
		String changelist;
		/* ### enum for text_mod */
		String treeConflictData;
	}
		
	private class WriteBaton {
		DbNode base;
		DbNode work;
		DbNode belowWork;
		SVNHashMap treeConflicts;
	};
	
	/* Write the information for ENTRY to WC_DB.  The WC_ID, REPOS_ID and REPOS_ROOT will all be used for writing ENTRY.
	   ### transitioning from straight sql to using the wc_db APIs.  For the time being, we'll need both parameters. */
	private WriteBaton writeEntry(boolean isCalculateEntryNode, WriteBaton parentNode, SVNWCDb db, SVNWCDbUpgradeData upgradeData, SVNEntry entry, TextBaseInfo textBaseInfo,
			File localRelPath, File tmpEntryAbsPath, File rootAbsPath, SVNEntry thisDir, boolean isCreateLocks) throws SVNException {
		DbNode baseNode = null;
		DbNode workingNode = null;
		DbNode belowWorkingNode = null;
		DbActualNode actualNode = null;
		
		String parentRelPath = null;
		if (localRelPath != null)
			parentRelPath = SVNFileUtil.getFilePath(SVNFileUtil.getFileDir(localRelPath)); 
		
		/* This is how it should work, it doesn't work like this yet because we need proper op_depth to layer the working nodes.

	     Using "svn add", "svn rm", "svn cp" only files can be replaced pre-wcng; directories can only be normal, deleted or added.
	     Files cannot be replaced within a deleted directory, so replaced files can only exist in a normal directory, or a directory that
	     is added+copied.  In a normal directory a replaced file needs a base node and a working node, in an added+copied directory a
	     replaced file needs two working nodes at different op-depths.

	     With just the above operations the conversion for files and directories is straightforward:

	           pre-wcng                             wcng
	     parent         child                 parent     child

	     normal         normal                base       base
	     add+copied     normal+copied         work       work
	     normal+copied  normal+copied         work       work
	     normal         delete                base       base+work
	     delete         delete                base+work  base+work
	     add+copied     delete                work       work
	     normal         add                   base       work
	     add            add                   work       work
	     add+copied     add                   work       work
	     normal         add+copied            base       work
	     add            add+copied            work       work
	     add+copied     add+copied            work       work
	     normal         replace               base       base+work
	     add+copied     replace               work       work+work
	     normal         replace+copied        base       base+work
	     add+copied     replace+copied        work       work+work

	     However "svn merge" make this more complicated.  The pre-wcng "svn merge" is capable of replacing a directory, that is it can
	     mark the whole tree deleted, and then copy another tree on top. 
	     The entries then represent the replacing tree overlayed on the deleted tree.

	       original       replace          schedule in
	       tree           tree             combined tree

	       A              A                replace+copied
	       A/f                             delete+copied
	       A/g            A/g              replace+copied
	                      A/h              add+copied
	       A/B            A/B              replace+copied
	       A/B/f                           delete+copied
	       A/B/g          A/B/g            replace+copied
	                      A/B/h            add+copied
	       A/C                             delete+copied
	       A/C/f                           delete+copied
	                      A/D              add+copied
	                      A/D/f            add+copied

	     The original tree could be normal tree, or an add+copied tree. 
	     Committing such a merge generally worked, but making further tree modifications before commit sometimes failed.

	     The root of the replace is handled like the file replace:

	           pre-wcng                             wcng
	     parent         child                 parent     child

	     normal         replace+copied        base       base+work
	     add+copied     replace+copied        work       work+work

	     although obviously the node is a directory rather then a file.
	     There are then more conversion states where the parent is replaced.

	           pre-wcng                                wcng
	     parent           child              parent            child

	     replace+copied   add                [base|work]+work  work
	     replace+copied   add+copied         [base|work]+work  work
	     replace+copied   delete+copied      [base|work]+work  [base|work]+work
	     delete+copied    delete+copied      [base|work]+work  [base|work]+work
	     replace+copied   replace+copied     [base|work]+work  [base|work]+work
	  */
		
		assert(parentNode != null || entry.getSchedule() == null);
		assert(parentNode == null || parentNode.base != null || parentNode.belowWork != null || parentNode.work != null);
		
		if (entry.getSchedule() == null) {
			if (entry.isCopied() || 
				(entry.getDepth() == SVNDepth.EXCLUDE && parentNode != null && parentNode.base == null && parentNode.work != null)) {
				workingNode = new DbNode();
			} else {
				baseNode = new DbNode();
			}
		}
		else if (entry.isScheduledForAddition()) {
			workingNode = new DbNode();
			if (entry.isDeleted()) {
				if (parentNode != null && parentNode.base != null)
					baseNode = new DbNode();
				else
					belowWorkingNode = new DbNode();
			}
		}
		else if (entry.isScheduledForDeletion()) {
			workingNode = new DbNode();
			if (parentNode != null && parentNode.base != null) 
				baseNode = new DbNode();
			if (parentNode != null && parentNode.work != null)
				belowWorkingNode = new DbNode();
		}
		else if (entry.isScheduledForReplacement()) {
			workingNode = new DbNode();
			if (parentNode != null && parentNode.base != null) 
				baseNode = new DbNode();
			else
				belowWorkingNode = new DbNode();
		}
		
		/* Something deleted in this revision means there should always be a BASE node to indicate the not-present node.  */
		if (entry.isDeleted()) {
			assert(baseNode != null || belowWorkingNode != null);
			assert(!entry.isIncomplete());
			if (baseNode != null)
				baseNode.presence = SVNWCDbStatus.NotPresent;
			else
				belowWorkingNode.presence = SVNWCDbStatus.NotPresent;
		} else if (entry.isAbsent()) {
			assert(baseNode != null && workingNode == null && belowWorkingNode == null);
			assert(!entry.isIncomplete());
			baseNode.presence = SVNWCDbStatus.Excluded;
		}
		
		if (entry.isCopied()) {
			if (entry.getCopyFromSVNURL() != null) {
				workingNode.reposId = upgradeData.repositoryId;
				String relPath = SVNURLUtil.getRelativeURL(thisDir.getRepositoryRootURL(), entry.getCopyFromSVNURL());
				if (relPath == null)
					workingNode.reposRelPath = null;
				else 
					workingNode.reposRelPath = relPath;
				workingNode.revision = entry.getCopyFromRevision();
				workingNode.opDepth = SVNWCUtils.relpathDepth(localRelPath);
			} else if (parentNode != null && parentNode.work != null && parentNode.work.reposRelPath != null) {
				workingNode.reposId = upgradeData.repositoryId;
				workingNode.reposRelPath = SVNPathUtil.append(parentNode.work.reposRelPath, SVNFileUtil.getFileName(localRelPath));
				workingNode.revision = parentNode.work.revision;
				workingNode.opDepth = parentNode.work.opDepth;
			} else {
				SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "No copyfrom URL for '{0}'", localRelPath);
	            SVNErrorManager.error(err, SVNLogType.WC);
			}
		}
		
		if (entry.getConflictOld() != null) {
			actualNode = new DbActualNode();
			if (parentRelPath != null && entry.getConflictOld() != null)
				actualNode.conflictOld = SVNPathUtil.append(parentRelPath, entry.getConflictOld());
			else
				actualNode.conflictOld = entry.getConflictOld();
			
			if (parentRelPath != null && entry.getConflictNew() != null)
				actualNode.conflictNew = SVNPathUtil.append(parentRelPath, entry.getConflictNew());
			else
				actualNode.conflictNew = entry.getConflictNew();
			
			if (parentRelPath != null && entry.getConflictWorking() != null)
				actualNode.conflictWorking = SVNPathUtil.append(parentRelPath, entry.getConflictWorking());
			else
				actualNode.conflictWorking = entry.getConflictWorking();
		}
		
		if (entry.getPropRejectFile() != null) {
			actualNode = new DbActualNode();
			actualNode.propReject = SVNPathUtil.append(
					entry.isDirectory() ? SVNFileUtil.getFilePath(localRelPath) : parentRelPath, entry.getPropRejectFile());
			
		}
		
		if (entry.getChangelistName() != null) {
			actualNode = new DbActualNode();
			actualNode.changelist = entry.getChangelistName();
		}
		
		SVNHashMap treeConflicts = null;
		/* ### set the text_mod value? */
		if (isCalculateEntryNode && entry.getTreeConflictData() != null) {
			/* Issues #3840/#3916: 1.6 stores multiple tree conflicts on the parent node, 1.7 stores them directly on the conflited nodes.
	         So "((skel1) (skel2))" becomes "(skel1)" and "(skel2)" */
			
			treeConflicts = new SVNHashMap();
			Map tcs = entry.getTreeConflicts();
	        for (Iterator keys = tcs.keySet().iterator(); keys.hasNext();) {
	            File entryPath = (File) keys.next();
	            SVNTreeConflictDescription conflict = (SVNTreeConflictDescription) tcs.get(entryPath);
				assert(conflict.isTreeConflict());
				/* Fix dubious data stored by old clients, local adds don't have a repository URL. */
				if (conflict.getConflictReason() == SVNConflictReason.ADDED)
					conflict.setSourceLeftVersion(null);
				SVNConflictVersion nullVersion = new SVNConflictVersion(null, null, SVNRepository.INVALID_REVISION, SVNNodeKind.UNKNOWN);
				SVNSkel newSkel = SVNTreeConflictUtil.getConflictSkel(nullVersion, conflict);
				String key = SVNFileUtil.getFilePath(SVNWCUtils.skipAncestor(rootAbsPath, conflict.getPath()));
				treeConflicts.put(key, newSkel.toString());
            }
		}
		
		if (parentNode != null && parentNode.treeConflicts != null) {
			String treeConflictData = (String)parentNode.treeConflicts.get(SVNFileUtil.getFilePath(localRelPath));
			if (treeConflictData != null) {
				actualNode = new DbActualNode();
				actualNode.treeConflictData = treeConflictData;
				/* Reset hash so that we don't write the row again when writing actual-only nodes */
				parentNode.treeConflicts.remove(SVNFileUtil.getFilePath(localRelPath));
			}
			
			
		}
		
		if (entry.getExternalFilePath() != null) {
			baseNode = new DbNode();
		}
		
		/* Insert the base node. */
		if (baseNode != null) {
			baseNode.wcId = upgradeData.workingCopyId;
			baseNode.localRelPath = SVNFileUtil.getFilePath(localRelPath);
			baseNode.opDepth = 0;
			baseNode.parentRelPath = parentRelPath;
			baseNode.revision = entry.getRevision();
			baseNode.lastModTime = SVNDate.parseDate(entry.getTextTime());
			baseNode.translatedSize = entry.getWorkingSize();
			if (entry.getDepth() != SVNDepth.EXCLUDE) {
				baseNode.depth = entry.getDepth();
			} else {
				baseNode.presence = SVNWCDbStatus.Excluded;
				baseNode.depth = SVNDepth.INFINITY;
			}
			if (entry.isDeleted()) {
				assert(baseNode.presence == SVNWCDbStatus.NotPresent);
				baseNode.kind = entry.getKind();
			} else if (entry.isAbsent()) {
				assert(baseNode.presence == SVNWCDbStatus.ServerExcluded);
				/* ### should be svn_node_unknown, but let's store what we have. */
				baseNode.kind = entry.getKind();
				/* Store the most likely revision in the node to avoid base nodes without a valid revision. Of course
	             we remember that the data is still incomplete. */
				if (baseNode.revision == ISVNWCDb.INVALID_REVNUM && parentNode != null && parentNode.base != null)
					baseNode.revision = parentNode.base.revision;
			} else {
				baseNode.kind = entry.getKind();
				if (baseNode.presence != SVNWCDbStatus.ServerExcluded) {
					/* All subdirs are initially incomplete, they stop being incomplete when the entries file in the subdir is
	                 upgraded and remain incomplete if that doesn't happen. */
					if (entry.isDirectory() && "".equals(entry.getName())) {
						baseNode.presence = SVNWCDbStatus.Incomplete;
						/* Store the most likely revision in the node to avoid base nodes without a valid revision. Of course
	                     we remember that the data is still incomplete. */
						if (parentNode != null && parentNode.base != null) {
							baseNode.revision = parentNode.base.revision;
						}
					} else if (entry.isIncomplete()) {
							/* ### nobody should have set the presence.  */
							assert(baseNode.presence == SVNWCDbStatus.Normal);
							baseNode.presence = SVNWCDbStatus.Incomplete;
						}
					}
			}
			
			if (entry.isDirectory()) {
				baseNode.checksum = null;
			} else {
				if (textBaseInfo != null && textBaseInfo.revertBase != null && textBaseInfo.revertBase.sha1Checksum != null) {
					baseNode.checksum = textBaseInfo.revertBase.sha1Checksum;
				} else if (textBaseInfo != null && textBaseInfo.normalBase != null && textBaseInfo.normalBase.sha1Checksum != null) {
					baseNode.checksum = textBaseInfo.normalBase.sha1Checksum;
				} else {
					baseNode.checksum = null;
				}
				 /* The base MD5 checksum is available in the entry, unless there is a copied WORKING node.  
				  * If possible, verify that the entry checksum matches the base file that we found. */
				if (!(workingNode != null && entry.isCopied())) {
					SvnChecksum entryMd5Checksum = new SvnChecksum(Kind.md5, entry.getChecksum());
					SvnChecksum foundMd5Checksum = null;
					if (textBaseInfo != null && textBaseInfo.revertBase != null && textBaseInfo.revertBase.md5Checksum != null) {
						foundMd5Checksum = textBaseInfo.revertBase.md5Checksum;
					} else if (textBaseInfo != null && textBaseInfo.normalBase != null && textBaseInfo.normalBase.md5Checksum != null) {
						foundMd5Checksum = textBaseInfo.normalBase.md5Checksum;
					}
					
					
					if (entryMd5Checksum != null && foundMd5Checksum != null && !entryMd5Checksum.equals(foundMd5Checksum)) {
						SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, 
							"Bad base MD5 checksum for '{0}'; expected: '{1}'; found '{2}';", 
									SVNFileUtil.createFilePath(rootAbsPath, localRelPath), entryMd5Checksum, foundMd5Checksum);
			           SVNErrorManager.error(err, SVNLogType.WC);
					}  else {
		                  /* ### Not sure what conditions this should cover. */
		                  /* SVN_ERR_ASSERT(entry->deleted || ...); */
		            }
					
				}
			}
			
			if (thisDir.getRepositoryRootURL() != null) {
				baseNode.reposId = upgradeData.repositoryId;
				if (entry.getSVNURL() != null) {
					String relPath = SVNURLUtil.getRelativeURL(thisDir.getRepositoryRootURL(), entry.getSVNURL());
					baseNode.reposRelPath = relPath != null ? relPath : "";
				} else {
					String relPath = SVNURLUtil.getRelativeURL(thisDir.getRepositoryRootURL(), thisDir.getSVNURL());
					if (relPath == null) {
						baseNode.reposRelPath = entry.getName();
					} else {
						baseNode.reposRelPath = SVNPathUtil.append(relPath, entry.getName());
					}
				}
			}

		      /* TODO: These values should always be present, if they are missing 
		       * during an upgrade, set a flag, and then ask the user to talk to the server.

		         Note: cmt_rev is the distinguishing value. The others may be 0 or NULL if the corresponding revprop has been deleted.  */
			
			baseNode.changedRev = entry.getCommittedRevision();
			baseNode.changedDate = SVNDate.parseDate(entry.getCommittedDate());
			baseNode.changedAuthor = entry.getAuthor();
			
			if (entry.getExternalFilePath() != null) {
				baseNode.isFileExternal = true;
			}
			
			insertNode(upgradeData.root.getSDb(), baseNode);
			
			/* We have to insert the lock after the base node, because the node
	         must exist to lookup various bits of repos related information for the abs path. */
			
			if (entry.getLockToken() != null && isCreateLocks) {
				SVNWCDbLock lock = new SVNWCDbLock();
				lock.token = entry.getLockToken();
				lock.owner = entry.getLockOwner();
				lock.comment = entry.getLockComment();
				lock.date = SVNDate.parseDate(entry.getLockCreationDate());
				
				db.addLock(tmpEntryAbsPath, lock);
			}
		}
		
		if (belowWorkingNode != null) {
			DbNode work = parentNode.belowWork != null ? parentNode.belowWork : parentNode.work;
			belowWorkingNode.wcId = upgradeData.workingCopyId;
			belowWorkingNode.localRelPath = SVNFileUtil.getFilePath(localRelPath);
			belowWorkingNode.opDepth = work.opDepth;
			belowWorkingNode.parentRelPath = parentRelPath;
			belowWorkingNode.presence = SVNWCDbStatus.Normal;
			belowWorkingNode.kind = entry.getKind();
			belowWorkingNode.reposId = upgradeData.repositoryId;
			
			if (work.reposRelPath != null) {
				belowWorkingNode.reposRelPath = SVNPathUtil.append(work.reposRelPath, entry.getName());
			} else {
				belowWorkingNode.reposRelPath = null;
			}
			belowWorkingNode.revision = parentNode.work.revision;
		    
			/* The revert_base checksum isn't available in the entry structure, so the caller provides it. */

			/* text_base_info is NULL for files scheduled to be added. */
			belowWorkingNode.checksum = null;
			if (textBaseInfo != null) {
				if (entry.isScheduledForDeletion()) {
					belowWorkingNode.checksum = textBaseInfo.normalBase.sha1Checksum;
				} else {
					belowWorkingNode.checksum = textBaseInfo.revertBase.sha1Checksum;
				}
			}
			
			belowWorkingNode.translatedSize = 0;
			belowWorkingNode.changedRev = ISVNWCDb.INVALID_REVNUM;
			belowWorkingNode.changedDate = null;
			belowWorkingNode.changedAuthor = null;
			belowWorkingNode.depth = SVNDepth.INFINITY;
			belowWorkingNode.lastModTime = null;
			belowWorkingNode.properties = null;
			
			insertNode(upgradeData.root.getSDb(), belowWorkingNode);
		}
		
		/* Insert the working node. */
		if (workingNode != null) {
			workingNode.wcId = upgradeData.workingCopyId;
			workingNode.localRelPath = SVNFileUtil.getFilePath(localRelPath);
			workingNode.parentRelPath = parentRelPath;
			workingNode.changedRev = ISVNWCDb.INVALID_REVNUM;
			workingNode.lastModTime = SVNDate.parseDate(entry.getTextTime());
			workingNode.translatedSize = entry.getWorkingSize();
			
			if (entry.getDepth() != SVNDepth.EXCLUDE) {
				workingNode.depth = entry.getDepth();
			} else {
				workingNode.presence = SVNWCDbStatus.Excluded;
				workingNode.depth = SVNDepth.INFINITY;
			}
			
			if (entry.isDirectory()) {
				workingNode.checksum = null;
			} else {
				/* text_base_info is NULL for files scheduled to be added. */
				if (textBaseInfo != null) {
					workingNode.checksum = textBaseInfo.normalBase.sha1Checksum;
				}
				/* If an MD5 checksum is present in the entry, we can verify that it matches the MD5 of the base file we found earlier. */
				/*#ifdef SVN_DEBUG
				if (entry->checksum && text_base_info)
		          {
		            svn_checksum_t *md5_checksum;
		            SVN_ERR(svn_checksum_parse_hex(&md5_checksum, svn_checksum_md5,
		                                           entry->checksum, result_pool));
		            SVN_ERR_ASSERT(
		              md5_checksum && text_base_info->normal_base.md5_checksum);
		            SVN_ERR_ASSERT(svn_checksum_match(
		              md5_checksum, text_base_info->normal_base.md5_checksum));
		          }
		         #endif*/
			}
			workingNode.kind = entry.getKind();
			if (workingNode.presence != SVNWCDbStatus.Excluded) {
				/* All subdirs start of incomplete, and stop being incomplete when the entries file in the subdir is upgraded. */
				if (entry.isDirectory() && "".equals(entry.getName())) {
					workingNode.presence = SVNWCDbStatus.Incomplete;
					workingNode.kind = SVNNodeKind.DIR;
				} else if (entry.isScheduledForDeletion()) {
					workingNode.presence = SVNWCDbStatus.BaseDeleted;
					workingNode.kind = entry.getKind();
				} else {
					/* presence == normal  */
					workingNode.kind = entry.getKind();
					if (entry.isIncomplete()) {
						/* We shouldn't be overwriting another status.  */
						assert(workingNode.presence == SVNWCDbStatus.Normal);
						workingNode.presence = SVNWCDbStatus.Incomplete;
					}
				}
			}
			
			 /* These should generally be unset for added and deleted files,
	         and contain whatever information we have for copied files. Let's just store whatever we have.

	         Note: cmt_rev is the distinguishing value. The others may be 0 or NULL if the corresponding revprop has been deleted.  */
			if (workingNode.presence != SVNWCDbStatus.BaseDeleted) {
				workingNode.changedRev = entry.getCommittedRevision();
				workingNode.changedDate =  SVNDate.parseDate(entry.getCommittedDate());
				workingNode.changedAuthor = entry.getAuthor();
			}
			
			if (entry.isScheduledForDeletion() && parentNode != null && parentNode.work != null && parentNode.work.presence == SVNWCDbStatus.BaseDeleted) {
				workingNode.opDepth = parentNode.work.opDepth;
			} else {
				workingNode.opDepth = SVNWCUtils.relpathDepth(localRelPath);
			}
			
			insertNode(upgradeData.root.getSDb(), workingNode);
		}
		
		/* Insert the actual node. */
		if (actualNode != null) {
			actualNode.wcId = upgradeData.workingCopyId;
			actualNode.localRelPath = SVNFileUtil.getFilePath(localRelPath);
			actualNode.parentRelPath = parentRelPath;
			insertActualNode(upgradeData.root.getSDb(), actualNode);
		}
		
		WriteBaton entryNode = null;
		if (isCalculateEntryNode) {
			entryNode = new WriteBaton();
			entryNode.base = baseNode;
			entryNode.work = workingNode;
			entryNode.belowWork = belowWorkingNode;
			entryNode.treeConflicts = treeConflicts;
		}
		
		if (entry.getExternalFilePath() != null) {
			/* TODO: Maybe add a file external registration inside EXTERNALS here, 
            to allow removing file externals that aren't referenced from svn:externals.
      		The svn:externals values are processed anyway after everything is upgraded */
		}
		return entryNode;
	}
	
	/* No transaction required: called from write_entry which is itself transaction-wrapped. */
	private void insertNode(SVNSqlJetDb sDb, DbNode node) throws SVNException {
		assert(node.opDepth > 0 || node.reposRelPath != null);
		SVNSqlJetStatement stmt = sDb.getStatement(SVNWCDbStatements.INSERT_NODE);
		stmt.bindf("isisnnnnsnrisnnni",  
				node.wcId,
				node.localRelPath == null ? "" : node.localRelPath,
				node.opDepth,
				node.parentRelPath,
				/* Setting depth for files? */
				(node.kind == SVNNodeKind.DIR) ? SVNDepth.asString(node.depth) : null,
				node.changedRev,
				node.changedDate != null ? node.changedDate : 0,
				node.changedAuthor,
				node.lastModTime
				);
		
		if (node.reposRelPath != null) {
			stmt.bindLong(5, node.reposId);
			stmt.bindString(6, node.reposRelPath);
			stmt.bindLong(7, node.revision);
		}
		
		stmt.bindString(8, SvnWcDbStatementUtil.getPresenceText(node.presence));
		
		if (node.kind == SVNNodeKind.NONE) 
			stmt.bindString(10, "unknown");
		else
			stmt.bindString(10, node.kind.toString());
			
		if (node.kind == SVNNodeKind.FILE) 
			stmt.bindChecksum(14, node.checksum);
		
		if (node.properties != null)  /* ### Never set, props done later */
			stmt.bindProperties(15, node.properties);
		
		if (node.translatedSize != ISVNWCDb.INVALID_FILESIZE) 
			stmt.bindLong(16, node.translatedSize);
		
		if (node.isFileExternal)
			stmt.bindLong(20, 1);
		
		stmt.done();
	}
	
	private void insertActualNode(SVNSqlJetDb sDb, DbActualNode actualNode) throws SVNException {
		SVNSqlJetStatement stmt = sDb.getStatement(SVNWCDbStatements.INSERT_ACTUAL_NODE);
		stmt.bindLong(1, actualNode.wcId);
		stmt.bindString(2, actualNode.localRelPath);
		stmt.bindString(3, actualNode.parentRelPath);
		if (actualNode.properties != null)
			stmt.bindProperties(4, actualNode.properties);
		if (actualNode.conflictOld != null) {
			stmt.bindString(5, actualNode.conflictOld);
			stmt.bindString(6, actualNode.conflictNew);
			stmt.bindString(7, actualNode.conflictWorking);
		}
		if (actualNode.propReject != null) 
			stmt.bindString(8, actualNode.propReject);
		if (actualNode.changelist != null) 
			stmt.bindString(9, actualNode.changelist);
		if (actualNode.treeConflictData != null) 
			stmt.bindString(10, actualNode.treeConflictData);
		stmt.done();
	}
	
	private void writeActualOnlyEntries(SVNHashMap treeConflicts, SVNSqlJetDb sDb, long wcId, String dirRelPath) throws SVNException {
		for (Iterator<String> items = treeConflicts.keySet().iterator(); items.hasNext();) {
			String path = items.next();
			DbActualNode actualNode = new DbActualNode();
			actualNode.wcId = wcId;
			actualNode.localRelPath = path;
			actualNode.parentRelPath = dirRelPath;
			actualNode.treeConflictData = (String)treeConflicts.get(path);
			insertActualNode(sDb, actualNode);
		}
	}
	
 }
