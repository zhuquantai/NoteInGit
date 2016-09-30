/*
* Copyright (C) 2011-2014 MediaTek Inc.
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

package com.android.internal.telephony.gsm;

import android.os.ServiceManager;
import android.content.Context;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;


/// M: CC053: MoMS [Mobile Managerment] @{
// 2. MT Phone Call Interception
import android.os.Bundle;
import android.content.pm.PackageManager;
import com.mediatek.common.mom.IMobileManager;
import com.mediatek.common.mom.IMobileManagerService;
import com.mediatek.common.mom.MobileManagerUtils;
import android.os.RemoteException;
/// @}

/// M: CC052: DM @{
import android.os.IBinder;
import com.mediatek.common.dm.DmAgent;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import com.android.internal.telephony.CallStateException;
/// @}

/// [incoming indication]. @{
/* For adjust PhoneAPP priority, mtk04070, 20120307 */
import android.os.AsyncResult;
import android.os.Process;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
/// @}

/// M: CC069: Terminal Based Call Waiting @{
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE;
import static com.android.internal.telephony.TelephonyProperties.TERMINAL_BASED_CALL_WAITING_DISABLED;
import static com.android.internal.telephony.TelephonyProperties.TERMINAL_BASED_CALL_WAITING_ENABLED_OFF;
/// @}

import android.telephony.DisconnectCause;

/// M: For switch antenna feature @{
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
/// @}

public final class GsmCallTrackerHelper {

    static final String LOG_TAG = "GsmCallTracker";

    protected static final int EVENT_POLL_CALLS_RESULT             = 1;
    protected static final int EVENT_CALL_STATE_CHANGE             = 2;
    protected static final int EVENT_REPOLL_AFTER_DELAY            = 3;
    protected static final int EVENT_OPERATION_COMPLETE            = 4;
    protected static final int EVENT_GET_LAST_CALL_FAIL_CAUSE      = 5;

    protected static final int EVENT_SWITCH_RESULT                 = 8;
    protected static final int EVENT_RADIO_AVAILABLE               = 9;
    protected static final int EVENT_RADIO_NOT_AVAILABLE           = 10;
    protected static final int EVENT_CONFERENCE_RESULT             = 11;
    protected static final int EVENT_SEPARATE_RESULT               = 12;
    protected static final int EVENT_ECT_RESULT                    = 13;
    protected static final int EVENT_EXIT_ECM_RESPONSE_CDMA        = 14;
    protected static final int EVENT_CALL_WAITING_INFO_CDMA        = 15;
    protected static final int EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA = 16;
    protected static final int EVENT_THREE_WAY_DIAL_BLANK_FLASH    = 20;

    /// M: CC010: Add RIL interface @{
    protected static final int EVENT_HANG_UP_RESULT                = 21;
    protected static final int EVENT_DIAL_CALL_RESULT              = 22;
    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE    = 23;
    protected static final int EVENT_INCOMING_CALL_INDICATION      = 24;
    //protected static final int EVENT_CNAP_INDICATION               = 25; //obsolete
    protected static final int EVENT_SPEECH_CODEC_INFO             = 26;
    /// @}
    protected static final int EVENT_CDMA_CALL_ACCEPTED            = 27;
    protected static final int EVENT_CDMA_DIAL_THREEWAY_DELAY      = 28;
    protected static final int EVENT_EXIT_ECM_RESPONSE_DIAL_THREEWAY = 29;
    ///M: IMS conference call feature. @{
    protected static final int EVENT_ECONF_SRVCC_INDICATION = 30;
    protected static final int EVENT_ECONF_RESULT_INDICATION = 31;
    protected static final int EVENT_RETRIEVE_HELD_CALL_RESULT = 32;
    protected static final int EVENT_CALL_INFO_INDICATION = 33;
    /// @}

    private Context mContext;
    private GsmCallTracker mTracker;

    /// M: CC053: MoMS [Mobile Managerment] @{
    // 2. MT Phone Call Interception
    private IMobileManagerService mMobileManagerService;
    public boolean mIsRejectedByMoms = false;
    /// @}

    /// M: CC052: DM&PPL @{
    public boolean isInLock = false;
    public boolean isFullLock = false;
    public boolean needHangupMOCall = false;
    public boolean needHangupMTCall = false;

    private DmAgent mDmAgent;
    private BroadcastReceiver mReceiver;
    /// @}

    /// [Force release]
    private boolean hasPendingHangupRequest = false;
    private int pendingHangupRequest = 0;

    /// [incoming indication]
    private int pendingMTCallId = 0;
    private int pendingMTSeqNum = 0;

    /// M: CC017: Forwarding number via EAIC @{
    // To store forwarding address from incoming call indication
    private boolean mContainForwardingAddress = false;
    private String  mForwardingAddress = null;
    private int     mForwardingAddressCallId = 0;
    /// @}

    /// M: For switch antenna feature @{
    private static final boolean MTK_SWITCH_ANTENNA_SUPPORT =
                SystemProperties.get("ro.mtk_switch_antenna").equals("1");
    /// @}

    GsmCallTrackerHelper(Context context, GsmCallTracker tracker) {

        mContext = context;
        mTracker = tracker;

        /// M: CC052: DM @{
        IBinder binder = ServiceManager.getService("DmAgent");
        mDmAgent = DmAgent.Stub.asInterface(binder);

        mReceiver = new GsmCallTrackerReceiver();

        IntentFilter filter = new IntentFilter("com.mediatek.dm.LAWMO_LOCK");
        filter.addAction("com.mediatek.dm.LAWMO_UNLOCK");
        Intent intent = mContext.registerReceiver(mReceiver, filter);

        DmUpdateStatus();
        /// @}
    }

    void log(String msg) {
        Rlog.d(LOG_TAG, "[CC][GsmCT][Helper] " + msg);
    }

    public void LogerMessage(int msgType) {

        switch (msgType) {
            case EVENT_POLL_CALLS_RESULT:
                log("handle EVENT_POLL_CALLS_RESULT");
            break;

            case EVENT_CALL_STATE_CHANGE:
                log("handle EVENT_CALL_STATE_CHANGE");
            break;

            case EVENT_REPOLL_AFTER_DELAY:
                log("handle EVENT_REPOLL_AFTER_DELAY");
            break;

            case EVENT_OPERATION_COMPLETE:
                log("handle EVENT_OPERATION_COMPLETE");
            break;

            case EVENT_GET_LAST_CALL_FAIL_CAUSE:
                log("handle EVENT_GET_LAST_CALL_FAIL_CAUSE");
            break;

            case EVENT_SWITCH_RESULT:
                log("handle EVENT_SWITCH_RESULT");
            break;

            case EVENT_RADIO_AVAILABLE:
                log("handle EVENT_RADIO_AVAILABLE");
            break;

            case EVENT_RADIO_NOT_AVAILABLE:
                log("handle EVENT_RADIO_NOT_AVAILABLE");
            break;

            case EVENT_CONFERENCE_RESULT:
                log("handle EVENT_CONFERENCE_RESULT");
            break;

            case EVENT_SEPARATE_RESULT:
                log("handle EVENT_SEPARATE_RESULT");
            break;

            case EVENT_ECT_RESULT:
                log("handle EVENT_ECT_RESULT");
            break;

            /* M: CC part start */
            case EVENT_HANG_UP_RESULT:
                log("handle EVENT_HANG_UP_RESULT");
            break;

            case EVENT_DIAL_CALL_RESULT:
                log("handle EVENT_DIAL_CALL_RESULT");
            break;

            case EVENT_INCOMING_CALL_INDICATION:
                log("handle EVENT_INCOMING_CALL_INDICATION");
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                log("handle EVENT_RADIO_OFF_OR_NOT_AVAILABLE");
            break;

            //obsolete
            /*
            case EVENT_CNAP_INDICATION:
                log("handle EVENT_CNAP_INDICATION");
            break;
            */
            /* M: CC part end */

            default:
                log("handle XXXXX");
            break;

        }
    }

    void LogState() {

        int callId = 0;
        int count = 0;

        for (int i = 0, s = mTracker.MAX_CONNECTIONS; i < s; i++) {
            if (mTracker.mConnections[i] != null) {
                callId = mTracker.mConnections[i].mIndex + 1;
                count ++;
                Rlog.i(LOG_TAG, "* conn id " + callId + " existed");
            }
        }
        Rlog.i(LOG_TAG, "* GsmCT has " + count + " connection");
    }

    /// M: CC053: MoMS [Mobile Managerment] @{
    // 2. MT Phone Call Interception
    public void MobileManagermentQueryPermission(String number, int CallMode, long subId) {

        if (MobileManagerUtils.isSupported()) {
            try {
                if (mMobileManagerService == null) {
                    mMobileManagerService = IMobileManagerService.Stub.asInterface(
                        ServiceManager.getService(Context.MOBILE_SERVICE));
                }
                log("getInterceptionEnabledSetting = " + mMobileManagerService.getInterceptionEnabledSetting());
                if (mMobileManagerService.getInterceptionEnabledSetting()) {
                    Bundle parameter = new Bundle();
                    parameter.putString(IMobileManager.PARAMETER_PHONENUMBER, number);
                    parameter.putInt(IMobileManager.PARAMETER_CALLTYPE, CallMode);
                    parameter.putInt(IMobileManager.PARAMETER_SLOTID, (int) subId); /*simID need to update mtk02003*/
                    int result = mMobileManagerService.triggerManagerApListener(IMobileManager.CONTROLLER_CALL, parameter, PackageManager.PERMISSION_GRANTED);
                    if (result == PackageManager.PERMISSION_GRANTED)
                        mIsRejectedByMoms = false;
                    else
                        mIsRejectedByMoms = true;
                }
            } catch (RemoteException e) {
                log("MoMS, Suppressing notification faild!");
            }
        } else {
            mIsRejectedByMoms = false;
        }
    }

    public void MobileManagermentResetIsBlocking() {
        mIsRejectedByMoms = false;
    }

    public boolean MobileManagermentGetIsBlocking() {
        return mIsRejectedByMoms;
    }
    /// @}

    /// M: CC052: DM @{
    private void DmUpdateStatus() {
        try {
            if (mDmAgent != null) {
                isInLock = mDmAgent.isLockFlagSet();
                isFullLock = (mDmAgent.getLockType() == 1);
                log("isInLock = " + isInLock + ", isFullLock = " + isFullLock);
                needHangupMOCall = mDmAgent.isHangMoCallLocking();
                needHangupMTCall = mDmAgent.isHangMtCallLocking();
                log("needHangupMOCall = " + needHangupMOCall + ", needHangupMTCall = " + needHangupMTCall);
            }
        } catch (RemoteException ex) {
        }
    }

    public boolean DmCheckIfCallCanComing(GsmConnection c) {

        log("isInLock = " + isInLock + ", isFullLock = " + isFullLock);
        if (isInLock && isFullLock) {
            log("hang up MT call because of in DM lock state");
            try {
                mTracker.hangup(c);
            } catch (CallStateException ex) {
                Rlog.e(LOG_TAG, "unexpected error on hangup");
            }
            return false;
        }
        else {
            return true;
        }
    }

    private class GsmCallTrackerReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {

            // update variables when receive intent
            DmUpdateStatus();
            String action = intent.getAction();
            if (TextUtils.equals(action, "com.mediatek.dm.LAWMO_LOCK")) {
                // when phone is not idle, hang up call
                // when phone is idle, do nothing
                if (PhoneConstants.State.IDLE != mTracker.mState) {
                    if (needHangupMOCall && needHangupMTCall) {
                        mTracker.hangupAll();
                    } else {
                        int count = mTracker.mConnections.length;
                        log("The count of connections is" + count);
                        for (int i = 0; i < count; i++) {
                            GsmConnection cn = mTracker.mConnections[i];
                            if ((cn.isIncoming() && needHangupMTCall) ||
                               (!cn.isIncoming() && needHangupMOCall))
                                try {
                                    mTracker.hangup(mTracker.mConnections[i]);
                                } catch (CallStateException ex) {
                                    log("unexpected error on hangup");
                                }
                        }
                    }
                }
            }
        }
    }
    /// @}

    public boolean ForceReleaseConnection(GsmCall call, GsmCall hangupCall) {
        GsmConnection cn;
        if (call.mState == Call.State.DISCONNECTING) {
            for (int i = 0; i < call.mConnections.size(); i++) {
                cn = (GsmConnection) call.mConnections.get(i);
                mTracker.mCi.forceReleaseCall(cn.mIndex + 1, mTracker.obtainCompleteMessage());
            }
            /* To solve ALPS01525265 */
            if (call == hangupCall) {
               return true;
            }
        }
        return false;
    }

    public boolean ForceReleaseAllConnection(GsmCall call) {
        boolean forceReleaseFg = ForceReleaseConnection(mTracker.mForegroundCall, call);
        boolean forceReleaseBg = ForceReleaseConnection(mTracker.mBackgroundCall, call);
        boolean forceReleaseRing = ForceReleaseConnection(mTracker.mRingingCall, call);

        /* To solve ALPS01525265 */
        if (forceReleaseFg || forceReleaseBg || forceReleaseRing) {
           Rlog.d(LOG_TAG, "hangup(GsmCall)Hang up disconnecting call, return directly");
           return true;
        }

        return false;
    }

    public boolean ForceReleaseNotRingingConnection(GsmCall call) {
        boolean forceReleaseFg = ForceReleaseConnection(mTracker.mForegroundCall, call);
        boolean forceReleaseBg = ForceReleaseConnection(mTracker.mBackgroundCall, call);

        /* To solve ALPS01525265 */
        if (forceReleaseFg || forceReleaseBg) {
           Rlog.d(LOG_TAG, "hangup(GsmCall)Hang up disconnecting call, return directly");
           return true;
        }

        return true;
    }

    public boolean hangupConnectionByIndex(GsmCall c, int index) {
        int count = c.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmConnection cn = (GsmConnection) c.mConnections.get(i);
            try {
                if (cn.getGSMIndex() == index) {
                    mTracker.mCi.hangupConnection(index, mTracker.obtainCompleteMessage());
                    return true;
                }
            } catch (CallStateException ex) {
                // Ignore "connection not found"
                // Call may have hung up already
                Rlog.w(LOG_TAG, "GsmCallTracker hangupConnectionByIndex() on absent connection ");
            }
        }
        return false;
    }

    public boolean hangupFgConnectionByIndex(int index) {
        return hangupConnectionByIndex(mTracker.mForegroundCall, index);
    }

    public boolean hangupBgConnectionByIndex(int index) {
        return hangupConnectionByIndex(mTracker.mBackgroundCall, index);
    }

    public boolean hangupRingingConnectionByIndex(int index) {
        return hangupConnectionByIndex(mTracker.mRingingCall, index);
    }


    protected int getCurrentTotalConnections() {
        int count = 0;
        for (int i = 0; i < mTracker.MAX_CONNECTIONS; i++) {
            if (mTracker.mConnections[i] != null) {
                count ++;
            }
            }
        return count;
    }

    protected void PendingHangupRequestUpdate() {
       log("updatePendingHangupRequest - " + mTracker.mHangupPendingMO + hasPendingHangupRequest + pendingHangupRequest);
       if (mTracker.mHangupPendingMO) {
           if (hasPendingHangupRequest) {
               pendingHangupRequest--;
               if (pendingHangupRequest == 0) {
                  hasPendingHangupRequest = false;
               }
           }
       }
    }

    public void PendingHangupRequestInc() {
        hasPendingHangupRequest = true;
        pendingHangupRequest++;
    }

    public void PendingHangupRequestDec() {
        if (hasPendingHangupRequest) {
            pendingHangupRequest--;
            if (pendingHangupRequest == 0) {
                hasPendingHangupRequest = false;
            }
        }
    }

    public void PendingHangupRequestReset() {
        hasPendingHangupRequest = false;
        pendingHangupRequest = 0;
    }

    public boolean hasPendingHangupRequest() {
        return hasPendingHangupRequest;
    }

    public int CallIndicationGetId() {
        return pendingMTCallId;
    }

    public int CallIndicationGetSeqNo() {
        return pendingMTSeqNum;
    }

    public void CallIndicationProcess(AsyncResult ar) {
        int mode = 0;
        String[] incomingCallInfo = (String[]) ar.result;
        int callId = Integer.parseInt(incomingCallInfo[0]);
        int callMode = Integer.parseInt(incomingCallInfo[3]);
        int seqNumber = Integer.parseInt(incomingCallInfo[4]);

        log("CallIndicationProcess " + mode + " pendingMTCallId " + pendingMTCallId +
                " pendingMTSeqNum " + pendingMTSeqNum);

        /// M: CC069: Terminal Based Call Waiting @{
        String tbcwMode = mTracker.mPhone.getSystemProperty(
                PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                TERMINAL_BASED_CALL_WAITING_DISABLED);
        GsmCall.State fgState = (mTracker.mForegroundCall == null) ? GsmCall.State.IDLE
                : mTracker.mForegroundCall.getState();
        GsmCall.State bgState = (mTracker.mBackgroundCall == null) ? GsmCall.State.IDLE
                : mTracker.mBackgroundCall.getState();

        log("PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE = " + tbcwMode
                + " , ForgroundCall State = " + fgState
                + " , BackgroundCall State = " + bgState);
        if (TERMINAL_BASED_CALL_WAITING_ENABLED_OFF.equals(tbcwMode)
                && ((fgState == GsmCall.State.ACTIVE) || (bgState == GsmCall.State.HOLDING))) {
            log("PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE = "
                    + "TERMINAL_BASED_CALL_WAITING_ENABLED_OFF."
                    + " Reject the call as UDUB ");
            mode = 1; //1:disallow MT call
            mTracker.mCi.setCallIndication(mode, callId, seqNumber, null);
            //Terminal Based Call Waiting OFF:Silent Reject without generating missed call log
            return;
        }
        /// @}

        /// M: CC017: Forwarding number via EAIC @{
        /* Check if EAIC message contains forwarding address(A calls B and it is forwarded to C,
             C may receive forwarding address - B's phone number). */
        mForwardingAddress = null;
        if ((incomingCallInfo[5] != null) && (incomingCallInfo[5].length() > 0)) {
            /* This value should be set to true after CallManager approves the incoming call */
            mContainForwardingAddress = false;
            mForwardingAddress = incomingCallInfo[5];
            mForwardingAddressCallId = callId;
            log("EAIC message contains forwarding address - " + mForwardingAddress + "," + callId);
        }
        /// @}

        /// M: CC059: Reject MT when another MT already exists via EAIC disapproval @{
        if (mTracker.mState == PhoneConstants.State.RINGING) {
            mode = 1;
        }
        /// @}
        /// M: For 3G VT only @{
        else if (mTracker.mState == PhoneConstants.State.OFFHOOK) {
            if (callMode == 10) {
                // incoming VT call, reject it since one active call (VT or voice) already exists
                // FIXME: will a new VT call comes if one VT call already exists?
                for (int i = 0; i < mTracker.MAX_CONNECTIONS; i++) {
                    Connection cn = mTracker.mConnections[i];
                    if (cn != null) {
                        mode = 1;
                        break;
                    }
                }
            } else if (callMode == 0) {
                // incoming voice call, reject new voice call since one VT call already exists
                for (int i = 0; i < mTracker.MAX_CONNECTIONS; i++) {
                    Connection cn = mTracker.mConnections[i];
                    if (cn != null && cn.isVideo()) {
                        mode = 1;
                        break;
                    }
                }
            } else {
                // the incoming call is neither VT nor voice call, reject it anyway.
                mode = 1;
            }
        }
        /// @}

        /// M: CC053: MoMS [Mobile Managerment] @{
        // 2. MT Phone Call Interception
        MobileManagermentResetIsBlocking();
        if (mode == 0) {
            MobileManagermentQueryPermission(incomingCallInfo[1], callMode, mTracker.mPhone.getSubId());
            if (MobileManagermentGetIsBlocking() || !shouldNotifyMtCall()) {
                mode = 1;
            }
            else {
                mode = 0;
            }
        }
        /// @}

        /* To raise PhoneAPP priority to avoid delaying incoming call screen to be showed, mtk04070, 20120307 */
        if (mode == 0) {
            pendingMTCallId = callId;
            pendingMTSeqNum = seqNumber;
            mTracker.mVoiceCallIncomingIndicationRegistrants.notifyRegistrants();
            log("notify mVoiceCallIncomingIndicationRegistrants " + pendingMTCallId + " " + pendingMTSeqNum);
        }

        /// M: CC059: Reject MT when another MT already exists via EAIC disapproval @{
        if (mode == 1) {
            DriverCall dc = new DriverCall();
            dc.isMT = true;
            dc.index = callId;
            dc.state = DriverCall.State.WAITING;

            mTracker.mCi.setCallIndication(mode, callId, seqNumber, null);

            /// M: For 3G VT only @{
            //dc.isVoice = true;
            if (callMode == 0) {
                dc.isVoice = true;
                dc.isVideo = false;
            } else if (callMode == 10) {
                dc.isVoice = false;
                dc.isVideo = true;
            } else {
                dc.isVoice = false;
                dc.isVideo = false;
            }
            /// @}
            dc.number = incomingCallInfo[1];
            dc.numberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
            dc.TOA = Integer.parseInt(incomingCallInfo[2]);
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);

            GsmConnection cn = new GsmConnection(mTracker.mPhone.getContext(), dc, mTracker, callId);
            cn.onReplaceDisconnect(DisconnectCause.INCOMING_MISSED);
        }
        /// @}
    }

    public void CallIndicationResponse(boolean accept) {
        int mode = 0;

        log("setIncomingCallIndicationResponse " + mode + " pendingMTCallId " + pendingMTCallId + " pendingMTSeqNum " + pendingMTSeqNum);

        if (accept) {
            int pid = Process.myPid();

            mode = 0;
            Process.setThreadPriority(pid, Process.THREAD_PRIORITY_DEFAULT - 10);
            log("Adjust the priority of process - " + pid + " to " + Process.getThreadPriority(pid));

            /// M: CC017: Forwarding number via EAIC @{
            /* EAIC message contains forwarding address(A calls B and it is forwarded to C,
                 C may receive forwarding number - B's phone number). */
            if (mForwardingAddress != null) {
                mContainForwardingAddress = true;
            }
            /// @}

        } else {
            mode = 1;
        }
        mTracker.mCi.setCallIndication(mode, pendingMTCallId, pendingMTSeqNum, null);
        pendingMTCallId = 0;
        pendingMTSeqNum = 0;
    }

    public void CallIndicationEnd() {

        /* To adjust PhoneAPP priority to normal, mtk04070, 20120307 */
        int pid = Process.myPid();
        if (Process.getThreadPriority(pid) != Process.THREAD_PRIORITY_DEFAULT) {
            Process.setThreadPriority(pid, Process.THREAD_PRIORITY_DEFAULT);
            log("Current priority = " + Process.getThreadPriority(pid));
        }
    }

    /// M: CC017: Forwarding number via EAIC @{
    /**
      *  To clear forwarding address variables
      */
    public void clearForwardingAddressVariables(int index) {
       if (mContainForwardingAddress &&
       (mForwardingAddressCallId == (index + 1))) {
          mContainForwardingAddress = false;
          mForwardingAddress = null;
          mForwardingAddressCallId = 0;
       }
    }

    /**
      *  To clear forwarding address variables
      */
    public void setForwardingAddressToConnection(int index, Connection conn) {
        if (mContainForwardingAddress &&
             (mForwardingAddress != null) &&
             (mForwardingAddressCallId == (index + 1))) {
             conn.setForwardingAddress(mForwardingAddress);
             log("Store forwarding address - " + mForwardingAddress);
             log("Get forwarding address - " + conn.getForwardingAddress());
             clearForwardingAddressVariables(index);
        }
    }

    private boolean shouldNotifyMtCall() {
        if (MTK_SWITCH_ANTENNA_SUPPORT) {
            Rlog.d(LOG_TAG, "shouldNotifyMtCall, mTracker.mPhone:" + mTracker.mPhone);
            Phone[] phones = PhoneFactory.getPhones();
            for (Phone phone : phones) {
                Rlog.d(LOG_TAG, "phone:" + phone + ", state:" + phone.getState());
                if (phone.getState() != PhoneConstants.State.IDLE && phone != mTracker.mPhone) {
                    Rlog.d(LOG_TAG, "shouldNotifyMtCall, another phone active, phone:" + phone
                            + ", state:" + phone.getState());
                    return false;
                }
            }
        }
        return true;
    }
}

