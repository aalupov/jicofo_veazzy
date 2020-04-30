/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.xmpp.extensions.jitsimeet;

import org.jivesoftware.smack.provider.*;

import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.xmlpull.v1.*;

/**
 * The parser of {@link ParticipantIdIq}.
 *
 * @author Pawel Domas
 */
public class ParticipantIdIqProvider
        extends IQProvider<ParticipantIdIq> {

    /**
     * Registers this IQ provider into given <tt>ProviderManager</tt>.
     */
    public static void registerParticipantIdIqProvider() {
        ProviderManager.addIQProvider(
                ParticipantIdIq.ELEMENT_NAME,
                ParticipantIdIq.NAMESPACE,
                new ParticipantIdIqProvider());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParticipantIdIq parse(XmlPullParser parser, int initialDepth)
            throws Exception {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!ParticipantIdIq.NAMESPACE.equals(namespace)) {
            return null;
        }

        String rootElement = parser.getName();

        ParticipantIdIq iq;

        if (ParticipantIdIq.ELEMENT_NAME.equals(rootElement)) {
            iq = new ParticipantIdIq();
            String jidStr = parser.getAttributeValue("", ParticipantIdIq.JID_ATTR_NAME);
            if (jidStr != null) {
                Jid jid = JidCreate.from(jidStr);
                iq.setJid(jid);
            }

            String actorStr
                    = parser.getAttributeValue("", ParticipantIdIq.ACTOR_ATTR_NAME);
            if (actorStr != null) {
                Jid actor = JidCreate.from(actorStr);
                iq.setActor(actor);
            }

            String withMeStr
                    = parser.getAttributeValue("", ParticipantIdIq.WITH_ME_ATTR_NAME);
            if (withMeStr != null) {
                iq.setWithMe(Boolean.valueOf(withMeStr));
            }

        } else {
            return null;
        }

        boolean done = false;

        while (!done) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG: {
                    String name = parser.getName();

                    if (rootElement.equals(name)) {
                        done = true;
                    }
                    break;
                }

                case XmlPullParser.TEXT: {
                    String participantId = parser.getText();
                    iq.setParticipantId(participantId);
                    break;
                }
            }
        }

        return iq;
    }
}
