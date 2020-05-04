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
 * The parser of {@link VeazzyBlindIq}.
 *
 * @author Pawel Domas
 */
public class VeazzyBlindIqProvider
        extends IQProvider<VeazzyBlindIq> {

    /**
     * Registers this IQ provider into given <tt>ProviderManager</tt>.
     */
    public static void registerVeazzyBlindIqProvider() {
        ProviderManager.addIQProvider(VeazzyBlindIq.ELEMENT_NAME,
                VeazzyBlindIq.NAMESPACE,
                new VeazzyBlindIqProvider());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VeazzyBlindIq parse(XmlPullParser parser, int initialDepth)
            throws Exception {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!VeazzyBlindIq.NAMESPACE.equals(namespace)) {
            return null;
        }

        String rootElement = parser.getName();

        VeazzyBlindIq iq;

        if (VeazzyBlindIq.ELEMENT_NAME.equals(rootElement)) {
            iq = new VeazzyBlindIq();
            String jidStr = parser.getAttributeValue("", VeazzyBlindIq.JID_ATTR_NAME);
            if (jidStr != null) {
                Jid jid = JidCreate.from(jidStr);
                iq.setJid(jid);
            }

            String actorStr
                    = parser.getAttributeValue("", VeazzyBlindIq.ACTOR_ATTR_NAME);
            if (actorStr != null) {
                Jid actor = JidCreate.from(actorStr);
                iq.setActor(actor);
            }

            String blockVideoControlStr
                    = parser.getAttributeValue("", VeazzyBlindIq.BLOCK_VIDEO_CONTROL_ATTR_NAME);
            if (blockVideoControlStr != null) {
                iq.setBlockVideoControl(Boolean.valueOf(blockVideoControlStr));
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
                    if (parser.getText() != null && parser.getText().length() > 0) {
                        Boolean doBlind = Boolean.parseBoolean(parser.getText());
                        iq.setDoBlind(doBlind);
                    }
                    break;
                }
            }
        }

        return iq;
    }
}
