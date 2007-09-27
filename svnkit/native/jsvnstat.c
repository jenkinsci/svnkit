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
#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include "jsvnstat.h"

/*
 * return values: int[3] attributes
 * attributes[0] & 0xF - type and can be one of the following values:
 *   1 - ordinar file
 *   2 - directory
 *   3 - symlink
 *   4 - unknown
 *   5 - error occurres during system call
 * 
 * int uPerms = attributes[0] >> 12; - user permissions
 * int gPerms = attributes[0] >> 8;  - group permissions
 * int oPerms = attributes[0] >> 4;  - world permissions
 * 
 * int uid = attributes[1];          - user id
 * int gid = attributes[2];          - group id
 * 
 */
JNIEXPORT jintArray JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_getAttributes 
    (JNIEnv *env, jclass clazz, jstring path, jboolean find_links) {
    
    struct stat info;
    int srv, attributes, uid, gid;
    const char* p_path;
	mode_t mode;
	jintArray attributesArray;
	jint *arrayElements;
	
    p_path = (char*)((*env)->GetStringUTFChars(env, path, 0));
    
    if (find_links)
        srv = lstat(p_path, &info);
    else
        srv = stat(p_path, &info);

    if (srv == 0) {
    	mode = info.st_mode;
    	
    	switch (mode & S_IFMT) {
        case S_IFREG:
            attributes = 1;  
            break;
        case S_IFDIR:
            attributes = 2;  
            break;
        case S_IFLNK:
            attributes = 3;  
            break;
        default:
            attributes = 4;  
            break;
        }
    
    	if (mode & S_IRUSR) {
    		attributes |= (0x4 << 12);	
    	}
    	if (mode & S_IWUSR) {
    		attributes |= (0x2 << 12);	
    	}
    	if (mode & S_IXUSR) {
    		attributes |= (0x1 << 12);	
    	}

    	if (mode & S_IRGRP) {
    		attributes |= (0x4 << 8);	
    	}
    	if (mode & S_IWGRP) {
    		attributes |= (0x2 << 8);	
    	}
    	if (mode & S_IXGRP) {
    		attributes |= (0x1 << 8);	
    	}
    	
    	if (mode & S_IROTH) {
    		attributes |= (0x4 << 4);	
    	}
    	if (mode & S_IWOTH) {
    		attributes |= (0x2 << 4);	
    	}
    	if (mode & S_IXOTH) {
    		attributes |= (0x1 << 4);	
    	}
    	
    	gid = info.st_gid;
    	uid = info.st_uid;
    } else {
    	attributes = 5;
    }

	attributesArray = (*env)->NewIntArray(env, 3);
    arrayElements = (*env)->GetIntArrayElements(env, attributesArray, 0);
	arrayElements[0] = attributes;
	arrayElements[1] = uid;
	arrayElements[2] = gid;
	(*env)->ReleaseIntArrayElements(env, attributesArray, arrayElements, 0);
    (*env)->ReleaseStringUTFChars(env, path, p_path);
    return attributesArray;
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

