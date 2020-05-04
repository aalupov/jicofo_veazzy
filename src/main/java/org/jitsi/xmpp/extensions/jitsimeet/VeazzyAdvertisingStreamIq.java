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

import org.jivesoftware.smack.packet.*;
import org.jxmpp.jid.*;

/**
 * IQ used for the signaling of audio muting functionality in Jitsi Meet
 * conferences.
 *
 * @author Pawel Domas
 */
public class VeazzyAdvertisingStreamIq
        extends IQ {

    /**
     * Name space of mute packet extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/advertisingstream";

    public static final int STREAM_STATUS_STOP = 0;
    public static final int STREAM_STATUS_START = 1;
    
    /**
     * XML element name of mute packet extension.
     */
    public static final String ELEMENT_NAME = "advertisingStream";

    /**
     * Attribute name of "jid".
     */
    public static final String JID_ATTR_NAME = "jid";

    /**
     * Attribute name of "actor".
     */
    public static final String ACTOR_ATTR_NAME = "actor";

    /**
     * Streamd peer MUC jid.
     */
    private Jid jid;

    /**
     * The jid of the peer tha initiated the streamId, optional.
     */
    private Jid actor;

    /**
     * To stream or not.
     */
    private int advertisingStreamStatus;

    /**
     * Creates a new instance of this class.
     */
    public VeazzyAdvertisingStreamIq() {
        super(ELEMENT_NAME, NAMESPACE);
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
            IQChildElementXmlStringBuilder xml) {
        if (jid != null) {
            xml.attribute(JID_ATTR_NAME, jid);
        }

        if (actor != null) {
            xml.attribute(ACTOR_ATTR_NAME, actor);
        }

        xml.rightAngleBracket()
                .append(String.valueOf(advertisingStreamStatus));

        return xml;
    }

    /**
     * Sets the MUC jid of the user to be muted/unmuted.
     *
     * @param jid muc jid in the form of room_name@muc.server.net/nickname.
     */
    public void setJid(Jid jid) {
        this.jid = jid;
    }

    /**
     * @return MUC jid of the participant in the form of
     * "room_name@muc.server.net/nickname".
     */
    public Jid getJid() {
        return jid;
    }

    /**
     * The action contained in the text part of 'mute' XML element body.
     *
     * @param advertisingStreamStatus
     */
    public void setAdvertisingStreamStatus(int advertisingStreamStatus) {
        this.advertisingStreamStatus = advertisingStreamStatus;
    }

    /**
     * @return stream
     */
    public int getAdvertisingStreamStatus() {
        return advertisingStreamStatus;
    }

    /**
     * Returns the peer jid that initiated the mute, if any.
     *
     * @return the peer jid that initiated the mute.
     */
    public Jid getActor() {
        return actor;
    }

    /**
     * Sets jid for the peer that initiated the mute.
     *
     * @param actor the jid of the peer doing the mute.
     */
    public void setActor(Jid actor) {
        this.actor = actor;
    }
}
