/*
 * Copyright 2008-present MongoDB, Inc.
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

package com.mongodb.internal.async.client

import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.ServerAddress
import com.mongodb.async.FutureResultCallback
import com.mongodb.connection.ClusterConnectionMode
import com.mongodb.connection.ClusterDescription
import com.mongodb.connection.ClusterType
import com.mongodb.connection.ServerConnectionState
import com.mongodb.connection.ServerDescription
import com.mongodb.connection.ServerType
import com.mongodb.internal.IgnorableRequestContext
import com.mongodb.internal.binding.AsyncClusterAwareReadWriteBinding
import com.mongodb.internal.binding.AsyncClusterBinding
import com.mongodb.internal.binding.AsyncConnectionSource
import com.mongodb.internal.connection.Cluster
import com.mongodb.internal.connection.Server
import com.mongodb.internal.connection.ServerTuple
import com.mongodb.internal.session.ClientSessionContext
import spock.lang.Specification

class ClientSessionBindingSpecification extends Specification {
    def 'should return the session context from the binding'() {
        given:
        def session = Stub(AsyncClientSession)
        def wrappedBinding = Stub(AsyncClusterAwareReadWriteBinding)
        def binding = new ClientSessionBinding(session, false, wrappedBinding)

        when:
        def context = binding.getSessionContext()

        then:
        (context as ClientSessionContext).getClientSession() == session
    }

    def 'should return the session context from the connection source'() {
        given:
        def session = Stub(AsyncClientSession)
        def wrappedBinding = Mock(AsyncClusterAwareReadWriteBinding) {
            getCluster() >> {
                Mock(Cluster) {
                    getDescription() >> {
                        new ClusterDescription(ClusterConnectionMode.MULTIPLE, ClusterType.REPLICA_SET, [])
                    }
                }
            }
        }
        wrappedBinding.retain() >> wrappedBinding
        def binding = new ClientSessionBinding(session, false, wrappedBinding)

        when:
        def futureResultCallback = new FutureResultCallback<AsyncConnectionSource>()
        binding.getReadConnectionSource(futureResultCallback)

        then:
        1 * wrappedBinding.getReadConnectionSource(_) >> {
            it[0].onResult(Stub(AsyncConnectionSource), null)
        }

        when:
        def context = futureResultCallback.get().getSessionContext()

        then:
        (context as ClientSessionContext).getClientSession() == session

        when:
        futureResultCallback = new FutureResultCallback<AsyncConnectionSource>()
        binding.getWriteConnectionSource(futureResultCallback)

        then:
        1 * wrappedBinding.getWriteConnectionSource(_) >> {
            it[0].onResult(Stub(AsyncConnectionSource), null)
        }

        when:
        context = futureResultCallback.get().getSessionContext()

        then:
        (context as ClientSessionContext).getClientSession() == session
    }

    def 'should close client session when binding reference count drops to zero if it is owned by the binding'() {
        given:
        def session = Mock(AsyncClientSession)
        def wrappedBinding = createStubBinding()
        def binding = new ClientSessionBinding(session, true, wrappedBinding)
        binding.retain()

        when:
        binding.release()

        then:
        0 * session.close()

        when:
        binding.release()

        then:
        1 * session.close()
    }

    def 'should close client session when binding reference count drops to zero due to connection source if it is owned by the binding'() {
        given:
        def session = Mock(AsyncClientSession)
        def wrappedBinding = createStubBinding()
        def binding = new ClientSessionBinding(session, true, wrappedBinding)
        def futureResultCallback = new FutureResultCallback<AsyncConnectionSource>()
        binding.getReadConnectionSource(futureResultCallback)
        def readConnectionSource = futureResultCallback.get()
        futureResultCallback = new FutureResultCallback<AsyncConnectionSource>()
        binding.getWriteConnectionSource(futureResultCallback)
        def writeConnectionSource = futureResultCallback.get()

        when:
        binding.release()

        then:
        0 * session.close()

        when:
        writeConnectionSource.release()

        then:
        0 * session.close()

        when:
        readConnectionSource.release()

        then:
        1 * session.close()
    }

    def 'should not close client session when binding reference count drops to zero if it is not owned by the binding'() {
        given:
        def session = Mock(AsyncClientSession)
        def wrappedBinding = createStubBinding()
        def binding = new ClientSessionBinding(session, false, wrappedBinding)
        binding.retain()

        when:
        binding.release()

        then:
        0 * session.close()

        when:
        binding.release()

        then:
        0 * session.close()
    }

    def 'owned session is implicit'() {
        given:
        def session = Mock(AsyncClientSession)
        def wrappedBinding = createStubBinding()

        when:
        def binding = new ClientSessionBinding(session, ownsSession, wrappedBinding)

        then:
        binding.getSessionContext().isImplicitSession() == ownsSession

        where:
        ownsSession << [true, false]
    }

    private AsyncClusterAwareReadWriteBinding createStubBinding() {
        def cluster = Mock(Cluster) {
            selectServerAsync(_, _) >> {
                it[1].onResult(new ServerTuple(Stub(Server), ServerDescription.builder()
                        .type(ServerType.STANDALONE)
                        .state(ServerConnectionState.CONNECTED)
                        .address(new ServerAddress())
                        .build()), null)
            }
            getDescription() >> {
                new ClusterDescription(ClusterConnectionMode.MULTIPLE, ClusterType.REPLICA_SET, [])
            }
        }
        new AsyncClusterBinding(cluster, ReadPreference.primary(), ReadConcern.DEFAULT, null, IgnorableRequestContext.INSTANCE)
    }
}
