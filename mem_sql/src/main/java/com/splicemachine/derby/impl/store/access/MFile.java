/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.derby.impl.store.access;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.io.StorageFactory;
import com.splicemachine.db.io.StorageFile;
import com.splicemachine.db.io.StorageRandomAccessFile;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * @author Scott Fines
 *         Date: 1/21/16
 */
class MFile implements StorageFile{
    private final MemStorageFactory storage;
    private final Path path;
    private final boolean isDir;
    private byte[] data;

    private boolean created = false;

    public MFile(MemStorageFactory storage,
            String directoryName,
                 String fileName){
        if(fileName!=null){
            this.path=Paths.get(directoryName,fileName);
            this.isDir=false;
        }else{
            this.path=Paths.get(directoryName);
            this.isDir=false;
        }
        this.storage = storage;
    }
    public MFile(MemStorageFactory storage,Path p,boolean isDir){
        this.path = p;
        this.storage = storage;
        this.isDir = isDir;
    }

    @Override
    public String toString(){
        return getPath();
    }

    @Override
    public String[] list(){
        List<Path> children = storage.getChildren(path);
        String[] childNames = new String[children.size()];
        int i=0;
        for(Path p:children){
            childNames[i] = p.getFileName().toString();
            i++;
        }
        return childNames;
    }

    @Override
    public boolean canWrite(){
        return !isDir;
    }

    @Override
    public boolean exists(){
        return created;
    }

    @Override
    public boolean isDirectory(){
        return isDir;
    }

    @Override
    public boolean delete(){
        data = null;
        created=false;
        storage.remove(this);
        return true;
    }

    @Override
    public boolean deleteAll(){
        return delete();
    }

    @Override
    public String getPath(){
        return path.toAbsolutePath().toString();
    }

    @Override
    public String getCanonicalPath() throws IOException{
        return getPath();
    }

    @Override
    public String getName(){
        Path fileName=path.getFileName();
        return fileName.toString();
    }

    @Override
    public URL getURL() throws MalformedURLException{
        return path.toUri().toURL();
    }

    @Override
    public boolean createNewFile() throws IOException{
        created=true;
        return true;
    }

    @Override
    public boolean renameTo(StorageFile newName){
        return false;
    }

    @Override
    public boolean mkdir(){
        created=true;
        return true;
    }

    @Override
    public boolean mkdirs(){
        created=true;
        return true;
    }

    @Override
    public long length(){
        return data.length;
    }

    @Override
    public StorageFile getParentDir(){
        Path parent=path.getParent();
        if(parent==null)
            return new MFile(storage,"/",null);
        else return new MFile(storage,parent,true);
    }

    @Override
    public boolean setReadOnly(){
        return false;
    }

    @Override
    public OutputStream getOutputStream() throws FileNotFoundException{
        return getOutputStream(false);
    }

    @Override
    public OutputStream getOutputStream(boolean append) throws FileNotFoundException{
        return new ByteArrayOutputStream(){
            @Override
            public void close() throws IOException{
                MFile.this.data =super.toByteArray();
                super.close();
            }
        };
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException{
        if(this.data==null)
            return new ByteArrayInputStream(new byte[]{});
        else
            return new ByteArrayInputStream(this.data);
    }

    @Override
    public int getExclusiveFileLock() throws StandardException{
        return 0;
    }

    @Override
    public void releaseExclusiveFileLock(){

    }

    @Override
    public StorageRandomAccessFile getRandomAccessFile(String mode) throws FileNotFoundException{
        return null;
    }

    @Override
    public void limitAccessToOwner(){

    }

    Path path(){
        return path;
    }
}
