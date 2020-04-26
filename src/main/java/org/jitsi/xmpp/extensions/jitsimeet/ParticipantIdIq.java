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

import org.jitsi.utils.logging.Logger;
import org.jivesoftware.smack.packet.*;
import org.jxmpp.jid.*;

/**
 * IQ used for the signaling of moderator ID in Jitsi Meet
 * conferences.
 *
 * @author Pawel Domas
 */
public class ParticipantIdIq
    extends IQ
{
    
    /**
     * The classLogger instance used by this class.
     */
    private final static Logger classLogger
        = Logger.getLogger(ParticipantIdIq.class);

    /**
     * The logger for this instance. Uses the logging level either the one of
     * {@link #classLogger} or the one passed to the constructor, whichever
     * is higher.
     */
    private final Logger logger = Logger.getLogger(classLogger, null);
    
    /**
     * Name space of participantId packet extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/participant";

    /**
     * XML element name of participantId packet extension.
     */
    public static final String ELEMENT_NAME = "participantId";

    /**
     * Attribute name of "jid".
     */
    public static final String JID_ATTR_NAME = "jid";

    /**
     * Attribute name of "actor".
     */
    public static final String ACTOR_ATTR_NAME = "actor";
    
    /**
     * Attribute name of "with_me".
     */
    public static final String WITH_ME_ATTR_NAME = "withMe";

    /**
     * Muted peer MUC jid.
     */
    private Jid jid;

    /**
     * The jid of the peer tha initiated the participantId, optional.
     */
    private Jid actor;

    /**
     * participantId.
     */
    private String participantId;

    /**
     * Translation with moderator or without.
     */
    private Boolean withMe;

    /**
     * Creates a new instance of this class.
     */
    public ParticipantIdIq()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
            IQChildElementXmlStringBuilder xml)
    {
        
        if (jid != null)
        {
            xml.attribute(JID_ATTR_NAME, jid);
        }

        if (actor != null)
        {
            xml.attribute(ACTOR_ATTR_NAME, actor);
        }
        
        if (withMe != null)
        {
            xml.attribute(WITH_ME_ATTR_NAME, withMe);
        }
        
        xml.rightAngleBracket()
            .append(participantId);

        logger.warn("Building xml ParticipantId " + xml.toString());
        
        return xml;
    }

    /**
     * Sets the MUC jid of the user.
     * @param jid muc jid in the form of room_name@muc.server.net/nickname.
     */
    public void setJid(Jid jid)
    {
        this.jid = jid;
    }

    /**
     * Returns MUC jid of the participant in the form of
     * "room_name@muc.server.net/nickname".
     */
    public Jid getJid()
    {
        return jid;
    }

    /**
     * The action contained in the text part of 'participantId' XML element body.
     * @param participantId 
     */
    public void setParticipantId(String participantId)
    {
        this.participantId = participantId;
    }

    /**
     * Returns participantId
     * or <tt>null</tt> if the action has not been specified(which is invalid).
     */
    public String getParticipantId()
    {
        return participantId;
    }


    /**
     * Returns the peer jid that initiated the participantId, if any.
     * @return the peer jid that initiated the participantId.
     */
    public Jid getActor()
    {
        return actor;
    }

    /**
     * Sets jid for the peer that initiated the participantId.
     * @param actor the jid of the peer doing the participantId.
     */
    public void setActor(Jid actor)
    {
        this.actor = actor;
    }
    
    /**
     * The action contained in the text part of 'participantId' XML element body.
     * @param with_me <tt>true</tt> to translate with moderator. <tt>null</tt> means no
     *             action is included in result XML.
     */
    public void setWithMe(Boolean withMe)
    {
        this.withMe = withMe;
    }

    /**
     * Returns <tt>true</tt> to translate with moderato, <tt>false</tt> to translate the one participant only
     * or <tt>null</tt> if the action has not been specified(which is invalid).
     */
    public Boolean getWithMe()
    {
        return withMe;
    }
}
