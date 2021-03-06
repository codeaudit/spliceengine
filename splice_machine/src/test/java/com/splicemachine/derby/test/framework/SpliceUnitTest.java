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

package com.splicemachine.derby.test.framework;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spark_project.guava.base.Joiner;
import org.junit.Assert;
import org.junit.runner.Description;
import com.splicemachine.utils.Pair;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

public class SpliceUnitTest {

    private static Pattern overallCostP = Pattern.compile("totalCost=[0-9]+\\.?[0-9]*");
    private static Pattern outputRowsP = Pattern.compile("outputRows=[0-9]+\\.?[0-9]*");

	public String getSchemaName() {
		Class<?> enclosingClass = getClass().getEnclosingClass();
		if (enclosingClass != null)
		    return enclosingClass.getSimpleName().toUpperCase();
		else
		    return getClass().getSimpleName().toUpperCase();
	}

    /**
     * Load a table with given values
     *
     * @param statement calling test's statement that may be in txn
     * @param tableName fully-qualified table name, i.e., <pre>schema.table</pre>
     * @param values list of row values
     * @throws Exception
     */
    public static void loadTable(Statement statement, String tableName, List<String> values) throws Exception {
        for (String rowVal : values) {
            statement.executeUpdate("insert into " + tableName + " values " + rowVal);
        }
    }

    public String getTableReference(String tableName) {
		return getSchemaName() + "." + tableName;
	}

	public String getPaddedTableReference(String tableName) {
		return " " + getSchemaName() + "." + tableName.toUpperCase()+ " ";
	}

	
	public static int resultSetSize(ResultSet rs) throws Exception {
		int i = 0;
		while (rs.next()) {
			i++;
		}
		return i;
	}

    public static int columnWidth(ResultSet rs ) throws SQLException {
        return rs.getMetaData().getColumnCount();
    }

	public static String format(String format, Object...args) {
		return String.format(format, args);
	}
	public static String getBaseDirectory() {
		String userDir = System.getProperty("user.dir");
        /*
         * The ITs can run in multiple different locations based on the different architectures
         * that are available, but the actual test data files are located in the splice_machine directory; thus,
         * to find the source file, we have to do a little looking around. Implicitely, the ITs run
         * in a sibling directory to splice_machine (like mem_sql or hbase_sql), so we need to make
         * sure that we go up and over to the splice_machine directory.
         *
         * Of course, if we are in the correct location to begin with, then we are good to go.
         */
        if(userDir.endsWith("splice_machine")) return userDir;

        Path nioPath = Paths.get(userDir);
        while(nioPath!=null){
            /*
             * Look for splice_machine in our parent hierarchy. If we can find it, then we are good
             */
            if(nioPath.endsWith("splice_machine")) break;
            nioPath = nioPath.getParent();
        }
        if(nioPath==null){
            /*
             * We did not find it in our parent hierarchy.  It's possible that it's in a child
             * directory of us, so look around at it directly
             */
            Path us = Paths.get(userDir);
            nioPath = Paths.get(us.toString(),"splice_machine");
            if(!Files.exists(nioPath)){
             /* Try to go up and to the left. If it's not
             * there, then we are screwed anyway, so just go with it
             */
                Path parent=Paths.get(userDir).getParent();
                nioPath=Paths.get(parent.toString(),"splice_machine");
            }
        }
        return nioPath.toString();
	}

    public static String getResourceDirectory() {
		return getBaseDirectory()+"/src/test/test-data/";
	}

    public static String getHbaseRootDirectory() {
        return getHBaseDirectory()+"/target/hbase";
    }

    public static String getHBaseDirectory() {
        String userDir = System.getProperty("user.dir");
        /*
         * The ITs can run in multiple different locations based on the different architectures
         * that are available, but the actual test data files are located in the splice_machine directory; thus,
         * to find the source file, we have to do a little looking around. Implicitely, the ITs run
         * in a sibling directory to splice_machine (like mem_sql or hbase_sql), so we need to make
         * sure that we go up and over to the splice_machine directory.
         *
         * Of course, if we are in the correct location to begin with, then we are good to go.
         */
        if(userDir.endsWith("hbase_sql")) return userDir;

        Path nioPath = Paths.get(userDir);
        while(nioPath!=null){
            /*
             * Look for splice_machine in our parent hierarchy. If we can find it, then we are good
             */
            if(nioPath.endsWith("hbase_sql")) break;
            nioPath = nioPath.getParent();
        }
        if(nioPath==null){
            /*
             * We did not find it in our parent hierarchy.  It's possible that it's in a child
             * directory of us, so look around at it directly
             */
            Path us = Paths.get(userDir);
            nioPath = Paths.get(us.toString(),"hbase_sql");
            if(!Files.exists(nioPath)){
             /* Try to go up and to the left. If it's not
             * there, then we are screwed anyway, so just go with it
             */
                Path parent=Paths.get(userDir).getParent();
                nioPath=Paths.get(parent.toString(),"hbase_sql");
            }
        }
        return nioPath.toString();
    }
    
    public static String getHiveWarehouseDirectory() {
		return getBaseDirectory()+"/user/hive/warehouse";
	}

    public static class MyWatcher extends SpliceTableWatcher {

        public MyWatcher(String tableName, String schemaName, String createString) {
            super(tableName, schemaName, createString);
        }

        public void create(Description desc) {
            super.starting(desc);
        }
    }

    protected void firstRowContainsQuery(String query, String contains,SpliceWatcher methodWatcher) throws Exception {
        rowContainsQuery(1,query,contains,methodWatcher);
    }

    protected void secondRowContainsQuery(String query, String contains,SpliceWatcher methodWatcher) throws Exception {
        rowContainsQuery(2,query,contains,methodWatcher);
    }

    protected void thirdRowContainsQuery(String query, String contains,SpliceWatcher methodWatcher) throws Exception {
        rowContainsQuery(3,query,contains,methodWatcher);
    }

    protected void fourthRowContainsQuery(String query, String contains,SpliceWatcher methodWatcher) throws Exception {
        rowContainsQuery(4,query,contains,methodWatcher);
    }

    protected void rowContainsQuery(int[] levels, String query,SpliceWatcher methodWatcher,String... contains) throws Exception {
        try(ResultSet resultSet = methodWatcher.executeQuery(query)){
            int i=0;
            int k=0;
            while(resultSet.next()){
                i++;
                for(int level : levels){
                    if(level==i){
                        Assert.assertTrue("failed query at level ("+level+"): \n"+query+"\nExpected: "+contains[k]+"\nWas: "
                                              +resultSet.getString(1),resultSet.getString(1).contains(contains[k]));
                        k++;
                    }
                }
            }
        }
    }

    protected void rowContainsCount(int[] levels, String query,SpliceWatcher methodWatcher,double[] counts, double[] deltas ) throws Exception {
        try(ResultSet resultSet = methodWatcher.executeQuery(query)){
            int i=0;
            int k=0;
            while(resultSet.next()){
                i++;
                for(int level : levels){
                    if(level==i){
                        Assert.assertEquals("failed query at level ("+level+"): \n"+query+"\nExpected: "+counts[k]+"\nWas: "
                                        +resultSet.getString(1),
                                counts[k],parseOutputRows(resultSet.getString(1)),deltas[k]);
                        k++;
                    }
                }
            }
        }
    }

    protected String getExplainMessage(int level, String query,SpliceWatcher methodWatcher) throws Exception {
        try(ResultSet resultSet = methodWatcher.executeQuery(query)){
            int i=0;
            int k=0;
            while(resultSet.next()){
                i++;
                if(level==i){
                    return resultSet.getString(1);
                }
            }
        }
        Assert.fail("Missing level: " + level);
        return null;
    }


    protected void rowContainsQuery(int level, String query, String contains, SpliceWatcher methodWatcher) throws Exception {
        try(ResultSet resultSet = methodWatcher.executeQuery(query)){
            for(int i=0;i<level;i++){
                resultSet.next();
            }
            String actualString=resultSet.getString(1);
            String failMessage=String.format("expected result of query '%s' to contain '%s' at row %,d but did not, actual result was '%s'",
                    query,contains,level,actualString);
            Assert.assertTrue(failMessage,actualString.contains(contains));
        }
    }


    protected void queryDoesNotContainString(String query, String notContains,SpliceWatcher methodWatcher) throws Exception {
        ResultSet resultSet = methodWatcher.executeQuery(query);
        while (resultSet.next())
            Assert.assertFalse("failed query: " + query + " -> " + resultSet.getString(1), resultSet.getString(1).contains(notContains));
    }

    public static void rowsContainsQuery(String query, Contains mustContain, SpliceWatcher methodWatcher) throws Exception {
        ResultSet resultSet = methodWatcher.executeQuery(query);
        int i = 0;
        for (Pair<Integer,String> p : mustContain.get()) {
            for (; i< p.getFirst();i++)
                resultSet.next();
            Assert.assertTrue("failed query: " + query + " -> " + resultSet.getString(1), resultSet.getString(1).contains(p.getSecond()));
        }
    }


    public static double parseTotalCost(String planMessage) {
        Matcher m1 = overallCostP.matcher(planMessage);
        Assert.assertTrue("No Overall cost found!", m1.find());
        return Double.parseDouble(m1.group().substring("totalCost=".length()));
    }

    public static double parseOutputRows(String planMessage) {
        Matcher m1 = outputRowsP.matcher(planMessage);
        Assert.assertTrue("No OutputRows found!", m1.find());
        return Double.parseDouble(m1.group().substring("outputRows=".length()));
    }

    public static class Contains {
        private List<Pair<Integer,String>> rows = new ArrayList<>();

        public Contains add(Integer row, String shouldContain) {
            rows.add(new Pair<>(row, shouldContain));
            return this;
        }

        public List<Pair<Integer,String>> get() {
            Collections.sort(this.rows, new Comparator<Pair<Integer, String>>() {
                @Override
                public int compare(Pair<Integer, String> p1, Pair<Integer, String> p2) {
                    return p1.getFirst().compareTo(p2.getFirst());
                }
            });
            return this.rows;
        }
    }

    protected static void importData(SpliceWatcher methodWatcher, String schema,String tableName, String fileName) throws Exception {
        String file = SpliceUnitTest.getResourceDirectory()+ fileName;
        PreparedStatement ps = methodWatcher.prepareStatement(String.format("call SYSCS_UTIL.IMPORT_DATA('%s','%s','%s','%s',',',null,null,null,null,1,null,true,'utf-8')", schema, tableName, null, file));
        ps.executeQuery();
    }

    protected static void validateImportResults(ResultSet resultSet, int good,int bad) throws SQLException {
        Assert.assertTrue("No rows returned!",resultSet.next());
        Assert.assertEquals("Incorrect number of files reported!",1,resultSet.getInt(3));
        Assert.assertEquals("Incorrect number of rows reported!",good,resultSet.getInt(1));
        Assert.assertEquals("Incorrect number of bad records reported!", bad, resultSet.getInt(2));
    }

    public static String printMsgSQLState(String testName, SQLException e) {
        // useful for debugging import errors
        StringBuilder buf =new StringBuilder(testName);
        buf.append("\n");
        int i =1;
        SQLException child = e;
        while (child != null) {
            buf.append(i++).append(" ").append(child.getSQLState()).append(" ")
                    .append(child.getLocalizedMessage()).append("\n");
            child = child.getNextException();
        }
        return buf.toString();
    }

    public static File createBadLogDirectory(String schemaName) {
        File badImportLogDirectory = new File(SpliceUnitTest.getBaseDirectory()+"/target/BAD/"+schemaName);
        if (badImportLogDirectory.exists()) {
            recursiveDelete(badImportLogDirectory);
        }
        assertTrue("Couldn't create "+badImportLogDirectory,badImportLogDirectory.mkdirs());
        assertTrue("Failed to create "+badImportLogDirectory,badImportLogDirectory.exists());
        return badImportLogDirectory;
    }

    public static void recursiveDelete(File file) {
        if (file != null) {
            File[] directoryFiles = file.listFiles();
            if (directoryFiles != null) {
                for (File aFile : directoryFiles) {
                    if (aFile.isDirectory()) {
                        recursiveDelete(aFile);
                    } else {
                        assertTrue("Couldn't delete " + aFile, aFile.delete());
                    }
                }
            }
            assertTrue("Couldn't delete "+file,file.delete());
        }
    }

    public static File createImportFileDirectory(String schemaName) {
        File importFileDirectory = new File(SpliceUnitTest.getBaseDirectory()+"/target/import_data/"+schemaName);
        if (importFileDirectory.exists()) {
            //noinspection ConstantConditions
            for (File file : importFileDirectory.listFiles()) {
                assertTrue("Couldn't create "+file,file.delete());
            }
            assertTrue("Couldn't create "+importFileDirectory,importFileDirectory.delete());
        }
        assertTrue("Couldn't create " + importFileDirectory, importFileDirectory.mkdirs());
        assertTrue("Failed to create "+importFileDirectory,importFileDirectory.exists());
        return importFileDirectory;
    }

    public static void assertBadFileContainsError(File directory, String importFileName,
                                                  String errorCode, String errorMsg) throws IOException {
        printBadFile(directory, importFileName, errorCode, errorMsg, true);
    }

    public static String printBadFile(File directory, String importFileName) throws IOException {
        return printBadFile(directory, importFileName, null, null, false);
    }

    public static String printBadFile(File directory, String importFileName, String errorCode, String errorMsg, boolean assertTrue) throws IOException {
        // look for file in the "baddir" directory with same name as import file ending in ".bad"
        String badFile = getBadFile(directory, importFileName);
        boolean exists = existsBadFile(directory, importFileName);
        if (exists) {
            List<String> badLines = Files.readAllLines((new File(directory, badFile)).toPath(), Charset.defaultCharset());
            if (errorCode != null && ! errorCode.isEmpty()) {
                // make sure at least one error entry contains the errorCode
                boolean found = false;
                Set<String> codes = new HashSet<>();
                for (String line : badLines) {
                    addCode(line, codes);
                    if (line.startsWith(errorCode)) {
                        found = true;
                        if (assertTrue && errorMsg != null) {
                            assertThat("Incorrect error message!", line, containsString(errorMsg));
                        }
                        break;
                    }
                }
                if (! found && assertTrue) {
                    fail("Didn't find expected SQLState '"+errorCode+"' in bad file: "+badFile+" Found: "+codes);
                }
            }
            return "Error file contents: "+badLines.toString();
        } else if (assertTrue) {
            fail("Bad file ["+badFile+"] does not exist.");
        }
        return "File does not exist: "+badFile;
    }

    private static void addCode(String line, Set<String> codes) {
        if (line != null && ! line.isEmpty()) {
            String[] parts = line.split("\\s");
            if (parts.length > 0) {
                codes.add(parts[0]);
            }
        }
    }

    public static String printImportFile(File directory, String fileName) throws IOException {
        File file = new File(directory, fileName);
        if (file.exists()) {
            List<String> badLines = new ArrayList<>();
            for(String line : Files.readAllLines(file.toPath(), Charset.defaultCharset())) {
                badLines.add("{" + line + "}");
            }
            return "File contents: "+badLines.toString();
        }
        return "File does not exist: "+file.getCanonicalPath();
    }


    public static SpliceUnitTest.TestFileGenerator generatePartialRow(File directory, String fileName, int size,
                                                                       List<int[]> fileData) throws IOException {
        SpliceUnitTest.TestFileGenerator generator = new SpliceUnitTest.TestFileGenerator(directory, fileName);
        try {
            for (int i = 0; i < size; i++) {
                int[] row = {i, 0}; //0 is the value sql chooses for null entries
                fileData.add(row);
                generator.row(row);
            }
        } finally {
            generator.close();
        }
        return generator;
    }

    public static SpliceUnitTest.TestFileGenerator generateFullRow(File directory, String fileName, int size,
                                                                    List<int[]> fileData,
                                                                    boolean duplicateLast) throws IOException {
        SpliceUnitTest.TestFileGenerator generator = new SpliceUnitTest.TestFileGenerator(directory, fileName);
        try {
            for (int i = 0; i < size; i++) {
                int[] row = {i, 2 * i};
                fileData.add(row);
                generator.row(row);
            }
            if (duplicateLast) {
                int[] row = {size - 1, 2 * (size - 1)};
                fileData.add(row);
                generator.row(row);
            }
        } finally {
            generator.close();
        }
        return generator;
    }

    public static boolean existsBadFile(File badDir, String prefix) {
        String[] files = badDir.list();
        for (String file : files) {
            if (file.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String getBadFile(File badDir, String prefix) {
        String[] files = badDir.list();
        for (String file : files) {
            if (file.startsWith(prefix)) {
                return file;
            }
        }
        return null;
    }
    /**
     * System to generate fake data points into a file. This way we can write out quick,
     * well known files without storing a bunch of extras anywhere.
     *
     * @author Scott Fines
     *         Date: 10/20/14
     */
    public static class TestFileGenerator implements Closeable {
        private final String fileName;
        private final File file;
        private BufferedWriter writer;
        private final Joiner joiner;

        public TestFileGenerator(File directory, String fileName) throws IOException {
            this.fileName = fileName+".csv";
            this.file = new File(directory, this.fileName);
            this.writer =  new BufferedWriter(new FileWriter(file));
            this.joiner = Joiner.on(",");
        }

        public TestFileGenerator row(String[] row) throws IOException {
            String line = joiner.join(row)+"\n";
            writer.write(line);
            return this;
        }

        public TestFileGenerator row(int[] row) throws IOException {
            String[] copy = new String[row.length];
            for(int i=0;i<row.length;i++){
                copy[i] = Integer.toString(row[i]);
            }
            return row(copy);
        }

        public String getFileName(){
            return this.fileName;
        }

        public String getFilePath() throws IOException {
            return file.getCanonicalPath();
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }

    public static class ResultList {
        private List<List<String>> resultList = new ArrayList<>();
        private List<List<String>> expectedList = new ArrayList<>();

        public static ResultList create() {
            return new ResultList();
        }

        public ResultList toFileRow(String... values) {
            if (values != null && values.length > 0) {
                resultList.add(Arrays.asList(values));
            }
            return this;
        }

        public ResultList expected(String... values) {
            if (values != null && values.length > 0) {
                expectedList.add(Arrays.asList(values));
            }
            return this;
        }

        public ResultList fill(PrintWriter pw) {
            for (List<String> row : resultList) {
                pw.println(Joiner.on(",").join((row)));
            }
            return this;
        }

        public int nRows() {
            return resultList.size();
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            for (List<String> row : expectedList) {
                buf.append(row.toString()).append("\n");
            }
            return buf.toString();
        }
    }

    public static String getJarFileForClass(Class clazz) throws Exception {
        if (clazz == null)
            return null;
        URL jarURL = clazz.getProtectionDomain().getCodeSource().getLocation();
        if (jarURL == null)
            return null;
        return jarURL.toURI().getPath();
    }

    public static void assertFailed(Connection connection, String sql, String errorState) {
        try {
            connection.createStatement().execute(sql);
            fail("Did not fail");
        } catch (Exception e) {
            assertTrue("Incorrect error type!", e instanceof SQLException);
            SQLException se = (SQLException) e;
            assertEquals("Incorrect error state!", errorState, se.getSQLState());
        }
    }
}
