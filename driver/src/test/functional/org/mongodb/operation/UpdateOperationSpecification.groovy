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



package org.mongodb.operation

import org.bson.types.ObjectId
import org.mongodb.Document
import org.mongodb.FunctionalSpecification
import org.mongodb.codecs.DocumentCodec

import static java.util.Arrays.asList
import static org.mongodb.Fixture.getSession
import static org.mongodb.WriteConcern.ACKNOWLEDGED

class UpdateOperationSpecification extends FunctionalSpecification {
    def 'should return correct result for update'() {
        given:
        new InsertOperation<Document>(getNamespace(), true, ACKNOWLEDGED, asList(new InsertRequest<Document>(new Document('_id', 1))),
                                      new DocumentCodec(), getSession(), true).execute()
        def op = new UpdateOperation(getNamespace(), true, ACKNOWLEDGED, asList(new UpdateRequest(new Document('_id', 1),
                                                                                                  new Document('$set',
                                                                                                               new Document('x', 1)))),
                                     new DocumentCodec(), getSession(), true)

        when:
        def result = op.execute();

        then:
        result.wasAcknowledged()
        result.count == 1
        result.upsertedId == null
        result.isUpdateOfExisting()
    }

    def 'should return correct result for upsert'() {
        def id = new ObjectId()
        given:
        def op = new UpdateOperation(getNamespace(), true, ACKNOWLEDGED,
                                     asList(new UpdateRequest(new Document('_id', id),
                                                              new Document('$set', new Document('x', 1))).upsert(true)),
                                     new DocumentCodec(), getSession(), true)

        when:
        def result = op.execute();

        then:
        result.wasAcknowledged()
        result.count == 1
        result.upsertedId == id
        !result.isUpdateOfExisting()
    }
}