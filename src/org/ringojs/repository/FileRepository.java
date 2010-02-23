/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile: FileRepository.java,v $
 * $Author: hannes $
 * $Revision: 1.14 $
 * $Date: 2006/04/07 14:37:11 $
 */

package org.ringojs.repository;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;
import java.util.ArrayList;

/**
 * Repository implementation for directories providing file resources
 */
public class FileRepository extends AbstractRepository {

    // Directory serving sub-repositories and file resources
    protected final File directory;

    protected long lastModified = -1;
    protected long lastChecksum = 0;
    protected long lastChecksumTime = 0;

    /**
     * Defines how long the checksum of the repository will be cached
     */
    final long cacheTime = 1000L;

    /**
     * Constructs a FileRepository using the given argument
     * @param initArgs absolute path to the directory
     */
    public FileRepository(String initArgs) throws IOException {
        this(new File(initArgs), null);
    }

    /**
     * Constructs a FileRepository using the given directory as top-level
     * repository
     * @param dir directory
     */
    public FileRepository(File dir) throws IOException {
        this(dir, null);
    }

    /**
     * Constructs a FileRepository using the given directory and top-level
     * repository
     * @param dir directory
     * @param parent top-level repository
     */
    protected FileRepository(File dir, FileRepository parent) throws IOException {
        // make sure our directory has an absolute path,
        // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4117557
        directory = dir.getCanonicalFile();

        if (parent == null) {
            path = name = directory.getAbsolutePath();
        } else {
            this.parent = parent;
            name = directory.getName();
            path = directory.getAbsolutePath();
        }
        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }
    }

    /**
     * Check whether the repository exists.
     * @return true if the repository exists.
     */
    public boolean exists() {
        return directory.exists() && directory.isDirectory();
    }

    /**
     * Get a child repository with the given name
     *
     * @param name the name of the repository
     * @return the child repository
     */
    public Repository getChildRepository(String name) throws IOException {
        if (".".equals(name)) {
            return this;
        } else if ("..".equals(name)) {
            return getParentRepository();
        }
        AbstractRepository child = repositories.get(name);
        if (child == null) {
            File f = new File(directory, name);
            child = new FileRepository(f, this);
            repositories.put(name, child);
        }
        return child;
    }

    /**
     * Returns the date the repository was last modified.
     *
     * @return last modified date
     */
    public long lastModified() {
        return directory.lastModified();
    }

    /**
     * Checksum of the repository and all its contained resources. Implementations
     * should make sure to return a different checksum if any contained resource
     * has changed.
     *
     * @return checksum
     */
    public synchronized long getChecksum() throws IOException {
        // delay checksum check if already checked recently
        if (System.currentTimeMillis() > lastChecksumTime + cacheTime) {
            // FIXME
            long checksum = lastModified;

            for (Resource res: resources.values()) {
                checksum += res.lastModified();
            }

            lastChecksum = checksum;
            lastChecksumTime = System.currentTimeMillis();
        }

        return lastChecksum;
    }

    /**
     * Called to create a child resource for this repository
     */
    @Override
    protected Resource lookupResource(String name) throws IOException {
        AbstractResource res = resources.get(name);
        if (res == null) {
            res = new FileResource(new File(directory, name), this);
            resources.put(name, res);
        }
        return res;
    }

    protected void getResources(List<Resource> list, boolean recursive)
            throws IOException {
        File[] dir = directory.listFiles();

        for (File file: dir) {
            if (file.isFile()) {
                Resource resource = lookupResource(file.getName());
                list.add(resource);
            } else if (recursive && file.isDirectory()) {
                AbstractRepository repo = (AbstractRepository) getChildRepository(file.getName());
                repo.getResources(list, true);
            }
        }
    }

    public Repository[] getRepositories() throws IOException {
        File[] dir = directory.listFiles();
        List<Repository> list = new ArrayList<Repository>(dir.length);

        for (File file: dir) {
            if (file.isDirectory()) {
                list.add(getChildRepository(file.getName()));
            }
        }
        return list.toArray(new Repository[list.size()]);
    }

    public URL getUrl() throws MalformedURLException {
        // Trailing slash on directories is required for ClassLoaders
        return new URL("file:" + path);
    }

    @Override
    public int hashCode() {
        return 17 + (37 * directory.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FileRepository &&
               directory.equals(((FileRepository) obj).directory);
    }

    @Override
    public String toString() {
        return new StringBuffer("FileRepository[").append(path).append("]").toString();
    }
}