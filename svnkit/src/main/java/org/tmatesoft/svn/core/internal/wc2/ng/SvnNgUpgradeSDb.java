package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectFieldsStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetUpdateStatement;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.internal.wc2.old.SvnOldUpgrade;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgUpgradeSDb {
	/* Return a string indicating the released version (or versions) of Subversion that used WC format number WC_FORMAT, or some other
	 * suitable string if no released version used WC_FORMAT.
	 *
	 * ### It's not ideal to encode this sort of knowledge in this low-level library.  On the other hand, it doesn't need to be updated often and
	 * should be easily found when it does need to be updated.  */
	private static String versionStringFromFormat(int wcFormat) {
		switch (wcFormat) {
			case 4: return "<=1.3";
			case 8: return "1.4";
			case 9: return "1.5";
			case 10: return "1.6";
		}
	  return "(unreleased development version)";
	}
	
	public static int upgrade(final File wcrootAbsPath, final SVNSqlJetDb sDb, int startFormat) throws SVNException {
		int resultFormat = 0;
		File bumpWcRootAbsPath = wcrootAbsPath;
		
		if (startFormat < SVNWCContext.WC_NG_VERSION /* 12 */) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Working copy ''{0}'' is too old (format {1}, created by Subversion {3})", 
					wcrootAbsPath, startFormat, versionStringFromFormat(startFormat));
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		
		/* Early WCNG formats no longer supported. */
		if (startFormat < 19) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
					"Working copy ''{0}'' is an old development version (format {1}); to upgrade it, use a format 18 client, then " +
					"use ''tools/dev/wc-ng/bump-to-19.py'', then use the current client",
					wcrootAbsPath, startFormat);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		
		/* ### need lock-out. only one upgrade at a time. note that other code cannot use this un-upgraded database until we finish the upgrade.  */

		/* Note: none of these have "break" statements; the fall-through is intentional. */
		switch (startFormat)
	    {
	      case 19:
	    	  sDb.beginTransaction(SqlJetTransactionMode.WRITE);
	    	  try {
	    		  bumpTo20(sDb, wcrootAbsPath);
	    	  } catch (SVNException e) {
	    		  sDb.rollback();
	    		  throw e;
	    	  }
	    	  finally {
	    		  sDb.commit();
	    	  }
	        resultFormat = 20;
	        /* FALLTHROUGH  */
	      case 20:
	    	  sDb.beginTransaction(SqlJetTransactionMode.WRITE);
	    	  try {
	    		  bumpTo21(sDb, wcrootAbsPath);
	    	  } catch (SVNException e) {
	    		  sDb.rollback();
	    		  throw e;
	    	  }
	    	  finally {
	    		  sDb.commit();
	    	  }
	        resultFormat = 21;
	        /* FALLTHROUGH  */

	      case 21:
	    	  sDb.beginTransaction(SqlJetTransactionMode.WRITE);
	    	  try {
	    		  bumpTo22(sDb, wcrootAbsPath);
	    	  } catch (SVNException e) {
	    		  sDb.rollback();
	    		  throw e;
	    	  }
	    	  finally {
	    		  sDb.commit();
	    	  }
	        resultFormat = 22;
	        /* FALLTHROUGH  */

	      case 22:
	    	  sDb.beginTransaction(SqlJetTransactionMode.WRITE);
	    	  try {
	    		  bumpTo23(sDb, wcrootAbsPath);
	    	  } catch (SVNException e) {
	    		  sDb.rollback();
	    		  throw e;
	    	  }
	    	  finally {
	    		  sDb.commit();
	    	  }
	        resultFormat = 23;
	        /* FALLTHROUGH  */

	      case 23:
	        //SVN_ERR(svn_sqlite__with_transaction(sdb, bump_to_24, &bb, scratch_pool));
	        resultFormat = 24;
	        /* FALLTHROUGH  */

	      case 24:
	        //SVN_ERR(svn_sqlite__with_transaction(sdb, bump_to_25, &bb, scratch_pool));
	        resultFormat = 25;
	        /* FALLTHROUGH  */

	      case 25:
	        //SVN_ERR(svn_sqlite__with_transaction(sdb, bump_to_26, &bb, scratch_pool));
	        resultFormat = 26;
	        /* FALLTHROUGH  */

	      case 26:
	        //SVN_ERR(svn_sqlite__with_transaction(sdb, bump_to_27, &bb, scratch_pool));
	        resultFormat = 27;
	        /* FALLTHROUGH  */

	      case 27:
	        //SVN_ERR(svn_sqlite__with_transaction(sdb, bump_to_28, &bb, scratch_pool));
	        resultFormat = 28;
	        /* FALLTHROUGH  */

	      case 28:
	        //SVN_ERR(svn_sqlite__with_transaction(sdb, bump_to_29, &bb, scratch_pool));
	        resultFormat = 29;
	        /* FALLTHROUGH  */

	      /* ### future bumps go here.  */
	      //#if 0
	      		//case XXX-1:
	        	/* Revamp the recording of tree conflicts.  */
	        	//SVN_ERR(svn_sqlite__with_transaction(sdb, bump_to_XXX, &bb, scratch_pool));
	        	//*result_format = XXX;
	        	/* FALLTHROUGH  */
	       //#endif*/
	       
	    }

		/*
	  #ifdef SVN_DEBUG
	  if (*result_format != start_format)
	    {
	      int schema_version;
	      SVN_ERR(svn_sqlite__read_schema_version(&schema_version, sdb, scratch_pool));

	      /* If this assertion fails the schema isn't updated correctly /
	      SVN_ERR_ASSERT(schema_version == *result_format);
	    }
	  #endif
	  */

	  /* Zap anything that might be remaining or escaped our notice.  */
	  SvnOldUpgrade.wipeObsoleteFiles(wcrootAbsPath);

	  return resultFormat;
	}
	
	public static void bumpTo20(SVNSqlJetDb sDb, File wcRootAbsPath) throws SVNException {
		/*!!!
		sDb.getDb().createTable("CREATE TABLE NODES ( wc_id  INTEGER NOT NULL REFERENCES WCROOT (id), local_relpath  TEXT NOT NULL, op_depth INTEGER NOT NULL, "
                + "  parent_relpath  TEXT, repos_id  INTEGER REFERENCES REPOSITORY (id), repos_path  TEXT, revision  INTEGER, presence  TEXT NOT NULL, "
                + "  moved_here  INTEGER, moved_to  TEXT, kind  TEXT NOT NULL, properties  BLOB, depth  TEXT, checksum  TEXT, symlink_target  TEXT, "
                + "  changed_revision  INTEGER, changed_date INTEGER, changed_author TEXT, translated_size  INTEGER, last_mod_time  INTEGER, "
                + "  dav_cache  BLOB, file_external  TEXT, PRIMARY KEY (wc_id, local_relpath, op_depth) ); ");
		sDb.getDb().createIndex("CREATE INDEX I_NODES_PARENT ON NODES (wc_id, parent_relpath, op_depth); ");
		*/
        	
		//SVN_ERR(svn_sqlite__exec_statements(sdb, STMT_UPGRADE_TO_20));
	}
	
	private static void migrateTreeConflictData(SVNSqlJetDb sDb) throws SVNException
	{
		SVNSqlJetStatement stmt = new SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.ACTUAL_NODE__Fields>(sDb, SVNWCDbSchema.ACTUAL_NODE) {
			protected void defineFields() {
				fields.add(SVNWCDbSchema.ACTUAL_NODE__Fields.wc_id);
				fields.add(SVNWCDbSchema.ACTUAL_NODE__Fields.local_relpath);
				fields.add(SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data);
			}
			protected boolean isFilterPassed() throws SVNException {
		        return !isColumnNull(SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data);
		    }
		};
        /* Iterate over each node which has a set of tree conflicts, then insert all of them into the new schema.  */
		try {
			while (stmt.next()) {
				migrateSingleTreeConflictData(sDb, 
						stmt.getColumnString(SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data),
			    		stmt.getColumnLong(SVNWCDbSchema.ACTUAL_NODE__Fields.wc_id),
			    		SVNFileUtil.createFilePath(stmt.getColumnString(SVNWCDbSchema.ACTUAL_NODE__Fields.local_relpath)));
			}
		} finally {
			stmt.reset();
		}
		
		/* Erase all the old tree conflict data.  */
		stmt = new SVNSqlJetUpdateStatement(sDb, SVNWCDbSchema.ACTUAL_NODE) {
			public Map<String, Object> getUpdateValues() throws SVNException {
				Map<String, Object> rowValues = getRowValues();
		        rowValues.put(SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data.toString(), null);
		        return rowValues;
			}
		};
		try {
			stmt.exec();
		} finally {
			stmt.reset();
		}
	}
		
	private static void migrateSingleTreeConflictData(SVNSqlJetDb sDb, String treeConflictData, long wcId, File localRelPath) throws SVNException {
		Map conflicts = SVNTreeConflictUtil.readTreeConflicts(localRelPath, treeConflictData);
		for (Iterator keys = conflicts.keySet().iterator(); keys.hasNext();) {
            File entryPath = (File)keys.next();
            SVNTreeConflictDescription conflict = (SVNTreeConflictDescription) conflicts.get(entryPath);
			
			//String conflictRelpath = SVNFileUtil.getFilePath(
			//		SVNFileUtil.createFilePath(localRelPath, SVNFileUtil.getBasePath(conflict.getPath())));
				 
			/* See if we need to update or insert an ACTUAL node. */
			SVNSqlJetStatement stmt = sDb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE); 
			stmt.bindf("is", wcId, conflict.getPath());
			
			boolean haveRow = false;
			try {
				haveRow = stmt.next();
			} finally {
				stmt.reset();
			}
			
			if (haveRow) {
				/* There is an existing ACTUAL row, so just update it. */
				stmt = sDb.getStatement(SVNWCDbStatements.UPDATE_ACTUAL_CONFLICT_DATA);
			} else {
				 /* We need to insert an ACTUAL row with the tree conflict data. */
				stmt = sDb.getStatement(SVNWCDbStatements.INSERT_ACTUAL_CONFLICT_DATA);
			}
			
			stmt.bindf("iss", wcId, conflict.getPath(), SVNTreeConflictUtil.getSingleTreeConflictData(conflict));
			if (!haveRow)
				stmt.bindString(4, SVNFileUtil.getFilePath(localRelPath));
			
			try {
				stmt.exec();
			} finally {
				stmt.reset();
			}
			
        }
	}
	
	private static void setVersion(SVNSqlJetDb sDb, int version) throws SVNException {
		try {
			sDb.getDb().pragma("pragma user_version = " + version);
		} catch (SqlJetException e) {
			SVNSqlJetDb.createSqlJetError(e);
		}
	}
	
	private static void bumpTo21(SVNSqlJetDb sDb, File wcRootAbsPath) throws SVNException {
		setVersion(sDb, (int)21);
		migrateTreeConflictData(sDb);
	}
	
	public static void bumpTo22(SVNSqlJetDb sDb, File wcRootAbsPath) throws SVNException {
		//UPDATE actual_node SET tree_conflict_data = conflict_data;
		SVNSqlJetUpdateStatement stmt = new SVNSqlJetUpdateStatement(sDb, SVNWCDbSchema.ACTUAL_NODE) {
			public Map<String, Object> getUpdateValues() throws SVNException {
				Map<String, Object> rowValues = getRowValues();
		        rowValues.put(SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data.toString(), 
		        		rowValues.get(SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_data.toString()));
		        return rowValues;
			}
		};
		try {
			stmt.exec();
		} finally {
			stmt.reset();
		}
		
		//UPDATE actual_node SET conflict_data = NULL;
		stmt = new SVNSqlJetUpdateStatement(sDb, SVNWCDbSchema.ACTUAL_NODE) {
			public Map<String, Object> getUpdateValues() throws SVNException {
				Map<String, Object> rowValues = getRowValues();
		        rowValues.put(SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_data.toString(), null);
		        return rowValues;
			}
		};
		try {
			stmt.exec();
		} finally {
			stmt.reset();
		}

		setVersion(sDb, (int)22);
	}
	
	public static void bumpTo23(SVNSqlJetDb sDb, File wcRootAbsPath) throws SVNException {
		//-- STMT_HAS_WORKING_NODES
		//SELECT 1 FROM nodes WHERE op_depth > 0
		//LIMIT 1
		SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.NODES__Fields> stmt = new SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.NODES__Fields>(sDb, SVNWCDbSchema.NODES) {
			protected void defineFields() {
				fields.add(SVNWCDbSchema.NODES__Fields.wc_id);
			}
			protected boolean isFilterPassed() throws SVNException {
		        return getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth) > 0;
		    }
		};
		
		boolean haveRow = false;
		try {
			haveRow = stmt.next();
		} finally {
			stmt.reset();
		}
		
		if (haveRow) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
					"The working copy at ''{0}'' is format 22 with WORKING nodes; use a format 22 client to diff/revert before using this client", 
					wcRootAbsPath);
			SVNErrorManager.error(err, SVNLogType.WC);
		}

		setVersion(sDb, (int)23);
	
	}
}
