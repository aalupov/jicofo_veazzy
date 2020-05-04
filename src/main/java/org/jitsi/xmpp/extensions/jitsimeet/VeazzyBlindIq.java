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
public class VeazzyBlindIq
        extends IQ {

    /**
     * Name space of blind packet extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/video";

    /**
     * XML element name of blind packet extension.
     */
    public static final String ELEMENT_NAME = "blind";

    /**
     * Attribute name of "jid".
     */
    public static final String JID_ATTR_NAME = "jid";

    /**
     * Attribute name of "actor".
     */
    public static final String ACTOR_ATTR_NAME = "actor";

    /**
     * Attribute name of "block".
     */
    public static final String BLOCK_VIDEO_CONTROL_ATTR_NAME = "blockvideocontrol";

    /**
     * Blinded peer MUC jid.
     */
    private Jid jid;

    /**
     * The jid of the peer that initiated the blind, optional.
     */
    private Jid actor;

    /**
     * To blind or unblind.
     */
    private Boolean doBlind;

    /**
     * To block webcam or display webcam.
     */
    private Boolean blockVideoControl;

    /**
     * Creates a new instance of this class.
     */
    public VeazzyBlindIq() {
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

        if (blockVideoControl != null) {
            xml.attribute(BLOCK_VIDEO_CONTROL_ATTR_NAME, blockVideoControl);
        }

        xml.rightAngleBracket()
                .append(doBlind.toString());

        return xml;
    }

    /**
     * Sets the MUC jid of the user to be blinded/unblinded.
     *
     * @param jid muc jid in the form of room_name@muc.server.net/nickname.
     */
    public void setJid(Jid jid) {
        this.jid = jid;
    }

    /**
     * Returns MUC jid of the participant in the form of
     * "room_name@muc.server.net/nickname".
     */
    public Jid getJid() {
        return jid;
    }

    /**
     * The action contained in the text part of 'blind' XML element body.
     *
     * @param doBlind <tt>true</tt> to blind the participant. <tt>null</tt> means no
     * action is included in result XML.
     */
    public void setDoBlind(Boolean doBlind) {
        this.doBlind = doBlind;
    }

    /**
     * Returns <tt>true</tt> to blind the participant, <tt>false</tt> to unblind
     * or <tt>null</tt> if the action has not been specified(which is invalid).
     */
    public Boolean getDoBlind() {
        return doBlind;
    }

    /**
     * Returns the peer jid that initiated the blind, if any.
     *
     * @return the peer jid that initiated the blind.
     */
    public Jid getActor() {
        return actor;
    }

    /**
     * Sets jid for the peer that initiated the blind.
     *
     * @param actor the jid of the peer doing the blind.
     */
    public void setActor(Jid actor) {
        this.actor = actor;
    }

    /**
     * The action contained in the text part of 'blind' XML element body.
     *
     * @param blockVideoControl <tt>true</tt> to block the webcam of the participant.
     * <tt>null</tt> means no action is included in result XML.
     */
    public void setBlockVideoControl(Boolean blockVideoControl) {
        this.blockVideoControl = blockVideoControl;
    }

    /**
     * Returns <tt>true</tt> to block the webcam of the participant,
     * <tt>false</tt> to show the webcam button or <tt>null</tt> if the
     * action has not been specified(which is invalid).
     */
    public Boolean getBlockVideoControl() {
        return blockVideoControl;
    }

}
