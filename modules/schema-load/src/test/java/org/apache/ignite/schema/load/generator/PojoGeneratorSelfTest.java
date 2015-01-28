/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.schema.load.generator;

import junit.framework.TestCase;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.schema.generator.PojoGenerator;
import org.apache.ignite.schema.model.PojoDescriptor;
import org.apache.ignite.schema.parser.DatabaseMetadataParser;
import org.apache.ignite.schema.ui.ConfirmCallable;
import org.apache.ignite.schema.ui.MessageBox;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.apache.ignite.schema.ui.MessageBox.Result.YES_TO_ALL;

/**
 * Tests for POJO generator.
 */
public class PojoGeneratorSelfTest extends TestCase {
    /** Default connection URL (value is <tt>jdbc:h2:mem:jdbcCacheStore;DB_CLOSE_DELAY=-1</tt>). */
    protected static final String DFLT_CONN_URL = "jdbc:h2:mem:autoCacheStore;DB_CLOSE_DELAY=-1";

    /** File separator. */
    private static final String PS = File.separator;

    /** Path to temp folder where generated POJOs will be saved. */
    private static final String OUT_DIR_PATH = System.getProperty("java.io.tmpdir") + PS +
        "ignite-schema-loader" + PS + "out";

    /** Marker string to skip date generation while comparing.*/
    private static final String GEN_PTRN = "Code generated by Apache Ignite Schema Load utility";

    /** Connection to in-memory H2 database. */
    private Connection conn;

    /** {@inheritDoc} */
    @Override public void setUp() throws Exception {
        Class.forName("org.h2.Driver");

        conn = DriverManager.getConnection(DFLT_CONN_URL, "sa", "");

        Statement stmt = conn.createStatement();

        stmt.executeUpdate("CREATE TABLE PRIMITIVES (pk INTEGER PRIMARY KEY, " +
            " boolCol BOOLEAN NOT NULL, byteCol TINYINT NOT NULL, shortCol SMALLINT NOT NULL, intCol INTEGER NOT NULL, " +
            " longCol BIGINT NOT NULL, floatCol REAL NOT NULL, doubleCol DOUBLE NOT NULL, doubleCol2 DOUBLE NOT NULL, " +
            " bigDecimalCol DECIMAL(10, 0), strCol VARCHAR(10), dateCol DATE, timeCol TIME, tsCol TIMESTAMP, " +
            " arrCol BINARY(10))");

        stmt.executeUpdate("CREATE TABLE OBJECTS (pk INTEGER PRIMARY KEY, " +
            " boolCol BOOLEAN, byteCol TINYINT, shortCol SMALLINT, intCol INTEGER, longCol BIGINT, floatCol REAL," +
            " doubleCol DOUBLE, doubleCol2 DOUBLE, bigDecimalCol DECIMAL(10, 0), strCol VARCHAR(10), " +
            " dateCol DATE, timeCol TIME, tsCol TIMESTAMP, arrCol BINARY(10))");

        conn.commit();

        U.closeQuiet(stmt);
    }

    /** {@inheritDoc} */
    @Override public void tearDown() throws Exception {
        U.closeQuiet(conn);
    }

    /**
     * Test that POJOs generated correctly.
     */
    public void testPojoGeneration() throws Exception {
        List<PojoDescriptor> pojos = DatabaseMetadataParser.parse(conn);

        ConfirmCallable askOverwrite = new ConfirmCallable(null, "") {
            @Override public MessageBox.Result confirm(String msg) {
                return YES_TO_ALL;
            }
        };

        String pkg = "org.apache.ignite.schema.load.model";
        String intPath = "org" + PS + "apache" + PS + "ignite" + PS + "schema" + PS + "load" + PS + "model";

        for (PojoDescriptor pojo : pojos) {
            if (!pojo.valueClassName().isEmpty()) {
                PojoGenerator.generate(pojo, OUT_DIR_PATH, pkg, true, true, askOverwrite);

                assertTrue("Generated POJO files does not accordance to predefined for type " + pojo.keyClassName(),
                        compareFiles(pojo.keyClassName(), intPath));

                assertTrue("Generated POJO files does not accordance to predefined for type " + pojo.valueClassName(),
                        compareFiles(pojo.valueClassName(), intPath));
            }
        }
    }

    /**
     * @param typeName Type name.
     * @param intPath Int path.
     * @return {@code true} if generated POJO as expected.
     */
    private boolean compareFiles(String typeName, String intPath) {
        String relPath = intPath + PS + typeName;

        try (BufferedReader baseReader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/" + relPath + ".txt")))) {
            try (BufferedReader generatedReader = new BufferedReader(new FileReader(OUT_DIR_PATH + PS + relPath + ".java"))) {
                String baseLine;

                while ((baseLine = baseReader.readLine()) != null) {
                    String generatedLine = generatedReader.readLine();

                    if (!baseLine.equals(generatedLine) && !baseLine.contains(GEN_PTRN)
                        && !generatedLine.contains(GEN_PTRN)) {
                        System.out.println("Expected: " + baseLine);
                        System.out.println("Generated: " + generatedLine);

                        return false;
                    }
                }

                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }
}
