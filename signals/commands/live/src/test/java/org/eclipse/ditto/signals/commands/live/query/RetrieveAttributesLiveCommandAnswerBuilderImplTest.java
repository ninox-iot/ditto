/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.live.query;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.text.MessageFormat;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.signals.commands.live.assertions.LiveCommandAssertions;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link RetrieveAttributesLiveCommandAnswerBuilderImpl}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RetrieveAttributesLiveCommandAnswerBuilderImplTest {

    @Mock
    private RetrieveAttributesLiveCommand commandMock;

    private RetrieveAttributesLiveCommandAnswerBuilderImpl underTest;

    /** */
    @Before
    public void setUp() {
        Mockito.when(commandMock.getThingEntityId()).thenReturn(TestConstants.Thing.THING_ID);
        Mockito.when(commandMock.getDittoHeaders()).thenReturn(DittoHeaders.empty());

        underTest = RetrieveAttributesLiveCommandAnswerBuilderImpl.newInstance(commandMock);
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetNewInstanceWithNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveAttributesLiveCommandAnswerBuilderImpl.newInstance(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void buildAnswerWithoutResponse() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse().build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasNoEvent();
    }

    /** */
    @Test
    public void buildAnswerWithRetrieveAttributesResponseOnly() {
        final Attributes attributes = TestConstants.Thing.ATTRIBUTES;

        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(responseFactory -> responseFactory.retrieved(attributes))
                        .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingQueryCommandResponse()
                .hasType(RetrieveAttributesResponse.TYPE)
                .hasDittoHeaders(DittoHeaders.newBuilder().responseRequired(false).build())
                .hasResourcePath(JsonPointer.of("attributes"));
    }

    /** */
    @Test
    public void buildAnswerWithAttributeNotAccessibleErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(
                        RetrieveAttributesLiveCommandAnswerBuilder.ResponseFactory::attributesNotAccessibleError)
                        .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.newBuilder().responseRequired(false).build())
                .withStatus(HttpStatus.NOT_FOUND)
                .withDittoRuntimeExceptionOfType(AttributesNotAccessibleException.class);
    }

}
