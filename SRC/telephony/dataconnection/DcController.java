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

package com.android.internal.telephony.dataconnection;

import android.content.Context;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.net.LinkProperties.CompareResult;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.Rlog;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.dataconnection.DataConnection.UpdateLinkPropertyResult;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import android.text.TextUtils;

/** M: start */
import android.net.LinkProperties;
/** M: end */


/**
 * Data Connection Controller which is a package visible class and controls
 * multiple data connections. For instance listening for unsolicited messages
 * and then demultiplexing them to the appropriate DC.
 */
class DcController extends StateMachine {
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    private PhoneBase mPhone;
    private DcTrackerBase mDct;
    private DcTesterDeactivateAll mDcTesterDeactivateAll;

    // package as its used by Testing code
    ArrayList<DataConnection> mDcListAll = new ArrayList<DataConnection>();
    private HashMap<Integer, DataConnection> mDcListActiveByCid =
            new HashMap<Integer, DataConnection>();

    /**
     * Constants for the data connection activity:
     * physical link down/up
     *
     * TODO: Move to RILConstants.java
     */
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT = 1;
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;
    static final int DATA_CONNECTION_ACTIVE_UNKNOWN = Integer.MAX_VALUE;

    // One of the DATA_CONNECTION_ACTIVE_XXX values
    int mOverallDataConnectionActiveState = DATA_CONNECTION_ACTIVE_UNKNOWN;

    private DccDefaultState mDccDefaultState = new DccDefaultState();

    TelephonyManager mTelephonyManager;
    private PhoneStateListener mPhoneStateListener;

    //mExecutingCarrierChange tracks whether the phone is currently executing
    //carrier network change
    private volatile boolean mExecutingCarrierChange;

    /// M: [C2K] add for PCModem feature, the feature is only enabled in CDMA.
    private static final int DATA_CONNECTION_PC_MODEM_CONNECTED = 3;
    private static final int DATA_CONNECTION_PC_MODEM_DISCONNECTED = 4;
    private static final String REASON_PC_MODEM_CONNECTED = "pcModemConnected";
    private static final String REASON_PC_MODEM_DISCONNECTED = "pcModemDisconnected";
    private boolean mPcModemConnected;

    /**
     * Constructor.
     *
     * @param name to be used for the Controller
     * @param phone the phone associated with Dcc and Dct
     * @param dct the DataConnectionTracker associated with Dcc
     * @param handler defines the thread/looper to be used with Dcc
     */
    private DcController(String name, PhoneBase phone, DcTrackerBase dct,
            Handler handler) {
        super(name, handler);
        setLogRecSize(300);
        log("E ctor");
        mPhone = phone;
        mDct = dct;
        addState(mDccDefaultState);
        setInitialState(mDccDefaultState);
        log("X ctor");

        mPhoneStateListener = new PhoneStateListener(handler.getLooper()) {
            @Override
            public void onCarrierNetworkChange(boolean active) {
                mExecutingCarrierChange = active;
            }
        };

        mTelephonyManager = (TelephonyManager) phone.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if(mTelephonyManager != null) {
            mTelephonyManager.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_CARRIER_NETWORK_CHANGE);
        }
    }

    static DcController makeDcc(PhoneBase phone, DcTrackerBase dct, Handler handler) {
        DcController dcc = new DcController("Dcc", phone, dct, handler);
        dcc.start();
        return dcc;
    }

    void dispose() {
        log("dispose: call quiteNow()");
        if(mTelephonyManager != null) mTelephonyManager.listen(mPhoneStateListener, 0);
        quitNow();
    }

    void addDc(DataConnection dc) {
        mDcListAll.add(dc);
    }

    void removeDc(DataConnection dc) {
        mDcListActiveByCid.remove(dc.mCid);
        mDcListAll.remove(dc);
    }

    void addActiveDcByCid(DataConnection dc) {
        if (DBG && dc.mCid < 0) {
            log("addActiveDcByCid dc.mCid < 0 dc=" + dc);
        }
        mDcListActiveByCid.put(dc.mCid, dc);
    }

    void removeActiveDcByCid(DataConnection dc) {
        DataConnection removedDc = null;
        try {
            removedDc = mDcListActiveByCid.remove(dc.mCid);
            if (DBG && removedDc == null) {
                log("removeActiveDcByCid removedDc=null dc.mCid=" + dc.mCid);
            }
        } catch (ConcurrentModificationException e) {
            log("concurrentModificationException happened!!");
        }
    }

    boolean isExecutingCarrierChange() {
        return mExecutingCarrierChange;
    }

    // MTK
    void getDataCallListForSimLoaded() {
        log("getDataCallList");
        mPhone.mCi.getDataCallList(obtainMessage(
                DataConnection.EVENT_DATA_STATE_CHANGED_FOR_LOADED));
    }

    private class DccDefaultState extends State {
        @Override
        public void enter() {
            mPhone.mCi.registerForRilConnected(getHandler(),
                    DataConnection.EVENT_RIL_CONNECTED, null);
            mPhone.mCi.registerForDataNetworkStateChanged(getHandler(),
                    DataConnection.EVENT_DATA_STATE_CHANGED, null);
            if (Build.IS_DEBUGGABLE) {
                mDcTesterDeactivateAll =
                        new DcTesterDeactivateAll(mPhone, DcController.this, getHandler());
            }
        }

        @Override
        public void exit() {
            if (mPhone != null) {
                mPhone.mCi.unregisterForRilConnected(getHandler());
                mPhone.mCi.unregisterForDataNetworkStateChanged(getHandler());
            }
            if (mDcTesterDeactivateAll != null) {
                mDcTesterDeactivateAll.dispose();
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case DataConnection.EVENT_RIL_CONNECTED:
                    ar = (AsyncResult)msg.obj;
                    if (ar.exception == null) {
                        if (DBG) {
                            log("DccDefaultState: msg.what=EVENT_RIL_CONNECTED mRilVersion=" +
                                ar.result);
                        }
                    } else {
                        log("DccDefaultState: Unexpected exception on EVENT_RIL_CONNECTED");
                    }
                    break;

                case DataConnection.EVENT_DATA_STATE_CHANGED:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        onDataStateChanged((ArrayList<DataCallResponse>) ar.result, false);
                    } else {
                        log("DccDefaultState: EVENT_DATA_STATE_CHANGED:" +
                                    " exception; likely radio not available, ignore");
                    }
                    break;
                case DataConnection.EVENT_DATA_STATE_CHANGED_FOR_LOADED:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        onDataStateChanged((ArrayList<DataCallResponse>) ar.result, true);
                    } else {
                        log("DccDefaultState: EVENT_DATA_STATE_CHANGED:" +
                                    " exception; likely radio not available, ignore");
                    }
                    mDct.sendMessage(obtainMessage(DctConstants.EVENT_SETUP_DATA_WHEN_LOADED));
                    break;
            }
            return HANDLED;
        }

        /**
         * Process the new list of "known" Data Calls
         * @param dcsList as sent by RIL_UNSOL_DATA_CALL_LIST_CHANGED
         */
        private void onDataStateChanged(ArrayList<DataCallResponse> dcsList,
                boolean isRecordLoaded) {
            if (DBG) {
                lr("onDataStateChanged: dcsList=" + dcsList
                        + " mDcListActiveByCid=" + mDcListActiveByCid);
            }
            if (VDBG) {
                log("onDataStateChanged: mDcListAll=" + mDcListAll);
            }

            /// M: [IRAT] update DC cid for IRAT
            log("onDataStateChanged: updateDcInfo");
            updateDcInfo(dcsList);

            /// M: [C2K] pre-check data state to see if it is caused by PCModem.
            if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                if (preCheckDataState(dcsList)) {
                    log("onDataStateChanged: pre-check state return for CDMA.");
                    return;
                }
            }

            // Create hashmap of cid to DataCallResponse
            HashMap<Integer, DataCallResponse> dataCallResponseListByCid =
                    new HashMap<Integer, DataCallResponse>();
            for (DataCallResponse dcs : dcsList) {
                dataCallResponseListByCid.put(dcs.cid, dcs);
            }

            // Add a DC that is active but not in the
            // dcsList to the list of DC's to retry
            ArrayList<DataConnection> dcsToRetry = new ArrayList<DataConnection>();
            for (DataConnection dc : mDcListActiveByCid.values()) {
                if (dataCallResponseListByCid.get(dc.mCid) == null) {
                    if (DBG) log("onDataStateChanged: add to retry dc=" + dc);
                    dcsToRetry.add(dc);
                }
            }
            if (DBG) log("onDataStateChanged: dcsToRetry=" + dcsToRetry);

            // Find which connections have changed state and send a notification or cleanup
            // and any that are in active need to be retried.
            ArrayList<ApnContext> apnsToCleanup = new ArrayList<ApnContext>();

            boolean isAnyDataCallDormant = false;
            boolean isAnyDataCallActive = false;

            for (DataCallResponse newState : dcsList) {

                DataConnection dc = mDcListActiveByCid.get(newState.cid);
                if (dc == null) {
                    // UNSOL_DATA_CALL_LIST_CHANGED arrived before SETUP_DATA_CALL completed.
                    loge("onDataStateChanged: no associated DC yet, ignore");

                    // MTK: Deactivate unlinked PDP context
                    loge("Deactivate unlinked PDP context.");
                    mDct.deactivatePdpByCid(newState.cid);
                    // MTK

                    continue;
                }

                if (dc.mApnContexts.size() == 0) {
                    if (DBG) loge("onDataStateChanged: no connected apns, ignore");
                } else {
                    // Determine if the connection/apnContext should be cleaned up
                    // or just a notification should be sent out.
                    if (DBG) log("onDataStateChanged: Found ConnId=" + newState.cid
                            + " newState=" + newState.toString());
                    if (newState.active == DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE) {
                        if (mDct.mIsCleanupRequired) {
                            apnsToCleanup.addAll(dc.mApnContexts);
                            mDct.mIsCleanupRequired = false;
                        } else {
                            DcFailCause failCause = DcFailCause.fromInt(newState.status);
                            if (DBG) log("onDataStateChanged: inactive failCause=" + failCause);
                            if (failCause.isRestartRadioFail()) {
                                if (DBG) log("onDataStateChanged: X restart radio");
                                mDct.sendRestartRadio();
                            } else if (mDct.isPermanentFail(failCause)) {
                                if (DBG) log("onDataStateChanged: inactive, add to cleanup list");
                                apnsToCleanup.addAll(dc.mApnContexts);
                            } else {
                                if (DBG) log("onDataStateChanged: inactive, add to retry list");
                                dcsToRetry.add(dc);
                            }
                        }
                    } else {
                        // Its active so update the DataConnections link properties
                        UpdateLinkPropertyResult result = dc.updateLinkProperty(newState);
                        if (result.oldLp.equals(result.newLp)) {
                            if (DBG) log("onDataStateChanged: no change");
                        } else {
                            if (result.oldLp.isIdenticalInterfaceName(result.newLp)) {
                                if (! result.oldLp.isIdenticalDnses(result.newLp) ||
                                        ! result.oldLp.isIdenticalRoutes(result.newLp) ||
                                        ! result.oldLp.isIdenticalHttpProxy(result.newLp) ||
                                        ! isIpMatched(result.oldLp, result.newLp)) {
                                    // If the same address type was removed and
                                    // added we need to cleanup
                                    CompareResult<LinkAddress> car =
                                        result.oldLp.compareAddresses(result.newLp);
                                    if (DBG) {
                                        log("onDataStateChanged: oldLp=" + result.oldLp +
                                                " newLp=" + result.newLp + " car=" + car);
                                    }
                                    boolean needToClean = false;
                                    for (LinkAddress added : car.added) {
                                        for (LinkAddress removed : car.removed) {
                                            if (NetworkUtils.addressTypeMatches(
                                                    removed.getAddress(),
                                                    added.getAddress())) {
                                                /// M:[IRAT] Don't clean data
                                                ///   in IRAT project when IP changed.
                                                log("[IRAT_DcController] Don't set cleanup flag.");
                                                if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                                                        || !SvlteUtils.isActiveSvlteMode(mPhone)) {
                                                    needToClean = true;
                                                }
                                                break;
                                            }
                                        }
                                    }
                                    if (needToClean) {
                                        if (DBG) {
                                            log("onDataStateChanged: addr change," +
                                                    " cleanup apns=" + dc.mApnContexts +
                                                    " oldLp=" + result.oldLp +
                                                    " newLp=" + result.newLp);
                                        }
                                        apnsToCleanup.addAll(dc.mApnContexts);
                                    } else {
                                        if (DBG) log("onDataStateChanged: simple change");

                                        for (ApnContext apnContext : dc.mApnContexts) {
                                             mPhone.notifyDataConnection(
                                                 PhoneConstants.REASON_LINK_PROPERTIES_CHANGED,
                                                 apnContext.getApnType());
                                        }
                                    }
                                } else {
                                    if (DBG) {
                                        log("onDataStateChanged: no changes");
                                    }
                                }
                            } else {
                                apnsToCleanup.addAll(dc.mApnContexts);
                                if (DBG) {
                                    log("onDataStateChanged: interface change, cleanup apns="
                                            + dc.mApnContexts);
                                }
                            }
                        }
                    }
                }

                if (newState.active == DATA_CONNECTION_ACTIVE_PH_LINK_UP) {
                    isAnyDataCallActive = true;
                }
                if (newState.active == DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT) {
                    isAnyDataCallDormant = true;
                }
            }

            int newOverallDataConnectionActiveState = mOverallDataConnectionActiveState;

            if (isAnyDataCallDormant && !isAnyDataCallActive) {
                // There is no way to indicate link activity per APN right now. So
                // Link Activity will be considered dormant only when all data calls
                // are dormant.
                // If a single data call is in dormant state and none of the data
                // calls are active broadcast overall link state as dormant.
                if (DBG) {
                    log("onDataStateChanged: Data Activity updated to DORMANT. stopNetStatePoll");
                }
                mDct.sendStopNetStatPoll(DctConstants.Activity.DORMANT);
                newOverallDataConnectionActiveState = DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT;
            } else {
                //Fixed:[ALPS01782373] SIM 1 Data icon should shows
                //when you send a MMS by SIM2.(once)
                mDct.sendStartNetStatPoll(DctConstants.Activity.NONE);
                if (DBG) {
                    log("onDataStateChanged: Data Activity updated to NONE. " +
                            "isAnyDataCallActive = " + isAnyDataCallActive +
                            " isAnyDataCallDormant = " + isAnyDataCallDormant);
                }
                if (isAnyDataCallActive) {
                    newOverallDataConnectionActiveState = DATA_CONNECTION_ACTIVE_PH_LINK_UP;
                    //mDct.sendStartNetStatPoll(DctConstants.Activity.NONE);
                } else {
                    newOverallDataConnectionActiveState = DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE;
                }
            }

            // TODO: b/23319188 Enable/Disable this based on enable/disable of dormancy indications 
            //if (mOverallDataConnectionActiveState != newOverallDataConnectionActiveState) {
            //    mOverallDataConnectionActiveState = newOverallDataConnectionActiveState;
            //    long time = SystemClock.elapsedRealtimeNanos();
            //    int dcPowerState;
            //    switch (mOverallDataConnectionActiveState) {
            //        case DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE:
            //        case DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT:
            //            dcPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_LOW;
            //            break;
            //        case DATA_CONNECTION_ACTIVE_PH_LINK_UP:
            //            dcPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_HIGH;
            //            break;
            //        default:
            //            dcPowerState = DataConnectionRealTimeInfo.DC_POWER_STATE_UNKNOWN;
            //            break;
            //    }
            //    DataConnectionRealTimeInfo dcRtInfo =
            //            new DataConnectionRealTimeInfo(time , dcPowerState);
            //    log("onDataStateChanged: notify DcRtInfo changed dcRtInfo=" + dcRtInfo);
            //    mPhone.notifyDataConnectionRealTimeInfo(dcRtInfo); 
            //}

            if (DBG) {
                lr("onDataStateChanged: dcsToRetry=" + dcsToRetry
                        + " apnsToCleanup=" + apnsToCleanup);
            }

            // Cleanup connections that have changed
            for (ApnContext apnContext : apnsToCleanup) {
               mDct.sendCleanUpConnection(true, apnContext);
            }

            int isLoaded = isRecordLoaded ? 1 : 0;
            // Retry connections that have disappeared
            for (DataConnection dc : dcsToRetry) {
                if (DBG) {
                    log("onDataStateChanged: send EVENT_LOST_CONNECTION dc.mTag="
                            + dc.mTag + "isLoaded:" + isLoaded);
                }
                dc.sendMessage(DataConnection.EVENT_LOST_CONNECTION, dc.mTag, isLoaded);
            }

            if (DBG) {
                log("onDataStateChanged: X");
            }
        }
    }

    /**
     * lr is short name for logAndAddLogRec
     * @param s
     */
    private void lr(String s) {
        logAndAddLogRec(s);
    }

    @Override
    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    @Override
    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    /**
     * @return the string for msg.what as our info.
     */
    @Override
    protected String getWhatToString(int what) {
        String info = null;
        info = DataConnection.cmdToString(what);
        if (info == null) {
            info = DcAsyncChannel.cmdToString(what);
        }
        return info;
    }

    @Override
    public String toString() {
        return "mDcListAll=" + mDcListAll + " mDcListActiveByCid=" + mDcListActiveByCid;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println(" mPhone=" + mPhone);
        pw.println(" mDcListAll=" + mDcListAll);
        pw.println(" mDcListActiveByCid=" + mDcListActiveByCid);
    }

    /** M: if new IP contains old IP, we treat is equally
     *  This is to handle that network response both IPv4 and IPv6 IP,
     *  but APN settings is IPv4 or IPv6
     */
    private boolean isIpMatched(LinkProperties oldLp, LinkProperties newLp) {
        if (oldLp.isIdenticalAddresses(newLp)) {
            return true;
        } else {
            if (DBG) log("isIpMatched: address count is different but matched");
            return newLp.getAddresses().containsAll(oldLp.getAddresses());
        }
    }

    /**
     * M: [C2K] pre-check data state to see if the change is caused by PCModem
     * connected/disconnected, cleanup all connections if PCModem is connected
     * and try to restore data connections when PCModem is disconnected.
     * @param dcsList data call response from RILD.
     * @return True if the change is caused by PCModem connected/disconnected,
     *         or else return false.
     */
    private boolean preCheckDataState(ArrayList<DataCallResponse> dcsList) {
        if (dcsList.size() != 0) {
            log("preCheckForC2K  active = " + dcsList.get(0).active);
            if (dcsList.get(0).active == DATA_CONNECTION_PC_MODEM_CONNECTED) {
                if (DBG) {
                    log("preCheckForC2K PC Modem enabled");
                }
                mPcModemConnected = true;
                mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS,
                        REASON_PC_MODEM_CONNECTED));
                return true;
            } else if (dcsList.get(0).active == DATA_CONNECTION_PC_MODEM_DISCONNECTED) {
                log("preCheckForC2K PC Modem disconnected");
                mPcModemConnected = false;
                mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA,
                        REASON_PC_MODEM_DISCONNECTED));
                return true;
            }
        }
        return false;
    }

    /**
     * M: [C2K] whether PCModem is connected.
     * @return True if PCModem is connected, or else false.
     */
    boolean isPcModemConnected() {
        return mPcModemConnected;
    }

    //M: [C2K][IRAT] need update phone when Rat finished @{
    void updatePhone(PhoneBase newPhone) {
        log("DcController, updatePhone");
        if (mPhone != null) {
            mPhone.mCi.unregisterForRilConnected(getHandler());
            mPhone.mCi.unregisterForDataNetworkStateChanged(getHandler());
        }
        mPhone = newPhone;
        mPhone.mCi.registerForRilConnected(getHandler(),
                DataConnection.EVENT_RIL_CONNECTED, null);
        mPhone.mCi.registerForDataNetworkStateChanged(getHandler(),
                DataConnection.EVENT_DATA_STATE_CHANGED, null);

        if (mDcTesterDeactivateAll != null) {
            mDcTesterDeactivateAll.updatePhone(newPhone);
        }
    }

    /**
     * M: [C2K][IRAT] Update active DC cid if it is not same as datacallresponse's.
     * Because that the cid of DC may change when IRAT between LTE and EHRPD/HRPD
     * @param dcsList
     */
    private void updateDcInfo(ArrayList<DataCallResponse> dcsList) {
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport() || !SvlteUtils.isActiveSvlteMode(mPhone)) {
            return;
        }
        //To record cids need to be changed, key is old cid, value is new cid.
        HashMap<Integer, Integer> changeCids = new HashMap<Integer, Integer>();
        for (DataConnection dc : mDcListActiveByCid.values()) {
            for (DataCallResponse newState : dcsList) {
                int ifId = getIfIdFromIfName(newState.ifname);
                if (ifId != -1) {
                    if (dc.getDataConnectionId() == ifId) {
                        log("[IRAT_DcController] updateDcInfo: ifId = " + ifId + ", dc.getCid() = "
                                + dc.getCid() + ", newState.cid = " + newState.cid);
                        if (dc.getCid() != newState.cid) {
                            changeCids.put(dc.getCid(), newState.cid);
                        }
                    }
                }
            }
        }
        //Update cid
        for (int cid : changeCids.keySet()) {
            DataConnection dc = mDcListActiveByCid.get(cid);
            if (dc != null) {
                //NOTE: Need consider to make mDcListActiveByCid to be a queue to guarantee
                //      the order of dc is consistent with the order of addition of them.
                //      Otherwise can't handle the situation that changeCids is [0:1, 1:2].
                removeActiveDcByCid(dc);
                log("[IRAT_DcController] updateDcInfo: cid " + dc.mCid
                        + " change to " + changeCids.get(cid));
                dc.mCid = changeCids.get(cid);
                addActiveDcByCid(dc);
            }
        }
        log("[IRAT_DcController] updateDcInfo: mDcListActiveByCid = " + mDcListActiveByCid);
    }

    /**
     * M: [C2K][IRAT] Convert interface name to interface id.
     * @param ifName
     * @return interface id
     */
    private int getIfIdFromIfName(String ifName) {
        log("[IRAT_DcController] getIfIdFromIfName, ifName = " + ifName);
        int ifIdInt = -1;
        if (ifName != null && ifName.length() >= 1) {
            String ifIdString = ifName.substring(ifName.length() - 1);
            log("[IRAT_DcController] updateDcInfo: ifIdString = " + ifIdString);
            try {
                ifIdInt = Integer.valueOf(ifIdString);
            } catch (NumberFormatException e) {
                loge("[IRAT_DcController] updateDcInfo," +
                        " the last char of interface id is not a number!");
            }
        }
        return ifIdInt;
    }
    //M: }@
}
