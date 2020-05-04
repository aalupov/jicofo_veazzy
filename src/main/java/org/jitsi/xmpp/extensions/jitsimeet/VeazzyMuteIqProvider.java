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
 * The parser of {@link VeazzyMuteIq}.
 *
 * @author Pawel Domas
 */
public class VeazzyMuteIqProvider
        extends IQProvider<VeazzyMuteIq> {

    /**
     * Registers this IQ provider into given <tt>ProviderManager</tt>.
     */
    public static void registerVeazzyMuteIqProvider() {
        ProviderManager.addIQProvider(VeazzyMuteIq.ELEMENT_NAME,
                VeazzyMuteIq.NAMESPACE,
                new VeazzyMuteIqProvider());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VeazzyMuteIq parse(XmlPullParser parser, int initialDepth)
            throws Exception {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!VeazzyMuteIq.NAMESPACE.equals(namespace)) {
            return null;
        }

        String rootElement = parser.getName();

        VeazzyMuteIq iq;

        if (VeazzyMuteIq.ELEMENT_NAME.equals(rootElement)) {
            iq = new VeazzyMuteIq();
            String jidStr = parser.getAttributeValue("", VeazzyMuteIq.JID_ATTR_NAME);
            if (jidStr != null) {
                Jid jid = JidCreate.from(jidStr);
                iq.setJid(jid);
            }

            String actorStr
                    = parser.getAttributeValue("", VeazzyMuteIq.ACTOR_ATTR_NAME);
            if (actorStr != null) {
                Jid actor = JidCreate.from(actorStr);
                iq.setActor(actor);
            }

            String blockStr
                    = parser.getAttributeValue("", VeazzyMuteIq.BLOCK_ATTR_NAME);
            if (blockStr != null) {
                iq.setBlock(Boolean.valueOf(blockStr));
            }

            String videoStr
                    = parser.getAttributeValue("", VeazzyMuteIq.VIDEO_ATTR_NAME);
            if (videoStr != null) {
                iq.setVideo(Boolean.valueOf(videoStr));
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
                    Boolean mute = Boolean.parseBoolean(parser.getText());
                    iq.setMute(mute);
                    break;
                }
            }
        }

        return iq;
    }
}
