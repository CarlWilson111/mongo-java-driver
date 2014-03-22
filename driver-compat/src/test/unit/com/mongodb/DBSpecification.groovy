/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb
import org.mongodb.Document
import org.mongodb.MongoCommandFailureException
import org.mongodb.ServerCursor
import org.mongodb.SimpleBufferProvider
import org.mongodb.codecs.DocumentCodec
import org.mongodb.connection.Cluster
import org.mongodb.connection.ClusterDescription
import org.mongodb.connection.MongoTimeoutException
import org.mongodb.connection.ServerDescription
import org.mongodb.session.Session
import spock.lang.Specification
import spock.lang.Subject

import static com.mongodb.ReadPreference.primary
import static java.util.concurrent.TimeUnit.SECONDS
import static org.mongodb.connection.ClusterConnectionMode.SINGLE
import static org.mongodb.connection.ClusterType.UNKNOWN

class DBSpecification extends Specification {
    private final Session session = Mock()
    private final Cluster cluster = Mock()
    private final Mongo mongo = Mock()

    @Subject
    private final DB database = new DB(mongo, 'myDatabase', new DocumentCodec())

    def setup() {
        mongo.getCluster() >> { cluster }
        mongo.getSession() >> { session }
        mongo.getBufferProvider() >> { new SimpleBufferProvider() }
        //TODO: this shouldn't be required.  I think.
        database.setReadPreference(primary())
    }

    @SuppressWarnings('UnnecessaryQualifiedReference')
    def 'should throw com.mongodb.MongoException if createCollection fails'() {
        given:
        cluster.getDescription(10, SECONDS) >> { new ClusterDescription(SINGLE, UNKNOWN, Collections.<ServerDescription> emptyList()) }
        session.createServerConnectionProvider(_) >> {
            throw new MongoCommandFailureException(new org.mongodb.CommandResult(new org.mongodb.connection.ServerAddress(),
                                                                                 new Document(), 15L))
        }

        when:
        database.createCollection('myNewCollection', new BasicDBObject());

        then:
        thrown(com.mongodb.MongoException)
    }

    @SuppressWarnings('UnnecessaryQualifiedReference')
    def 'should throw com.mongodb.MongoCursorNotFoundException if cursor not found'() {
        given:
        cluster.getDescription(10, SECONDS) >> { new ClusterDescription(SINGLE, UNKNOWN, Collections.<ServerDescription> emptyList()) }
        session.createServerConnectionProvider(_) >> {
            throw new org.mongodb.MongoCursorNotFoundException(new ServerCursor(1, new org.mongodb.connection.ServerAddress()))
        }

        when:
        database.executeCommand(new Document('isMaster', 1), org.mongodb.ReadPreference.primary());

        then:
        thrown(com.mongodb.MongoCursorNotFoundException)
    }

    @SuppressWarnings('UnnecessaryQualifiedReference')
    def 'should throw com.mongodb.MongoException if getCollectionNames fails'() {
        given:
        session.createServerConnectionProvider(_) >> {
            throw new MongoCommandFailureException(new org.mongodb.CommandResult(new org.mongodb.connection.ServerAddress(),
                                                                                 new Document(), 15L))
        }

        when:
        database.getCollectionNames();

        then:
        thrown(com.mongodb.CommandFailureException)
    }

    @SuppressWarnings('UnnecessaryQualifiedReference')
    def 'should wrap org.mongodb.MongoException as com.mongodb.MongoException for getClusterDescription'() {
        given:
        cluster.getDescription(10, SECONDS) >> { throw new MongoTimeoutException('This Exception should not escape') }

        when:
        database.getClusterDescription()

        then:
        thrown(com.mongodb.MongoException)
    }

}
