/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.lisapark.octopus.util.json;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

/**
 *
 * @author alex
 */
public class WJutils {

    public static void main(String[] args) {
        
        WJutils wjutils = new WJutils();
        
        // Get Gson object
        Gson gson = new GsonBuilder().setPrettyPrinting().create();        
        
        String url = "http://api.wayjournal.com/get_roads";
//        String url = "http://map.wayjournal.com/graph/properties";
        String wjResponse = wjutils.getFileList(url);
        
        WJresponse resp = gson.fromJson(wjResponse, WJresponse.class);
        
        List<WJfile> files = Arrays.asList(resp.getFiles());
        
        for (WJfile file : files) {
            System.out.println(file);
        }

    }   

    public String getFileList(String url) {
       
        String answer = "";
        HttpClient client = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(url);
        try {
            HttpResponse response = client.execute(httpGet);
            answer = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);

        } catch (IOException e) {
        } 
        
        return answer;
    }
    
    public class WJresponse {
        private String cmd;
        private int code;
        
        private WJfile[] files;

        /**
         * @return the cmd
         */
        public String getCmd() {
            return cmd;
        }

        /**
         * @param cmd the cmd to set
         */
        public void setCmd(String cmd) {
            this.cmd = cmd;
        }

        /**
         * @return the code
         */
        public int getCode() {
            return code;
        }

        /**
         * @param code the code to set
         */
        public void setCode(int code) {
            this.code = code;
        }

        /**
         * @return the files
         */
        public WJfile[] getFiles() {
            return files;
        }

        /**
         * @param files the files to set
         */
        public void setFiles(WJfile[] files) {
            this.files = files;
        }
    }
    
    public class WJfile {
        
        public static final String FILE_NAME = "file_name";
        public static final String FILENAME = "filename";
        public static final String URL = "url";
        public static final String SIZE = "size";
        
        private String file_name;
//        private String filename;
        private String url;
        private long size;

        /**
         * @return the file_name
         */
        public String getFile_name() {
            return file_name;
        }

        /**
         * @param file_name the file_name to set
         */
        public void setFile_name(String file_name) {
            this.file_name = file_name;
        }

        /**
         * @return the filename
         */
        public String getFilename() {
            return file_name;
        }

        /**
         * @param filename the filename to set
         */
        public void setFilename(String filename) {
            this.file_name = filename;
        }
        
        /**
         * @return the url
         */
        public String getUrl() {
            return url;
        }

        /**
         * @param url the url to set
         */
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * @return the size
         */
        public long getSize() {
            return size;
        }

        /**
         * @param size the size to set
         */
        public void setSize(long size) {
            this.size = size;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = Maps.newHashMap();
            
            map.put("file_name", getFile_name());
            map.put("filename", getFilename());
            map.put("url", getUrl());
            map.put("size", getSize());
            
            return map;            
        }
        
        @Override
        public String toString(){
            return toMap().toString();
        }        

    }
}
