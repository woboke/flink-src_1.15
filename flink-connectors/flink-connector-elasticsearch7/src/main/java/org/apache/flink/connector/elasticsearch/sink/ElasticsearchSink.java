/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.connector.elasticsearch.sink;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.connector.sink.Committer;
import org.apache.flink.api.connector.sink.GlobalCommitter;
import org.apache.flink.api.connector.sink.Sink;
import org.apache.flink.api.connector.sink.SinkWriter;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.core.io.SimpleVersionedSerializer;

import org.apache.http.HttpHost;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Flink Sink to insert or update data in an Elasticsearch index. The sink supports the following
 * delivery guarantees.
 *
 * <ul>
 *   <li>{@link DeliveryGuarantee#NONE} does not provide any guarantees: actions are flushed to
 *       Elasticsearch only depending on the configurations of the bulk processor. In case of a
 *       failure, it might happen that actions are lost if the bulk processor still has buffered
 *       actions.
 *   <li>{@link DeliveryGuarantee#AT_LEAST_ONCE} on a checkpoint the sink will wait until all
 *       buffered actions are flushed to and acknowledged by Elasticsearch. No actions will be lost
 *       but actions might be sent to Elasticsearch multiple times when Flink restarts. These
 *       additional requests may cause inconsistent data in ElasticSearch right after the restart,
 *       but eventually everything will be consistent again.
 * </ul>
 *
 * @param <IN> type of the records converted to Elasticsearch actions
 * @see ElasticsearchSinkBuilder on how to construct a ElasticsearchSink
 */
@PublicEvolving
public class ElasticsearchSink<IN> implements Sink<IN, Void, Void, Void> {

    private final List<HttpHost> hosts;
    private final ElasticsearchEmitter<? super IN> emitter;
    private final BulkProcessorConfig buildBulkProcessorConfig;
    private final NetworkClientConfig networkClientConfig;
    private final DeliveryGuarantee deliveryGuarantee;

    ElasticsearchSink(
            List<HttpHost> hosts,
            ElasticsearchEmitter<? super IN> emitter,
            DeliveryGuarantee deliveryGuarantee,
            BulkProcessorConfig buildBulkProcessorConfig,
            NetworkClientConfig networkClientConfig) {
        this.hosts = checkNotNull(hosts);
        checkArgument(!hosts.isEmpty(), "Hosts cannot be empty.");
        this.emitter = checkNotNull(emitter);
        this.deliveryGuarantee = checkNotNull(deliveryGuarantee);
        this.buildBulkProcessorConfig = checkNotNull(buildBulkProcessorConfig);
        this.networkClientConfig = checkNotNull(networkClientConfig);
    }

    /**
     * Create a {@link ElasticsearchSinkBuilder} to construct a new {@link ElasticsearchSink}.
     *
     * @param <IN> type of incoming records
     * @return {@link ElasticsearchSinkBuilder}
     */
    public static <IN> ElasticsearchSinkBuilder<IN> builder() {
        return new ElasticsearchSinkBuilder<>();
    }

    @Override
    public SinkWriter<IN, Void, Void> createWriter(InitContext context, List<Void> states)
            throws IOException {
        return new ElasticsearchWriter<>(
                hosts,
                emitter,
                deliveryGuarantee == DeliveryGuarantee.AT_LEAST_ONCE,
                buildBulkProcessorConfig,
                networkClientConfig,
                context.metricGroup(),
                context.getMailboxExecutor());
    }

    @Override
    public Optional<SimpleVersionedSerializer<Void>> getWriterStateSerializer() {
        return Optional.empty();
    }

    @Override
    public Optional<Committer<Void>> createCommitter() throws IOException {
        return Optional.empty();
    }

    @Override
    public Optional<GlobalCommitter<Void, Void>> createGlobalCommitter() throws IOException {
        return Optional.empty();
    }

    @Override
    public Optional<SimpleVersionedSerializer<Void>> getCommittableSerializer() {
        return Optional.empty();
    }

    @Override
    public Optional<SimpleVersionedSerializer<Void>> getGlobalCommittableSerializer() {
        return Optional.empty();
    }
}
