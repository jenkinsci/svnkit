package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.util.Date;

public class SvnAnnotateItem {
	private Date date;
	private long revision;
	private String author;
	private String line;
	private Date mergedDate; 
    private long mergedRevision;
    private String mergedAuthor;
    private String mergedPath;
    private int lineNumber;
    private File contents;
    private boolean isEof;
    private boolean isRevision;
    private boolean isLine;
    
    public SvnAnnotateItem(boolean isEof)
    {
    	this.isEof = true;
    }
    
    public SvnAnnotateItem(Date date, long revision, String author, String line, Date mergedDate, 
            long mergedRevision, String mergedAuthor, String mergedPath, int lineNumber) {
    	this.isLine = true;
    	this.date = date;
    	this.revision = revision;
    	this.author = author;
    	this.line = line;
    	this.mergedDate = mergedDate;
    	this.mergedRevision = mergedRevision;
    	this.mergedAuthor = mergedAuthor;
    	this.mergedPath = mergedPath;
    	this.lineNumber = lineNumber;
    }
    
    public SvnAnnotateItem(Date date, long revision, String author, File contents) {
    	this.isRevision = true;
    	this.date = date;
    	this.revision = revision;
    	this.author = author;
    	this.contents = contents;
    }
    
    public Date getDate() {
		return date;
	}
	
	public long getRevision() {
		return revision;
	}
	
	public String getAuthor() {
		return author;
	}
	
	public Date getMergedDate() {
		return mergedDate;
	}
	
	public String getLine() {
		return line;
	}
	
	public long getMergedRevision() {
		return mergedRevision;
	}
	
	public String getMergedAuthor() {
		return mergedAuthor;
	}
	
	public String getMergedPath() {
		return mergedPath;
	}
	
	public int getLineNumber() {
		return lineNumber;
	}
	
	public File getContents() {
		return contents;
	}
	
	public boolean isEof() {
	 return isEof;
	}
	
	public boolean isLine() {
		return isLine;
	}
	
	public boolean isRevision() {
		return isRevision;
	}
	
	
    
    

}
