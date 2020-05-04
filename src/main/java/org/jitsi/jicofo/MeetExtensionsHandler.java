/*
 * Jicofo, the Jitsi Conference Focus.
 *
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
package org.jitsi.jicofo;

import org.jitsi.xmpp.extensions.rayo.*;
import net.java.sip.communicator.service.protocol.*;

import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.jicofo.jigasi.*;
import org.jitsi.jicofo.db.RoomStatus;
import org.jitsi.protocol.xmpp.*;
import org.jitsi.utils.logging.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.id.*;
import org.jxmpp.jid.*;

import java.util.*;
import java.util.stream.*;

/**
 * Class handles various Jitsi Meet extensions IQs like {@link VeazzyMuteIq}.
 *
 * @author Pawel Domas
 * @author Boris Grozev
 */
public class MeetExtensionsHandler {

    /**
     * The logger
     */
    private final static Logger logger
            = Logger.getLogger(MeetExtensionsHandler.class);

    /**
     * <tt>FocusManager</tt> instance for accessing info about all active
     * conferences.
     */
    private final FocusManager focusManager;

    /**
     * The currently used XMPP connection.
     */
    private XmppConnection connection;

    private VeazzyMuteIqHandler veazzyMuteIqHandler;
    private VeazzyRoomStatusIqHandler veazzyRoomStatusIqHandler;
    private VeazzyRoomManagerIqHandler veazzyRoomManagerIqHandler;
    private VeazzyMainScreenParticipantIqHandler veazzyMainScreenParticipantIqHandler;
    private VeazzyStreamIqHandler veazzyStreamIqHandler;
    
    private DialIqHandler dialIqHandler;

    /**
     * The currently used DB connection.
     */
    private JDBCPostgreSQL clientSql;

    private Boolean roomStatusFromDb;

    /**
     * Creates new instance of {@link MeetExtensionsHandler}.
     *
     * @param focusManager <tt>FocusManager</tt> that will be used by new
     * instance to access active conferences and focus XMPP connection.
     */
    public MeetExtensionsHandler(FocusManager focusManager) {
        this.focusManager = focusManager;

        VeazzyMuteIqProvider.registerVeazzyMuteIqProvider();
        VeazzyRoomStatusIqProvider.registerVeazzyRoomStatusIqProvider();
        VeazzyRoomManagerIqProvider.registerVeazzyRoomManagerIqProvider();
        VeazzyMainScreenParticipantIqProvider.registerVeazzyMainScreenParticipantIqProvider();
        VeazzyStreamIqProvider.registerVeazzyStreamIqProvider();
        
        new RayoIqProvider().registerRayoIQs();
        StartMutedProvider.registerStartMutedProvider();
    }

    /**
     * Initializes this instance and bind packet listeners.
     */
    public void init() {
        this.connection
                = focusManager.getOperationSet(
                        OperationSetDirectSmackXmpp.class).getXmppConnection();

        veazzyMuteIqHandler = new VeazzyMuteIqHandler();
        veazzyRoomStatusIqHandler = new VeazzyRoomStatusIqHandler();
        veazzyRoomManagerIqHandler = new VeazzyRoomManagerIqHandler();
        veazzyMainScreenParticipantIqHandler = new VeazzyMainScreenParticipantIqHandler();
        veazzyStreamIqHandler = new VeazzyStreamIqHandler();
        
        dialIqHandler = new DialIqHandler();
        
        clientSql = new JDBCPostgreSQL();
        roomStatusFromDb = true;
        
        connection.registerIQRequestHandler(veazzyMuteIqHandler);
        connection.registerIQRequestHandler(veazzyRoomStatusIqHandler);
        connection.registerIQRequestHandler(veazzyRoomManagerIqHandler);
        connection.registerIQRequestHandler(veazzyMainScreenParticipantIqHandler);
        connection.registerIQRequestHandler(veazzyStreamIqHandler);
        
        connection.registerIQRequestHandler(dialIqHandler);
    }

    private class VeazzyMuteIqHandler extends AbstractIqRequestHandler {

        VeazzyMuteIqHandler() {
            super(VeazzyMuteIq.ELEMENT_NAME,
                    VeazzyMuteIq.NAMESPACE,
                    IQ.Type.set,
                    Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleMuteIq((VeazzyMuteIq) iqRequest);
        }
    }

    private class VeazzyRoomStatusIqHandler extends AbstractIqRequestHandler {

        VeazzyRoomStatusIqHandler() {
            super(
                    VeazzyRoomStatusIq.ELEMENT_NAME,
                    VeazzyRoomStatusIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleRoomStatusIq((VeazzyRoomStatusIq) iqRequest);
        }
    }

    private class VeazzyRoomManagerIqHandler extends AbstractIqRequestHandler {

        VeazzyRoomManagerIqHandler() {
            super(
                    VeazzyRoomManagerIq.ELEMENT_NAME,
                    VeazzyRoomManagerIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleModeratorIdIq((VeazzyRoomManagerIq) iqRequest);
        }
    }

    private class VeazzyMainScreenParticipantIqHandler extends AbstractIqRequestHandler {

        VeazzyMainScreenParticipantIqHandler() {
            super(
                    VeazzyMainScreenParticipantIq.ELEMENT_NAME,
                    VeazzyMainScreenParticipantIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleParticipantIdIq((VeazzyMainScreenParticipantIq) iqRequest);
        }
    }

    private class VeazzyStreamIqHandler extends AbstractIqRequestHandler {

        VeazzyStreamIqHandler() {
            super(
                    VeazzyStreamIq.ELEMENT_NAME,
                    VeazzyStreamIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleStreamIq((VeazzyStreamIq) iqRequest);
        }
    }
    
    private class DialIqHandler extends AbstractIqRequestHandler {

        DialIqHandler() {
            super(RayoIqProvider.DialIq.ELEMENT_NAME,
                    RayoIqProvider.NAMESPACE,
                    IQ.Type.set,
                    Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            // let's retry 2 times sending the rayo
            // by default we have 15 seconds timeout waiting for reply
            // 3 timeouts will give us 45 seconds to reply to user with an error
            return handleRayoIQ((RayoIqProvider.DialIq) iqRequest, 2, null);
        }
    }

    /**
     * Disposes this instance and stop listening for extensions packets.
     */
    public void dispose() {
        if (connection != null) {
            
            connection.unregisterIQRequestHandler(veazzyMuteIqHandler);
            connection.unregisterIQRequestHandler(veazzyRoomStatusIqHandler);
            connection.unregisterIQRequestHandler(veazzyRoomManagerIqHandler);
            connection.unregisterIQRequestHandler(veazzyMainScreenParticipantIqHandler);
            connection.unregisterIQRequestHandler(veazzyStreamIqHandler);
            
            connection.unregisterIQRequestHandler(dialIqHandler);
            connection = null;
        }
    }

    private JitsiMeetConferenceImpl getConferenceForMucJid(Jid mucJid) {
        EntityBareJid roomName = mucJid.asEntityBareJidIfPossible();
        if (roomName == null) {
            return null;
        }
        return focusManager.getConference(roomName);
    }

    private EntityBareJid getConferenceName(Jid mucJid) {
        EntityBareJid roomName = mucJid.asEntityBareJidIfPossible();
        if (roomName == null) {
            return null;
        }
        return roomName;
    }

    private IQ handleMuteIq(VeazzyMuteIq muteIq) {
        Boolean doMute = muteIq.getMute();
        Boolean blockStatus = muteIq.getBlock();
        logger.info("Block status is " + blockStatus);
        Boolean videoMute = muteIq.getVideo();
        logger.info("video to mute is " + videoMute);
        Jid jid = muteIq.getJid();

        if (doMute == null || jid == null) {
            return IQ.createErrorResponse(muteIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = muteIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Mute error: room not found for JID: " + from);
            return IQ.createErrorResponse(muteIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        if (conference.handleMuteRequest(muteIq.getFrom(), jid, doMute)) {
            result = IQ.createResultIQ(muteIq);

            if (!muteIq.getFrom().equals(jid)) {
                logger.info(doMute);
                VeazzyMuteIq muteStatusUpdate = new VeazzyMuteIq();
                muteStatusUpdate.setActor(from);
                muteStatusUpdate.setType(IQ.Type.set);
                muteStatusUpdate.setTo(jid);
                muteStatusUpdate.setBlock(blockStatus);
                muteStatusUpdate.setVideo(videoMute);

                muteStatusUpdate.setMute(doMute);

                connection.sendStanza(muteStatusUpdate);
            }
        } else {
            result = IQ.createErrorResponse(
                    muteIq,
                    XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
        }

        return result;
    }

    private IQ handleRoomStatusIq(VeazzyRoomStatusIq roomStatusIq) {
        
        Boolean doRoomStatusOpen = roomStatusIq.getRoomStatus();
        Boolean checkRequest = roomStatusIq.getCheckRequest();

        Jid jid = roomStatusIq.getJid();

        String confName = getConferenceName(jid).toString();
        logger.info("Room Name is " + confName);

        if (confName != null) {
            RoomStatus roomStatus = clientSql.getRoomStatusFromDB(confName);
            if(roomStatus != null) {
                roomStatusFromDb = roomStatus.getStatus();
                logger.info("Room Status From DB is " + roomStatusFromDb);
            }
            else {
                logger.info("Room Status From DB not found");
            }
        }

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(roomStatusIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        if (checkRequest == null && doRoomStatusOpen == null) {
            logger.debug("checkRequest and doRoomStatusOpen null");
            return IQ.createErrorResponse(roomStatusIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = roomStatusIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Room status error: room not found for JID: " + from);
            return IQ.createErrorResponse(roomStatusIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        boolean check = false;
        if (checkRequest != null) {
            check = checkRequest;
            logger.info("Asking for room status checkRequest: " + check);
        }

        if (!check) {
            //if (conference.handleRoomStatusRequest(roomStatusIq.getFrom(), jid, doRoomStatusOpen))
            if (conference.handleRoomStatusRequest(roomStatusIq.getFrom(), doRoomStatusOpen)) {
                result = IQ.createResultIQ(roomStatusIq);

                if (roomStatusIq.getFrom().equals(jid)) {
                    VeazzyRoomStatusIq roomStatusUpdate = new VeazzyRoomStatusIq();
                    roomStatusUpdate.setActor(from);
                    roomStatusUpdate.setType(IQ.Type.set);
                    roomStatusUpdate.setTo(jid);

                    roomStatusUpdate.setRoomStatus(doRoomStatusOpen);

                    connection.sendStanza(roomStatusUpdate);

                    //update DB
                    if (confName != null) {
                        RoomStatus roomStatus = clientSql.getRoomStatusFromDB(confName);
                        if(roomStatus != null) {
                            //update
                            roomStatus.setStatus(doRoomStatusOpen);
                            clientSql.updateRoomStatusToDB(roomStatus);
                            logger.info("Room Status updated for room " + roomStatus.getRoomName());
                        }
                        else {
                            //create
                            roomStatus = new RoomStatus(confName, doRoomStatusOpen);
                            clientSql.insertRoomStatusToDB(roomStatus);
                            logger.info("Room Status created for room " + roomStatus.getRoomName());
                        }
                    }
                }
            } else {
                result = IQ.createErrorResponse(
                        roomStatusIq,
                        XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
            }
        } else {
            boolean roomStatus = roomStatusFromDb;
            result = IQ.createResultIQ(roomStatusIq);

            VeazzyRoomStatusIq roomStatusUpdate = new VeazzyRoomStatusIq();
            roomStatusUpdate.setActor(from);
            roomStatusUpdate.setType(IQ.Type.set);
            roomStatusUpdate.setTo(jid);

            roomStatusUpdate.setRoomStatus(roomStatus);

            connection.sendStanza(roomStatusUpdate);
        }

        return result;
    }

    private IQ handleModeratorIdIq(VeazzyRoomManagerIq moderatorIdIq) {
        String doModeratorIdOpen = moderatorIdIq.getModeratorId();
        logger.info("ModeratorId is " + doModeratorIdOpen);
        Boolean moderatorIdRequest = moderatorIdIq.getModeratorIdRequest();

        Jid jid = moderatorIdIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(moderatorIdIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        if (moderatorIdRequest == null && doModeratorIdOpen == null) {
            logger.debug("moderatorIdRequest and doModeratorIdOpen null");
            return IQ.createErrorResponse(moderatorIdIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = moderatorIdIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Moderator Id error: ID not found for JID: " + from);
            return IQ.createErrorResponse(moderatorIdIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        boolean check = false;
        if (moderatorIdRequest != null) {
            check = moderatorIdRequest;
            logger.info("Asking for moderator id moderatorIdRequest: " + check);
        }

        if (!check) {

            if (conference.handleModeratorIdRequest(moderatorIdIq.getFrom(), doModeratorIdOpen)) {
                result = IQ.createResultIQ(moderatorIdIq);

                if (moderatorIdIq.getFrom().equals(jid)) {
                    VeazzyRoomManagerIq moderatorIdUpdate = new VeazzyRoomManagerIq();
                    moderatorIdUpdate.setActor(from);
                    moderatorIdUpdate.setType(IQ.Type.set);
                    moderatorIdUpdate.setTo(jid);

                    moderatorIdUpdate.setModeratorId(doModeratorIdOpen);

                    connection.sendStanza(moderatorIdUpdate);

                }
            } else {
                result = IQ.createErrorResponse(
                        moderatorIdIq,
                        XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
            }
        } else {
            String moderatorId = conference.getVeazzyRoomManagerId();
            result = IQ.createResultIQ(moderatorIdIq);

            VeazzyRoomManagerIq moderatorIdUpdate = new VeazzyRoomManagerIq();
            moderatorIdUpdate.setActor(from);
            moderatorIdUpdate.setType(IQ.Type.set);
            moderatorIdUpdate.setTo(jid);

            moderatorIdUpdate.setModeratorId(moderatorId);

            connection.sendStanza(moderatorIdUpdate);
        }

        return result;
    }

    private IQ handleParticipantIdIq(VeazzyMainScreenParticipantIq participantIdIq) {
        String doParticipantIdOpen = participantIdIq.getParticipantId();
        logger.info("ParticipantId is " + doParticipantIdOpen);
        Boolean withMe = participantIdIq.getWithMe();

        Jid jid = participantIdIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(participantIdIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        if (doParticipantIdOpen == null) {
            logger.debug("doParticipantIdOpen is null");
            return IQ.createErrorResponse(participantIdIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = participantIdIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Participant Id error: ID not found for JID: " + from);
            return IQ.createErrorResponse(participantIdIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        if (conference.handleParticipantIdRequest(participantIdIq.getFrom(), doParticipantIdOpen)) {
            result = IQ.createResultIQ(participantIdIq);

            if (!participantIdIq.getFrom().equals(jid)) {
                VeazzyMainScreenParticipantIq participantIdUpdate = new VeazzyMainScreenParticipantIq();
                participantIdUpdate.setActor(from);
                participantIdUpdate.setType(IQ.Type.set);
                participantIdUpdate.setTo(jid);
                participantIdUpdate.setWithMe(withMe);

                participantIdUpdate.setParticipantId(doParticipantIdOpen);

                connection.sendStanza(participantIdUpdate);

            }
        } else {
            result = IQ.createErrorResponse(
                    participantIdIq,
                    XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
        }

        return result;
    }

    
    private IQ handleStreamIq(VeazzyStreamIq streamIq) {
        
        logger.info("handleStreamIq");
        Boolean stream = streamIq.getStream();

        Jid jid = streamIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(streamIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }
        if (stream == null) {
            logger.debug("stream  null");
            return IQ.createErrorResponse(streamIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = streamIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Stream Id error: room not found for JID: " + from);
            return IQ.createErrorResponse(streamIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        logger.info("handleStreamIq condition OK");
        
        if (conference.handleStreamIdRequest(jid, streamIq.getFrom(), stream)) {
            
            result = IQ.createResultIQ(streamIq);

            if (!streamIq.getFrom().equals(jid)) {
                
                VeazzyStreamIq streamIdUpdate = new VeazzyStreamIq();
                streamIdUpdate.setActor(from);
                streamIdUpdate.setType(IQ.Type.set);
                streamIdUpdate.setTo(jid);
                streamIdUpdate.setStream(stream);

                connection.sendStanza(streamIdUpdate);
            }
        } else {
            result = IQ.createErrorResponse(
                    streamIq,
                    XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
        }

        return result;
    }

    
    /**
     * Checks whether sending the rayo message is ok (checks member, moderators)
     * and sends the message to the selected jigasi (from brewery muc or to the
     * component service).
     *
     * @param dialIq the iq to send.
     * @param retryCount the number of attempts to be made for sending this iq,
     * if no reply is received from the remote side.
     * @param exclude <tt>null</tt> or a list of jigasi Jids which we already
     * tried sending in attempt to retry.
     *
     * @return the iq to be sent as a reply.
     */
    private IQ handleRayoIQ(RayoIqProvider.DialIq dialIq, int retryCount,
            List<Jid> exclude) {
        Jid from = dialIq.getFrom();

        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);

        if (conference == null) {
            logger.debug("Dial error: room not found for JID: " + from);
            return IQ.createErrorResponse(dialIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        ChatRoomMemberRole role = conference.getRoleForMucJid(from);

        if (role == null) {
            // Only room members are allowed to send requests
            return IQ.createErrorResponse(
                    dialIq, XMPPError.getBuilder(XMPPError.Condition.forbidden));
        }

        if (ChatRoomMemberRole.MODERATOR.compareTo(role) < 0) {
            // Moderator permission is required
            return IQ.createErrorResponse(
                    dialIq, XMPPError.getBuilder(XMPPError.Condition.not_allowed));
        }

        Set<String> bridgeRegions = conference.getBridges().keySet().stream()
                .map(b -> b.getRegion())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Check if Jigasi is available
        Jid jigasiJid;
        JigasiDetector detector = conference.getServices().getJigasiDetector();
        if (detector == null
                || (jigasiJid = detector.selectJigasi(
                        exclude, bridgeRegions)) == null) {
            jigasiJid = conference.getServices().getSipGateway();
        }

        if (jigasiJid == null) {
            // Not available
            return IQ.createErrorResponse(
                    dialIq,
                    XMPPError.getBuilder(
                            XMPPError.Condition.service_unavailable).build());
        }

        // Redirect original request to Jigasi component
        RayoIqProvider.DialIq forwardDialIq = new RayoIqProvider.DialIq(dialIq);
        forwardDialIq.setFrom((Jid) null);
        forwardDialIq.setTo(jigasiJid);
        forwardDialIq.setStanzaId(StanzaIdUtil.newStanzaId());

        try {
            IQ reply = connection.sendPacketAndGetReply(forwardDialIq);

            if (reply == null) {
                if (retryCount > 0) {
                    if (exclude == null) {
                        exclude = new ArrayList<>();
                    }
                    exclude.add(jigasiJid);

                    // let's retry lowering the number of attempts
                    return this.handleRayoIQ(dialIq, retryCount - 1, exclude);
                } else {
                    return IQ.createErrorResponse(
                            dialIq,
                            XMPPError.getBuilder(
                                    XMPPError.Condition.remote_server_timeout));
                }
            }

            // Send Jigasi response back to the client
            reply.setFrom((Jid) null);
            reply.setTo(from);
            reply.setStanzaId(dialIq.getStanzaId());
            return reply;
        } catch (OperationFailedException e) {
            logger.error("Failed to send DialIq - XMPP disconnected", e);
            return IQ.createErrorResponse(
                    dialIq,
                    XMPPError.getBuilder(
                            XMPPError.Condition.internal_server_error)
                            .setDescriptiveEnText("Failed to forward DialIq"));
        }
    }
}
