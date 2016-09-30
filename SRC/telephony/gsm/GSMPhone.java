/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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

/* M: SS part */
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import com.android.internal.telephony.TelephonyIntents;
/* M: SS part end */
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.VideoProfile;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;

import android.text.TextUtils;
import android.telephony.Rlog;
import android.util.Log;

import com.android.ims.ImsManager;
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
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE;
import static com.android.internal.telephony.TelephonyProperties.TERMINAL_BASED_CALL_WAITING_DISABLED;
import static com.android.internal.telephony.TelephonyProperties.TERMINAL_BASED_CALL_WAITING_ENABLED_OFF;
import static com.android.internal.telephony.TelephonyProperties.TERMINAL_BASED_CALL_WAITING_ENABLED_ON;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_UT_CFU_NOTIFICATION_MODE;
import static com.android.internal.telephony.TelephonyProperties.UT_CFU_NOTIFICATION_MODE_DISABLED;
import static com.android.internal.telephony.TelephonyProperties.UT_CFU_NOTIFICATION_MODE_ON;
import static com.android.internal.telephony.TelephonyProperties.UT_CFU_NOTIFICATION_MODE_OFF;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
/// M: SS Ut part
import com.android.internal.telephony.CallForwardInfoEx;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
/// M: SS Ut part
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.SuppSrvRequest;

/// M: SS Ut part
import com.android.internal.telephony.SSRequestDecisionMaker;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCall;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;

/// M: SS Ut part @{
import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.ISupplementaryServiceExt;
/// @}

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.FemtoCellInfo;
import com.mediatek.internal.telephony.NetworkInfoWithAcT;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import android.os.SsvManager;//zhibin.zhong add for Defect:1508254,1496823
import android.os.Build;
/**
 * {@hide}
 */
public class GSMPhone extends PhoneBase {
    // NOTE that LOG_TAG here is "GSM", which means that log messages
    // from this file will go into the radio log rather than the main
    // log.  (Use "adb logcat -b radio" to see them.)
    static final String LOG_TAG = "GSMPhone";
    private static final boolean LOCAL_DEBUG = true;
    private static final boolean VDBG = false; /* STOPSHIP if true */

    // Key used to read/write current ciphering state
    public static final String CIPHERING_KEY = "ciphering_key";
    // Key used to read/write voice mail number
    public static final String VM_NUMBER = "vm_number_key";
    // Key used to read/write the SIM IMSI used for storing the voice mail
    public static final String VM_SIM_IMSI = "vm_sim_imsi_key";

    // Instance Variables
    GsmCallTracker mCT;
    GsmServiceStateTracker mSST;
    ArrayList <GsmMmiCode> mPendingMMIs = new ArrayList<GsmMmiCode>();
    SimPhoneBookInterfaceManager mSimPhoneBookIntManager;
    PhoneSubInfo mSubInfo;


    Registrant mPostDialHandler;

    /** List of Registrants to receive Supplementary Service Notifications. */
    RegistrantList mSsnRegistrants = new RegistrantList();

    // mEcmTimerResetRegistrants are informed after Ecm timer is canceled or re-started
    private final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();

    private String mImei;
    private String mImeiSv;
    private String mVmNumber;

    /**
        * mImeiAbnormal=0, Valid IMEI
        * mImeiAbnormal=1, IMEI is null or not valid format
        * mImeiAbnormal=2, Phone1/Phone2 have same IMEI
        */
    private int mImeiAbnormal = 0;

    /* M: SS part */
    private static final String CFU_QUERY_ICCID_PROP = "persist.radio.cfu.iccid.";
    private static final String CFU_QUERY_SIM_CHANGED_PROP = "persist.radio.cfu.change.";

    private static final String SS_SERVICE_CLASS_PROP = "gsm.radio.ss.sc";

    /* M: SS part end */

    private IsimUiccRecords mIsimUiccRecords;

    /// M: CC010: Add RIL interface @{
    /** List of Registrants to receive CRSS Notifications. */
    RegistrantList mCallRelatedSuppSvcRegistrants = new RegistrantList();
    /// @}

    /// M: CC030: CRSS notification @{
    private AsyncResult mCachedSsn = null;
    private AsyncResult mCachedCrssn = null;
    /// @}

    /* M: SS part */
    private boolean needQueryCfu = true;

    /* For solving ALPS01023811
       To determine if CFU query is for power-on query.
    */
    private int mCfuQueryRetryCount = 0;
    private static final String CFU_QUERY_PROPERTY_NAME = "gsm.poweron.cfu.query.";
    private static final int cfuQueryWaitTime = 1000;
    private static final int CFU_QUERY_MAX_COUNT = 60;
    /* M: SS part end */

    /// M: SS Ut part @{
    SSRequestDecisionMaker mSSReqDecisionMaker;
    ISupplementaryServiceExt mSupplementaryServiceExt;
    public static final int TBCW_UNKNOWN = 0;
    public static final int TBCW_NOT_OPTBCW = 1;
    public static final int TBCW_OPTBCW_VOLTE_USER = 2;
    public static final int TBCW_OPTBCW_NOT_VOLTE_USER = 3;
    public static final int TBCW_OPTBCW_WITH_CS = 4;
    private int mTbcwMode = TBCW_UNKNOWN;
    /// @}

    /* M: Network part */
    public static final String UTRAN_INDICATOR = "3G";
    public static final String LTE_INDICATOR = "4G";
    public static final String ACT_TYPE_GSM = "0";
    public static final String ACT_TYPE_UTRAN = "2";
    public static final String ACT_TYPE_LTE = "7";
//begin-zhibin.zhong modify for Task:712472,716728,736751,736757
    public static final String CONFIG_FIRST_BOOT_DEFAULT = "config_first_boot_def_value";
	private static final String VM_SIM1_PREFS_NAME = "vm_numbers";//used to sync with voicemailSetting
	private static final String VM_SIM2_PREFS_NAME = "vm_numbers_sim2";//used to sync with voicemailSetting
	private static final String VM_NUMBER_SIM1 = "1#VMNumber";//used to sync with voicemailSetting
	private static final String VM_NUMBER_SIM2 = "2#VMNumber";//used to sync with voicemailSetting
//end-zhibin.zhong modify for Task:712472,716728,736751,736757
    // IMS registration
    private boolean mImsStatus = false;
    private String mImsExtInfo;
    /* M: Network part end */
    private final static String SSV_VOICE_MAIL_NUMBER = "com.android.phone.ssvvoicemailnumber";//zhibin.zhong add for Defect:1508254,1496823
    //BEGIN add by minguo.wan for TASK 730571 2015-11-16
    public static boolean CF_STATE_UNCONDITIONAL = false;
    public static boolean CF_STATE_BUSY = false;
    public static boolean CF_STATE_NO_REPLY = false;
    public static boolean  CF_STATE_NOT_REACHABLE = false;
    //END add by minguo.wan for TASK 730571 2015-11-16

    static final boolean MTK_SWITCH_ANTENNA_SUPPORT =
                    SystemProperties.get("ro.mtk_switch_antenna").equals("1");
    private static final int PHONE_COUNT = TelephonyManager.getDefault().getPhoneCount();
    private int mCallState;
    private int mRatMode;
    private boolean mCallEstablished = false;

    private static final List<String> MCCMNC_BLOCK_LIST =
                    Arrays.asList("22210"); // Same with MMTelSSUtil.java.

    private static final List<String> OP03_MCCMNC_LIST = Arrays.asList("20801", "20802");

    private static final List<String> OP05_MCCMNC_LIST = Arrays.asList("26201", "26206",
            "26278");

    private static final List<String> OP07_MCCMNC_LIST = Arrays.asList("31030", "31080",
            "310150", "310170", "310280", "310380", "310410", "310560", "310680");

    private static final List<String> OP08_MCCMNC_LIST = Arrays.asList("23203", "23204",
            "21901", "23001", "21630", "29702", "20416", "20420", "26002", "22004", "23430",
            "310160", "310260", "310490", "310580", "310660");

    private static final List<String> OP11_MCCMNC_LIST = Arrays.asList("23420");

    private static final List<String> OP15_MCCMNC_LIST = Arrays.asList("26203", "26207",
            "26208", "26211", "26277"); //26203 and 26277 is from latest Telefonica req.

    private static final List<String> OP18_MCCMNC_LIST = Arrays.asList("405854",
            "405855",
            "405856",
            "405872",
            "405857",
            "405858",
            "405859",
            "405860",
            "405861",
            "405862",
            "405873",
            "405863",
            "405864",
            "405874",
            "405865",
            "405866",
            "405867",
            "405868",
            "405869",
            "405871",
            "405870",
            "405840");

    private static final List<String> OP_TBCLIR_MCCMNC_LIST = Arrays.asList(
        "26202", "26204", "26209" // VDF Germany
        );


    // Create Cfu (Call forward unconditional) so that dialing number &
    // mOnComplete (Message object passed by client) can be packed &
    // given as a single Cfu object as user data to RIL.
    private static class Cfu {
        final String mSetCfNumber;
        final Message mOnComplete;

        Cfu(String cfNumber, Message onComplete) {
            mSetCfNumber = cfNumber;
            mOnComplete = onComplete;
        }
    }

    public boolean mIsNetworkInitiatedUssd = false;

    // Constructors

    public
    GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super("GSM", notifier, context, ci, unitTestMode);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mCi.setPhoneType(PhoneConstants.PHONE_TYPE_GSM);
        mCT = new GsmCallTracker(this);

        mSST = new GsmServiceStateTracker(this);
        /// M: [C2K][IRAT] Delay to create and share DcTracker for SVLTE. @{
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            // Do nothing, we will create and share DcTracker in LteDcPhoneProxy.
            log("IRAT support, doesn't create DcTracker here.");
        } else {
            mDcTracker = new DcTracker(this);
        }
        /// @}

        if (!unitTestMode) {
            mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            mSubInfo = new PhoneSubInfo(this);
        }

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.setOnUSSD(this, EVENT_USSD, null);
        mCi.setOnSuppServiceNotification(this, EVENT_SSN, null);
        mSST.registerForNetworkAttached(this, EVENT_REGISTERED_TO_NETWORK, null);
        mCi.setOnSs(this, EVENT_SS, null);
        /// M: CC010: Add RIL interface @{
        mCT.registerForVoiceCallIncomingIndication(this, EVENT_VOICE_CALL_INCOMING_INDICATION, null);
        /// @}
        setProperties();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        filter.addAction(ImsManager.ACTION_IMS_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);
     }

    public
    GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId) {
        this(context, ci, notifier, false, phoneId);
    }

    public
    GSMPhone(Context context, CommandsInterface ci,
            PhoneNotifier notifier, boolean unitTestMode, int phoneId) {
        super("GSM", notifier, context, ci, unitTestMode, phoneId);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mCi.setPhoneType(PhoneConstants.PHONE_TYPE_GSM);
        mCT = new GsmCallTracker(this);

        mSST = new GsmServiceStateTracker(this);

        /// M: [C2K][IRAT] Delay to create and share DcTracker for SVLTE. @{
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            // Do nothing, we will create and share DcTracker in
            // LteDcPhoneProxy.
            log("IRAT support, doesn't create DcTracker here.");
        } else {
            mDcTracker = new DcTracker(this);
        }
        /// @}

        if (!unitTestMode) {
            mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            mSubInfo = new PhoneSubInfo(this);
        }

        /// M: SS Ut part @{
        mSSReqDecisionMaker = new SSRequestDecisionMaker(context, this);

        if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            try {
                mSupplementaryServiceExt = MPlugin.createInstance(
                        ISupplementaryServiceExt.class.getName(), context);
                if (mSupplementaryServiceExt != null) {
                    mSupplementaryServiceExt.registerReceiver(context, phoneId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        /// @}

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.setOnUSSD(this, EVENT_USSD, null);
        mCi.setOnSuppServiceNotification(this, EVENT_SSN, null);
        if (phoneId == PhoneConstants.SIM_ID_1) {
            mCi.registerForAbnormalEvent(this, EVENT_ABNORMAL_EVENT, null);
        }
        mSST.registerForNetworkAttached(this, EVENT_REGISTERED_TO_NETWORK, null);
        mCi.setOnSs(this, EVENT_SS, null);
        /// M: CC010: Add RIL interface @{
        mCT.registerForVoiceCallIncomingIndication(this, EVENT_VOICE_CALL_INCOMING_INDICATION, null);
        /* register for CRSS Notification */
        mCi.setOnCallRelatedSuppSvc(this, EVENT_CRSS_IND, null);
        /// @}

        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        filter.addAction(ImsManager.ACTION_IMS_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);

        setProperties();

        log("GSMPhone: constructor: sub = " + mPhoneId);

        setProperties();
    }

    protected void setProperties() {
        TelephonyManager.from(mContext).setPhoneType(getPhoneId(), PhoneConstants.PHONE_TYPE_GSM);
    }

    @Override
    public void dispose() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();

            if (mSSReqDecisionMaker != null) {
                mSSReqDecisionMaker.dispose();
            }

            //Unregister from all former registered events
            mCi.unregisterForAvailable(this); //EVENT_RADIO_AVAILABLE
            unregisterForSimRecordEvents();
            mCi.unregisterForOffOrNotAvailable(this); //EVENT_RADIO_OFF_OR_NOT_AVAILABLE
            mCi.unregisterForOn(this); //EVENT_RADIO_ON
            mSST.unregisterForNetworkAttached(this); //EVENT_REGISTERED_TO_NETWORK
            mCi.unSetOnUSSD(this);
            mCi.unSetOnSuppServiceNotification(this);
            mCi.unSetOnSs(this);

            /** M: for suspend data during plmn list */
            mCi.unregisterForGetAvailableNetworksDone(this);
            DctController.getInstance().setDataAllowed(getSubId(), true, null, 0);

            Rlog.d(LOG_TAG, "GSMPhone:dispose: clear mPendingMMIs");
            mPendingMMIs.clear();

            //Force all referenced classes to unregister their former registered events
            mCT.dispose();
            /// M: [C2K][IRAT] Delay to create and share DcTracker for SVLTE. @{
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                // Do nothing, we will create and share DcTracker in
                // LteDcPhoneProxy.
                log("IRAT support, doesn't dispose DcTracker here.");
            } else {
                mDcTracker.dispose();
            }
            /// @}
            mSST.dispose();
            mSimPhoneBookIntManager.dispose();
            mSubInfo.dispose();
        }
    }

    @Override
    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        mSimulatedRadioControl = null;
        mSimPhoneBookIntManager = null;
        mSubInfo = null;
        mCT = null;
        mSST = null;

        super.removeReferences();
    }

    @Override
    protected void finalize() {
        if(LOCAL_DEBUG) Rlog.d(LOG_TAG, "GSMPhone finalized");
    }

    @Override
    public ServiceState
    getServiceState() {

        if (mSST != null) {
            log("getServiceState : getState() : " + mSST.mSS.getState()
                    + " getDataRegState() : " + mSST.mSS.getDataRegState());
        }

        if (mSST == null ||
                (mSST.mSS.getState() != ServiceState.STATE_IN_SERVICE &&
                // IMS service state is reliable only when data registration state is in service
                mSST.mSS.getDataRegState() == ServiceState.STATE_IN_SERVICE)) {
            /// M: ALPS02500449, Fix timing issue.
            /// Keep ref for mImsPhone to avoid null pointer exception
            /// if mImsPhone set to null by other thread. @{
            ImsPhone phone = mImsPhone;
            if (phone != null) {
                log("return ImsPhone service state!!");
                return ServiceState.mergeServiceStates(
                        (mSST == null) ? new ServiceState() : mSST.mSS,
                        phone.getServiceState());
            }
            /// @}
        }

        if (mSST != null) {
            return mSST.mSS;
        } else {
            // avoid potential NPE in EmergencyCallHelper during Phone switch
            return new ServiceState();
        }
    }

    @Override
    public CellLocation getCellLocation() {
        return mSST.getCellLocation();
    }

    @Override
    public PhoneConstants.State getState() {
        if (mImsPhone != null) {
            PhoneConstants.State imsState = mImsPhone.getState();
            if (imsState != PhoneConstants.State.IDLE) {
                return imsState;
            }
        }

        return mCT.mState;
    }

    @Override
    public int getPhoneType() {
        return PhoneConstants.PHONE_TYPE_GSM;
    }

    @Override
    public ServiceStateTracker getServiceStateTracker() {
        return mSST;
    }

    @Override
    public CallTracker getCallTracker() {
        return mCT;
    }

    // pending voice mail count updated after phone creation
    private void updateVoiceMail() {
        int countVoiceMessages = 0;
        IccRecords r = mIccRecords.get();
        if (r != null) {
            // get voice mail count from SIM
            countVoiceMessages = r.getVoiceMessageCount();
        }
        int countVoiceMessagesStored = getStoredVoiceMessageCount();
	//begin-zhibin.zhong add for D384807 by merging D252809 from pixi3-45 4G
        /*if (countVoiceMessages == -1 && countVoiceMessagesStored != 0) {
            countVoiceMessages = countVoiceMessagesStored;
        }*/
        if (countVoiceMessagesStored != 0) {
            countVoiceMessages = countVoiceMessagesStored;
        }
	//end-zhibin.zhong add for D384807 by merging D252809 from pixi3-45 4G
        Rlog.d(LOG_TAG, "updateVoiceMail countVoiceMessages = " + countVoiceMessages
                +" subId "+getSubId());
        setVoiceMessageCount(countVoiceMessages);
    }

    @Override
    public List<? extends MmiCode>
    getPendingMmiCodes() {
        /* M: SS part */
        Rlog.d(LOG_TAG, "mPendingMMIs.size() = " + mPendingMMIs.size());
        /* M: SS part end */
        return mPendingMMIs;
    }

    private static final boolean MTK_IMS_SUPPORT = SystemProperties.get("ro.mtk_ims_support")
                                                                .equals("1") ? true : false;

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;
        log("[" + mPhoneId + "]  getDataConnectionState, mtk_ims_support:"
            + MTK_IMS_SUPPORT + " E ");
        //MTK-START [ALPS00093395] Temporary solution to avoid apnType NullException
        if (apnType == null) {
            apnType = "";
        }
        //MTK-END[ALPS00093395] Temporary solution to avoid apnType NullException

        if (mSST == null) {
            // Radio Technology Change is ongoning, dispose() and removeReferences() have
            // already been called
            log("[" + mPhoneId + "] C1: mSST null");
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (!apnType.equals(PhoneConstants.APN_TYPE_EMERGENCY) &&
                mSST.getCurrentDataConnectionState() != ServiceState.STATE_IN_SERVICE) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow

            // Emergency APN is available even in Out Of Service
            // Pass the actual State of EPDN
            log("[" + mPhoneId + "] C2: dataConnectionState is not in service");
            if (MTK_IMS_SUPPORT && apnType.equals(PhoneConstants.APN_TYPE_IMS)) {
                switch (mDcTracker.getState(apnType)) {
                case RETRYING:
                   log("[" + mPhoneId + "] apnType: " + apnType
                   + " is in retrying state!! return connecting state");
                   ret = PhoneConstants.DataState.CONNECTING;
                   break;
                case CONNECTED:
                   ret = PhoneConstants.DataState.CONNECTED;
                   break;
                case CONNECTING:
                case SCANNING:
                   ret = PhoneConstants.DataState.CONNECTING;
                   break;
                case FAILED:
                case IDLE:
                default:
                   ret = PhoneConstants.DataState.DISCONNECTED;
                   break;
                };
            } else {
               ret = PhoneConstants.DataState.DISCONNECTED;
            }
        } else if (mDcTracker.isApnTypeEnabled(apnType) == false ||
                mDcTracker.isApnTypeActive(apnType) == false) {
            //TODO: isApnTypeActive() is just checking whether ApnContext holds
            //      Dataconnection or not. Checking each ApnState below should
            //      provide the same state. Calling isApnTypeActive() can be removed.
            log("[" + mPhoneId + "] C3: apnType: " + apnType + ", apnTypeEnabled: "
                    + mDcTracker.isApnTypeEnabled(apnType) + ", apnTypeActive: "
                    + mDcTracker.isApnTypeActive(apnType));

            ret = PhoneConstants.DataState.DISCONNECTED;
        } else { /* mSST.gprsState == ServiceState.STATE_IN_SERVICE */
            DctConstants.State state = mDcTracker.getState(apnType);
            log("[" + mPhoneId + "] C4: mSST.gprsState is in service, DcTracker.getState(apnType):"
            + state);
            switch (state) {
                case RETRYING:
                    //M: ALPS01285188
                    if (PhoneConstants.APN_TYPE_MMS.equals(apnType)) {
                        log("mms is retrying!!");
                        ret = PhoneConstants.DataState.CONNECTING;
                        break;
                    }
                case FAILED:
                case IDLE:
                    ret = PhoneConstants.DataState.DISCONNECTED;
                    break;

                case CONNECTED:
                case DISCONNECTING:
                    PhoneConstants.State callState = PhoneConstants.State.IDLE;
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                        // TODO: Remove it when apply CDMALTEPhone
                        // M: Get CS phone's call state
                        callState = SvlteUtils.getSvltePhoneProxy(getPhoneId())
                                .getCallTracker().getState();
                    } else {
                        callState = mCT.mState;
                    }
                    if (callState != PhoneConstants.State.IDLE
                            && !mSST.isConcurrentVoiceAndDataAllowed()) {
                        ret = PhoneConstants.DataState.SUSPENDED;
                    } else {
                        ret = PhoneConstants.DataState.CONNECTED;
                    }

                    // M: check peer phone is in call also
                    int phoneCount = TelephonyManager.getDefault().getPhoneCount();
                    if (TelephonyManager.getDefault().isMultiSimEnabled()
                            && SystemProperties.getInt("ro.mtk_dt_support", 0) != 1) {
                        for (int i = 0; i < phoneCount; i++) {
                            PhoneBase pb = getActivePhone(i);

                            int phoneId = getPhoneId();
                            if (CdmaFeatureOptionUtils.isSvlteSupport()) {
                                phoneId = SvlteUtils.getSvltePhoneIdByPhoneId(phoneId);
                            }
                            if (pb != null && i != phoneId &&
                                    pb.getState() != PhoneConstants.State.IDLE) {
                                Rlog.d(LOG_TAG, "GSMPhone[" + phoneId + "] Phone" + i +
                                        " is in call");
                                if (CdmaFeatureOptionUtils.isSvlteSupport()) {
                                    // Now data is on GSMPhone and peer phone is in call,
                                    // if peer phone is also GSMPhone, should return SUSPENDED
                                    if (pb instanceof GSMPhone) {
                                        Rlog.d(LOG_TAG, "CdmaLteDcSupport, data and cal is both on"
                                                + " GSMPhone, data state set to SUSPENDED");
                                        ret = PhoneConstants.DataState.SUSPENDED;
                                    }
                                } else {
                                    Rlog.d(LOG_TAG, "Data state set to SUSPENDED");
                                    ret = PhoneConstants.DataState.SUSPENDED;
                                }
                                break;
                            }
                        }
                    }

                    //ALPS01454896: If default data is disable, and current state is disconnecting
                    //we don't have to show the data icon.
                    if (ret == PhoneConstants.DataState.CONNECTED &&
                                apnType == PhoneConstants.APN_TYPE_DEFAULT &&
                                mDcTracker.getState(apnType) == DctConstants.State.DISCONNECTING &&
                                !mDcTracker.getDataEnabled()) {
                        log("Connected but default data is not open.");
                        ret = PhoneConstants.DataState.DISCONNECTED;
                    }
                    break;

                case CONNECTING:
                case SCANNING:
                    ret = PhoneConstants.DataState.CONNECTING;
                    break;

                default:
                    break;
            }
        }
        log("[" + mPhoneId + "]  getDataConnectionState X, return state:" + ret);
        return ret;
    }

    @Override
    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;

        if (mSST.getCurrentDataConnectionState() == ServiceState.STATE_IN_SERVICE) {
            switch (mDcTracker.getActivity()) {
                case DATAIN:
                    ret = DataActivityState.DATAIN;
                break;

                case DATAOUT:
                    ret = DataActivityState.DATAOUT;
                break;

                case DATAINANDOUT:
                    ret = DataActivityState.DATAINANDOUT;
                break;

                case DORMANT:
                    ret = DataActivityState.DORMANT;
                break;

                default:
                    ret = DataActivityState.NONE;
                break;
            }
        }

        return ret;
    }

    /**
     * Notify any interested party of a Phone state change
     * {@link com.android.internal.telephony.PhoneConstants.State}
     */
    /*package*/ void notifyPhoneStateChanged() {
        mNotifier.notifyPhoneState(this);
    }

    /**
     * Notify registrants of a change in the call state. This notifies changes in
     * {@link com.android.internal.telephony.Call.State}. Use this when changes
     * in the precise call state are needed, else use notifyPhoneStateChanged.
     */
    /*package*/ void notifyPreciseCallStateChanged() {
        /* we'd love it if this was package-scoped*/
        super.notifyPreciseCallStateChangedP();
    }

    public void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
    }

    /*package*/ void
    notifyDisconnect(Connection cn) {
        mDisconnectRegistrants.notifyResult(cn);

        mNotifier.notifyDisconnectCause(cn.getDisconnectCause(), cn.getPreciseDisconnectCause());
    }

    void notifyUnknownConnection(Connection cn) {
        super.notifyUnknownConnectionP(cn);
    }

    void notifySuppServiceFailed(SuppService code) {
        mSuppServiceFailedRegistrants.notifyResult(code);
    }

    /*package*/ void
    notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    /*package*/
    void notifyLocationChanged() {
        mNotifier.notifyCellLocation(this);
    }

    @Override
    public void
    notifyCallForwardingIndicator() {
        mNotifier.notifyCallForwardingChanged(this);
    }

    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    void notifySpeechCodecInfo(int type) {
        mSpeechCodecInfoRegistrants.notifyResult(type);
    }
    /// @}

    // override for allowing access from other classes of this package
    /**
     * {@inheritDoc}
     */
    @Override
    public void
    setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(mPhoneId, property, value);
    }

    @Override
    public void registerForSuppServiceNotification(
            Handler h, int what, Object obj) {
        mSsnRegistrants.addUnique(h, what, obj);
        /// M: CC030: CRSS notification @{
        // Do not enable or disable CSSN since it is already enabled in RIL initial callback.
        //if (mSsnRegistrants.size() == 1) mCi.setSuppServiceNotifications(true, null);
        if (mCachedSsn != null) {
            mSsnRegistrants.notifyRegistrants(mCachedSsn);
        }
        /// @}
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
        mSsnRegistrants.remove(h);
        /// M: CC030: CRSS notification @{
        // Do not enable or disable CSSN since it is already enabled in RIL initial callback.
        //if (mSsnRegistrants.size() == 0) mCi.setSuppServiceNotifications(false, null);
        mCachedSsn = null;
        /// @}
    }

    @Override
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        mSimRecordsLoadedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimRecordsLoaded(Handler h) {
        mSimRecordsLoadedRegistrants.remove(h);
    }

    @Override
    public void
    acceptCall(int videoState) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;
        if ( imsPhone != null && imsPhone.getRingingCall().isRinging() ) {
            imsPhone.acceptCall(videoState);
        } else {
            /// M: For 3G VT only @{
            //mCT.acceptCall();
            mCT.acceptCall(videoState);
            /// @}
        }
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
        boolean canImsConference = false;
        if (mImsPhone != null) {
            canImsConference = mImsPhone.canConference();
        }
        return mCT.canConference() || canImsConference;
    }

    public boolean canDial() {
        return mCT.canDial();
    }

    @Override
    public void conference() {
        if (mImsPhone != null && mImsPhone.canConference()) {
            log("conference() - delegated to IMS phone");
            mImsPhone.conference();
            return;
        }
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
    public GsmCall
    getForegroundCall() {
        return mCT.mForegroundCall;
    }

    @Override
    public GsmCall
    getBackgroundCall() {
        return mCT.mBackgroundCall;
    }

    @Override
    public Call getRingingCall() {
        ImsPhone imsPhone = mImsPhone;
        /// M: ALPS02816660 @{
        // It returns the ringing call of ImsPhone if the ringing call of GSMPhone isn't ringing.
        // In CallManager.registerPhone(), it always registers ringing call of ImsPhone, because
        // the ringing call of GSMPhone isn't ringing. Consequently, it can't answer GSM call
        // successfully by invoking TelephonyManager.answerRingingCall() since the implementation
        // in PhoneInterfaceManager.answerRingingCallInternal() could not get the correct ringing
        // call from CallManager. So we check the ringing call state of imsPhone first as
        // accpetCall() does.
        if (imsPhone != null && imsPhone.getRingingCall().isRinging()) {
            return imsPhone.getRingingCall();
        }
        /// @}
        return mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (getRingingCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                mCT.rejectCall();
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "reject failed", e);
                notifySuppServiceFailed(Phone.SuppService.REJECT);
            }
        } else if (getBackgroundCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 0: hangupWaitingOrBackground");
            mCT.hangupWaitingOrBackground();
        }

        return true;
    }

    private boolean handleCallWaitingIncallSupplementaryService(
            String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        GsmCall call = getForegroundCall();

        try {
            if (len > 1) {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';

                if (callIndex >= 1 && callIndex <= GsmCallTracker.MAX_CONNECTIONS) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: hangupConnectionByIndex " +
                            callIndex);
                    mCT.hangupConnectionByIndex(call, callIndex);
                }
            } else {
                if (call.getState() != GsmCall.State.IDLE) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: hangup foreground");
                    //mCT.hangupForegroundResumeBackground();
                    mCT.hangup(call);
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            }
        } catch (CallStateException e) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                "hangup failed", e);
            notifySuppServiceFailed(Phone.SuppService.HANGUP);
        }

        return true;
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        GsmCall call = getForegroundCall();

        if (len > 1) {
            try {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';
                GsmConnection conn = mCT.getConnectionByIndex(call, callIndex);

                // gsm index starts at 1, up to 5 connections in a call,
                if (conn != null && callIndex >= 1 && callIndex <= GsmCallTracker.MAX_CONNECTIONS) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 2: separate call "+
                            callIndex);
                    mCT.separate(conn);
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "separate: invalid call index "+
                            callIndex);
                    notifySuppServiceFailed(Phone.SuppService.SEPARATE);
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "separate failed", e);
                notifySuppServiceFailed(Phone.SuppService.SEPARATE);
            }
        } else {
            try {
                if (getRingingCall().getState() != GsmCall.State.IDLE) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 2: accept ringing call");
                    mCT.acceptCall();
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 2: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "switch failed", e);
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

        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {

        int len = dialString.length();

        if (len != 1) {
            return false;
        }

        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 4: explicit call transfer");
        explicitCallTransfer();
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

    @Override
    public boolean handleInCallMmiCommands(String dialString) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;
        if (imsPhone != null
                && imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE) {
            return imsPhone.handleInCallMmiCommands(dialString);
        }

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

    boolean isInCall() {
        GsmCall.State foregroundCallState = getForegroundCall().getState();
        GsmCall.State backgroundCallState = getBackgroundCall().getState();
        GsmCall.State ringingCallState = getRingingCall().getState();

       return (foregroundCallState.isAlive() ||
                backgroundCallState.isAlive() ||
                ringingCallState.isAlive());
    }

    @Override
    public Connection
    dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, null, videoState, null);
    }

    @Override
    public Connection
    dial (String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras)
            throws CallStateException {
        boolean isEmergency = PhoneNumberUtils.isEmergencyNumber(dialString);
        ImsPhone imsPhone = mImsPhone;

        boolean imsUseEnabled = isImsUseEnabled()
                 && imsPhone != null
                 && (imsPhone.isVolteEnabled() || imsPhone.isVowifiEnabled())
                 && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE);

        boolean useImsForEmergency = ImsManager.isVolteEnabledByPlatform(mContext)
                && imsPhone != null
                && isEmergency
                &&  mContext.getResources().getBoolean(
                        com.android.internal.R.bool.useImsAlwaysForEmergencyCall)
                && ImsManager.isNonTtyOrTtyOnVolteEnabled(mContext)
                && (imsPhone.getServiceState().getState() != ServiceState.STATE_POWER_OFF);

        if (LOCAL_DEBUG) {
            Rlog.d(LOG_TAG, "imsUseEnabled=" + imsUseEnabled
                    + ", useImsForEmergency=" + useImsForEmergency
                    + ", imsPhone=" + imsPhone
                    + ", imsPhone.isVolteEnabled()="
                    + ((imsPhone != null) ? imsPhone.isVolteEnabled() : "N/A")
                    + ", imsPhone.isVowifiEnabled()="
                    + ((imsPhone != null) ? imsPhone.isVowifiEnabled() : "N/A")
                    + ", imsPhone.getServiceState().getState()="
                    + ((imsPhone != null) ? imsPhone.getServiceState().getState() : "N/A"));
        }

        ImsPhone.checkWfcWifiOnlyModeBeforeDial(mImsPhone, mContext);

        /// M: should be removed later, just for debug @{
        Rlog.w(LOG_TAG, "IMS: imsphone = " + imsPhone + "isEmergencyNumber = " + PhoneNumberUtils.isEmergencyNumber(dialString));
        if (imsPhone != null) {
            Rlog.w(LOG_TAG, "service state = " + imsPhone.getServiceState().getState());
        }
        /// @}

        if (imsUseEnabled || useImsForEmergency) {
            try {
                if (videoState == VideoProfile.STATE_AUDIO_ONLY) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Trying IMS PS call");
                    return imsPhone.dial(dialString, uusInfo, videoState, intentExtras);
                } else {
                    if (SystemProperties.get("ro.mtk_vilte_support").equals("1")) {
                        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Trying IMS PS video call");
                        return imsPhone.dial(dialString, uusInfo, videoState, intentExtras);
                    } else {
                        /// M: For 3G VT only @{
                        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Trying (non-IMS) CS video call");
                        return dialInternal(dialString, uusInfo, videoState, intentExtras);
                        /// @}
                    }
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "IMS PS call exception " + e +
                        "imsUseEnabled =" + imsUseEnabled + ", imsPhone =" + imsPhone);
                if (!ImsPhone.CS_FALLBACK.equals(e.getMessage())) {
                    CallStateException ce = new CallStateException(e.getMessage());
                    ce.setStackTrace(e.getStackTrace());
                    throw ce;
                }
            }
        }

        if (mSST != null && mSST.mSS.getState() == ServiceState.STATE_OUT_OF_SERVICE
                && mSST.mSS.getDataRegState() != ServiceState.STATE_IN_SERVICE && !isEmergency) {
            throw new CallStateException("cannot dial in current state");
        }
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Trying (non-IMS) CS call");
        /// M: For 3G VT only @{
        //return dialInternal(dialString, null, VideoProfile.STATE_AUDIO_ONLY, intentExtras);
        return dialInternal(dialString, null, videoState, intentExtras);
        /// @}
    }

    @Override
    protected Connection
    dialInternal (String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras)
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

        // Only look at the Network portion for mmi
        String networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        /* M: SS part */
        //Defect1552813,1604370 zubai.li change to remove the MSISDN 2016.03.02 for user version start
        //old:Rlog.d(LOG_TAG, "network portion:" + networkPortion);
        if (!Build.IS_USER_BUILD){
            Rlog.d(LOG_TAG, "network portion:" + networkPortion);
        }
        //Defect1552813,1604370 zubai.li change to remove the MSISDN 2016.03.02 for user version end
        /* M: SS part end */
        GsmMmiCode mmi =
                GsmMmiCode.newFromDialString(networkPortion, this, mUiccApplication.get());
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                               "dialing w/ mmi '" + mmi + "'...");

        if (mmi == null) {
            /// M: For 3G VT only @{
            //return mCT.dial(newDialString, uusInfo, intentExtras);
            if (videoState == VideoProfile.STATE_AUDIO_ONLY) {
                return mCT.dial(newDialString, uusInfo, intentExtras);
            } else {
                if (!is3GVTEnabled()) {
                    throw new CallStateException("cannot vtDial for non-3GVT-capable device");
                }
                return mCT.vtDial(newDialString, uusInfo, intentExtras);
            }
            /// @}
        } else if (mmi.isTemporaryModeCLIR()) {
            /// M: For 3G VT only @{
            //return mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo, intentExtras);
            if (videoState == VideoProfile.STATE_AUDIO_ONLY) {
                return mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo, intentExtras);
            } else {
                if (!is3GVTEnabled()) {
                    throw new CallStateException("cannot vtDial for non-3GVT-capable device");
                }
                return mCT.vtDial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo, intentExtras);
            }
            /// @}
        } else {
            /* M: SS part */
            Rlog.d(LOG_TAG, "[dial]mPendingMMIs.add(mmi) + " + mmi);
            /* M: SS part end */
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();

            // FIXME should this return null or something else?
            return null;
        }
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(dialString, this, mUiccApplication.get());

        if (mmi != null && mmi.isPinPukCommand()) {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return true;
        }

        return false;
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        GsmMmiCode mmi = GsmMmiCode.newFromUssdUserInput(ussdMessge, this, mUiccApplication.get());
        /* M: SS part */
        Rlog.d(LOG_TAG, "[sendUssdResponse]mPendingMMIs.add(mmi) + " + mmi);
        /* M: SS part end */
        mPendingMMIs.add(mmi);
        mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    @Override
    public void
    sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                    "sendDtmf called with invalid character '" + c + "'");
        } else {
            if (mCT.mState ==  PhoneConstants.State.OFFHOOK) {
                mCi.sendDtmf(c, null);
            }
        }
    }

    @Override
    public void
    startDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                "startDtmf called with invalid character '" + c + "'");
        } else {
            mCi.startDtmf(c, null);
        }
    }

    @Override
    public void
    stopDtmf() {
        mCi.stopDtmf(null);
    }

    public void
    sendBurstDtmf(String dtmfString) {
        Rlog.e(LOG_TAG, "[GSMPhone] sendBurstDtmf() is a CDMA method");
    }

    @Override
    public void
    setRadioPower(boolean power) {
        mSST.setRadioPower(power);
    }

    private void storeVoiceMailNumber(String number) {
      //begin-zhibin.zhong modify Task:712472,716728,736751,736757
        if (getPhoneId()== 0) {
            SharedPreferences editor1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
            editor1.edit().putString(VM_NUMBER_SIM1, number).commit();
        } else if (getPhoneId() == 1) {
            SharedPreferences editor2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
            editor2.edit().putString(VM_NUMBER_SIM2, number).commit();
                      }

        setVmSimImsi(getSubscriberId());
        LOGD("GSMPhone storeVoiceMailNumber" + VM_NUMBER + getPhoneId() + "=" + number);
        LOGD("GSMPhone storeVoiceMailNumber VM_SIM_IMSI" +getPhoneId()+ "=" + getSubscriberId());
	//end-zhibin.zhong modify for Task:712472,716728,736751,736757
    }

    @Override
    public String getVoiceMailNumber() {
        //begin-zhibin.zhong modify for Task:712472,716728,736751,736757
        String number = null;
        if (this.mContext.getResources().getBoolean(com.android.internal.R.bool.def_is_voice_mail_requirement_for_NL)
		  && getSubscriberId() != null //zhibin.zhong modify for avoiding voicemail null pointer exception.
                && getSubscriberId().startsWith("204")) {
            if (getPhoneId() == 0) {
                SharedPreferences sp1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
                number = sp1.getString(VM_NUMBER_SIM1, null);
            } else if (getPhoneId() == 1) {
                SharedPreferences sp2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
                number = sp2.getString(VM_NUMBER_SIM2, null);
                                }
            LOGD("getVoiceMailNumber NL number = " + number + ", mySimId = " + getPhoneId());
        } else {

       boolean isFirstSim = this.mContext.getResources().getBoolean(com.android.internal.R.bool.def_is_first_read_sim_voice_mail_number);
       // Read from the SIM. If its null, try reading from the shared preference area.
        IccRecords r = mIccRecords.get();
        number = (r != null) ? r.getVoiceMailNumber() : ""; 
        LOGD("getVoiceMailNumber isFirstSim = " + isFirstSim + " r = " + r + " number = " + number
                + " mySimId = " + getPhoneId());
        if (isFirstSim) {
            if (TextUtils.isEmpty(number)) {

                if (getPhoneId() == 0) {
                    SharedPreferences sp1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
                    number = sp1.getString(VM_NUMBER_SIM1, null);
			 LOGD("getVoiceMailNumber,VM_SIM1_PREFS_NAME: number = " + number);
                } else if (getPhoneId() == 1) {
                    SharedPreferences sp2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
                    number = sp2.getString(VM_NUMBER_SIM2, null);
			 LOGD("getVoiceMailNumber ,VM_SIM2_PREFS_NAME:number = " + number);
                                          }
                LOGD("getVoiceMailNumber number = " + number);
            } else {
                LOGD("getVoiceMailNumber ,storeVoiceMailNumber:number = " + number);
                storeVoiceMailNumber(number);
                                 }
        } else {

            if (getPhoneId() == 0) {
                SharedPreferences sp1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
                number = sp1.getString(VM_NUMBER_SIM1, null);
            } else if (getPhoneId() == 1) {
                SharedPreferences sp2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
                number = sp2.getString(VM_NUMBER_SIM2, null);
                                }
            LOGD("zhibin => getVoiceMailNumber isFirstSim is false, get the sim" + getPhoneId()
                    + " ,perso number = " + number);

			if (TextUtils.isEmpty(number)) {
				number = (r != null) ? r.getVoiceMailNumber() : "";
			       //begin-zhibin.zhong add for defect262782
				if (!TextUtils.isEmpty(number)){
					 LOGD("getVoiceMailNumber ,storeVoiceMailNumber:from sim card number = " + number);
	                             storeVoiceMailNumber(number);
			      }
				//end-zhibin.zhong add for defect262782
			}
		}
	 //begin-zhibin.zhong add for Defect:1550032
         //set voicemail for all French sim card
         boolean isFrench = getContext().getResources().getBoolean(com.android.internal.R.bool.def_is_french_voice_mail_number);
	  IccRecords rec = mIccRecords.get();
	  String simVMNumber = (rec != null) ? rec.getVoiceMailNumber() : "";
         if (isFrench && TextUtils.isEmpty(simVMNumber)) {
             String plmn = (rec != null) ? rec.getOperatorNumeric() : "";
             String spn = (rec !=null) ? rec.getServiceProviderName() : "";
             Rlog.d(LOG_TAG, "[GSMPhone] getVoiceMailNumber, plmn " + plmn + ", spn " + spn);
             if (!TextUtils.isEmpty(plmn)) {
                 if("20801".equals(plmn) || "20823".equals(plmn)) {//Virgin,Orange,C le,Coriolis
                     number = "888";
                 } else if ("20810".equals(plmn)) {//Leclerc,La Poste,Darty,C le,SFR
                     //TODO find the voicemail of Operator 15 WAP
                     //but CTS feedback that is not necessary to implement Voice Message for Operator 15 WAP.
                     number = "123";
                 } else if ("20815".equals(plmn)) {//Free
                     number = "666";
                 } else if ("20820".equals(plmn)) {//Bouygues Telecom
                     number = "660";
                 } else if ("20826".equals(plmn)) {//EI Telecom
                    number = "777";
                 } else {
                     Rlog.d(LOG_TAG, "[GSMPhone] getVoiceMailNumber plmn maybe is not for French!");
                 }
                 storeVoiceMailNumber(number);
             } else {
                 Rlog.d(LOG_TAG, "[GSMPhone] getVoiceMailNumber plmn is null!");
             }
         }
         //end-zhibin.zhong add for Defect:1550032
        } 
        return number;
	//end-zhibin.zhong modify for Task:712472,716728,736751,736757
    }

    private String getVmSimImsi() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sp.getString(VM_SIM_IMSI + getPhoneId(), null);
    }

    private void setVmSimImsi(String imsi) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_SIM_IMSI + getPhoneId(), imsi);
        editor.apply();
    }

    @Override
    public String getVoiceMailAlphaTag() {
        String ret;
        IccRecords r = mIccRecords.get();

        ret = (r != null) ? r.getVoiceMailAlphaTag() : "";

        if (ret == null || ret.length() == 0) {
            //begin-zhibin.zhong modify for task740447
	     String voiceMailName =mContext.getString(com.android.internal.R.string.def_sms_voice_mail_name);
	     if(TextUtils.isEmpty(voiceMailName)){
                   return mContext.getText(
                com.android.internal.R.string.defaultVoiceMailAlphaTag).toString();
	     	}else{
                  return voiceMailName;
		}
          //end-zhibin.zhong modify for task740447
        }

        return ret;
    }

    @Override
    public String getDeviceId() {
        Rlog.d(LOG_TAG, "[GSMPhone] getDeviceId: " + mImei);
        return mImei;
    }

    @Override
    public String getDeviceSvn() {
        Rlog.d(LOG_TAG, "[GSMPhone] getDeviceSvn: " + mImeiSv);
        return mImeiSv;
    }

    @Override
    public IsimRecords getIsimRecords() {
        return mIsimUiccRecords;
    }

    @Override
    public String getImei() {
        return mImei;
    }

    @Override
    public String getEsn() {
        Rlog.e(LOG_TAG, "[GSMPhone] getEsn() is a CDMA method");
        return "0";
    }

    @Override
    public String getMeid() {
        Rlog.e(LOG_TAG, "[GSMPhone] getMeid() is a CDMA method");
        return "0";
    }

    @Override
    public String getNai() {
        IccRecords r = mUiccController.getIccRecords(mPhoneId, UiccController.APP_FAM_3GPP2);
        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Rlog.v(LOG_TAG, "IccRecords is " + r);
        }
        return (r != null) ? r.getNAI() : null;
    }

    @Override
    public String getSubscriberId() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getIMSI() : null;
    }

    @Override
    public String getGroupIdLevel1() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getGid1() : null;
    }

    @Override
    public String getGroupIdLevel2() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getGid2() : null;
    }

    @Override
    public String getLine1Number() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnNumber() : null;
    }

    @Override
    public String getMsisdn() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnNumber() : null;
    }

    @Override
    public String getLine1AlphaTag() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnAlphaTag() : null;
    }

    @Override
    public boolean setLine1Number(String alphaTag, String number, Message onComplete) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setMsisdnNumber(alphaTag, number, onComplete);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void setVoiceMailNumber(String alphaTag,
                            String voiceMailNumber,
                            Message onComplete) {

        Rlog.d(LOG_TAG, "[GSMPhone] setVoiceMailNumber  alphaTag:" + alphaTag + " voiceMailNumber:" + voiceMailNumber);

        Message resp;
        mVmNumber = voiceMailNumber;
        resp = obtainMessage(EVENT_SET_VM_NUMBER_DONE, 0, 0, onComplete);
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, mVmNumber, resp);
        }
    }

    private boolean isValidCommandInterfaceCFReason (int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
        case CF_REASON_UNCONDITIONAL:
        case CF_REASON_BUSY:
        case CF_REASON_NO_REPLY:
        case CF_REASON_NOT_REACHABLE:
        case CF_REASON_ALL:
        case CF_REASON_ALL_CONDITIONAL:
            return true;
        default:
            return false;
        }
    }

    @Override
    public String getSystemProperty(String property, String defValue) {
        if(getUnitTestMode()) {
            return null;
        }
        return TelephonyManager.getTelephonyProperty(mPhoneId, property, defValue);
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

    public void updateDataConnectionTracker() {
        ((DcTracker)mDcTracker).update();
    }

    protected  boolean isCfEnable(int action) {
        return (action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION);
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_GET_CF,
                    onComplete);
            ss.mParcel.writeInt(commandInterfaceCFReason);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
            imsPhone.getCallForwardingOption(commandInterfaceCFReason, imsUtResult);
            return;
        }

        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "requesting call forwarding query.");
            Message resp;
            //BEGIN modify by minguo.wan for TASK 730571 2015-11-16
            /*old: if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
            } else {
                resp = onComplete;
            }*/
            boolean isShowCFIForBusyNoanswerNotavailable = false;
            String value = mContext.getResources().getString(com.android.internal.R.string.ro_update_cfi_for_all);
            if (!TextUtils.isEmpty(value)){
                isShowCFIForBusyNoanswerNotavailable = Boolean.parseBoolean(value); 
               }
            if (isShowCFIForBusyNoanswerNotavailable){
                resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, commandInterfaceCFReason, 0,onComplete);
            } else {
	            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
	                resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
	            } else {
	                resp = onComplete;
	            }
            }
            //END modify by minguo.wan for TASK 730571 2015-11-16

            /// M: SS Ut part @{
            if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                    && isGsmUtSupport()) {
                mSSReqDecisionMaker.queryCallForwardStatus(commandInterfaceCFReason,
                        0, null, resp);
                return;
            }
            /// @}

            if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
                setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
            }
            mCi.queryCallForwardStatus(commandInterfaceCFReason,0,null,resp);
        }
    }

    @Override
    public void getCallForwardingOptionForServiceClass(int commandInterfaceCFReason,
                                  int serviceClass,
                                  Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_GET_CF,
                    onComplete);
            ss.mParcel.writeInt(commandInterfaceCFReason);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
	    setServiceClass(serviceClass);
            imsPhone.getCallForwardingOption(commandInterfaceCFReason, imsUtResult);
            return;
        }

        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "requesting call forwarding query.");
            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
            } else {
                resp = onComplete;
            }

            /// M: SS Ut part @{
            if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                    && isGsmUtSupport()) {
                mSSReqDecisionMaker.queryCallForwardStatus(commandInterfaceCFReason,
                        serviceClass, null, resp);
                return;
            }
            /// @}

            if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
                setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
            }
            mCi.queryCallForwardStatus(commandInterfaceCFReason,serviceClass,null,resp);
        }
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_SET_CF,
                    onComplete);
            ss.mParcel.writeInt(commandInterfaceCFAction);
            ss.mParcel.writeInt(commandInterfaceCFReason);
            ss.mParcel.writeString(dialingNumber);
            ss.mParcel.writeInt(timerSeconds);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
            imsPhone.setCallForwardingOption(commandInterfaceCFAction,
                    commandInterfaceCFReason, dialingNumber, timerSeconds, imsUtResult);
            return;
        }

        if (    (isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

            Message resp;
          //BEGIN modify by minguo.wan for TASK 730571 2015-11-16
            /*old: if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                int origUtCfuMode = 0;
                String utCfuMode = getSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                        UT_CFU_NOTIFICATION_MODE_DISABLED);
                if (UT_CFU_NOTIFICATION_MODE_ON.equals(utCfuMode)) {
                    origUtCfuMode = 1;
                } else if (UT_CFU_NOTIFICATION_MODE_OFF.equals(utCfuMode)) {
                    origUtCfuMode = 2;
                }

                setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                        UT_CFU_NOTIFICATION_MODE_DISABLED);

                Cfu cfu = new Cfu(dialingNumber, onComplete);
                resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                        isCfEnable(commandInterfaceCFAction) ? 1 : 0, origUtCfuMode, cfu);
            } else {
                resp = onComplete;
            }*/
             boolean isShowCFIForBusyNoanswerNotavailable = false;
             String value = mContext.getResources().getString(com.android.internal.R.string.ro_update_cfi_for_all);
             if (!TextUtils.isEmpty(value)) {
                 isShowCFIForBusyNoanswerNotavailable = Boolean.parseBoolean(value);
             }
             Cfu cfu = new Cfu(dialingNumber, onComplete);
             if (isShowCFIForBusyNoanswerNotavailable) {
                 resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, cfu);
             } else {
	            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
	                int origUtCfuMode = 0;
	                String utCfuMode = getSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
	                        UT_CFU_NOTIFICATION_MODE_DISABLED);
	                if (UT_CFU_NOTIFICATION_MODE_ON.equals(utCfuMode)) {
	                    origUtCfuMode = 1;
	                } else if (UT_CFU_NOTIFICATION_MODE_OFF.equals(utCfuMode)) {
	                    origUtCfuMode = 2;
	                }

	                setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
	                        UT_CFU_NOTIFICATION_MODE_DISABLED);

	                resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
	                        isCfEnable(commandInterfaceCFAction) ? 1 : 0, origUtCfuMode, cfu);
	            } else {
	                resp = onComplete;
	            }
             }
            //END modify by minguo.wan for TASK 730571 2015-11-16

            /// M: SS Ut part @{
            if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                    && isGsmUtSupport()) {
                mSSReqDecisionMaker.setCallForward(commandInterfaceCFAction,
                        commandInterfaceCFReason, CommandsInterface.SERVICE_CLASS_VOICE,
                        dialingNumber, timerSeconds, resp);
                return;
            }
            /// @}

            if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
                setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
            }
            mCi.setCallForward(commandInterfaceCFAction,
                    commandInterfaceCFReason,
                    CommandsInterface.SERVICE_CLASS_VOICE,
                    dialingNumber,
                    timerSeconds,
                    resp);
        }
    }

    @Override
    public void setCallForwardingOptionForServiceClass(int commandInterfaceCFReason,
                                 int commandInterfaceCFAction,
                                 String dialingNumber,
                                 int timerSeconds,
                                 int serviceClass,
                                 Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_SET_CF,
                    onComplete);
            ss.mParcel.writeInt(commandInterfaceCFAction);
            ss.mParcel.writeInt(commandInterfaceCFReason);
            ss.mParcel.writeString(dialingNumber);
            ss.mParcel.writeInt(timerSeconds);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
            imsPhone.setCallForwardingOption(commandInterfaceCFAction,
                    commandInterfaceCFReason, dialingNumber, serviceClass, timerSeconds, imsUtResult);
            return;
        }

        if (    (isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                int origUtCfuMode = 0;
                String utCfuMode = getSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                        UT_CFU_NOTIFICATION_MODE_DISABLED);
                if (UT_CFU_NOTIFICATION_MODE_ON.equals(utCfuMode)) {
                    origUtCfuMode = 1;
                } else if (UT_CFU_NOTIFICATION_MODE_OFF.equals(utCfuMode)) {
                    origUtCfuMode = 2;
                }

                setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                        UT_CFU_NOTIFICATION_MODE_DISABLED);

                Cfu cfu = new Cfu(dialingNumber, onComplete);
                resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                        isCfEnable(commandInterfaceCFAction) ? 1 : 0, origUtCfuMode, cfu);
            } else {
                resp = onComplete;
            }

            /// M: SS Ut part @{
            if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                    && isGsmUtSupport()) {
                mSSReqDecisionMaker.setCallForward(commandInterfaceCFAction,
                        commandInterfaceCFReason, serviceClass,
                        dialingNumber, timerSeconds, resp);
                return;
            }
            /// @}

            if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
                setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
            }
            mCi.setCallForward(commandInterfaceCFAction,
                    commandInterfaceCFReason,
                    serviceClass,
                    dialingNumber,
                    timerSeconds,
                    resp);
        }
    }

    /**
     * Get Terminal-based CLIR.
     * @return Response array by 27.007.
     */
    public int[] getSavedClirSetting() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        int clirSetting = sp.getInt(CLIR_KEY + getPhoneId(), -1);
        int presentationMode;
        int getClirResult;
        if ((clirSetting == 0) || (clirSetting == -1)) {
            //allow CLI presentation
            presentationMode = 4;
            getClirResult = CommandsInterface.CLIR_DEFAULT;
        } else if (clirSetting == 1) {
            //restrict CLI presentation
            presentationMode = 3;
            getClirResult = CommandsInterface.CLIR_INVOCATION;
        } else {
            //allow CLI presentation
            presentationMode = 4;
            getClirResult = CommandsInterface.CLIR_SUPPRESSION;
        }

        int getClirResponse [] = new int[2];
        getClirResponse[0] = getClirResult;
        getClirResponse[1] = presentationMode;

        Rlog.i(LOG_TAG, "getClirResult: " + getClirResult);
        Rlog.i(LOG_TAG, "presentationMode: " + presentationMode);

        return getClirResponse;
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {

        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {

            /// M: SS Ut part @{
            if (isOp01IccCard()) {
                sendErrorResponse(onComplete,
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                return;
            }
            /// @}

            int[] result = getSavedClirSetting();
            if (result[0] == CommandsInterface.CLIR_DEFAULT) {
                Rlog.i(LOG_TAG, "CLIR DEFAULT, so return DEFAULT directly.");
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, result, null);
                    onComplete.sendToTarget();
                }
                return;
            }

            if (isOpTbClir()) {
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, result, null);
                    onComplete.sendToTarget();
                }
                return;
            }

            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_GET_CLIR,
                    onComplete);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
            imsPhone.getOutgoingCallerIdDisplay(imsUtResult);
            return;
        }

        /// M: SS Ut part @{
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isGsmUtSupport()) {
            int[] result = getSavedClirSetting();
            if (result[0] == CommandsInterface.CLIR_DEFAULT) {
                Rlog.i(LOG_TAG, "CLIR DEFAULT, so return DEFAULT directly.");
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, result, null);
                    onComplete.sendToTarget();
                }
                return;
            }

            if (isOpTbClir()) {
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, result, null);
                    onComplete.sendToTarget();
                }
                return;
            }

            mSSReqDecisionMaker.getCLIR(onComplete);
            return;
        }
        /// @}

        if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
            setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
        }
        /*
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.getOutgoingCallerIdDisplay(onComplete);
            return;
        }
        */
        mCi.getCLIR(onComplete);
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
                                           Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {

            /// M: SS Ut part @{
            if (isOp01IccCard()) {
                sendErrorResponse(onComplete,
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                return;
            }
            /// @}

            if (isOpTbClir()) {
                mCi.setCLIR(commandInterfaceCLIRMode,
                obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
                return;
            }

            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_SET_CLIR,
                    onComplete);
            ss.mParcel.writeInt(commandInterfaceCLIRMode);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
            imsPhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode, imsUtResult);
            return;
        }

        /// M: SS Ut part @{
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isGsmUtSupport()) {
            if (isOpTbClir()) {
                mCi.setCLIR(commandInterfaceCLIRMode,
                obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
                return;
            }

            mSSReqDecisionMaker.setCLIR(commandInterfaceCLIRMode,
                    obtainMessage(EVENT_SET_CLIR_COMPLETE,
                            commandInterfaceCLIRMode, 0, onComplete));
            return;
        }
        /// @}

        if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
            setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
        }
        /*
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode,
            obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
            return;
        }
        */
        mCi.setCLIR(commandInterfaceCLIRMode,
                obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
    }

    private void initTbcwMode() {
        if (mTbcwMode == TBCW_UNKNOWN) {
            if (isOpTbcwWithCS(getPhoneId())) {
                setTbcwMode(TBCW_OPTBCW_WITH_CS);
                setTbcwToEnabledOnIfDisabled();
            } else if (!isUsimCard()) {
                setTbcwMode(TBCW_OPTBCW_NOT_VOLTE_USER);
                setSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                                TERMINAL_BASED_CALL_WAITING_DISABLED);
            }
        }
        Rlog.i(LOG_TAG, "initTbcwMode: " + mTbcwMode);
    }

    public int getTbcwMode() {
        if (mTbcwMode == TBCW_UNKNOWN) {
            initTbcwMode();
        }
        return mTbcwMode;
    }

    public void setTbcwMode(int newMode) {
        Rlog.i(LOG_TAG,"Set tbcwmode: " + newMode);
        mTbcwMode = newMode;
    }

    /**
     * Set the system property PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE
     * to TERMINAL_BASED_CALL_WAITING_ENABLED_ON if it is TERMINAL_BASED_CALL_WAITING_DISABLED.
     */
    public void setTbcwToEnabledOnIfDisabled() {
        String tbcwMode = getSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                TERMINAL_BASED_CALL_WAITING_DISABLED);
        if (TERMINAL_BASED_CALL_WAITING_DISABLED.equals(tbcwMode)) {
            setSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                    TERMINAL_BASED_CALL_WAITING_ENABLED_ON);
        }
    }

    /**
     * Return Terminal-based Call Waiting configuration.
     * @param onComplete Message callback
     */
    public void getTerminalBasedCallWaiting(Message onComplete) {
        String tbcwMode = getSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                TERMINAL_BASED_CALL_WAITING_DISABLED);
        if (LOCAL_DEBUG) {
            Rlog.d(LOG_TAG, "getTerminalBasedCallWaiting(): tbcwMode = " + tbcwMode
                    + ", onComplete = " + onComplete);
        }
        if (TERMINAL_BASED_CALL_WAITING_ENABLED_ON.equals(tbcwMode)) {
            if (onComplete != null) {
                int[] cwInfos = new int[2];
                cwInfos[0] = 1;
                cwInfos[1] = SERVICE_CLASS_VOICE;
                AsyncResult.forMessage(onComplete, cwInfos, null);
                onComplete.sendToTarget();
            }
            return;
        } else if (TERMINAL_BASED_CALL_WAITING_ENABLED_OFF.equals(tbcwMode)) {
            if (onComplete != null) {
                int[] cwInfos = new int[2];
                cwInfos[0] = 0;
                AsyncResult.forMessage(onComplete, cwInfos, null);
                onComplete.sendToTarget();
            }
            return;
        }

        Rlog.e(LOG_TAG, "getTerminalBasedCallWaiting(): ERROR: tbcwMode = " + tbcwMode);
        return;
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        if (mTbcwMode == TBCW_UNKNOWN) {
            initTbcwMode();
        }

        if (LOCAL_DEBUG) {
            Rlog.d(LOG_TAG, "getCallWaiting(): mTbcwMode = " + mTbcwMode
                    + ", onComplete = " + onComplete);
        }
        if (mTbcwMode == TBCW_OPTBCW_VOLTE_USER) {
            getTerminalBasedCallWaiting(onComplete);
            return;
        } else if (mTbcwMode == TBCW_OPTBCW_NOT_VOLTE_USER) {
            mCi.queryCallWaiting(CommandsInterface.SERVICE_CLASS_NONE, onComplete);
            return;
        } else if (mTbcwMode == TBCW_OPTBCW_WITH_CS) {
            Message resp = obtainMessage(EVENT_GET_CALL_WAITING_DONE, onComplete);
            mCi.queryCallWaiting(CommandsInterface.SERVICE_CLASS_NONE, resp);
            return;
        }

        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
//            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_GET_CW,
//                    onComplete);
//            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
//
//            imsPhone.getCallWaiting(imsUtResult);
            Rlog.d(LOG_TAG, "getCallWaiting(): IMS in service");
            setTbcwMode(TBCW_OPTBCW_VOLTE_USER);
            setTbcwToEnabledOnIfDisabled();
            getTerminalBasedCallWaiting(onComplete);
            return;
        }

        /// M: SS Ut part @{
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isGsmUtSupport()) {
            mSSReqDecisionMaker.queryCallWaiting(CommandsInterface.SERVICE_CLASS_NONE, onComplete);
            return;
        }
        /// @}

        //As per 3GPP TS 24.083, section 1.6 UE doesn't need to send service
        //class parameter in call waiting interrogation  to network
        if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
            setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
        }
        mCi.queryCallWaiting(CommandsInterface.SERVICE_CLASS_NONE, onComplete);
    }

    /**
     * Set Terminal-based Call Waiting configuration.
     * @param enable true if activate Call Waiting. false if deactivate Call Waiting.
     * @param onComplete Message callback
     */
    public void setTerminalBasedCallWaiting(boolean enable, Message onComplete) {
        String tbcwMode = getSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                TERMINAL_BASED_CALL_WAITING_DISABLED);
        if (LOCAL_DEBUG) {
            Rlog.d(LOG_TAG, "setTerminalBasedCallWaiting(): tbcwMode = " + tbcwMode
                    + ", enable = " + enable);
        }
        if (TERMINAL_BASED_CALL_WAITING_ENABLED_ON.equals(tbcwMode)) {
            if (!enable) {
                setSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                        TERMINAL_BASED_CALL_WAITING_ENABLED_OFF);
            }
            if (onComplete != null) {
                AsyncResult.forMessage(onComplete, null, null);
                onComplete.sendToTarget();
            }
            return;
        } else if (TERMINAL_BASED_CALL_WAITING_ENABLED_OFF.equals(tbcwMode)) {
            if (enable) {
                setSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                        TERMINAL_BASED_CALL_WAITING_ENABLED_ON);
            }
            if (onComplete != null) {
                AsyncResult.forMessage(onComplete, null, null);
                onComplete.sendToTarget();
            }
            return;
        }

        Rlog.e(LOG_TAG, "setTerminalBasedCallWaiting(): ERROR: tbcwMode = " + tbcwMode);
        return;
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        if (mTbcwMode == TBCW_UNKNOWN) {
            initTbcwMode();
        }

        if (LOCAL_DEBUG) {
            Rlog.d(LOG_TAG, "setCallWaiting(): mTbcwMode = " + mTbcwMode
                    + ", onComplete = " + onComplete);
        }

        if (mTbcwMode == TBCW_OPTBCW_VOLTE_USER) {
            setTerminalBasedCallWaiting(enable, onComplete);
            return;
        } else if (mTbcwMode == TBCW_OPTBCW_NOT_VOLTE_USER) {
            mCi.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
            return;
        } else if (mTbcwMode == TBCW_OPTBCW_WITH_CS) {
            Message resp = obtainMessage(EVENT_SET_CALL_WAITING_DONE,
                enable == true ? 1 : 0, 0, onComplete);
            mCi.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, resp);
            return;
        }

        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
//            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_SET_CW,
//                    onComplete);
//
//            int enableState = enable ? 1 : 0;
//            ss.mParcel.writeInt(enableState);
//            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
//
//            imsPhone.setCallWaiting(enable, imsUtResult);
            Rlog.d(LOG_TAG, "setCallWaiting(): IMS in service");
            setTbcwMode(TBCW_OPTBCW_VOLTE_USER);
            setTbcwToEnabledOnIfDisabled();
            setTerminalBasedCallWaiting(enable, onComplete);
            return;
        }

        /// M: SS Ut part @{
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isGsmUtSupport()) {
            mSSReqDecisionMaker.setCallWaiting(enable,
                    CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
            return;
        }
        /// @}

        if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
            setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
        }
        mCi.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    @Override
    public void getAvailableNetworks(Message response) {
        log("getAvailableNetworks");
        /** M: for suspend data during plmn list */
        DctController.getInstance().setDataAllowed(getSubId(), false,
                                                                Phone.REASON_QUERY_PLMN, 330000);

        mCi.registerForGetAvailableNetworksDone(this, EVENT_GET_AVAILABLE_NETWORK_DONE, null);
        mCi.getAvailableNetworks(response);
    }

    @Override
    public synchronized void cancelAvailableNetworks(Message response) {
        log("cancelAvailableNetworks");
        mCi.unregisterForGetAvailableNetworksDone(this);
        DctController.getInstance().setDataAllowed(getSubId(), true, null, 0);

        mCi.cancelAvailableNetworks(response);
    }

    @Override
    public void
    setNetworkSelectionModeSemiAutomatic(OperatorInfo network, Message response) {
        // wrap the response message in our own message along with
        // an empty string (to indicate automatic selection) for the
        // operator's id.
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = "";
        nsm.operatorAlphaLong = "";

        Message msg = obtainMessage(EVENT_SET_NETWORK_AUTOMATIC_COMPLETE, nsm);

        String actype = ACT_TYPE_GSM;
        if (network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(UTRAN_INDICATOR)) {
            actype = ACT_TYPE_UTRAN;
        } else if (network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(LTE_INDICATOR)) {
            actype = ACT_TYPE_LTE;
        }

        mCi.setNetworkSelectionModeSemiAutomatic(network.getOperatorNumeric(), actype, msg);
    }

    @Override
    public void
    selectNetworkManually(OperatorInfo network,
            Message response) {
        // wrap the response message in our own message along with
        // the operator's id.
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = network.getOperatorNumeric();
        nsm.operatorAlphaLong = network.getOperatorAlphaLong();

        Message msg = obtainMessage(EVENT_SET_NETWORK_MANUAL_COMPLETE, nsm);

        Rlog.d(LOG_TAG, "GSMPhone selectNetworkManually() :" + network);

        String actype = ACT_TYPE_GSM;
        if (network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(UTRAN_INDICATOR)) {
            actype = ACT_TYPE_UTRAN;
        } else if (network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(LTE_INDICATOR)) {
            actype = ACT_TYPE_LTE;
        }

        mCi.setNetworkSelectionModeManualWithAct(network.getOperatorNumeric(), actype, msg);
    }

    @Override
    public void
    getNeighboringCids(Message response) {
        mCi.getNeighboringCids(response);
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mPostDialHandler = new Registrant(h, what, obj);
    }

    @Override
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
       if (mImsPhone != null) {
           mImsPhone.setUiTTYMode(uiTtyMode, onComplete);
       }
    }

    @Override
    public void setMute(boolean muted) {
        mCT.setMute(muted);
    }

    @Override
    public boolean getMute() {
        return mCT.getMute();
    }

    @Override
    public void getDataCallList(Message response) {
        mCi.getDataCallList(response);
    }

    @Override
    public void updateServiceLocation() {
        mSST.enableSingleLocationUpdate();
    }

    @Override
    public void enableLocationUpdates() {
        mSST.enableLocationUpdates();
    }

    @Override
    public void disableLocationUpdates() {
        mSST.disableLocationUpdates();
    }

    @Override
    public boolean getDataRoamingEnabled() {
        return mDcTracker.getDataOnRoamingEnabled();
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
        mDcTracker.setDataOnRoamingEnabled(enable);
    }

    @Override
    public boolean getDataEnabled() {
        return mDcTracker.getDataEnabled();
    }

    @Override
    public void setDataEnabled(boolean enable) {
        mDcTracker.setDataEnabled(enable);
    }

    /**
     * Removes the given MMI from the pending list and notifies
     * registrants that it is complete.
     * @param mmi MMI that is done
     */
    /*package*/ void
    onMMIDone(GsmMmiCode mmi) {
        /* Only notify complete if it's on the pending list.
         * Otherwise, it's already been handled (eg, previously canceled).
         * The exception is cancellation of an incoming USSD-REQUEST, which is
         * not on the list.
         */

        /* M: SS part */
        Rlog.d(LOG_TAG, "mPendingMMIs.remove(mmi) - " + mmi);
        /* M: SS part end */
        if (mPendingMMIs.remove(mmi) || mmi.isUssdRequest() || mmi.isSsInfo()) {
            mMmiCompleteRegistrants.notifyRegistrants(
                new AsyncResult(null, mmi, null));
        }
    }

    /**
     * Removes the given MMI from the pending list and notifies
     * registrants that it is complete.
     * @param mmi MMI that is done
     * @param obj User object to deliver to application
     */
    public void onMMIDone(GsmMmiCode mmi, Object obj) {
        /* Only notify complete if it's on the pending list.
         * Otherwise, it's already been handled (eg, previously canceled).
         * The exception is cancellation of an incoming USSD-REQUEST, which is
         * not on the list.
         */
        /* M: SS part */
        Rlog.d(LOG_TAG, "mPendingMMIs.remove(mmi) - " + mmi);
        /* M: SS part end */
        if (mPendingMMIs.remove(mmi) || mmi.isUssdRequest() || mmi.isSsInfo()) {
            mMmiCompleteRegistrants.notifyRegistrants(
                    new AsyncResult(obj, mmi, null));
        }
    }

    private void
    onNetworkInitiatedUssd(GsmMmiCode mmi) {
        mMmiCompleteRegistrants.notifyRegistrants(
            new AsyncResult(null, mmi, null));
    }


    /** ussdMode is one of CommandsInterface.USSD_MODE_* */
    private void
    onIncomingUSSD (int ussdMode, String ussdMessage) {
        boolean isUssdError;
        boolean isUssdRequest;
        boolean isUssdRelease;
        boolean isUssdhandleByStk;

        Rlog.d(LOG_TAG, "onIncomingUSSD(): mIsNetworkInitiatedUssd = " + mIsNetworkInitiatedUssd);

        isUssdRequest
            = (ussdMode == CommandsInterface.USSD_MODE_REQUEST);
        /* M: SS part */
        //MTK-START [mtk04070][111118][ALPS00093395]MTK modified
        isUssdError
            = ((ussdMode == CommandsInterface.USSD_OPERATION_NOT_SUPPORTED)
               || (ussdMode == CommandsInterface.USSD_NETWORK_TIMEOUT));
        //MTK-END [mtk04070][111118][ALPS00093395]MTK modified

        isUssdhandleByStk
            = (ussdMode == CommandsInterface.USSD_HANDLED_BY_STK);
        /* M: SS part end */

        isUssdRelease = (ussdMode == CommandsInterface.USSD_MODE_NW_RELEASE);
        Rlog.d(LOG_TAG, "ussdMode= " + ussdMode);
        Rlog.d(LOG_TAG, "isUssdRequest=" + isUssdRequest + " isUssdError= " + isUssdError);

        // See comments in GsmMmiCode.java
        // USSD requests aren't finished until one
        // of these two events happen
        GsmMmiCode found = null;
        Rlog.d(LOG_TAG, "USSD:mPendingMMIs= " + mPendingMMIs + " size=" + mPendingMMIs.size());
        for (int i = 0, s = mPendingMMIs.size() ; i < s; i++) {
            Rlog.d(LOG_TAG, "i= " + i + " isPending=" + mPendingMMIs.get(i).isPendingUSSD());
            if(mPendingMMIs.get(i).isPendingUSSD()) {
                found = mPendingMMIs.get(i);
                Rlog.d(LOG_TAG, "found = " + found);
                break;
            }
        }

        if (found != null) {
            // Complete pending USSD
            /* M: SS part */
            //For ALPS01471897
            Rlog.d(LOG_TAG, "setUserInitiatedMMI  TRUE");
            found.setUserInitiatedMMI(true);
            /* M: SS part end */
            if (isUssdRelease && mIsNetworkInitiatedUssd) {
                Rlog.d(LOG_TAG, "onIncomingUSSD(): USSD_MODE_NW_RELEASE.");
                found.onUssdRelease();
            } else if (isUssdError) {
                found.onUssdFinishedError();
            } else if (isUssdhandleByStk) {
                found.onUssdStkHandling(ussdMessage, isUssdRequest);
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else { // pending USSD not found
            // The network may initiate its own USSD request

            // ignore everything that isnt a Notify or a Request
            // also, discard if there is no message to present

            /* M: SS part */
            //For ALPS01471897
            Rlog.d(LOG_TAG, "The default value of UserInitiatedMMI is FALSE");
            mIsNetworkInitiatedUssd = true;
            Rlog.d(LOG_TAG, "onIncomingUSSD(): Network Initialized USSD");

            if (!isUssdError && ussdMessage != null) {
                GsmMmiCode mmi;
                mmi = GsmMmiCode.newNetworkInitiatedUssd(ussdMessage,
                                                   isUssdRequest,
                                                   GSMPhone.this,
                                                   mUiccApplication.get());
                onNetworkInitiatedUssd(mmi);

            //MTK-START [mtk04070][111118][ALPS00093395]MTK added
            } else if (isUssdError) {
                GsmMmiCode mmi;
                mmi = GsmMmiCode.newNetworkInitiatedUssdError(ussdMessage,
                                                   isUssdRequest,
                                                   GSMPhone.this,
                                                   mUiccApplication.get());
                onNetworkInitiatedUssd(mmi);
            //MTK-END [mtk04070][111118][ALPS00093395]MTK added
            }
            /* M: SS part end */
        }

        /* M: SS part */
        if (isUssdRelease || isUssdError) {
            mIsNetworkInitiatedUssd = false;
        }
        /* M: SS part end */
    }

    /**
     * Make sure the network knows our preferred setting.
     */
    protected  void syncClirSetting() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        /* M: SS part *///TODO  check sp.getInt(xxx+getPhoneID())
        /// M: Add key for SIM2 CLIR setting.
        String keyName = (getPhoneId() == PhoneConstants.SUB1) ? CLIR_KEY : CLIR_KEY_2;

        //int clirSetting = sp.getInt(CLIR_KEY, -1);
        int clirSetting = sp.getInt(CLIR_KEY + getPhoneId(), -1);

        Rlog.i(LOG_TAG, "syncClirSetting: " + clirSetting);
        /* M: SS part end */
        if (clirSetting >= 0) {
            mCi.setCLIR(clirSetting, null);
        }
    }
//begin-zhibin.zhong modify for Task:712472,716728,736751,736757
    protected void LOGD(String message) {
    //private void LOGD(String message) {
        Rlog.d(LOG_TAG, "GSMPhone :" + message);
    }
//end-zhibin.zhong modify for Task:712472,716728,736751,736757
    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;
        Message onComplete;

        // messages to be handled whether or not the phone is being destroyed
        // should only include messages which are being re-directed and do not use
        // resources of the phone being destroyed
        switch (msg.what) {
            // handle the select network completion callbacks.
            case EVENT_SET_NETWORK_MANUAL_COMPLETE:
            case EVENT_SET_NETWORK_AUTOMATIC_COMPLETE:
                super.handleMessage(msg);
                return;
        }

        if (!mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch (msg.what) {
            case EVENT_RADIO_AVAILABLE: {
                mCi.getBasebandVersion(
                        obtainMessage(EVENT_GET_BASEBAND_VERSION_DONE));

                mCi.getIMEI(obtainMessage(EVENT_GET_IMEI_DONE));
                mCi.getIMEISV(obtainMessage(EVENT_GET_IMEISV_DONE));
                mCi.getRadioCapability(obtainMessage(EVENT_GET_RADIO_CAPABILITY));
                startLceAfterRadioIsAvailable();
            }
            break;

            case EVENT_RADIO_ON:
            	/*BEGIN-20151022-zubin.chen-add-for-tast730553*/
            	if (IsRoamingReminderenable()) {
            	    Settings.System.putInt(mContext.getContentResolver(),
            	    Settings.System.IS_ROAMING_REMINDER_SHOWED, 0);
            	}
            	/*END-20151022-zubin.chen-add-for-tast730553*/
                // If this is on APM off, SIM may already be loaded. Send setPreferredNetworkType
                // request to RIL to preserve user setting across APM toggling
                // Comment out setPreferredNetworkTypeIfSimLoaded()
                // and replace with setDeviceRatMode()
                //setPreferredNetworkTypeIfSimLoaded();
                mSST.setDeviceRatMode(mPhoneId);
                break;

            case EVENT_REGISTERED_TO_NETWORK:
                //Modify by wwanying for defect2774631 on 2016.08.25 begin
                //if (!isOpTbClir()) {
                    syncClirSetting();
                //}
                //Modify by wwanying for defect2774631 on 2016.08.25 begin
                /* M: SS part */
            case EVENT_QUERY_CFU: // fallback from EVENT_REGISTERED_TO_NETWORK
                if (needQueryCfu) {
                    String defaultQueryCfuMode = PhoneConstants.CFU_QUERY_TYPE_DEF_VALUE;
                    if (mSupplementaryServiceExt != null) {
                        defaultQueryCfuMode = mSupplementaryServiceExt.getOpDefaultQueryCfuMode();
                        Rlog.d(LOG_TAG, "defaultQueryCfuMode = " + defaultQueryCfuMode);
                    }
                    String cfuSetting;
                    if (!TelephonyManager.from(mContext).isVoiceCapable()) {
                        // disable CFU query for non voice capable devices (i.e. tablet devices)
                        cfuSetting = SystemProperties.get(PhoneConstants.CFU_QUERY_TYPE_PROP, "1");
                    } else {
                        cfuSetting = SystemProperties.get(PhoneConstants.CFU_QUERY_TYPE_PROP,
                                defaultQueryCfuMode);
                    }
                    String isTestSim = "0";
                    /// M: Add for CMCC RRM test. @{
                    boolean isRRMEnv = false;
                    String operatorNumeric = null;
                    /// @}
                    if (mPhoneId == PhoneConstants.SIM_ID_1) {
                        isTestSim = SystemProperties.get("gsm.sim.ril.testsim", "0");
                    }
                    else if (mPhoneId == PhoneConstants.SIM_ID_2) {
                        isTestSim = SystemProperties.get("gsm.sim.ril.testsim.2", "0");
                    }

                    /// M: Add for CMCC RRM test. @{
                    // RRM test use 46602 as PLMN, which will not appear in the actual network
                    // Note that this should be modified when the PLMN for RRM test is changed
                    operatorNumeric = getServiceState().getOperatorNumeric();
                    if (operatorNumeric != null && operatorNumeric.equals("46602")) {
                        isRRMEnv = true;
                    }
                    /// @}
                    Rlog.d(LOG_TAG, "[GSMPhone] CFU_KEY = " + cfuSetting + " isTestSIM : " + isTestSim + " isRRMEnv : " + isRRMEnv);

                    if (isTestSim.equals("0") && isRRMEnv == false) { /// M: Add for CMCC RRM test.
                        String isChangedProp = CFU_QUERY_SIM_CHANGED_PROP + getPhoneId();
                        String isChanged = SystemProperties.get(isChangedProp, "0");

                        Rlog.d(LOG_TAG, "[GSMPhone] isChanged " + isChanged);
                        // 0 : default
                        // 1 : OFF
                        // 2 : ON
                        if (cfuSetting.equals("2")
                            || (cfuSetting.equals("0") && isChanged.equals("1"))) {
                            /* For solving ALPS01023811 */
                            mCfuQueryRetryCount = 0;
                            queryCfuOrWait();
                            needQueryCfu = false;
                            SystemProperties.set(isChangedProp, "0");
                        } else {
                            String utCfuMode = getSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                    UT_CFU_NOTIFICATION_MODE_DISABLED);
                            if (UT_CFU_NOTIFICATION_MODE_ON.equals(utCfuMode)) {
                                IccRecords r = mIccRecords.get();
                                if (r != null) {
                                    r.setVoiceCallForwardingFlag(1, true, "");
                                }
                            } else if (UT_CFU_NOTIFICATION_MODE_OFF.equals(utCfuMode)) {
                                IccRecords r = mIccRecords.get();
                                if (r != null) {
                                    r.setVoiceCallForwardingFlag(1, false, "");
                                }
                            }
                        }
                    } else {
                        String utCfuMode = getSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                UT_CFU_NOTIFICATION_MODE_DISABLED);
                        if (UT_CFU_NOTIFICATION_MODE_ON.equals(utCfuMode)) {
                            IccRecords r = mIccRecords.get();
                            if (r != null) {
                                r.setVoiceCallForwardingFlag(1, true, "");
                            }
                        } else if (UT_CFU_NOTIFICATION_MODE_OFF.equals(utCfuMode)) {
                            IccRecords r = mIccRecords.get();
                            if (r != null) {
                                r.setVoiceCallForwardingFlag(1, false, "");
                            }
                        }
                    }
                }
                /* M: SS part end */
                break;

            case EVENT_SIM_RECORDS_LOADED:
      //begin-zhibin.zhong modify for Task:712472,716728,736751,736757
                new Thread(new Runnable(){ 
                public void run(){                        
			updateCurrentCarrierInProvider();            		
		   }            	
		   }).start();

                // Check if this is a different SIM than the previous one. If so unset the
                // voice mail number.
             /*   String imsi = getVmSimImsi();
                String imsiFromSIM = getSubscriberId();
                if (imsi != null && imsiFromSIM != null && !imsiFromSIM.equals(imsi)) {
                    storeVoiceMailNumber(null);
                    setVmSimImsi(null);
                }*/

		  SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());
                SharedPreferences.Editor editor = settings.edit();
                boolean isReadDefValue = settings.getBoolean(CONFIG_FIRST_BOOT_DEFAULT, true);
                LOGD("GSMPhone isReadDefValue =" + isReadDefValue);
                String voicemailNumberSim1 = null;
                String voicemailNumberSim2 = null;
                // Check if this is a different SIM than the previous one. If so unset the
                // voice mail number.
                String imsi = settings.getString(VM_SIM_IMSI + getPhoneId(), null);
                String imsiFromSIM = getSubscriberId();
                LOGD("GSMPhone EVENT_SIM_RECORDS_LOADED imsi =" + imsi);
                LOGD("GSMPhone EVENT_SIM_RECORDS_LOADED imsiFromSIM =" + imsiFromSIM);
                boolean isNLVoicemailRequirement = this.mContext.getResources().getBoolean(com.android.internal.R.bool.def_is_voice_mail_requirement_for_NL);
                String NLvoicemailNumber = this.mContext.getResources().getString(com.android.internal.R.string.def_voice_mail_number_for_NL);
                LOGD(" GSMPhone EVENT_SIM_RECORDS_LOADED is NL voice mail requirement = " + isNLVoicemailRequirement
                        + ", NL voicemail number = " + NLvoicemailNumber);//zhibin.zhong modify for voicemail
                if (imsi != null && imsiFromSIM != null && !imsiFromSIM.equals(imsi)) {
                    boolean isErase =this.mContext.getResources().getBoolean(com.android.internal.R.bool.def_is_erase_last_sim_voice_mail_number_record);// CR362805 add by bo.huang@jrdcom.com
                    LOGD("GSMPhone EVENT_SIM_RECORDS_LOADED isErase =" + isErase);
                     if (imsiFromSIM.startsWith("204")) {
                        LOGD("GSMPhone EVENT_SIM_RECORDS_LOADED the IMSI of this new SIMcard is start with 204");
                        if (getPhoneId() == 0) {
                            SharedPreferences vm_sim1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
                            vm_sim1.edit().putString(VM_NUMBER_SIM1, NLvoicemailNumber).commit();
                        } else if (getPhoneId() == 1) {
                            SharedPreferences vm_sim2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
                            vm_sim2.edit().putString(VM_NUMBER_SIM2, NLvoicemailNumber).commit();
                                                          }
                        setVmSimImsi(imsiFromSIM);
                    } else {
                        if (true == isErase) {
                            storeVoiceMailNumber(null);
                            setVmSimImsi(null);
                            isReadDefValue = true;//when insert another SIM card, isReadDefValue= true.
                        }else{
                            setVmSimImsi(imsiFromSIM);
                        }
                    	}
             }                         
 
                 /*If the first boot slot1(slot2) insert a SIM card which imsi is starts with 204, but Slot2(slot1) is not insert SIM card,
                 * then insert a SIM card which imsi is starts wit 204 into slot2(slot1) and reboot, the need to store the imsi of
                 * SIM2(SIM1). the voice mail number 1233 also need to storage. */
                if (isNLVoicemailRequirement && !isReadDefValue && imsi == null && imsiFromSIM != null) {
                    LOGD("GSMPhone EVENT_SIM_RECORDS_LOADED old IMSI is null, store the imsi from SIM and voice mail number from perso, mySimId = " + getPhoneId());
                    if(imsiFromSIM.startsWith("204")) {
                        if(getPhoneId() == 0) {
                            SharedPreferences vm_sim1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
                            vm_sim1.edit().putString(VM_NUMBER_SIM1, NLvoicemailNumber).commit();
                        } else if (getPhoneId() == 1){
                            SharedPreferences vm_sim2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
                            vm_sim2.edit().putString(VM_NUMBER_SIM2, NLvoicemailNumber).commit();
                                                                }
                                 }
                      setVmSimImsi(imsiFromSIM);
                   } 
                if (isReadDefValue) {
                       setVmSimImsi(imsiFromSIM); 

			if (isNLVoicemailRequirement && imsiFromSIM != null && imsiFromSIM.startsWith("204")) {//zhibin.zhong modify for avoiding voicemail null pointer exception.
                        LOGD("GSMPhone EVENT_SIM_RECORDS_LOADED first insert SIM, mySimId = " + getPhoneId());
                        if(getPhoneId() == 0) {
                            SharedPreferences vm_sim1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
                            vm_sim1.edit().putString(VM_NUMBER_SIM1, NLvoicemailNumber).commit();
                        } else if (getPhoneId() == 1){
                            SharedPreferences vm_sim2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
                            vm_sim2.edit().putString(VM_NUMBER_SIM2, NLvoicemailNumber).commit();
                                                                }
                    } else {
                        voicemailNumberSim1 = mContext.getResources().getString(com.android.internal.R.string.def_sim1_phone_voice_mail_number);
                        LOGD("GSMPhone voicemailNumberSim1 read from plf =" + voicemailNumberSim1);

                        voicemailNumberSim2 = mContext.getResources().getString(com.android.internal.R.string.def_sim2_phone_voice_mail_number);
                        LOGD("GSMPhone voicemailNumberSim2 read from plf =" + voicemailNumberSim2);
                   
                        if (voicemailNumberSim1 != null && !voicemailNumberSim1.equals("0")) {
                            SharedPreferences vm_sim1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
                            vm_sim1.edit().putString(VM_NUMBER_SIM1, voicemailNumberSim1).commit();
                        }
                        if (voicemailNumberSim2 != null && !voicemailNumberSim2.equals("0")) {
                            SharedPreferences vm_sim2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
                            vm_sim2.edit().putString(VM_NUMBER_SIM2, voicemailNumberSim2).commit();
                        }
                	}
                        isReadDefValue = false;
                        editor.putBoolean(CONFIG_FIRST_BOOT_DEFAULT, false);
                        editor.commit();
                }
		//end-zhibin.zhong modify for Task:712472,716728,736751,736757
                //begin-zhibin.zhong add for Defect:1508254,1496823
                   boolean isSsvVersion = SystemProperties.getBoolean("ro.ssv.enabled", false);
		     LOGD("isSsvVersion: " + isSsvVersion);
                   if (isSsvVersion) {
		      boolean isVoiceMailSsv = SsvManager.isAppNeedChange(SSV_VOICE_MAIL_NUMBER);
                    LOGD("isAppNeedChange: " + isVoiceMailSsv );
                    if (isVoiceMailSsv) {
                        voicemailNumberSim1 = mContext.getResources().getString(com.android.internal.R.string.def_sim1_phone_voice_mail_number);
                        LOGD("GSMPhone SSVvoicemailNumberSim1 read from plf =" + voicemailNumberSim1);
						
			    voicemailNumberSim2 = mContext.getResources().getString(com.android.internal.R.string.def_sim2_phone_voice_mail_number);
                        LOGD("GSMPhone SSVvoicemailNumberSim2 read from plf =" + voicemailNumberSim2);
						
                         if (voicemailNumberSim1 != null && !voicemailNumberSim1.equals("0")) {
                            SharedPreferences vm_sim1 = mContext.getSharedPreferences(VM_SIM1_PREFS_NAME, 0);
                            vm_sim1.edit().putString(VM_NUMBER_SIM1, voicemailNumberSim1).commit();
                        }
						 
                        if (voicemailNumberSim2 != null && !voicemailNumberSim2.equals("0")) {
                            SharedPreferences vm_sim2 = mContext.getSharedPreferences(VM_SIM2_PREFS_NAME, 0);
                            vm_sim2.edit().putString(VM_NUMBER_SIM2, voicemailNumberSim2).commit();
                        }

			     SsvManager.appChangeComplete(SSV_VOICE_MAIL_NUMBER);			
                    }
                }
            //end-zhibin.zhong add for Defect:1508254,1496823
                mSimRecordsLoadedRegistrants.notifyRegistrants();
                updateVoiceMail();
            break;

            case EVENT_GET_BASEBAND_VERSION_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Baseband version: " + ar.result);
                /// M: Svlte solution2 modify, support BASEBAND version of stack 2. @{
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    TelephonyManager.from(mContext).setBasebandVersionForPhone(
                            SvlteUtils.getSlotId(getPhoneId()),
                            (String) ar.result);
                } else {
                    TelephonyManager.from(mContext).setBasebandVersionForPhone(getPhoneId(),
                            (String) ar.result);
                }
                /// @}
            break;

            case EVENT_GET_IMEI_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    Rlog.d(LOG_TAG, "Null IMEI!!");
                    setDeviceIdAbnormal(1);
                    break;
                }

                mImei = (String)ar.result;
                Rlog.d(LOG_TAG, "IMEI: " + mImei);

                try {
                    Long.parseLong(mImei);
                    setDeviceIdAbnormal(0);
                } catch (NumberFormatException e) {
                    setDeviceIdAbnormal(1);
                    Rlog.d(LOG_TAG, "Invalid format IMEI!!");
                }
            break;

            case EVENT_GET_IMEISV_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                mImeiSv = (String)ar.result;
            break;

            case EVENT_USSD:
                ar = (AsyncResult)msg.obj;

                String[] ussdResult = (String[]) ar.result;

                if (ussdResult.length > 1) {
                    try {
                        onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                    } catch (NumberFormatException e) {
                        Rlog.w(LOG_TAG, "error parsing USSD");
                    }
                }
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE: {
                // Some MMI requests (eg USSD) are not completed
                // within the course of a CommandsInterface request
                // If the radio shuts off or resets while one of these
                // is pending, we need to clean up.

                for (int i = mPendingMMIs.size() - 1; i >= 0; i--) {
                    if (mPendingMMIs.get(i).isPendingUSSD()) {
                        mPendingMMIs.get(i).onUssdFinishedError();
                    }
                }
                ImsPhone imsPhone = mImsPhone;
                if (imsPhone != null) {
                    if (imsPhone.isVowifiEnabled() == false) {
                        /// M: ALPS01973935. @{
                        //imsPhone.getServiceState().setStateOff();
                        imsPhone.getServiceState().setState(ServiceState.STATE_OUT_OF_SERVICE);
                        /// @}
                    }
                }
                mRadioOffOrNotAvailableRegistrants.notifyRegistrants();
                /// M: CC010: Add RIL interface @{
                mRadioOffOrNotAvailableRegistrants.notifyRegistrants();
                /// @}

                mCi.unregisterForGetAvailableNetworksDone(this);
                DctController.getInstance().setDataAllowed(getSubId(), true, null, 0);
            }
            break;

            case EVENT_SSN:
                ar = (AsyncResult)msg.obj;
                SuppServiceNotification not = (SuppServiceNotification) ar.result;
                /// M: CC030: CRSS notification @{
                mCachedSsn = ar;
                /// @}
                mSsnRegistrants.notifyRegistrants(ar);
            break;

            case EVENT_SET_CALL_FORWARD_DONE:
                ar = (AsyncResult)msg.obj;
                IccRecords r = mIccRecords.get();
                Cfu cfu = (Cfu) ar.userObj;
                if (ar.exception == null && r != null) {
                    //only CFU would go in this case.
                    //because only CFU use EVENT_SET_CALL_FORWARD_DONE.
                    //So no need to check it is for CFU.
                    if (queryCFUAgainAfterSet()) {
                        if (ar.result != null) {
                            CallForwardInfo[] cfinfo = (CallForwardInfo[]) ar.result;
                            if (cfinfo == null || cfinfo.length == 0) {
                                Rlog.i(LOG_TAG, "cfinfo is null or length is 0.");
                            } else {
                                Rlog.i(LOG_TAG, "[EVENT_SET_CALL_FORWARD_DONE check cfinfo");
                                for (int i = 0 ; i < cfinfo.length ; i++) {
                                    if ((cfinfo[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                                        r.setVoiceCallForwardingFlag(1, (cfinfo[i].status == 1),
                                            cfinfo[i].number);
                                        break;
                                    }
                                }
                            }
                        } else {
                            Rlog.i(LOG_TAG, "ar.result is null.");
                        }
                    } else {
                        r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                    }
                }
                if ((ar.exception != null) && (msg.arg2 != 0)) {
                    if (msg.arg2 == 1) {
                        setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                UT_CFU_NOTIFICATION_MODE_ON);
                    } else {
                        setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                UT_CFU_NOTIFICATION_MODE_OFF);
                    }
                }
                if (cfu.mOnComplete != null) {
                    AsyncResult.forMessage(cfu.mOnComplete, ar.result, ar.exception);
                    cfu.mOnComplete.sendToTarget();
                }
                break;

            case EVENT_GET_CALL_WAITING_DONE:
                ar = (AsyncResult) msg.obj;
                Rlog.d(LOG_TAG, "[EVENT_GET_CALL_WAITING_]ar.exception = " + ar.exception);

                onComplete = (Message) ar.userObj;
                if (ar.exception == null) {
                    int[] cwArray = (int[]) ar.result;
                    // If cwArray[0] is = 1, then cwArray[1] must follow,
                    // with the TS 27.007 service class bit vector of services
                    // for which call waiting is enabled.
                    try {
                        Rlog.d(LOG_TAG, "EVENT_GET_CALL_WAITING_DONE cwArray[0]:cwArray[1] = "
                                + cwArray[0] + ":" + cwArray[1]);

                        boolean csEnable = ((cwArray[0] == 1) &&
                            ((cwArray[1] & 0x01) == SERVICE_CLASS_VOICE));

                        setTerminalBasedCallWaiting(csEnable, null);

                        if (onComplete != null) {
                            AsyncResult.forMessage(onComplete, ar.result, null);
                            onComplete.sendToTarget();
                            break;
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        Rlog.e(LOG_TAG, "EVENT_GET_CALL_WAITING_DONE: improper result: err ="
                                + e.getMessage());
                        if (onComplete != null) {
                            AsyncResult.forMessage(onComplete, ar.result, null);
                            onComplete.sendToTarget();
                            break;
                        }
                    }
                } else {
                    if (onComplete != null) {
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        break;
                    }
                }
                break;

            case EVENT_SET_CALL_WAITING_DONE:
                ar = (AsyncResult) msg.obj;
                onComplete = (Message) ar.userObj;

                if (ar.exception != null) {
                    Rlog.d(LOG_TAG, "EVENT_SET_CALL_WAITING_DONE: ar.exception=" + ar.exception);

                    if (onComplete != null) {
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        break;
                    }
                } else {
                    boolean enable = msg.arg1 == 1 ? true : false;
                    setTerminalBasedCallWaiting(enable, onComplete);
                }
                break;

            case EVENT_SET_VM_NUMBER_DONE:
                ar = (AsyncResult)msg.obj;
                Rlog.d(LOG_TAG, "[GSMPhone] handle EVENT_SET_VM_NUMBER_DONE");
                if (IccVmNotSupportedException.class.isInstance(ar.exception)) {
                    storeVoiceMailNumber(mVmNumber);
                    ar.exception = null;
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;


            case EVENT_GET_CALL_FORWARD_DONE:
                /* M: SS part */ //TODO need check mPhoneID
                /* For solving ALPS00997715 */
                Rlog.d(LOG_TAG, "mPhoneId= " + mPhoneId + "subId=" + getSubId());
                setSystemProperty(CFU_QUERY_PROPERTY_NAME + mPhoneId, "0");
                ar = (AsyncResult)msg.obj;
                Rlog.d(LOG_TAG, "[EVENT_GET_CALL_FORWARD_DONE]ar.exception = " + ar.exception);
                /* M: SS part end */
                //BEGIN modify by minguo.wan for TASK 730571 2015-11-16
                /*old: if (ar.exception == null) {
                    handleCfuQueryResult((CallForwardInfo[])ar.result);
                } */
                boolean cffEnabled = false;
                int mCommandInterfaceCFReason = msg.arg1;
                Rlog.d("Get Callforward's Reason: ",String.valueOf(mCommandInterfaceCFReason));

                if (ar.exception == null) {
                    cffEnabled = handleCfuQueryResult((CallForwardInfo[])ar.result);
                }
                //END modify by minguo.wan for TASK 730571 2015-11-16
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    //BEGIN add by minguo.wan for TASK 730571 2015-11-16
                    if (mCommandInterfaceCFReason == CF_REASON_UNCONDITIONAL){
                         GSMPhone.CF_STATE_UNCONDITIONAL = cffEnabled ? true : false;   
                    }else if (mCommandInterfaceCFReason == CF_REASON_BUSY) {
                         GSMPhone.CF_STATE_BUSY = cffEnabled ? true : false;
                    }else if (mCommandInterfaceCFReason == CF_REASON_NO_REPLY) {
                         GSMPhone.CF_STATE_NO_REPLY = cffEnabled ? true : false;
                    }else if (mCommandInterfaceCFReason == CF_REASON_NOT_REACHABLE){
                          GSMPhone.CF_STATE_NOT_REACHABLE = cffEnabled ? true : false;  
                    }
                     //END add by minguo.wan for TASK 730571 2015-11-16
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_SET_NETWORK_AUTOMATIC:
                // Automatic network selection from EF_CSP SIM record
                ar = (AsyncResult) msg.obj;
                if (mSST.mSS.getIsManualSelection()) {
                    setNetworkSelectionModeAutomatic((Message) ar.result);
                    Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: set to automatic");
                } else {
                    // prevent duplicate request which will push current PLMN to low priority
                    Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: already automatic, ignore");
                }
                break;

            case EVENT_ICC_RECORD_EVENTS:
                ar = (AsyncResult)msg.obj;
                processIccRecordEvents((Integer)ar.result);
                break;
            /* M: SS part */
            case EVENT_SET_CLIR_COMPLETE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    saveClirSetting(msg.arg1);
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_SS:
                ar = (AsyncResult)msg.obj;
                Rlog.d(LOG_TAG, "Event EVENT_SS received");
                // SS data is already being handled through MMI codes.
                // So, this result if processed as MMI response would help
                // in re-using the existing functionality.
                GsmMmiCode mmi = new GsmMmiCode(this, mUiccApplication.get());
                mmi.processSsData(ar);
                break;

             //MTK-START [mtk04070][111118][ALPS00093395]MTK added
             case EVENT_CFU_IND:
                /* Line1 is enabled or disabled while reveiving this EVENT */
                if (mIccRecords.get() != null) {
                   /* Line1 is enabled or disabled while reveiving this EVENT */
                   ar = (AsyncResult) msg.obj;
                   int[] cfuResult = (int[]) ar.result;
                   mIccRecords.get().setVoiceCallForwardingFlag(1, (cfuResult[0] == 1), null);
                }
                break;

              case EVENT_CFU_QUERY_TIMEOUT:
                  Rlog.d(LOG_TAG, "[EVENT_CFU_QUERY_TIMEOUT]mCfuQueryRetryCount = "
                      + mCfuQueryRetryCount);
                  if (++mCfuQueryRetryCount < CFU_QUERY_MAX_COUNT) {
                     queryCfuOrWait();
                  }
                  break;
              /* M: SS part end */

              /// M: CC010: Add RIL interface @{
             case EVENT_CRSS_IND:
                ar = (AsyncResult) msg.obj;
                SuppCrssNotification noti = (SuppCrssNotification) ar.result;

                /// M: CC016: number presentation via CLIP @{
                if (noti.code == SuppCrssNotification.CRSS_CALLING_LINE_ID_PREST) {
                    // update numberPresentation in gsmconnection
                    if (getRingingCall().getState() != GsmCall.State.IDLE) {
                        Connection cn = (Connection) (getRingingCall().getConnections().get(0));
                        /* CLI validity value,
                          0: PRESENTATION_ALLOWED,
                          1: PRESENTATION_RESTRICTED,
                          2: PRESENTATION_UNKNOWN
                          3: PRESENTATION_PAYPHONE
                        */

                        Rlog.d(LOG_TAG, "set number presentation to connection : " + noti.cli_validity);
                        switch (noti.cli_validity) {
                            case 1:
                                cn.setNumberPresentation(PhoneConstants.PRESENTATION_RESTRICTED);
                                break;

                            case 2:
                                cn.setNumberPresentation(PhoneConstants.PRESENTATION_UNKNOWN);
                                break;

                            case 3:
                                cn.setNumberPresentation(PhoneConstants.PRESENTATION_PAYPHONE);
                                break;

                            case 0:
                            default:
                                cn.setNumberPresentation(PhoneConstants.PRESENTATION_ALLOWED);
                                break;
                        }
                    }
                /// @}
                /// M: CC018: Redirecting number via COLP @{
                } else if (noti.code == SuppCrssNotification.CRSS_CONNECTED_LINE_ID_PREST) {
                    /* If the phone number contains in +COLP is different from the address of connection,
                       store it to connection as redirecting address.
                    */
                    Rlog.d(LOG_TAG, "[COLP]noti.number = " + noti.number);
                    if (getForegroundCall().getState() != GsmCall.State.IDLE) {
                        Connection cn = (Connection) (getForegroundCall().getConnections().get(0));
                        if ((cn != null) &&
                            (cn.getAddress() != null) &&
                            !cn.getAddress().equals(noti.number)) {
                           cn.setRedirectingAddress(noti.number);
                           Rlog.d(LOG_TAG, "[COLP]Redirecting address = " +
                            cn.getRedirectingAddress());
                        }
                    }
                }
                /// @}

                /// M: CC030: CRSS notification @{
                mCachedCrssn = ar;
                /// @}
                mCallRelatedSuppSvcRegistrants.notifyRegistrants(ar);

                break;

            case EVENT_VOICE_CALL_INCOMING_INDICATION:
                log("handle EVENT_VOICE_CALL_INCOMING_INDICATION");
                mVoiceCallIncomingIndicationRegistrants.notifyRegistrants(new AsyncResult(null, this, null));
                break;
            /// @}

            case EVENT_GET_AVAILABLE_NETWORK_DONE:
                log("handle EVENT_GET_AVAILABLE_NETWORK_DONE");
                mCi.unregisterForGetAvailableNetworksDone(this);
                DctController.getInstance().setDataAllowed(getSubId(), true, null, 0);
                break;

            case EVENT_IMS_UT_DONE:
                ar = (AsyncResult) msg.obj;
                if (ar == null) {
                    Rlog.e(LOG_TAG, "EVENT_IMS_UT_DONE: Error AsyncResult null!");
                } else {
                    SuppSrvRequest ss = (SuppSrvRequest) ar.userObj;
                    if (ss == null) {
                        Rlog.e(LOG_TAG, "EVENT_IMS_UT_DONE: Error SuppSrvRequest null!");
                    } else if (SuppSrvRequest.SUPP_SRV_REQ_SET_CF_IN_TIME_SLOT
                            == ss.getRequestCode()) {
                        if (ar.exception == null) {
                            ss.mParcel.setDataPosition(0);
                            Rlog.d(LOG_TAG, "EVENT_IMS_UT_DONE: SUPP_SRV_REQ_SET_CF_IN_TIME_SLOT");
                            int commandInterfaceCFAction = ss.mParcel.readInt();
                            int commandInterfaceCFReason = ss.mParcel.readInt();
                            String dialingNumber = ss.mParcel.readString();
                            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                                if (isCfEnable(commandInterfaceCFAction)) {
                                    setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                            UT_CFU_NOTIFICATION_MODE_ON);
                                } else {
                                    setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                            UT_CFU_NOTIFICATION_MODE_OFF);
                                }
                            }
                        }
                        onComplete = ss.getResultCallback();
                        if (onComplete != null) {
                            AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                            onComplete.sendToTarget();
                        }
                        ss.mParcel.recycle();
                    } else {
                        CommandException cmdException = null;
                        ImsException imsException = null;
                        if ((ar.exception != null) && (ar.exception instanceof CommandException)) {
                            cmdException = (CommandException) ar.exception;
                        }
                        if ((ar.exception != null) && (ar.exception instanceof ImsException)) {
                            imsException = (ImsException) ar.exception;
                        }
                        if ((cmdException != null) && (cmdException.getCommandError()
                                == CommandException.Error.UT_XCAP_403_FORBIDDEN)) {
                            setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                            Message msgCSFB = obtainMessage(EVENT_IMS_UT_CSFB, ss);
                            sendMessage(msgCSFB);
                        } else if ((cmdException != null) && (cmdException.getCommandError()
                                == CommandException.Error.UT_UNKNOWN_HOST)) {
                            setCsFallbackStatus(PhoneConstants.UT_CSFB_ONCE);
                            Message msgCSFB = obtainMessage(EVENT_IMS_UT_CSFB, ss);
                            sendMessage(msgCSFB);
                        } else if ((imsException != null) && (imsException.getCode()
                                == ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN)) {
                            setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                            Message msgCSFB = obtainMessage(EVENT_IMS_UT_CSFB, ss);
                            sendMessage(msgCSFB);
                        } else if ((imsException != null) && (imsException.getCode()
                                == ImsReasonInfo.CODE_UT_UNKNOWN_HOST)) {
                            setCsFallbackStatus(PhoneConstants.UT_CSFB_ONCE);
                            Message msgCSFB = obtainMessage(EVENT_IMS_UT_CSFB, ss);
                            sendMessage(msgCSFB);
                        } else {
                            if ((ar.exception == null) &&
                                    (SuppSrvRequest.SUPP_SRV_REQ_SET_CF == ss.getRequestCode())) {
                                ss.mParcel.setDataPosition(0);
                                Rlog.d(LOG_TAG, "EVENT_IMS_UT_DONE: SUPP_SRV_REQ_SET_CF");
                                int commandInterfaceCFAction = ss.mParcel.readInt();
                                int commandInterfaceCFReason = ss.mParcel.readInt();
                                String dialingNumber = ss.mParcel.readString();
                                if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                                    if (queryCFUAgainAfterSet()) {
                                        if (ar.result != null) {
                                            CallForwardInfo[] cfinfo =
                                                (CallForwardInfo[]) ar.result;

                                            if (cfinfo == null || cfinfo.length == 0) {
                                                Rlog.i(LOG_TAG, "cfinfo is null or 0.");
                                            } else {
                                                for (int i = 0 ; i < cfinfo.length ; i++) {
                                                    if ((cfinfo[i].serviceClass
                                                        & SERVICE_CLASS_VOICE) != 0) {
                                                        if (cfinfo[i].status == 1) {
                                                            Rlog.d(LOG_TAG,
                                                                "Set enable, serviceClass: "
                                                                + cfinfo[i].serviceClass);
                                                            setSystemProperty(
                                                                PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                                                UT_CFU_NOTIFICATION_MODE_ON);
                                                        } else {
                                                            Rlog.d(LOG_TAG,
                                                                "Set disable, serviceClass: "
                                                                + cfinfo[i].serviceClass);
                                                            setSystemProperty(
                                                                PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                                                UT_CFU_NOTIFICATION_MODE_OFF);
                                                        }
                                                        break;
                                                    }
                                                }
                                            }
                                        } else {
                                            Rlog.i(LOG_TAG, "ar.result is null.");
                                        }
                                    } else {
                                        if (isCfEnable(commandInterfaceCFAction)) {
                                            setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                                    UT_CFU_NOTIFICATION_MODE_ON);
                                        } else {
                                            setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                                    UT_CFU_NOTIFICATION_MODE_OFF);
                                        }
                                    }
                                }
                            } else if ((imsException != null) && (imsException.getCode()
                                    == ImsReasonInfo.CODE_UT_XCAP_404_NOT_FOUND)) {
                                // Only consider CB && op05 and response 404 status.
                                // Get it from ImsPhone.java
                                // if not CB && op05, then transfer to GENERIC_FAILURE
                                if (isOp05IccCard()
                                    && (ss.getRequestCode() == SuppSrvRequest.SUPP_SRV_REQ_GET_CB
                                    || ss.getRequestCode() == SuppSrvRequest.SUPP_SRV_REQ_SET_CB)) {
                                    ar.exception = new CommandException(
                                        CommandException.Error.UT_XCAP_404_NOT_FOUND);
                                } else {
                                    ar.exception = new CommandException(
                                        CommandException.Error.GENERIC_FAILURE);
                                }
                            } else if ((cmdException != null) && (cmdException.getCommandError()
                                == CommandException.Error.UT_XCAP_404_NOT_FOUND)) {
                                // Only consider CB && op05 and response 404 status.
                                // Get it from ImsPhone.java
                                // if not CB && op05, then transfer to GENERIC_FAILURE
                                if (isOp05IccCard()
                                    && (ss.getRequestCode() == SuppSrvRequest.SUPP_SRV_REQ_GET_CB
                                    || ss.getRequestCode() == SuppSrvRequest.SUPP_SRV_REQ_SET_CB)) {
                                    Rlog.i(LOG_TAG, "GSMPhone get UT_XCAP_404_NOT_FOUND.");
                                } else {
                                    ar.exception = new CommandException(
                                        CommandException.Error.GENERIC_FAILURE);
                                }
                            } else if ((imsException != null) && (imsException.getCode()
                                    == ImsReasonInfo.CODE_UT_XCAP_409_CONFLICT)) {
                                if (!isEnableXcapHttpResponse409()) {
                                    // Transfer back to gereric failure.
                                    Rlog.i(LOG_TAG, "GSMPhone get UT_XCAP_409_CONFLICT, " +
                                        "return GENERIC_FAILURE");
                                    ar.exception = new CommandException(
                                        CommandException.Error.GENERIC_FAILURE);
                                } else {
                                    Rlog.i(LOG_TAG, "GSMPhone get UT_XCAP_409_CONFLICT.");
                                    ar.exception = new CommandException(
                                        CommandException.Error.UT_XCAP_409_CONFLICT);
                                }
                            } else if ((cmdException != null) && (cmdException.getCommandError()
                                == CommandException.Error.UT_XCAP_409_CONFLICT)) {
                                if (!isEnableXcapHttpResponse409()) {
                                    // Transfer back to gereric failure.
                                    Rlog.i(LOG_TAG, "GSMPhone get UT_XCAP_409_CONFLICT, " +
                                        "return GENERIC_FAILURE");
                                    ar.exception = new CommandException(
                                        CommandException.Error.GENERIC_FAILURE);
                                } else {
                                    Rlog.i(LOG_TAG, "GSMPhone get UT_XCAP_409_CONFLICT.");
                                }
                            }

                            onComplete = ss.getResultCallback();
                            if (onComplete != null) {
                                AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                                onComplete.sendToTarget();
                            }
                            ss.mParcel.recycle();
                        }
                    }
                }
                break;

            case EVENT_IMS_UT_CSFB:
                handleImsUtCsfb(msg);
                break;
            case EVENT_ABNORMAL_EVENT:
                log("handle EVENT_ABNORMAL_EVENT");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null && ar.result != null) {
                    String[] msgString = (String[]) ar.result;
                    for (int i = 0; i < msgString.length; i++) {
                        log("msgString[" + i + "]=" + msgString[i]);
                    }
                    handleAbnormalEvent(msgString);
                } else {
                    log("AsyncResult is wrong " + ar.exception);
                }
                break;
            case EVENT_SET_BAND_MODE_DONE:
                log("handle EVENT_SET_BAND_MODE_DONE");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    log("B40 broken. set band mode complete");
                } else {
                    log("AsyncResult is wrong " + ar.exception);
                }
                break;

            /// M: SS OP01 Ut @{
            case EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE:
                Rlog.d(LOG_TAG, "mPhoneId = " + mPhoneId + ", subId = " + getSubId());
                setSystemProperty(CFU_QUERY_PROPERTY_NAME + mPhoneId, "0");
                ar = (AsyncResult) msg.obj;
                Rlog.d(LOG_TAG, "[EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE]ar.exception = "
                        + ar.exception);
                if (ar.exception == null) {
                    handleCfuInTimeSlotQueryResult((CallForwardInfoEx[]) ar.result);
                }
                Rlog.d(LOG_TAG, "[EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE]msg.arg1 = "
                        + msg.arg1);
                if ((ar.exception != null) && (ar.exception instanceof CommandException)) {
                    CommandException cmdException = (CommandException) ar.exception;
                    if ((msg.arg1 == 1) && (cmdException != null) &&
                            (cmdException.getCommandError() ==
                                    CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED)) {
                        getCallForwardingOption(CF_REASON_UNCONDITIONAL,
                                obtainMessage(EVENT_GET_CALL_FORWARD_DONE));
                    }
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_SET_CALL_FORWARD_TIME_SLOT_DONE:
                ar = (AsyncResult) msg.obj;
                IccRecords records = mIccRecords.get();
                CfuEx cfuEx = (CfuEx) ar.userObj;
                if (ar.exception == null && records != null) {
                    records.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfuEx.mSetCfNumber);
                    saveTimeSlot(cfuEx.mSetTimeSlot);
                    if (msg.arg1 == 1) {
                        setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                UT_CFU_NOTIFICATION_MODE_ON);
                    } else {
                        setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                UT_CFU_NOTIFICATION_MODE_OFF);
                    }
                }
                if (cfuEx.mOnComplete != null) {
                    AsyncResult.forMessage(cfuEx.mOnComplete, ar.result, ar.exception);
                    cfuEx.mOnComplete.sendToTarget();
                }
                break;
            /// @}
            default:
                super.handleMessage(msg);
        }
    }

    private void handleAbnormalEvent(String[] args) {
        int caseId = Integer.valueOf(args[0]);
        int argNum = Integer.valueOf(args[1]);
        switch (caseId) {
            case 1:
                log("B40 broken");
                int[] bandMode = new int[3];
                bandMode[0] = 100;
                for (int i = 0; i < 2; i++) {
                    if (args[i + 2].equals("4294967295") || args[i + 2].equals("FFFFFFFF")) {
                        bandMode[i + 1] = -1;
                    } else {
                        bandMode[i + 1] = Integer.valueOf(args[i + 2]);
                    }
                }
                mCi.setBandMode(bandMode, obtainMessage(EVENT_SET_BAND_MODE_DONE));
                break;
            default:
                log("Unknown abnormal case");
                break;
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
            return  mUiccController.getUiccCardApplication(mPhoneId,
                    UiccController.APP_FAM_3GPP);
    }

    @Override
    protected void onUpdateIccAvailability() {
        if (mUiccController == null ) {
            return;
        }

        UiccCardApplication newUiccApplication =
                mUiccController.getUiccCardApplication(mPhoneId, UiccController.APP_FAM_IMS);
        IsimUiccRecords newIsimUiccRecords = null;

        if (newUiccApplication != null) {
            newIsimUiccRecords = (IsimUiccRecords)newUiccApplication.getIccRecords();
            if (LOCAL_DEBUG) log("New ISIM application found");
        }
        mIsimUiccRecords = newIsimUiccRecords;

        newUiccApplication = getUiccCardApplication();

        UiccCardApplication app = mUiccApplication.get();
        if (app != newUiccApplication) {
            if (app != null) {
                if (LOCAL_DEBUG) log("Removing stale icc objects.");
                if (mIccRecords.get() != null) {
                    unregisterForSimRecordEvents();
                    mSimPhoneBookIntManager.updateIccRecords(null);
                }
                mIccRecords.set(null);
                mUiccApplication.set(null);
            }
            if (newUiccApplication != null) {
                if (LOCAL_DEBUG) log("New Uicc application found");
                mUiccApplication.set(newUiccApplication);
                mIccRecords.set(newUiccApplication.getIccRecords());
                registerForSimRecordEvents();
                mSimPhoneBookIntManager.updateIccRecords(mIccRecords.get());
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case IccRecords.EVENT_CFI:
                notifyCallForwardingIndicator();
                break;
        }
    }

    /**
     * Sets the "current" field in the telephony provider according to the SIM's operator
     *
     * @return true for success; false otherwise.
     */
    public boolean updateCurrentCarrierInProvider() {
        long currentDds = SubscriptionManager.getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();

        log("updateCurrentCarrierInProvider: mSubId = " + getSubId()
                + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);

        if (!TextUtils.isEmpty(operatorNumeric) && (getSubId() == currentDds)) {
            try {
                Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "current");
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, operatorNumeric);
                mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    /**
     * Saves CLIR setting so that we can re-apply it as necessary
     * (in case the RIL resets it across reboots).
     */
    public void saveClirSetting(int commandInterfaceCLIRMode) {
        // open the shared preferences editor, and write the value.
        Rlog.i(LOG_TAG, "saveClirSetting: " + commandInterfaceCLIRMode);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        /* M: SS part */ //TODO need to review the CLIR_KEY
        /// M: Add key for SIM2 CLIR setting.
        //String keyName = (getMySimId()==PhoneConstants.GEMINI_SIM_1) ? CLIR_KEY : CLIR_KEY_2;
        SharedPreferences.Editor editor = sp.edit();

        //editor.putInt(keyName, commandInterfaceCLIRMode);
        editor.putInt(CLIR_KEY + getPhoneId(), commandInterfaceCLIRMode);
        // /* M: SS part end */

        // commit and log the result.
        if (! editor.commit()) {
            Rlog.e(LOG_TAG, "failed to commit CLIR preference");
        }
    }

    //BEGIN modify by minguo.wan for TASK 730571 2015-11-16
    //old: private void handleCfuQueryResult(CallForwardInfo[] infos) {
    private boolean handleCfuQueryResult(CallForwardInfo[] infos) {
        boolean cf_state = false;
    //END modify by minguo.wan for TASK 730571 2015-11-16
        IccRecords r = mIccRecords.get();
        if (r != null) {
            if (infos == null || infos.length == 0) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                r.setVoiceCallForwardingFlag(1, false, null);
            } else {
                for (int i = 0, s = infos.length; i < s; i++) {
                    if ((infos[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                        r.setVoiceCallForwardingFlag(1, (infos[i].status == 1),
                            infos[i].number);
                        // should only have the one
                        cf_state = (infos[i].status == 1);  //add by minguo.wan for TASK 730571 2015-11-16
                        break;
                    }
                }
            }
        }
        return cf_state;    //add by minguo.wan for TASK 730571 2015-11-16
    }

    /**
     * Retrieves the PhoneSubInfo of the GSMPhone
     */
    @Override
    public PhoneSubInfo getPhoneSubInfo(){
        return mSubInfo;
    }

    /**
     * Retrieves the IccPhoneBookInterfaceManager of the GSMPhone
     */
    @Override
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager(){
        return mSimPhoneBookIntManager;
    }

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param activate 0 = activate, 1 = deactivate
     * @param response Callback message is empty on completion
     */
    @Override
    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Configure cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override
    public boolean isCspPlmnEnabled() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.isCspPlmnEnabled() : false;
    }

    boolean isManualNetSelAllowed() {

        int nwMode = Phone.PREFERRED_NT_MODE;
        int subId = getSubId();

        nwMode = android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + subId, nwMode);

        Rlog.d(LOG_TAG, "isManualNetSelAllowed in mode = " + nwMode);
        /*
         *  For multimode targets in global mode manual network
         *  selection is disallowed
         */
        if (isManualSelProhibitedInGlobalMode()
                && ((nwMode == Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA)
                        || (nwMode == Phone.NT_MODE_GLOBAL)) ){
            Rlog.d(LOG_TAG, "Manual selection not supported in mode = " + nwMode);
            return false;
        } else {
            Rlog.d(LOG_TAG, "Manual selection is supported in mode = " + nwMode);
        }

        /*
         *  Single mode phone with - GSM network modes/global mode
         *  LTE only for 3GPP
         *  LTE centric + 3GPP Legacy
         *  Note: the actual enabling/disabling manual selection for these
         *  cases will be controlled by csp
         */
        return true;
    }

    private boolean isManualSelProhibitedInGlobalMode() {
        boolean isProhibited = false;
        final String configString = getContext().getResources().getString(com.android.internal.
                                            R.string.prohibit_manual_network_selection_in_gobal_mode);

        if (!TextUtils.isEmpty(configString)) {
            String[] configArray = configString.split(";");

            if (configArray != null &&
                    ((configArray.length == 1 && configArray[0].equalsIgnoreCase("true")) ||
                        (configArray.length == 2 && !TextUtils.isEmpty(configArray[1]) &&
                            configArray[0].equalsIgnoreCase("true") &&
                            configArray[1].equalsIgnoreCase(getGroupIdLevel1())))) {
                            isProhibited = true;
            }
        }
        Rlog.d(LOG_TAG, "isManualNetSelAllowedInGlobal in current carrier is " + isProhibited);
        return isProhibited;
    }

    private void registerForSimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.registerForNetworkSelectionModeAutomatic(
                this, EVENT_SET_NETWORK_AUTOMATIC, null);
        r.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
        r.registerForRecordsLoaded(this, EVENT_SIM_RECORDS_LOADED, null);
    }

    private void unregisterForSimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.unregisterForNetworkSelectionModeAutomatic(this);
        r.unregisterForRecordsEvents(this);
        r.unregisterForRecordsLoaded(this);
    }

    public void forceNotifyServiceStateChange() {
        super.notifyServiceStateChangedP(mSST.mSS);
    }

    @Override
    public void exitEmergencyCallbackMode() {
        if (mImsPhone != null) {
            mImsPhone.exitEmergencyCallbackMode();
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GSMPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mCT=" + mCT);
        pw.println(" mSST=" + mSST);
        pw.println(" mPendingMMIs=" + mPendingMMIs);
        pw.println(" mSimPhoneBookIntManager=" + mSimPhoneBookIntManager);
        pw.println(" mSubInfo=" + mSubInfo);
        if (VDBG) pw.println(" mImei=" + mImei);
        if (VDBG) pw.println(" mImeiSv=" + mImeiSv);
        pw.println(" mVmNumber=" + mVmNumber);
    }

    @Override
    public boolean setOperatorBrandOverride(String brand) {
        if (mUiccController == null) {
            return false;
        }

        UiccCard card = mUiccController.getUiccCard(getPhoneId());
        if (card == null) {
            return false;
        }

        boolean status = card.setOperatorBrandOverride(brand);

        // Refresh.
        if (status) {
            IccRecords iccRecords = mIccRecords.get();
            if (iccRecords != null) {
                TelephonyManager.from(mContext).setSimOperatorNameForPhone(
                        getPhoneId(), iccRecords.getServiceProviderName());
            }
            if (mSST != null) {
                mSST.pollState();
            }
        }
        return status;
    }

    /**
     * @return operator numeric.
     */
    public String getOperatorNumeric() {
        String operatorNumeric = null;
        IccRecords r = mIccRecords.get();
        if (r != null) {
            operatorNumeric = r.getOperatorNumeric();
        }
        return operatorNumeric;
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        ((DcTracker)mDcTracker)
                .registerForAllDataDisconnected(h, what, obj);
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        ((DcTracker)mDcTracker).unregisterForAllDataDisconnected(h);
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        ((DcTracker)mDcTracker)
                .setInternalDataEnabled(enable, onCompleteMsg);
    }


    public boolean setInternalDataEnabledFlag(boolean enable) {
        return ((DcTracker)mDcTracker)
                .setInternalDataEnabledFlag(enable);
    }

    public void notifyEcbmTimerReset(Boolean flag) {
        mEcmTimerResetRegistrants.notifyResult(flag);
    }

    /**
     * Registration point for Ecm timer reset
     *
     * @param h handler to notify
     * @param what User-defined message code
     * @param obj placed in Message.obj
     */
    @Override
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForEcmTimerReset(Handler h) {
        mEcmTimerResetRegistrants.remove(h);
    }

    /**
     * Sets the SIM voice message waiting indicator records.
     * @param line GSM Subscriber Profile Number, one-based. Only '1' is supported
     * @param countWaiting The number of messages waiting, if known. Use
     *                     -1 to indicate that an unknown number of
     *                      messages are waiting
     */
    @Override
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setVoiceMessageWaiting(line, countWaiting);
        } else {
            log("SIM Records not found, MWI not updated");
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[GSMPhone] " + s);
    }

    /// M: CC010: Add RIL interface @{
    public void
    hangupAll() throws CallStateException {
        mCT.hangupAll();
    }

    /**
     * Set EAIC to accept or reject modem to send MT call related notifications.
     *
     * @param accept {@code true} if accept; {@code false} if reject.
     * @internal
     */
    public void setIncomingCallIndicationResponse(boolean accept) {
        log("setIncomingCallIndicationResponse " + accept);
        mCT.setIncomingCallIndicationResponse(accept);
    }

    public void registerForCrssSuppServiceNotification(
            Handler h, int what, Object obj) {
        mCallRelatedSuppSvcRegistrants.addUnique(h, what, obj);
        /// M: CC030: CRSS notification @{
        if (mCachedCrssn != null) {
            mCallRelatedSuppSvcRegistrants.notifyRegistrants(mCachedCrssn);
        }
        /// @}
    }

    public void unregisterForCrssSuppServiceNotification(Handler h) {
        mCallRelatedSuppSvcRegistrants.remove(h);
        /// M: CC030: CRSS notification @{
        mCachedCrssn = null;
        /// @}
    }
    /// @}

    /* M: SS part */
    /**
     * Get Call Barring State
     */
    public void getFacilityLock(String facility, String password, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {

            /// M: SS Ut part @{
            if (isOutgoingCallBarring(facility) &&
                    isOp01IccCard()) {
                sendErrorResponse(onComplete,
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                return;
            }
            /// @}

            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_GET_CB,
                    onComplete);
            ss.mParcel.writeString(facility);
            ss.mParcel.writeString(password);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);

            imsPhone.getCallBarring(facility, imsUtResult);
            return;
        }

        /// M: SS Ut part @{
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isGsmUtSupport()) {
            mSSReqDecisionMaker.queryFacilityLock(facility,
                    password, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
            return;
        }
        /// @}

        if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
            setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
        }
        mCi.queryFacilityLock(facility, password, CommandsInterface.SERVICE_CLASS_VOICE,
                onComplete);
    }

    /**
     * Set Call Barring State
     */

    public void setFacilityLock(String facility, boolean enable, String password, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {

            /// M: SS Ut part @{
            if (isOutgoingCallBarring(facility) &&
                    isOp01IccCard()) {
                sendErrorResponse(onComplete,
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                return;
            }
            /// @}

            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_SET_CB,
                    onComplete);
            ss.mParcel.writeString(facility);
            int enableState = enable ? 1 : 0;
            ss.mParcel.writeInt(enableState);
            ss.mParcel.writeString(password);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);

            imsPhone.setCallBarring(facility, enable, password, imsUtResult);
            return;
        }

        /// M: SS Ut part @{
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isGsmUtSupport()) {
            mSSReqDecisionMaker.setFacilityLock(facility,
                    enable, password, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
            return;
        }
        /// @}

        if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
            setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
        }
        mCi.setFacilityLock(facility, enable, password, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    public void getFacilityLockForServiceClass(String facility, String password,
        int serviceClass, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {

            /// M: SS Ut part @{
            if (isOutgoingCallBarring(facility) &&
                    isOp01IccCard()) {
                sendErrorResponse(onComplete,
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                return;
            }
            /// @}

            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_GET_CB,
                    onComplete);
            ss.mParcel.writeString(facility);
            ss.mParcel.writeString(password);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
            setServiceClass(serviceClass);
            imsPhone.getCallBarring(facility, imsUtResult);
            return;
        }

        /// M: SS Ut part @{
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isGsmUtSupport()) {
            setServiceClass(serviceClass);
            mSSReqDecisionMaker.queryFacilityLock(facility,
                    password, serviceClass, onComplete);
            return;
        }
        /// @}

        if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
            setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
        }
        mCi.queryFacilityLock(facility, password, serviceClass,
                onComplete);
    }

    public void setFacilityLockForServiceClass(String facility, boolean enable,
        String password, int serviceClass, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED) && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {

            /// M: SS Ut part @{
            if (isOutgoingCallBarring(facility) &&
                    isOp01IccCard()) {
                sendErrorResponse(onComplete,
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                return;
            }
            /// @}

            SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_SET_CB,
                    onComplete);
            ss.mParcel.writeString(facility);
            int enableState = enable ? 1 : 0;
            ss.mParcel.writeInt(enableState);
            ss.mParcel.writeString(password);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
            setServiceClass(serviceClass);
            imsPhone.setCallBarring(facility, enable, password, imsUtResult);
            return;
        }

        /// M: SS Ut part @{
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isGsmUtSupport()) {
            setServiceClass(serviceClass);
            mSSReqDecisionMaker.setFacilityLock(facility,
                    enable, password, serviceClass, onComplete);
            return;
        }
        /// @}

        if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
            setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
        }
        mCi.setFacilityLock(facility, enable, password, serviceClass, onComplete);
    }

    /**
     * Change Call Barring Password
     */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {

        if (isDuringImsCall()) {
            // Prevent CS domain SS request during IMS call
            if (onComplete != null) {
                CommandException ce = new CommandException(
                        CommandException.Error.GENERIC_FAILURE);
                AsyncResult.forMessage(onComplete, null, ce);
                onComplete.sendToTarget();
            }
        } else {
            mCi.changeBarringPassword(facility, oldPwd, newPwd, onComplete);
        }

    }

    /**
     * Change Call Barring Password with confirm
     */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, String newCfm, Message onComplete) {

        if (isDuringImsCall()) {
            // Prevent CS domain SS request during IMS call
            if (onComplete != null) {
                CommandException ce = new CommandException(
                        CommandException.Error.GENERIC_FAILURE);
                AsyncResult.forMessage(onComplete, null, ce);
                onComplete.sendToTarget();
            }
        } else {
            mCi.changeBarringPassword(facility, oldPwd, newPwd, newCfm, onComplete);
        }

    }
    /* M: SS part end */

    /// M: SS OP01 Ut @{
    private static class CfuEx {
        final String mSetCfNumber;
        final long[] mSetTimeSlot;
        final Message mOnComplete;

        CfuEx(String cfNumber, long[] cfTimeSlot, Message onComplete) {
            mSetCfNumber = cfNumber;
            mSetTimeSlot = cfTimeSlot;
            mOnComplete = onComplete;
        }
    }

    @Override
    public void getCallForwardInTimeSlot(int commandInterfaceCFReason, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isOp01IccCard() && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.getCallForwardInTimeSlot(commandInterfaceCFReason, onComplete);
            return;
        }

        if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
            if (LOCAL_DEBUG) {
                Rlog.d(LOG_TAG, "requesting call forwarding in time slot query.");
            }
            Message resp;
            resp = obtainMessage(EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE, onComplete);

            if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                    && isGsmUtSupport()) {
                setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                    UT_CFU_NOTIFICATION_MODE_DISABLED);

                mSSReqDecisionMaker.queryCallForwardInTimeSlotStatus(
                        commandInterfaceCFReason,
                        CommandsInterface.SERVICE_CLASS_VOICE, resp);
            } else {
                sendErrorResponse(onComplete,
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete, CommandException.Error.GENERIC_FAILURE);
        }
    }

    @Override
    public void setCallForwardInTimeSlot(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            long[] timeSlot,
            Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                && isOp01IccCard() && (imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            SuppSrvRequest ss = SuppSrvRequest.obtain(
                    SuppSrvRequest.SUPP_SRV_REQ_SET_CF_IN_TIME_SLOT, onComplete);
            ss.mParcel.writeInt(commandInterfaceCFAction);
            ss.mParcel.writeInt(commandInterfaceCFReason);
            ss.mParcel.writeString(dialingNumber);
            ss.mParcel.writeInt(timerSeconds);
            Message imsUtResult = obtainMessage(EVENT_IMS_UT_DONE, ss);
            imsPhone.setCallForwardInTimeSlot(commandInterfaceCFAction,
                    commandInterfaceCFReason, dialingNumber,
                    timerSeconds, timeSlot, imsUtResult);
            return;
        }

        if ((isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL)) {
            Message resp;
            CfuEx cfuEx = new CfuEx(dialingNumber, timeSlot, onComplete);
            resp = obtainMessage(EVENT_SET_CALL_FORWARD_TIME_SLOT_DONE,
                    isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, cfuEx);

            if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                    && isGsmUtSupport()) {
                mSSReqDecisionMaker.setCallForwardInTimeSlot(commandInterfaceCFAction,
                        commandInterfaceCFReason, CommandsInterface.SERVICE_CLASS_VOICE,
                        dialingNumber, timerSeconds, timeSlot, resp);
            } else {
                sendErrorResponse(onComplete,
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
            }
        } else {
            sendErrorResponse(onComplete, CommandException.Error.GENERIC_FAILURE);
        }
    }

    private void handleCfuInTimeSlotQueryResult(CallForwardInfoEx[] infos) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            if (infos == null || infos.length == 0) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                r.setVoiceCallForwardingFlag(1, false, null);
            } else {
                for (int i = 0, s = infos.length; i < s; i++) {
                    if ((infos[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                        r.setVoiceCallForwardingFlag(1, (infos[i].status == 1),
                                infos[i].number);
                        saveTimeSlot(infos[i].timeSlot);
                        break;
                    }
                }
            }
        }
    }

    void sendErrorResponse(Message onComplete, CommandException.Error error) {
        Rlog.d(LOG_TAG, "sendErrorResponse" + error);
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, new CommandException(error));
            onComplete.sendToTarget();
        }
    }
    /// @}

    public boolean queryCfuOrWait() {
        int sid1 = 99, sid2 = 99;
        /* M: SS part */ //TODO need to check if there any new implementation
        //int slotId = SubscriptionManager.getSlotId(getSubId());//reference code

        /*
        if (mySimId == PhoneConstants.GEMINI_SIM_1) {
           sid1 = PhoneConstants.GEMINI_SIM_2;
           sid2 = PhoneConstants.GEMINI_SIM_3;
        } else if (mySimId == PhoneConstants.GEMINI_SIM_2) {
           sid1 = PhoneConstants.GEMINI_SIM_1;
           sid2 = PhoneConstants.GEMINI_SIM_3;
        } else if (mySimId == PhoneConstants.GEMINI_SIM_3) {
           sid1 = PhoneConstants.GEMINI_SIM_1;
           sid2 = PhoneConstants.GEMINI_SIM_2;
        }*/
        String oppositePropertyValue1 = SystemProperties.get(CFU_QUERY_PROPERTY_NAME + sid1);
        String oppositePropertyValue2 = SystemProperties.get(CFU_QUERY_PROPERTY_NAME + sid2);
        if ((oppositePropertyValue1.equals("1")) ||
            (oppositePropertyValue2.equals("1"))) { /* The opposite phone is querying CFU status */
           Message message = obtainMessage(EVENT_CFU_QUERY_TIMEOUT);
           sendMessageDelayed(message, cfuQueryWaitTime);
           return false;
        } else {
           //setSystemProperty(CFU_QUERY_PROPERTY_NAME + mySimId, "1");//* M: SS part */TODO
           boolean bDataEnable = getDataEnabled();
           Rlog.i(LOG_TAG, "bDataEnable: " + bDataEnable);

           if ((getCsFallbackStatus() == PhoneConstants.UT_CSFB_PS_PREFERRED)
                   && isGsmUtSupport() && bDataEnable) {
               mSSReqDecisionMaker.queryCallForwardInTimeSlotStatus(
                       CF_REASON_UNCONDITIONAL,
                       SERVICE_CLASS_VOICE,
                       obtainMessage(EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE, 1, 0, null));
           } else {
               if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
                   setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
               }
               mCi.queryCallForwardStatus(CF_REASON_UNCONDITIONAL, SERVICE_CLASS_VOICE, null, obtainMessage(EVENT_GET_CALL_FORWARD_DONE));
           }
           return true;
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (LOCAL_DEBUG) Rlog.w(LOG_TAG, "received broadcast " + action);

            /* M: SS part */
            if (TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                SubscriptionManager subMgr = SubscriptionManager.from(mContext);
                SubscriptionInfo mySubInfo = null;
                if (subMgr != null) {
                    mySubInfo = subMgr.getActiveSubscriptionInfo(getSubId());
                }

                String mySettingName = CFU_QUERY_ICCID_PROP + getPhoneId();
                String oldIccId = SystemProperties.get(mySettingName, "");

                String defaultQueryCfuMode = PhoneConstants.CFU_QUERY_TYPE_DEF_VALUE;
                if (mSupplementaryServiceExt != null) {
                    defaultQueryCfuMode = mSupplementaryServiceExt.getOpDefaultQueryCfuMode();
                    Rlog.d(LOG_TAG, "defaultQueryCfuMode = " + defaultQueryCfuMode);
                }
                String cfuSetting = SystemProperties.get(PhoneConstants.CFU_QUERY_TYPE_PROP,
                                defaultQueryCfuMode);

                if ((mySubInfo != null) && (mySubInfo.getIccId() != null)) {
                    if (!mySubInfo.getIccId().equals(oldIccId)) {
                        Rlog.w(LOG_TAG, " mySubId " + getSubId() + " mySettingName "
                                + mySettingName + " old iccid : " + oldIccId + " new iccid : "
                                + mySubInfo.getIccId());
                        SystemProperties.set(mySettingName, mySubInfo.getIccId());
                        String isChanged = CFU_QUERY_SIM_CHANGED_PROP + getPhoneId();
                        SystemProperties.set(isChanged, "1");
                        needQueryCfu = true;
                        setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
                        setTbcwMode(TBCW_UNKNOWN);  //reset to unknow due to sim change.
                        setSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                                TERMINAL_BASED_CALL_WAITING_DISABLED);
                        /// M: SS OP01 Ut
                        saveTimeSlot(null);

                        // Remove the CLIR setting for new SIM
                        SharedPreferences sp =
                                PreferenceManager.getDefaultSharedPreferences(getContext());
                        int clirSetting = sp.getInt(CLIR_KEY + getPhoneId(), -1);
                        if (clirSetting != -1) {
                            SharedPreferences.Editor editor = sp.edit();
                            editor.remove(CLIR_KEY + getPhoneId());
                            if (!editor.commit()) {
                                Rlog.e(LOG_TAG, "failed to commit the removal of CLIR preference");
                            }
                        }

                        setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                UT_CFU_NOTIFICATION_MODE_DISABLED);

                        // When we get Icc ID later than REGISTERED_TO_NETWORK, need to query CFU
                        if (mSST != null && mSST.mSS != null
                                && (mSST.mSS.getState() == ServiceState.STATE_IN_SERVICE)) {
                            Rlog.w(LOG_TAG, "Send EVENT_QUERY_CFU");
                            Message msgQueryCfu = obtainMessage(EVENT_QUERY_CFU);
                            sendMessage(msgQueryCfu);
                        }
                    } else if (cfuSetting.equals("2")) {
                        Rlog.i(LOG_TAG, "Always query CFU.");
                        needQueryCfu = true;
                        Message msgQueryCfu = obtainMessage(EVENT_QUERY_CFU);
                        sendMessage(msgQueryCfu);
                    }
                }
                Rlog.d(LOG_TAG, "onReceive(): ACTION_SUBINFO_RECORD_UPDATED: mTbcwMode = "
                        + mTbcwMode);
                if ((mTbcwMode == TBCW_UNKNOWN) && (isIccCardMncMccAvailable(getPhoneId()))) {
                    if (isOpTbcwWithCS(getPhoneId())) {
                        setTbcwMode(TBCW_OPTBCW_WITH_CS);
                        setTbcwToEnabledOnIfDisabled();
                    }
                }
            } else if (action.equals(ImsManager.ACTION_IMS_STATE_CHANGED)) {
                int reg = intent.getIntExtra(ImsManager.EXTRA_IMS_REG_STATE_KEY , -1);
                int slotId = intent.getIntExtra(ImsManager.EXTRA_PHONE_ID, -1);
                Rlog.d(LOG_TAG, "onReceive ACTION_IMS_STATE_CHANGED: reg=" + reg
                        + ", SimID=" + slotId);
                if (slotId == getPhoneId() && (reg == ServiceState.STATE_IN_SERVICE)) {
                    if (isOpTbcwWithCS(getPhoneId())) {
                        setTbcwMode(TBCW_OPTBCW_WITH_CS);
                        setTbcwToEnabledOnIfDisabled();
                    } else {
                        // TBCW for VoLTE user
                        setTbcwMode(TBCW_OPTBCW_VOLTE_USER);
                        setTbcwToEnabledOnIfDisabled();
                    }

                    Rlog.i(LOG_TAG, "needQueryCfu for IMS CFU status.");
                    needQueryCfu = true;
                    Message msgQueryCfu = obtainMessage(EVENT_QUERY_CFU);
                    sendMessage(msgQueryCfu);
                }
            } else if (action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE)) {
                Rlog.d(LOG_TAG, "set needQueryCfu to true.");
                needQueryCfu = true;
            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean bAirplaneModeOn = intent.getBooleanExtra("state", false);

                Rlog.d(LOG_TAG, "ACTION_AIRPLANE_MODE_CHANGED, bAirplaneModeOn = " +
                       bAirplaneModeOn);
                if (bAirplaneModeOn) {
                    Rlog.d(LOG_TAG, "Set needQueryCfu true.");
                    needQueryCfu = true;
                }
            }
            /* M: SS part end */
        }/* end of onReceive */
    };


    public int getPhoneId() {
        return mPhoneId;
    }
    /* M: SS part end */

    // Added by M begin

    // ALPS00302702 RAT balancing
    public int getEfRatBalancing() {
        if (mIccRecords.get() != null) {
            return mIccRecords.get().getEfRatBalancing();
        }
        return 0;
    }

    // MVNO-API START
    public String getMvnoMatchType() {
        String type = PhoneConstants.MVNO_TYPE_NONE;
        if (mIccRecords.get() != null) {
            type = mIccRecords.get().getMvnoMatchType();
        }
        log("getMvnoMatchType: Type = " + type);
        return type;
    }

    public String getMvnoPattern(String type) {
        String pattern = "";
        log("getMvnoPattern:Type = " + type);

        if (mIccRecords.get() != null) {
            if (type.equals(PhoneConstants.MVNO_TYPE_SPN)) {
                pattern = mIccRecords.get().getSpNameInEfSpn();
            } else if (type.equals(PhoneConstants.MVNO_TYPE_IMSI)) {
                pattern = mIccRecords.get().isOperatorMvnoForImsi();
            } else if (type.equals(PhoneConstants.MVNO_TYPE_PNN)) {
                pattern = mIccRecords.get().isOperatorMvnoForEfPnn();
            } else if (type.equals(PhoneConstants.MVNO_TYPE_GID)) {
                pattern = mIccRecords.get().getGid1();
            } else {
                log("getMvnoPattern: Wrong type.");
            }
        }
        log("getMvnoPattern: pattern = " + pattern);
        return pattern;
    }

    // MVNO-API END
    public void setTrm(int mode, Message response) {
        mCi.setTrm(mode, response);
    }

    /**
     * Request security context authentication for USIM/SIM/ISIM
     */
    public void doGeneralSimAuthentication(int sessionId, int mode, int tag,
            String param1, String param2, Message result) {
        mCi.doGeneralSimAuthentication(sessionId, mode, tag, param1, param2, result);
    }

    public void queryPhbStorageInfo(int type, Message response) {
        mCi.queryPhbStorageInfo(type, response);
    }
    // Added by M end


    public void getPolCapability(Message onComplete) {
        mCi.getPOLCapabilty(onComplete);
    }

    public void getPol(Message onComplete) {
        mCi.getCurrentPOLList(onComplete);
    }

    public void setPolEntry(NetworkInfoWithAcT networkWithAct, Message onComplete) {
        mCi.setPOLEntry(networkWithAct.getPriority(), networkWithAct.getOperatorNumeric(),
                                    networkWithAct.getAccessTechnology(), onComplete);
    }

    /**
       * Check if phone is hiding network temporary out of service state
       * @return if phone is hiding network temporary out of service state.
       */
    public int getNetworkHideState() {
        if (mSST.dontUpdateNetworkStateFlag == true) {
            return ServiceState.STATE_OUT_OF_SERVICE;
        } else {
            return mSST.mSS.getState();
        }
    }

    /**
     * Returns current located PLMN string(ex: "46000") or null if not availble (ex: in flight mode or no signal area)
     */
    public String getLocatedPlmn() {
        return mSST.getLocatedPlmn();
    }

    /**
     * Refresh Spn Display due to configuration change
     @internal
     */
    public void refreshSpnDisplay() {
        mSST.refreshSpnDisplay();
    }

    // Femtocell (CSG) feature START
    public void getFemtoCellList(String operatorNumeric, int rat, Message response) {
        Rlog.d(LOG_TAG, "getFemtoCellList(),operatorNumeric=" + operatorNumeric + ",rat=" + rat);
        mCi.getFemtoCellList(operatorNumeric, rat, response);
    }

    public void abortFemtoCellList(Message response) {
        Rlog.d(LOG_TAG, "abortFemtoCellList()");
        mCi.abortFemtoCellList(response);
    }

    public void selectFemtoCell(FemtoCellInfo femtocell, Message response) {
        Rlog.d(LOG_TAG, "selectFemtoCell(): " + femtocell);
        mCi.selectFemtoCell(femtocell, response);
    }
    // Femtocell (CSG) feature END

    public int isDeviceIdAbnormal() {
        return mImeiAbnormal;
    }

    public void setDeviceIdAbnormal(int abnormal) {
        mImeiAbnormal = abnormal;
    }

    public boolean isDuringImsCall() {
        if (mImsPhone != null) {
            ImsPhoneCall.State foregroundCallState = mImsPhone.getForegroundCall().getState();
            ImsPhoneCall.State backgroundCallState = mImsPhone.getBackgroundCall().getState();
            ImsPhoneCall.State ringingCallState = mImsPhone.getRingingCall().getState();
            boolean isDuringImsCall = (foregroundCallState.isAlive() ||
                    backgroundCallState.isAlive() || ringingCallState.isAlive());
            if (isDuringImsCall) {
                Rlog.d(LOG_TAG, "During IMS call.");
                return true;
            }
        }
        return false;
    }

    private void handleImsUtCsfb(Message msg) {
        SuppSrvRequest ss = (SuppSrvRequest) msg.obj;
        if (ss == null) {
            Rlog.e(LOG_TAG, "handleImsUtCsfb: Error SuppSrvRequest null!");
            return;
        }

        if (isDuringImsCall()) {
            // Prevent CS domain SS request during IMS call
            Message resultCallback = ss.getResultCallback();
            if (resultCallback != null) {
                CommandException ce = new CommandException(
                        CommandException.Error.GENERIC_FAILURE);
                AsyncResult.forMessage(resultCallback, null, ce);
                resultCallback.sendToTarget();
            }

            if (getCsFallbackStatus() == PhoneConstants.UT_CSFB_ONCE) {
                setCsFallbackStatus(PhoneConstants.UT_CSFB_PS_PREFERRED);
            }

            ss.setResultCallback(null);
            ss.mParcel.recycle();
            return;
        }

        final int requestCode = ss.getRequestCode();
        ss.mParcel.setDataPosition(0);
        switch(requestCode) {
            case SuppSrvRequest.SUPP_SRV_REQ_GET_CF:
            {
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_GET_CF");
                int commandInterfaceCFReason = ss.mParcel.readInt();
                getCallForwardingOption(commandInterfaceCFReason, ss.getResultCallback());
                break;
            }
            case SuppSrvRequest.SUPP_SRV_REQ_SET_CF:
            {
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_SET_CF");
                int commandInterfaceCFAction = ss.mParcel.readInt();
                int commandInterfaceCFReason = ss.mParcel.readInt();
                String dialingNumber = ss.mParcel.readString();
                int timerSeconds = ss.mParcel.readInt();
                setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason,
                        dialingNumber, timerSeconds, ss.getResultCallback());
                break;
            }
            case SuppSrvRequest.SUPP_SRV_REQ_GET_CLIR:
            {
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_GET_CLIR");
                getOutgoingCallerIdDisplay(ss.getResultCallback());
                break;
            }
            case SuppSrvRequest.SUPP_SRV_REQ_SET_CLIR:
            {
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_SET_CLIR");
                int commandInterfaceCLIRMode = ss.mParcel.readInt();
                setOutgoingCallerIdDisplay(commandInterfaceCLIRMode, ss.getResultCallback());
                break;
            }
            case SuppSrvRequest.SUPP_SRV_REQ_GET_CW:
            {
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_GET_CW");
                getCallWaiting(ss.getResultCallback());
                break;
            }
            case SuppSrvRequest.SUPP_SRV_REQ_SET_CW:
            {
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_SET_CW");
                int enableState = ss.mParcel.readInt();
                boolean enable = (enableState != 0);
                setCallWaiting(enable, ss.getResultCallback());
                break;
            }
            case SuppSrvRequest.SUPP_SRV_REQ_GET_CB:
            {
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_GET_CB");
                String facility = ss.mParcel.readString();
                String password = ss.mParcel.readString();
                getFacilityLock(facility, password, ss.getResultCallback());
                break;
            }
            case SuppSrvRequest.SUPP_SRV_REQ_SET_CB:
            {
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_SET_CB");
                String facility = ss.mParcel.readString();
                int enableState = ss.mParcel.readInt();
                boolean enable = (enableState != 0);
                String password = ss.mParcel.readString();
                setFacilityLock(facility, enable, password, ss.getResultCallback());
                break;
            }
            case SuppSrvRequest.SUPP_SRV_REQ_MMI_CODE:
            {
                String dialString = ss.mParcel.readString();
                Rlog.d(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_MMI_CODE: dialString = "
                        + dialString);
                try {
                    dial(dialString, VideoProfile.STATE_AUDIO_ONLY);
                } catch (CallStateException ex) {
                    Rlog.e(LOG_TAG, "handleImsUtCsfb: SUPP_SRV_REQ_MMI_CODE: CallStateException!");
                    ex.printStackTrace();
                }
                break;
            }
            default:
                Rlog.e(LOG_TAG, "handleImsUtCsfb: invalid requestCode = " + requestCode);
                break;
        }

        ss.setResultCallback(null);
        ss.mParcel.recycle();
    }

    ///M: For svlte support. @{
    /* package */void
    notifyServiceStateChangedForSvlte(ServiceState ss) {
        super.notifyServiceStateChangedPForSvlte(ss);
    }

    private PhoneBase getActivePhone(int phoneId) {
        PhoneBase csPhone = null;
        Phone phone = PhoneFactory.getPhone(phoneId);

        if (phone instanceof SvltePhoneProxy) {
            csPhone = (PhoneBase) ((SvltePhoneProxy) phone).getCsPhone();
        } else {
            csPhone = (PhoneBase) ((PhoneProxy) phone).getActivePhone();
        }

        Rlog.d(LOG_TAG, "getActivePhone: Phone = " + phone + " ,csPhone = " + csPhone);

        return csPhone;
    }

    ///@}

    /// M: For VoLTE enhanced conference call. @{
    @Override
    public Connection
    dial(List<String> numbers, int videoState) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;
        boolean imsUseEnabled =
                ImsManager.isVolteEnabledByPlatform(mContext) &&
                ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mContext) &&
                ImsManager.isNonTtyOrTtyOnVolteEnabled(mContext);

        if (!imsUseEnabled) {
            Rlog.w(LOG_TAG, "IMS is disabled and can not dial conference call directly.");
            return null;
        }

        if (imsPhone != null) {
            Rlog.w(LOG_TAG, "service state = " + imsPhone.getServiceState().getState());
        }

        if (imsUseEnabled && imsPhone != null
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            try {
                if (LOCAL_DEBUG) {
                    Rlog.d(LOG_TAG, "Trying IMS PS conference call");
                }
                return imsPhone.dial(numbers, videoState);
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) {
                    Rlog.d(LOG_TAG, "IMS PS conference call exception " + e);
                }
                if (!ImsPhone.CS_FALLBACK.equals(e.getMessage())) {
                    CallStateException ce = new CallStateException(e.getMessage());
                    ce.setStackTrace(e.getStackTrace());
                    throw ce;
                }
            }
        }

        return null;
    }
    /// @}
    /*BEGIN-20151022-zubin.chen-add-for-tast730553*/
    private boolean IsRoamingReminderenable() {
        return mContext.getResources().getBoolean(com.android.internal.R.bool.def_show_roaming_reminder_menu) 
        		|| mContext.getResources().getBoolean(com.android.internal.R.bool.def_russian_data_roaming_settings);//20150617-zubin.chen-add-for-df336665
    }
    /*BEGIN-20151022-zubin.chen-add-for-tast730553*/    

    /**
     * Switch antenna.
     * @param callState call state, 0 means call disconnected and 1 means call established.
     * @param ratMode RAT mode, 0 means GSM and 7 means C2K.
     */
    public void switchAntenna(int callState, int ratMode) {
        Rlog.d(LOG_TAG, "switchAntenna, callState:" + callState + ", ratMode:" + ratMode
                + ", mCallEstablished:" + mCallEstablished + ", PHONE_COUNT:" + PHONE_COUNT);
        if (MTK_SWITCH_ANTENNA_SUPPORT && PHONE_COUNT > 1) {
            if ((callState != PhoneConstants.STATE_CONNECTED
                    && callState != PhoneConstants.STATE_DISCONNECTED)
                || (ratMode != PhoneConstants.RAT_MODE_GSM
                        && ratMode != PhoneConstants.RAT_MODE_C2K)) {
                Rlog.e(LOG_TAG, "Invalid parameter!");
                return;
            }
            if ((callState == PhoneConstants.STATE_CONNECTED && mCallEstablished)
                || (callState == PhoneConstants.STATE_DISCONNECTED && !mCallEstablished)
                || (callState == mCallState && ratMode == mRatMode)) {
                Rlog.e(LOG_TAG, "Dummy operation, ignore!");
                return;
            }
            mCallState = callState;
            mRatMode = ratMode;
            if (callState == PhoneConstants.STATE_CONNECTED) {
                mCallEstablished = true;
            } else if (callState == PhoneConstants.STATE_DISCONNECTED) {
                mCallEstablished = false;
            }
            mCi.switchAntenna(callState, ratMode);
        }
    }

    private boolean isIccCardMncMccAvailable(int phoneId) {
        UiccController uiccCtl = UiccController.getInstance();
        IccRecords iccRecords = uiccCtl.getIccRecords(phoneId, UiccController.APP_FAM_3GPP);
        if (iccRecords != null) {
            String mccMnc = iccRecords.getOperatorNumeric();
            Rlog.d(LOG_TAG, "isIccCardMncMccAvailable(): mccMnc is " + mccMnc);
            return (mccMnc != null);
        }
        Rlog.d(LOG_TAG, "isIccCardMncMccAvailable(): false");
        return false;
    }

    /**
     * Check whether GSMPhone support UT interface for the
     * supplementary service configuration or not.
     *
     * @return true if support UT interface in GSMPhone
     */
    public boolean isGsmUtSupport() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        if (SystemProperties.get("ro.mtk_ims_support").equals("1") &&
                SystemProperties.get("ro.mtk_volte_support").equals("1")) {

            if ((isOp01IccCard()
                || isOp03IccCard()
                || isOp05IccCard()
                || isOp06IccCard()
                || isOp07IccCard()
                || isOp11IccCard()
                || isOp15IccCard()
                || isOp18IccCard())
                && isUsimCard()) {
                return true;
            }
        }
        return false;
    }

    public boolean isUsimCard() {
        String iccCardType = PhoneFactory.getPhone(getPhoneId()).getIccCard().getIccCardType();
        if (iccCardType != null && iccCardType.equals("USIM")) {
            return true;
        }
        return false;
    }

    public boolean isOutgoingCallBarring(String facility) {
        if (facility.equals(CommandsInterface.CB_FACILITY_BAOC) ||
            facility.equals(CommandsInterface.CB_FACILITY_BAOIC) ||
            facility.equals(CommandsInterface.CB_FACILITY_BAOICxH)) {
            return true;
        }
        return false;
    }

    public boolean isOp01IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        Rlog.d(LOG_TAG, "isOp01IccCard, mccMnc is " + mccMnc);
        if (mccMnc != null) {
            if (mccMnc.equals("46000") || mccMnc.equals("46002")
                    || mccMnc.equals("46007") || mccMnc.equals("46008")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check whether GSMPhone is op03.
     *
     * @return true if icccard is op03.
     */
    public boolean isOp03IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        if (mccMnc != null) {
            Rlog.d(LOG_TAG, "isOp03IccCard, mccMnc is " + mccMnc);
            return OP03_MCCMNC_LIST.contains(mccMnc);
        }

        Rlog.d(LOG_TAG, "isOp03IccCard: false");
        return false;
    }

    /**
     * Check whether GSMPhone is op05.
     *
     * @return true if icccard is op05.
     */
    public boolean isOp05IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        if (mccMnc != null) {
            Rlog.d(LOG_TAG, "isOp05IccCard, mccMnc is " + mccMnc);
            return OP05_MCCMNC_LIST.contains(mccMnc);
        }

        Rlog.d(LOG_TAG, "isOp05IccCard: false");
        return false;
    }

    public boolean isOp06IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        Rlog.d(LOG_TAG, "isOp06IccCard, mccMnc is " + mccMnc);
        if (mccMnc != null) {
            if (mccMnc.equals("26202")
                || mccMnc.equals("26204")
                || mccMnc.equals("26209")
                || mccMnc.equals("20205")
                || mccMnc.equals("21670")
                || mccMnc.equals("27402")
                || mccMnc.equals("27403")
                || mccMnc.equals("27201")
                || mccMnc.equals("22210")
                || mccMnc.equals("27801")
                || mccMnc.equals("20404")
                || mccMnc.equals("26801")
                || mccMnc.equals("22601")
                || mccMnc.equals("21401")
                || mccMnc.equals("21406")
                || mccMnc.equals("28602")
                || mccMnc.equals("23415")
                || mccMnc.equals("23591")
                || mccMnc.equals("90128")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check whether GSMPhone is op07.
     *
     * @param phoneId input current phone id.
     * @return true if icccard is op07.
     */
    public boolean isOp07IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        if (mccMnc != null) {
            Rlog.d(LOG_TAG, "isOp07IccCard, mccMnc is " + mccMnc);
            return OP07_MCCMNC_LIST.contains(mccMnc);
        }

        Rlog.d(LOG_TAG, "isOp07IccCard: false");
        return false;
    }

    /**
     * Check whether GSMPhone is op08.
     *
     * @return true if icccard is op08.
     */
    public boolean isOp08IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        if (mccMnc != null) {
            Rlog.d(LOG_TAG, "isOp08IccCard, mccMnc is " + mccMnc);
            return OP08_MCCMNC_LIST.contains(mccMnc);
        }

        Rlog.d(LOG_TAG, "isOp08IccCard: false");
        return false;
    }

    /**
     * Check whether GSMPhone is op11.
     *
     * @return true if icccard is op11.
     */
    public boolean isOp11IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        if (mccMnc != null) {
            Rlog.d(LOG_TAG, "isOp11IccCard, mccMnc is " + mccMnc);
            return OP11_MCCMNC_LIST.contains(mccMnc);
        }

        Rlog.d(LOG_TAG, "isOp11IccCard: false");
        return false;
    }

    public boolean isOp15IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        if (mccMnc != null) {
            Rlog.d(LOG_TAG, "isOp15IccCard, mccMnc is " + mccMnc);
            return OP15_MCCMNC_LIST.contains(mccMnc);
        }

        Rlog.d(LOG_TAG, "isOp15IccCard: false");
        return false;
    }

    /**
     * Check whether GSMPhone is op18.
     *
     * @param phoneId input current phone id.
     * @return true if icccard is op18.
     */
    public boolean isOp18IccCard() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        if (mccMnc != null) {
            Rlog.d(LOG_TAG, "isOp18IccCard, mccMnc is " + mccMnc);
            return OP18_MCCMNC_LIST.contains(mccMnc);
        }

        Rlog.d(LOG_TAG, "isOp18IccCard: false");
        return false;
    }

    private boolean isNotSupportXcap(int phoneId) {
        String mccMnc = getOperatorNumeric();
        Rlog.d(LOG_TAG, "isNotSupportXcap, mccMnc is " + mccMnc);
        if (mccMnc != null) {
            return MCCMNC_BLOCK_LIST.contains(mccMnc);
        }

        return false;
    }

    private boolean isOpTbcwWithCS(int phoneId) {
        if (isNotSupportXcap(phoneId)) {
            Rlog.d(LOG_TAG, "isOpTbcwWithCS(): TBCW operator not support.");
            return true;
        }

        Rlog.d(LOG_TAG, "isOpTbcwWithCS(): TBCW operator");
        return false;
    }

    /**
     * Check whether Operator support TBCLIR.
     *
     * @param phoneId input current phone id.
     * @return true if Operator support TBCLIR.
     */
    public boolean isOpTbClir() {
        if (isOp03IccCard()
            || isOp08IccCard()
            || isOp05IccCard()
            || isOp07IccCard()
            || isSupportTBClir()) {
            Rlog.d(LOG_TAG, "isOpTbClir(): true");
            return true;
        }
        Rlog.d(LOG_TAG, "isOpTbClir(): false");
        return false;
    }

    public void setServiceClass(int serviceClass) {
        Rlog.d(LOG_TAG, "setServiceClass: " + serviceClass);
        SystemProperties.set(SS_SERVICE_CLASS_PROP, String.valueOf(serviceClass));
    }

    /**
     * Get the enable/disable for 409 conflict response.
     * @return Operator support 409 response.
     */
    public boolean isEnableXcapHttpResponse409() {
        if (isOp05IccCard()) {
           Rlog.i(LOG_TAG, "isEnableXcapHttpResponse409 is true.");
           return true;
        }

        Rlog.i(LOG_TAG, "isEnableXcapHttpResponse409 is false.");
        return false;
    }

    private boolean isSupportTBClir() {
        if (SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return false;
        }

        String mccMnc = getOperatorNumeric();
        if (mccMnc != null) {
            Rlog.d(LOG_TAG, "isSupportTBClir, mccMnc is " + mccMnc);
            return OP_TBCLIR_MCCMNC_LIST.contains(mccMnc);
        }

        Rlog.d(LOG_TAG, "isSupportTBClir: false");
        return false;
    }

    public boolean queryCFUAgainAfterSet() {
        if (isOp05IccCard()
            || isOp11IccCard()) {
            return true;
        }
        Rlog.d(LOG_TAG, "No need to query CFU again.");
        return false;
    }
}
