package org.tmatesoft.svn.core.internal.wc17.db;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SvnWcDbRevertList implements Comparator<SvnWcDbRevertList.RevertListRow> {

    private Map<String, RevertListRow> rows = new HashMap<String, RevertListRow>();
    private Map<String, RevertListRow> actualRows = new HashMap<String, RevertListRow>();
    
    public static class RevertListRow {
        String localRelpath;
        long actual;
        String conflictOld;
        String conflictNew;
        String conflictWorking;
        String propReject;
        long notify;
        long opDepth;
        long reposId;
        String kind;
        
        public int hashCode() {
            return (int) (localRelpath.hashCode() + 57*actual);
        }
        
        public boolean equals(Object o) {
            if (o instanceof RevertListRow) {
                RevertListRow that = (RevertListRow) o;
                return that.localRelpath.equals(localRelpath) && that.actual == actual;
             }
            return false;
        }
    }

    public int compare(RevertListRow r1, RevertListRow r2) {
        if (r1 == r2) {
            return 0;
        } else if (r1 == null || r2 == null) {
            return r1 == null ? 1 : -1;
        }
        final int paths = r1.localRelpath.compareTo(r2.localRelpath);
        if (paths != 0) {
            return paths;
        }
        return r1.actual > r2.actual ? 1 : (r1.actual == r2.actual ? 0 : -1);
    }
    
    public Iterator<RevertListRow> rows() {
        final Set<RevertListRow> combinedRows = new TreeSet<SvnWcDbRevertList.RevertListRow>(this);
        combinedRows.addAll(rows.values());
        combinedRows.addAll(actualRows.values());
        return combinedRows.iterator();
    }
    
    public void insertRow(String localRelpath, long actual, String conflictOld, String conflictNew, String conflictWorking,
            String propReject, long notify) {
        final RevertListRow row = new RevertListRow();
        row.localRelpath = localRelpath;
        row.actual = actual;
        row.conflictNew = conflictNew;
        row.conflictOld = conflictOld;
        row.conflictWorking = conflictWorking;
        row.propReject = propReject;
        row.notify = notify;
        
        if (actual == 1) {
            actualRows.put(localRelpath, row);
        } else {
            rows.put(localRelpath, row);
        }
    }

    public void insertRow(String localRelpath, int actual, long opDepth, long reposId, String kind) {
        final RevertListRow row = new RevertListRow();
        row.localRelpath = localRelpath;
        row.actual = actual;
        row.opDepth = opDepth;
        row.reposId = reposId;
        row.kind = kind;
        if (actual == 1) {
            actualRows.put(localRelpath, row);
        } else {
            rows.put(localRelpath, row);
        }
    }
    
    public RevertListRow getRow(String localRelpath) {
        return rows.get(localRelpath);
    }

    public RevertListRow getActualRow(String localRelpath) {
        return actualRows.get(localRelpath);
    }

    public void deleteRow(String filePath) {
        actualRows.remove(filePath);
        rows.remove(filePath);        
    }

}
