/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.internal.telephony.imsphone;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.UserHandle;

import android.preference.PreferenceManager;

import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import com.android.ims.ImsCallForwardInfo;
/// M: SS OP01 Ut
import com.android.ims.ImsCallForwardInfoEx;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsConfig;
import com.android.ims.ImsEcbm;
import com.android.ims.ImsEcbmStateListener;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsSsInfo;
import com.android.ims.ImsUtInterface;

import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOIC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOICxH;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAIC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAICr;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_ALL;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_MO;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_MT;

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_DISABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ERASURE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_REGISTRATION;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REGISTERED;

import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_NONE;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_UT_CFU_NOTIFICATION_MODE;
import static com.android.internal.telephony.TelephonyProperties.UT_CFU_NOTIFICATION_MODE_DISABLED;
import static com.android.internal.telephony.TelephonyProperties.UT_CFU_NOTIFICATION_MODE_ON;
import static com.android.internal.telephony.TelephonyProperties.UT_CFU_NOTIFICATION_MODE_OFF;


import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
/// M: SS OP01 Ut
import com.android.internal.telephony.CallForwardInfoEx;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SuppSrvRequest;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.IccRecords;

/// M: ALPS01953873. @{
import java.util.Arrays;
/// @}
import java.util.ArrayList;
import java.util.List;

/**
 * {@hide}
 */
public class ImsPhone extends ImsPhoneBase {
    private static final String LOG_TAG = "ImsPhone";
    private static final boolean DBG = true;
    private static final boolean VDBG = false; // STOPSHIP if true

    protected static final int EVENT_SET_CALL_BARRING_DONE          = EVENT_LAST + 1;
    protected static final int EVENT_GET_CALL_BARRING_DONE          = EVENT_LAST + 2;
    protected static final int EVENT_SET_CALL_WAITING_DONE          = EVENT_LAST + 3;
    protected static final int EVENT_GET_CALL_WAITING_DONE          = EVENT_LAST + 4;
    protected static final int EVENT_SET_CLIR_DONE                  = EVENT_LAST + 5;
    protected static final int EVENT_GET_CLIR_DONE                  = EVENT_LAST + 6;
    protected static final int EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED  = EVENT_LAST + 7;

    public static final String CS_FALLBACK = "cs_fallback";
    public static final String UT_BUNDLE_KEY_CLIR = "queryClir";

    /// M: @{
    /**
     * Used as the message in CallStateException.
     * We don't support dialing a USSD number while there is an existing IMS call.
     */
    public static final String USSD_DURING_IMS_INCALL = "ussd_during_ims_incall";
    /// @}

    public static final String EXTRA_KEY_ALERT_TITLE = "alertTitle";
    public static final String EXTRA_KEY_ALERT_MESSAGE = "alertMessage";
    public static final String EXTRA_KEY_ALERT_SHOW = "alertShow";
    public static final String EXTRA_KEY_NOTIFICATION_MESSAGE = "notificationMessage";

    static final int RESTART_ECM_TIMER = 0; // restart Ecm timer
    static final int CANCEL_ECM_TIMER = 1; // cancel Ecm timer

    // Default Emergency Callback Mode exit timer
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;

    // Instance Variables
    PhoneBase mDefaultPhone;
    ImsPhoneCallTracker mCT;
    ArrayList <ImsPhoneMmiCode> mPendingMMIs = new ArrayList<ImsPhoneMmiCode>();

    Registrant mPostDialHandler;
    ServiceState mSS = new ServiceState();

    // To redial silently through GSM or CDMA when dialing through IMS fails
    private String mLastDialString;

    WakeLock mWakeLock;
    protected boolean mIsPhoneInEcmState;

    // mEcmExitRespRegistrant is informed after the phone has been exited the emergency
    // callback mode keep track of if phone is in emergency callback mode
    private Registrant mEcmExitRespRegistrant;

    private final RegistrantList mSilentRedialRegistrants = new RegistrantList();

    private boolean mImsRegistered = false;

    private String mDialString;
    // List of Registrants to send supplementary service notifications to.
    private RegistrantList mSsnRegistrants = new RegistrantList();

    // A runnable which is used to automatically exit from Ecm after a period of time.
    private Runnable mExitEcmRunnable = new Runnable() {
        @Override
        public void run() {
            exitEmergencyCallbackMode();
        }
    };

    // Create Cf (Call forward) so that dialling number &
    // mIsCfu (true if reason is call forward unconditional)
    // mOnComplete (Message object passed by client) can be packed &
    // given as a single Cf object as user data to UtInterface.
    private static class Cf {
        final String mSetCfNumber;
        final Message mOnComplete;
        final boolean mIsCfu;

        Cf(String cfNumber, boolean isCfu, Message onComplete) {
            mSetCfNumber = cfNumber;
            mIsCfu = isCfu;
            mOnComplete = onComplete;
        }
    }

    // Constructors

    ImsPhone(Context context, PhoneNotifier notifier, Phone defaultPhone) {
        super("ImsPhone", context, notifier);

        mDefaultPhone = (PhoneBase) defaultPhone;
        /// M: ALPS02759855. ImsPhoneCallTracker may change service state earlier. @{
        // mCT = new ImsPhoneCallTracker(this);
        // mSS.setStateOff();
        mSS.setStateOff();
        mCT = new ImsPhoneCallTracker(this);
        /// @}

        mPhoneId = mDefaultPhone.getPhoneId();

        // This is needed to handle phone process crashes
        // Same property is used for both CDMA & IMS phone.
        mIsPhoneInEcmState = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_INECM_MODE, false);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG);
        mWakeLock.setReferenceCounted(false);

        if (mDefaultPhone.getServiceStateTracker() != null) {
            mDefaultPhone.getServiceStateTracker()
                    .registerForDataRegStateOrRatChanged(this,
                    EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED, null);
        }
        updateDataServiceState();
    }

    public void updateParentPhone(PhoneBase parentPhone) {
        // synchronization is managed at the PhoneBase scope (which calls this function)
        if (mDefaultPhone != null && mDefaultPhone.getServiceStateTracker() != null) {
            mDefaultPhone.getServiceStateTracker().
                    unregisterForDataRegStateOrRatChanged(this);
        }
        mDefaultPhone = parentPhone;
        mPhoneId = mDefaultPhone.getPhoneId();
        if (mDefaultPhone.getServiceStateTracker() != null) {
            mDefaultPhone.getServiceStateTracker()
                    .registerForDataRegStateOrRatChanged(this,
                    EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED, null);
        }
        updateDataServiceState();

        // When the parent phone is updated, we need to notify listeners of the cached video
        // capability.
        Rlog.d(LOG_TAG, "updateParentPhone - Notify video capability changed " + mIsVideoCapable);
        notifyForVideoCapabilityChanged(mIsVideoCapable);
    }

    @Override
    public void dispose() {
        Rlog.d(LOG_TAG, "dispose");

        // Nothing to dispose in PhoneBase
        //super.dispose();
        mPendingMMIs.clear();
        mCT.dispose();

        //Force all referenced classes to unregister their former registered events
        if (mDefaultPhone != null && mDefaultPhone.getServiceStateTracker() != null) {
            mDefaultPhone.getServiceStateTracker().
                    unregisterForDataRegStateOrRatChanged(this);
        }
    }

    @Override
    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        super.removeReferences();

        mCT = null;
        mSS = null;
    }

    @Override
    public ServiceState
    getServiceState() {
        return mSS;
    }

    /* package */ void setServiceState(int state) {
        mSS.setState(state);
        updateDataServiceState();
    }

    @Override
    public CallTracker getCallTracker() {
        return mCT;
    }

    @Override
    public List<? extends ImsPhoneMmiCode>
    getPendingMmiCodes() {
        return mPendingMMIs;
    }


    @Override
    public void
    acceptCall(int videoState) throws CallStateException {
        mCT.acceptCall(videoState);
    }

    @Override
    public void
    rejectCall() throws CallStateException {
        mCT.rejectCall();
    }

    @Override
    public void
    switchHoldingAndActive() throws CallStateException {
        mCT.switchWaitingOrHoldingAndActive();
    }

    @Override
    public boolean canConference() {
        return mCT.canConference();
    }

    public boolean canDial() {
        return mCT.canDial();
    }

    @Override
    public void conference() {
        mCT.conference();
    }

    @Override
    public void clearDisconnected() {
        mCT.clearDisconnected();
    }

    @Override
    public boolean canTransfer() {
        return mCT.canTransfer();
    }

    @Override
    public void explicitCallTransfer() {
        mCT.explicitCallTransfer();
    }

    @Override
    public ImsPhoneCall
    getForegroundCall() {
        return mCT.mForegroundCall;
    }

    @Override
    public ImsPhoneCall
    getBackgroundCall() {
        return mCT.mBackgroundCall;
    }

    @Override
    public ImsPhoneCall
    getRingingCall() {
        return mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (getRingingCall().getState() != ImsPhoneCall.State.IDLE) {
            if (DBG) Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                mCT.rejectCall();
            } catch (CallStateException e) {
                if (DBG) Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(Phone.SuppService.REJECT);
            }
        } else if (getBackgroundCall().getState() != ImsPhoneCall.State.IDLE) {
            if (DBG) Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
            try {
                mCT.hangup(getBackgroundCall());
            } catch (CallStateException e) {
                if (DBG) Rlog.d(LOG_TAG, "hangup failed", e);
            }
        }

        return true;
    }


    private boolean handleCallWaitingIncallSupplementaryService(
            String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        ImsPhoneCall call = getForegroundCall();

        try {
            if (len > 1) {
                if (DBG) Rlog.d(LOG_TAG, "not support 1X SEND");
                notifySuppServiceFailed(Phone.SuppService.HANGUP);
            } else {
                if (call.getState() != ImsPhoneCall.State.IDLE) {
                    if (DBG) Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
                    mCT.hangup(call);
                } else {
                    if (DBG) Rlog.d(LOG_TAG, "MmiCode 1: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            }
        } catch (CallStateException e) {
            if (DBG) Rlog.d(LOG_TAG, "hangup failed", e);
            notifySuppServiceFailed(Phone.SuppService.HANGUP);
        }

        return true;
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        ImsPhoneCall call = getForegroundCall();

        if (len > 1) {
            if (DBG) Rlog.d(LOG_TAG, "separate not supported");
            notifySuppServiceFailed(Phone.SuppService.SEPARATE);
        } else {
            try {
                if (getRingingCall().getState() != ImsPhoneCall.State.IDLE) {
                    if (DBG) Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                    mCT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
                } else {
                    if (DBG) Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            } catch (CallStateException e) {
                if (DBG) Rlog.d(LOG_TAG, "switch failed", e);
                notifySuppServiceFailed(Phone.SuppService.SWITCH);
            }
        }

        return true;
    }

    private boolean handleMultipartyIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (DBG) Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {

        int len = dialString.length();

        if (len != 1) {
            return false;
        }

        if (DBG) Rlog.d(LOG_TAG, "MmiCode 4: not support explicit call transfer");
        notifySuppServiceFailed(Phone.SuppService.TRANSFER);
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        // Treat it as an "unknown" service.
        notifySuppServiceFailed(Phone.SuppService.UNKNOWN);
        return true;
    }

    public void notifySuppSvcNotification(SuppServiceNotification suppSvc) {
        Rlog.d(LOG_TAG, "notifySuppSvcNotification: suppSvc = " + suppSvc);

        AsyncResult ar = new AsyncResult(null, suppSvc, null);
        mSsnRegistrants.notifyRegistrants(ar);
    }

    @Override
    public boolean handleInCallMmiCommands(String dialString) {
        if (!isInCall()) {
            return false;
        }

        if (TextUtils.isEmpty(dialString)) {
            return false;
        }

        boolean result = false;
        char ch = dialString.charAt(0);
        switch (ch) {
            case '0':
                result = handleCallDeflectionIncallSupplementaryService(
                        dialString);
                break;
            case '1':
                result = handleCallWaitingIncallSupplementaryService(
                        dialString);
                break;
            case '2':
                result = handleCallHoldIncallSupplementaryService(dialString);
                break;
            case '3':
                result = handleMultipartyIncallSupplementaryService(dialString);
                break;
            case '4':
                result = handleEctIncallSupplementaryService(dialString);
                break;
            case '5':
                result = handleCcbsIncallSupplementaryService(dialString);
                break;
            default:
                break;
        }

        return result;
    }

    /// M: for USSD over IMS workaround. @{
    private boolean isUssdDuringInCall(ImsPhoneMmiCode mmi) {
        if (mmi == null || !mmi.isUssdNumber()) {
            return false;
        }

        return isInCall();
    }
    /// @}

    boolean isInCall() {
        ImsPhoneCall.State foregroundCallState = getForegroundCall().getState();
        ImsPhoneCall.State backgroundCallState = getBackgroundCall().getState();
        ImsPhoneCall.State ringingCallState = getRingingCall().getState();

       return (foregroundCallState.isAlive() ||
               backgroundCallState.isAlive() ||
               ringingCallState.isAlive());
    }

    void notifyNewRingingConnection(Connection c) {
        mDefaultPhone.notifyNewRingingConnectionP(c);
    }

    public static void checkWfcWifiOnlyModeBeforeDial(ImsPhone imsPhone, Context context)
            throws CallStateException {
        if (imsPhone == null ||
                !imsPhone.isVowifiEnabled()) {
            boolean wfcWiFiOnly = (ImsManager.isWfcEnabledByPlatform(context) &&
                    ImsManager.isWfcEnabledByUser(context) &&
                    (ImsManager.getWfcMode(context) ==
                            ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY));
            if (wfcWiFiOnly) {
                throw new CallStateException(
                        CallStateException.ERROR_DISCONNECTED,
                        "WFC Wi-Fi Only Mode: IMS not registered");
            }
        }
    }

    void notifyUnknownConnection(Connection c) {
        mDefaultPhone.notifyUnknownConnectionP(c);
    }

    public void notifyForVideoCapabilityChanged(boolean isVideoCapable) {
        mIsVideoCapable = isVideoCapable;
        mDefaultPhone.notifyForVideoCapabilityChanged(isVideoCapable);
    }

    @Override
    public Connection
    dial(String dialString, int videoState) throws CallStateException {
        return dialInternal(dialString, videoState, null);
    }

    @Override
    public Connection
    dial(String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras)
            throws CallStateException {
        // ignore UUSInfo
        return dialInternal (dialString, videoState, intentExtras);
    }

    protected Connection dialInternal(String dialString, int videoState, Bundle intentExtras)
            throws CallStateException {
        /// M: Ignore stripping for VoLTE SIP uri. @{
        String newDialString = dialString;
        if (!PhoneNumberUtils.isUriNumber(dialString)) {
            // Need to make sure dialString gets parsed properly
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        }
        /// @}

        // handle in-call MMI first if applicable
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }

        if (mDefaultPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            return mCT.dial(dialString, videoState, intentExtras);
        }

        /// M: Ignore extracting for VoLTE SIP uri. @{
        String networkPortion = dialString;
        if (!PhoneNumberUtils.isUriNumber(dialString)) {
            // Only look at the Network portion for mmi
            networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        }
        /// @}
        ImsPhoneMmiCode mmi =
                ImsPhoneMmiCode.newFromDialString(networkPortion, this);
        if (DBG) Rlog.d(LOG_TAG,
                "dialing w/ mmi '" + mmi + "'...");

        /// M: If there is an existing call, CS_FB will cause call end. So just ignore it. @{
        if (isUssdDuringInCall(mmi)) {
            if (DBG) {
                Rlog.d(LOG_TAG, "USSD during in-call, ignore this operation!");
            }
            throw new CallStateException(USSD_DURING_IMS_INCALL);
        }
        /// @}

        mDialString = dialString;
        if (mmi == null) {
            return mCT.dial(dialString, videoState, intentExtras);
        } else if (mmi.isTemporaryModeCLIR()) {
            return mCT.dial(mmi.getDialingNumber(), mmi.getCLIRMode(), videoState, intentExtras);
        } else if (!mmi.isSupportedOverImsPhone()) {
            if (DBG) {
                Rlog.d(LOG_TAG, "MMI not supported over ImsPhone()");
            }
            // If the mmi is not supported by IMS service,
            // try to initiate dialing with default phone
            throw new CallStateException(CS_FALLBACK);
        } else {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();

            return null;
        }
    }

    @Override
    public void
    sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                    "sendDtmf called with invalid character '" + c + "'");
        } else {
            if (mCT.mState ==  PhoneConstants.State.OFFHOOK) {
                mCT.sendDtmf(c, null);
            }
        }
    }

    @Override
    public void
    startDtmf(char c) {
        if (!(PhoneNumberUtils.is12Key(c) || (c >= 'A' && c <= 'D'))) {
            Rlog.e(LOG_TAG,
                    "startDtmf called with invalid character '" + c + "'");
        } else {
            mCT.startDtmf(c);
        }
    }

    @Override
    public void
    stopDtmf() {
        mCT.stopDtmf();
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mPostDialHandler = new Registrant(h, what, obj);
    }

    /*package*/ void notifyIncomingRing() {
        if (DBG) Rlog.d(LOG_TAG, "notifyIncomingRing");
        AsyncResult ar = new AsyncResult(null, null, null);
        sendMessage(obtainMessage(EVENT_CALL_RING, ar));
    }

    @Override
    public void setMute(boolean muted) {
        mCT.setMute(muted);
    }

    @Override
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        mCT.setUiTTYMode(uiTtyMode, onComplete);
    }

    @Override
    public boolean getMute() {
        return mCT.getMute();
    }

    @Override
    public PhoneConstants.State getState() {
        return mCT.mState;
    }

    private boolean isValidCommandInterfaceCFReason (int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
        case CF_REASON_UNCONDITIONAL:
        case CF_REASON_BUSY:
        case CF_REASON_NO_REPLY:
        case CF_REASON_NOT_REACHABLE:
        case CF_REASON_ALL:
        case CF_REASON_ALL_CONDITIONAL:
        case CF_REASON_NOT_REGISTERED:
            return true;
        default:
            return false;
        }
    }

    private boolean isValidCommandInterfaceCFAction (int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
        case CF_ACTION_DISABLE:
        case CF_ACTION_ENABLE:
        case CF_ACTION_REGISTRATION:
        case CF_ACTION_ERASURE:
            return true;
        default:
            return false;
        }
    }

    private  boolean isCfEnable(int action) {
        return (action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION);
    }

    private int getConditionFromCFReason(int reason) {
        switch(reason) {
            case CF_REASON_UNCONDITIONAL: return ImsUtInterface.CDIV_CF_UNCONDITIONAL;
            case CF_REASON_BUSY: return ImsUtInterface.CDIV_CF_BUSY;
            case CF_REASON_NO_REPLY: return ImsUtInterface.CDIV_CF_NO_REPLY;
            case CF_REASON_NOT_REACHABLE: return ImsUtInterface.CDIV_CF_NOT_REACHABLE;
            case CF_REASON_ALL: return ImsUtInterface.CDIV_CF_ALL;
            case CF_REASON_ALL_CONDITIONAL: return ImsUtInterface.CDIV_CF_ALL_CONDITIONAL;
            case CF_REASON_NOT_REGISTERED: return ImsUtInterface.CDIV_CF_NOT_LOGGED_IN;
            default:
                break;
        }

        return ImsUtInterface.INVALID;
    }

    private int getCFReasonFromCondition(int condition) {
        switch(condition) {
            case ImsUtInterface.CDIV_CF_UNCONDITIONAL: return CF_REASON_UNCONDITIONAL;
            case ImsUtInterface.CDIV_CF_BUSY: return CF_REASON_BUSY;
            case ImsUtInterface.CDIV_CF_NO_REPLY: return CF_REASON_NO_REPLY;
            case ImsUtInterface.CDIV_CF_NOT_REACHABLE: return CF_REASON_NOT_REACHABLE;
            case ImsUtInterface.CDIV_CF_ALL: return CF_REASON_ALL;
            case ImsUtInterface.CDIV_CF_ALL_CONDITIONAL: return CF_REASON_ALL_CONDITIONAL;
            case ImsUtInterface.CDIV_CF_NOT_LOGGED_IN: return CF_REASON_NOT_REGISTERED;
            default:
                break;
        }

        return CF_REASON_NOT_REACHABLE;
    }

    private int getActionFromCFAction(int action) {
        switch(action) {
            case CF_ACTION_DISABLE: return ImsUtInterface.ACTION_DEACTIVATION;
            case CF_ACTION_ENABLE: return ImsUtInterface.ACTION_ACTIVATION;
            case CF_ACTION_ERASURE: return ImsUtInterface.ACTION_ERASURE;
            case CF_ACTION_REGISTRATION: return ImsUtInterface.ACTION_REGISTRATION;
            default:
                break;
        }

        return ImsUtInterface.INVALID;
    }

    /*
    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "getCLIR");
        Message resp;
        resp = obtainMessage(EVENT_GET_CLIR_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.queryCLIR(resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    @Override
    public void setOutgoingCallerIdDisplay(int clirMode, Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "setCLIR action= " + clirMode);
        Message resp;
        resp = obtainMessage(EVENT_SET_CLIR_DONE, onComplete);
        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.updateCLIR(clirMode, resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }
    */

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason,
            Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "getCallForwardingOption reason=" + commandInterfaceCFReason);
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (DBG) Rlog.d(LOG_TAG, "requesting call forwarding query.");

            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                ((GSMPhone) mDefaultPhone).setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                        UT_CFU_NOTIFICATION_MODE_DISABLED);
            }

            Message resp;
            resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);

            try {
                ImsUtInterface ut = mCT.getUtInterface();
                ut.queryCallForward(getConditionFromCFReason(commandInterfaceCFReason),null,resp);
            } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber,
                CommandsInterface.SERVICE_CLASS_VOICE, timerSeconds, onComplete);
    }

    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int serviceClass,
            int timerSeconds,
            Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "setCallForwardingOption action=" + commandInterfaceCFAction
                + ", reason=" + commandInterfaceCFReason + " serviceClass=" + serviceClass);
        if ((isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {
            Message resp;

            // + [ALPS02301009]
            if (dialingNumber == null || dialingNumber.isEmpty()) {
                if (mDefaultPhone != null &&
                    mDefaultPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                    if (((GSMPhone) mDefaultPhone).isOp07IccCard()) {
                        if (isCfEnable(commandInterfaceCFAction)) {
                            String getNumber = getPreviousDialNumber(commandInterfaceCFReason);

                            if (getNumber != null && !getNumber.isEmpty()) {
                                dialingNumber = getNumber;
                            }
                        }
                    }
                }
            }
            // - [ALPS02301009]

            Cf cf = new Cf(dialingNumber,
                    (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL ? true : false),
                    onComplete);
            resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                    commandInterfaceCFAction, commandInterfaceCFReason, cf);

            try {
                ImsUtInterface ut = mCT.getUtInterface();
                ut.updateCallForward(getActionFromCFAction(commandInterfaceCFAction),
                        getConditionFromCFReason(commandInterfaceCFReason),
                        dialingNumber,
                        serviceClass,
                        timerSeconds,
                        resp);
             } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
             }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "getCallWaiting");
        Message resp;
        resp = obtainMessage(EVENT_GET_CALL_WAITING_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.queryCallWaiting(resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    public void setCallWaiting(boolean enable, int serviceClass, Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "setCallWaiting enable=" + enable);
        Message resp;
        resp = obtainMessage(EVENT_SET_CALL_WAITING_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.updateCallWaiting(enable, serviceClass, resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    private int getCBTypeFromFacility(String facility) {
        if (CB_FACILITY_BAOC.equals(facility)) {
            return ImsUtInterface.CB_BAOC;
        } else if (CB_FACILITY_BAOIC.equals(facility)) {
            return ImsUtInterface.CB_BOIC;
        } else if (CB_FACILITY_BAOICxH.equals(facility)) {
            return ImsUtInterface.CB_BOIC_EXHC;
        } else if (CB_FACILITY_BAIC.equals(facility)) {
            return ImsUtInterface.CB_BAIC;
        } else if (CB_FACILITY_BAICr.equals(facility)) {
            return ImsUtInterface.CB_BIC_WR;
        } else if (CB_FACILITY_BA_ALL.equals(facility)) {
            return ImsUtInterface.CB_BA_ALL;
        } else if (CB_FACILITY_BA_MO.equals(facility)) {
            return ImsUtInterface.CB_BA_MO;
        } else if (CB_FACILITY_BA_MT.equals(facility)) {
            return ImsUtInterface.CB_BA_MT;
        }

        return 0;
    }

    /**
     * Get Call Barring State.
     * @param facility the call barring method
     * @param onComplete message callback
     */
    /* package */ public void getCallBarring(String facility, Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "getCallBarring facility=" + facility);
        Message resp;
        resp = obtainMessage(EVENT_GET_CALL_BARRING_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.queryCallBarring(getCBTypeFromFacility(facility), resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    /**
     * Set Call Barring State.
     * @param facility the call barring method
     * @param lockState activation flag
     * @param password password
     * @param onComplete message callback
     */
    /* package */ public void setCallBarring(String facility, boolean lockState, String password,
            Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "setCallBarring facility=" + facility
                + ", lockState=" + lockState);
        Message resp;
        resp = obtainMessage(EVENT_SET_CALL_BARRING_DONE, onComplete);

        int action;
        if (lockState) {
            action = CommandsInterface.CF_ACTION_ENABLE;
        }
        else {
            action = CommandsInterface.CF_ACTION_DISABLE;
        }

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            // password is not required with Ut interface
            ut.updateCallBarring(getCBTypeFromFacility(facility), action, resp, null);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        Rlog.d(LOG_TAG, "sendUssdResponse");
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromUssdUserInput(ussdMessge, this);
        mPendingMMIs.add(mmi);
        mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    /* package */
    void sendUSSD (String ussdString, Message response) {
        mCT.sendUSSD(ussdString, response);
    }

    /* package */
    void cancelUSSD() {
        mCT.cancelUSSD();
    }

    /* package */
    void sendErrorResponse(Message onComplete) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null,
                    new CommandException(CommandException.Error.GENERIC_FAILURE));
            onComplete.sendToTarget();
        }
    }

    /* package */
    void sendErrorResponse(Message onComplete, Throwable e) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, getCommandException(e));
            onComplete.sendToTarget();
        }
    }

    /* package */
    void sendErrorResponse(Message onComplete, ImsReasonInfo reasonInfo) {
        Rlog.d(LOG_TAG, "sendErrorResponse reasonCode=" + reasonInfo.getCode());
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, getCommandException(reasonInfo.getCode()));
            onComplete.sendToTarget();
        }
    }

    /* package */
    CommandException getCommandException(int code) {
        return getCommandException(code, null);
    }

    CommandException getCommandException(int code, String errorString) {
        Rlog.d(LOG_TAG, "getCommandException code= " + code
                + ", errorString= " + errorString);
        CommandException.Error error = CommandException.Error.GENERIC_FAILURE;

        switch(code) {
            case ImsReasonInfo.CODE_UT_NOT_SUPPORTED:
                error = CommandException.Error.REQUEST_NOT_SUPPORTED;
                break;
            case ImsReasonInfo.CODE_UT_CB_PASSWORD_MISMATCH:
                error = CommandException.Error.PASSWORD_INCORRECT;
                break;
            case ImsReasonInfo.CODE_UT_SERVICE_UNAVAILABLE:
                error = CommandException.Error.RADIO_NOT_AVAILABLE;
                break;
            case ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN:
                error = CommandException.Error.UT_XCAP_403_FORBIDDEN;
                break;
            case ImsReasonInfo.CODE_UT_UNKNOWN_HOST:
                error = CommandException.Error.UT_UNKNOWN_HOST;
                break;
            case ImsReasonInfo.CODE_UT_XCAP_409_CONFLICT:
                if (((GSMPhone) mDefaultPhone).isEnableXcapHttpResponse409()) {
                    Rlog.d(LOG_TAG, "getCommandException UT_XCAP_409_CONFLICT");
                    error = CommandException.Error.UT_XCAP_409_CONFLICT;
                }
                break;
            default:
                break;
        }

        return new CommandException(error, errorString);
    }

    /* package */
    CommandException getCommandException(Throwable e) {
        CommandException ex = null;

        if (e instanceof ImsException) {
            ex = getCommandException(((ImsException)e).getCode(), e.getMessage());
        } else {
            Rlog.d(LOG_TAG, "getCommandException generic failure");
            ex = new CommandException(CommandException.Error.GENERIC_FAILURE);
        }
        return ex;
    }

    private void
    onNetworkInitiatedUssd(ImsPhoneMmiCode mmi) {
        Rlog.d(LOG_TAG, "onNetworkInitiatedUssd");
        mMmiCompleteRegistrants.notifyRegistrants(
            new AsyncResult(null, mmi, null));
    }

    /* package */
    void onIncomingUSSD (int ussdMode, String ussdMessage) {
        if (DBG) Rlog.d(LOG_TAG, "onIncomingUSSD ussdMode=" + ussdMode);

        boolean isUssdError;
        boolean isUssdRequest;

        isUssdRequest
            = (ussdMode == CommandsInterface.USSD_MODE_REQUEST);

        isUssdError
            = (ussdMode != CommandsInterface.USSD_MODE_NOTIFY
                && ussdMode != CommandsInterface.USSD_MODE_REQUEST);

        ImsPhoneMmiCode found = null;
        for (int i = 0, s = mPendingMMIs.size() ; i < s; i++) {
            if(mPendingMMIs.get(i).isPendingUSSD()) {
                found = mPendingMMIs.get(i);
                break;
            }
        }

        if (found != null) {
            // Complete pending USSD
            if (isUssdError) {
                found.onUssdFinishedError();
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else { // pending USSD not found
            // The network may initiate its own USSD request

            // ignore everything that isnt a Notify or a Request
            // also, discard if there is no message to present
            if (!isUssdError && ussdMessage != null) {
                ImsPhoneMmiCode mmi;
                mmi = ImsPhoneMmiCode.newNetworkInitiatedUssd(ussdMessage,
                        isUssdRequest,
                        ImsPhone.this);
                onNetworkInitiatedUssd(mmi);
            }
        }
    }

    /**
     * Removes the given MMI from the pending list and notifies
     * registrants that it is complete.
     * @param mmi MMI that is done
     */
    /*package*/ void
    onMMIDone(ImsPhoneMmiCode mmi) {
        /* Only notify complete if it's on the pending list.
         * Otherwise, it's already been handled (eg, previously canceled).
         * The exception is cancellation of an incoming USSD-REQUEST, which is
         * not on the list.
         */
        if (mPendingMMIs.remove(mmi) || mmi.isUssdRequest()) {
            mMmiCompleteRegistrants.notifyRegistrants(
                    new AsyncResult(null, mmi, null));
        }
    }

    public ArrayList<Connection> getHandoverConnection() {
        ArrayList<Connection> connList = new ArrayList<Connection>();
        // Add all foreground call connections
        connList.addAll(getForegroundCall().mConnections);
        // Add all background call connections
        connList.addAll(getBackgroundCall().mConnections);
        // Add all background call connections
        connList.addAll(getRingingCall().mConnections);
        if (connList.size() > 0) {
            return connList;
        } else {
            return null;
        }
    }

    public void notifySrvccState(Call.SrvccState state) {
        mCT.notifySrvccState(state);
    }

    /* package */ void
    initiateSilentRedial() {
        String result = mLastDialString;
        AsyncResult ar = new AsyncResult(null, result, null);
        if (ar != null) {
            mSilentRedialRegistrants.notifyRegistrants(ar);
        }
    }

    public void registerForSilentRedial(Handler h, int what, Object obj) {
        mSilentRedialRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSilentRedial(Handler h) {
        mSilentRedialRegistrants.remove(h);
    }

    @Override
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        mSsnRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
        mSsnRegistrants.remove(h);
    }

    @Override
    public int getSubId() {
        return mDefaultPhone.getSubId();
    }

    @Override
    public int getPhoneId() {
        return mDefaultPhone.getPhoneId();
    }

    private IccRecords getIccRecords() {
        return mDefaultPhone.mIccRecords.get();
    }

    private CallForwardInfo getCallForwardInfo(ImsCallForwardInfo info) {
        CallForwardInfo cfInfo = new CallForwardInfo();
        cfInfo.status = info.mStatus;
        cfInfo.reason = getCFReasonFromCondition(info.mCondition);
        cfInfo.serviceClass = info.mServiceClass;
        cfInfo.toa = info.mToA;
        cfInfo.number = info.mNumber;
        cfInfo.timeSeconds = info.mTimeSeconds;
        return cfInfo;
    }

    private CallForwardInfo[] handleCfQueryResult(ImsCallForwardInfo[] infos) {
        CallForwardInfo[] cfInfos = null;

        if (infos != null && infos.length != 0) {
            cfInfos = new CallForwardInfo[infos.length];
        }

        IccRecords r = getIccRecords();
        if (infos == null || infos.length == 0) {
            if (r != null) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                r.setVoiceCallForwardingFlag(1, false, null);
                ((GSMPhone) mDefaultPhone).setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                        UT_CFU_NOTIFICATION_MODE_OFF);
            }
        } else {
            for (int i = 0, s = infos.length; i < s; i++) {
                if (infos[i].mCondition == ImsUtInterface.CDIV_CF_UNCONDITIONAL) {
                    if (r != null) {
                        r.setVoiceCallForwardingFlag(1, (infos[i].mStatus == 1),
                            infos[i].mNumber);
                        String mode = infos[i].mStatus == 1 ?
                            UT_CFU_NOTIFICATION_MODE_ON : UT_CFU_NOTIFICATION_MODE_OFF;

                        ((GSMPhone) mDefaultPhone).setSystemProperty(
                            PROPERTY_UT_CFU_NOTIFICATION_MODE, mode);
                    }
                }
                cfInfos[i] = getCallForwardInfo(infos[i]);
            }
        }

        return cfInfos;
    }

    private int[] handleCbQueryResult(ImsSsInfo[] infos) {
        int[] cbInfos = new int[1];
        cbInfos[0] = infos[0].mStatus;

        return cbInfos;
    }

    private int[] handleCwQueryResult(ImsSsInfo[] infos) {
        int[] cwInfos = new int[2];
        cwInfos[0] = 0;

        if (infos[0].mStatus == 1) {
            cwInfos[0] = 1;
            cwInfos[1] = SERVICE_CLASS_VOICE;
        }

        return cwInfos;
    }

    private void
    sendResponse(Message onComplete, Object result, Throwable e) {
        if (onComplete != null) {
            CommandException ex = null;
            if (e != null) {
                ex = getCommandException(e);
                AsyncResult.forMessage(onComplete, result, ex);
            } else {
                AsyncResult.forMessage(onComplete, result, null);
            }
            onComplete.sendToTarget();
        }
    }

    private void updateDataServiceState() {
        if (mSS != null && mDefaultPhone.getServiceStateTracker() != null
                && mDefaultPhone.getServiceStateTracker().mSS != null) {
            ServiceState ss = mDefaultPhone.getServiceStateTracker().mSS;
            mSS.setDataRegState(ss.getDataRegState());
            mSS.setRilDataRadioTechnology(ss.getRilDataRadioTechnology());
            Rlog.d(LOG_TAG, "updateDataServiceState: defSs = " + ss + " imsSs = " + mSS);
        }
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        Message onComplete;

        if (DBG) Rlog.d(LOG_TAG, "handleMessage what=" + msg.what);
        switch (msg.what) {
            case EVENT_SET_CALL_FORWARD_DONE: {
                IccRecords r = getIccRecords();
                Cf cf = (Cf) ar.userObj;
                int cfAction = msg.arg1;
                int cfReason = msg.arg2;

                int cfEnable = isCfEnable(cfAction) ? 1 : 0;
                CallForwardInfo[] cfInfos = null;

                if (cf.mIsCfu && ar.exception == null && r != null) {
                    if (((GSMPhone) mDefaultPhone).queryCFUAgainAfterSet()
                        && cfReason == CF_REASON_UNCONDITIONAL) {
                        if (ar.result == null) {
                            Rlog.i(LOG_TAG, "arResult is null.");
                        } else {
                            Rlog.d(LOG_TAG, "[EVENT_SET_CALL_FORWARD_DONE check cfinfo.");
                            cfInfos = handleCfQueryResult((ImsCallForwardInfo[]) ar.result);
                        }
                    } else {
                        r.setVoiceCallForwardingFlag(1, cfEnable == 1, cf.mSetCfNumber);
                    }
                }

                // + [ALPS02301009]
                if (mDefaultPhone != null &&
                    mDefaultPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                    if (((GSMPhone) mDefaultPhone).isOp07IccCard()) {
                        if (ar.exception == null) {
                            if (cfEnable == 1) {
                                boolean ret = applySharePreference(cfReason, cf.mSetCfNumber);
                                if (!ret) {
                                    Rlog.d(LOG_TAG, "applySharePreference false.");
                                }
                            }

                            if (cfAction == CF_ACTION_ERASURE) {
                                clearSharePreference(cfReason);
                            }
                        }
                    }
                }
                // - [ALPS02301009]

                sendResponse(cf.mOnComplete, cfInfos, ar.exception);
                break;
            }
            case EVENT_GET_CALL_FORWARD_DONE: {
                CallForwardInfo[] cfInfos = null;
                if (ar.exception == null) {
                    cfInfos = handleCfQueryResult((ImsCallForwardInfo[])ar.result);
                }
                sendResponse((Message) ar.userObj, cfInfos, ar.exception);
                break;
             }
             case EVENT_GET_CALL_BARRING_DONE:
                // Transfer CODE_UT_XCAP_404_NOT_FOUND to UT_XCAP_404_NOT_FOUND.
                // Only for op15 and CB.
                // If not op15 and CB, it would transfer to GENERIC_FAILURE in getCommandException.
                if (((GSMPhone) mDefaultPhone).isOp05IccCard() &&
                    (ar.exception != null) && (ar.exception instanceof ImsException)) {
                     ImsException imsException = (ImsException) ar.exception;
                     if ((imsException != null) && (imsException.getCode() ==
                            ImsReasonInfo.CODE_UT_XCAP_404_NOT_FOUND)) {
                         Message resp = (Message) ar.userObj;
                         if (resp != null) {
                            AsyncResult.forMessage(resp, null, new CommandException(
                                    CommandException.Error.UT_XCAP_404_NOT_FOUND));
                            resp.sendToTarget();
                            return;
                        }
                    }
                }

             case EVENT_GET_CALL_WAITING_DONE:
                int[] ssInfos = null;
                if (ar.exception == null) {
                    if (msg.what == EVENT_GET_CALL_BARRING_DONE) {
                        ssInfos = handleCbQueryResult((ImsSsInfo[])ar.result);
                    } else if (msg.what == EVENT_GET_CALL_WAITING_DONE) {
                        ssInfos = handleCwQueryResult((ImsSsInfo[])ar.result);
                    }
                }
                sendResponse((Message) ar.userObj, ssInfos, ar.exception);
                break;
              /*
              case EVENT_GET_CLIR_DONE:
                Bundle ssInfo = (Bundle) ar.result;
                int[] clirInfo = null;
                if (ssInfo != null) {
                    clirInfo = ssInfo.getIntArray(ImsPhoneMmiCode.UT_BUNDLE_KEY_CLIR);
                }
                sendResponse((Message) ar.userObj, clirInfo, ar.exception);
                break;
             */
             //case EVENT_SET_CLIR_DONE:
             case EVENT_SET_CALL_BARRING_DONE:
                // Transfer CODE_UT_XCAP_404_NOT_FOUND to UT_XCAP_404_NOT_FOUND.
                // Only for op15 and CB.
                // If not op15 and CB, it would transfer to GENERIC_FAILURE in getCommandException.
                if (((GSMPhone) mDefaultPhone).isOp05IccCard() &&
                    (ar.exception != null) && (ar.exception instanceof ImsException)) {
                     ImsException imsException = (ImsException) ar.exception;
                     if ((imsException != null) && (imsException.getCode() ==
                            ImsReasonInfo.CODE_UT_XCAP_404_NOT_FOUND)) {
                         Message resp = (Message) ar.userObj;
                         if (resp != null) {
                            AsyncResult.forMessage(resp, null, new CommandException(
                                    CommandException.Error.UT_XCAP_404_NOT_FOUND));
                            resp.sendToTarget();
                            return;
                        }
                     }
                }

             case EVENT_SET_CALL_WAITING_DONE:
                sendResponse((Message) ar.userObj, null, ar.exception);
                break;

             /// M: ALPS01844253. VoLTE SS. @{
             case EVENT_SET_CLIR_DONE:
                 if (ar.exception == null) {
                     if (mDefaultPhone instanceof GSMPhone) {
                         GSMPhone gsmPhone = (GSMPhone) mDefaultPhone;
                         gsmPhone.saveClirSetting(msg.arg1);
                     }
                 }
                 sendResponse((Message) ar.userObj, ar.result, ar.exception);
                 break;

             case EVENT_GET_CLIR_DONE:
                 int[] clirInfo = null;
                 if (ar.exception == null) {
                     Bundle ssInfo = (Bundle) ar.result;
                     clirInfo = ssInfo.getIntArray(UT_BUNDLE_KEY_CLIR);
                     // clirInfo[0] = The 'n' parameter from TS 27.007 7.7
                     // clirInfo[1] = The 'm' parameter from TS 27.007 7.7
                     Rlog.d(LOG_TAG, "EVENT_GET_CLIR_DONE: CLIR param n=" + clirInfo[0]
                             + " m=" + clirInfo[1]);
                 }
                 sendResponse((Message) ar.userObj, clirInfo, ar.exception);
                 break;
             /// @}

             /// M: SS OP01 Ut @{
             case EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE:
                 CallForwardInfoEx[] cfInfosEx = null;
                 if (ar.exception == null) {
                     cfInfosEx = handleCfInTimeSlotQueryResult(
                            (ImsCallForwardInfoEx[]) ar.result);
                 }

                 if ((ar.exception != null) && (ar.exception instanceof ImsException)) {
                     ImsException imsException = (ImsException) ar.exception;
                     if ((imsException != null) && (imsException.getCode() ==
                            ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN)) {
                         mDefaultPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                         Message resp = (Message) ar.userObj;
                         if (resp != null) {
                            AsyncResult.forMessage(resp, cfInfosEx, new CommandException(
                                    CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED));
                            resp.sendToTarget();
                            return;
                        }
                     }
                 }
                 sendResponse((Message) ar.userObj, cfInfosEx, ar.exception);
                 break;

             case EVENT_SET_CALL_FORWARD_TIME_SLOT_DONE: {
                 IccRecords records = getIccRecords();
                 CfEx cfEx = (CfEx) ar.userObj;
                 if (cfEx.mIsCfu && ar.exception == null && records != null) {
                     int cfAction = msg.arg1;
                     int cfEnable = isCfEnable(cfAction) ? 1 : 0;

                     records.setVoiceCallForwardingFlag(1, cfEnable == 1, cfEx.mSetCfNumber);
                     saveTimeSlot(cfEx.mSetTimeSlot);
                 }

                 if ((ar.exception != null) && (ar.exception instanceof ImsException)) {
                     ImsException imsException = (ImsException) ar.exception;
                     if ((imsException != null) && (imsException.getCode() ==
                            ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN)) {
                         mDefaultPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                         Message resp = cfEx.mOnComplete;
                         if (resp != null) {
                            AsyncResult.forMessage(resp, null, new CommandException(
                                    CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED));
                            resp.sendToTarget();
                            return;
                        }
                     }
                 }
                 sendResponse(cfEx.mOnComplete, null, ar.exception);
                 break;
             /// @}
             }
             case EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED:
                 if (DBG) Rlog.d(LOG_TAG, "EVENT_DEFAULT_PHONE_DATA_STATE_CHANGED");
                 updateDataServiceState();
                 break;

             default:
                 super.handleMessage(msg);
                 break;
        }
    }

    /**
     * Listen to the IMS ECBM state change
     */
    ImsEcbmStateListener mImsEcbmStateListener =
            new ImsEcbmStateListener() {
                @Override
                public void onECBMEntered() {
                    if (DBG) Rlog.d(LOG_TAG, "onECBMEntered");
                    handleEnterEmergencyCallbackMode();
                }

                @Override
                public void onECBMExited() {
                    if (DBG) Rlog.d(LOG_TAG, "onECBMExited");
                    handleExitEmergencyCallbackMode();
                }
            };

    public boolean isInEmergencyCall() {
        return mCT.isInEmergencyCall();
    }

    public boolean isInEcm() {
        return mIsPhoneInEcmState;
    }

    void sendEmergencyCallbackModeChange() {
        // Send an Intent
        Intent intent = new Intent(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        intent.putExtra(PhoneConstants.PHONE_IN_ECM_STATE, mIsPhoneInEcmState);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
        if (DBG) Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange");
    }

    @Override
    public void exitEmergencyCallbackMode() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (DBG) Rlog.d(LOG_TAG, "exitEmergencyCallbackMode()");

        // Send a message which will invoke handleExitEmergencyCallbackMode
        ImsEcbm ecbm;
        try {
            ecbm = mCT.getEcbmInterface();
            ecbm.exitEmergencyCallbackMode();
        } catch (ImsException e) {
            e.printStackTrace();
        }
    }

    private void handleEnterEmergencyCallbackMode() {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= "
                    + mIsPhoneInEcmState);
        }
        // if phone is not in Ecm mode, and it's changed to Ecm mode
        if (mIsPhoneInEcmState == false) {
            mIsPhoneInEcmState = true;
            // notify change
            sendEmergencyCallbackModeChange();
            setSystemProperty(TelephonyProperties.PROPERTY_INECM_MODE, "true");

            // Post this runnable so we will automatically exit
            // if no one invokes exitEmergencyCallbackMode() directly.
            long delayInMillis = SystemProperties.getLong(
                    TelephonyProperties.PROPERTY_ECM_EXIT_TIMER, DEFAULT_ECM_EXIT_TIMER_VALUE);
            postDelayed(mExitEcmRunnable, delayInMillis);
            // We don't want to go to sleep while in Ecm
            mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode() {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode: mIsPhoneInEcmState = "
                    + mIsPhoneInEcmState);
        }
        // Remove pending exit Ecm runnable, if any
        removeCallbacks(mExitEcmRunnable);

        if (mEcmExitRespRegistrant != null) {
            mEcmExitRespRegistrant.notifyResult(Boolean.TRUE);
        }
            if (mIsPhoneInEcmState) {
                mIsPhoneInEcmState = false;
                setSystemProperty(TelephonyProperties.PROPERTY_INECM_MODE, "false");
            }
            // send an Intent
            sendEmergencyCallbackModeChange();
    }

    /**
     * Handle to cancel or restart Ecm timer in emergency call back mode if action is
     * CANCEL_ECM_TIMER, cancel Ecm timer and notify apps the timer is canceled; otherwise, restart
     * Ecm timer and notify apps the timer is restarted.
     */
    void handleTimerInEmergencyCallbackMode(int action) {
        switch (action) {
            case CANCEL_ECM_TIMER:
                removeCallbacks(mExitEcmRunnable);
                if (mDefaultPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                    ((GSMPhone) mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                } else { // Should be CDMA - also go here by default
                    ((CDMAPhone) mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                }
                break;
            case RESTART_ECM_TIMER:
                long delayInMillis = SystemProperties.getLong(
                        TelephonyProperties.PROPERTY_ECM_EXIT_TIMER, DEFAULT_ECM_EXIT_TIMER_VALUE);
                postDelayed(mExitEcmRunnable, delayInMillis);
                if (mDefaultPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                    ((GSMPhone) mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                } else { // Should be CDMA - also go here by default
                    ((CDMAPhone) mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                }
                break;
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
        }
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        mEcmExitRespRegistrant.clear();
    }

    public void onFeatureCapabilityChanged() {
        mDefaultPhone.getServiceStateTracker().onImsCapabilityChanged();
    }

    public boolean isVolteEnabled() {
        return mCT.isVolteEnabled();
    }

    public boolean isVowifiEnabled() {
        return mCT.isVowifiEnabled();
    }

    public boolean isVideoCallEnabled() {
        return mCT.isVideoCallEnabled();
    }

    public Phone getDefaultPhone() {
        return mDefaultPhone;
    }

    public boolean isImsRegistered() {
        return mImsRegistered;
    }

    public void setImsRegistered(boolean value) {
        mImsRegistered = value;
        /// M: ALPS02494504. Remove notification when registering on IMS. @{
        if (mImsRegistered) {
            final String notificationTag = "wifi_calling";
            final int notificationId = 1;

            NotificationManager notificationManager =
                    (NotificationManager) mContext.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationTag, notificationId);
        }
        /// @}
    }

    public void callEndCleanupHandOverCallIfAny() {
        mCT.callEndCleanupHandOverCallIfAny();
    }

    private BroadcastReceiver mResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Add notification only if alert was not shown by WfcSettings
            if (getResultCode() == Activity.RESULT_OK) {
                /// M: print message for debug.
                Rlog.d(LOG_TAG, "Receive registration broadcast!");

                // Default result code (as passed to sendOrderedBroadcast)
                // means that intent was not received by WfcSettings.

                CharSequence title = intent.getCharSequenceExtra(EXTRA_KEY_ALERT_TITLE);
                CharSequence messageAlert = intent.getCharSequenceExtra(EXTRA_KEY_ALERT_MESSAGE);
                CharSequence messageNotification = intent.getCharSequenceExtra(EXTRA_KEY_NOTIFICATION_MESSAGE);

                Intent resultIntent = new Intent(Intent.ACTION_MAIN);
                resultIntent.setClassName("com.android.settings",
                        "com.android.settings.Settings$WifiCallingSettingsActivity");
                resultIntent.putExtra(EXTRA_KEY_ALERT_SHOW, true);
                resultIntent.putExtra(EXTRA_KEY_ALERT_TITLE, title);
                resultIntent.putExtra(EXTRA_KEY_ALERT_MESSAGE, messageAlert);
                PendingIntent resultPendingIntent =
                        PendingIntent.getActivity(
                                mContext,
                                0,
                                resultIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT
                        );

                final Notification notification =
                        new Notification.Builder(mContext)
                                .setSmallIcon(android.R.drawable.stat_sys_warning)
                                .setContentTitle(title)
                                .setContentText(messageNotification)
                                .setAutoCancel(true)
                                .setContentIntent(resultPendingIntent)
                                .setStyle(new Notification.BigTextStyle().bigText(messageNotification))
                                .build();
                final String notificationTag = "wifi_calling";
                final int notificationId = 1;

                NotificationManager notificationManager =
                        (NotificationManager) mContext.getSystemService(
                                Context.NOTIFICATION_SERVICE);
                notificationManager.notify(notificationTag, notificationId,
                        notification);
            }
        }
    };

    /**
     * Show notification in case of some error codes.
     */
    public void processDisconnectReason(ImsReasonInfo imsReasonInfo) {
        if (imsReasonInfo.mCode == imsReasonInfo.CODE_REGISTRATION_ERROR
                && imsReasonInfo.mExtraMessage != null) {

            final String[] wfcOperatorErrorCodes =
                    mContext.getResources().getStringArray(
                            com.android.internal.R.array.wfcOperatorErrorCodes);
            final String[] wfcOperatorErrorAlertMessages =
                    mContext.getResources().getStringArray(
                            com.android.internal.R.array.wfcOperatorErrorAlertMessages);
            final String[] wfcOperatorErrorNotificationMessages =
                    mContext.getResources().getStringArray(
                            com.android.internal.R.array.wfcOperatorErrorNotificationMessages);

            for (int i = 0; i < wfcOperatorErrorCodes.length; i++) {
                // Match error code.
                if (!imsReasonInfo.mExtraMessage.startsWith(
                        wfcOperatorErrorCodes[i])) {
                    continue;
                }
                // If there is no delimiter at the end of error code string
                // then we need to verify that we are not matching partial code.
                // EXAMPLE: "REG9" must not match "REG99".
                // NOTE: Error code must not be empty.
                int codeStringLength = wfcOperatorErrorCodes[i].length();
                char lastChar = wfcOperatorErrorCodes[i].charAt(codeStringLength-1);
                if (Character.isLetterOrDigit(lastChar)) {
                    if (imsReasonInfo.mExtraMessage.length() > codeStringLength) {
                        char nextChar = imsReasonInfo.mExtraMessage.charAt(codeStringLength);
                        if (Character.isLetterOrDigit(nextChar)) {
                            continue;
                        }
                    }
                }

                final CharSequence title = mContext.getText(
                        com.android.internal.R.string.wfcRegErrorTitle);

                CharSequence messageAlert = imsReasonInfo.mExtraMessage;
                CharSequence messageNotification = imsReasonInfo.mExtraMessage;
                if (!wfcOperatorErrorAlertMessages[i].isEmpty()) {
                    messageAlert = wfcOperatorErrorAlertMessages[i];
                }
                if (!wfcOperatorErrorNotificationMessages[i].isEmpty()) {
                    messageNotification = wfcOperatorErrorNotificationMessages[i];
                }

                // UX requirement is to disable WFC in case of "permanent" registration failures.
                /// M: ALPS02423361, mark out this to prevent unexpected behavior. @{
                //ImsManager.setWfcSetting(mContext, false);
                /// @}

                // If WfcSettings are active then alert will be shown
                // otherwise notification will be added.
                Intent intent = new Intent(ImsManager.ACTION_IMS_REGISTRATION_ERROR);
                intent.putExtra(EXTRA_KEY_ALERT_TITLE, title);
                intent.putExtra(EXTRA_KEY_ALERT_MESSAGE, messageAlert);
                intent.putExtra(EXTRA_KEY_NOTIFICATION_MESSAGE, messageNotification);
                mContext.sendOrderedBroadcast(intent, null, mResultReceiver,
                        null, Activity.RESULT_OK, null, null);

                // We can only match a single error code
                // so should break the loop after a successful match.
                break;
            }
        }
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        if (DBG) {
            Rlog.d(LOG_TAG, "getOutgoingCallerIdDisplay");
        }
        Message resp;
        resp = obtainMessage(EVENT_GET_CLIR_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.queryCLIR(resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
            Message onComplete) {
        if (DBG) {
            Rlog.d(LOG_TAG, "setOutgoingCallerIdDisplay: commandInterfaceCLIRMode="
                    + commandInterfaceCLIRMode);
        }
        Message resp;
        resp = obtainMessage(EVENT_SET_CLIR_DONE, commandInterfaceCLIRMode, 0, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.updateCLIR(commandInterfaceCLIRMode, resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    /**
     * CS Fall back to GSMPhone for MMI code.
     *
     *@param reason the reason for CS fallback
     */
    public void handleMmiCodeCsfb(int reason) {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleMmiCodeCsfb: reason = " + reason + ", mDialString = "
                    + mDialString);
        }
        if (reason == ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN) {
            mDefaultPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
        } else if (reason == ImsReasonInfo.CODE_UT_UNKNOWN_HOST) {
            mDefaultPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_ONCE);
        }
        SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_MMI_CODE, null);
        ss.mParcel.writeString(mDialString);
        Message msgCSFB = mDefaultPhone.obtainMessage(EVENT_IMS_UT_CSFB, ss);

        mDefaultPhone.sendMessage(msgCSFB);
    }

    /// M: SS OP01 Ut @{
    private static class CfEx {
        final String mSetCfNumber;
        final long[] mSetTimeSlot;
        final Message mOnComplete;
        final boolean mIsCfu;

        CfEx(String cfNumber, long[] cfTimeSlot, boolean isCfu, Message onComplete) {
            mSetCfNumber = cfNumber;
            mSetTimeSlot = cfTimeSlot;
            mIsCfu = isCfu;
            mOnComplete = onComplete;
        }
    }

    @Override
    public void getCallForwardInTimeSlot(int commandInterfaceCFReason,
            Message onComplete) {
        if (DBG) {
            Rlog.d(LOG_TAG, "getCallForwardInTimeSlot reason = " + commandInterfaceCFReason);
        }
        if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
            if (DBG) {
                Rlog.d(LOG_TAG, "requesting call forwarding in a time slot query.");
            }

            ((GSMPhone) mDefaultPhone).setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                    UT_CFU_NOTIFICATION_MODE_DISABLED);

            Message resp;
            resp = obtainMessage(EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE, onComplete);

            try {
                ImsUtInterface ut = mCT.getUtInterface();
                ut.queryCallForwardInTimeSlot(
                        getConditionFromCFReason(commandInterfaceCFReason),
                        resp);
            } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override
    public void setCallForwardInTimeSlot(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            long[] timeSlot,
            Message onComplete) {
        if (DBG) {
            Rlog.d(LOG_TAG, "setCallForwardInTimeSlot action = " + commandInterfaceCFAction
                    + ", reason = " + commandInterfaceCFReason);
        }
        if ((isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL)) {
            Message resp;
            CfEx cfEx = new CfEx(dialingNumber, timeSlot, true, onComplete);
            resp = obtainMessage(EVENT_SET_CALL_FORWARD_TIME_SLOT_DONE,
                    commandInterfaceCFAction, 0, cfEx);

            try {
                ImsUtInterface ut = mCT.getUtInterface();
                ut.updateCallForwardInTimeSlot(getActionFromCFAction(commandInterfaceCFAction),
                        getConditionFromCFReason(commandInterfaceCFReason),
                        dialingNumber,
                        timerSeconds,
                        timeSlot,
                        resp);
             } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
             }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    private CallForwardInfoEx[] handleCfInTimeSlotQueryResult(ImsCallForwardInfoEx[] infos) {
        CallForwardInfoEx[] cfInfos = null;

        if (infos != null && infos.length != 0) {
            cfInfos = new CallForwardInfoEx[infos.length];
        }

        IccRecords r = getIccRecords();
        if (infos == null || infos.length == 0) {
            if (r != null) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                r.setVoiceCallForwardingFlag(1, false, null);

                ((GSMPhone) mDefaultPhone).setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                        UT_CFU_NOTIFICATION_MODE_OFF);
            }
        } else {
            for (int i = 0, s = infos.length; i < s; i++) {
                if (infos[i].mCondition == ImsUtInterface.CDIV_CF_UNCONDITIONAL) {
                    if (r != null) {
                        r.setVoiceCallForwardingFlag(1, (infos[i].mStatus == 1),
                                infos[i].mNumber);

                        String mode = infos[i].mStatus == 1 ?
                            UT_CFU_NOTIFICATION_MODE_ON : UT_CFU_NOTIFICATION_MODE_OFF;
                        ((GSMPhone) mDefaultPhone).setSystemProperty(
                            PROPERTY_UT_CFU_NOTIFICATION_MODE, mode);
                        saveTimeSlot(infos[i].mTimeSlot);
                    }
                }
                cfInfos[i] = getCallForwardInfoEx(infos[i]);
            }
        }

        return cfInfos;
    }

    private CallForwardInfoEx getCallForwardInfoEx(ImsCallForwardInfoEx info) {
        CallForwardInfoEx cfInfo = new CallForwardInfoEx();
        cfInfo.status = info.mStatus;
        cfInfo.reason = getCFReasonFromCondition(info.mCondition);
        cfInfo.serviceClass = info.mServiceClass;
        cfInfo.toa = info.mToA;
        cfInfo.number = info.mNumber;
        cfInfo.timeSeconds = info.mTimeSeconds;
        cfInfo.timeSlot = info.mTimeSlot;
        return cfInfo;
    }

    @Override
    public void hangupAll() throws CallStateException {
        if (DBG) {
            Rlog.d(LOG_TAG, "hangupAll");
        }
        mCT.hangupAll();
    }
    /// @}

    /// M: Add the new API to get the CommandsInterface @{
    //@Override
    public CommandsInterface getCommandsInterface() {
        return mDefaultPhone.mCi;
    }
    /// @}

    /// M: For VoLTE enhanced conference call. @{
    @Override
    public Connection dial(List<String> numbers, int videoState)
            throws CallStateException {
        return mCT.dial(numbers, videoState);
    }
    /// @}

    /// M: ALPS01953873. @{
    /**
     * Query if currently this phone support the specific feature.
     * @param feature defineded in Phone.java
     * @return true if supporting.
     * @hide
     */
    @Override
    public boolean isFeatureSupported(FeatureType feature) {
        if (feature == FeatureType.VOLTE_ENHANCED_CONFERENCE
                || feature == FeatureType.VIDEO_RESTRICTION) {
            final List<String> voLteEnhancedConfMccMncList
                = Arrays.asList(
                    // 1. CMCC:
                    "46000", "46002", "46007", "46008", "46011");

            IccRecords iccRecords = getIccRecords();
            if (iccRecords != null) {
                String mccMnc = iccRecords.getOperatorNumeric();
                boolean ret = voLteEnhancedConfMccMncList.contains(mccMnc);

                Rlog.d(LOG_TAG,
                    "isFeatureSupported(" + feature + "): ret = " + ret +
                    " current mccMnc = " + mccMnc);
                return ret;
            } else {
                Rlog.d(LOG_TAG,
                    "isFeatureSupported(" + feature + ") no iccRecords");
            }
        } else if (feature == FeatureType.VOLTE_CONF_REMOVE_MEMBER) {
            // Support remove member by default.
            return true;
        }

        return false;
    }

    /// M: ALPS02320160 @{
    /// Google issue. ImsConference constructor will get null icc serial number from
    /// ImsPhoneBase.getIccSerialNumber(). Override that to get icc from GsmPhone.
    @Override
    public String getIccSerialNumber() {
        if (mDefaultPhone != null) {
            return mDefaultPhone.getIccSerialNumber();
        }
        return null;
    }
    /// @}

    // + [ALPS02301009]
    private void clearSharePreference(int cfReason) {
        String key = null;
        switch (cfReason) {
            case CF_REASON_BUSY:
                key = CFB_KEY + "_" + String.valueOf(mPhoneId);
                break;
            case CF_REASON_NO_REPLY:
                key = CFNR_KEY + "_" + String.valueOf(mPhoneId);
                break;
            case CF_REASON_NOT_REACHABLE:
                key = CFNRC_KEY + "_" + String.valueOf(mPhoneId);
                break;
            default:
                Rlog.e(LOG_TAG, "No need to store cfreason: " + cfReason);
                return;
        }

        Rlog.e(LOG_TAG, "Read to clear the key: " + key);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.remove(key);
        if (!editor.commit()) {
            Rlog.e(LOG_TAG, "failed to commit the removal of CF preference: " + key);
        } else {
            Rlog.e(LOG_TAG, "Commit the removal of CF preference: " + key);
        }
    }

    private boolean applySharePreference(int cfReason, String setNumber) {
        String key = null;
        switch (cfReason) {
            case CF_REASON_BUSY:
                key = CFB_KEY + "_" + String.valueOf(mPhoneId);
                break;
            case CF_REASON_NO_REPLY:
                key = CFNR_KEY + "_" + String.valueOf(mPhoneId);
                break;
            case CF_REASON_NOT_REACHABLE:
                key = CFNRC_KEY + "_" + String.valueOf(mPhoneId);
                break;
            default:
                Rlog.d(LOG_TAG, "No need to store cfreason: " + cfReason);
                return false;
        }

        IccRecords r = getIccRecords();
        if (r == null) {
            Rlog.d(LOG_TAG, "No iccRecords");
            return false;
        }

        String currentImsi = r.getIMSI();

        if (currentImsi == null || currentImsi.isEmpty()) {
            Rlog.d(LOG_TAG, "currentImsi is empty");
            return false;
        }

        if (setNumber == null || setNumber.isEmpty()) {
            Rlog.d(LOG_TAG, "setNumber is empty");
            return false;
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();

        String content = currentImsi + ";" + setNumber;

        if (content == null || content.isEmpty()) {
            Rlog.e(LOG_TAG, "imsi or content are empty or null.");
            return false;
        }

        Rlog.e(LOG_TAG, "key: " + key);
        Rlog.e(LOG_TAG, "content: " + content);

        editor.putString(key, content);
        editor.apply();

        return true;
    }

    private String getPreviousDialNumber(int cfReason) {
        String key = null;
        switch (cfReason) {
            case CF_REASON_BUSY:
                key = CFB_KEY + "_" + String.valueOf(mPhoneId);
                break;
            case CF_REASON_NO_REPLY:
                key = CFNR_KEY + "_" + String.valueOf(mPhoneId);
                break;
            case CF_REASON_NOT_REACHABLE:
                key = CFNRC_KEY + "_" + String.valueOf(mPhoneId);
                break;
            default:
                Rlog.d(LOG_TAG, "No need to do the reason: " + cfReason);
                return null;
        }

        if (key == null) {
            return null;
        }

        Rlog.d(LOG_TAG, "key: " + key);

        IccRecords r = getIccRecords();
        if (r == null) {
            Rlog.d(LOG_TAG, "No iccRecords");
            return null;
        }

        String currentImsi = r.getIMSI();

        if (currentImsi == null || currentImsi.isEmpty()) {
            Rlog.d(LOG_TAG, "currentImsi is empty");
            return null;
        }

        Rlog.d(LOG_TAG, "currentImsi: " + currentImsi);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        String info = sp.getString(key, null);

        if (info == null) {
            Rlog.d(LOG_TAG, "Sharedpref not with: " + key);
            return null;
        }

        String[] infoAry = info.split(";");

        if (infoAry == null || infoAry.length < 2) {
            Rlog.d(LOG_TAG, "infoAry.length < 2");
            return null;
        }

        String imsi = infoAry[0];
        String number = infoAry[1];

        if (imsi == null || imsi.isEmpty()) {
            Rlog.d(LOG_TAG, "Sharedpref imsi is empty.");
            return null;
        }

        if (number == null || number.isEmpty()) {
            Rlog.d(LOG_TAG, "Sharedpref number is empty.");
            return null;
        }

        Rlog.d(LOG_TAG, "Sharedpref imsi: " + imsi);
        Rlog.d(LOG_TAG, "Sharedpref number: " + number);

        if (currentImsi.equals(imsi)) {
            Rlog.d(LOG_TAG, "Get dial number from sharepref: " + number);
            return number;
        } else {
            SharedPreferences.Editor editor = sp.edit();
            editor.remove(key);
            if (!editor.commit()) {
                Rlog.e(LOG_TAG, "failed to commit the removal of CF preference: " + key);
            }
        }

        return null;
    }

    private static final String CFB_KEY = "CFB";
    private static final String CFNR_KEY = "CFNR";
    private static final String CFNRC_KEY = "CFNRC";
    // - [ALPS02301009]
}
