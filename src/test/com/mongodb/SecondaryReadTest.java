/**
 * Copyright (C) 2008 10gen Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb;

// Mongo

import com.mongodb.util.TestCase;
import org.bson.types.ObjectId;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

// Java

public class SecondaryReadTest extends TestCase {


    private static final int INSERT_COUNT = 1000;

    private static final int ITERATION_COUNT = 100;

    private static final int TOTAL_COUNT = INSERT_COUNT * ITERATION_COUNT;

    private static final double MAX_DEVIATION_PERCENT = 1.0;

    @Test(groups = {"basic"})
    public void testSecondaryReads1() throws Exception {

        final Mongo mongo = loadMongo();

        try {
            if (isStandalone(mongo)) {
                return;
            }

            final List<TestHost> testHosts = extractHosts(mongo);

            final DBCollection col = loadCleanDbCollection(mongo);

            final List<ObjectId> insertedIds = insertTestData(col);

            // Get the opcounter/query data for the hosts.
            loadQueryCount(testHosts, true);

            final int secondaryCount = getSecondaryCount(testHosts);

            // Perform some reads on the secondaries
            col.setReadPreference(ReadPreference.SECONDARY);

            for (int idx=0; idx < ITERATION_COUNT; idx++) {
                for (ObjectId id : insertedIds) {
                    col.findOne(new BasicDBObject("_id", id));
                }
            }

            loadQueryCount(testHosts, false);

            verifySecondaryCounts(secondaryCount, testHosts);
        } finally { if (mongo != null) mongo.close(); }
   }

    @Test(groups = {"basic"})
    public void testSecondaryReads2() throws Exception {

        final Mongo mongo = loadMongo();

        try {
            if (isStandalone(mongo)) {
                return;
            }

            final List<TestHost> testHosts = extractHosts(mongo);

            final DBCollection col = loadCleanDbCollection(mongo);

            final List<ObjectId> insertedIds = insertTestData(col);

            // Get the opcounter/query data for the hosts.
            loadQueryCount(testHosts, true);

            final int secondaryCount = getSecondaryCount(testHosts);

            // Perform some reads on the secondaries
            mongo.setReadPreference(ReadPreference.SECONDARY);

            for (int idx=0; idx < ITERATION_COUNT; idx++) {
                for (ObjectId id : insertedIds) {
                    col.findOne(new BasicDBObject("_id", id));
                }
            }

            loadQueryCount(testHosts, false);

            verifySecondaryCounts(secondaryCount, testHosts);
        } finally { if (mongo != null) mongo.close(); }
   }

    @Test(groups = {"basic"})
    public void testSecondaryReads3() throws Exception {

        final Mongo mongo = loadMongo();

        try {
            if (isStandalone(mongo)) {
                return;
            }

            final List<TestHost> testHosts = extractHosts(mongo);

            final DBCollection col = loadCleanDbCollection(mongo);

            final List<ObjectId> insertedIds = insertTestData(col);

            // Get the opcounter/query data for the hosts.
            loadQueryCount(testHosts, true);

            final int secondaryCount = getSecondaryCount(testHosts);

            // Perform some reads on the secondaries
            col.getDB().setReadPreference(ReadPreference.SECONDARY);

            for (int idx=0; idx < ITERATION_COUNT; idx++) {
                for (ObjectId id : insertedIds) {
                    col.findOne(new BasicDBObject("_id", id));
                }
            }

            loadQueryCount(testHosts, false);

            verifySecondaryCounts(secondaryCount, testHosts);
        } finally { if (mongo != null) mongo.close(); }
   }

    @Test(groups = {"basic"})
    public void testSecondaryReadCursor() throws Exception {
        final Mongo mongo = loadMongo();
        try {
            if (isStandalone(mongo)) {
                return;
            }

            final List<TestHost> testHosts = extractHosts(mongo);

            final DBCollection col = loadCleanDbCollection(mongo);

            insertTestData(col);

            // Get the opcounter/query data for the hosts.
            loadQueryCount(testHosts, true);

            // Perform some reads on the secondaries
            col.setReadPreference(ReadPreference.SECONDARY);

            final DBCursor cur = col.find();

            cur.hasNext();

            ServerAddress curServerAddress = cur.getServerAddress();

            assertEquals(true, serverIsSecondary(curServerAddress, testHosts));

            try {
                while (cur.hasNext()) {
                    cur.next();
                    assertEquals(true, serverIsSecondary(cur.getServerAddress(), testHosts));
                }
            } finally { cur.close(); }

            loadQueryCount(testHosts, false);

        } finally { if (mongo != null) mongo.close(); }
    }

    private boolean serverIsSecondary(final ServerAddress pServerAddr, final List<TestHost> pHosts) {
        for (final TestHost h : pHosts) {
            if (!h.stateStr.equals("SECONDARY")) continue;
            final int portIdx = h.hostnameAndPort.indexOf(":");
            final int port = Integer.parseInt(h.hostnameAndPort.substring(portIdx+1, h.hostnameAndPort.length()));
            final String hostname = h.hostnameAndPort.substring(0, portIdx);

            //System.out.println("---- hostname: " + hostname + " + server addr host: " + pServerAddr.getHost());

            if (pServerAddr.getPort() == port && hostname.equals(pServerAddr.getHost())) return true;
        }

        return false;
    }

    private Mongo loadMongo() throws Exception {
        return new Mongo(new MongoURI("mongodb://127.0.0.1:27017,127.0.0.1:27018/?connectTimeoutMS=30000;socketTimeoutMS=30000;maxpoolsize=5;autoconnectretry=true"));
    }

    @SuppressWarnings({"unchecked"})
    private List<TestHost> extractHosts(Mongo mongo) {
        CommandResult result = runReplicaSetStatusCommand(mongo);

        List<TestHost> pHosts = new ArrayList<TestHost>();

        // Extract the repl set members.
        for (final BasicDBObject member : (List<BasicDBObject>) result.get("members")) {
            String hostnameAndPort = member.getString("name");
            if (!hostnameAndPort.contains(":")) {
                hostnameAndPort = hostnameAndPort + ":27017";
            }

            final String stateStr = member.getString("stateStr");

            pHosts.add(new TestHost(hostnameAndPort, stateStr));
        }

        return pHosts;
    }

    private DBCollection loadCleanDbCollection(final Mongo pMongo) {
        pMongo.getDB("com_mongodb_unittest_SecondaryReadTest").dropDatabase();
        final DB db = pMongo.getDB("com_mongodb_unittest_SecondaryReadTest");
        return db.getCollection("testBalance");
    }

    private List<ObjectId> insertTestData(final DBCollection pCol) throws Exception {
        final ArrayList<ObjectId> insertedIds = new ArrayList<ObjectId>();

        // Insert some test data.
        for (int idx=0; idx < INSERT_COUNT; idx++) {
            final ObjectId id = ObjectId.get();
            WriteResult writeResult = pCol.insert(new BasicDBObject("_id", id), WriteConcern.REPLICAS_SAFE);
            writeResult.getLastError().throwOnError();
            insertedIds.add(id);
        }

        // Make sure everything is inserted.
        while (true) {
            final long count = pCol.count();
            if (count == INSERT_COUNT) break;
            Thread.sleep(1000);
        }

        return insertedIds;
    }

    private int getSecondaryCount(final List<TestHost> pHosts) {
        int secondaryCount = 0;
        for (final TestHost testHost : pHosts) if (testHost.stateStr.equals("SECONDARY")) secondaryCount++;
        return secondaryCount;
    }

    private void verifySecondaryCounts(final int pSecondaryCount, final List<TestHost> pHosts) {

        // Verify the counts.
        final int expectedPerSecondary = TOTAL_COUNT / pSecondaryCount;

        for (final TestHost testHost : pHosts) {

            if (!testHost.stateStr.equals("SECONDARY")) continue;

            final long queriesExecuted = testHost.getQueriesExecuted();

            final double deviation;
            if (queriesExecuted > expectedPerSecondary) {
                deviation = (double)100 - (((double)expectedPerSecondary / (double)queriesExecuted) * (double)100);
            } else {
                deviation = (double)100 - (((double)queriesExecuted / (double)expectedPerSecondary) * (double)100);
            }
            assertLess(deviation, MAX_DEVIATION_PERCENT);
        }
    }

    private static void loadQueryCount(final List<TestHost> pHosts, final boolean pBefore) throws Exception {
        for (final TestHost testHost : pHosts) {
            final Mongo mongoHost = new Mongo(new MongoURI("mongodb://"+testHost.hostnameAndPort+"/?connectTimeoutMS=30000;socketTimeoutMS=30000;maxpoolsize=5;autoconnectretry=true"));
            try {
                final CommandResult serverStatusResult
                = mongoHost.getDB("com_mongodb_unittest_SecondaryReadTest").command(new BasicDBObject("serverStatus", 1));

                final BasicDBObject opcounters = (BasicDBObject)serverStatusResult.get("opcounters");

                if (pBefore) testHost.queriesBefore = opcounters.getLong("query");
                else testHost.queriesAfter = opcounters.getLong("query");

            } finally { if (mongoHost != null) mongoHost.close(); }
        }
    }

    private static class TestHost {
        private final String hostnameAndPort;
        private final String stateStr;

        private long queriesBefore;
        private long queriesAfter;

        public long getQueriesExecuted() { return queriesAfter - queriesBefore; }

        private TestHost(final String pHostnameAndPort, final String pStateStr) {
            hostnameAndPort = pHostnameAndPort;
            stateStr = pStateStr;
        }
    }
}

