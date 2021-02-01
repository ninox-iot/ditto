/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.ControlMessage;
import akka.dispatch.RequiresMessageQueue;
import akka.dispatch.UnboundedControlAwareMessageQueueSemantics;
import akka.japi.function.Function;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.javadsl.Source;

/**
 * Collects changes from ThingUpdaters and forward them downstream on demand.
 */
public final class ChangeQueueActor extends AbstractActor
        implements RequiresMessageQueue<UnboundedControlAwareMessageQueueSemantics> {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "changeQueueActor";

    private static final Duration ASK_SELF_TIMEOUT = Duration.ofSeconds(5L);
    private static final String TIMER_SEGMENT_WAIT_FOR_DEQUEUE = "wait_for_dequeue";

    /**
     * Caching changes of 1 Thing per key.
     * Change type values according to caching strategy;
     * for example, replace AtomicReference by a concurrent queue if changes are to be computed from events directly.
     */
    private Map<ThingId, Metadata> cache = new HashMap<>();

    private ChangeQueueActor() {
        // prevent instantiation elsewhere
    }

    /**
     * @return Props of a ChangeQueueActor.
     */
    public static Props props() {
        return Props.create(ChangeQueueActor.class)
                .withMailbox("akka.actor.mailbox.unbounded-control-aware-queue-based");
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Metadata.class, this::enqueue)
                .matchEquals(Control.DUMP, this::dump)
                .build();
    }

    /**
     * Enqueue a change.
     *
     * @param metadata a description of the change.
     */
    private void enqueue(final Metadata metadata) {
        metadata.getTimers().forEach(timer -> timer.startNewSegment(TIMER_SEGMENT_WAIT_FOR_DEQUEUE));
        cache.merge(metadata.getThingId(), metadata, Metadata::prependSenders);
    }

    /**
     * Create a source of nonempty queue snapshots such that the queue content is cleared after each snapshot.
     *
     * @param changeQueueActor reference to this actor
     * @param writeInterval minimum delays between cache dumps.
     * @return source of queue snapshots.
     */
    public static Source<Map<ThingId, Metadata>, NotUsed> createSource(
            final ActorRef changeQueueActor,
            final Duration writeInterval) {

        final Source<Control, NotUsed> repeat;
        if (!writeInterval.isNegative() && !writeInterval.isZero()) {
            repeat = Source.repeat(Control.DUMP)
                    .throttle(1, writeInterval);
        } else {
            repeat = Source.repeat(Control.DUMP);
        }
        return repeat
                .flatMapConcat(ChangeQueueActor.askSelf(changeQueueActor))
                .filter(map -> !map.isEmpty())
                .map(map -> {
                    map.values().forEach(metadata -> metadata.getTimers().forEach(timer ->
                            Optional.ofNullable(timer.getSegments().get(TIMER_SEGMENT_WAIT_FOR_DEQUEUE))
                                    .ifPresent(dequeueSegment -> {
                                        if (dequeueSegment.isRunning()) {
                                            dequeueSegment.stop();
                                        }
                                    })
                    ));
                    return map;
                });
    }

    private void dump(final Control dump) {
        getSender().tell(cache, getSelf());
        cache = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Function<Control, Source<Map<ThingId, Metadata>, NotUsed>> askSelf(final ActorRef self) {
        return message -> Source.completionStageSource(
                Patterns.ask(self, message, ASK_SELF_TIMEOUT)
                        .handle((result, error) -> {
                            if (result instanceof Map) {
                                return Source.single((Map<ThingId, Metadata>) result);
                            } else {
                                return Source.empty();
                            }
                        }))
                .mapMaterializedValue(whatever -> NotUsed.getInstance());
    }

    private enum Control implements ControlMessage {
        DUMP
    }
}
