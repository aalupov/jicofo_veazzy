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
import org.jitsi.jicofo.db.VeazzyRoomStatus;
import org.jitsi.protocol.xmpp.*;
import org.jitsi.utils.logging.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.id.*;
import org.jxmpp.jid.*;

import java.util.*;
import java.util.stream.*;

/**
 * Class handles various Jitsi Meet extensions IQs like {@link MuteIq}.
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

    private MuteIqHandler muteIqHandler;
    private DialIqHandler dialIqHandler;
    
    private VeazzyBlindIqHandler veazzyBlindIqHandler;
    private VeazzyRoomStatusIqHandler veazzyRoomStatusIqHandler;
    private VeazzyRoomManagerIqHandler veazzyRoomManagerIqHandler;
    private VeazzyRoomFocalParticipantIqHandler veazzyRoomFocalParticipantIqHandler;
    private VeazzyAdvertisingStreamIqHandler veazzyAdvertisingStreamIqHandler;
    private VeazzyQuizQuestionIqHandler veazzyQuizQuestionIqHandler;
    private VeazzyQuizAnswerIqHandler veazzyQuizAnswerIqHandler;

    /**
     * The currently used DB connection.
     */
    private JDBCPostgreSQL clientSql;

    private int veazzyRoomStatusFromDb;

    /**
     * Creates new instance of {@link MeetExtensionsHandler}.
     *
     * @param focusManager <tt>FocusManager</tt> that will be used by new
     * instance to access active conferences and focus XMPP connection.
     */
    public MeetExtensionsHandler(FocusManager focusManager) {
        this.focusManager = focusManager;

        MuteIqProvider.registerMuteIqProvider();
        
        VeazzyBlindIqProvider.registerVeazzyBlindIqProvider();
        VeazzyRoomStatusIqProvider.registerVeazzyRoomStatusIqProvider();
        VeazzyRoomManagerIqProvider.registerVeazzyRoomManagerIqProvider();
        VeazzyRoomFocalParticipantIqProvider.registerVeazzyRoomFocalParticipantIqProvider();
        VeazzyAdvertisingStreamIqProvider.registerVeazzyAdvertisingStreamIqProvider();
        VeazzyQuizQuestionIqProvider.registerVeazzyQuizQuestionIqProvider();
        VeazzyQuizAnswerIqProvider.registerVeazzyQuizAnswerIqProvider();
        
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

        muteIqHandler = new MuteIqHandler();
        dialIqHandler = new DialIqHandler();
        
        veazzyBlindIqHandler = new VeazzyBlindIqHandler();
        veazzyRoomStatusIqHandler = new VeazzyRoomStatusIqHandler();
        veazzyRoomManagerIqHandler = new VeazzyRoomManagerIqHandler();
        veazzyRoomFocalParticipantIqHandler = new VeazzyRoomFocalParticipantIqHandler();
        veazzyAdvertisingStreamIqHandler = new VeazzyAdvertisingStreamIqHandler();
        veazzyQuizQuestionIqHandler = new VeazzyQuizQuestionIqHandler();
        veazzyQuizAnswerIqHandler = new VeazzyQuizAnswerIqHandler();
                
        clientSql = new JDBCPostgreSQL();
        veazzyRoomStatusFromDb = VeazzyRoomStatus.ROOM_STATUS_OPENED;
        
        connection.registerIQRequestHandler(muteIqHandler);
        connection.registerIQRequestHandler(dialIqHandler);
        
        connection.registerIQRequestHandler(veazzyBlindIqHandler);
        connection.registerIQRequestHandler(veazzyRoomStatusIqHandler);
        connection.registerIQRequestHandler(veazzyRoomManagerIqHandler);
        connection.registerIQRequestHandler(veazzyRoomFocalParticipantIqHandler);
        connection.registerIQRequestHandler(veazzyAdvertisingStreamIqHandler);
        connection.registerIQRequestHandler(veazzyQuizQuestionIqHandler);
        connection.registerIQRequestHandler(veazzyQuizAnswerIqHandler);
    }

    private class MuteIqHandler extends AbstractIqRequestHandler {

        MuteIqHandler() {
            super(MuteIq.ELEMENT_NAME,
                    MuteIq.NAMESPACE,
                    IQ.Type.set,
                    Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleMuteIq((MuteIq) iqRequest);
        }
    }

    private class VeazzyBlindIqHandler extends AbstractIqRequestHandler {

        VeazzyBlindIqHandler() {
            super(VeazzyBlindIq.ELEMENT_NAME,
                    VeazzyBlindIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleVeazzyBlindIq((VeazzyBlindIq) iqRequest);
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
            return handleRoomManagerIq((VeazzyRoomManagerIq) iqRequest);
        }
    }

    private class VeazzyRoomFocalParticipantIqHandler extends AbstractIqRequestHandler {

        VeazzyRoomFocalParticipantIqHandler() {
            super(
                    VeazzyRoomFocalParticipantIq.ELEMENT_NAME,
                    VeazzyRoomFocalParticipantIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleRoomFocalParticipantIq((VeazzyRoomFocalParticipantIq) iqRequest);
        }
    }

    private class VeazzyAdvertisingStreamIqHandler extends AbstractIqRequestHandler {

        VeazzyAdvertisingStreamIqHandler() {
            super(
                    VeazzyAdvertisingStreamIq.ELEMENT_NAME,
                    VeazzyAdvertisingStreamIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleAdvertisingStreamIq((VeazzyAdvertisingStreamIq) iqRequest);
        }
    }
    
    private class VeazzyQuizQuestionIqHandler extends AbstractIqRequestHandler {

        VeazzyQuizQuestionIqHandler() {
            super(
                    VeazzyQuizQuestionIq.ELEMENT_NAME,
                    VeazzyQuizQuestionIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleQuizQuestionIq((VeazzyQuizQuestionIq) iqRequest);
        }
    }
    
    private class VeazzyQuizAnswerIqHandler extends AbstractIqRequestHandler {

        VeazzyQuizAnswerIqHandler() {
            super(
                    VeazzyQuizAnswerIq.ELEMENT_NAME,
                    VeazzyQuizAnswerIq.NAMESPACE,
                    IQ.Type.set,
                    IQRequestHandler.Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest) {
            return handleQuizAnswerIq((VeazzyQuizAnswerIq) iqRequest);
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
            
            connection.unregisterIQRequestHandler(muteIqHandler);
            connection.unregisterIQRequestHandler(dialIqHandler);
            
            connection.unregisterIQRequestHandler(veazzyBlindIqHandler);
            connection.unregisterIQRequestHandler(veazzyRoomStatusIqHandler);
            connection.unregisterIQRequestHandler(veazzyRoomManagerIqHandler);
            connection.unregisterIQRequestHandler(veazzyRoomFocalParticipantIqHandler);
            connection.unregisterIQRequestHandler(veazzyAdvertisingStreamIqHandler);
            connection.unregisterIQRequestHandler(veazzyQuizQuestionIqHandler);
            connection.unregisterIQRequestHandler(veazzyQuizAnswerIqHandler);
            
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

    private IQ handleMuteIq(MuteIq muteIq) {
        
        Boolean doMute = muteIq.getDoMute();
        Boolean blockAudioControl = muteIq.getBlockAudioControl();
        logger.info("Block Audio Control is " + blockAudioControl);

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

        if (conference.handleMuteRequest(muteIq.getFrom(), jid, doMute, blockAudioControl)) {
            result = IQ.createResultIQ(muteIq);

            if (!muteIq.getFrom().equals(jid)) {
                logger.info("Mute: " + doMute);
                MuteIq muteStatusUpdate = new MuteIq();
                muteStatusUpdate.setActor(from);
                muteStatusUpdate.setType(IQ.Type.set);
                muteStatusUpdate.setTo(jid);
                muteStatusUpdate.setBlockAudioControl(blockAudioControl);

                muteStatusUpdate.setDoMute(doMute);

                connection.sendStanza(muteStatusUpdate);
            }
        } else {
            result = IQ.createErrorResponse(
                    muteIq,
                    XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
        }

        return result;
    }
    
    private IQ handleVeazzyBlindIq(VeazzyBlindIq blindIq) {
        
        Boolean doBlind = blindIq.getDoBlind();
        Boolean blockVideoControl = blindIq.getBlockVideoControl();
        logger.info("Block Video Control is " + blockVideoControl);
        
        Jid jid = blindIq.getJid();

        if (doBlind == null || jid == null) {
            return IQ.createErrorResponse(blindIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = blindIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Blind error: room not found for JID: " + from);
            return IQ.createErrorResponse(blindIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        if (conference.handleBlindRequest(blindIq.getFrom(), jid, doBlind, blockVideoControl)) {
            result = IQ.createResultIQ(blindIq);

            if (!blindIq.getFrom().equals(jid)) {
                logger.info("Blind: " + doBlind);
                VeazzyBlindIq blindStatusUpdate = new VeazzyBlindIq();
                blindStatusUpdate.setActor(from);
                blindStatusUpdate.setType(IQ.Type.set);
                blindStatusUpdate.setTo(jid);
                blindStatusUpdate.setBlockVideoControl(blockVideoControl);

                blindStatusUpdate.setDoBlind(doBlind);

                connection.sendStanza(blindStatusUpdate);
            }
        } else {
            result = IQ.createErrorResponse(
                    blindIq,
                    XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
        }

        return result;
    }

    private IQ handleRoomStatusIq(VeazzyRoomStatusIq roomStatusIq) {
        
        int veazzyRoomStatus = roomStatusIq.getRoomStatus();
        Boolean checkRequest = roomStatusIq.isCheckRoomStatusRequest();

        Jid jid = roomStatusIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(roomStatusIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        String confName = getConferenceName(jid).toString();
        logger.info("Room Name is " + confName);
        
        boolean check = false;
        if (checkRequest == null) {
            logger.debug("checkRequest null");
            return IQ.createErrorResponse(roomStatusIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }
        else {
            check = checkRequest;
            logger.info("Asking for room status checkRequest: " + check);
            
            if(check) {
                
                if (confName != null) {
                    VeazzyRoomStatus roomStatus = clientSql.getRoomStatusFromDB(confName);
                    if(roomStatus != null) {
                        veazzyRoomStatusFromDb = roomStatus.getStatus();
                        logger.info("Room Status From DB is " + veazzyRoomStatusFromDb);
                    }
                    else {
                        logger.info("Room Status From DB not found");
                    }
                }
            }
        }

        Jid from = roomStatusIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Room status error: room not found for JID: " + from);
            return IQ.createErrorResponse(roomStatusIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        if (!check) {
            
            if (conference.handleRoomStatusRequest(roomStatusIq.getFrom(), veazzyRoomStatus)) {
                result = IQ.createResultIQ(roomStatusIq);

                if (roomStatusIq.getFrom().equals(jid)) {
                    VeazzyRoomStatusIq roomStatusUpdate = new VeazzyRoomStatusIq();
                    roomStatusUpdate.setActor(from);
                    roomStatusUpdate.setType(IQ.Type.set);
                    roomStatusUpdate.setTo(jid);

                    roomStatusUpdate.setRoomStatus(veazzyRoomStatus);

                    connection.sendStanza(roomStatusUpdate);

                    //update DB
                    if (confName != null) {
                        VeazzyRoomStatus roomStatus = clientSql.getRoomStatusFromDB(confName);
                        if(roomStatus != null) {
                            //update
                            roomStatus.setStatus(veazzyRoomStatus);
                            clientSql.updateRoomStatusToDB(roomStatus);
                            logger.info("Room Status updated for room " + roomStatus.getRoomName());
                        }
                        else {
                            //create
                            roomStatus = new VeazzyRoomStatus(confName, veazzyRoomStatus);
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
            
            int roomStatus = veazzyRoomStatusFromDb;
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

    private IQ handleRoomManagerIq(VeazzyRoomManagerIq roomManagerIq) {
        
        String roomManagerId = roomManagerIq.getRoomManagerId();
        logger.info("RoomManagerId is " + roomManagerId);
        
        Boolean roomManagerIdRequest = roomManagerIq.isCheckRoomManagerIdRequest();

        Jid jid = roomManagerIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(roomManagerIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        if (roomManagerId == null && roomManagerIdRequest == null) {
            logger.debug("roomManagerId and roomManagerIdRequest null");
            return IQ.createErrorResponse(roomManagerIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = roomManagerIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Room Manager Id error: ID not found for JID: " + from);
            return IQ.createErrorResponse(roomManagerIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        boolean check = false;
        if (roomManagerIdRequest != null) {
            check = roomManagerIdRequest;
            logger.info("Asking for Room Manager id roomManagerIdRequest: " + check);
        }

        if (!check) {

            if (conference.handleRoomManagerIdRequest(roomManagerIq.getFrom(), roomManagerId)) {
                result = IQ.createResultIQ(roomManagerIq);

                if (roomManagerIq.getFrom().equals(jid)) {
                    VeazzyRoomManagerIq roomManagerUpdate = new VeazzyRoomManagerIq();
                    roomManagerUpdate.setActor(from);
                    roomManagerUpdate.setType(IQ.Type.set);
                    roomManagerUpdate.setTo(jid);

                    roomManagerUpdate.setRoomManagerId(roomManagerId);

                    connection.sendStanza(roomManagerUpdate);

                }
            } else {
                result = IQ.createErrorResponse(
                        roomManagerIq,
                        XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
            }
        } else {
            
            String managerId = conference.getVeazzyRoomManagerId();
            result = IQ.createResultIQ(roomManagerIq);

            VeazzyRoomManagerIq roomManagerUpdate = new VeazzyRoomManagerIq();
            roomManagerUpdate.setActor(from);
            roomManagerUpdate.setType(IQ.Type.set);
            roomManagerUpdate.setTo(jid);

            roomManagerUpdate.setRoomManagerId(managerId);

            connection.sendStanza(roomManagerUpdate);
        }

        return result;
    }

    private IQ handleRoomFocalParticipantIq(VeazzyRoomFocalParticipantIq roomFocalParticipantIq) {
        
        String roomFocalParticipantId = roomFocalParticipantIq.getRoomFocalParticipantId();
        logger.info("RoomFocalParticipantId is " + roomFocalParticipantId);
        
        Boolean roomFocalParticipantIdRequest = roomFocalParticipantIq.isCheckRoomFocalParticipantIdRequest();

        Jid jid = roomFocalParticipantIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(roomFocalParticipantIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        if (roomFocalParticipantId == null && roomFocalParticipantIdRequest == null) {
            logger.debug("roomFocalParticipantId and roomFocalParticipantIdRequest null");
            return IQ.createErrorResponse(roomFocalParticipantIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = roomFocalParticipantIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Room Focal Participant Id error: ID not found for JID: " + from);
            return IQ.createErrorResponse(roomFocalParticipantIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        boolean check = false;
        if (roomFocalParticipantIdRequest != null) {
            check = roomFocalParticipantIdRequest;
            logger.info("Asking for Room Focal Participant id roomFocalParticipantIdRequest: " + check);
        }

        if (!check) {

            if (conference.handleFocalParticipantIdRequest(roomFocalParticipantIq.getFrom(), roomFocalParticipantId)) {
                
                result = IQ.createResultIQ(roomFocalParticipantIq);

                if (!roomFocalParticipantIq.getFrom().equals(jid)) {
                    VeazzyRoomFocalParticipantIq roomFocalParticipantUpdate = new VeazzyRoomFocalParticipantIq();
                    roomFocalParticipantUpdate.setActor(from);
                    roomFocalParticipantUpdate.setType(IQ.Type.set);
                    roomFocalParticipantUpdate.setTo(jid);

                    roomFocalParticipantUpdate.setRoomFocalParticipantId(roomFocalParticipantId);

                    connection.sendStanza(roomFocalParticipantUpdate);

                }
            } else {
                result = IQ.createErrorResponse(
                        roomFocalParticipantIq,
                        XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
            }
        } else {
            
            String focalParticipantId = conference.getVeazzyRoomFocalParticipantId();
            result = IQ.createResultIQ(roomFocalParticipantIq);

            VeazzyRoomFocalParticipantIq roomFocalParticipantUpdate = new VeazzyRoomFocalParticipantIq();
            roomFocalParticipantUpdate.setActor(from);
            roomFocalParticipantUpdate.setType(IQ.Type.set);
            roomFocalParticipantUpdate.setTo(jid);

            roomFocalParticipantUpdate.setRoomFocalParticipantId(focalParticipantId);

            connection.sendStanza(roomFocalParticipantUpdate);
        }

        return result;
    }

    private IQ handleAdvertisingStreamIq(VeazzyAdvertisingStreamIq advertisingStreamIq) {
        
        logger.info("handleStreamIq");
        int streamStatus = advertisingStreamIq.getAdvertisingStreamStatus();

        Jid jid = advertisingStreamIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(advertisingStreamIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = advertisingStreamIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Stream Id error: room not found for JID: " + from);
            return IQ.createErrorResponse(advertisingStreamIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        logger.info("handleStreamIq condition OK");
        
        if (conference.handleAdvertisingStreamIdRequest(jid, advertisingStreamIq.getFrom(), streamStatus)) {
            
            result = IQ.createResultIQ(advertisingStreamIq);

            if (!advertisingStreamIq.getFrom().equals(jid)) {
                
                VeazzyAdvertisingStreamIq streamIdUpdate = new VeazzyAdvertisingStreamIq();
                streamIdUpdate.setActor(from);
                streamIdUpdate.setType(IQ.Type.set);
                streamIdUpdate.setTo(jid);
                streamIdUpdate.setAdvertisingStreamStatus(streamStatus);

                connection.sendStanza(streamIdUpdate);
            }
        } else {
            result = IQ.createErrorResponse(
                    advertisingStreamIq,
                    XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
        }

        return result;
    }
    
    private IQ handleQuizQuestionIq(VeazzyQuizQuestionIq veazzyQuizQuestionIq) {
        
        logger.info("veazzyQuizQuestionIq");

        Jid jid = veazzyQuizQuestionIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(veazzyQuizQuestionIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = veazzyQuizQuestionIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Quiz Id error: room not found for JID: " + from);
            return IQ.createErrorResponse(veazzyQuizQuestionIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        logger.info("handleQuizIq condition OK");
        
        if (conference.handleQuizQuestionIdRequest(jid, veazzyQuizQuestionIq.getFrom())) {
            
            result = IQ.createResultIQ(veazzyQuizQuestionIq);

            if (!veazzyQuizQuestionIq.getFrom().equals(jid)) {
                
                VeazzyQuizQuestionIq quizQuestionUpdate = new VeazzyQuizQuestionIq();
                quizQuestionUpdate.setActor(from);
                quizQuestionUpdate.setType(IQ.Type.set);
                quizQuestionUpdate.setTo(jid);
                
                quizQuestionUpdate.setQuestion(veazzyQuizQuestionIq.getQuestion());
                quizQuestionUpdate.setAnswerA(veazzyQuizQuestionIq.getAnswerA());
                quizQuestionUpdate.setAnswerB(veazzyQuizQuestionIq.getAnswerB());
                quizQuestionUpdate.setAnswerC(veazzyQuizQuestionIq.getAnswerC());
                quizQuestionUpdate.setAnswerD(veazzyQuizQuestionIq.getAnswerD());
                quizQuestionUpdate.setStatusA(veazzyQuizQuestionIq.getStatusA());
                quizQuestionUpdate.setStatusB(veazzyQuizQuestionIq.getStatusB());
                quizQuestionUpdate.setStatusC(veazzyQuizQuestionIq.getStatusC());
                quizQuestionUpdate.setStatusD(veazzyQuizQuestionIq.getStatusD());

                connection.sendStanza(quizQuestionUpdate);
            }
        } else {
            result = IQ.createErrorResponse(
                    veazzyQuizQuestionIq,
                    XMPPError.getBuilder(XMPPError.Condition.internal_server_error));
        }

        return result;
    }
    
    private IQ handleQuizAnswerIq(VeazzyQuizAnswerIq veazzyQuizAnswerIq) {
        
        logger.info("veazzyQuizAnswerIq");

        Jid jid = veazzyQuizAnswerIq.getJid();

        if (jid == null) {
            logger.debug("jid null");
            return IQ.createErrorResponse(veazzyQuizAnswerIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        Jid from = veazzyQuizAnswerIq.getFrom();
        JitsiMeetConferenceImpl conference = getConferenceForMucJid(from);
        if (conference == null) {
            logger.debug("Answer Id error: room not found for JID: " + from);
            return IQ.createErrorResponse(veazzyQuizAnswerIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
        }

        IQ result;

        logger.info("handleAnswerIq condition OK");
        
        if (conference.handleQuizAnswerIdRequest(jid, veazzyQuizAnswerIq.getFrom())) {
            
            result = IQ.createResultIQ(veazzyQuizAnswerIq);

            if (!veazzyQuizAnswerIq.getFrom().equals(jid)) {
                
                VeazzyQuizAnswerIq quizAnswerUpdate = new VeazzyQuizAnswerIq();
                quizAnswerUpdate.setActor(from);
                quizAnswerUpdate.setType(IQ.Type.set);
                quizAnswerUpdate.setTo(jid);
                
                quizAnswerUpdate.setAnswer(veazzyQuizAnswerIq.getAnswer());

                connection.sendStanza(quizAnswerUpdate);
            }
        } else {
            result = IQ.createErrorResponse(
                    veazzyQuizAnswerIq,
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
