/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import org.jitsi.jicofo.bridge.*;
import org.jitsi.utils.*;
import org.jitsi.xmpp.extensions.colibri.*;
import org.jitsi.xmpp.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

import org.jitsi.impl.protocol.xmpp.colibri.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.jicofo.event.*;
import org.jitsi.jicofo.jigasi.*;
import org.jitsi.jicofo.recording.jibri.*;
import org.jitsi.protocol.xmpp.*;
import org.jitsi.protocol.xmpp.colibri.*;
import org.jitsi.protocol.xmpp.util.*;
import org.jitsi.eventadmin.*;

import org.jitsi.utils.logging.Logger;
import org.jivesoftware.smack.packet.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.jid.parts.*;
import org.jxmpp.stringprep.*;
import org.osgi.framework.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.*;
import org.jitsi.protocol.xmpp.colibri.exception.RoomClosedException;

/**
 * Represents a Jitsi Meet conference. Manages the Jingle sessions with the
 * participants, as well as the COLIBRI session with the jitsi-videobridge
 * instances used for the conference.
 *
 * A note on synchronization: this class uses a lot of 'synchronized' blocks, on
 * 4 different objects ({@link #bridges}, {@link #participantLock}, {@code this}
 * and {@link BridgeSession#octoParticipant}). At the time of this writing it
 * seems that multiple locks are acquired only in the following orders:
 * {@code participantsLock} -> {@code bridges}, and {@code participantsLock} ->
 * {@code this} -> {@code bridges}.
 *
 * This seems safe, but it is hard to maintain this way, and we should re-factor
 * to simplify.
 *
 *
 * @author Pawel Domas
 * @author Boris Grozev
 */
public class JitsiMeetConferenceImpl
        implements JitsiMeetConference, RegistrationStateChangeListener, JingleRequestHandler, EventHandler {

    /**
     * The classLogger instance used by this class.
     */
    private final static Logger classLogger = Logger.getLogger(JitsiMeetConferenceImpl.class);

    /**
     * A random generator.
     */
    private final static Random RANDOM = new Random();

    /**
     * An identifier of this {@link JitsiMeetConferenceImpl}.
     */
    private final String id;

    /**
     * Name of MUC room that is hosting Jitsi Meet conference.
     */
    private final EntityBareJid roomName;

    /**
     * {@link ConferenceListener} that will be notified about conference events.
     */
    private final JitsiMeetConferenceImpl.ConferenceListener listener;

    /**
     * The logger for this instance. Uses the logging level either the one of
     * {@link #classLogger} or the one passed to the constructor, whichever is
     * higher.
     */
    private final Logger logger = Logger.getLogger(classLogger, null);

    /**
     * The instance of conference configuration.
     */
    private final JitsiMeetConfig config;

    /**
     * The instance of global configuration.
     */
    private final JitsiMeetGlobalConfig globalConfig;

    /**
     * XMPP protocol provider handler used by the focus.
     */
    private final ProtocolProviderHandler protocolProviderHandler;

    /**
     * XMPP protocol provider used for the JVB connection. Unless configured
     * with properties defined in {@link BridgeMucDetector} it wil be the same
     * as {@link #protocolProviderHandler}
     */
    private final ProtocolProviderHandler jvbXmppConnection;

    /**
     * The name of XMPP user used by the focus to login.
     */
    private final Resourcepart focusUserName;

    /**
     * Chat room operation set used to handle MUC stuff.
     */
    private OperationSetMultiUserChat chatOpSet;

    /**
     * Jibri operation set to (un)register recorders.
     */
    private OperationSetJibri jibriOpSet;

    /**
     * Conference room chat instance.
     */
    private volatile ChatRoomExtension chatRoom;

    /**
     * Operation set used to handle Jingle sessions with conference
     * participants.
     */
    private OperationSetJingle jingle;

    /**
     * Colibri operation set used to manage videobridge channels allocations.
     */
    private OperationSetColibriConference colibri;

    /**
     * Jitsi Meet tool used for specific operations like adding presence
     * extensions.
     */
    private OperationSetJitsiMeetTools meetTools;

    /**
     * The list of all conference participants.
     */
    private final List<Participant> participants = new CopyOnWriteArrayList<>();

    /**
     * This lock is used to synchronise write access to {@link #participants}.
     *
     * WARNING: To avoid deadlocks we must make sure that any code paths that
     * lock both {@link #bridges} and {@link #participantLock} does so in the
     * correct order. The lock on {@link #participantLock} must be acquired
     * first.
     */
    private final Object participantLock = new Object();

    /**
     * The {@link JibriRecorder} instance used to provide live streaming through
     * Jibri.
     */
    private JibriRecorder jibriRecorder;

    /**
     * The {@link JibriSipGateway} instance which provides video SIP gateway
     * services for this conference.
     */
    private JibriSipGateway jibriSipGateway;

    /**
     * The {@link TranscriberManager} who listens for participants requesting
     * transcription and, when necessary, dialing the transcriber instance.
     */
    private TranscriberManager transcriberManager;

    /**
     * Information about Jitsi Meet conference services like videobridge, SIP
     * gateway, Jirecon.
     */
    private JitsiMeetServices services;

    /**
     * Chat room roles and presence handler.
     */
    private ChatRoomRoleAndPresence rolesAndPresence;

    /**
     * Indicates if this instance has been started (initialized).
     */
    private volatile boolean started;

    /**
     * Idle timestamp for this focus, -1 means active, otherwise
     * System.currentTimeMillis() is set when focus becomes idle. Used to detect
     * idle session and expire it if idle time limit is exceeded.
     */
    private long idleTimestamp = -1;

    /**
     * A timeout task which will terminate media session of the user who is
     * sitting alone in the room for too long.
     */
    private Future<?> singleParticipantTout;

    /**
     * Contains the flags which indicate whether participants being invited to
     * the conference as a result of joining (as opposed to having already
     * joined) should be invited with the "start muted" option. The element at
     * offset 0 is for audio, at offset 1 for video.
     */
    private boolean[] startMuted = {false, false};

    /**
     * The name of shared Etherpad document. Is advertised through MUC Presence
     * by Jicofo user.
     */
    private final String etherpadName;

    /**
     * Bridge <tt>EventHandler</tt> registration.
     */
    private ServiceRegistration<EventHandler> eventHandlerRegistration;

    /**
     * <tt>ScheduledExecutorService</tt> service used to schedule delayed tasks
     * by this <tt>JitsiMeetConference</tt> instance.
     */
    private ScheduledExecutorService executor;

    /**
     * The list of {@link BridgeSession} currently in use by this conference.
     *
     * WARNING: To avoid deadlocks we must make sure that any code paths that
     * lock both {@link #bridges} and {@link #participantLock} does so in the
     * correct order. The lock on {@link #participantLock} must be acquired
     * first.
     *
     */
    private final List<BridgeSession> bridges = new LinkedList<>();

    /**
     * The conference properties that we advertise in presence in the XMPP MUC.
     */
    private final ConferenceProperties conferenceProperties = new ConferenceProperties();

    /**
     * See {@link JitsiMeetConference#includeInStatistics()}
     */
    private final boolean includeInStatistics;

    
    private int veazzyRoomStatus = VeazzyRoomStatusIq.ROOM_STATUS_OPENED;
    private String veazzyRoomManagerId = "00000000";
    private String veazzyRoomFocalParticipantId = "00000000";
    
    /**
     * Creates new instance of {@link JitsiMeetConferenceImpl}.
     *
     * @param roomName name of MUC room that is hosting the conference.
     * @param focusUserName focus user login.
     * @param listener the listener that will be notified about this instance
     * events.
     * @param config the conference configuration instance.
     * @param globalConfig an instance of the global config service.
     * @param logLevel (optional) the logging level to be used by this instance.
     * See {@link #logger} for more details.
     */
    public JitsiMeetConferenceImpl(EntityBareJid roomName, Resourcepart focusUserName,
            ProtocolProviderHandler protocolProviderHandler, ProtocolProviderHandler jvbXmppConnection,
            ConferenceListener listener, JitsiMeetConfig config, JitsiMeetGlobalConfig globalConfig, Level logLevel,
            String id, boolean includeInStatistics) {
        this.protocolProviderHandler = Objects.requireNonNull(protocolProviderHandler, "protocolProviderHandler");
        this.jvbXmppConnection = Objects.requireNonNull(jvbXmppConnection, "jvbXmppConnection");
        this.config = Objects.requireNonNull(config, "config");

        this.id = id;
        this.roomName = roomName;
        this.focusUserName = focusUserName;
        this.listener = listener;
        this.globalConfig = globalConfig;
        this.etherpadName = createSharedDocumentName();

        if (logLevel != null) {
            logger.setLevel(logLevel);
        }
        this.includeInStatistics = includeInStatistics;
    }

    public JitsiMeetConferenceImpl(EntityBareJid roomName, Resourcepart focusUserName,
            ProtocolProviderHandler protocolProviderHandler, ProtocolProviderHandler jvbXmppConnection,
            ConferenceListener listener, JitsiMeetConfig config, JitsiMeetGlobalConfig globalConfig, Level logLevel,
            String id) {
        this(roomName, focusUserName, protocolProviderHandler, jvbXmppConnection, listener, config, globalConfig,
                logLevel, id, false);
    }

    /**
     * Starts conference focus processing, bind listeners and so on...
     *
     * @throws Exception if error occurs during initialization. Instance is
     * considered broken in that case. Its stop method will be called before
     * throwing the exception to perform deinitialization where possible.
     * {@link ConferenceListener}s will be notified that this conference has
     * ended.
     */
    public synchronized void start() throws Exception {
        if (started) {
            return;
        }

        started = true;

        try {
            colibri = jvbXmppConnection.getOperationSet(OperationSetColibriConference.class);
            jingle = protocolProviderHandler.getOperationSet(OperationSetJingle.class);

            // Wraps OperationSetJingle in order to introduce
            // our nasty "lip-sync" hack
            if (Boolean.TRUE.equals(getConfig().isLipSyncEnabled())) {
                logger.info("Lip-sync enabled in " + getRoomName());
                jingle = new LipSyncHack(this, jingle);
            }

            chatOpSet = protocolProviderHandler.getOperationSet(OperationSetMultiUserChat.class);
            meetTools = protocolProviderHandler.getOperationSet(OperationSetJitsiMeetTools.class);
            jibriOpSet = protocolProviderHandler.getOperationSet(OperationSetJibri.class);

            BundleContext osgiCtx = FocusBundleActivator.bundleContext;

            executor = ServiceUtils.getService(osgiCtx, ScheduledExecutorService.class);

            services = ServiceUtils.getService(osgiCtx, JitsiMeetServices.class);

            // Set pre-configured SIP gateway
            // if (config.getPreConfiguredSipGateway() != null)
            // {
            // services.setSipGateway(config.getPreConfiguredSipGateway());
            // }
            if (protocolProviderHandler.isRegistered()) {
                joinTheRoom();
            }

            protocolProviderHandler.addRegistrationListener(this);

            idleTimestamp = System.currentTimeMillis();

            // Register for bridge events
            eventHandlerRegistration = EventUtil.registerEventHandler(osgiCtx,
                    new String[]{BridgeEvent.BRIDGE_UP, BridgeEvent.BRIDGE_DOWN}, this);

            JibriDetector jibriDetector = services.getJibriDetector();
            if (jibriDetector != null && jibriOpSet != null) {
                jibriRecorder = new JibriRecorder(osgiCtx, this, getXmppConnection(), executor, globalConfig);

                jibriOpSet.addJibri(jibriRecorder);
            }

            JibriDetector sipJibriDetector = services.getSipJibriDetector();
            if (sipJibriDetector != null && jibriOpSet != null) {
                jibriSipGateway = new JibriSipGateway(osgiCtx, this, getXmppConnection(),
                        FocusBundleActivator.getSharedThreadPool(), globalConfig);

                jibriOpSet.addJibri(jibriSipGateway);
            }
        } catch (Exception e) {
            try {
                stop();
            } catch (Exception x) {
                logger.warn("An exception was caught while invoking stop()"
                        + " as part of handling another exception that occurred"
                        + " while invoking start(). This is the exception that"
                        + " stop() threw (start()'s exception will be thrown" + " next).", x);
            }

            throw e;
        }
    }

    /**
     * Stops the conference, disposes colibri channels and releases all
     * resources used by the focus.
     */
    synchronized void stop() {
        if (!started) {
            return;
        }

        started = false;

        if (jibriSipGateway != null) {
            try {
                jibriSipGateway.dispose();
                jibriOpSet.removeJibri(jibriSipGateway);
            } catch (Exception e) {
                logger.error("jibriSipGateway.dispose error", e);
            }
            jibriSipGateway = null;
        }

        if (jibriRecorder != null) {
            try {
                jibriRecorder.dispose();
                jibriOpSet.removeJibri(jibriRecorder);
            } catch (Exception e) {
                logger.error("jibriRecorder.dispose error", e);
            }
            jibriRecorder = null;
        }

        if (eventHandlerRegistration != null) {
            eventHandlerRegistration.unregister();
            eventHandlerRegistration = null;
        }

        protocolProviderHandler.removeRegistrationListener(this);

        try {
            disposeConference();
        } catch (Exception e) {
            logger.error("disposeConference error", e);
        }

        try {
            leaveTheRoom();
        } catch (Exception e) {
            logger.error("leaveTheRoom error", e);
        }

        if (jingle != null) {
            try {
                jingle.terminateHandlersSessions(this);
            } catch (Exception e) {
                logger.error("terminateHandlersSessions error", e);
            }
        }

        if (listener != null) {
            listener.conferenceEnded(this);
        }
    }

    /**
     * Returns <tt>true</tt> if focus has joined the conference room.
     */
    public boolean isInTheRoom() {
        return chatRoom != null && chatRoom.isJoined();
    }

    /**
     * Joins the conference room.
     *
     * @throws Exception if we have failed to join the room for any reason
     */
    private void joinTheRoom() throws Exception {
        logger.info("Joining the room: " + roomName);

        chatRoom = (ChatRoomExtension) chatOpSet.findRoom(roomName.toString());
        chatRoom.setConference(this);

        // terminateParticipant
        // if(!chatRoom.getRoomStatus())
        // throw new RoomClosedException("The room is closed to new participant");
        rolesAndPresence = new ChatRoomRoleAndPresence(this, chatRoom);
        rolesAndPresence.init();

        transcriberManager = new TranscriberManager(protocolProviderHandler, chatOpSet.findRoom(roomName.toString()),
                services.getJigasiDetector());
        transcriberManager.init();

        chatRoom.join();

        // Advertise shared Etherpad document
        meetTools.sendPresenceExtension(chatRoom, EtherpadPacketExt.forDocumentName(etherpadName));

        // Advertise the conference creation time in presence
        setConferenceProperty(ConferenceProperties.KEY_CREATED_MS, Long.toString(System.currentTimeMillis()), false);

        // Advertise whether octo is enabled/disabled in presence
        setConferenceProperty(ConferenceProperties.KEY_OCTO_ENABLED, Boolean.toString(config.isOctoEnabled()));

        // Trigger focus joined room event
        EventAdmin eventAdmin = FocusBundleActivator.getEventAdmin();
        if (eventAdmin != null) {
            eventAdmin.postEvent(EventFactory.focusJoinedRoom(roomName, getId()));
        }
    }

    /**
     * Sets a conference property and sends an updated presence stanza in the
     * MUC.
     *
     * @param key the key of the property.
     * @param value the value of the property.
     */
    private void setConferenceProperty(String key, String value) {
        setConferenceProperty(key, value, true);
    }

    /**
     * Sets a conference property and optionally (depending on
     * {@code updatePresence}) sends an updated presence stanza in the MUC.
     *
     * @param key the key of the property.
     * @param value the value of the property.
     * @param updatePresence {@code true} to send an updated presence stanza,
     * and {@code false} to only add the property locally. This is useful to
     * allow updating multiple properties but sending a single presence update.
     */
    private void setConferenceProperty(String key, String value, boolean updatePresence) {
        conferenceProperties.put(key, value);
        if (updatePresence) {
            meetTools.sendPresenceExtension(chatRoom, ConferenceProperties.clone(conferenceProperties));
        }
    }

    /**
     * Leaves the conference room.
     */
    private void leaveTheRoom() {
        if (chatRoom == null) {
            logger.error("Chat room already left!");
            return;
        }

        if (rolesAndPresence != null) {
            rolesAndPresence.dispose();
            rolesAndPresence = null;
        }
        if (transcriberManager != null) {
            transcriberManager.dispose();
            transcriberManager = null;
        }

        if (chatRoom.isJoined()) {
            chatRoom.leave();
        }

        chatRoom.setConference(null);
        chatRoom = null;
    }

    /**
     * Method called by {@link #rolesAndPresence} when new member joins the
     * conference room.
     *
     * @param chatRoomMember the new member that has just joined the room.
     */
    protected void onMemberJoined(final XmppChatMember chatRoomMember) {
        synchronized (participantLock) {
            logger.info("Member " + chatRoomMember.getContactAddress() + " joined.");
            getFocusManager().getStatistics().totalParticipants.incrementAndGet();

            if (!isFocusMember(chatRoomMember)) {
                idleTimestamp = -1;
            }

            // Are we ready to start ?
            if (!checkMinParticipants()) {
                return;
            }

            // Cancel single participant timeout when someone joins ?
            cancelSingleParticipantTimeout();

            // Invite all not invited yet
            if (participants.size() == 0) {
                for (final ChatRoomMember member : chatRoom.getMembers()) {
                    inviteChatMember((XmppChatMember) member, member == chatRoomMember);
                }
            } // Only the one who has just joined
            else {
                inviteChatMember(chatRoomMember, true);
            }
        }
    }

    /**
     * Creates a new {@link ColibriConference} instance for use by this
     * {@link JitsiMeetConferenceImpl}.
     */
    private ColibriConference createNewColibriConference(Jid bridgeJid) throws XmppStringprepException {
        ColibriConferenceImpl colibriConference = (ColibriConferenceImpl) colibri.createNewConference();
        colibriConference.setGID(id);

        colibriConference.setConfig(config);

        Localpart roomName = chatRoom.getRoomJid().getLocalpart();
        colibriConference.setName(roomName);
        colibriConference.setJitsiVideobridge(bridgeJid);

        return colibriConference;
    }

    /**
     * Adds a {@link XmppChatMember} to the conference. Creates the
     * {@link Participant} instance corresponding to the {@link XmppChatMember}.
     * established and videobridge channels being allocated.
     *
     * @param chatRoomMember the chat member to be invited into the conference.
     * @param justJoined whether the chat room member should be invited as a
     * result of just having joined (as opposed to e.g. another participant
     * joining triggering the invite).
     */
    private void inviteChatMember(XmppChatMember chatRoomMember, boolean justJoined) {
        synchronized (participantLock) {
            if (isFocusMember(chatRoomMember)) {
                return;
            }

            // Participant already connected ?
            if (findParticipantForChatMember(chatRoomMember) != null) {
                return;
            }

            final Participant participant = new Participant(this, chatRoomMember, globalConfig.getMaxSourcesPerUser());

            participants.add(participant);
            inviteParticipant(participant, false, hasToStartMuted(participant, justJoined));
        }
    }

    /**
     * Selects a {@link Bridge} to use for a specific {@link Participant}.
     *
     * @param participant the participant for which to select a {@link Bridge}.
     * @return the {@link Bridge}, or {@code null} if one could not be found or
     * the participant already has an associated {@link Bridge}.
     */
    private Bridge selectBridge(Participant participant) {
        if (findBridgeSession(participant) != null) {
            // This should never happen.
            logger.error("The participant already has a bridge:" + participant.getMucJid());
            return null;
        }

        // Select a Bridge for the new participant.
        Bridge bridge = null;
        Jid enforcedVideoBridge = config.getEnforcedVideobridge();
        BridgeSelector bridgeSelector = getServices().getBridgeSelector();

        if (enforcedVideoBridge != null) {
            bridge = bridgeSelector.getBridge(enforcedVideoBridge);
            if (bridge == null) {
                logger.warn("The enforced bridge is not registered with " + "BridgeSelector, will try to use a "
                        + "different one.");
            }
        }

        if (bridge == null) {
            bridge = bridgeSelector.selectBridge(this, participant.getChatMember().getRegion(), config.isOctoEnabled());
        }

        if (bridge == null) {
            // Can not find a bridge to use.
            logger.error("Can not invite participant -- no bridge available.");

            if (chatRoom != null && !chatRoom.containsPresenceExtension(BridgeNotAvailablePacketExt.ELEMENT_NAME,
                    BridgeNotAvailablePacketExt.NAMESPACE)) {
                meetTools.sendPresenceExtension(chatRoom, new BridgeNotAvailablePacketExt());
            }
            return null;

        }

        return bridge;
    }

    /**
     * Get a stream of those bridges which are operational. Caller should be
     * synchronized on bridges.
     */
    private Stream<BridgeSession> operationalBridges() {
        return bridges.stream().filter(session -> !session.hasFailed && session.bridge.isOperational());
    }

    /**
     * Invites a {@link Participant} to the conference. Selects the
     * {@link BridgeSession} to use and starts a new
     * {@link ParticipantChannelAllocator} to allocate COLIBRI channels and
     * initiate a Jingle session with the {@link Participant}.
     *
     * @param participant the participant to invite.
     * @param reInvite whether the participant is to be re-invited or invited
     * for the first time.
     * @param startMuted an array of size 2, which will determine whether the
     * offer sent to the participant should indicate that the participant should
     * start audio muted (depending on the value of the element at index 0) and
     * video muted (depending on the value of the element at index 1).
     */
    private void inviteParticipant(Participant participant, boolean reInvite, boolean[] startMuted) {
        BridgeSession bridgeSession;
        synchronized (bridges) {
            Bridge bridge = selectBridge(participant);
            if (bridge == null) {
                return;
            }

            bridgeSession = findBridgeSession(bridge);
            if (bridgeSession == null) {
                // The selected bridge is not yet used for this conference,
                // so initialize a new BridgeSession
                try {
                    bridgeSession = new BridgeSession(bridge);
                } catch (XmppStringprepException e) {
                    logger.error("Invalid room name", e);
                    return;
                }

                bridges.add(bridgeSession);
                setConferenceProperty(ConferenceProperties.KEY_BRIDGE_COUNT, Integer.toString(bridges.size()));

                if (operationalBridges().count() >= 2) {
                    // Octo needs to be enabled (by inviting an Octo
                    // participant for each bridge), or if it is already enabled
                    // the list of relays for each bridge may need to be
                    // updated.
                    updateOctoRelays();
                }
            }

            bridgeSession.participants.add(participant);
            participant.setBridgeSession(bridgeSession);
            logger.info(
                    "Added participant jid= " + participant.getMucJid() + ", bridge=" + bridgeSession.bridge.getJid());
            logRegions();

            // Colibri channel allocation and jingle invitation take time, so
            // schedule them on a separate thread.
            ParticipantChannelAllocator channelAllocator = new ParticipantChannelAllocator(this, bridgeSession,
                    participant, startMuted, reInvite);

            participant.setChannelAllocator(channelAllocator);
            FocusBundleActivator.getSharedThreadPool().submit(channelAllocator);
        }
    }

    /**
     * Re-calculates the Octo relays for bridges in the conference, and updates
     * each bridge session.
     */
    private void updateOctoRelays() {
        synchronized (bridges) {
            List<String> allRelays = getAllRelays(null);

            if (logger.isDebugEnabled()) {
                logger.debug("Updating Octo relays for " + this + ". All relays:" + allRelays);
            }

            operationalBridges().forEach(bridge -> bridge.setRelays(allRelays));
        }
    }

    /**
     * @param exclude a relay id to exclude from the result.
     * @return the set of all Octo relays of bridges in the conference, except
     * for {@code exclude}.
     */
    private List<String> getAllRelays(String exclude) {
        synchronized (bridges) {
            return operationalBridges().map(bridge -> bridge.bridge.getRelayId()).filter(Objects::nonNull)
                    .filter(bridge -> !bridge.equals(exclude)).collect(Collectors.toList());
        }
    }

    /**
     * Logs the regions of all bridges and participants of the conference.
     */
    private void logRegions() {

        StringBuilder sb = new StringBuilder(
                "Region info, conference=" + getId() + " octo_enabled= " + config.isOctoEnabled() + ": [");
        synchronized (bridges) {
            for (BridgeSession bridgeSession : bridges) {
                sb.append("[").append(bridgeSession.bridge.getRegion());
                for (Participant p : bridgeSession.participants) {
                    sb.append(", ").append(p.getChatMember().getRegion());
                }
                sb.append("]");
            }
        }

        sb.append("]");
        logger.info(sb.toString());
    }

    /**
     * @return the {@link BridgeSession} instance which is used for a specific
     * {@link Participant}, or {@code null} if there is no bridge for the
     * participant.
     * @param participant the {@link Participant} for which to find the bridge.
     */
    private BridgeSession findBridgeSession(Participant participant) {
        synchronized (bridges) {
            for (BridgeSession bridgeSession : bridges) {
                if (bridgeSession.participants.contains(participant)) {
                    return bridgeSession;
                }
            }
        }
        return null;
    }

    /**
     * @return the {@link BridgeSession} instance used by this
     * {@link JitsiMeetConferenceImpl} which corresponds to a particular
     * jitsi-videobridge instance represented by a {@link Bridge}, or
     * {@code null} if the {@link Bridge} is not currently used in this
     * conference.
     * @param state the {@link BridgeSession} which represents a particular
     * jitsi-videobridge instance for which to return the {@link BridgeSession}.
     */
    private BridgeSession findBridgeSession(Bridge state) {
        synchronized (bridges) {
            for (BridgeSession bridgeSession : bridges) {
                if (bridgeSession.bridge.equals(state)) {
                    return bridgeSession;
                }
            }
        }

        return null;
    }

    /**
     * @return the {@link BridgeSession} instance used by this
     * {@link JitsiMeetConferenceImpl} which corresponds to a particular
     * jitsi-videobridge instance represented by its JID, or {@code null} if the
     * {@link Bridge} is not currently used in this conference.
     * @param jid the XMPP JID which represents a particular jitsi-videobridge
     * instance for which to return the {@link BridgeSession}.
     */
    private BridgeSession findBridgeSession(Jid jid) {
        synchronized (bridges) {
            for (BridgeSession bridgeSession : bridges) {
                if (bridgeSession.bridge.getJid().equals(jid)) {
                    return bridgeSession;
                }
            }
        }

        return null;
    }

    /**
     * Returns array of boolean values that indicates whether the last
     * participant have to start video or audio muted.
     *
     * @param participant the participant
     * @param justJoined indicates whether the participant joined the room now
     * or he was in the room before.
     * @return array of boolean values that indicates whether the last
     * participant have to start video or audio muted. The first element should
     * be associated with the audio and the second with video.
     */
    private boolean[] hasToStartMuted(Participant participant, boolean justJoined) {
        final boolean[] startMuted = new boolean[]{false, false};
        if (this.startMuted != null && this.startMuted[0] && justJoined) {
            startMuted[0] = true;
        }

        if (this.startMuted != null && this.startMuted[1] && justJoined) {
            startMuted[1] = true;
        }

        if (startMuted[0] && startMuted[1]) {
            return startMuted;
        }

        int participantNumber = participant != null ? participant.getChatMember().getJoinOrderNumber()
                : participants.size();

        if (!startMuted[0]) {
            Integer startAudioMuted = config.getStartAudioMuted();
            if (startAudioMuted != null) {
                startMuted[0] = (participantNumber > startAudioMuted);
            }
        }

        if (!startMuted[1]) {
            Integer startVideoMuted = config.getStartVideoMuted();
            if (startVideoMuted != null) {
                startMuted[1] = (participantNumber > startVideoMuted);
            }
        }

        return startMuted;
    }

    /**
     * Returns {@code true} if there are at least two non-focus participants in
     * the room.
     *
     * @return <tt>true</tt> if we have at least two non-focus participants.
     */
    private boolean checkMinParticipants() {
        int minParticipants = config.getMinParticipants();
        // minParticipants + 1 focus
        if (chatRoom.getMembersCount() >= (minParticipants + 1)) {
            return true;
        }

        int realCount = 0;
        for (ChatRoomMember member : chatRoom.getMembers()) {
            if (!isFocusMember((XmppChatMember) member)) {
                realCount++;
            }
        }

        return realCount >= minParticipants;
    }

    /**
     * Check if given chat member is a focus.
     *
     * @param member the member to check.
     *
     * @return <tt>true</tt> if given {@link ChatRoomMember} is a focus
     * participant.
     */
    boolean isFocusMember(XmppChatMember member) {
        return member.getName().equals(focusUserName.toString());
    }

    /**
     * Checks if given MUC jid belongs to the focus user.
     *
     * @param mucJid the full MUC address to check.
     *
     * @return <tt>true</tt> if given <tt>jid</tt> belongs to the focus
     * participant or <tt>false</tt> otherwise.
     */
    @Override
    public boolean isFocusMember(Jid mucJid) {
        ChatRoomExtension chatRoom = this.chatRoom;
        return mucJid != null && chatRoom != null && mucJid.equals(chatRoom.getLocalOccupantJid());
    }

    /**
     * Check if given member represent SIP gateway participant.
     *
     * @param member the chat member to be checked.
     *
     * @return <tt>true</tt> if given <tt>member</tt> represents the SIP gateway
     */
    // FIXME remove once Jigasi is a "robot"
    boolean isSipGateway(ChatRoomMember member) {
        Participant participant = findParticipantForChatMember(member);

        return participant != null && participant.isSipGateway();
    }

    /**
     * Destroys the MUC room and deletes the conference which results in all
     * participant being removed from the XMPP chat room.
     *
     * @param reason the reason text that will be advertised to all participants
     * upon exit.
     */
    public void destroy(String reason) {
        if (chatRoom == null) {
            logger.error("Unable to destroy conference MUC room: " + roomName);
            return;
        }

        chatRoom.destroy(reason, null);
    }

    /**
     * Disposes of this conference. Expires all allocated COLIBRI conferences.
     *
     * Does not terminate jingle sessions with its participants (why???).
     *
     */
    private void disposeConference() {
        // If the conference is being disposed the timeout is not needed
        // anymore
        cancelSingleParticipantTimeout();

        synchronized (bridges) {
            for (BridgeSession bridgeSession : bridges) {
                // No need to expire channels, just expire the whole colibri
                // conference.
                // bridgeSession.terminateAll();
                bridgeSession.dispose();
            }
            bridges.clear();
            setConferenceProperty(ConferenceProperties.KEY_BRIDGE_COUNT, "0");
        }

        // TODO: what about removing the participants and ending their jingle
        // session?
    }

    /**
     * Method called by {@link #rolesAndPresence} when one of the members has
     * been kicked out of the conference room.
     *
     * @param chatRoomMember kicked chat room member.
     */
    protected void onMemberKicked(ChatRoomMember chatRoomMember) {
        synchronized (participantLock) {
            logger.info("Member " + chatRoomMember.getContactAddress() + " kicked !!!");

            onMemberLeft(chatRoomMember);
        }
    }

    /**
     * Method called by {@link #rolesAndPresence} when someone leave conference
     * chat room.
     *
     * @param chatRoomMember the member that has left the room.
     */
    protected void onMemberLeft(ChatRoomMember chatRoomMember) {
        
        synchronized (participantLock) {
            
            String contactAddress = chatRoomMember.getContactAddress();
            logger.info("Member " + contactAddress + " is leaving");
            
            if (chatRoomMember.getRole() != null) {
                
                logger.info("Member has a role of " + chatRoomMember.getRole().getRoleName() + " - " + chatRoomMember.getRole().getRoleIndex());
                if (chatRoomMember.getRole().compareTo(ChatRoomMemberRole.MODERATOR) >= 0) { // (OWNER, ADMINISTRATOR, MODERATOR)

                }
            }
            else {
                logger.info("Member has no role");
            }
            
            Participant leftParticipant = findParticipantForChatMember(chatRoomMember);
            if (leftParticipant != null) {
                
                terminateParticipant(leftParticipant, Reason.GONE, null);
            } else {
                logger.warn("Participant not found for " + contactAddress + " terminated already or never started ?");
            }

            if (participants.size() >= 1) {
                
                if (participants.size() == 1) {
                    rescheduleSingleParticipantTimeout();
                }
            } else if (participants.isEmpty()) {
                
                stop();
            }
        }
    }
   
                
    private void terminateParticipant(Participant participant, Reason reason, String message) {
        
        BridgeSession bridgeSession;
        synchronized (participantLock) {
            Jid contactAddress = participant.getMucJid();
            if (participant.isSessionEstablished()) {
                
                JingleSession jingleSession = participant.getJingleSession();
                logger.info("Terminating: " + contactAddress);
                
                if(participants != null) {
                    
                    if(participants.size() == 1) {
                        // The last participant is being terminated
                        
                        if(chatRoom != null && chatRoom.getRoomJid() != null) {
                            
                            String room_name = chatRoom.getRoomJid().toString();
                            //String room_name = fromJid.toString();

                            if(room_name.contains("@")) {
                                room_name = room_name.substring(0, room_name.indexOf("@"));
                            }

                            String cmd = "/usr/share/jitsi-meet/stream.sh " + room_name + " 0";
                            logger.info("All participant left running cmd " + cmd);
                            runScriptCmd(cmd);
                        }
                    }
                    else if(participants.size() > 1) {
                        // A participant is being terminated, we need to check if he's the roomManager
                    
                        logger.info("There is more than one participant left before terminating");

                        if(chatRoom != null && chatRoom.getRoomJid() != null) {

                            String shortRoomName = chatRoom.getRoomJid().toString();
                            if(shortRoomName.contains("@")) {
                                shortRoomName = shortRoomName.substring(0, shortRoomName.indexOf("@"));
                            }
                            if(contactAddress != null && contactAddress.toString() != null
                                    && chatRoom != null) { //&& chatRoom.getVeazzyRoomManagerId() != null

                                logger.debug("Terminating" + " checking isVeazzyRoomManager for " 
                                        + shortRoomName + " - " + contactAddress.toString());  

                                String participantShortId = contactAddress.toString();

                                if(participantShortId.contains("/")) {
                                    int ind = participantShortId.lastIndexOf("/");
                                    ind += 1;
                                    participantShortId = participantShortId.substring(ind);
                                }

                                String roomManagerId = getVeazzyRoomManagerId();
                                if(participantShortId != null && roomManagerId != null) { 

                                    logger.debug("Check isVeazzyRoomManager() for participantShortId " 
                                            + participantShortId + " (" + contactAddress.toString() + ")"
                                            + " - Manager " + roomManagerId);

                                    if(roomManagerId.equals(participantShortId)) {
                                        String cmd = "/usr/share/jitsi-meet/stream.sh " + shortRoomName + " 1";
                                        logger.info("Participant was terminate running cmd " + cmd);
                                        runScriptCmd(cmd);
                                    }
                                }
                            }
                            else if(chatRoom == null) {
                                logger.debug("Terminating" + " but contactAddress is null");  
                            }
                        }
                        else if(chatRoom == null) {
                            logger.debug("Terminating" + " but chatRoom is null");  
                        }
                    }
                }
                else if(participants == null) {
                    logger.debug("Terminating" + " but participants is null");  
                }
                
                jingle.terminateSession(jingleSession, reason, message);

                removeSources(jingleSession, participant.getSourcesCopy(), participant.getSourceGroupsCopy(),
                        false /* no JVB update - will expire */);
            }

            bridgeSession = participant.terminateBridgeSession();

            boolean removed = participants.remove(participant);
            logger.info("Removed participant: " + removed + ", " + contactAddress);
        }

        if (bridgeSession != null) {
            maybeExpireBridgeSession(bridgeSession);
        }
    }

    /**
     * Expires the session with a particular bridge if it has no real (non-octo)
     * participants left.
     *
     * @param bridgeSession the bridge session to expire.
     */
    private void maybeExpireBridgeSession(BridgeSession bridgeSession) {
        synchronized (bridges) {
            if (bridgeSession.participants.isEmpty()) {
                bridgeSession.terminateAll();
                bridges.remove(bridgeSession);
                setConferenceProperty(ConferenceProperties.KEY_BRIDGE_COUNT, Integer.toString(bridges.size()));

                updateOctoRelays();
            }
        }
    }

    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt) {
        logger.info("Reg state changed: " + evt);

        if (RegistrationState.REGISTERED.equals(evt.getNewState())) {

            if (chatRoom == null) {
                try {
                    joinTheRoom();
                } catch (RoomClosedException e) {
                    logger.error("Failed to join the room: " + roomName + ", room is closed.", e);

                    stop();
                } catch (Exception e) {
                    logger.error("Failed to join the room: " + roomName, e);

                    stop();
                }
            }
        } else if (RegistrationState.UNREGISTERED.equals(evt.getNewState())) {
            stop();
        }
    }

    private Participant findParticipantForJingleSession(JingleSession jingleSession) {
        for (Participant participant : participants) {
            if (participant.getChatMember().getOccupantJid().equals(jingleSession.getAddress())) {
                return participant;
            }
        }
        return null;
    }

    private Participant findParticipantForChatMember(ChatRoomMember chatMember) {
        for (Participant participant : participants) {
            if (participant.getChatMember().equals(chatMember)) {
                return participant;
            }
        }
        return null;
    }

    public Participant findParticipantForRoomJidForRoomStatusRequest(Jid roomJid) {
        logger.info("findParticipantForRoomJidForRoomStatusRequest()" + " looking for Jid - " + roomJid + ".");

        for (Participant participant : participants) {

            logger.info("listOfCurrentOccupantJid() " + participant.getChatMember().getOccupantJid() + ".");

            if (participant.getChatMember().getOccupantJid().equals(roomJid)) {
                return participant;
            }
        }
        return null;
    }

    public Participant findParticipantForRoomJid(Jid roomJid) {
        for (Participant participant : participants) {
            if (participant.getChatMember().getOccupantJid().equals(roomJid)) {
                return participant;
            }
        }
        return null;
    }

    @Override
    public ChatRoomMemberRole getRoleForMucJid(Jid mucJid) {
        if (chatRoom == null) {
            return null;
        }

        for (ChatRoomMember member : chatRoom.getMembers()) {
            if (member.getContactAddress().equals(mucJid.toString())) {
                return member.getRole();
            }
        }
        return null;
    }

    /**
     * Callback called when 'session-accept' is received from invited
     * participant.
     *
     * {@inheritDoc}
     */
    @Override
    public XMPPError onSessionAccept(JingleSession jingleSession, List<ContentPacketExtension> answer) {
        Participant participant = findParticipantForJingleSession(jingleSession);
        Jid participantJid = jingleSession.getAddress();

        if (participant == null) {
            String errorMsg = "No participant found for: " + participantJid;
            logger.warn(errorMsg);
            return XMPPError.from(XMPPError.Condition.item_not_found, errorMsg).build();
        }

        if (participant.getJingleSession() != null) {
            // FIXME: we should reject it ?
            logger.error("Reassigning jingle session for participant: " + participantJid);
        }

        // XXX We will be acting on the received session-accept bellow.
        // Unfortunately, we may have not received an acknowledgment of our
        // session-initiate yet and whatever we do bellow will be torn down when
        // the acknowledgement timeout occurs later on. Since we will have
        // acted on the session-accept by the time the acknowledgement timeout
        // occurs, we may as well ignore the timeout.
        jingleSession.setAccepted(true);

        participant.setJingleSession(jingleSession);

        // Extract and store various session information in the Participant
        participant.setRTPDescription(answer);
        participant.addTransportFromJingle(answer);

        MediaSourceMap sourcesAdvertised = MediaSourceMap.getSourcesFromContent(answer);
        MediaSourceGroupMap sourceGroupsAdvertised = MediaSourceGroupMap.getSourceGroupsForContents(answer);
        if (sourcesAdvertised.isEmpty() && globalConfig.injectSsrcForRecvOnlyEndpoints) {
            // We inject an SSRC in order to insure that the participant has
            // at least one SSRC advertised. Otherwise, non-local bridges in the
            // conference will not be aware of the participant. We intentionally
            // use a negative value, because this is an invalid SSRC and will
            // not be actually used on the wire.
            SourcePacketExtension sourcePacketExtension = new SourcePacketExtension();
            long ssrc = RANDOM.nextInt() & 0xffff_ffffl;
            logger.info(participant + " did not advertise any SSRCs. Injecting " + ssrc);
            sourcePacketExtension.setSSRC(ssrc);
            sourcesAdvertised.addSource(MediaType.AUDIO.toString(), sourcePacketExtension);
        }
        MediaSourceMap sourcesAdded;
        MediaSourceGroupMap sourceGroupsAdded;
        try {
            Object[] sourcesAndGroupsAdded = tryAddSourcesToParticipant(participant, sourcesAdvertised,
                    sourceGroupsAdvertised);
            sourcesAdded = (MediaSourceMap) sourcesAndGroupsAdded[0];
            sourceGroupsAdded = (MediaSourceGroupMap) sourcesAndGroupsAdded[1];
        } catch (InvalidSSRCsException e) {
            logger.error("Error processing session accept from: " + participantJid + ": " + e.getMessage());

            return XMPPError.from(XMPPError.Condition.bad_request, e.getMessage()).build();
        }

        logger.info(
                "Received session-accept from " + participant.getMucJid() + " with accepted sources:" + sourcesAdded);

        // Update channel info - we may miss update during conference restart,
        // but the state will be synced up after channels are allocated for this
        // participant on the new bridge
        synchronized (bridges) {
            BridgeSession participantBridge = findBridgeSession(participant);
            if (participantBridge != null) {
                participantBridge.updateColibriChannels(participant);
            } else {
                logger.warn("No bridge found for a participant: " + participant);
                // TODO: how do we handle this? Re-invite?
            }

            // If we accepted any new sources from the participant, update
            // the state of all remote bridges.
            if ((!sourcesAdded.isEmpty() || !sourceGroupsAdded.isEmpty()) && participantBridge != null) {
                propagateNewSourcesToOcto(participantBridge, sourcesAdded, sourceGroupsAdded);
            }
        }

        // Loop over current participant and send 'source-add' notification
        propagateNewSources(participant, sourcesAdded.copyDeep(), sourceGroupsAdded.copy());

        // Notify the participant itself since it is now stable
        if (participant.hasSourcesToAdd()) {
            jingle.sendAddSourceIQ(participant.getSourcesToAdd(), participant.getSourceGroupsToAdd(), jingleSession);

            participant.clearSourcesToAdd();
        }
        if (participant.hasSourcesToRemove()) {
            jingle.sendRemoveSourceIQ(participant.getSourcesToRemove(), participant.getSourceGroupsToRemove(),
                    jingleSession);

            participant.clearSourcesToRemove();
        }

        return null;
    }

    /**
     * Will re-allocate channels on the bridge for participant who signals ICE
     * state 'failed'. New transport is sent in the 'transport-info' message
     * similar to the conference migration scenario.
     *
     * {@inheritDoc}
     */
    @Override
    public XMPPError onSessionInfo(JingleSession session, JingleIQ iq) {
        Jid address = session.getAddress();
        Participant participant = findParticipantForJingleSession(session);

        // FIXME: (duplicate) there's very similar logic in onSessionAccept
        if (participant == null) {
            String errorMsg = "No session for " + address;

            logger.warn("onSessionInfo: " + errorMsg);

            return XMPPError.from(XMPPError.Condition.item_not_found, errorMsg).build();
        }

        IceStatePacketExtension iceStatePE = iq.getExtension(IceStatePacketExtension.ELEMENT_NAME,
                IceStatePacketExtension.NAMESPACE);
        String iceState = iceStatePE != null ? iceStatePE.getText() : null;

        if (!"failed".equalsIgnoreCase(iceState)) {
            logger.info(String.format("Ignored 'ice-state' update for participant: %s state: %s", address, iceState));

            return null;
        }

        BridgeSessionPacketExtension bsPE = iq.getExtension(BridgeSessionPacketExtension.ELEMENT_NAME,
                BridgeSessionPacketExtension.NAMESPACE);
        String bridgeSessionId = bsPE != null ? bsPE.getId() : null;
        BridgeSession bridgeSession = findBridgeSession(participant);

        if (bridgeSession != null) {
            logger.info(String.format("Received ICE failed notification from %s, session: %s", address, bridgeSession));
            reInviteParticipant(participant);
        } else {
            logger.info(String.format(
                    "Ignored ICE failed notification for invalid session," + " participant: %s, bridge session ID: %s",
                    address, bridgeSessionId));
        }

        return null;
    }

    /**
     * Advertises new sources across all conference participants by using
     * 'source-add' Jingle notification.
     *
     * @param sourceOwner the <tt>Participant</tt> who owns the sources.
     * @param sources the <tt>MediaSourceMap</tt> with the sources to advertise.
     * @param sourceGroups the <tt>MediaSourceGroupMap</tt> with source groups
     * to advertise.
     */
    private void propagateNewSources(Participant sourceOwner, MediaSourceMap sources,
            MediaSourceGroupMap sourceGroups) {
        participants.stream().filter(otherParticipant -> otherParticipant != sourceOwner).forEach(participant -> {
            if (!participant.isSessionEstablished()) {
                logger.warn("No jingle session yet for " + participant.getMucJid());

                participant.scheduleSourcesToAdd(sources);
                participant.scheduleSourceGroupsToAdd(sourceGroups);

                return;
            }

            jingle.sendAddSourceIQ(sources, sourceGroups, participant.getJingleSession());
        });
    }

    /**
     * Adds the specified sources and source groups to the Octo participants of
     * all bridges except for {@code exclude}.
     *
     * @param exclude the bridge to which sources will not be added (i.e. the
     * bridge to which the participant whose sources we are adding is
     * connected).
     * @param sources the sources to add.
     * @param sourceGroups the source groups to add.
     */
    private void propagateNewSourcesToOcto(BridgeSession exclude, MediaSourceMap sources,
            MediaSourceGroupMap sourceGroups) {
        synchronized (bridges) {
            operationalBridges().filter(bridge -> !bridge.equals(exclude))
                    .forEach(bridge -> bridge.addSourcesToOcto(sources, sourceGroups));
        }
    }

    /**
     * Callback called when we receive 'transport-info' from conference
     * participant. The info is forwarded to the videobridge at this point.
     *
     * {@inheritDoc}
     */
    @Override
    public void onTransportInfo(JingleSession session, List<ContentPacketExtension> contentList) {
        Participant participant = findParticipantForJingleSession(session);
        if (participant == null) {
            logger.warn("Failed to process transport-info," + " no session for: " + session.getAddress());
            return;
        }

        // Participant will figure out bundle or non-bundle transport
        // based on its hasBundleSupport() value
        participant.addTransportFromJingle(contentList);

        BridgeSession bridgeSession = findBridgeSession(participant);
        // We can hit null here during conference restart, but the state will be
        // synced up later when the client sends 'transport-accept'
        if (bridgeSession == null) {
            logger.warn("Skipped transport-info processing - no conference");
            return;
        }

        if (participant.hasBundleSupport()) {
            bridgeSession.colibriConference.updateBundleTransportInfo(participant.getBundleTransport(),
                    participant.getEndpointId());
        } else {
            bridgeSession.colibriConference.updateTransportInfo(participant.getTransportMap(),
                    participant.getColibriChannelsInfo());
        }
    }

    /**
     * 'transport-accept' message is received by the focus after it has sent
     * 'transport-replace' which is supposed to move the conference to another
     * bridge. It means that the client has accepted new transport.
     *
     * {@inheritDoc}
     */
    @Override
    public XMPPError onTransportAccept(JingleSession jingleSession, List<ContentPacketExtension> contents) {
        jingleSession.setAccepted(true);

        logger.info("Got transport-accept from: " + jingleSession.getAddress());

        // We basically do the same processing as with transport-info by just
        // forwarding transport/rtp information to the bridge
        onTransportInfo(jingleSession, contents);

        return null;
    }

    /**
     * Message sent by the client when for any reason it's unable to handle
     * 'transport-replace' message.
     *
     * {@inheritDoc}
     */
    @Override
    public void onTransportReject(JingleSession jingleSession, JingleIQ reply) {
        Participant p = findParticipantForJingleSession(jingleSession);
        if (p == null) {
            logger.warn("No participant for " + jingleSession);
            return;
        }

        // We could expire channels immediately here, but we're leaving them to
        // auto expire on the bridge or we're going to do that when user leaves
        // the MUC anyway
        logger.error("Participant has rejected our transport offer: " + p.getMucJid() + ", response: " + reply.toXML());
    }

    /**
     * Callback called when we receive 'source-add' notification from conference
     * participant. New sources received are advertised to active participants.
     * If some participant does not have Jingle session established yet then
     * those sources are scheduled for future update.
     *
     * {@inheritDoc}
     */
    @Override
    public XMPPError onAddSource(JingleSession jingleSession, List<ContentPacketExtension> contents) {
        Jid address = jingleSession.getAddress();
        Participant participant = findParticipantForJingleSession(jingleSession);
        if (participant == null) {
            String errorMsg = "Add-source: no state for " + address;
            logger.warn(errorMsg);
            return XMPPError.from(XMPPError.Condition.item_not_found, errorMsg).build();
        }

        Object[] added;
        try {
            added = tryAddSourcesToParticipant(participant, contents);
        } catch (InvalidSSRCsException e) {
            logger.error("Error adding SSRCs from: " + address + ": " + e.getMessage());
            return XMPPError.from(XMPPError.Condition.bad_request, e.getMessage()).build();
        }

        MediaSourceMap sourcesToAdd = (MediaSourceMap) added[0];
        MediaSourceGroupMap sourceGroupsToAdd = (MediaSourceGroupMap) added[1];

        if (sourcesToAdd.isEmpty() && sourceGroupsToAdd.isEmpty()) {
            logger.warn("Not sending source-add, notification would be empty");
            return null;
        }

        // Updates source groups on the bridge
        // We may miss the notification, but the state will be synced up
        // after conference has been relocated to the new bridge
        synchronized (bridges) {
            BridgeSession bridgeSession = findBridgeSession(participant);
            if (bridgeSession != null) {
                bridgeSession.colibriConference.updateSourcesInfo(participant.getSourcesCopy(),
                        participant.getSourceGroupsCopy(), participant.getColibriChannelsInfo());

                propagateNewSourcesToOcto(bridgeSession, sourcesToAdd, sourceGroupsToAdd);
            } else {
                logger.warn("No bridge for a participant.");
                // TODO: how do we handle this? Re-invite?
            }
        }

        propagateNewSources(participant, sourcesToAdd, sourceGroupsToAdd);

        return null;
    }

    /**
     * Callback called when we receive 'source-remove' notification from
     * conference participant. New sources received are advertised to active
     * participants. If some participant does not have Jingle session
     * established yet then those sources are scheduled for future update.
     *
     * {@inheritDoc}
     */
    @Override
    public XMPPError onRemoveSource(JingleSession sourceJingleSession, List<ContentPacketExtension> contents) {
        MediaSourceMap sourcesToRemove = MediaSourceMap.getSourcesFromContent(contents);

        MediaSourceGroupMap sourceGroupsToRemove = MediaSourceGroupMap.getSourceGroupsForContents(contents);

        return removeSources(sourceJingleSession, sourcesToRemove, sourceGroupsToRemove, true);
    }

    /**
     * Removes sources from the conference and notifies other participants.
     *
     * @param sourceJingleSession source Jingle session from which sources are
     * being removed.
     * @param sourcesToRemove the {@link MediaSourceMap} of sources to be
     * removed from the conference.
     * @param updateChannels tells whether or not sources update request should
     * be sent to the bridge.
     */
    private XMPPError removeSources(JingleSession sourceJingleSession, MediaSourceMap sourcesToRemove,
            MediaSourceGroupMap sourceGroupsToRemove, boolean updateChannels) {
        Participant participant = findParticipantForJingleSession(sourceJingleSession);
        Jid participantJid = sourceJingleSession.getAddress();
        if (participant == null) {
            logger.warn("Remove-source: no session for " + participantJid);

            return null;
        }

        final MediaSourceMap conferenceSources = getAllSources();
        final MediaSourceGroupMap conferenceSourceGroups = getAllSourceGroups();

        SSRCValidator validator = new SSRCValidator(participant.getEndpointId(), conferenceSources,
                conferenceSourceGroups, globalConfig.getMaxSourcesPerUser(), this.logger);

        Object[] removed;

        try {
            removed = validator.tryRemoveSourcesAndGroups(sourcesToRemove, sourceGroupsToRemove);
        } catch (InvalidSSRCsException e) {
            logger.error("Error removing SSRCs from: " + participantJid + ": " + e.getMessage());
            return XMPPError.from(XMPPError.Condition.bad_request, e.getMessage()).build();
        }

        // Only sources owned by this participant end up in "removed" set
        final MediaSourceMap removedSources = (MediaSourceMap) removed[0];
        final MediaSourceGroupMap removedGroups = (MediaSourceGroupMap) removed[1];

        if (removedSources.isEmpty() && removedGroups.isEmpty()) {
            logger.warn("No sources or groups to be removed from: " + participantJid);
            return null;
        }

        participant.removeSources(removedSources);
        participant.removeSourceGroups(removedGroups);

        // We remove all ssrc params from SourcePacketExtension as we want
        // the client to simply remove all lines corresponding to given SSRC and
        // not care about parameter's values we send.
        // Some params might get out of sync for various reasons like for
        // example Chrome coming up with 'default' value for missing 'mslabel'
        // or when we'll be doing lip-sync stream merge
        SSRCSignaling.deleteSSRCParams(removedSources);

        // Updates source Groups on the bridge
        BridgeSession bridgeSession = findBridgeSession(participant);
        // We may hit null here during conference restart, but that's not
        // important since the bridge for this instance will not be used
        // anymore and state is synced up soon after channels are allocated
        if (updateChannels && bridgeSession != null) {
            bridgeSession.colibriConference.updateSourcesInfo(participant.getSourcesCopy(),
                    participant.getSourceGroupsCopy(), participant.getColibriChannelsInfo());
        }

        synchronized (bridges) {
            operationalBridges().filter(bridge -> !bridge.equals(bridgeSession))
                    .forEach(bridge -> bridge.removeSourcesFromOcto(removedSources, removedGroups));
        }

        logger.info("Removing " + participantJid + " sources " + removedSources);

        participants.stream().filter(otherParticipant -> otherParticipant != participant).forEach(otherParticipant -> {
            if (otherParticipant.isSessionEstablished()) {
                jingle.sendRemoveSourceIQ(removedSources, removedGroups, otherParticipant.getJingleSession());
            } else {
                logger.warn("Remove source: no jingle session for " + participantJid);

                otherParticipant.scheduleSourcesToRemove(removedSources);

                otherParticipant.scheduleSourceGroupsToRemove(removedGroups);
            }
        });

        return null;
    }

    /**
     * Adds the sources and groups described by the given list of Jingle
     * {@link ContentPacketExtension} to the given participant.
     *
     * @param participant - The {@link Participant} instance to which sources
     * and groups will be added.
     * @param contents - The list of Jingle 'content' packet extensions which
     * describe media sources and groups.
     *
     * @return See returns description of
     * {@link SSRCValidator#tryAddSourcesAndGroups(MediaSourceMap, MediaSourceGroupMap)}.
     * @throws InvalidSSRCsException See throws description of
     * {@link SSRCValidator#tryAddSourcesAndGroups(MediaSourceMap, MediaSourceGroupMap)}.
     */
    private Object[] tryAddSourcesToParticipant(Participant participant, List<ContentPacketExtension> contents)
            throws InvalidSSRCsException {
        return tryAddSourcesToParticipant(participant, MediaSourceMap.getSourcesFromContent(contents),
                MediaSourceGroupMap.getSourceGroupsForContents(contents));
    }

    /**
     * Adds the given sources and groups to the given participant.
     *
     * @param participant - The {@link Participant} instance to which sources
     * and groups will be added.
     * @param newSources - The new media sources to add.
     * @param newGroups - The new media group sources to add.
     *
     * @return See returns description of
     * {@link SSRCValidator#tryAddSourcesAndGroups(MediaSourceMap, MediaSourceGroupMap)}.
     * @throws InvalidSSRCsException See throws description of
     * {@link SSRCValidator#tryAddSourcesAndGroups(MediaSourceMap, MediaSourceGroupMap)}.
     */
    private Object[] tryAddSourcesToParticipant(Participant participant, MediaSourceMap newSources,
            MediaSourceGroupMap newGroups) throws InvalidSSRCsException {
        MediaSourceMap conferenceSources = getAllSources();
        MediaSourceGroupMap conferenceSourceGroups = getAllSourceGroups();

        SSRCValidator validator = new SSRCValidator(participant.getEndpointId(), conferenceSources,
                conferenceSourceGroups, globalConfig.getMaxSourcesPerUser(), this.logger);

        // Claim the new sources by injecting owner tag into packet extensions,
        // so that the validator will be able to tell who owns which sources.
        participant.claimSources(newSources);

        Object[] added = validator.tryAddSourcesAndGroups(newSources, newGroups);

        participant.addSourcesAndGroups((MediaSourceMap) added[0], (MediaSourceGroupMap) added[1]);

        return added;
    }

    /**
     * Gathers the list of all sources that exist in the current conference
     * state.
     *
     * @return <tt>MediaSourceMap</tt> of all sources of given media type that
     * exist in the current conference state.
     */
    private MediaSourceMap getAllSources() {
        return getAllSources(Collections.emptyList());
    }

    /**
     * Gathers the list of all sources that exist in the current conference
     * state.
     *
     * @param except optional <tt>Participant</tt> instance whose sources will
     * be excluded from the list
     *
     * @return <tt>MediaSourceMap</tt> of all sources of given media type that
     * exist in the current conference state.
     */
    MediaSourceMap getAllSources(Participant except) {
        return getAllSources(Collections.singletonList(except));
    }

    /**
     * Gathers the list of all sources that exist in the current conference
     * state.
     *
     * @param except optional <tt>Participant</tt> instance whose sources will
     * be excluded from the list
     *
     * @return <tt>MediaSourceMap</tt> of all sources of given media type that
     * exist in the current conference state.
     */
    private MediaSourceMap getAllSources(List<Participant> except) {
        MediaSourceMap mediaSources = new MediaSourceMap();

        for (Participant participant : participants) {
            // We want to exclude this one
            if (except.contains(participant)) {
                continue;
            }

            mediaSources.add(participant.getSourcesCopy());
        }

        return mediaSources;
    }

    /**
     * Gathers the list of all source groups that exist in the current
     * conference state.
     *
     * @return the list of all source groups of given media type that exist in
     * current conference state.
     */
    private MediaSourceGroupMap getAllSourceGroups() {
        return getAllSourceGroups(Collections.emptyList());
    }

    /**
     * Gathers the list of all source groups that exist in the current
     * conference state.
     *
     * @param except optional <tt>Participant</tt> instance whose source groups
     * will be excluded from the list
     *
     * @return the list of all source groups of given media type that exist in
     * current conference state.
     */
    MediaSourceGroupMap getAllSourceGroups(Participant except) {
        return getAllSourceGroups(Collections.singletonList(except));
    }

    /**
     * Gathers the list of all source groups that exist in the current
     * conference state.
     *
     * @param except a list of participants whose sources will not be included
     * in the result.
     *
     * @return the list of all source groups of given media type that exist in
     * current conference state.
     */
    private MediaSourceGroupMap getAllSourceGroups(List<Participant> except) {
        MediaSourceGroupMap sourceGroups = new MediaSourceGroupMap();

        for (Participant participant : participants) {
            // Excluded this participant groups
            if (except.contains(participant)) {
                continue;
            }

            sourceGroups.add(participant.getSourceGroupsCopy());
        }

        return sourceGroups;
    }

    /**
     * Returns global config instance.
     *
     * @return instance of <tt>JitsiMeetGlobalConfig</tt> used by this
     * conference.
     */
    JitsiMeetGlobalConfig getGlobalConfig() {
        return globalConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityBareJid getRoomName() {
        return roomName;
    }

    /**
     * @return {@link XmppConnection} instance for this conference.
     */
    XmppConnection getXmppConnection() {
        return protocolProviderHandler.getXmppConnection();
    }

    /**
     * Returns XMPP protocol provider of the focus account.
     */
    public ProtocolProviderService getXmppProvider() {
        return protocolProviderHandler.getProtocolProvider();
    }

    public XmppChatMember findMember(Jid from) {
        return chatRoom == null ? null : chatRoom.findChatMember(from);
    }

    /**
     * Returns {@link System#currentTimeMillis()} timestamp indicating the time
     * when this conference has become idle(we can measure how long is it). -1
     * is returned if this conference is considered active.
     *
     */
    public long getIdleTimestamp() {
        return idleTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityFullJid getFocusJid() {
        return JidCreate.fullFrom(roomName, focusUserName);
    }

    /**
     * Returns <tt>OperationSetJingle</tt> for the XMPP connection used in this
     * <tt>JitsiMeetConference</tt> instance.
     */
    public OperationSetJingle getJingle() {
        return jingle;
    }

    /**
     * Returns {@link JitsiMeetServices} instance used in this conference.
     */
    public JitsiMeetServices getServices() {
        return services;
    }

    /**
     * Handles mute request sent from participants.
     *
     * @param fromJid MUC jid of the participant that requested mute status
     * change.
     * @param toBeMutedJid MUC jid of the participant whose mute status will be
     * changed(eventually).
     * @param doMute the new audio mute status to set.
     * @return <tt>true</tt> if status has been set successfully.
     * @param blockAudioControl the new audio mute status to set.
     */
    boolean handleMuteRequest(Jid fromJid, Jid toBeMutedJid, boolean doMute, boolean blockAudioControl) {
        Participant principal = findParticipantForRoomJid(fromJid);
        if (principal == null) {
            logger.warn("Failed to perform mute operation - " + fromJid + " not exists in the conference.");
            return false;
        }
        // Only moderators can mute others
        if (!fromJid.equals(toBeMutedJid)
                && ChatRoomMemberRole.MODERATOR.compareTo(principal.getChatMember().getRole()) < 0) {
            logger.warn("Permission denied for mute operation from " + fromJid);
            return false;
        }

        Participant participant = findParticipantForRoomJid(toBeMutedJid);
        if (participant == null) {
            logger.warn("Participant for jid: " + toBeMutedJid + " not found");
            return false;
        }

        if (doMute && participant.isSipGateway() && !participant.hasAudioMuteSupport()) {
            logger.warn("Blocking mute request to jigasi. " + "Muting SIP participants is disabled.");
            return false;
        }

        if (doMute && participant.isJibri()) {
            logger.warn("Blocking mute request to jibri. ");
            return false;
        }

        logger.info("Will " + (doMute ? "mute" : "unmute") + " " + toBeMutedJid + " on behalf of " + fromJid);

        BridgeSession bridgeSession = findBridgeSession(participant);
        boolean succeeded = bridgeSession != null
                && bridgeSession.colibriConference.muteParticipant(participant.getColibriChannelsInfo(), doMute);

        if (succeeded) {
            participant.setMuted(doMute);
            participant.setBlockAudioStatus(blockAudioControl);
            return true;
        }

        return false;
    }
    
    boolean handleBlindRequest(Jid fromJid, Jid toBeBlindedJid, boolean doBlind, boolean blockVideoControl) {
        Participant principal = findParticipantForRoomJid(fromJid);
        if (principal == null) {
            logger.warn("Failed to perform blind operation - " + fromJid + " not exists in the conference.");
            return false;
        }
        // Only moderators can blind others
        if (!fromJid.equals(toBeBlindedJid)
                && ChatRoomMemberRole.MODERATOR.compareTo(principal.getChatMember().getRole()) < 0) {
            logger.warn("Permission denied for blind operation from " + fromJid);
            return false;
        }

        Participant participant = findParticipantForRoomJid(toBeBlindedJid);
        if (participant == null) {
            logger.warn("Participant for jid: " + toBeBlindedJid + " not found");
            return false;
        }

        //if (doBlind && participant.isSipGateway() && !participant.hasAudioMuteSupport()) {
        //    logger.warn("Blocking blind request to jigasi. " + "Muting SIP participants is disabled.");
        //    return false;
        //}

        if (doBlind && participant.isJibri()) {
            logger.warn("Blocking blind request to jibri. ");
            return false;
        }

        logger.info("Will " + (doBlind ? "blind" : "unblind") + " " + toBeBlindedJid + " on behalf of " + fromJid);

        BridgeSession bridgeSession = findBridgeSession(participant);
        boolean succeeded = bridgeSession != null
                && bridgeSession.colibriConference.blindParticipant(participant.getColibriChannelsInfo(), doBlind);

        if (succeeded) {
            participant.setBlinded(doBlind);
            participant.setBlockVideoStatus(blockVideoControl);
            return true;
        }

        return false;
    }

    /**
     * Handles mute request sent from participants.
     *
     * @param fromJid MUC jid of the participant that requested mute status
     * change.
     * @param veazzyRoomStatus the new audio mute status to set.
     * @return <tt>true</tt> if status has been set successfully.
     */
    boolean handleRoomStatusRequest(Jid fromJid, int veazzyRoomStatus) {

        if (participants != null && !participants.isEmpty()) {

            Participant principal = findParticipantForRoomJidForRoomStatusRequest(fromJid);
            if (principal == null) {
                logger.warn("Failed to perform roomStatus operation - " + fromJid + " not exists in the conference.");

                return false;
            }
            // Only moderators can mute others
            // if (//!fromJid.equals(toBeMutedJid) &&
            if (ChatRoomMemberRole.MODERATOR.compareTo(principal.getChatMember().getRole()) < 0) {
                logger.warn("Permission denied for roomStatus operation from " + fromJid);
                return false;
            }
        } else {
            if (participants == null || (participants != null && participants.isEmpty())) {
                if (participants == null) {
                    logger.info("Participants NULL, nobody in the room - Not looking for " + fromJid);
                }
                if (participants != null && participants.isEmpty()) {
                    logger.info("Participants empty, nobody in the room - Not looking for " + fromJid);
                }
                logger.info("Looking for xwpp chat member instead with " + fromJid);
                XmppChatMember member = findMember(fromJid);
                if (member != null) {
                    logger.info("Found member " + member.getContactAddress() + " - " + member.getDisplayName());
                    if (ChatRoomMemberRole.ADMINISTRATOR.compareTo(member.getRole()) == 0) {
                        logger.info("This member is ADMINISTRATOR");
                    }
                    if (ChatRoomMemberRole.GUEST.compareTo(member.getRole()) == 0) {
                        logger.info("This member is GUEST");
                    }
                    if (ChatRoomMemberRole.MEMBER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is MEMBER");
                    }
                    if (ChatRoomMemberRole.MODERATOR.compareTo(member.getRole()) == 0) {
                        logger.info("This member is MODERATOR");
                    }
                    if (ChatRoomMemberRole.OUTCAST.compareTo(member.getRole()) == 0) {
                        logger.info("This member is OUTCAST");
                    }
                    if (ChatRoomMemberRole.OWNER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is OWNER");
                    }
                    if (ChatRoomMemberRole.SILENT_MEMBER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is SILENT_MEMBER");
                    }
                    if (ChatRoomMemberRole.MODERATOR.compareTo(member.getRole()) < 0) {
                        logger.warn("Permission denied for roomStatus operation from " + fromJid);
                        return false;
                    }
                } else {
                    logger.info("Member not found");
                }
            } else {
                logger.info("Should not be here");
            }
        }

        logger.info("Will " + (veazzyRoomStatus == VeazzyRoomStatusIq.ROOM_STATUS_OPENED ? "open room" : "close room") + " on behalf of " + fromJid);

        //chatRoom.setVeazzyRoomStatus(doRoomStatusOpen);
        setVeazzyRoomStatus(veazzyRoomStatus);
        return true;
    }

    boolean handleRoomManagerIdRequest(Jid fromJid, String roomManagerId) {

        if (participants != null && !participants.isEmpty()) {

            Participant principal = findParticipantForRoomJidForRoomStatusRequest(fromJid);
            if (principal == null) {
                logger.warn("Failed to perform ModeratorId operation - " + fromJid + " not exists in the conference.");

                return false;
            }
            // Only moderators can mute others
            //if (//!fromJid.equals(toBeMutedJid) &&
            if (ChatRoomMemberRole.MODERATOR.compareTo(principal.getChatMember().getRole()) < 0) {
                logger.warn("Permission denied for ModeratorId operation from " + fromJid);
                return false;
            }
        } else {
            if (participants == null || (participants != null && participants.isEmpty())) {
                if (participants == null) {
                    logger.info("Participants NULL, nobody in the room - Not looking for " + fromJid);
                }
                if (participants != null && participants.isEmpty()) {
                    logger.info("Participants empty, nobody in the room - Not looking for " + fromJid);
                }
                logger.info("Looking for xwpp chat member instead with " + fromJid);
                XmppChatMember member = findMember(fromJid);
                if (member != null) {
                    logger.info("Found member " + member.getContactAddress() + " - " + member.getDisplayName());
                    if (ChatRoomMemberRole.ADMINISTRATOR.compareTo(member.getRole()) == 0) {
                        logger.info("This member is ADMINISTRATOR");
                    }
                    if (ChatRoomMemberRole.GUEST.compareTo(member.getRole()) == 0) {
                        logger.info("This member is GUEST");
                    }
                    if (ChatRoomMemberRole.MEMBER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is MEMBER");
                    }
                    if (ChatRoomMemberRole.MODERATOR.compareTo(member.getRole()) == 0) {
                        logger.info("This member is MODERATOR");
                    }
                    if (ChatRoomMemberRole.OUTCAST.compareTo(member.getRole()) == 0) {
                        logger.info("This member is OUTCAST");
                    }
                    if (ChatRoomMemberRole.OWNER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is OWNER");
                    }
                    if (ChatRoomMemberRole.SILENT_MEMBER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is SILENT_MEMBER");
                    }
                    if (ChatRoomMemberRole.MODERATOR.compareTo(member.getRole()) < 0) {
                        logger.warn("Permission denied for roomStatus operation from " + fromJid);
                        return false;
                    }
                } else {
                    logger.info("Member not found");
                }
            } else {
                logger.info("Should not be here");
            }
        }

        //chatRoom.setVeazzyRoomManagerId(roomManagerId);
        setVeazzyRoomManagerId(roomManagerId);
        return true;
    }
    
    boolean handleFocalParticipantIdRequest(Jid fromJid, String roomFocalParticipantId) {

        if (participants != null && !participants.isEmpty()) {

            Participant principal = findParticipantForRoomJidForRoomStatusRequest(fromJid);
            if (principal == null) {
                logger.warn("Failed to perform ParticipantId operation - " + fromJid + " not exists in the conference.");

                return false;
            }
            // Only moderators can mute others
            //if (//!fromJid.equals(toBeMutedJid) &&
            if (ChatRoomMemberRole.MODERATOR.compareTo(principal.getChatMember().getRole()) < 0) {
                logger.warn("Permission denied for ParticipantId operation from " + fromJid);
                return false;
            }
        } else {
            if (participants == null || (participants != null && participants.isEmpty())) {
                if (participants == null) {
                    logger.info("Participants NULL, nobody in the room - Not looking for " + fromJid);
                }
                if (participants != null && participants.isEmpty()) {
                    logger.info("Participants empty, nobody in the room - Not looking for " + fromJid);
                }
                logger.info("Looking for xwpp chat member instead with " + fromJid);
                XmppChatMember member = findMember(fromJid);
                if (member != null) {
                    logger.info("Found member " + member.getContactAddress() + " - " + member.getDisplayName());
                    if (ChatRoomMemberRole.ADMINISTRATOR.compareTo(member.getRole()) == 0) {
                        logger.info("This member is ADMINISTRATOR");
                    }
                    if (ChatRoomMemberRole.GUEST.compareTo(member.getRole()) == 0) {
                        logger.info("This member is GUEST");
                    }
                    if (ChatRoomMemberRole.MEMBER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is MEMBER");
                    }
                    if (ChatRoomMemberRole.MODERATOR.compareTo(member.getRole()) == 0) {
                        logger.info("This member is MODERATOR");
                    }
                    if (ChatRoomMemberRole.OUTCAST.compareTo(member.getRole()) == 0) {
                        logger.info("This member is OUTCAST");
                    }
                    if (ChatRoomMemberRole.OWNER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is OWNER");
                    }
                    if (ChatRoomMemberRole.SILENT_MEMBER.compareTo(member.getRole()) == 0) {
                        logger.info("This member is SILENT_MEMBER");
                    }
                    if (ChatRoomMemberRole.MODERATOR.compareTo(member.getRole()) < 0) {
                        logger.warn("Permission denied for roomStatus operation from " + fromJid);
                        return false;
                    }
                } else {
                    logger.info("Member not found");
                }
            } else {
                logger.info("Should not be here");
            }
        }

        //chatRoom.setVeazzyMainScreenParticipantId(roomFocalParticipantId);
        setVeazzyRoomFocalParticipantId(roomFocalParticipantId);
        
        return true;
    }

    boolean handleAdvertisingStreamIdRequest(Jid jid, Jid fromJid, int streamStatus) {
        
        logger.info("handleStreamIdRequest");
        
        // if true
        // /usr/share/jitsi-meet/stream.sh $room_name 1
            
        // if false
        // /usr/share/jitsi-meet/stream.sh $room_name 0
            
        String room_name = jid.toString();
        //String room_name = fromJid.toString();
            
        if(room_name.contains("@")) {
            room_name = room_name.substring(0, room_name.indexOf("@"));
        }
            
        String cmd = "/usr/share/jitsi-meet/stream.sh " + room_name;
            
        switch(streamStatus) {
            case VeazzyAdvertisingStreamIq.STREAM_STATUS_START:
                cmd += " " + VeazzyAdvertisingStreamIq.STREAM_STATUS_START;
                logger.info("handleStreamId Boolean.TRUE running cmd " + cmd);
                runScriptCmd(cmd);
                break;
            case VeazzyAdvertisingStreamIq.STREAM_STATUS_STOP:
                cmd += " " + VeazzyAdvertisingStreamIq.STREAM_STATUS_STOP;
                logger.info("handleStreamId Boolean.FALSE running cmd " + cmd);
                runScriptCmd(cmd);
                break;
        }
        return true;
    }
    
    boolean handleQuizQuestionIdRequest(Jid jid, Jid fromJid) {
        
        logger.info("handleQuizQuestionIdRequest");
        
        return true;
    }
    boolean handleQuizAnswerIdRequest(Jid jid, Jid fromJid) {
        
        logger.info("handleQuizAnswerIdRequest");
        
        return true;
    }
    
    //int getVeazzyRoomStatus() {
    //    return chatRoom.getVeazzyRoomStatus();
    //}
    
    //String getVeazzyRoomManagerId() {
    //    return chatRoom.getVeazzyRoomManagerId();
    //}
    
    //String getVeazzyRoomFocalParticipantId() {
    //    return chatRoom.getVeazzyRoomFocalParticipantId();
    //}

    /**
     * Returns current participants count. A participant is chat member who has
     * some videobridge and media state assigned(not just raw chat room member).
     * For example chat member which belongs to the focus never becomes a
     * participant.
     */
    public int getParticipantCount() {
        return participants.size();
    }

    /**
     * Focus instance ID
     */
    public String getId() {
        return id;
    }

    @Override
    public void handleEvent(Event event) {
        if (!(event instanceof BridgeEvent)) {
            logger.error("Unexpected event type: " + event);
            return;
        }

        BridgeEvent bridgeEvent = (BridgeEvent) event;
        Jid bridgeJid = bridgeEvent.getBridgeJid();
        switch (event.getTopic()) {
            case BridgeEvent.BRIDGE_DOWN:
                onBridgeDown(bridgeJid);
                break;
            case BridgeEvent.BRIDGE_UP:
                onBridgeUp(bridgeJid);
                break;
        }
    }

    private void onBridgeUp(Jid bridgeJid) {
        // Check if we're not shutting down
        if (!started) {
            return;
        }

        // TODO: if one of our bridges failed, we should have invited its
        // participants to another one. Here we should re-invite everyone if
        // the conference is not running (e.g. there was a single bridge and
        // it failed, then in was brought up).
        if (chatRoom != null && checkMinParticipants() && bridges.isEmpty()) {
            logger.info("New bridge available: " + bridgeJid + " will try to restart: " + getRoomName());

            logger.warn("Restarting the conference for room: " + getRoomName());

            synchronized (participantLock) {
                reInviteParticipants(participants);
            }
        }
    }

    /**
     * Notifies this conference that one of its channel allocators failed to
     * allocate channels, and that the participants on a specific bridge need to
     * be re-invited.
     *
     * @param bridgeJid the JID of the bridge on which participants need to be
     * re-invited.
     */
    void channelAllocationFailed(Jid bridgeJid) {
        onBridgeDown(bridgeJid);
    }

    /**
     * Notifies this conference that the bridge with a specific JID has failed.
     *
     * @param bridgeJid the JID of the bridge which failed.
     */
    void onBridgeDown(Jid bridgeJid) {
        List<Participant> participantsToReinvite = Collections.emptyList();

        synchronized (bridges) {
            BridgeSession bridgeSession = findBridgeSession(bridgeJid);
            if (bridgeSession != null) {
                logger.error("One of our bridges failed: " + bridgeJid);

                // Note: the Jingle sessions are still alive, we'll just
                // (try to) move to a new bridge and send transport-replace.
                participantsToReinvite = bridgeSession.terminateAll();

                bridges.remove(bridgeSession);
                setConferenceProperty(ConferenceProperties.KEY_BRIDGE_COUNT, Integer.toString(bridges.size()));

                updateOctoRelays();
            }
        }

        if (!participantsToReinvite.isEmpty()) {
            reInviteParticipants(participantsToReinvite);
        }
    }

    /**
     * A callback called by {@link ParticipantChannelAllocator} when
     * establishing the Jingle session with its participant fails.
     *
     * @param channelAllocator the channel allocator which failed.
     */
    void onInviteFailed(ParticipantChannelAllocator channelAllocator) {
        terminateParticipant(channelAllocator.getParticipant(), Reason.GENERAL_ERROR, "jingle session failed");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatRoomExtension getChatRoom() {
        return chatRoom;
    }

    /**
     * Returns config for this conference.
     *
     * @return <tt>JitsiMeetConfig</tt> instance used in this conference.
     */
    public JitsiMeetConfig getConfig() {
        return config;
    }

    /**
     * Returns the <tt>Logger</tt> used by this instance.
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * A method to be called by {@link AbstractChannelAllocator} just after it
     * has created a new Colibri conference on the JVB.
     *
     * @param colibriConference the {@link ColibriConference} instance which has
     * just allocated a conference on the bridge.
     * @param videobridgeJid the JID of the JVB where given
     */
    public void onColibriConferenceAllocated(ColibriConference colibriConference, Jid videobridgeJid) {
        // TODO: do we need this event?
        EventAdmin eventAdmin = FocusBundleActivator.getEventAdmin();
        if (eventAdmin != null) {
            eventAdmin.postEvent(EventFactory.conferenceRoom(colibriConference.getConferenceId(), roomName, getId(),
                    videobridgeJid));
        }

        // Remove "bridge not available" from Jicofo's presence
        // There is no check if it was ever added, but should be harmless
        ChatRoom chatRoom = this.chatRoom;
        if (meetTools != null && chatRoom != null) {
            meetTools.removePresenceExtension(chatRoom, new BridgeNotAvailablePacketExt());
        }
    }

    /**
     * Sets the value of the <tt>startMuted</tt> property of this instance.
     *
     * @param startMuted the new value to set on this instance. The specified
     * array is copied.
     */
    @Override
    public void setStartMuted(boolean[] startMuted) {
        this.startMuted = startMuted;
    }

    /**
     * Creates the shared document name by either using the conference room name
     * or a random string, depending on configuration.
     *
     * @return the shared document name.
     */
    private String createSharedDocumentName() {
        if (config.useRoomAsSharedDocName()) {
            return roomName.getLocalpart().toString();
        } else {
            return UUID.randomUUID().toString().replaceAll("-", "");
        }
    }

    /**
     * An adapter for {@link #reInviteParticipants(List)}.
     *
     * @param participant the {@link Participant} to be re invited into the
     * conference.
     */
    private void reInviteParticipant(Participant participant) {
        ArrayList<Participant> l = new ArrayList<>(1);

        l.add(participant);

        reInviteParticipants(l);
    }

    /**
     * Re-invites {@link Participant}s into the conference.
     *
     * @param participants the list of {@link Participant}s to be re-invited.
     */
    private void reInviteParticipants(List<Participant> participants) {
        synchronized (participantLock) {
            for (Participant participant : participants) {
                boolean synchronousExpire = false;
                BridgeSession session = participant.getBridgeSession();
                // If Participant is being re-invited to a healthy session
                // do a graceful channel expire with waiting for the
                // RESULT response. At the time of this writing the JVB may
                // process packets out of order and in the ICE failed scenario
                // the channel may not be expired correctly thus not
                // resulting in the restart at all. The ICE transport manager
                // must be recreated on the bridge to get new ICE credentials.
                if (session != null && session.bridge.isOperational() && !session.hasFailed) {
                    synchronousExpire = true;
                }

                participant.terminateBridgeSession(synchronousExpire);
            }
            for (Participant participant : participants) {
                inviteParticipant(participant, true, hasToStartMuted(participant, false));
            }
        }
    }

    /**
     * (Re)schedules {@link SinglePersonTimeout}.
     */
    private void rescheduleSingleParticipantTimeout() {
        if (executor != null) {
            cancelSingleParticipantTimeout();

            long timeout = globalConfig.getSingleParticipantTimeout();

            singleParticipantTout = executor.schedule(new SinglePersonTimeout(), timeout, TimeUnit.MILLISECONDS);

            logger.debug("Scheduled single person timeout for " + getRoomName());
        }
    }

    /**
     * Cancels {@link SinglePersonTimeout}.
     */
    private void cancelSingleParticipantTimeout() {
        if (executor != null && singleParticipantTout != null) {
            // This log is printed also when it's executed by the timeout thread
            // itself
            logger.debug("Cancelling single person timeout in room: " + getRoomName());

            singleParticipantTout.cancel(false);
            singleParticipantTout = null;
        }
    }

    /**
     * The task is scheduled with some delay when we end up with single
     * <tt>Participant</tt> in the room to terminate its media session. There is
     * no point in streaming media to the videobridge and using the bandwidth
     * when nobody is receiving it.
     */
    private class SinglePersonTimeout implements Runnable {

        @Override
        public void run() {
            synchronized (participantLock) {
                if (participants.size() == 1) {
                    Participant p = participants.get(0);
                    logger.info("Timing out single participant: " + p.getMucJid());

                    terminateParticipant(p, Reason.EXPIRED, "Idle session timeout");

                    disposeConference();
                } else {
                    logger.error("Should never execute if more than 1 participant? " + getRoomName());
                }
                singleParticipantTout = null;
            }
        }
    }

    /**
     * Returns the COLIBRI conference ID of one of the bridges used by this
     * conference. TODO: remove this (it is only used for testing)
     */
    public String getJvbConferenceId() {
        for (BridgeSession bridgeSession : bridges) {
            if (bridgeSession != null) {
                return bridgeSession.colibriConference.getConferenceId();
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Bridge, Integer> getBridges() {
        Map<Bridge, Integer> bridges = new HashMap<>();
        synchronized (this.bridges) {
            for (BridgeSession bridgeSession : this.bridges) {
                // TODO: do we actually want the hasFailed check?
                if (!bridgeSession.hasFailed) {
                    bridges.put(bridgeSession.bridge, bridgeSession.participants.size());
                }
            }
        }
        return bridges;
    }

    /**
     * The interface used to listen for conference events.
     */
    interface ConferenceListener {

        /**
         * Event fired when conference has ended.
         *
         * @param conference the conference instance that has ended.
         */
        void conferenceEnded(JitsiMeetConferenceImpl conference);
    }

    /**
     * Represents a {@link Bridge} instance as used by this
     * {@link JitsiMeetConferenceImpl}.
     */
    class BridgeSession {

        /**
         * The {@link Bridge}.
         */
        final Bridge bridge;

        /**
         * The bridge session's id.
         *
         * At the time of this writing it's used to distinguish between current
         * and outdated ICE failed notifications coming from the client.
         *
         * It can often happen that during a bridge failure multiple clients
         * will send ICE failed messages because all of them will have
         * connectivity broken. Jicofo will mark the bridge as unhealthy when
         * processing the first notification and any following ones should be
         * discarded.
         */
        final String id = JitsiMeetConferenceImpl.this.id + "_" + Integer.toHexString(RANDOM.nextInt(0x1_000000));

        /**
         * The list of participants in the conference which use this
         * {@link BridgeSession}.
         */
        List<Participant> participants = new LinkedList<>();

        /**
         * The {@link ColibriConference} instance used to communicate with the
         * jitsi-videobridge represented by this {@link BridgeSession}.
         */
        final ColibriConference colibriConference;

        /**
         * The single {@link OctoParticipant} for this bridge session, if any.
         */
        private OctoParticipant octoParticipant;

        /**
         * Indicates if the bridge used in this conference is faulty. We use
         * this flag to skip channel expiration step when the conference is
         * being disposed of.
         */
        public boolean hasFailed = false;

        /**
         * Initializes a new {@link BridgeSession} instance.
         *
         * @param bridge the {@link Bridge} which the new {@link BridgeSession}
         * instance is to represent.
         */
        BridgeSession(Bridge bridge) throws XmppStringprepException {
            this.bridge = Objects.requireNonNull(bridge, "bridge");
            this.colibriConference = createNewColibriConference(bridge.getJid());
        }

        /**
         * Disposes of this {@link BridgeSession}, attempting to expire the
         * COLIBRI conference.
         */
        private void dispose() {
            // We will not expire channels if the bridge is faulty or
            // when our connection is down
            if (!hasFailed && protocolProviderHandler.isRegistered()) {
                colibriConference.expireConference();
            } else {
                // TODO: make sure this doesn't block waiting for a response
                colibriConference.dispose();
            }

            // TODO: should we terminate (or clear) #participants?
        }

        /**
         * Expires the COLIBRI channels (via
         * {@link #terminate(AbstractParticipant, boolean)}) for all
         * participants.
         *
         * @return the list of participants which were removed from
         * {@link #participants} as a result of this call (does not include the
         * Octo participant).
         */
        private List<Participant> terminateAll() {
            List<Participant> terminatedParticipants = new LinkedList<>();
            // sync on what?
            for (Participant participant : new LinkedList<>(participants)) {
                if (participant.terminateBridgeSession() != null) {
                    terminatedParticipants.add(participant);
                }
            }

            if (octoParticipant != null) {
                terminate(octoParticipant, false);
            }

            return terminatedParticipants;
        }

        /**
         * Expires the COLIBRI channels allocated for a specific
         * {@link Participant} and removes the participant from
         * {@link #participants}.
         *
         * @param participant the {@link Participant} for which to expire the
         * COLIBRI channels.
         * @param syncExpire - whether or not the Colibri channels should be
         * expired in a synchronous manner, that is with blocking the current
         * thread until the RESULT packet is received.
         * @return {@code true} if the participant was a member of
         * {@link #participants} and was removed as a result of this call, and
         * {@code false} otherwise.
         */
        public boolean terminate(AbstractParticipant participant, boolean syncExpire) {
            boolean octo = participant == this.octoParticipant;
            boolean removed = octo || participants.remove(participant);
            if (removed) {
                logRegions();
            }

            ColibriConferenceIQ channelsInfo = participant != null ? participant.getColibriChannelsInfo() : null;

            if (channelsInfo != null && !hasFailed) {
                String id = (participant instanceof Participant) ? ((Participant) participant).getMucJid().toString()
                        : "octo";
                logger.info("Expiring channels for: " + id + " on: " + bridge);
                colibriConference.expireChannels(channelsInfo, syncExpire);
            }

            if (octo) {
                this.octoParticipant = null;
            }

            return removed;
        }

        /**
         * Sends a COLIBRI message which updates the channels for a particular
         * {@link Participant} in this {@link BridgeSession}, setting the
         * participant's RTP description, sources, transport information, etc.
         *
         * @param participant
         */
        private void updateColibriChannels(Participant participant) {
            colibriConference.updateChannelsInfo(participant.getColibriChannelsInfo(),
                    participant.getRtpDescriptionMap(), participant.getSourcesCopy(), participant.getSourceGroupsCopy(),
                    participant.getBundleTransport(), participant.getTransportMap(), participant.getEndpointId(), null);
        }

        /**
         * Sends a COLIBRI message which updates the channels for the Octo
         * participant in this {@link BridgeSession}.
         */
        private void updateColibriOctoChannels(OctoParticipant octoParticipant) {
            if (octoParticipant != null) {
                colibriConference.updateChannelsInfo(octoParticipant.getColibriChannelsInfo(),
                        octoParticipant.getRtpDescriptionMap(), octoParticipant.getSourcesCopy(),
                        octoParticipant.getSourceGroupsCopy(), null, null, null, octoParticipant.getRelays());
            }
        }

        /**
         * Returns the Octo participant for this {@link BridgeSession}. If a
         * participant doesn't exist yet, it is created.
         *
         * @return the {@link OctoParticipant} for this {@link BridgeSession}.
         */
        private OctoParticipant getOctoParticipant() {
            if (octoParticipant != null) {
                return octoParticipant;
            }

            List<String> remoteRelays = getAllRelays(bridge.getRelayId());
            return getOctoParticipant(new LinkedList<>(remoteRelays));
        }

        /**
         * Returns the Octo participant for this {@link BridgeSession}. If a
         * participant doesn't exist yet, it is created and initialized with
         * {@code relays} as the list of remote Octo relays.
         *
         * @return the {@link OctoParticipant} for this {@link BridgeSession}.
         */
        private OctoParticipant getOctoParticipant(List<String> relays) {
            if (octoParticipant == null) {
                octoParticipant = createOctoParticipant(relays);
            }
            return octoParticipant;
        }

        /**
         * Adds sources and source groups to this {@link BridgeSession}'s Octo
         * participant. If the Octo participant's session is already
         * established, then the sources are added and a colibri message is sent
         * to the bridge. Otherwise, they are scheduled to be added once the
         * session is established.
         *
         * @param sources the sources to add.
         * @param sourceGroups the source groups to add.
         */
        private void addSourcesToOcto(MediaSourceMap sources, MediaSourceGroupMap sourceGroups) {
            OctoParticipant octoParticipant = getOctoParticipant();

            synchronized (octoParticipant) {
                if (octoParticipant.isSessionEstablished()) {
                    octoParticipant.addSourcesAndGroups(sources, sourceGroups);
                    updateColibriOctoChannels(octoParticipant);
                } else {
                    // The allocator will take care of updating these when the
                    // session is established.
                    octoParticipant.scheduleSourcesToAdd(sources);
                    octoParticipant.scheduleSourceGroupsToAdd(sourceGroups);
                }
            }
        }

        /**
         * Removes sources and source groups
         *
         * @param sources
         * @param sourceGroups
         */
        private void removeSourcesFromOcto(MediaSourceMap sources, MediaSourceGroupMap sourceGroups) {
            OctoParticipant octoParticipant = this.octoParticipant;
            if (octoParticipant != null) {
                synchronized (octoParticipant) {
                    if (octoParticipant.isSessionEstablished()) {
                        octoParticipant.removeSources(sources);
                        octoParticipant.removeSourceGroups(sourceGroups);

                        updateColibriOctoChannels(octoParticipant);
                    } else {
                        octoParticipant.scheduleSourcesToRemove(sources);
                        octoParticipant.scheduleSourceGroupsToRemove(sourceGroups);
                    }
                }
            }
        }

        /**
         * Sets the list of Octo relays for this {@link BridgeSession}.
         *
         * @param allRelays all relays in the conference (including the relay of
         * the bridge of this {@link BridgeSession}).
         */
        private void setRelays(List<String> allRelays) {
            List<String> remoteRelays = new LinkedList<>(allRelays);
            remoteRelays.remove(bridge.getRelayId());

            if (logger.isDebugEnabled()) {
                logger.debug("Updating Octo relays for " + bridge + " in " + JitsiMeetConferenceImpl.this + " to "
                        + remoteRelays);
            }

            OctoParticipant octoParticipant = getOctoParticipant(remoteRelays);
            octoParticipant.setRelays(remoteRelays);
        }

        /**
         * Creates an {@link OctoParticipant} for this {@link BridgeSession} and
         * starts an {@link OctoChannelAllocator} to allocate channels for it.
         *
         * @param relays the list of Octo relay ids to set to the newly
         * allocated channels.
         * @return the instance which was created.
         */
        private OctoParticipant createOctoParticipant(List<String> relays) {
            logger.info("Creating an Octo participant for " + bridge + " in " + JitsiMeetConferenceImpl.this);

            OctoParticipant octoParticipant = new OctoParticipant(JitsiMeetConferenceImpl.this, relays);

            MediaSourceMap remoteSources = getAllSources(participants);
            MediaSourceGroupMap remoteGroups = getAllSourceGroups(participants);

            octoParticipant.addSourcesAndGroups(remoteSources, remoteGroups);

            OctoChannelAllocator channelAllocator = new OctoChannelAllocator(JitsiMeetConferenceImpl.this, this,
                    octoParticipant);
            octoParticipant.setChannelAllocator(channelAllocator);

            FocusBundleActivator.getSharedThreadPool().submit(channelAllocator);

            return octoParticipant;
        }

        @Override
        public String toString() {
            return String.format("BridgeSession[id=%s, bridge=%s]@%d", id, bridge, hashCode());
        }
    }

    @Override
    public boolean includeInStatistics() {
        return includeInStatistics;
    }

    protected FocusManager getFocusManager() {
        return ServiceUtils.getService(FocusBundleActivator.bundleContext, FocusManager.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("JitsiMeetConferenceImpl[id=%s, name=%s]", id, getRoomName().toString());
    }
    
    
    // https://stackoverflow.com/questions/525212/how-to-run-unix-shell-script-from-java-code
    
    public void runScriptCmd(String myCommand){
        try {
            Runtime.getRuntime().exec(myCommand);
        } catch (IOException ex) {
            Logger.getLogger(JitsiMeetConferenceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void runCommandFile1(String filePath) throws IOException{
        File file = new File(filePath);
        if(!file.isFile()){
            throw new IllegalArgumentException("The file " + filePath + " does not exist");
        }
        if(isLinux()){
            Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", filePath}, null);
        }else if(isWindows()){
            Runtime.getRuntime().exec("cmd /c start " + filePath);
        }
    }
    
    public static boolean isLinux(){
        String os = System.getProperty("os.name");  
        return os.toLowerCase().indexOf("linux") >= 0;
    }

    public static boolean isWindows(){
        String os = System.getProperty("os.name");
        return os.toLowerCase().indexOf("windows") >= 0;
    }
    
    public void runCommandFile2(String fileName) { //"myshellScript.sh"
        ProcessBuilder pb = new ProcessBuilder(fileName, "myArg1", "myArg2");
        Map<String, String> env = pb.environment();
        env.put("VAR1", "myValue");
        env.remove("OTHERVAR");
        env.put("VAR2", env.get("VAR1") + "suffix");
        pb.directory(new File("myDir"));
        try {
            Process p = pb.start();
        } catch (IOException ex) {
            Logger.getLogger(JitsiMeetConferenceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    public static void readBashScript() {
        try {
            Process proc = Runtime.getRuntime().exec("/home/destino/workspace/JavaProject/listing.sh /"); //Whatever you want to execute
            BufferedReader read = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            try {
                proc.waitFor();
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
            while (read.ready()) {
                System.out.println(read.readLine());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    
    /**
     * @return the veazzyRoomStatus
     */
    //@Override
    public int getVeazzyRoomStatus() {
        return veazzyRoomStatus;
    }

    /**
     * @param veazzyRoomStatus the veazzyRoomStatus to set
     */
    //@Override
    public void setVeazzyRoomStatus(int veazzyRoomStatus) {
        this.veazzyRoomStatus = veazzyRoomStatus;
    }

    /**
     * @return the veazzyRoomManagerId
     */
    //@Override
    public String getVeazzyRoomManagerId() {
        return veazzyRoomManagerId;
    }

    /**
     * @param veazzyRoomManagerId to set
     */
    //@Override
    public void setVeazzyRoomManagerId(String veazzyRoomManagerId) {
        this.veazzyRoomManagerId = veazzyRoomManagerId;
    }

    /**
     * @return the veazzyRoomFocalParticipantId
     */
    //@Override
    public String getVeazzyRoomFocalParticipantId() {
        return veazzyRoomFocalParticipantId;
    }

    /**
     * @param veazzyRoomFocalParticipantId to set
     */
    //@Override
    public void setVeazzyRoomFocalParticipantId(String veazzyRoomFocalParticipantId) {
        this.veazzyRoomFocalParticipantId = veazzyRoomFocalParticipantId;
    }
}
