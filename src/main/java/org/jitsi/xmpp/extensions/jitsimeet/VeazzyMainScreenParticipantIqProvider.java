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
 * The parser of {@link VeazzyMainScreenParticipantIq}.
 *
 * @author Pawel Domas
 */
public class VeazzyMainScreenParticipantIqProvider
        extends IQProvider<VeazzyMainScreenParticipantIq> {

    /**
     * Registers this IQ provider into given <tt>ProviderManager</tt>.
     */
    public static void registerVeazzyMainScreenParticipantIqProvider() {
        ProviderManager.addIQProvider(VeazzyMainScreenParticipantIq.ELEMENT_NAME,
                VeazzyMainScreenParticipantIq.NAMESPACE,
                new VeazzyMainScreenParticipantIqProvider());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VeazzyMainScreenParticipantIq parse(XmlPullParser parser, int initialDepth)
            throws Exception {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!VeazzyMainScreenParticipantIq.NAMESPACE.equals(namespace)) {
            return null;
        }

        String rootElement = parser.getName();

        VeazzyMainScreenParticipantIq iq;

        if (VeazzyMainScreenParticipantIq.ELEMENT_NAME.equals(rootElement)) {
            iq = new VeazzyMainScreenParticipantIq();
            String jidStr = parser.getAttributeValue("", VeazzyMainScreenParticipantIq.JID_ATTR_NAME);
            if (jidStr != null) {
                Jid jid = JidCreate.from(jidStr);
                iq.setJid(jid);
            }

            String actorStr
                    = parser.getAttributeValue("", VeazzyMainScreenParticipantIq.ACTOR_ATTR_NAME);
            if (actorStr != null) {
                Jid actor = JidCreate.from(actorStr);
                iq.setActor(actor);
            }

            String withMeStr
                    = parser.getAttributeValue("", VeazzyMainScreenParticipantIq.WITH_ME_ATTR_NAME);
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
