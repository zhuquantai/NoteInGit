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

import android.content.Context;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.SystemClock;
/// M: for OP01 customization. @{
import android.os.SystemProperties;
/// @}
/// M: for conference SRVCC. @{
import android.telecom.ConferenceParticipant;
/// @}
import android.telecom.Log;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;

import com.android.ims.ImsConferenceState;
import com.android.ims.ImsException;
import com.android.ims.ImsStreamMediaProfile;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.UUSInfo;

import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;

/// M: For VoLTE enhanced conference call. @{
import java.util.ArrayList;
/// @}
import java.util.List;
import java.util.Objects;

/**
 * {@hide}
 */
public class ImsPhoneConnection extends Connection {
    private static final String LOG_TAG = "ImsPhoneConnection";
    private static final boolean DBG = true;

    //***** Instance Variables

    private ImsPhoneCallTracker mOwner;
    private ImsPhoneCall mParent;
    private ImsCall mImsCall;

    /// M: ALPS02614972, move to Connection.java.
    //private String mPostDialString;      // outgoing calls only
    private boolean mDisconnected;

    /*
    int mIndex;          // index in ImsPhoneCallTracker.connections[], -1 if unassigned
                        // The GSM index is 1 + this
    */

    /*
     * These time/timespan values are based on System.currentTimeMillis(),
     * i.e., "wall clock" time.
     */
    private long mDisconnectTime;

    private int mNextPostDialChar;       // index into postDialString

    private int mCause = DisconnectCause.NOT_DISCONNECTED;
    private PostDialState mPostDialState = PostDialState.NOT_STARTED;
    private UUSInfo mUusInfo;
    private Handler mHandler;

    private PowerManager.WakeLock mPartialWakeLock;

    // The cached connect time of the connection when it turns into a conference.
    private long mConferenceConnectTime = 0;

    //***** Event Constants
    private static final int EVENT_DTMF_DONE = 1;
    private static final int EVENT_PAUSE_DONE = 2;
    private static final int EVENT_NEXT_POST_DIAL = 3;
    private static final int EVENT_WAKE_LOCK_TIMEOUT = 4;

    //***** Constants
    private static final int PAUSE_DELAY_MILLIS = 3 * 1000;
    private static final int WAKE_LOCK_TIMEOUT_MILLIS = 60*1000;

    /// M: @{
    // For VoLTE enhanced conference call.
    private ArrayList<String> mConfDialStrings = null;

    // For conference SRVCC.
    List<ConferenceParticipant> mConferenceParticipants = null;
    /// @}

    /// M: ALPS02136981. Prints debug logs for ImsPhone.
    private int mCallIdBeforeDisconnected = -1;

    //***** Inner Classes

    class MyHandler extends Handler {
        MyHandler(Looper l) {super(l);}

        @Override
        public void
        handleMessage(Message msg) {

            switch (msg.what) {
                case EVENT_NEXT_POST_DIAL:
                case EVENT_DTMF_DONE:
                case EVENT_PAUSE_DONE:
                    processNextPostDialChar();
                    break;
                case EVENT_WAKE_LOCK_TIMEOUT:
                    releaseWakeLock();
                    break;
            }
        }
    }

    //***** Constructors

    /** This is probably an MT call */
    /*package*/
    ImsPhoneConnection(Context context, ImsCall imsCall, ImsPhoneCallTracker ct,
           ImsPhoneCall parent, boolean isUnknown) {
        createWakeLock(context);
        acquireWakeLock();

        mOwner = ct;
        mHandler = new MyHandler(mOwner.getLooper());
        mImsCall = imsCall;

        if ((imsCall != null) && (imsCall.getCallProfile() != null)) {
            mAddress = imsCall.getCallProfile().getCallExtra(ImsCallProfile.EXTRA_OI);
            mCnapName = imsCall.getCallProfile().getCallExtra(ImsCallProfile.EXTRA_CNA);
            mNumberPresentation = ImsCallProfile.OIRToPresentation(
                    imsCall.getCallProfile().getCallExtraInt(ImsCallProfile.EXTRA_OIR));
            mCnapNamePresentation = ImsCallProfile.OIRToPresentation(
                    imsCall.getCallProfile().getCallExtraInt(ImsCallProfile.EXTRA_CNAP));
            updateMediaCapabilities(imsCall);
        } else {
            mNumberPresentation = PhoneConstants.PRESENTATION_UNKNOWN;
            mCnapNamePresentation = PhoneConstants.PRESENTATION_UNKNOWN;
        }

        mIsIncoming = !isUnknown;
        mCreateTime = System.currentTimeMillis();
        mUusInfo = null;

        //mIndex = index;

        updateWifiState();

        mParent = parent;
        mParent.attach(this,
                (mIsIncoming? ImsPhoneCall.State.INCOMING: ImsPhoneCall.State.DIALING));
    }

    /** This is an MO call, created when dialing */
    /*package*/
    ImsPhoneConnection(Context context, String dialString, ImsPhoneCallTracker ct,
            ImsPhoneCall parent) {
        createWakeLock(context);
        acquireWakeLock();

        mOwner = ct;
        mHandler = new MyHandler(mOwner.getLooper());

        mDialString = dialString;

        /// M: Ignore extraction for VoLTE SIP address which is NOT a PSTN phone number. @{
        mAddress = dialString;
        mPostDialString = "";
        if (!PhoneNumberUtils.isUriNumber(dialString)) {
            mAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString);
            mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        }
        /// @}

        //mIndex = -1;

        mIsIncoming = false;
        mCnapName = null;
        mCnapNamePresentation = PhoneConstants.PRESENTATION_ALLOWED;
        mNumberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
        mCreateTime = System.currentTimeMillis();

        mParent = parent;
        parent.attachFake(this, ImsPhoneCall.State.DIALING);
    }

    public void dispose() {
    }

    static boolean
    equalsHandlesNulls (Object a, Object b) {
        return (a == null) ? (b == null) : a.equals (b);
    }

    @Override
    public String getOrigDialString(){
        return mDialString;
    }

    @Override
    public ImsPhoneCall getCall() {
        return mParent;
    }

    @Override
    public long getDisconnectTime() {
        return mDisconnectTime;
    }

    @Override
    public long getHoldingStartTime() {
        return mHoldingStartTime;
    }

    @Override
    public long getHoldDurationMillis() {
        if (getState() != ImsPhoneCall.State.HOLDING) {
            // If not holding, return 0
            return 0;
        } else {
            return SystemClock.elapsedRealtime() - mHoldingStartTime;
        }
    }

    @Override
    public int getDisconnectCause() {
        return mCause;
    }

    public void setDisconnectCause(int cause) {
        mCause = cause;
    }

    @Override
    public String getVendorDisconnectCause() {
      return null;
    }

    public ImsPhoneCallTracker getOwner () {
        return mOwner;
    }

    @Override
    public ImsPhoneCall.State getState() {
        if (mDisconnected) {
            return ImsPhoneCall.State.DISCONNECTED;
        } else {
            return super.getState();
        }
    }

    @Override
    public void hangup() throws CallStateException {
        /// M: ALPS02136981. Prints debug logs for ImsPhone. @{
        if (mOwner != null) {
            mOwner.logDebugMessagesWithOpFormat("CC", "Hangup", this, "ImsphoneConnection.hangup");
        }
        /// @}

        if (!mDisconnected) {
            mOwner.hangup(this);
        } else {
            throw new CallStateException ("disconnected");
        }
    }

    @Override
    public void separate() throws CallStateException {
        throw new CallStateException ("not supported");
    }

    @Override
    public PostDialState getPostDialState() {
        return mPostDialState;
    }

    @Override
    public void proceedAfterWaitChar() {
        if (mPostDialState != PostDialState.WAIT) {
            Rlog.w(LOG_TAG, "ImsPhoneConnection.proceedAfterWaitChar(): Expected "
                    + "getPostDialState() to be WAIT but was " + mPostDialState);
            return;
        }

        setPostDialState(PostDialState.STARTED);

        processNextPostDialChar();
    }

    @Override
    public void proceedAfterWildChar(String str) {
        if (mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "ImsPhoneConnection.proceedAfterWaitChar(): Expected "
                    + "getPostDialState() to be WILD but was " + mPostDialState);
            return;
        }

        setPostDialState(PostDialState.STARTED);

        // make a new postDialString, with the wild char replacement string
        // at the beginning, followed by the remaining postDialString.

        StringBuilder buf = new StringBuilder(str);
        buf.append(mPostDialString.substring(mNextPostDialChar));
        mPostDialString = buf.toString();
        mNextPostDialChar = 0;
        if (Phone.DEBUG_PHONE) {
            Rlog.d(LOG_TAG, "proceedAfterWildChar: new postDialString is " +
                    mPostDialString);
        }

        processNextPostDialChar();
    }

    @Override
    public void cancelPostDial() {
        setPostDialState(PostDialState.CANCELLED);
    }

    /**
     * Called when this Connection is being hung up locally (eg, user pressed "end")
     */
    void
    onHangupLocal() {
        mCause = DisconnectCause.LOCAL;
    }

    /** Called when the connection has been disconnected */
    public boolean
    onDisconnect(int cause) {
        Rlog.d(LOG_TAG, "onDisconnect: cause=" + cause);
        if (mCause != DisconnectCause.LOCAL) mCause = cause;

        /// M: ALPS02459178. Handle rejected call as missed with OP01 requirement. @{
        String optr = SystemProperties.get("ro.operator.optr");
        if (optr != null && optr.equals("OP01")) {
            if (isIncoming() && getConnectTime() == 0 && mCause == DisconnectCause.LOCAL) {
                mCause = DisconnectCause.INCOMING_REJECTED;
            }
        }
        /// @}

        return onDisconnect();
    }

    /*package*/ boolean
    onDisconnect() {
        boolean changed = false;

        if (!mDisconnected) {
            //mIndex = -1;

            mDisconnectTime = System.currentTimeMillis();
            mDuration = SystemClock.elapsedRealtime() - mConnectTimeReal;
            mDisconnected = true;

            mOwner.mPhone.notifyDisconnect(this);

            if (mParent != null) {
                changed = mParent.connectionDisconnected(this);
            } else {
                Rlog.d(LOG_TAG, "onDisconnect: no parent");
            }

            /// M: ALPS02136981. Prints debug logs for ImsPhone. @{
            //Cache call ID before close mImsCall.
            mCallIdBeforeDisconnected = getCallId();
            /// @}

            if (mImsCall != null) mImsCall.close();
            mImsCall = null;
        }
        releaseWakeLock();

        return changed;
    }

    /**
     * An incoming or outgoing call has connected
     */
    void
    onConnectedInOrOut() {
        mConnectTime = System.currentTimeMillis();
        mConnectTimeReal = SystemClock.elapsedRealtime();
        mDuration = 0;

        if (Phone.DEBUG_PHONE) {
            Rlog.d(LOG_TAG, "onConnectedInOrOut: connectTime=" + mConnectTime);
        }

        if (!mIsIncoming) {
            // outgoing calls only
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    /*package*/ void
    onStartedHolding() {
        mHoldingStartTime = SystemClock.elapsedRealtime();
    }
    /**
     * Performs the appropriate action for a post-dial char, but does not
     * notify application. returns false if the character is invalid and
     * should be ignored
     */
    private boolean
    processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            mOwner.sendDtmf(c, mHandler.obtainMessage(EVENT_DTMF_DONE));
        } else if (c == PhoneNumberUtils.PAUSE) {
            // From TS 22.101:
            // It continues...
            // Upon the called party answering the UE shall send the DTMF digits
            // automatically to the network after a delay of 3 seconds( 20 ).
            // The digits shall be sent according to the procedures and timing
            // specified in 3GPP TS 24.008 [13]. The first occurrence of the
            // "DTMF Control Digits Separator" shall be used by the ME to
            // distinguish between the addressing digits (i.e. the phone number)
            // and the DTMF digits. Upon subsequent occurrences of the
            // separator,
            // the UE shall pause again for 3 seconds ( 20 ) before sending
            // any further DTMF digits.
            mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_PAUSE_DONE),
                    PAUSE_DELAY_MILLIS);
        } else if (c == PhoneNumberUtils.WAIT) {
            setPostDialState(PostDialState.WAIT);
        } else if (c == PhoneNumberUtils.WILD) {
            setPostDialState(PostDialState.WILD);
        } else {
            return false;
        }

        return true;
    }

    @Override
    public String
    getRemainingPostDialString() {
        if (mPostDialState == PostDialState.CANCELLED
            || mPostDialState == PostDialState.COMPLETE
            || mPostDialString == null
            || mPostDialString.length() <= mNextPostDialChar
        ) {
            return "";
        }

        return mPostDialString.substring(mNextPostDialChar);
    }

    @Override
    protected void finalize()
    {
        releaseWakeLock();
    }

    private void
    processNextPostDialChar() {
        char c = 0;
        Registrant postDialHandler;

        if (mPostDialState == PostDialState.CANCELLED) {
            //Rlog.d(LOG_TAG, "##### processNextPostDialChar: postDialState == CANCELLED, bail");
            return;
        }

        if (mPostDialString == null || mPostDialString.length() <= mNextPostDialChar) {
            setPostDialState(PostDialState.COMPLETE);

            // notifyMessage.arg1 is 0 on complete
            c = 0;
        } else {
            boolean isValid;

            setPostDialState(PostDialState.STARTED);

            c = mPostDialString.charAt(mNextPostDialChar++);

            isValid = processPostDialChar(c);

            if (!isValid) {
                // Will call processNextPostDialChar
                mHandler.obtainMessage(EVENT_NEXT_POST_DIAL).sendToTarget();
                // Don't notify application
                Rlog.e(LOG_TAG, "processNextPostDialChar: c=" + c + " isn't valid!");
                return;
            }
        }

        notifyPostDialListenersNextChar(c);

        // TODO: remove the following code since the handler no longer executes anything.
        postDialHandler = mOwner.mPhone.mPostDialHandler;

        Message notifyMessage;

        if (postDialHandler != null
                && (notifyMessage = postDialHandler.messageForRegistrant()) != null) {
            // The AsyncResult.result is the Connection object
            PostDialState state = mPostDialState;
            AsyncResult ar = AsyncResult.forMessage(notifyMessage);
            ar.result = this;
            ar.userObj = state;

            // arg1 is the character that was/is being processed
            notifyMessage.arg1 = c;

            //Rlog.v(LOG_TAG,
            //      "##### processNextPostDialChar: send msg to postDialHandler, arg1=" + c);
            notifyMessage.sendToTarget();
        }
    }

    /**
     * Set post dial state and acquire wake lock while switching to "started"
     * state, the wake lock will be released if state switches out of "started"
     * state or after WAKE_LOCK_TIMEOUT_MILLIS.
     * @param s new PostDialState
     */
    private void setPostDialState(PostDialState s) {
        if (mPostDialState != PostDialState.STARTED
                && s == PostDialState.STARTED) {
            acquireWakeLock();
            Message msg = mHandler.obtainMessage(EVENT_WAKE_LOCK_TIMEOUT);
            mHandler.sendMessageDelayed(msg, WAKE_LOCK_TIMEOUT_MILLIS);
        } else if (mPostDialState == PostDialState.STARTED
                && s != PostDialState.STARTED) {
            mHandler.removeMessages(EVENT_WAKE_LOCK_TIMEOUT);
            releaseWakeLock();
        }
        mPostDialState = s;
        notifyPostDialListeners();
    }

    private void
    createWakeLock(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG);
    }

    private void
    acquireWakeLock() {
        Rlog.d(LOG_TAG, "acquireWakeLock");
        mPartialWakeLock.acquire();
    }

    void
    releaseWakeLock() {
        synchronized(mPartialWakeLock) {
            if (mPartialWakeLock.isHeld()) {
                Rlog.d(LOG_TAG, "releaseWakeLock");
                mPartialWakeLock.release();
            }
        }
    }

    @Override
    public int getNumberPresentation() {
        return mNumberPresentation;
    }

    @Override
    public UUSInfo getUUSInfo() {
        return mUusInfo;
    }

    @Override
    public Connection getOrigConnection() {
        return null;
    }

    @Override
    public boolean isMultiparty() {
        return mImsCall != null && mImsCall.isMultiparty();
    }

    /**
     * Where {@link #isMultiparty()} is {@code true}, determines if this {@link ImsCall} is the
     * origin of the conference call (i.e. {@code #isConferenceHost()} is {@code true}), or if this
     * {@link ImsCall} is a member of a conference hosted on another device.
     *
     * @return {@code true} if this call is the origin of the conference call it is a member of,
     *      {@code false} otherwise.
     */
    public boolean isConferenceHost() {
        if (mImsCall == null) {
            return false;
        }
        return mImsCall.isConferenceHost();
    }

    /*package*/ ImsCall getImsCall() {
        return mImsCall;
    }

    /*package*/ void setImsCall(ImsCall imsCall) {
        mImsCall = imsCall;
    }

    /*package*/ void changeParent(ImsPhoneCall parent) {
        mParent = parent;
    }

    /**
     * @return {@code true} if the {@link ImsPhoneConnection} or its media capabilities have been
     *     changed, and {@code false} otherwise.
     */
    /*package*/ boolean update(ImsCall imsCall, ImsPhoneCall.State state) {
        if (state == ImsPhoneCall.State.ACTIVE) {
            // If the state of the call is active, but there is a pending request to the RIL to hold
            // the call, we will skip this update.  This is really a signalling delay or failure
            // from the RIL, but we will prevent it from going through as we will end up erroneously
            // making this call active when really it should be on hold.
            if (imsCall.isPendingHold()) {
                Rlog.w(LOG_TAG, "update : state is ACTIVE, but call is pending hold, skipping");
                return false;
            }

            if (mParent.getState().isRinging() || mParent.getState().isDialing()) {
                onConnectedInOrOut();
            }

            if (mParent.getState().isRinging() || mParent == mOwner.mBackgroundCall) {
                //mForegroundCall should be IDLE
                //when accepting WAITING call
                //before accept WAITING call,
                //the ACTIVE call should be held ahead

                /// M: ALPS01979162. @{
                if (mParent != mOwner.mHandoverCall) {
                    Rlog.d(LOG_TAG, "update() - Switch Connection to foreground call:" + this);
                    /// @}
                    mParent.detach(this);
                    mParent = mOwner.mForegroundCall;
                    mParent.attach(this);
                }
            }
        } else if (state == ImsPhoneCall.State.HOLDING) {
            onStartedHolding();
        }

        /// M: ALPS02256671. For PAU information changed. @{
        updatePauInfo(imsCall);
        /// @}

        boolean updateParent = mParent.update(this, imsCall, state);
        boolean updateWifiState = updateWifiState();
        boolean updateAddressDisplay = updateAddressDisplay(imsCall);
        boolean updateMediaCapabilities = updateMediaCapabilities(imsCall);

        return updateParent || updateWifiState || updateAddressDisplay || updateMediaCapabilities;
    }

    @Override
    public int getPreciseDisconnectCause() {
        return 0;
    }

    /**
     * Notifies this Connection of a request to disconnect a participant of the conference managed
     * by the connection.
     *
     * @param endpoint the {@link android.net.Uri} of the participant to disconnect.
     */
    @Override
    public void onDisconnectConferenceParticipant(Uri endpoint) {
        /// M: ALPS02136981. Prints debug logs for ImsPhone. @{
        if (mOwner != null) {
            mOwner.logDebugMessagesWithOpFormat("CC", "RemoveMember", this, " remove: " + endpoint);
        }
        /// @}

        ImsCall imsCall = getImsCall();
        if (imsCall == null) {
            return;
        }
        try {
            imsCall.removeParticipants(new String[]{endpoint.toString()});
        } catch (ImsException e) {
            // No session in place -- no change
            Rlog.e(LOG_TAG, "onDisconnectConferenceParticipant: no session in place. "+
                    "Failed to disconnect endpoint = " + endpoint);
        }
    }

    /// M: For VoLTE enhanced conference call. @{
    /**
     * Invite one or more participants to the conference managed by this connection.
     * @param numbers the numbers to invite into this conference.
     * @hide
     */
    public void onInviteConferenceParticipants(List<String> numbers) {
        /// M: ALPS02136981. Prints debug logs for ImsPhone. @{
        StringBuilder sb = new StringBuilder();
        for (String number : numbers) {
            sb.append(number);
            sb.append(", ");
        }
        if (mOwner != null) {
            mOwner.logDebugMessagesWithOpFormat("CC", "AddMember", this,
                    " invite with " + sb.toString());
        }
        /// @}

        ImsCall imsCall = getImsCall();
        if (imsCall == null) {
            return;
        }

        ArrayList<String> list = new ArrayList<String>();
        list.addAll(numbers);
        String[] participants = (String[]) list.toArray(new String[list.size()]);
        try {
            imsCall.inviteParticipants(participants);
        } catch (ImsException e) {
            Rlog.e(LOG_TAG,
                "inviteConferenceParticipants: no call session and fail to invite participants "
                + participants);
        }
    }
    /// @}

    /**
     * Sets the conference connect time.  Used when an {@code ImsConference} is created to out of
     * this phone connection.
     *
     * @param conferenceConnectTime The conference connect time.
     */
    public void setConferenceConnectTime(long conferenceConnectTime) {
        mConferenceConnectTime = conferenceConnectTime;
    }

    /**
     * @return The conference connect time.
     */
    public long getConferenceConnectTime() {
        return mConferenceConnectTime;
    }

    /**
     * Check for a change in the address display related fields for the {@link ImsCall}, and
     * update the {@link ImsPhoneConnection} with this information.
     *
     * @param imsCall The call to check for changes in address display fields.
     * @return Whether the address display fields have been changed.
     */
    private boolean updateAddressDisplay(ImsCall imsCall) {
        if (imsCall == null) {
            return false;
        }

        boolean changed = false;
        ImsCallProfile callProfile = imsCall.getCallProfile();
        if (callProfile != null) {
            String address = callProfile.getCallExtra(ImsCallProfile.EXTRA_OI);
            String name = callProfile.getCallExtra(ImsCallProfile.EXTRA_CNA);
            int nump = ImsCallProfile.OIRToPresentation(
                    callProfile.getCallExtraInt(ImsCallProfile.EXTRA_OIR));
            /// M: ALPS02583234, always show the callee's number. @{
            if (!mIsIncoming) {
                nump = PhoneConstants.PRESENTATION_ALLOWED;
            }
            /// @}
            int namep = ImsCallProfile.OIRToPresentation(
                    callProfile.getCallExtraInt(ImsCallProfile.EXTRA_CNAP));
            if (Phone.DEBUG_PHONE) {
                Rlog.d(LOG_TAG, "address = " +  address + " name = " + name +
                        " nump = " + nump + " namep = " + namep);
            }
            if(equalsHandlesNulls(mAddress, address)) {
                mAddress = address;
                changed = true;
            }
            if (TextUtils.isEmpty(name)) {
                if (!TextUtils.isEmpty(mCnapName)) {
                    mCnapName = "";
                    changed = true;
                }
            } else if (!name.equals(mCnapName)) {
                mCnapName = name;
                changed = true;
            }
            if (mNumberPresentation != nump) {
                mNumberPresentation = nump;
                changed = true;
            }
            if (mCnapNamePresentation != namep) {
                mCnapNamePresentation = namep;
                changed = true;
            }
        }
        return changed;
    }

    /// M: ALPS02256671. For PAU information changed. @{
    /**
     * Check for a change in the PAU information (P-Asserted-Identity URI)
     * for the {@link ImsCall}, and update the {@link ImsPhoneConnection} with this information.
     *
     * @param imsCall The call to check for changes in PAU information.
     * @hide
     */
    boolean updatePauInfo(ImsCall imsCall) {
        if (imsCall == null) {
            return false;
        }

        boolean changed = false;
        ImsCallProfile callProfile = imsCall.getCallProfile();
        if (callProfile != null) {
            String pau = callProfile.getCallExtra(ImsCallProfile.EXTRA_PAU, "");
            if (!equalsHandlesNulls(mPau, pau)) {
                Rlog.d(LOG_TAG, "updatePauInfo: new pau=" + pau + " old pau=" + mPau);
                mPau = pau;
                changed = true;
                notifyPauInfoUpdated(pau);
            }
        }
        return changed;
    }
    /// @}

    /**
     * Check for a change in the video capabilities and audio quality for the {@link ImsCall}, and
     * update the {@link ImsPhoneConnection} with this information.
     *
     * @param imsCall The call to check for changes in media capabilities.
     * @return Whether the media capabilities have been changed.
     */
    public boolean updateMediaCapabilities(ImsCall imsCall) {
        if (imsCall == null) {
            return false;
        }

        boolean changed = false;

        try {
            // The actual call profile (negotiated between local and peer).
            ImsCallProfile negotiatedCallProfile = imsCall.getCallProfile();
            // The capabilities of the local device.
            ImsCallProfile localCallProfile = imsCall.getLocalCallProfile();
            // The capabilities of the peer device.
            ImsCallProfile remoteCallProfile = imsCall.getRemoteCallProfile();

            if (negotiatedCallProfile != null) {
                int oldVideoState = getVideoState();
                int newVideoState = ImsCallProfile
                        .getVideoStateFromImsCallProfile(negotiatedCallProfile);

                if (oldVideoState != newVideoState) {
                    setVideoState(newVideoState);
                    changed = true;
                }
            }

            if (localCallProfile != null) {
                int callType = localCallProfile.mCallType;

                boolean newLocalVideoCapable = callType == ImsCallProfile.CALL_TYPE_VT;
                if (isLocalVideoCapable() != newLocalVideoCapable) {
                    setLocalVideoCapable(newLocalVideoCapable);
                    changed = true;
                }
            }

            if (remoteCallProfile != null) {
                    boolean newRemoteVideoCapable = remoteCallProfile.mCallType
                            == ImsCallProfile.CALL_TYPE_VT;

                    if (isRemoteVideoCapable() != newRemoteVideoCapable) {
                        setRemoteVideoCapable(newRemoteVideoCapable);
                        changed = true;
                    }
            }

            int newAudioQuality =
                    getAudioQualityFromCallProfile(localCallProfile, remoteCallProfile);
            if (getAudioQuality() != newAudioQuality) {
                setAudioQuality(newAudioQuality);
                changed = true;
            }
        } catch (ImsException e) {
            // No session in place -- no change
        }

        return changed;
    }

    /**
     * Check for a change in the wifi state of the ImsPhoneCallTracker and update the
     * {@link ImsPhoneConnection} with this information.
     *
     * @return Whether the ImsPhoneCallTracker's usage of wifi has been changed.
     */
    public boolean updateWifiState() {
        Rlog.d(LOG_TAG, "updateWifiState: " + mOwner.isVowifiEnabled());
        if (isWifi() != mOwner.isVowifiEnabled()) {
            setWifi(mOwner.isVowifiEnabled());
            return true;
        }
        return false;
    }

    /**
     * Determines the {@link ImsPhoneConnection} audio quality based on the local and remote
     * {@link ImsCallProfile}. If indicate a HQ audio call if the local stream profile
     * indicates AMR_WB or EVRC_WB and there is no remote restrict cause.
     *
     * @param localCallProfile The local call profile.
     * @param remoteCallProfile The remote call profile.
     * @return The audio quality.
     */
    private int getAudioQualityFromCallProfile(
            ImsCallProfile localCallProfile, ImsCallProfile remoteCallProfile) {
        if (localCallProfile == null || remoteCallProfile == null
                || localCallProfile.mMediaProfile == null) {
            return AUDIO_QUALITY_STANDARD;
        }

        boolean isHighDef = (localCallProfile.mMediaProfile.mAudioQuality
                        == ImsStreamMediaProfile.AUDIO_QUALITY_AMR_WB
                || localCallProfile.mMediaProfile.mAudioQuality
                        == ImsStreamMediaProfile.AUDIO_QUALITY_EVRC_WB)
                && remoteCallProfile.mRestrictCause == ImsCallProfile.CALL_RESTRICT_CAUSE_NONE;
        return isHighDef ? AUDIO_QUALITY_HIGH_DEFINITION : AUDIO_QUALITY_STANDARD;
    }

    @Override
    public Bundle getExtras() {
        Bundle extras = null;
        final ImsCall call = getImsCall();

        if (call != null) {
            final ImsCallProfile callProfile = call.getCallProfile();
            if (callProfile != null) {
                extras = callProfile.mCallExtras;
            }
        }
        if (extras == null) {
            if (DBG) Rlog.d(LOG_TAG, "Call profile extras are null.");
            return null;
        }
        return extras;
    }

    /**
     * Provides a string representation of the {@link ImsPhoneConnection}.  Primarily intended for
     * use in log statements.
     *
     * @return String representation of call.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ImsPhoneConnection objId: ");
        sb.append(System.identityHashCode(this));
        sb.append(" address:");
        sb.append(Log.pii(getAddress()));
        sb.append(" ImsCall:");
        if (mImsCall == null) {
            sb.append("null");
        } else {
            sb.append(mImsCall);
        }
        sb.append("]");
        /// M: @{
        sb.append(" state:" + getState());
        sb.append(" mParent:");
        sb.append(getParentCallName());
        /// @}
        return sb.toString();
    }

    /// M:  @{
    /**
     get Call Id of this connection. Used when SRVCC, GSMCallTracker needs to know
     the mapping between old Ims connection and new Gsm Connection, according to Call-ID.
     @return Call Id
     @hide
    */
    public int getCallId() {
        ImsCall call = getImsCall();
        if (call == null || call.getCallSession() == null) {
            return -1;
        }

        String callId = call.getCallSession().getCallId();
        if (callId == null) {
            Rlog.d(LOG_TAG, "Abnormal! Call Id = null");
            return -1;
        }

        return Integer.parseInt(callId);
    }

    /**
     * Now the connection is disconnected, get the Call Id before disconnected.
     * @return call ID
     * @hide
     */
    int getCallIdBeforeDisconnected() {
        return mCallIdBeforeDisconnected;
    }

    private ImsPhoneCall.State getCallStateFromConferenceState(String status) {
        if (status.equals(ImsConferenceState.STATUS_ALERTING)
                || status.equals(ImsConferenceState.STATUS_PENDING)
                || status.equals(ImsConferenceState.STATUS_DIALING_OUT)
                || status.equals(ImsConferenceState.STATUS_DIALING_IN)) {
            return ImsPhoneCall.State.ALERTING;
        } else if (status.equals(ImsConferenceState.STATUS_CONNECT_FAIL)
                || status.equals(ImsConferenceState.STATUS_DISCONNECTED)) {
            return ImsPhoneCall.State.DISCONNECTED;
        } else if (status.equals(ImsConferenceState.STATUS_ON_HOLD)) {
            return ImsPhoneCall.State.HOLDING;
        } else {
            // STATUS_CONNECTED or others
            return ImsPhoneCall.State.ACTIVE;
        }
    }

    /**
    * For VoLTE enhanced conference call.
    * @param dialStrings the dial strings of multiple MO.
    */
    void setConfDialStrings(ArrayList<String> dialStrings) {
        mConfDialStrings = dialStrings;
    }

    /**
    * For VoLTE enhanced conference call.
    * @return the array of the conference dial strings.
    */
    ArrayList<String> getConfDialStrings() {
        return mConfDialStrings;
    }

    String getParentCallName() {
        if (mOwner == null) {
            return "Unknown";
        }

        if (mParent == mOwner.mForegroundCall) {
            return "Foreground Call";
        } else if (mParent == mOwner.mBackgroundCall) {
            return "Background Call";
        } else if (mParent == mOwner.mRingingCall) {
            return "Ringing Call";
        } else if (mParent == mOwner.mHandoverCall) {
            return "Handover Call";
        } else {
            return "Abnormal";
        }
    }

    @Override
    public void updateConferenceParticipants(List<ConferenceParticipant> conferenceParticipants) {
        mConferenceParticipants = conferenceParticipants;
        super.updateConferenceParticipants(conferenceParticipants);
    }

    /**
     * After conference SRVCC, we need to restore the participants' address from XML.
     * @param index the index(order) in the XML, start from 0.
     * @return String the conference participant's address.
     * @hide
     */
    public String getConferenceParticipantAddress(int index) {
        String emptyAddress = "";

        if (mConferenceParticipants == null) {
            Rlog.d(LOG_TAG, "getConferenceParticipantAddress(): no XML information");
            return emptyAddress;
        }

        if (index < 0 || index + 1 >= mConferenceParticipants.size()) {
            Rlog.d(LOG_TAG, "getConferenceParticipantAddress(): invalid index");
            return emptyAddress;
        }

        // ToDo: how to know which one is the host? we assume the first one is always host.
        ConferenceParticipant participant = mConferenceParticipants.get(index + 1);
        if (participant == null) {
            Rlog.d(LOG_TAG, "getConferenceParticipantAddress(): empty participant info");
            return emptyAddress;
        }

        Uri userEntity = participant.getHandle();
        Rlog.d(LOG_TAG, "getConferenceParticipantAddress(): ret=" + userEntity);
        return userEntity.toString();
    }

    /**
     * Returns whether the ImsPhoneConnection was a conference incoming call.
     * @return true if ImsPhoneConnection is a conference incoming call.
     * @hide
     */
    @Override
    public boolean isIncomingCallMultiparty() {
        return mImsCall != null && mImsCall.isIncomingCallMultiparty();
    }
    /// @}

    /// M: ALPS02067267. @{
    /**
     * When conference SRVCC, callTracker.notifySrvccState() needs to distinguish it is
     * host or participant, and apply different mechanism.
     */
    void setConferenceAsHost() {
        Rlog.d(LOG_TAG, "set is conference host connection: " + this);
        mIsIncoming = false;
    }
    /// @}
    /// M: Update extras for ViLte. @{
    boolean updateExtras(ImsCall imsCall) {
        if (imsCall == null) {
            return false;
        }
        final ImsCallProfile callProfile = imsCall.getCallProfile();
        final Bundle extras = callProfile != null ? callProfile.mCallExtras : null;
        if (extras == null && DBG) {
            Rlog.d(LOG_TAG, "Call profile extras are null.");
        }
        /// M: Todo: remove the logs. @{
        Rlog.d(LOG_TAG, "update extras: " + extras);

        final boolean changed = !areBundlesEqual(extras, mExtras);
        if (changed) {
            setConnectionExtras(extras);
        }
        return changed;
    }

    private static boolean areBundlesEqual(Bundle extras, Bundle newExtras) {
        if (extras == null || newExtras == null) {
            return extras == newExtras;
        }

        if (extras.size() != newExtras.size()) {
            return false;
        }

        for(String key : extras.keySet()) {
            if (key != null) {
                final Object value = extras.get(key);
                final Object newValue = newExtras.get(key);
                if (!Objects.equals(value, newValue)) {
                    return false;
                }
            }
        }
        return true;
    }
    /// @}
}

