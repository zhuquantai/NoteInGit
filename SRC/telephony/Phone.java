/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import android.content.Context;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;

import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.RadioCapability;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UsimServiceTable;

import com.android.internal.telephony.PhoneConstants.*; // ????

import java.util.List;
import java.util.Locale;

import com.mediatek.internal.telephony.FemtoCellInfo;
import com.mediatek.internal.telephony.NetworkInfoWithAcT;

import com.android.internal.telephony.dataconnection.DcFailCause;

/**
 * Internal interface used to control the phone; SDK developers cannot
 * obtain this interface.
 *
 * {@hide}
 *
 */
public interface Phone {

    /** used to enable additional debug messages */
    static final boolean DEBUG_PHONE = true;

    public enum DataActivityState {
        /**
         * The state of a data activity.
         * <ul>
         * <li>NONE = No traffic</li>
         * <li>DATAIN = Receiving IP ppp traffic</li>
         * <li>DATAOUT = Sending IP ppp traffic</li>
         * <li>DATAINANDOUT = Both receiving and sending IP ppp traffic</li>
         * <li>DORMANT = The data connection is still active,
                                     but physical link is down</li>
         * </ul>
         */
        NONE, DATAIN, DATAOUT, DATAINANDOUT, DORMANT;
    }

    enum SuppService {
      UNKNOWN, SWITCH, SEPARATE, TRANSFER, CONFERENCE, REJECT, HANGUP, RESUME;
    }

    // "Features" accessible through the connectivity manager
    static final String FEATURE_ENABLE_MMS = "enableMMS";
    static final String FEATURE_ENABLE_SUPL = "enableSUPL";
    static final String FEATURE_ENABLE_DUN = "enableDUN";
    static final String FEATURE_ENABLE_HIPRI = "enableHIPRI";
    static final String FEATURE_ENABLE_DUN_ALWAYS = "enableDUNAlways";
    static final String FEATURE_ENABLE_FOTA = "enableFOTA";
    static final String FEATURE_ENABLE_IMS = "enableIMS";
    static final String FEATURE_ENABLE_CBS = "enableCBS";
    static final String FEATURE_ENABLE_EMERGENCY = "enableEmergency";

    /** M: start */
    static final String FEATURE_ENABLE_DM = "enableDM";
    static final String FEATURE_ENABLE_WAP = "enableWAP";
    static final String FEATURE_ENABLE_NET = "enableNET";
    static final String FEATURE_ENABLE_CMMAIL = "enableCMMAIL";
    static final String FEATURE_ENABLE_RCSE = "enableRCSE";
    /** M: end */

    /**
     * Optional reasons for disconnect and connect
     */
    static final String REASON_ROAMING_ON = "roamingOn";
    static final String REASON_ROAMING_OFF = "roamingOff";
    static final String REASON_DATA_DISABLED = "dataDisabled";
    static final String REASON_DATA_ENABLED = "dataEnabled";
    static final String REASON_DATA_ATTACHED = "dataAttached";
    static final String REASON_DATA_DETACHED = "dataDetached";
    static final String REASON_CDMA_DATA_ATTACHED = "cdmaDataAttached";
    static final String REASON_CDMA_DATA_DETACHED = "cdmaDataDetached";
    static final String REASON_APN_CHANGED = "apnChanged";
    static final String REASON_APN_SWITCHED = "apnSwitched";
    static final String REASON_APN_FAILED = "apnFailed";
    static final String REASON_RESTORE_DEFAULT_APN = "restoreDefaultApn";
    static final String REASON_RADIO_TURNED_OFF = "radioTurnedOff";
    static final String REASON_PDP_RESET = "pdpReset";
    static final String REASON_VOICE_CALL_ENDED = "2GVoiceCallEnded";
    static final String REASON_VOICE_CALL_STARTED = "2GVoiceCallStarted";
    static final String REASON_PS_RESTRICT_ENABLED = "psRestrictEnabled";
    static final String REASON_PS_RESTRICT_DISABLED = "psRestrictDisabled";
    static final String REASON_SIM_LOADED = "simLoaded";
    static final String REASON_NW_TYPE_CHANGED = "nwTypeChanged";
    static final String REASON_DATA_DEPENDENCY_MET = "dependencyMet";
    static final String REASON_DATA_DEPENDENCY_UNMET = "dependencyUnmet";
    static final String REASON_LOST_DATA_CONNECTION = "lostDataConnection";
    static final String REASON_CONNECTED = "connected";
    static final String REASON_SINGLE_PDN_ARBITRATION = "SinglePdnArbitration";
    static final String REASON_DATA_SPECIFIC_DISABLED = "specificDisabled";
    static final String REASON_SIM_NOT_READY = "simNotReady";
    static final String REASON_IWLAN_AVAILABLE = "iwlanAvailable";
    static final String REASON_CARRIER_CHANGE = "carrierChange";
    static final String REASON_FDN_ENABLED = "FdnEnabled";
    static final String REASON_FDN_DISABLED = "FdnDisabled";
    static final String REASON_APN_CONFLICT = "apnConflict";
    static final String REASON_RA_FAILED = "raFailed";
    static final String REASON_QUERY_PLMN = "queryPLMN";
    /// M: [C2K][IRAT] reason of IRAT. @{
    static final String REASON_CDMA_IRAT_STARTED = "CdmaIratStarted";
    static final String REASON_CDMA_IRAT_ENDED = "CdmaIratEnded";
    static final String REASON_CDMA_FALLBACK_HAPPENED = "CdmaFallbackHappened";
    /// @}
    static final String REASON_DISKSPACE_LOWER = "diskSpaceLower"; //heyan add for Task 742719 low memory notification 20151210

    // Used for band mode selection methods
    static final int BM_UNSPECIFIED = 0; // selected by baseband automatically
    static final int BM_EURO_BAND   = 1; // GSM-900 / DCS-1800 / WCDMA-IMT-2000
    static final int BM_US_BAND     = 2; // GSM-850 / PCS-1900 / WCDMA-850 / WCDMA-PCS-1900
    static final int BM_JPN_BAND    = 3; // WCDMA-800 / WCDMA-IMT-2000
    static final int BM_AUS_BAND    = 4; // GSM-900 / DCS-1800 / WCDMA-850 / WCDMA-IMT-2000
    static final int BM_AUS2_BAND   = 5; // GSM-900 / DCS-1800 / WCDMA-850
    static final int BM_BOUNDARY    = 6; // upper band boundary

    // Used for preferred network type
    // Note NT_* substitute RILConstants.NETWORK_MODE_* above the Phone
    int NT_MODE_WCDMA_PREF   = RILConstants.NETWORK_MODE_WCDMA_PREF;
    int NT_MODE_GSM_ONLY     = RILConstants.NETWORK_MODE_GSM_ONLY;
    int NT_MODE_WCDMA_ONLY   = RILConstants.NETWORK_MODE_WCDMA_ONLY;
    int NT_MODE_GSM_UMTS     = RILConstants.NETWORK_MODE_GSM_UMTS;

    int NT_MODE_CDMA         = RILConstants.NETWORK_MODE_CDMA;

    int NT_MODE_CDMA_NO_EVDO = RILConstants.NETWORK_MODE_CDMA_NO_EVDO;
    int NT_MODE_EVDO_NO_CDMA = RILConstants.NETWORK_MODE_EVDO_NO_CDMA;
    int NT_MODE_GLOBAL       = RILConstants.NETWORK_MODE_GLOBAL;

    int NT_MODE_LTE_CDMA_AND_EVDO        = RILConstants.NETWORK_MODE_LTE_CDMA_EVDO;
    int NT_MODE_LTE_GSM_WCDMA            = RILConstants.NETWORK_MODE_LTE_GSM_WCDMA;
    int NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA  = RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA;
    int NT_MODE_LTE_ONLY                 = RILConstants.NETWORK_MODE_LTE_ONLY;
    int NT_MODE_LTE_WCDMA                = RILConstants.NETWORK_MODE_LTE_WCDMA;
    int PREFERRED_NT_MODE                = RILConstants.PREFERRED_NETWORK_MODE;

    // Used for CDMA roaming mode
    static final int CDMA_RM_HOME        = 0;  // Home Networks only, as defined in PRL
    static final int CDMA_RM_AFFILIATED  = 1;  // Roaming an Affiliated networks, as defined in PRL
    static final int CDMA_RM_ANY         = 2;  // Roaming on Any Network, as defined in PRL

    // Used for CDMA subscription mode
    static final int CDMA_SUBSCRIPTION_UNKNOWN  =-1; // Unknown
    static final int CDMA_SUBSCRIPTION_RUIM_SIM = 0; // RUIM/SIM (default)
    static final int CDMA_SUBSCRIPTION_NV       = 1; // NV -> non-volatile memory

    static final int PREFERRED_CDMA_SUBSCRIPTION = CDMA_SUBSCRIPTION_NV;

    static final int TTY_MODE_OFF = 0;
    static final int TTY_MODE_FULL = 1;
    static final int TTY_MODE_HCO = 2;
    static final int TTY_MODE_VCO = 3;

     /**
     * CDMA OTA PROVISION STATUS, the same as RIL_CDMA_OTA_Status in ril.h
     */

    public static final int CDMA_OTA_PROVISION_STATUS_SPL_UNLOCKED = 0;
    public static final int CDMA_OTA_PROVISION_STATUS_SPC_RETRIES_EXCEEDED = 1;
    public static final int CDMA_OTA_PROVISION_STATUS_A_KEY_EXCHANGED = 2;
    public static final int CDMA_OTA_PROVISION_STATUS_SSD_UPDATED = 3;
    public static final int CDMA_OTA_PROVISION_STATUS_NAM_DOWNLOADED = 4;
    public static final int CDMA_OTA_PROVISION_STATUS_MDN_DOWNLOADED = 5;
    public static final int CDMA_OTA_PROVISION_STATUS_IMSI_DOWNLOADED = 6;
    public static final int CDMA_OTA_PROVISION_STATUS_PRL_DOWNLOADED = 7;
    public static final int CDMA_OTA_PROVISION_STATUS_COMMITTED = 8;
    public static final int CDMA_OTA_PROVISION_STATUS_OTAPA_STARTED = 9;
    public static final int CDMA_OTA_PROVISION_STATUS_OTAPA_STOPPED = 10;
    public static final int CDMA_OTA_PROVISION_STATUS_OTAPA_ABORTED = 11;


    /**
     * Get the current ServiceState. Use
     * <code>registerForServiceStateChanged</code> to be informed of
     * updates.
     */
    ServiceState getServiceState();

    /**
     * Get the current CellLocation.
     */
    CellLocation getCellLocation();

    /**
     * @return all available cell information or null if none.
     */
    public List<CellInfo> getAllCellInfo();

    /**
     * Sets the minimum time in milli-seconds between {@link PhoneStateListener#onCellInfoChanged
     * PhoneStateListener.onCellInfoChanged} will be invoked.
     *
     * The default, 0, means invoke onCellInfoChanged when any of the reported
     * information changes. Setting the value to INT_MAX(0x7fffffff) means never issue
     * A onCellInfoChanged.
     *
     * @param rateInMillis the rate
     */
    public void setCellInfoListRate(int rateInMillis);

    /**
     * Get the current for the default apn DataState. No change notification
     * exists at this interface -- use
     * {@link android.telephony.PhoneStateListener} instead.
     */
    DataState getDataConnectionState();

    /**
     * Get the current DataState. No change notification exists at this
     * interface -- use
     * {@link android.telephony.PhoneStateListener} instead.
     * @param apnType specify for which apn to get connection state info.
     */
    DataState getDataConnectionState(String apnType);

    /**
     * Get the current DataActivityState. No change notification exists at this
     * interface -- use
     * {@link android.telephony.TelephonyManager} instead.
     */
    DataActivityState getDataActivityState();

    /**
     * Gets the context for the phone, as set at initialization time.
     */
    Context getContext();

    /**
     * Disables the DNS check (i.e., allows "0.0.0.0").
     * Useful for lab testing environment.
     * @param b true disables the check, false enables.
     */
    void disableDnsCheck(boolean b);

    /**
     * Returns true if the DNS check is currently disabled.
     */
    boolean isDnsCheckDisabled();

    /**
     * Get current coarse-grained voice call state.
     * Use {@link #registerForPreciseCallStateChanged(Handler, int, Object)
     * registerForPreciseCallStateChanged()} for change notification. <p>
     * If the phone has an active call and call waiting occurs,
     * then the phone state is RINGING not OFFHOOK
     * <strong>Note:</strong>
     * This registration point provides notification of finer-grained
     * changes.<p>
     *
     */
    State getState();

    /**
     * Returns a string identifier for this phone interface for parties
     *  outside the phone app process.
     *  @return The string name.
     */
    String getPhoneName();

    /**
     * Return a numerical identifier for the phone radio interface.
     * @return PHONE_TYPE_XXX as defined above.
     */
    int getPhoneType();

    /**
     * Returns an array of string identifiers for the APN types serviced by the
     * currently active.
     *  @return The string array will always return at least one entry, Phone.APN_TYPE_DEFAULT.
     * TODO: Revisit if we always should return at least one entry.
     */
    String[] getActiveApnTypes();

    /**
     * Check if TETHER_DUN_APN setting or config_tether_apndata includes APN that matches
     * current operator.
     * @return true if there is a matching DUN APN.
     */
    boolean hasMatchedTetherApnSetting();

    /**
     * Returns string for the active APN host.
     *  @return type as a string or null if none.
     */
    String getActiveApnHost(String apnType);

    /**
     * Return the LinkProperties for the named apn or null if not available
     */
    LinkProperties getLinkProperties(String apnType);

    /**
     * Return the NetworkCapabilities
     */
    NetworkCapabilities getNetworkCapabilities(String apnType);

    /**
     * Get current signal strength. No change notification available on this
     * interface. Use <code>PhoneStateNotifier</code> or an equivalent.
     * An ASU is 0-31 or -1 if unknown (for GSM, dBm = -113 - 2 * asu).
     * The following special values are defined:</p>
     * <ul><li>0 means "-113 dBm or less".</li>
     * <li>31 means "-51 dBm or greater".</li></ul>
     *
     * @return Current signal strength as SignalStrength
     */
    SignalStrength getSignalStrength();

    /**
     * Notifies when a previously untracked non-ringing/waiting connection has appeared.
     * This is likely due to some other entity (eg, SIM card application) initiating a call.
     */
    void registerForUnknownConnection(Handler h, int what, Object obj);

    /**
     * Unregisters for unknown connection notifications.
     */
    void unregisterForUnknownConnection(Handler h);

    /**
     * Notifies when a Handover happens due to SRVCC or Silent Redial
     */
    void registerForHandoverStateChanged(Handler h, int what, Object obj);

    /**
     * Unregisters for handover state notifications
     */
    void unregisterForHandoverStateChanged(Handler h);

    /**
     * Register for getting notifications for change in the Call State {@link Call.State}
     * This is called PreciseCallState because the call state is more precise than the
     * {@link PhoneConstants.State} which can be obtained using the {@link PhoneStateListener}
     *
     * Resulting events will have an AsyncResult in <code>Message.obj</code>.
     * AsyncResult.userData will be set to the obj argument here.
     * The <em>h</em> parameter is held only by a weak reference.
     */
    void registerForPreciseCallStateChanged(Handler h, int what, Object obj);

    /**
     * Unregisters for voice call state change notifications.
     * Extraneous calls are tolerated silently.
     */
    void unregisterForPreciseCallStateChanged(Handler h);

    /**
     * Notifies when a new ringing or waiting connection has appeared.<p>
     *
     *  Messages received from this:
     *  Message.obj will be an AsyncResult
     *  AsyncResult.userObj = obj
     *  AsyncResult.result = a Connection. <p>
     *  Please check Connection.isRinging() to make sure the Connection
     *  has not dropped since this message was posted.
     *  If Connection.isRinging() is true, then
     *   Connection.getCall() == Phone.getRingingCall()
     */
    void registerForNewRingingConnection(Handler h, int what, Object obj);

    /**
     * Unregisters for new ringing connection notification.
     * Extraneous calls are tolerated silently
     */

    void unregisterForNewRingingConnection(Handler h);

    /**
     * Notifies when phone's video capabilities changes <p>
     *
     *  Messages received from this:
     *  Message.obj will be an AsyncResult
     *  AsyncResult.userObj = obj
     *  AsyncResult.result = true if phone supports video calling <p>
     */
    public void registerForVideoCapabilityChanged(Handler h, int what, Object obj);

    /**
     * Unregisters for video capability changed notification.
     * Extraneous calls are tolerated silently
     */
    public void unregisterForVideoCapabilityChanged(Handler h);

    /**
     * Notifies when an incoming call rings.<p>
     *
     *  Messages received from this:
     *  Message.obj will be an AsyncResult
     *  AsyncResult.userObj = obj
     *  AsyncResult.result = a Connection. <p>
     */
    void registerForIncomingRing(Handler h, int what, Object obj);

    /**
     * Unregisters for ring notification.
     * Extraneous calls are tolerated silently
     */

    void unregisterForIncomingRing(Handler h);

    /**
     * Notifies when out-band ringback tone is needed.<p>
     *
     *  Messages received from this:
     *  Message.obj will be an AsyncResult
     *  AsyncResult.userObj = obj
     *  AsyncResult.result = boolean, true to start play ringback tone
     *                       and false to stop. <p>
     */
    void registerForRingbackTone(Handler h, int what, Object obj);

    /**
     * Unregisters for ringback tone notification.
     */

    void unregisterForRingbackTone(Handler h);

    /**
     * Notifies when out-band on-hold tone is needed.<p>
     *
     *  Messages received from this:
     *  Message.obj will be an AsyncResult
     *  AsyncResult.userObj = obj
     *  AsyncResult.result = boolean, true to start play on-hold tone
     *                       and false to stop. <p>
     */
    void registerForOnHoldTone(Handler h, int what, Object obj);

    /**
     * Unregisters for on-hold tone notification.
     */

    void unregisterForOnHoldTone(Handler h);

    /**
     * Registers the handler to reset the uplink mute state to get
     * uplink audio.
     */
    void registerForResendIncallMute(Handler h, int what, Object obj);

    /**
     * Unregisters for resend incall mute notifications.
     */
    void unregisterForResendIncallMute(Handler h);

    /**
     * Notifies when a voice connection has disconnected, either due to local
     * or remote hangup or error.
     *
     *  Messages received from this will have the following members:<p>
     *  <ul><li>Message.obj will be an AsyncResult</li>
     *  <li>AsyncResult.userObj = obj</li>
     *  <li>AsyncResult.result = a Connection object that is
     *  no longer connected.</li></ul>
     */
    void registerForDisconnect(Handler h, int what, Object obj);

    /**
     * Unregisters for voice disconnection notification.
     * Extraneous calls are tolerated silently
     */
    void unregisterForDisconnect(Handler h);


    /**
     * Register for notifications of initiation of a new MMI code request.
     * MMI codes for GSM are discussed in 3GPP TS 22.030.<p>
     *
     * Example: If Phone.dial is called with "*#31#", then the app will
     * be notified here.<p>
     *
     * The returned <code>Message.obj</code> will contain an AsyncResult.
     *
     * <code>obj.result</code> will be an "MmiCode" object.
     */
    void registerForMmiInitiate(Handler h, int what, Object obj);

    /**
     * Unregisters for new MMI initiate notification.
     * Extraneous calls are tolerated silently
     */
    void unregisterForMmiInitiate(Handler h);

    /**
     * Register for notifications that an MMI request has completed
     * its network activity and is in its final state. This may mean a state
     * of COMPLETE, FAILED, or CANCELLED.
     *
     * <code>Message.obj</code> will contain an AsyncResult.
     * <code>obj.result</code> will be an "MmiCode" object
     */
    void registerForMmiComplete(Handler h, int what, Object obj);

    /**
     * Unregisters for MMI complete notification.
     * Extraneous calls are tolerated silently
     */
    void unregisterForMmiComplete(Handler h);

    /**
     * Registration point for Ecm timer reset
     * @param h handler to notify
     * @param what user-defined message code
     * @param obj placed in Message.obj
     */
    public void registerForEcmTimerReset(Handler h, int what, Object obj);

    /**
     * Unregister for notification for Ecm timer reset
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForEcmTimerReset(Handler h);

    /**
     * Returns a list of MMI codes that are pending. (They have initiated
     * but have not yet completed).
     * Presently there is only ever one.
     * Use <code>registerForMmiInitiate</code>
     * and <code>registerForMmiComplete</code> for change notification.
     */
    public List<? extends MmiCode> getPendingMmiCodes();

    /**
     * Sends user response to a USSD REQUEST message.  An MmiCode instance
     * representing this response is sent to handlers registered with
     * registerForMmiInitiate.
     *
     * @param ussdMessge    Message to send in the response.
     */
    public void sendUssdResponse(String ussdMessge);

    /**
     * Register for ServiceState changed.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a ServiceState instance
     */
    void registerForServiceStateChanged(Handler h, int what, Object obj);

    /**
     * Unregisters for ServiceStateChange notification.
     * Extraneous calls are tolerated silently
     */
    void unregisterForServiceStateChanged(Handler h);

    /**
     * Register for Supplementary Service notifications from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a SuppServiceNotification instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSuppServiceNotification(Handler h, int what, Object obj);

    /**
     * Unregisters for Supplementary Service notifications.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForSuppServiceNotification(Handler h);

    /**
     * Register for notifications when a supplementary service attempt fails.
     * Message.obj will contain an AsyncResult.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSuppServiceFailed(Handler h, int what, Object obj);

    /**
     * Unregister for notifications when a supplementary service attempt fails.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForSuppServiceFailed(Handler h);

    /**
     * Register for notifications when a sInCall VoicePrivacy is enabled
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj);

    /**
     * Unegister for notifications when a sInCall VoicePrivacy is enabled
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForInCallVoicePrivacyOn(Handler h);

    /**
     * Register for notifications when a sInCall VoicePrivacy is disabled
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj);

    /**
     * Unregister for notifications when a sInCall VoicePrivacy is disabled
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForInCallVoicePrivacyOff(Handler h);

    /**
     * Register for notifications when CDMA OTA Provision status change
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForCdmaOtaStatusChange(Handler h, int what, Object obj);

    /**
     * Unregister for notifications when CDMA OTA Provision status change
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForCdmaOtaStatusChange(Handler h);

    /**
     * Registration point for subscription info ready
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj);

    /**
     * Unregister for notifications for subscription info
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForSubscriptionInfoReady(Handler h);

    /**
     * Registration point for Sim records loaded
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj);

    /**
     * Unregister for notifications for Sim records loaded
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForSimRecordsLoaded(Handler h);

    /**
     * Register for TTY mode change notifications from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be an Integer containing new mode.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForTtyModeReceived(Handler h, int what, Object obj);

    /**
     * Unregisters for TTY mode change notifications.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForTtyModeReceived(Handler h);

    /**
     * Returns SIM record load state. Use
     * <code>getSimCard().registerForReady()</code> for change notification.
     *
     * @return true if records from the SIM have been loaded and are
     * available (if applicable). If not applicable to the underlying
     * technology, returns true as well.
     */
    boolean getIccRecordsLoaded();

    /**
     * Returns the ICC card interface for this phone, or null
     * if not applicable to underlying technology.
     */
    IccCard getIccCard();

    /**
     * Answers a ringing or waiting call. Active calls, if any, go on hold.
     * Answering occurs asynchronously, and final notification occurs via
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}.
     *
     * @param videoState The video state in which to answer the call.
     * @exception CallStateException when no call is ringing or waiting
     */
    void acceptCall(int videoState) throws CallStateException;

    /**
     * Reject (ignore) a ringing call. In GSM, this means UDUB
     * (User Determined User Busy). Reject occurs asynchronously,
     * and final notification occurs via
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}.
     *
     * @exception CallStateException when no call is ringing or waiting
     */
    void rejectCall() throws CallStateException;

    /**
     * Places any active calls on hold, and makes any held calls
     *  active. Switch occurs asynchronously and may fail.
     * Final notification occurs via
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}.
     *
     * @exception CallStateException if a call is ringing, waiting, or
     * dialing/alerting. In these cases, this operation may not be performed.
     */
    void switchHoldingAndActive() throws CallStateException;

    /**
     * Whether or not the phone can conference in the current phone
     * state--that is, one call holding and one call active.
     * @return true if the phone can conference; false otherwise.
     */
    boolean canConference();

    /**
     * Conferences holding and active. Conference occurs asynchronously
     * and may fail. Final notification occurs via
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}.
     *
     * @exception CallStateException if canConference() would return false.
     * In these cases, this operation may not be performed.
     */
    void conference() throws CallStateException;

    /**
     * Enable or disable enhanced Voice Privacy (VP). If enhanced VP is
     * disabled, normal VP is enabled.
     *
     * @param enable whether true or false to enable or disable.
     * @param onComplete a callback message when the action is completed.
     */
    void enableEnhancedVoicePrivacy(boolean enable, Message onComplete);

    /**
     * Get the currently set Voice Privacy (VP) mode.
     *
     * @param onComplete a callback message when the action is completed.
     */
    void getEnhancedVoicePrivacy(Message onComplete);

    /**
     * Whether or not the phone can do explicit call transfer in the current
     * phone state--that is, one call holding and one call active.
     * @return true if the phone can do explicit call transfer; false otherwise.
     */
    boolean canTransfer();

    /**
     * Connects the two calls and disconnects the subscriber from both calls
     * Explicit Call Transfer occurs asynchronously
     * and may fail. Final notification occurs via
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}.
     *
     * @exception CallStateException if canTransfer() would return false.
     * In these cases, this operation may not be performed.
     */
    void explicitCallTransfer() throws CallStateException;

    /**
     * Clears all DISCONNECTED connections from Call connection lists.
     * Calls that were in the DISCONNECTED state become idle. This occurs
     * synchronously.
     */
    void clearDisconnected();


    /**
     * Gets the foreground call object, which represents all connections that
     * are dialing or active (all connections
     * that have their audio path connected).<p>
     *
     * The foreground call is a singleton object. It is constant for the life
     * of this phone. It is never null.<p>
     *
     * The foreground call will only ever be in one of these states:
     * IDLE, ACTIVE, DIALING, ALERTING, or DISCONNECTED.
     *
     * State change notification is available via
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}.
     */
    Call getForegroundCall();

    /**
     * Gets the background call object, which represents all connections that
     * are holding (all connections that have been accepted or connected, but
     * do not have their audio path connected). <p>
     *
     * The background call is a singleton object. It is constant for the life
     * of this phone object . It is never null.<p>
     *
     * The background call will only ever be in one of these states:
     * IDLE, HOLDING or DISCONNECTED.
     *
     * State change notification is available via
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}.
     */
    Call getBackgroundCall();

    /**
     * Gets the ringing call object, which represents an incoming
     * connection (if present) that is pending answer/accept. (This connection
     * may be RINGING or WAITING, and there may be only one.)<p>

     * The ringing call is a singleton object. It is constant for the life
     * of this phone. It is never null.<p>
     *
     * The ringing call will only ever be in one of these states:
     * IDLE, INCOMING, WAITING or DISCONNECTED.
     *
     * State change notification is available via
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}.
     */
    Call getRingingCall();

    /**
     * Initiate a new voice connection. This happens asynchronously, so you
     * cannot assume the audio path is connected (or a call index has been
     * assigned) until PhoneStateChanged notification has occurred.
     *
     * @param dialString The dial string.
     * @param videoState The desired video state for the connection.
     * @exception CallStateException if a new outgoing call is not currently
     * possible because no more call slots exist or a call exists that is
     * dialing, alerting, ringing, or waiting.  Other errors are
     * handled asynchronously.
     */
    Connection dial(String dialString, int videoState) throws CallStateException;

    /**
     * Initiate a new voice connection with supplementary User to User
     * Information. This happens asynchronously, so you cannot assume the audio
     * path is connected (or a call index has been assigned) until
     * PhoneStateChanged notification has occurred.
     *
     * NOTE: If adding another parameter, consider creating a DialArgs parameter instead to
     * encapsulate all dial arguments and decrease scaffolding headache.
     *
     * @param dialString The dial string.
     * @param uusInfo The UUSInfo.
     * @param videoState The desired video state for the connection.
     * @param intentExtras The extras from the original CALL intent.
     * @exception CallStateException if a new outgoing call is not currently
     *                possible because no more call slots exist or a call exists
     *                that is dialing, alerting, ringing, or waiting. Other
     *                errors are handled asynchronously.
     */
    Connection dial(String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras)
            throws CallStateException;

    /**
     * Handles PIN MMI commands (PIN/PIN2/PUK/PUK2), which are initiated
     * without SEND (so <code>dial</code> is not appropriate).
     *
     * @param dialString the MMI command to be executed.
     * @return true if MMI command is executed.
     */
    boolean handlePinMmi(String dialString);

    /**
     * Handles in-call MMI commands. While in a call, or while receiving a
     * call, use this to execute MMI commands.
     * see 3GPP 20.030, section 6.5.5.1 for specs on the allowed MMI commands.
     *
     * @param command the MMI command to be executed.
     * @return true if the MMI command is executed.
     * @throws CallStateException
     */
    boolean handleInCallMmiCommands(String command) throws CallStateException;

    /**
     * Play a DTMF tone on the active call. Ignored if there is no active call.
     * @param c should be one of 0-9, '*' or '#'. Other values will be
     * silently ignored.
     */
    void sendDtmf(char c);

    /**
     * Start to paly a DTMF tone on the active call. Ignored if there is no active call
     * or there is a playing DTMF tone.
     * @param c should be one of 0-9, '*' or '#'. Other values will be
     * silently ignored.
     */
    void startDtmf(char c);

    /**
     * Stop the playing DTMF tone. Ignored if there is no playing DTMF
     * tone or no active call.
     */
    void stopDtmf();

    /**
     * send burst DTMF tone, it can send the string as single character or multiple character
     * ignore if there is no active call or not valid digits string.
     * Valid digit means only includes characters ISO-LATIN characters 0-9, *, #
     * The difference between sendDtmf and sendBurstDtmf is sendDtmf only sends one character,
     * this api can send single character and multiple character, also, this api has response
     * back to caller.
     *
     * @param dtmfString is string representing the dialing digit(s) in the active call
     * @param on the DTMF ON length in milliseconds, or 0 for default
     * @param off the DTMF OFF length in milliseconds, or 0 for default
     * @param onComplete is the callback message when the action is processed by BP
     *
     */
    void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete);

    /**
     * Sets the radio power on/off state (off is sometimes
     * called "airplane mode"). Current state can be gotten via
     * {@link #getServiceState()}.{@link
     * android.telephony.ServiceState#getState() getState()}.
     * <strong>Note: </strong>This request is asynchronous.
     * getServiceState().getState() will not change immediately after this call.
     * registerForServiceStateChanged() to find out when the
     * request is complete.
     *
     * @param power true means "on", false means "off".
     */
    void setRadioPower(boolean power);

    /**
     * Get voice message waiting indicator status. No change notification
     * available on this interface. Use PhoneStateNotifier or similar instead.
     *
     * @return true if there is a voice message waiting
     */
    boolean getMessageWaitingIndicator();

    /**
     * Get voice call forwarding indicator status. No change notification
     * available on this interface. Use PhoneStateNotifier or similar instead.
     *
     * @return true if there is a voice call forwarding
     */
    boolean getCallForwardingIndicator();

    /**
     * Get the line 1 phone number (MSISDN). For CDMA phones, the MDN is returned
     * and {@link #getMsisdn()} will return the MSISDN on CDMA/LTE phones.<p>
     *
     * @return phone number. May return null if not
     * available or the SIM is not ready
     */
    String getLine1Number();

    /**
     * Returns the alpha tag associated with the msisdn number.
     * If there is no alpha tag associated or the record is not yet available,
     * returns a default localized string. <p>
     */
    String getLine1AlphaTag();

    /**
     * Sets the MSISDN phone number in the SIM card.
     *
     * @param alphaTag the alpha tag associated with the MSISDN phone number
     *        (see getMsisdnAlphaTag)
     * @param number the new MSISDN phone number to be set on the SIM.
     * @param onComplete a callback message when the action is completed.
     *
     * @return true if req is sent, false otherwise. If req is not sent there will be no response,
     * that is, onComplete will never be sent.
     */
    boolean setLine1Number(String alphaTag, String number, Message onComplete);

    /**
     * Get the voice mail access phone number. Typically dialed when the
     * user holds the "1" key in the phone app. May return null if not
     * available or the SIM is not ready.<p>
     */
    String getVoiceMailNumber();

    /**
     * Returns unread voicemail count. This count is shown when the  voicemail
     * notification is expanded.<p>
     */
    int getVoiceMessageCount();

    /**
     * Returns the alpha tag associated with the voice mail number.
     * If there is no alpha tag associated or the record is not yet available,
     * returns a default localized string. <p>
     *
     * Please use this value instead of some other localized string when
     * showing a name for this number in the UI. For example, call log
     * entries should show this alpha tag. <p>
     *
     * Usage of this alpha tag in the UI is a common carrier requirement.
     */
    String getVoiceMailAlphaTag();

    /**
     * setVoiceMailNumber
     * sets the voicemail number in the SIM card.
     *
     * @param alphaTag the alpha tag associated with the voice mail number
     *        (see getVoiceMailAlphaTag)
     * @param voiceMailNumber the new voicemail number to be set on the SIM.
     * @param onComplete a callback message when the action is completed.
     */
    void setVoiceMailNumber(String alphaTag,
                            String voiceMailNumber,
                            Message onComplete);

    /**
     * getCallForwardingOptions
     * gets a call forwarding option. The return value of
     * ((AsyncResult)onComplete.obj) is an array of CallForwardInfo.
     *
     * @param commandInterfaceCFReason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param onComplete a callback message when the action is completed.
     *        @see com.android.internal.telephony.CallForwardInfo for details.
     */
    void getCallForwardingOption(int commandInterfaceCFReason,
                                  Message onComplete);

    /**
     * getCallForwardingOptions
     * gets a call forwarding option. The return value of
     * ((AsyncResult)onComplete.obj) is an array of CallForwardInfo.
     *
     * @param commandInterfaceCFReason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param serviceClass a basic class input.
     * @param onComplete a callback message when the action is completed.
     *        @see com.android.internal.telephony.CallForwardInfo for details.
     */
    void getCallForwardingOptionForServiceClass(int commandInterfaceCFReason,
                                  int serviceClass,
                                  Message onComplete);

    /**
     * setCallForwardingOptions
     * sets a call forwarding option.
     *
     * @param commandInterfaceCFReason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param commandInterfaceCFAction is one of the valid call forwarding
     *        CF_ACTIONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param dialingNumber is the target phone number to forward calls to
     * @param timerSeconds is used by CFNRy to indicate the timeout before
     *        forwarding is attempted.
     * @param onComplete a callback message when the action is completed.
     */
    void setCallForwardingOption(int commandInterfaceCFReason,
                                 int commandInterfaceCFAction,
                                 String dialingNumber,
                                 int timerSeconds,
                                 Message onComplete);

    /**
     * setCallForwardingOptions
     * sets a call forwarding option.
     *
     * @param commandInterfaceCFReason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param commandInterfaceCFAction is one of the valid call forwarding
     *        CF_ACTIONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param dialingNumber is the target phone number to forward calls to
     * @param timerSeconds is used by CFNRy to indicate the timeout before
     *        forwarding is attempted.
     * @param serviceClass a basic class input.
     * @param onComplete a callback message when the action is completed.
     */
    void setCallForwardingOptionForServiceClass(int commandInterfaceCFReason,
                                 int commandInterfaceCFAction,
                                 String dialingNumber,
                                 int timerSeconds,
                                 int serviceClass,
                                 Message onComplete);

    /**
     * getOutgoingCallerIdDisplay
     * gets outgoing caller id display. The return value of
     * ((AsyncResult)onComplete.obj) is an array of int, with a length of 2.
     *
     * @param onComplete a callback message when the action is completed.
     *        @see com.android.internal.telephony.CommandsInterface#getCLIR for details.
     */
    void getOutgoingCallerIdDisplay(Message onComplete);

    /**
     * setOutgoingCallerIdDisplay
     * sets a call forwarding option.
     *
     * @param commandInterfaceCLIRMode is one of the valid call CLIR
     *        modes, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface./code>
     * @param onComplete a callback message when the action is completed.
     */
    void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
                                    Message onComplete);

    /**
     * getCallWaiting
     * gets call waiting activation state. The return value of
     * ((AsyncResult)onComplete.obj) is an array of int, with a length of 1.
     *
     * @param onComplete a callback message when the action is completed.
     *        @see com.android.internal.telephony.CommandsInterface#queryCallWaiting for details.
     */
    void getCallWaiting(Message onComplete);

    /**
     * setCallWaiting
     * sets a call forwarding option.
     *
     * @param enable is a boolean representing the state that you are
     *        requesting, true for enabled, false for disabled.
     * @param onComplete a callback message when the action is completed.
     */
    void setCallWaiting(boolean enable, Message onComplete);

    /**
     * Scan available networks. This method is asynchronous; .
     * On completion, <code>response.obj</code> is set to an AsyncResult with
     * one of the following members:.<p>
     *<ul>
     * <li><code>response.obj.result</code> will be a <code>List</code> of
     * <code>OperatorInfo</code> objects, or</li>
     * <li><code>response.obj.exception</code> will be set with an exception
     * on failure.</li>
     * </ul>
     */
    void getAvailableNetworks(Message response);

    /**
     * Switches network selection mode to "automatic", re-scanning and
     * re-selecting a network if appropriate.
     *
     * @param response The message to dispatch when the network selection
     * is complete.
     *
     * @see #selectNetworkManually(OperatorInfo, android.os.Message )
     */
    void setNetworkSelectionModeAutomatic(Message response);

    /**
     * Manually selects a network. <code>response</code> is
     * dispatched when this is complete.  <code>response.obj</code> will be
     * an AsyncResult, and <code>response.obj.exception</code> will be non-null
     * on failure.
     *
     * @see #setNetworkSelectionModeAutomatic(Message)
     */
    void selectNetworkManually(OperatorInfo network,
                            Message response);

    /**
     * Query the radio for the current network selection mode.
     *
     * Return values:
     *     0 - automatic.
     *     1 - manual.
     */
    void getNetworkSelectionMode(Message response);

    /**
     *  Requests to set the preferred network type for searching and registering
     * (CS/PS domain, RAT, and operation mode)
     * @param networkType one of  NT_*_TYPE
     * @param response is callback message
     */
    void setPreferredNetworkType(int networkType, Message response);

    /**
     *  Query the preferred network type setting
     *
     * @param response is callback message to report one of  NT_*_TYPE
     */
    void getPreferredNetworkType(Message response);

    /**
     * Gets the default SMSC address.
     *
     * @param result Callback message contains the SMSC address.
     */
    void getSmscAddress(Message result);

    /**
     * Sets the default SMSC address.
     *
     * @param address new SMSC address
     * @param result Callback message is empty on completion
     */
    void setSmscAddress(String address, Message result);

    /**
     * Query neighboring cell IDs.  <code>response</code> is dispatched when
     * this is complete.  <code>response.obj</code> will be an AsyncResult,
     * and <code>response.obj.exception</code> will be non-null on failure.
     * On success, <code>AsyncResult.result</code> will be a <code>String[]</code>
     * containing the neighboring cell IDs.  Index 0 will contain the count
     * of available cell IDs.  Cell IDs are in hexadecimal format.
     *
     * @param response callback message that is dispatched when the query
     * completes.
     */
    void getNeighboringCids(Message response);

    /**
     * Sets an event to be fired when the telephony system processes
     * a post-dial character on an outgoing call.<p>
     *
     * Messages of type <code>what</code> will be sent to <code>h</code>.
     * The <code>obj</code> field of these Message's will be instances of
     * <code>AsyncResult</code>. <code>Message.obj.result</code> will be
     * a Connection object.<p>
     *
     * Message.arg1 will be the post dial character being processed,
     * or 0 ('\0') if end of string.<p>
     *
     * If Connection.getPostDialState() == WAIT,
     * the application must call
     * {@link com.android.internal.telephony.Connection#proceedAfterWaitChar()
     * Connection.proceedAfterWaitChar()} or
     * {@link com.android.internal.telephony.Connection#cancelPostDial()
     * Connection.cancelPostDial()}
     * for the telephony system to continue playing the post-dial
     * DTMF sequence.<p>
     *
     * If Connection.getPostDialState() == WILD,
     * the application must call
     * {@link com.android.internal.telephony.Connection#proceedAfterWildChar
     * Connection.proceedAfterWildChar()}
     * or
     * {@link com.android.internal.telephony.Connection#cancelPostDial()
     * Connection.cancelPostDial()}
     * for the telephony system to continue playing the
     * post-dial DTMF sequence.<p>
     *
     * Only one post dial character handler may be set. <p>
     * Calling this method with "h" equal to null unsets this handler.<p>
     */
    void setOnPostDialCharacter(Handler h, int what, Object obj);


    /**
     * Mutes or unmutes the microphone for the active call. The microphone
     * is automatically unmuted if a call is answered, dialed, or resumed
     * from a holding state.
     *
     * @param muted true to mute the microphone,
     * false to activate the microphone.
     */

    void setMute(boolean muted);

    /**
     * Gets current mute status. Use
     * {@link #registerForPreciseCallStateChanged(android.os.Handler, int,
     * java.lang.Object) registerForPreciseCallStateChanged()}
     * as a change notifcation, although presently phone state changed is not
     * fired when setMute() is called.
     *
     * @return true is muting, false is unmuting
     */
    boolean getMute();

    /**
     * Enables or disables echo suppression.
     */
    void setEchoSuppressionEnabled();

    /**
     * Invokes RIL_REQUEST_OEM_HOOK_RAW on RIL implementation.
     *
     * @param data The data for the request.
     * @param response <strong>On success</strong>,
     * (byte[])(((AsyncResult)response.obj).result)
     * <strong>On failure</strong>,
     * (((AsyncResult)response.obj).result) == null and
     * (((AsyncResult)response.obj).exception) being an instance of
     * com.android.internal.telephony.gsm.CommandException
     *
     * @see #invokeOemRilRequestRaw(byte[], android.os.Message)
     */
    void invokeOemRilRequestRaw(byte[] data, Message response);

    /**
     * Invokes RIL_REQUEST_OEM_HOOK_Strings on RIL implementation.
     *
     * @param strings The strings to make available as the request data.
     * @param response <strong>On success</strong>, "response" bytes is
     * made available as:
     * (String[])(((AsyncResult)response.obj).result).
     * <strong>On failure</strong>,
     * (((AsyncResult)response.obj).result) == null and
     * (((AsyncResult)response.obj).exception) being an instance of
     * com.android.internal.telephony.gsm.CommandException
     *
     * @see #invokeOemRilRequestStrings(java.lang.String[], android.os.Message)
     */
    void invokeOemRilRequestStrings(String[] strings, Message response);

    /**
     * Get the current active Data Call list
     *
     * @param response <strong>On success</strong>, "response" bytes is
     * made available as:
     * (String[])(((AsyncResult)response.obj).result).
     * <strong>On failure</strong>,
     * (((AsyncResult)response.obj).result) == null and
     * (((AsyncResult)response.obj).exception) being an instance of
     * com.android.internal.telephony.gsm.CommandException
     */
    void getDataCallList(Message response);

    /**
     * Update the ServiceState CellLocation for current network registration.
     */
    void updateServiceLocation();

    /**
     * Enable location update notifications.
     */
    void enableLocationUpdates();

    /**
     * Disable location update notifications.
     */
    void disableLocationUpdates();

    /**
     * For unit tests; don't send notifications to "Phone"
     * mailbox registrants if true.
     */
    void setUnitTestMode(boolean f);

    /**
     * @return true If unit test mode is enabled
     */
    boolean getUnitTestMode();

    /**
     * Assign a specified band for RF configuration.
     *
     * @param bandMode one of BM_*_BAND
     * @param response is callback message
     */
    void setBandMode(int bandMode, Message response);

    /**
     * Query the list of band mode supported by RF.
     *
     * @param response is callback message
     *        ((AsyncResult)response.obj).result  is an int[] where int[0] is
     *        the size of the array and the rest of each element representing
     *        one available BM_*_BAND
     */
    void queryAvailableBandMode(Message response);

    /**
     * @return true if enable data connection on roaming
     */
    boolean getDataRoamingEnabled();

    /**
     * @param enable set true if enable data connection on roaming
     */
    void setDataRoamingEnabled(boolean enable);

    /**
     * @return true if user has enabled data
     */
    boolean getDataEnabled();

    /**
     * @param @enable set {@code true} if enable data connection
     */
    void setDataEnabled(boolean enable);

    /**
     *  Query the CDMA roaming preference setting
     *
     * @param response is callback message to report one of  CDMA_RM_*
     */
    void queryCdmaRoamingPreference(Message response);

    /**
     *  Requests to set the CDMA roaming preference
     * @param cdmaRoamingType one of  CDMA_RM_*
     * @param response is callback message
     */
    void setCdmaRoamingPreference(int cdmaRoamingType, Message response);

    /**
     *  Requests to set the CDMA subscription mode
     * @param cdmaSubscriptionType one of  CDMA_SUBSCRIPTION_*
     * @param response is callback message
     */
    void setCdmaSubscription(int cdmaSubscriptionType, Message response);

    /**
     * If this is a simulated phone interface, returns a SimulatedRadioControl.
     * @return SimulatedRadioControl if this is a simulated interface;
     * otherwise, null.
     */
    SimulatedRadioControl getSimulatedRadioControl();

    /**
     * Report on whether data connectivity is allowed.
     */
    boolean isDataConnectivityPossible();

    /**
     * Report on whether data connectivity is allowed for an APN.
     */
    boolean isDataConnectivityPossible(String apnType);

    /**
     * Retrieves the unique device ID, e.g., IMEI for GSM phones and MEID for CDMA phones.
     */
    String getDeviceId();

    /**
     * Retrieves the software version number for the device, e.g., IMEI/SV
     * for GSM phones.
     */
    String getDeviceSvn();

    /**
     * Retrieves the unique subscriber ID, e.g., IMSI for GSM phones.
     */
    String getSubscriberId();

    /**
     * Retrieves the Group Identifier Level1 for GSM phones.
     */
    String getGroupIdLevel1();

    /**
     * Retrieves the Group Identifier Level2 for phones.
     */
    String getGroupIdLevel2();

    /**
     * Retrieves the serial number of the ICC, if applicable.
     */
    String getIccSerialNumber();

    /* CDMA support methods */

    /**
     * Retrieves the MIN for CDMA phones.
     */
    String getCdmaMin();

    /**
     * Check if subscription data has been assigned to mMin
     *
     * return true if MIN info is ready; false otherwise.
     */
    boolean isMinInfoReady();

    /**
     *  Retrieves PRL Version for CDMA phones
     */
    String getCdmaPrlVersion();

    /**
     * Retrieves the ESN for CDMA phones.
     */
    String getEsn();

    /**
     * Retrieves MEID for CDMA phones.
     */
    String getMeid();

    /**
     * Retrieves the MSISDN from the UICC. For GSM/UMTS phones, this is equivalent to
     * {@link #getLine1Number()}. For CDMA phones, {@link #getLine1Number()} returns
     * the MDN, so this method is provided to return the MSISDN on CDMA/LTE phones.
     */
    String getMsisdn();

    /**
     * Retrieves IMEI for phones. Returns null if IMEI is not set.
     */
    String getImei();

    /**
     * Retrieves Nai for phones. Returns null if Nai is not set.
     */
    String getNai();

    /**
     * Retrieves the PhoneSubInfo of the Phone
     */
    public PhoneSubInfo getPhoneSubInfo();

    /**
     * Retrieves the IccPhoneBookInterfaceManager of the Phone
     */
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager();

    /**
     * setTTYMode
     * sets a TTY mode option.
     * @param ttyMode is a one of the following:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param onComplete a callback message when the action is completed
     */
    void setTTYMode(int ttyMode, Message onComplete);

   /**
     * setUiTTYMode
     * sets a TTY mode option.
     * @param ttyMode is a one of the following:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param onComplete a callback message when the action is completed
     */
    void setUiTTYMode(int uiTtyMode, Message onComplete);

    /**
     * queryTTYMode
     * query the status of the TTY mode
     *
     * @param onComplete a callback message when the action is completed.
     */
    void queryTTYMode(Message onComplete);

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param activate
     *            0 = activate, 1 = deactivate
     * @param response
     *            Callback message is empty on completion
     */
    void activateCellBroadcastSms(int activate, Message response);

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param response
     *            Callback message is empty on completion
     */
    void getCellBroadcastSmsConfig(Message response);

    /**
     * Configure cell broadcast SMS.
     *
     * TODO: Change the configValuesArray to a RIL_BroadcastSMSConfig
     *
     * @param response
     *            Callback message is empty on completion
     */
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response);

    /* M: SS part */
    /**
     * getFacilityLock
     * gets Call Barring States. The return value of
     * (AsyncResult)response.obj).result will be an Integer representing
     * the sum of enabled serivice classes (sum of SERVICE_CLASS_*)
     *
     * @param facility one of CB_FACILTY_*
     * @param password password or "" if not required
     * @param onComplete a callback message when the action is completed.
     * @internal
     */
    void getFacilityLock(String facility, String password, Message onComplete);

    /**
     * setFacilityLock
     * sets Call Barring options.
     *
     * @param facility one of CB_FACILTY_*
     * @param enable true means lock, false means unlock
     * @param password password or "" if not required
     * @param onComplete a callback message when the action is completed.
     * @internal
     */
    void setFacilityLock(String facility, boolean enable, String password, Message onComplete);

        /**
     * getFacilityLock
     * gets Call Barring States. The return value of
     * (AsyncResult)response.obj).result will be an Integer representing
     * the sum of enabled serivice classes (sum of SERVICE_CLASS_*)
     *
     * @param facility one of CB_FACILTY_*
     * @param password password or "" if not required
     * @param serviceClass a basic class input.
     * @param onComplete a callback message when the action is completed.
     * @internal
     */
    void getFacilityLockForServiceClass(String facility, String password,
        int serviceClass, Message onComplete);

    /**
     * setFacilityLock
     * sets Call Barring options.
     *
     * @param facility one of CB_FACILTY_*
     * @param enable true means lock, false means unlock
     * @param password password or "" if not required
     * @param serviceClass a basic class input.
     * @param onComplete a callback message when the action is completed.
     * @internal
     */
    void setFacilityLockForServiceClass(String facility, boolean enable,
        String password, int serviceClass, Message onComplete);

    /**
     * changeBarringPassword
     * changes Call Barring related password.
     *
     * @param facility one of CB_FACILTY_*
     * @param oldPwd old password
     * @param newPwd new password
     * @param onComplete a callback message when the action is completed.
     */
    void changeBarringPassword(String facility, String oldPwd,
        String newPwd, Message onComplete);

    /**
     * changeBarringPassword
     * changes Call Barring related password.
     *
     * @param facility one of CB_FACILTY_*
     * @param oldPwd old password
     * @param newPwd new password
     * @param newCfm
     * @param onComplete a callback message when the action is completed.
     */
    void changeBarringPassword(String facility, String oldPwd,
        String newPwd, String newCfm, Message onComplete);

    /**
     * Get the UT CS fallback status.
     *
     * @return The UT CS fallback status:
     *         {@link PhoneConstants.UT_CSFB_PS_PREFERRED}
     *         {@link PhoneConstants.UT_CSFB_ONCE}
     *         {@link PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT}.
     */
    public int getCsFallbackStatus();
    /* M: SS part  end */

    /// M: SS OP01 Ut @{
    /**
     * getCallForwardInTimeSlot
     * gets a call forwarding in a time slot option. The return value of
     * ((AsyncResult)onComplete.obj) is an array of CallForwardInfoEx.
     *
     * @param commandInterfaceCFReason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     *        Only support the value is CF_REASON_ALL_CONDITIONAL now.
     * @param onComplete a callback message when the action is completed.
     *        @see com.android.internal.telephony.CallForwardInfoEx for details.
     */
    void getCallForwardInTimeSlot(int commandInterfaceCFReason,
                                  Message onComplete);

    /**
     * setCallForwardInTimeSlot
     * sets a call forwarding in a time slot option.
     *
     * @param commandInterfaceCFAction is one of the valid call forwarding
     *        CF_ACTIONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param commandInterfaceCFReason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     *        Only support the value is CF_REASON_ALL_CONDITIONAL now.
     * @param dialingNumber is the target phone number to forward calls to
     * @param timerSeconds is used by CFNRy to indicate the timeout before
     *        forwarding is attempted.
     * @param timeSlot is used to indicate the time slot in which calls
     *        will be forwarded.
     * @param onComplete a callback message when the action is completed.
     */
    void setCallForwardInTimeSlot(int commandInterfaceCFAction,
                                  int commandInterfaceCFReason,
                                  String dialingNumber,
                                  int timerSeconds,
                                  long[] timeSlot,
                                  Message onComplete);

    /**
     * Returns the time slot in which calls will be forwarded.
     */
    long[] getTimeSlot();
    /// @}

    public void notifyDataActivity();

    /**
     * Returns the CDMA ERI icon index to display
     */
    public int getCdmaEriIconIndex();

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     */
    public int getCdmaEriIconMode();

    /**
     * Returns the CDMA ERI text,
     */
    public String getCdmaEriText();

    /**
     * request to exit emergency call back mode
     * the caller should use setOnECMModeExitResponse
     * to receive the emergency callback mode exit response
     */
    void exitEmergencyCallbackMode();

    /**
     * this decides if the dial number is OTA(Over the air provision) number or not
     * @param dialStr is string representing the dialing digit(s)
     * @return  true means the dialStr is OTA number, and false means the dialStr is not OTA number
     */
    boolean isOtaSpNumber(String dialStr);

    /**
     * Returns true if OTA Service Provisioning needs to be performed.
     */
    boolean needsOtaServiceProvisioning();

    /**
     * Register for notifications when CDMA call waiting comes
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForCallWaiting(Handler h, int what, Object obj);

    /**
     * Unegister for notifications when CDMA Call waiting comes
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForCallWaiting(Handler h);


    /**
     * Register for signal information notifications from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a SuppServiceNotification instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */

    void registerForSignalInfo(Handler h, int what, Object obj) ;
    /**
     * Unregisters for signal information notifications.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForSignalInfo(Handler h);

    /**
     * Register for display information notifications from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a SuppServiceNotification instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForDisplayInfo(Handler h, int what, Object obj);

    /**
     * Unregisters for display information notifications.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForDisplayInfo(Handler h) ;

    /**
     * Register for CDMA number information record notification from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a CdmaInformationRecords.CdmaNumberInfoRec
     * instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForNumberInfo(Handler h, int what, Object obj);

    /**
     * Unregisters for number information record notifications.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForNumberInfo(Handler h);

    /**
     * Register for CDMA redirected number information record notification
     * from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a CdmaInformationRecords.CdmaRedirectingNumberInfoRec
     * instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForRedirectedNumberInfo(Handler h, int what, Object obj);

    /**
     * Unregisters for redirected number information record notification.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForRedirectedNumberInfo(Handler h);

    /**
     * Register for CDMA line control information record notification
     * from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a CdmaInformationRecords.CdmaLineControlInfoRec
     * instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForLineControlInfo(Handler h, int what, Object obj);

    /**
     * Unregisters for line control information notifications.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForLineControlInfo(Handler h);

    /**
     * Register for CDMA T53 CLIR information record notifications
     * from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a CdmaInformationRecords.CdmaT53ClirInfoRec
     * instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerFoT53ClirlInfo(Handler h, int what, Object obj);

    /**
     * Unregisters for T53 CLIR information record notification
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForT53ClirInfo(Handler h);

    /**
     * Register for CDMA T53 audio control information record notifications
     * from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a CdmaInformationRecords.CdmaT53AudioControlInfoRec
     * instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForT53AudioControlInfo(Handler h, int what, Object obj);

    /**
     * Unregisters for T53 audio control information record notifications.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForT53AudioControlInfo(Handler h);

    /**
     * Register for radio off or not available
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     * @internal
     */
    public void registerForRadioOffOrNotAvailable(Handler h, int what, Object obj);

    /**
     * Unregisters for radio off or not available
     *
     * @param h Handler to be removed from the registrant list.
     * @internal
     */
    public void unregisterForRadioOffOrNotAvailable(Handler h);

    /**
     * registers for exit emergency call back mode request response
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */

    void setOnEcbModeExitResponse(Handler h, int what, Object obj);

    /**
     * Unregisters for exit emergency call back mode request response
     *
     * @param h Handler to be removed from the registrant list.
     */
    void unsetOnEcbModeExitResponse(Handler h);

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link PhoneConstants#LTE_ON_CDMA_UNKNOWN}, {@link PhoneConstants#LTE_ON_CDMA_FALSE}
     * or {@link PhoneConstants#LTE_ON_CDMA_TRUE}
     */
    public int getLteOnCdmaMode();

    /**
     * TODO: Adding a function for each property is not good.
     * A fucntion of type getPhoneProp(propType) where propType is an
     * enum of GSM+CDMA+LTE props would be a better approach.
     *
     * Get "Restriction of menu options for manual PLMN selection" bit
     * status from EF_CSP data, this belongs to "Value Added Services Group".
     * @return true if this bit is set or EF_CSP data is unavailable,
     * false otherwise
     */
    boolean isCspPlmnEnabled();

    /**
     * Return an interface to retrieve the ISIM records for IMS, if available.
     * @return the interface to retrieve the ISIM records, or null if not supported
     */
    IsimRecords getIsimRecords();

    /**
     * Sets the SIM voice message waiting indicator records.
     * @param line GSM Subscriber Profile Number, one-based. Only '1' is supported
     * @param countWaiting The number of messages waiting, if known. Use
     *                     -1 to indicate that an unknown number of
     *                      messages are waiting
     */
    void setVoiceMessageWaiting(int line, int countWaiting);

    /**
     * Gets the USIM service table from the UICC, if present and available.
     * @return an interface to the UsimServiceTable record, or null if not available
     */
    UsimServiceTable getUsimServiceTable();

    /**
     * Gets the Uicc card corresponding to this phone.
     * @return the UiccCard object corresponding to the phone ID.
     */
    UiccCard getUiccCard();

    /**
     * Unregister from all events it registered for and dispose objects
     * created by this object.
     */
    void dispose();

    /**
     * Remove references to external object stored in this object.
     */
    void removeReferences();

    /**
     * Update the phone object if the voice radio technology has changed
     *
     * @param voiceRadioTech The new voice radio technology
     */
    void updatePhoneObject(int voiceRadioTech);

    /**
     * Read one of the NV items defined in {@link RadioNVItems} / {@code ril_nv_items.h}.
     * Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @param response callback message with the String response in the obj field
     */
    void nvReadItem(int itemID, Message response);

    /**
     * Write one of the NV items defined in {@link RadioNVItems} / {@code ril_nv_items.h}.
     * Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @param itemValue the value to write, as a String
     * @param response Callback message.
     */
    void nvWriteItem(int itemID, String itemValue, Message response);

    /**
     * Update the CDMA Preferred Roaming List (PRL) in the radio NV storage.
     * Used for device configuration by some CDMA operators.
     *
     * @param preferredRoamingList byte array containing the new PRL
     * @param response Callback message.
     */
    void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response);

    /**
     * Perform the specified type of NV config reset. The radio will be taken offline
     * and the device must be rebooted after erasing the NV. Used for device
     * configuration by some CDMA operators.
     *
     * @param resetType reset type: 1: reload NV reset, 2: erase NV reset, 3: factory NV reset
     * @param response Callback message.
     */
    void nvResetConfig(int resetType, Message response);

    /*
     * Returns the subscription id.
     */
    public int getSubId();

    /*
     * Returns the phone id.
     */
    public int getPhoneId();

    /**
     * Get P-CSCF address from PCO after data connection is established or modified.
     * @param apnType the apnType, "ims" for IMS APN, "emergency" for EMERGENCY APN
     */
    public String[] getPcscfAddress(String apnType);

    /**
     * Set IMS registration state
     */
    public void setImsRegistrationState(boolean registered);

    /**
     * Return the ImsPhone phone co-managed with this phone
     * @return an instance of an ImsPhone phone
     */
    public Phone getImsPhone();

    /**
     * Start listening for IMS service UP/DOWN events.
     */
    public void startMonitoringImsService();

    /**
     * Release the local instance of the ImsPhone and disconnect from
     * the phone.
     * @return the instance of the ImsPhone phone previously owned
     */
    public ImsPhone relinquishOwnershipOfImsPhone();

    /**
     * Take ownership and wire-up the input ImsPhone
     * @param imsPhone ImsPhone to be used.
     */
    public void acquireOwnershipOfImsPhone(ImsPhone imsPhone);

    /**
     * Return the service state of mImsPhone if it is STATE_IN_SERVICE
     * otherwise return the current voice service state
     */
    int getVoicePhoneServiceState();

    /**
     * Override the service provider name and the operator name for the current ICCID.
     */
    public boolean setOperatorBrandOverride(String brand);

    /**
     * Override the roaming indicator for the current ICCID.
     */
    public boolean setRoamingOverride(List<String> gsmRoamingList,
            List<String> gsmNonRoamingList, List<String> cdmaRoamingList,
            List<String> cdmaNonRoamingList);

    /**
     * Is Radio Present on the device and is it accessible
     */
    public boolean isRadioAvailable();

    /**
     * Is Radio turned on
     */
    public boolean isRadioOn();

    /**
     * shutdown Radio gracefully
     */
    public void shutdownRadio();

    /**
     *  Set phone radio capability
     *
     *  @param rc the phone radio capability defined in
     *         RadioCapability. It's a input object used to transfer parameter to logic modem
     *  @param response Callback message.
     */
    public void setRadioCapability(RadioCapability rc, Message response);

    /**
     *  Get phone radio capability
     *
     *  @return the capability of the radio defined in RadioCapability
     */
    public RadioCapability getRadioCapability();

    /**
     *  Get phone radio access family
     *
     *  @return a bit mask to identify the radio access family.
     */
    public int getRadioAccessFamily();

    /**
     *  Get the associated data modems Id.
     *
     *  @return a String containing the id of the data modem
     */
    public String getModemUuId();

    /**
     *  The RadioCapability has changed. This comes up from the RIL and is called when radios first
     *  become available or after a capability switch.  The flow is we use setRadioCapability to
     *  request a change with the RIL and get an UNSOL response with the new data which gets set
     *  here.
     *
     *  @param rc the phone radio capability currently in effect for this phone.
     */
    public void radioCapabilityUpdated(RadioCapability rc);

    /**
     * Registers the handler when phone radio  capability is changed.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForRadioCapabilityChanged(Handler h, int what, Object obj);

    /**
     * Unregister for notifications when phone radio type and access technology is changed.
     *
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForRadioCapabilityChanged(Handler h);

    /**
     * Query the IMS Registration Status.
     *
     * @return true if IMS is Registered
     */
    public boolean isImsRegistered();

    /**
     * Determines if video calling is enabled for the phone.
     *
     * @return {@code true} if video calling is enabled, {@code false} otherwise.
     */
    public boolean isVideoEnabled();

    /**
     * @return {@code true} if we are in emergency call back mode. This is a period where the phone
     * should be using as little power as possible and be ready to receive an incoming call from the
     * emergency operator.
     */
    public boolean isInEcm();

    /**
     * Returns the Status of Wi-Fi Calling
     *@hide
     */
    public boolean isWifiCallingEnabled();

     /**
     * Returns the Status of Volte
     *@hide
     */
    public boolean isVolteEnabled();

    /**
     * @return {@code true} if video call is present, false otherwise.
     */
    public boolean isVideoCallPresent();

    /**
     * Returns the status of Link Capacity Estimation (LCE) service.
     */
    public int getLceStatus();

    /**
     * Returns the locale based on the carrier properties (such as {@code ro.carrier}) and
     * SIM preferences.
     */
    public Locale getLocaleFromSimAndCarrierPrefs();

    /**
     * Returns the modem activity information
     */
    public void getModemActivityInfo(Message response);

    /// M: CC010: Add RIL interface @{
    /**
     * Register for Supplementary Service CRSS notifications from the network.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a SuppCrssNotification instance.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     * @internal
     */
    public void registerForCrssSuppServiceNotification(Handler h, int what, Object obj);

    /**
     * Unregisters for Supplementary Service CRSS notifications.
     * Extraneous calls are tolerated silently
     *
     * @param h Handler to be removed from the registrant list.
     * @internal
     */
    public void unregisterForCrssSuppServiceNotification(Handler h);

    /**
     * Register for notifications of EAIC URC.
     * Message.obj will contain an AsyncResult.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     * @internal
     */
    public void registerForVoiceCallIncomingIndication(Handler h, int what, Object obj);

    /**
     * Unregister for notifications of EAIC URC
     * @param h Handler to be removed from the registrant list.
     * @internal
     */
    public void unregisterForVoiceCallIncomingIndication(Handler h);

    public void registerForCipherIndication(Handler h, int what, Object obj);
    public void unregisterForCipherIndication(Handler h);
    /// @}

    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    /**
     * Register for notifications for speech codec info.
     * Message.obj will contain an AsyncResult.
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSpeechCodecInfo(Handler h, int what, Object obj);

    /**
     * Unregister for notifications for speech codec info
     * @param h Handler to be removed from the registrant list.
     */
    void unregisterForSpeechCodecInfo(Handler h);
    /// @}

    /// M: CC010: Add RIL interface @{
    /**
     * used to release all connections in the MS,
     * release all connections with one reqeust together, not seperated.
     * @internal
     */
    void hangupAll() throws CallStateException;
    /// @}

    /// M: For 3G VT only @{
    /**
     * Register for VT status indication.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a int[ ] instance
     *
     */
    void registerForVtStatusInfo(Handler h, int what, Object obj);

    /**
     * Unregister for VT status indication.
     *
     */
    void unregisterForVtStatusInfo(Handler h);
    /// @}

    // Added by M begin

    // ALPS00302702 RAT balancing
    int getEfRatBalancing();

    // MVNO-API START
    String getMvnoMatchType();
    String getMvnoPattern(String type);
    // MVNO-API END

    /**
     *send BT SAP profile
     *
     * @param nAction 0: requestConnectSIM,
     *                1: requestDisconnectOrPowerOffSIM
     *                2: requestPowerOnOrResetSIM
     *                3: requestDisconnectOrPowerOffSIM
     *                4: requestPowerOnOrResetSIM
     *                5: requestTransferApdu
     * @param nType only used when nAction is 2, 3, 4, 5
     * @param strData only used when nAction is 5
     * @param response a callback message when the action is completed.
     *
     * @internal
     */
    void sendBtSimProfile(int nAction, int nType, String strData, Message response);

    //MTK-START [mtk04070][111117][ALPS00093395]MTK added
    enum IccServiceStatus {
        NOT_EXIST_IN_SIM,
        NOT_EXIST_IN_USIM,
        ACTIVATED,
        INACTIVATED,
        UNKNOWN;
    }

    enum IccService {
        CHV1_DISABLE_FUNCTION,      //(0)CHV1 disable function (SIM only)
        SPN,                        //(1)Service Provider Name
        PNN,                        //(2)PLMN Network Name
        OPL,                        //(3)Operator PLMN List
        MWIS,                       //(4)Message Waiting Indication Status
        CFIS,                       //(5)Call Forwarding Indication Status
        SPDI,                       //(6)Service Provider Display Information
        EPLMN,                      //(7)Equivalent HPLMN (USIM only)
        SMSP,                       //(8)[ALPS01206315]Short Message Service Parameters
        FDN,                        //(9)FDN
        UNSUPPORTED_SERVICE;        //(10)

        public int getIndex() {
            int nIndex = -1;
        switch(this) {
        case CHV1_DISABLE_FUNCTION:
            nIndex = 0;
            break;
        case SPN:
            nIndex = 1;
            break;
        case PNN:
            nIndex = 2;
            break;
        case OPL:
            nIndex = 3;
            break;
        case MWIS:
            nIndex = 4;
            break;
        case CFIS:
            nIndex = 5;
            break;
        case SPDI:
            nIndex = 6;
            break;
        case EPLMN:
            nIndex = 7;
            break;
        case SMSP:
            nIndex = 8;
            break;
        case FDN:
            nIndex = 9;
            break;
        case UNSUPPORTED_SERVICE:
            nIndex = 10;
            break;
        default:
            break;
        }
            return nIndex;
        }
    }
    //MTK-END [mtk04070][111117][ALPS00093395]MTK added

    /**
     * Request 3G/4G context authentication, including AKA and GBA.
     */
    void doGeneralSimAuthentication(int sessionId, int mode, int tag,
            String param1, String param2, Message result);

    /**
     * Request the information of the given storage type
     *
     * @param type
     *          the type of the storage, refer to PHB_XDN defined in the RilConstants
     * @param response
     *          Callback message
     *          response.obj.result is an int[4]
     *          response.obj.result[0] is number of current used entries
     *          response.obj.result[1] is number of total entries in the storage
     *          response.obj.result[2] is maximum supported length of the number
     *          response.obj.result[3] is maximum supported length of the alphaId
     */
    void queryPhbStorageInfo(int type, Message response);
    // Added by M end

// M: network part START
    /**
     * Cancel scan available networks. This method is asynchronous; .
     */
    void cancelAvailableNetworks(Message response);


    void setNetworkSelectionModeSemiAutomatic(OperatorInfo network, Message response);

    /**
     * Register for Neighboring cell info changed.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a String[ ] instance
     */
    void registerForNeighboringInfo(Handler h, int what, Object obj);

    /**
     * Unregisters for Neighboring cell info changed notification.
     * Extraneous calls are tolerated silently
     */
    void unregisterForNeighboringInfo(Handler h);

    /**
     * Register for Network info changed.
     * Message.obj will contain an AsyncResult.
     * AsyncResult.result will be a String[ ] instance
     */
    void registerForNetworkInfo(Handler h, int what, Object obj);

    /**
     * Unregisters for Network info changed notification.
     * Extraneous calls are tolerated silently
     */
    void unregisterForNetworkInfo(Handler h);

    /**
     * Refresh Spn Display due to configuration change
     @internal
     */
    void refreshSpnDisplay();

  /** Get Network hided state
     * @internal
     */
    int getNetworkHideState();

  /**
     * Returns current located PLMN string(ex: "46000") or null if not availble (ex: in flight mode or no signal area)
     * @internal
     */
    String getLocatedPlmn();

 // Femtocell (CSG) feature START
 /**
     * Scan available femtocells. This method is asynchronous; .
     * On completion, <code>response.obj</code> is set to an AsyncResult with
     * one of the following members:.<p>
     *<ul>
     * <li><code>response.obj.result</code> will be a <code>List</code> of
     * <code>FemtoCellInfo</code> objects, or</li>
     * <li><code>response.obj.exception</code> will be set with an exception
     * on failure.</li>
     * </ul>
     * @internal
     */
    void getFemtoCellList(String operatorNumeric, int rat, Message response);


 /**
     * Abort scaning femtocell list. <code>response</code> is
     * dispatched when this is complete.  <code>response.obj</code> will be
     * an AsyncResult, and <code>response.obj.exception</code> will be non-null
     * on failure.
     * @param response is callback message
     * @internal
     */
    void abortFemtoCellList(Message response);

 /**
     * Manually selects a femtocell. <code>response</code> is
     * dispatched when this is complete.  <code>response.obj</code> will be
     * an AsyncResult, and <code>response.obj.exception</code> will be non-null
     * on failure.
     * @param femtocell is the specified femtocell to be selected
     * @param response is callback message
     * @internal
     */
    void selectFemtoCell(FemtoCellInfo femtocell, Message response);
    // Femtocell (CSG) feature END

    /** Get POL capability.
     *
     * @param onComplete a callback message when the action is completed.
     *
     * @internal
     */
    void getPolCapability(Message onComplete);

    /** Get Prefered operator list.
     *
     * @param onComplete a callback message when the action is completed.
     *
     * @internal
     */
    void getPol(Message onComplete);

    /** Set POL entry.
     *
     * @param networkWithAct network infor with act.
     * @param onComplete a callback message when the action is completed.
     *
     * @internal
     */
    void setPolEntry(NetworkInfoWithAcT networkWithAct, Message onComplete);

    DcFailCause getLastDataConnectionFailCause(String apnType);

//M: network part END

    /// M: IMS feature. @{
    /**
     * Register for notify IMS conference call indication.
     * Message.obj will contain an AsyncResult.
     */
    public void registerForImsConferenceCallNotification(Handler h, int what, Object obj);

    /**
     * Unregister for IMS conference call indication.
     */
    public void unregisterForImsConferenceCallNotification(Handler h);

    /**
     * Add new participant to IMS conference call.
     */
    public void addConferenceMember(int confCallId, String address, int callIdToAdd) throws CallStateException;

    /**
     * Initiate a new conference host connection.
     *
     * @param numbers The dial numbers.
     * @param videoState The desired video state for the connection.
     * @exception CallStateException if a new outgoing call is not currently possible because
     * no more call slots exist or a call exists that is dialing, alerting, ringing, or waiting.
     * Other errors are handled asynchronously.
     * @return Connection the MO connection.
     * @hide
     */
    public Connection dial(List<String> numbers, int videoState) throws CallStateException;
    /// @}

    /// M: c2k modify, phone interface. @{

    /**
     * Request to switch HPF.
     * @param enableHPF if enable the HPF
     * @param response the responding message
     */
    void requestSwitchHPF(boolean enableHPF, Message response);
    /**
     * Set CDMA avoid list.
     * @param avoidSYS the avoid list enabled
     * @param response the responding message
     */
    void setAvoidSYS(boolean avoidSYS, Message response);
    /**
     * Get CDMA avoid list.
     * @param response the responding message
     */
    void getAvoidSYSList(Message response);
    /**
     * Query CDMA network infomation.
     * @param response the responding message
     */
    void queryCDMANetworkInfo(Message response);

    /// @}

    /// M: [C2K][SVLTE] Support modem remote SIM access.
    /**
     * Configure the modem status.
     * @param modemStatus the modem status parameter.
     * @param remoteSimProtocol the remote SIM protocal.
     * @param result the responding message.
     */
    public void configModemStatus(int modemStatus, int remoteSimProtocol, Message result);

    /// M: [C2K] for eng mode start
    /**
     * M: Rregister on network information for eng mode.
     * @param h Handler for network information messages.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForEngModeNetworkInfo(Handler h, int what, Object obj);

    /**
     * M: Unrregister on network information for eng mode.
     * @param h Handler for network information messages.
     */
    void unregisterForEngModeNetworkInfo(Handler h);
    /// M: [C2K] for eng mode end

    /**
     * M: For SVLTE to update phone Id.
     * @param phoneId The new phone Id.
     */
    void setPhoneId(int phoneId);

    /**
     * Register for CDMA call really be accepted.
     *
     * @param h the handler which listen the changed.
     * @param what the message's what value.
     * @param obj the message's obj value.
     */
    void registerForCdmaCallAccepted(Handler h, int what, Object obj);

    /**
     * Unregister for CDMA call really be accepted.
     *
     * @param h the handler which listen the changed.
     */
    void unregisterForCdmaCallAccepted(Handler h);
    ///M: Add for SVLTE. @{
    /**
     * Register for SVLTE ServiceState changed.
     * @param h the handler which listen the changed.
     * @param what the message's what value.
     * @param obj the message's obj value.
     */
    void registerForSvlteServiceStateChanged(Handler h, int what, Object obj);

    /**
     * Unregisters for SVLTE ServiceStateChange notification.
     *
     * @param h the handler which listen the changed.
     */
    void unregisterForSvlteServiceStateChanged(Handler h);
    /**
     * Get the svlte ServiceState. Use
     * <code>registerForSvlteServiceStateChanged</code> to be informed of
     * updates.
     * @return the svlte service state.
     */
    ServiceState getSvlteServiceState();
    ///@}

    /**
     * Update RAF in phoneBase.
     *
     * @param radioAccessFamily the new RAF.
     */
    public void setRadioAccessFamily(int radioAccessFamily);

    /// M: ALPS01953873. @{

    /**
     * Define feature type provided by MTK. Queried by TelecomAccountRegistry to
     * build the phoneAccount's capability.
     * @hide
     */
    public enum FeatureType {
        // One-key conference, addMember. it will depends on current inserted SIM card.
        VOLTE_ENHANCED_CONFERENCE,

        // Conference remove member. Depends on current inserted SIM card.
        VOLTE_CONF_REMOVE_MEMBER,

        // Only one video call is allowed.
        VIDEO_RESTRICTION

        // ToDo: add more features from here.
    }

    /**
     * Query if currently this phone support the specific feature.
     * @param feature FeatureType.
     * @return true if supporting.
     * @hide
     */
    public boolean isFeatureSupported(FeatureType feature);
    /// @}

    /**
     * Switch antenna.
     * @param callState call state, 0 means call disconnected and 1 means call established.
     * @param ratMode RAT mode, 0 means GSM and 7 means C2K.
     */
    public void switchAntenna(int callState, int ratMode);
}
