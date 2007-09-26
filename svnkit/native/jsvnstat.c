/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
// jsvncrypt.c: Defines the entry point for the dll application.
//
#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include "jsvnstat.h"

/*
 * return values:
 *   1 - ordinar file
 *   2 - directory
 *   3 - symlink
 *   4 - unknown
 *  -1 - error occurres during system call 
 * 
 */
JNIEXPORT jint JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_getType 
    (JNIEnv *env, jclass clazz, jstring path, jboolean find_links) {
    
    struct stat info;
    int srv, ret_val;
    const char* p_path;
	mode_t mode;

    p_path = (char*)((*env)->GetStringUTFChars(env, path, 0));
    
    if (find_links)
        srv = lstat(p_path, &info);
    else
        srv = stat(p_path, &info);

    if (srv == 0) {
    	mode = info.st_mode;
    	
    	switch (mode & S_IFMT) {
        case S_IFREG:
            ret_val = 1;  
            break;
        case S_IFDIR:
            ret_val = 2;  
            break;
        case S_IFLNK:
            ret_val = 3;  
            break;
        default:
            ret_val = 4;  
            break;
        }
    } else {
    	ret_val = -1;
    }

    (*env)->ReleaseStringUTFChars(env, path, p_path);
    return ret_val;
}

JNIEXPORT jint JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_changeMode
    (JNIEnv *env, jclass clazz, jstring path, jboolean changeReadWrite, jboolean enableWrite, 
    jboolean changeExecutable, jboolean executable) {

    struct stat info;
    mode_t mode;
    const char* p_path;
    int srv;
	int result = 0;
	
    p_path = (char*)((*env)->GetStringUTFChars(env, path, 0));

    srv = stat(p_path, &info);
	
	if (srv == 0) {
		mode = info.st_mode;
	
		if (changeReadWrite) {
			if (enableWrite) {
				mode |= S_IWUSR | S_IWGRP | S_IWOTH;
			} else {
				mode &= ~(S_IWUSR | S_IWGRP | S_IWOTH);
			}
		}
	 
    	if (changeExecutable) {
    		if (executable) {
				mode |= S_IXUSR | S_IXGRP | S_IXOTH;  
    		} else {
				mode &= ~(S_IXUSR | S_IXGRP | S_IXOTH);  
	    	}
    	}
	
		if (mode != info.st_mode) {
	    	if (chmod(p_path, mode) != 0) {
    	    	result = 1;
    		} 
		}    
	}

	(*env)->ReleaseStringUTFChars(env, path, p_path);
	return result;
}

JNIEXPORT jint JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_link
    (JNIEnv *env, jclass clazz, jstring dstPath, jstring linkName) {

    struct stat info;
    mode_t mode;
    const char* p_dst;
    const char* p_link;
	int result = 0;
	
    p_dst = (char*)((*env)->GetStringUTFChars(env, dstPath, 0));
    p_link = (char*)((*env)->GetStringUTFChars(env, linkName, 0));
    result = symlink(p_dst, p_link);
	(*env)->ReleaseStringUTFChars(env, dstPath, p_dst);
	(*env)->ReleaseStringUTFChars(env, linkName, p_link);
	return result;
}
  
JNIEXPORT jstring JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_getLinkTargetPath
    (JNIEnv *env, jclass clazz, jstring linkPath) {
    
    struct stat info;
    int rv;
    const char* p_path;
	mode_t mode;
	char buf[1025];
	jstring target;
	
    p_path = (char*)((*env)->GetStringUTFChars(env, linkPath, 0));
    
    rv = readlink(p_path, buf, sizeof(buf) - 1);
    if (rv != -1) {
	    buf[rv] = '\0';
	    target = (*env)->NewStringUTF(env, buf);
    } else {
    	target = NULL;
    }
    
    (*env)->ReleaseStringUTFChars(env, linkPath, p_path);
    return target;
}

