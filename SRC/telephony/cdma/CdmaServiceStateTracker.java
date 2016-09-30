/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.internal.telephony.cdma;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Pair;
import android.util.TimeUtils;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.ICarrierConfigLoader;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.SpnOverride;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.HbpcdUtils;

import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.IServiceStateExt;
import com.mediatek.internal.telephony.RadioManager;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
/// M: Customize for swip via code.@{
import com.mediatek.internal.telephony.cdma.IPlusCodeUtils;
import com.mediatek.internal.telephony.cdma.ViaPolicyManager;
/// @}
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.uicc.SvlteUiccUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * {@hide}
 */
public class CdmaServiceStateTracker extends ServiceStateTracker {
    static final String LOG_TAG = "CdmaSST";
    protected static final int SST_TYPE = 1; //Add for CR ALPS02095186
    /// M: Modify the policy for svlte. @{
    protected CDMAPhone mPhone;
    protected CdmaCellLocation mCellLoc;
    protected CdmaCellLocation mNewCellLoc;
    protected int mPreUpdateSpnPhoneId;
    protected int mUpdateSpnPhoneId;
    protected int mSstType = SST_TYPE;
    /// @}

    // Min values used to by getOtasp()
    private static final String UNACTIVATED_MIN2_VALUE = "000000";
    private static final String UNACTIVATED_MIN_VALUE = "1111110111";
    private static final int EVENT_ALL_DATA_DISCONNECTED = 1001;
    private static final int EVENT_CDMA_NETWORK_EXSIT = 1002;

    private static final int MS_PER_HOUR = 60 * 60 * 1000;

    // Current Otasp value
    int mCurrentOtaspMode = OTASP_UNINITIALIZED;

     /** if time between NITZ updates is less than mNitzUpdateSpacing the update may be ignored. */
    private static final int NITZ_UPDATE_SPACING_DEFAULT = 1000 * 60 * 10;
    private int mNitzUpdateSpacing = SystemProperties.getInt("ro.nitz_update_spacing",
            NITZ_UPDATE_SPACING_DEFAULT);

    /** If mNitzUpdateSpacing hasn't been exceeded but update is > mNitzUpdate do the update */
    private static final int NITZ_UPDATE_DIFF_DEFAULT = 2000;
    private int mNitzUpdateDiff = SystemProperties.getInt("ro.nitz_update_diff",
            NITZ_UPDATE_DIFF_DEFAULT);

    /** Time stamp after 19 January 2038 is not supported under 32 bit */
    private static final int MAX_NITZ_YEAR = 2037;
    private boolean mCdmaRoaming = false;
    private int mRoamingIndicator;
    private boolean mIsInPrl;
    private int mDefaultRoamingIndicator;

    /**
     * Initially assume no data connection.
     */
    protected int mRegistrationState = -1;
    protected RegistrantList mCdmaForSubscriptionInfoReadyRegistrants = new RegistrantList();

    /**
     * Sometimes we get the NITZ time before we know what country we
     * are in. Keep the time zone information from the NITZ string so
     * we can fix the time zone once know the country.
     */
    protected boolean mNeedFixZone = false;
    private int mZoneOffset;
    private boolean mZoneDst;
    private long mZoneTime;
    protected boolean mGotCountryCode = false;
    String mSavedTimeZone;
    long mSavedTime;
    long mSavedAtTime;

    /** Wake lock used while setting time of day. */
    private PowerManager.WakeLock mWakeLock;
    private static final String WAKELOCK_TAG = "ServiceStateTracker";

    protected String mMdn;
    protected int mHomeSystemId[] = null;
    protected int mHomeNetworkId[] = null;
    protected String mMin;
    protected String mPrlVersion;
    protected boolean mIsMinInfoReady = false;

    private boolean mIsEriTextLoaded = false;
    protected boolean mIsSubscriptionFromRuim = false;
    private CdmaSubscriptionSourceManager mCdmaSSM;

    protected static final String INVALID_MCC = "000";
    protected static final String DEFAULT_MNC = "03";

    protected HbpcdUtils mHbpcdUtils = null;

    /* Used only for debugging purposes. */
    private String mRegistrationDeniedReason;

    private ContentResolver mCr;
    private String mCurrentCarrier = null;

    /// M: c2k modify, variables. @{

    /**
     * Values correspond to ServiceState.RADIO_TECHNOLOGY_ definitions.
     */
    protected int mNetworkType = 0;
    protected int mNewNetworkType = 0;

    /**
     * Mark when service state is in emergency call only mode.
     */
  ///M: Modify the policy for svlte. @{
    protected boolean mEmergencyOnly = false;
  /// @}

    private String mSid;
    private String mNid;

    private boolean mAutoTimeChanged = false;
    ///M: Modify the policy for svlte. @{
    protected boolean mInService = false;
    /// @}
    ///M: Add for the svlte set radio power and config band mode. @{
    protected boolean mFirstRadioChange = true;
    /// @}
    private IServiceStateExt mServiceStateExt;

    private boolean mEnableNotify = true;

    protected int mCdmaNetWorkMode = ServiceState.RIL_CDMA_NETWORK_MODE_UNKOWN;

    private static final String ACTION_VIA_SET_ETS_DEV = "via.cdma.action.set.ets.dev";
    private static final String EXTRAL_VIA_ETS_DEV = "via.cdma.extral.ets.dev";
    private static final String ACTION_VIA_ETS_DEV_CHANGED = "via.cdma.action.ets.dev.changed";
    ///M: for changed the mtklogger @{
    private static final String ACTION_VIA_SET_ETS_DEV_LOGGER =
        "via.cdma.action.set.ets.dev.c2klogger";
    private static final String ACTION_VIA_ETS_DEV_CHANGED_LOGGER =
        "via.cdma.action.ets.dev.changed.c2klogger";
    /// @}

    private static final String PRL_VERSION_KEY_NAME = "cdma.prl.version";

    /// M: [C2K][SVLTE]. @{
    // Support modem remote SIM access.
    protected boolean mConfigModemStatus = false;
    // Support 3gpp UICC card type.
    static final String[] PROPERTY_RIL_UICC_3GPP_TYPE = {
        "gsm.ril.uicc.3gpptype",
        "gsm.ril.uicc.3gpptype.2",
        "gsm.ril.uicc.3gpptype.3",
        "gsm.ril.uicc.3gpptype.4",
    };
    private static final String[]  PROPERTY_RIL_FULL_UICC_TYPE = {
        "gsm.ril.fulluicctype",
        "gsm.ril.fulluicctype.2",
        "gsm.ril.fulluicctype.3",
        "gsm.ril.fulluicctype.4",
    };
    /// @}

    private boolean mNetworkExsit = true;
    protected boolean mDataRadioRatChanged = false;

    /// M: Customize for swip via code.
    private IPlusCodeUtils mPlusCodeUtils = ViaPolicyManager.getPlusCodeUtils();

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("BroadcastReceiver: " + intent.getAction());
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                /// M: for ALPS01932490 status bar does not update after change language.
                refreshSpnDisplay();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                log("ACTION_SCREEN_ON");
                pollState();
            } else if (intent.getAction().equals(ACTION_VIA_SET_ETS_DEV)) {
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                        SvlteModeController.getCdmaSocketSlotId() !=
                        SvlteUtils.getSlotId(mPhone.getPhoneId())) {
                    return;
                }
                int dev = intent.getIntExtra(EXTRAL_VIA_ETS_DEV, 0);
                dev = (dev >= 0 && dev <= 1) ? dev : 0;
                log("BroadcastReceiver: dev=" + dev);
                setEtsDevices(dev);
            ///M: for changed the mtklogger @{
            } else if (intent.getAction().equals(ACTION_VIA_SET_ETS_DEV_LOGGER)) {
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                        SvlteModeController.getCdmaSocketSlotId() !=
                        SvlteUtils.getSlotId(mPhone.getPhoneId())) {
                    return;
                }
                int dev = intent.getIntExtra(EXTRAL_VIA_ETS_DEV, 0);
                dev = (dev >= 0 && dev <= 1) ? dev : 0;
                log("via.cdma.action.set.ets.dev.c2klogger BroadcastReceiver: dev=" + dev);
                setEtsDevicesLogger(dev);
            } else if (intent.getAction().equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                //ALPS02099188: Force update SPN for normal SPN update happened
                //during the SubscriptionController not ready period
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() &&
                        intent.getIntExtra(SubscriptionManager.INTENT_KEY_DETECT_STATUS,
                                SubscriptionManager.EXTRA_VALUE_NOCHANGE)
                            != SubscriptionManager.EXTRA_VALUE_NOCHANGE) {
                    updateSpnDisplay(true);
                }
            } else if (intent.getAction().equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                                        SubscriptionManager.INVALID_PHONE_INDEX);
                log("phoneId:" + phoneId + ", mPhoneId:" + mPhone.getPhoneId());
                if (phoneId == mPhone.getPhoneId()) {
                    String simStatus = intent.getStringExtra(
                                            IccCardConstants.INTENT_KEY_ICC_STATE);
                    log("simStatus: " + simStatus);
                    if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                        mMdn = null;
                        log("Clear MDN!");
                    }
                }
            }
            /// @}
        }
    };

    /// @}

    private ContentObserver mAutoTimeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (DBG) log("Auto time state changed");
            /// M: c2k modify, sync network time. @{
            // revertToNitzTime();
            if (getAutoTime()) {
                mAutoTimeChanged = true;
                queryCurrentNitzTime();
            } else {
                mAutoTimeChanged = false;
            }
            /// @}
        }
    };

    private ContentObserver mAutoTimeZoneObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (DBG) log("Auto time zone state changed");
            revertToNitzTimeZone();
        }
    };

    public CdmaServiceStateTracker(CDMAPhone phone) {
        this(phone, new CellInfoCdma());
    }

    protected CdmaServiceStateTracker(CDMAPhone phone, CellInfo cellInfo) {
        super(phone, phone.mCi, cellInfo);

        mPhone = phone;
        mCr = phone.getContext().getContentResolver();
        mCellLoc = new CdmaCellLocation();
        mNewCellLoc = new CdmaCellLocation();

        mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(phone.getContext(), mCi, this,
                EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED, null);
        mIsSubscriptionFromRuim = (mCdmaSSM.getCdmaSubscriptionSource() ==
                          CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_RUIM);

        PowerManager powerManager =
                (PowerManager)phone.getContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);

        mCi.registerForRadioStateChanged(this, EVENT_RADIO_STATE_CHANGED, null);

        mCi.registerForVoiceNetworkStateChanged(this, EVENT_NETWORK_STATE_CHANGED_CDMA, null);
        mCi.setOnNITZTime(this, EVENT_NITZ_TIME, null);

        /// M: [C2K][SVLTE] for ps type changed.
        mCi.registerForDataNetworkTypeChanged(this, EVENT_PS_NETWORK_TYPE_CHANGED, null);

        mCi.registerForCdmaPrlChanged(this, EVENT_CDMA_PRL_VERSION_CHANGED, null);
        phone.registerForEriFileLoaded(this, EVENT_ERI_FILE_LOADED, null);
        mCi.registerForCdmaOtaProvision(this,EVENT_OTA_PROVISION_STATUS_CHANGE, null);
        // M: For emergency only @{
        mCi.registerForNetworkExsit(this, EVENT_CDMA_NETWORK_EXSIT, null);
        //M: For emergency only }@

        // System setting property AIRPLANE_MODE_ON is set in Settings.
        int airplaneMode = Settings.Global.getInt(mCr, Settings.Global.AIRPLANE_MODE_ON, 0);
        mDesiredPowerState = ! (airplaneMode > 0);

        mCr.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME), true,
                mAutoTimeObserver);
        mCr.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE), true,
            mAutoTimeZoneObserver);
        setSignalStrengthDefaultValues();

        /// M: c2k modify, register network type changed event. @{
        mCi.registerForNetworkTypeChanged(this, EVENT_NETWORK_TYPE_CHANGED, null);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_VIA_SET_ETS_DEV);
        filter.addAction(ACTION_VIA_SET_ETS_DEV_LOGGER);
        // For ALPS01932490. Monitor locale change
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        // For ALPS02099188. Monitor SubscriptionController ready
        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        phone.getContext().registerReceiver(mIntentReceiver, filter);
        /// @}

        ///M: c2k modify, add for the cdma subsciption.
        mCi.registerForCdmaImsiReady(this, EVENT_CDMA_IMSI_READY_TO_QUERY, null);
        mHbpcdUtils = new HbpcdUtils(phone.getContext());

        // Reset OTASP state in case previously set by another service
        phone.notifyOtaspChanged(OTASP_UNINITIALIZED);

        if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            try {
                mServiceStateExt = MPlugin.createInstance(
                        IServiceStateExt.class.getName(), phone.getContext());
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");

        // Unregister for all events.
        mCi.unregisterForRadioStateChanged(this);
        mCi.unregisterForVoiceNetworkStateChanged(this);
        mCi.unregisterForCdmaOtaProvision(this);
        mPhone.unregisterForEriFileLoaded(this);
        if (mUiccApplcation != null) {mUiccApplcation.unregisterForReady(this);}
        if (mIccRecords != null) {mIccRecords.unregisterForRecordsLoaded(this);}
        mCi.unSetOnNITZTime(this);
        mCr.unregisterContentObserver(mAutoTimeObserver);
        mCr.unregisterContentObserver(mAutoTimeZoneObserver);
        mCdmaSSM.dispose(this);
        mCi.unregisterForCdmaPrlChanged(this);
        /// M: [C2K][SVLTE] for ps type changed. @{
        mCi.unregisterForDataNetworkTypeChanged(this);
        /// @}
        /// M: c2k modify, unregister. @{
        mCi.unregisterForNetworkTypeChanged(this);
        mPhone.getContext().unregisterReceiver(mIntentReceiver);
        /// @}
        ///M: Add for get cdma subscription.
        mCi.unregisterForCdmaImsiReady(this);
        mCi.unregisterForNetworkExsit(this);
        super.dispose();
    }

    @Override
    protected void finalize() {
        if (DBG) log("CdmaServiceStateTracker finalized");
    }

    /**
     * Registration point for subscription info ready
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mCdmaForSubscriptionInfoReadyRegistrants.add(r);

        if (isMinInfoReady()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        mCdmaForSubscriptionInfoReadyRegistrants.remove(h);
    }

    /**
     * Save current source of cdma subscription
     * @param source - 1 for NV, 0 for RUIM
     */
    private void saveCdmaSubscriptionSource(int source) {
        log("Storing cdma subscription source: " + source);
        Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                Settings.Global.CDMA_SUBSCRIPTION_MODE,
                source );
        log("Read from settings: " + Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                    Settings.Global.CDMA_SUBSCRIPTION_MODE, -1));
    }

    private void getSubscriptionInfoAndStartPollingThreads() {
        mCi.getCDMASubscription(obtainMessage(EVENT_POLL_STATE_CDMA_SUBSCRIPTION));

        // Get Registration Information
        pollState();
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;
        int[] ints;
        String[] strings;

        if (!mPhone.mIsTheCurrentActivePhone) {
            loge("Received message " + msg + "[" + msg.what + "]" +
                    " while being destroyed. Ignoring.");
            return;
        }

        switch (msg.what) {
        case EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED:
            handleCdmaSubscriptionSource(mCdmaSSM.getCdmaSubscriptionSource());
            break;

        case EVENT_RUIM_READY:
            setDeviceRatMode(mPhone.getPhoneId());

            /// M: c2k modify, modify. @{
            if (DBG) {
                log("EVENT_RUIM_READY isSubscriptionFromRuim" + mIsSubscriptionFromRuim);
            }
            mIsSubscriptionFromRuim = true;
            /// @}

            if (mPhone.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE) {
                // Subscription will be read from SIM I/O
                if (DBG) log("Receive EVENT_RUIM_READY");
                pollState();
            } else {
                if (DBG) log("Receive EVENT_RUIM_READY and Send Request getCDMASubscription.");
                getSubscriptionInfoAndStartPollingThreads();
            }

            // Only support automatic selection mode in CDMA.
            mCi.getNetworkSelectionMode(obtainMessage(EVENT_POLL_STATE_NETWORK_SELECTION_MODE));

            /// M: c2k modify, modify.
            queueNextSignalStrengthPoll();

            mPhone.prepareEri();

            /// M: c2k modify, modify. @{
            log("after pin unlock get NitzTime!!!!");
            queryCurrentNitzTime();
            /// @}
            break;

        case EVENT_NV_READY:
            updatePhoneObject();

            // Only support automatic selection mode in CDMA.
            mCi.getNetworkSelectionMode(obtainMessage(EVENT_POLL_STATE_NETWORK_SELECTION_MODE));

            /// M: c2k modify, modify. @{
            if (DBG) {
                log("EVENT_NV_READY isSubscriptionFromRuim" + mIsSubscriptionFromRuim);
            }
            mIsSubscriptionFromRuim = false;
            queueNextSignalStrengthPoll();
            /// @}

            // For Non-RUIM phones, the subscription information is stored in
            // Non Volatile. Here when Non-Volatile is ready, we can poll the CDMA
            // subscription info.
            getSubscriptionInfoAndStartPollingThreads();
            break;

        case EVENT_RADIO_STATE_CHANGED:
            if(mCi.getRadioState() == RadioState.RADIO_ON) {
                handleCdmaSubscriptionSource(mCdmaSSM.getCdmaSubscriptionSource());

                /// M: c2k modify, modify. @{
                // there is no RadioState.RADIO_ON state from ril.
                // Signal strength polling stops when radio is off.
                // queueNextSignalStrengthPoll();
                /// @}
            }
            // This will do nothing in the 'radio not available' case.
            /// M: c2k modify, modify. @{
            if (RadioManager.isMSimModeSupport()) {
                log("EVENT_RADIO_STATE_CHANGED setRadioPower:  mDesiredPowerState=" +
                        mDesiredPowerState + "  phoneId=" + mPhone.getPhoneId());
                RadioManager.getInstance().setRadioPower(mDesiredPowerState,
                        mPhone.getPhoneId());
            } else {
                // This will do nothing in the radio not available case.
                setPowerStateToDesired();
            }
            /// @}
            pollState();
            break;

        case EVENT_NETWORK_STATE_CHANGED_CDMA:
            pollState();
            break;

        case EVENT_GET_SIGNAL_STRENGTH:
            // This callback is called when signal strength is polled
            // all by itself.

            if (!(mCi.getRadioState().isOn())) {
                // Polling will continue when radio turns back on.
                return;
            }
            ar = (AsyncResult) msg.obj;
            onSignalStrengthResult(ar, false);
            /// M: [C2K][SVLTE] Notify signal strength changed internally. @{
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                if ((ar.exception == null) && (ar.result != null)) {
                    mSignalStrengthChangedRegistrants.notifyResult(
                            new SignalStrength((SignalStrength) ar.result));
                }
            }
            /// M: [C2K][SVLTE] Notify signal strength changed internally. @}
            queueNextSignalStrengthPoll();

            break;

        /// M: c2k modify. @{
        case EVENT_GET_NITZ_TIME:
            // update time only if networkType is valid.
            // RIL_Rgistration_state.radio_technology
            // RILJ ( 1270): [0027]< REGISTRATION_STATE {1, 0, 0, 8,
            if (!(mCi.getRadioState().isOn()) || this.mNetworkType == 0) {
                Rlog.d(LOG_TAG, "EVENT_GET_NITZ_TIME ignore: isOn()="
                        + mCi.getRadioState().isOn() + ", mRegistrationState="
                        + mRegistrationState + ", networkType=" + this.mNetworkType);
                // return;
            }

            ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Rlog.e(LOG_TAG, "EVENT_GET_NITZ_TIME: " + ar.exception.toString());
                // The request failed.
                return;
            }
            if ((((Object[]) ar.result)[0] == null) || (((Object[]) ar.result)[1] == null)) {
                return;
            }

            String nitzStringSolicite = (String) ((Object[]) ar.result)[0];
            long nitzReceiveTimeSolicite = ((Long) ((Object[]) ar.result)[1]).longValue();
            Rlog.d(LOG_TAG, "EVENT_GET_NITZ_TIME  nitzStringSolicite = " + nitzStringSolicite
                    + "nitzReceiveTimeSolicite = " + nitzReceiveTimeSolicite);
            setTimeFromNITZString(nitzStringSolicite, nitzReceiveTimeSolicite);
            break;
        /// @}

        case EVENT_GET_LOC_DONE_CDMA:
            ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                String states[] = (String[])ar.result;
                int baseStationId = -1;
                int baseStationLatitude = CdmaCellLocation.INVALID_LAT_LONG;
                int baseStationLongitude = CdmaCellLocation.INVALID_LAT_LONG;
                int systemId = -1;
                int networkId = -1;

                if (states.length > 9) {
                    try {
                        if (states[4] != null) {
                            baseStationId = Integer.parseInt(states[4]);
                        }
                        if (states[5] != null) {
                            baseStationLatitude = Integer.parseInt(states[5]);
                        }
                        if (states[6] != null) {
                            baseStationLongitude = Integer.parseInt(states[6]);
                        }
                        // Some carriers only return lat-lngs of 0,0
                        if (baseStationLatitude == 0 && baseStationLongitude == 0) {
                            baseStationLatitude  = CdmaCellLocation.INVALID_LAT_LONG;
                            baseStationLongitude = CdmaCellLocation.INVALID_LAT_LONG;
                        }
                        if (states[8] != null) {
                            systemId = Integer.parseInt(states[8]);
                        }
                        if (states[9] != null) {
                            networkId = Integer.parseInt(states[9]);
                        }
                    } catch (NumberFormatException ex) {
                        loge("error parsing cell location data: " + ex);
                    }
                }

                mCellLoc.setCellLocationData(baseStationId, baseStationLatitude,
                        baseStationLongitude, systemId, networkId);
                mPhone.notifyLocationChanged();
            }

            // Release any temporary cell lock, which could have been
            // acquired to allow a single-shot location update.
            disableSingleLocationUpdate();
            break;

        case EVENT_POLL_STATE_REGISTRATION_CDMA:
        case EVENT_POLL_STATE_GPRS:
            /// M: c2k modify, for TC-IRLAB-02011. @{
            if (!mEnableNotify) {
                break;
            }
            /// @}
        case EVENT_POLL_STATE_OPERATOR_CDMA:
            ar = (AsyncResult) msg.obj;
            handlePollStateResult(msg.what, ar);
            break;

        /// M: [C2K][SVLTE] for ps type changed. @{
        case EVENT_PS_NETWORK_TYPE_CHANGED:
            /// M: c2k modify, for TC-IRLAB-02011. @{
            if (!mEnableNotify) {
                break;
            }
            /// @}
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                ints = (int[]) ar.result;
                int dataRadioTechnology = ints[0];

                if (DBG) {
                    log("handleMessage:EVENT_PS_NETWORK_TYPE_CHANGED, set dataRadioTechnology="
                            + dataRadioTechnology + ", currentRat = "
                            + mSS.getRilDataRadioTechnology());
                }
                boolean hasRilDataRadioTechnologyChanged =
                        dataRadioTechnology != mSS.getRilDataRadioTechnology();
                if (hasRilDataRadioTechnologyChanged) {
                    mDataRadioRatChanged = true;
                    mSS.setRilDataRadioTechnology(dataRadioTechnology);
                    if (mNewNetworkType != 0) {
                        queryCurrentNitzTime();
                    }
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE,
                            ServiceState.rilRadioTechnologyToString(
                                    dataRadioTechnology));
                    notifyDataRegStateRilRadioTechnologyChanged();
                    // M: [C2K][IRAT] Doesn't notify data connection for AP IRAT, since
                    // the current PS may not on the side.
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                        if (!SvlteUtils.isActiveSvlteMode(mPhone)) {
                            mPhone.notifyDataConnection(null);
                        } else {
                            if (SvlteUtils.getSvltePhoneProxy(
                                    mPhone.getPhoneId()).getPsPhone() == mPhone) {
                                mPhone.notifyDataConnection(null);
                            } else {
                                log("Do nothing because it is not current PS phone");
                            }
                        }
                    } else {
                        mPhone.notifyDataConnection(null);
                    }
                }
            }
            break;
        /// @}

        case EVENT_POLL_STATE_CDMA_SUBSCRIPTION: // Handle RIL_CDMA_SUBSCRIPTION
            ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                String cdmaSubscription[] = (String[])ar.result;
                if (cdmaSubscription != null && cdmaSubscription.length >= 5) {
                    mMdn = cdmaSubscription[0];
                    parseSidNid(cdmaSubscription[1], cdmaSubscription[2]);

                    mMin = cdmaSubscription[3];
                    /// M: c2k modify. @{
                    updatePrlVersion(cdmaSubscription[4]);
                    /// @}
                    if (DBG) log("GET_CDMA_SUBSCRIPTION: MDN=" + mMdn);

                    mIsMinInfoReady = true;

                    updateOtaspState();
                    /// M: c2k modify. @{
                    if (mIccRecords != null) {
                        //RuimRecords ruim = (RuimRecords) mIccRecords;
                        // TODO MDN
                        // ruim.setNumberToSimInfo(mMdn);
                        if (DBG) {
                            log(("sendToTarget msg =" + (Message) ar.userObj));
                        }
                        if (((Message) ar.userObj) != null) {
                            AsyncResult.forMessage(((Message) ar.userObj)).exception = ar.exception;
                            ((Message) ar.userObj).sendToTarget();
                        }
                    }
                    /// @}
                    if (!mIsSubscriptionFromRuim && mIccRecords != null) {
                        if (DBG) {
                            log("GET_CDMA_SUBSCRIPTION set imsi in mIccRecords");
                        }
                        mIccRecords.setImsi(getImsi());
                    } else {
                        if (DBG) {
                            log("GET_CDMA_SUBSCRIPTION either mIccRecords is null  or NV type device" +
                                    " - not setting Imsi in mIccRecords");
                        }
                    }
                } else {
                    if (DBG) {
                        log("GET_CDMA_SUBSCRIPTION: error parsing cdmaSubscription params num="
                            + cdmaSubscription.length);
                    }
                }
            }
            break;

        case EVENT_POLL_SIGNAL_STRENGTH:
            // Just poll signal strength...not part of pollState()

            mCi.getSignalStrength(obtainMessage(EVENT_GET_SIGNAL_STRENGTH));
            break;

        /// M: c2k modify. @{
        case EVENT_QUERY_NITZ_TIME:
             mCi.getNitzTime(obtainMessage(EVENT_GET_NITZ_TIME));
             break;
        /// @}

        case EVENT_NITZ_TIME:
            ar = (AsyncResult) msg.obj;

            String nitzString = (String)((Object[])ar.result)[0];
            long nitzReceiveTime = ((Long)((Object[])ar.result)[1]).longValue();

            setTimeFromNITZString(nitzString, nitzReceiveTime);
            break;

        case EVENT_SIGNAL_STRENGTH_UPDATE:
            // This is a notification from CommandsInterface.setOnSignalStrengthUpdate.

            ar = (AsyncResult) msg.obj;

            // The radio is telling us about signal strength changes,
            // so we don't have to ask it.
            mDontPollSignalStrength = true;
            /// M: [C2K][SVLTE] Notify signal strength changed internally. @{
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                if ((ar.exception == null) && (ar.result != null)) {
                    mSignalStrengthChangedRegistrants.notifyResult(
                            new SignalStrength((SignalStrength) ar.result));
                }
            }
            /// M: [C2K][SVLTE] Notify signal strength changed internally. @}

            onSignalStrengthResult(ar, false);
            break;

        case EVENT_RUIM_RECORDS_LOADED:
            /// M: c2k modify. @{
            log("EVENT_RUIM_RECORDS_LOADED get NitzTime!!!!");
            queryCurrentNitzTime();
            /// @}
            log("EVENT_RUIM_RECORDS_LOADED: what=" + msg.what);
            updatePhoneObject();
            updateSpnDisplay();
            break;

        case EVENT_LOCATION_UPDATES_ENABLED:
            ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                mCi.getVoiceRegistrationState(obtainMessage(EVENT_GET_LOC_DONE_CDMA, null));
            }
            break;

        case EVENT_ERI_FILE_LOADED:
            // Repoll the state once the ERI file has been loaded.
            if (DBG) log("[CdmaServiceStateTracker] ERI file has been loaded, repolling.");
            pollState();
            break;

        case EVENT_OTA_PROVISION_STATUS_CHANGE:
            ar = (AsyncResult)msg.obj;
            if (ar.exception == null) {
                ints = (int[]) ar.result;
                int otaStatus = ints[0];
                if (otaStatus == Phone.CDMA_OTA_PROVISION_STATUS_COMMITTED
                    || otaStatus == Phone.CDMA_OTA_PROVISION_STATUS_OTAPA_STOPPED) {
                    if (DBG) log("EVENT_OTA_PROVISION_STATUS_CHANGE: Complete, Reload MDN");
                    mCi.getCDMASubscription( obtainMessage(EVENT_POLL_STATE_CDMA_SUBSCRIPTION));
                }
            }
            break;

        case EVENT_CDMA_PRL_VERSION_CHANGED:
            ar = (AsyncResult)msg.obj;
            if (ar.exception == null) {
                ints = (int[]) ar.result;
                /// M: c2k modify. @{
                updatePrlVersion(Integer.toString(ints[0]));
                /// @}
            }
            break;

        case EVENT_CHANGE_IMS_STATE:
            if (DBG) log("EVENT_CHANGE_IMS_STATE");
            setPowerStateToDesired();
            break;

        case EVENT_POLL_STATE_NETWORK_SELECTION_MODE:
            if (DBG) log("EVENT_POLL_STATE_NETWORK_SELECTION_MODE");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null && ar.result != null) {
                ints = (int[])ar.result;
                if (ints[0] == 1) {  // Manual selection.
                    mPhone.setNetworkSelectionModeAutomatic(null);
                }
            } else {
                log("Unable to getNetworkSelectionMode");
            }
            break;

        /// M: c2k modify. @{

        case EVENT_NETWORK_TYPE_CHANGED:
            if (DBG) {
                log("handleMessage (EVENT_NETWORK_TYPE_CHANGED)");
            }
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                ints = (int[]) ar.result;
                // 0: no network, 2:1x only mode, 4: Do only mode,8:1x/Do mode
                mCdmaNetWorkMode = ints[0];
                if (DBG) {
                    log(" mCdmaNetWorkMode = " + mCdmaNetWorkMode);
                }
                if (mCdmaNetWorkMode == ServiceState.RIL_CDMA_NETWORK_MODE_EVDO_ONLY) {
                    if (DBG) {
                        log("ServiceState.RIL_CDMA_NETWORK_MODE_EVDO_ONLY pollState ");
                    }
                    pollState();
                }
            }
            break;

        case EVENT_ETS_DEV_CHANGED:
            if (DBG) {
                log("handleMessage (EVENT_ETS_DEV_CHANGED)");
            }
            Intent intent = new Intent(ACTION_VIA_ETS_DEV_CHANGED);
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                intent.putExtra("set.ets.dev.result", true);
            } else {
                intent.putExtra("set.ets.dev.result", false);
            }
            mPhone.getContext().sendBroadcast(intent);
            break;

        case EVENT_SET_MDN_DONE:
            if (DBG) {
                log("handleMessage (EVENT_SET_MDN_DONE)");
            }
            ar = (AsyncResult) msg.obj;
            if (DBG) {
                log("sendToTarget msg =" + ar.userObj);
            }
            if (ar.userObj != null) {
                if (ar.exception == null) {
                    // Read mdn
                    if (DBG) {
                        log("setMdnNumber : read mdn after wirte sucess");
                    }
                    mCi.getCDMASubscription(obtainMessage(
                            EVENT_POLL_STATE_CDMA_SUBSCRIPTION, ((Message) ar.userObj)));
                } else {
                    if (DBG) {
                        log("setMdnNumber fail");
                    }
                    AsyncResult.forMessage(((Message) ar.userObj)).exception = ar.exception;
                    ((Message) ar.userObj).sendToTarget();
                }
            }
            break;

        /// @}

        ///M: for changed the mtklogger @{
        case EVENT_ETS_DEV_CHANGED_LOGGER:
            if (DBG) {
                log("handleMessage (ACTION_VIA_ETS_DEV_CHANGED_LOGGER)");
            }
            Intent intentEts = new Intent(ACTION_VIA_ETS_DEV_CHANGED_LOGGER);
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                intentEts.putExtra("set.ets.dev.result", true);
            } else {
                intentEts.putExtra("set.ets.dev.result", false);
            }
            mPhone.getContext().sendBroadcast(intentEts);
            break;

        case EVENT_ALL_DATA_DISCONNECTED:
            log("handle EVENT_ALL_DATA_DISCONNECTED");
            int dds = SubscriptionManager.getDefaultDataSubId();
            ProxyController.getInstance().unregisterForAllDataDisconnected(dds, this);
            synchronized (this) {
                if (mPendingRadioPowerOffAfterDataOff) {
                    if (DBG) log("EVENT_ALL_DATA_DISCONNECTED, turn radio off now.");
                    hangupAndPowerOff();
                    mPendingRadioPowerOffAfterDataOff = false;
                } else {
                    log("EVENT_ALL_DATA_DISCONNECTED is stale");
                }
            }
            break;
        ///@ }

       ///M: c2k modify, add for the cdma subsciption.
        case EVENT_CDMA_IMSI_READY_TO_QUERY:
            if (DBG) {
                log("handleMessage EVENT_CDMA_IMSI_READY_TO_QUERY, get cdma subscription");
            }
            mCi.getCDMASubscription(obtainMessage(EVENT_POLL_STATE_CDMA_SUBSCRIPTION));
            break;

        case EVENT_CDMA_NETWORK_EXSIT:
            log("handleMessage EVENT_CDMA_NETWORK_EXSIT");
            ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                return;
            }
            ints = (int[]) ar.result;
            mNetworkExsit = (1 == ints[0]) ? true : false;
            log("handleMessage EVENT_CDMA_NETWORK_EXSIT, mNetworkExsit = " + mNetworkExsit);
            break;

        default:
            super.handleMessage(msg);
        break;
        }
    }

    private void handleCdmaSubscriptionSource(int newSubscriptionSource) {
        log("Subscription Source : " + newSubscriptionSource);
        mIsSubscriptionFromRuim =
            (newSubscriptionSource == CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_RUIM);
        log("isFromRuim: " + mIsSubscriptionFromRuim);
        saveCdmaSubscriptionSource(newSubscriptionSource);
        if (!mIsSubscriptionFromRuim) {
            // NV is ready when subscription source is NV
            sendMessage(obtainMessage(EVENT_NV_READY));
        }
    }

    @Override
    protected void setPowerStateToDesired() {
        log("setPowerStateToDesired mDesiredPowerState:" + mDesiredPowerState
                + " current radio state:" + mCi.getRadioState()
                + ", mFirstRadioChange = " + mFirstRadioChange);

        // If we want it on and it's off, turn it on
        if (mDesiredPowerState
            && mCi.getRadioState() == CommandsInterface.RadioState.RADIO_OFF) {
            configAndSetRadioPower(true);
        } else if (!mDesiredPowerState && mCi.getRadioState().isOn()) {
            DcTrackerBase dcTracker = mPhone.mDcTracker;

            log("setPowerStateToDesired powerOffRadioSafely");

            // If it's on and available and we want it off gracefully
            powerOffRadioSafely(dcTracker);
        } else if (mDeviceShuttingDown && mCi.getRadioState().isAvailable()) {
            mCi.requestShutdown(null);
        /// M: c2k modify @{
        } else if (!mDesiredPowerState && !mCi.getRadioState().isOn() && mFirstRadioChange) {
            // For boot up in Airplane mode, we would like to startup modem in cfun_state=4
            if (!RadioManager.isMSimModeSupport()) {
                log("VIA For boot up in Airplane mode");
                configAndSetRadioPower(false);
            }
        }
        if (mFirstRadioChange) {
            if (mCi.getRadioState() == CommandsInterface.RadioState.RADIO_UNAVAILABLE) {
                log("First radio changed but radio unavailable, not to set first radio change off");
            } else {
                log("First radio changed and radio available, set first radio change off");
                mFirstRadioChange = false;
            }
        }
        /// @}
    }

    /// M: c2k modify @{

    protected void setDeviceRatMode(int phoneId) {
        log("[setDeviceRatMode]+");
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            int networkType = getPreferredNetworkModeSettings(phoneId);
            if (networkType >= Phone.NT_MODE_WCDMA_PREF) {
                mCi.setPreferredNetworkType(networkType, null);
            } else {
                log("networkType invalid!!");
            }
        }
        log("[setDeviceRatMode]-");
    }

    /**
     * Clean up existing voice and data connection then turn off radio power.
     *
     * Hang up the existing voice calls to decrease call drop rate.
     */
    @Override
    public void powerOffRadioSafely(DcTrackerBase dcTracker) {
        synchronized (this) {
            if (!mPendingRadioPowerOffAfterDataOff) {
                // In some network, deactivate PDP connection cause releasing of RRC connection,
                // which MM/IMSI detaching request needs. Without this detaching, network can
                // not release the network resources previously attached.
                // So we are avoiding data detaching on these networks.
                String[] networkNotClearData = mPhoneBase.getContext().getResources()
                        .getStringArray(com.android.internal.R.array.networks_not_clear_data);
                String currentNetwork = mSS.getOperatorNumeric();
                if ((networkNotClearData != null) && (currentNetwork != null)) {
                    for (int i = 0; i < networkNotClearData.length; i++) {
                        if (currentNetwork.equals(networkNotClearData[i])) {
                            // Don't clear data connection for this carrier
                            if (DBG)
                                log("Not disconnecting data for " + currentNetwork);
                            hangupAndPowerOff();
                            return;
                        }
                    }
                }
                // To minimize race conditions we call cleanUpAllConnections on
                // both if else paths instead of before this isDisconnected test.
                if (dcTracker.isDisconnected()) {
                    // To minimize race conditions we do this after isDisconnected
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    if (DBG) log("Data disconnected, turn off radio right away.");
                    hangupAndPowerOff();
                } else {
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    Message msg = Message.obtain(this);
                    msg.what = EVENT_SET_RADIO_POWER_OFF;
                    msg.arg1 = ++mPendingRadioPowerOffAfterDataOffTag;
                    if (sendMessageDelayed(msg, 5000)) {
                        if (DBG) log("Wait upto 5s for data to disconnect, then turn off radio.");
                        mPhone.registerForAllDataDisconnected(this,
                                    EVENT_ALL_DATA_DISCONNECTED, null);
                        mPendingRadioPowerOffAfterDataOff = true;
                    } else {
                        log("Cannot send delayed Msg, turn off radio right away.");
                        hangupAndPowerOff();
                    }
                }
            }
        }
    }

    private void setEtsDevices(int dev) {
        Message msg = this.obtainMessage(EVENT_ETS_DEV_CHANGED);
        mCi.requestSetEtsDev(dev, msg);
    }

    ///M: for changed the mtklogger @{
    private void setEtsDevicesLogger(int dev) {
        Message msg = this.obtainMessage(EVENT_ETS_DEV_CHANGED_LOGGER);
        mCi.requestSetEtsDev(dev, msg);
    }
    ///@ }

    /// M: for ALPS01932490 status bar does not update after change language.
    /**
     * Refresh SPN items before display.
     */
    public void refreshSpnDisplay() {
        String numeric = mSS.getOperatorNumeric();
        String newAlphaLong = null;
        String newAlphaShort = null;

        if ((numeric != null) && (!(numeric.equals("")))) {
            newAlphaLong = SpnOverride.getInstance().lookupOperatorName(
                           SubscriptionManager.getSubIdUsingPhoneId(mPhone.getPhoneId())
                           , numeric, true, mPhone.getContext());
            newAlphaShort = SpnOverride.getInstance().lookupOperatorName(
                            SubscriptionManager.getSubIdUsingPhoneId(mPhone.getPhoneId())
                            , numeric, false, mPhone.getContext());
            mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA, newAlphaLong);
        }

        log("refreshSpnDisplay set alpha to " + newAlphaLong + "," + newAlphaShort + "," + numeric);

        mSS.setOperatorName(newAlphaLong, newAlphaShort, numeric);
        updateSpnDisplay(true);
    }

    @Override
    protected void updateSpnDisplay() {
        updateSpnDisplay(true);
    }

    protected void updateSpnDisplay(boolean forceUpdate) {
        if (!needUpdateSpn()) {
            Rlog.d(LOG_TAG, "no need to updateSpnDisplay");
            return;
        }

        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                && (mPreUpdateSpnPhoneId != mUpdateSpnPhoneId
                || getUpdateSvlteSpnSstType() != SST_TYPE)) {
            forceUpdate = true;
        }

        int rule = 0;
        String spn = "";
        ///M: Add for the spn display feature. @{
        // From RuimRecord get show display rule and spn
        if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            try {
                if (mServiceStateExt.allowSpnDisplayed()
                        && (!CdmaFeatureOptionUtils.isCT6MSupport())) {
                    IccRecords r = mPhone.mIccRecords.get();
                    rule = (r != null) ? r.getDisplayRule(mSS
                            .getOperatorNumeric()) : IccRecords.SPN_RULE_SHOW_PLMN;
                    spn = (r != null) ? r.getServiceProviderName() : "";
                    log("updateSpnDisplay, rule = " + rule + " spn = " + spn);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        /// @}
        // mOperatorAlphaLong contains the ERI text
        String strNumPlmn = mSS.getOperatorNumeric();
        String plmn = null;
        boolean showPlmn = false;

        if (plmn == null || plmn.equals("")) {
           Rlog.d(LOG_TAG, "No matched EONS and No CPHS ONS");
           plmn = mSS.getOperatorAlphaLong();
           if (plmn == null || plmn.equals(mSS.getOperatorNumeric())) {
               plmn = mSS.getOperatorAlphaShort();
           }

            if (plmn != null) {
                showPlmn = true;
                if (plmn.equals("")) {
                    Rlog.d(LOG_TAG, "add by via");
                    plmn = null;
                }
            }
        }

        Rlog.d(LOG_TAG, "updateSpnDisplay getOperatorAlphaLong = " + mSS.getOperatorAlphaLong()
             + ", getOperatorAlphaShort = " + mSS.getOperatorAlphaShort() + ", plmn = " + plmn);
        // For emergency calls only, pass the EmergencyCallsOnly string via EXTRA_PLMN
        /*if (mEmergencyOnly && cm.getRadioState().isOn()) {
            plmn = Resources.getSystem().
                getText(com.android.internal.R.string.emergency_calls_only).toString();
        }*/
        Rlog.d(LOG_TAG, "updateSpnDisplay ss.getState() = " + mSS.getState()
                              + ", ss.getDataRegState() = " + mSS.getDataRegState());
        // Do not display SPN before get normal service

        if ((mSS.getState() != ServiceState.STATE_IN_SERVICE) &&
                (mSS.getDataRegState() != ServiceState.STATE_IN_SERVICE)) {
            showPlmn = true;
            //plmn = null;
            plmn = Resources.getSystem().
                   getText(com.android.internal.R.string.lockscreen_carrier_default).toString();
        }

        ///M: Fix the emergency only not show in sim pin lock. @{
        boolean isCardLockedOrAbsent = isSimLockedOrAbsent();
        Rlog.d(LOG_TAG, "updateSpnDisplay phone isCardLockedOrAbsent = " + isCardLockedOrAbsent);
        //String cardState = SystemProperties.get("net.cdma.via.card.state");
        //Rlog.d(LOG_TAG, "updateSpnDisplay phone cardState= " + cardState);
        /// @}

        String serviceState = SystemProperties.get("net.cdma.via.service.state");
        Rlog.d(LOG_TAG, "updateSpnDisplay phone serviceState= " + serviceState);

        //show "emergency call only" if state is in services
        //while no RUIM card or RUIM card has been locked
        ///M: Fix the emergency only not show in sim pin lock.@{
        if (isCardLockedOrAbsent
             && serviceState != null && serviceState.equals("in service")
             && mCi.getRadioState().isOn()) {
            Rlog.d(LOG_TAG, "updateSpnDisplay phone show emergency call only");
            showPlmn = true;
        } else {
            if (mSS.getState() == ServiceState.STATE_IN_SERVICE) {
                Rlog.d(LOG_TAG, "updateSpnDisplay card normal");
                showPlmn = true;
            }
        }
        if (mEmergencyOnly) {
            Rlog.d(LOG_TAG,
                    "updateSpnDisplay phone show emergency call only, mEmergencyOnly = true");
            plmn = Resources.getSystem().
                    getText(com.android.internal.R.string.emergency_calls_only).toString();
        }
        /// @}
        ///M: Modify for the spn display feature. @{
        boolean showSpn = false;
        if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            try {
                if (mServiceStateExt.allowSpnDisplayed()
                        && (!CdmaFeatureOptionUtils.isCT6MSupport())) {
                    showSpn = !TextUtils.isEmpty(spn)
                        && ((rule & RuimRecords.SPN_RULE_SHOW_SPN)
                                == RuimRecords.SPN_RULE_SHOW_SPN);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }

        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int[] subIds = SubscriptionManager.getSubId(mPhone.getPhoneId());
        if (subIds != null && subIds.length > 0) {
            subId = subIds[0];
        }

        boolean plmnspnShowCondition = showPlmn != mCurShowPlmn || showSpn != mCurShowSpn
                || !TextUtils.equals(spn, mCurSpn)
                || !TextUtils.equals(plmn, mCurPlmn)
                || mSubId != subId;
        log("updateSpnDisplay: mSubId = " + mSubId + ", subId =" + subId
                + "plmnspnShowCondition is " + plmnspnShowCondition);

        if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            try {
                plmnspnShowCondition = (mServiceStateExt.allowSpnDisplayed()
                        && (!CdmaFeatureOptionUtils.isCT6MSupport())) ?
                        (showPlmn != mCurShowPlmn || showSpn != mCurShowSpn
                        || !TextUtils.equals(spn, mCurSpn)
                        || !TextUtils.equals(plmn, mCurPlmn)
                        || mSubId != subId)
                        : (!TextUtils.equals(plmn, mCurPlmn) || mSubId != subId);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }

        if (plmnspnShowCondition || forceUpdate) {
            // Allow A blank plmn, "" to set showPlmn to true. Previously, we
            // would set showPlmn to true only if plmn was not empty, i.e. was not
            // null and not blank. But this would cause us to incorrectly display
            // "No Service". Now showPlmn is set to true for any non null string.
            showPlmn = plmn != null;

            ///M: Modify for the spn display feature. @{
            // airplane mode, roaming state or spn is null, do not show spn

            if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
                try {
                    if (mServiceStateExt.allowSpnDisplayed()
                            && (!CdmaFeatureOptionUtils.isCT6MSupport())) {
                        if (mSS.getVoiceRegState() == ServiceState.STATE_POWER_OFF
                                || mSS.getRoaming()
                                || (spn == null || spn.equals(""))) {
                            showSpn = false;
                            showPlmn = true;
                        } else {
                            showSpn = true;
                            showPlmn = false;
                        }
                    }
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
            if (DBG) {
                log(String.format("updateSpnDisplay: changed sending intent" +
                            " showPlmn='%b' plmn='%s' showSpn='%b' spn='%s' subId='%d' ",
                            showPlmn, plmn, showSpn, spn, subId));
            }

            Intent intent = new Intent(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);

            // For Gemini, share the same intent, do not replace the other one
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);

            intent.putExtra(TelephonyIntents.EXTRA_SHOW_SPN, showSpn);
            intent.putExtra(TelephonyIntents.EXTRA_SPN, spn);
            intent.putExtra(TelephonyIntents.EXTRA_SHOW_PLMN, showPlmn);
            intent.putExtra(TelephonyIntents.EXTRA_PLMN, plmn);

            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhone.getPhoneId());
            mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);

            if (plmnspnShowCondition) {
                boolean setResult = mSubscriptionController.setPlmnSpn(mPhone.getPhoneId(),
                        showPlmn, plmn, showSpn, spn);
                if (!setResult) {
                    mSpnUpdatePending = true;
                }
                log("CDMA showSpn: setResult = " + setResult);
            }

            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                setUpdateSvlteSpnSstType(SST_TYPE);
            }

            log("CDMA showSpn:" + showSpn + " spn:" + spn +
                    " showPlmn:" + showPlmn + " plmn:" + plmn +
                    " rule:" +  rule  + " phoneId:" + mPhone.getPhoneId());
        }

        ///M: Modify for the spn display feature. @{
        mCurShowSpn = showSpn;
        mCurShowPlmn = showPlmn;
        mSubId = subId;
        mCurSpn = spn;
        mCurPlmn = plmn;
        ///@}
    }

    /// @}

    @Override
    protected Phone getPhone() {
        return mPhone;
    }

    /**
    * Hanlde the PollStateResult message
    */
    protected void handlePollStateResultMessage(int what, AsyncResult ar){
        int ints[];
        String states[];
        switch (what) {
            case EVENT_POLL_STATE_GPRS: {
                states = (String[])ar.result;
                if (DBG) {
                    log("handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=" +
                            states.length + " states=" + states);
                }

                int regState = ServiceState.RIL_REG_STATE_UNKNOWN;
                int dataRadioTechnology = 0;

                if (states.length > 0) {
                    try {
                        regState = Integer.parseInt(states[0]);

                        // states[3] (if present) is the current radio technology
                        if (states.length >= 4 && states[3] != null) {
                            dataRadioTechnology = Integer.parseInt(states[3]);
                        }
                    } catch (NumberFormatException ex) {
                        loge("handlePollStateResultMessage: error parsing GprsRegistrationState: "
                                        + ex);
                    }
                }

                int dataRegState = regCodeToServiceState(regState);
                String roamingIndicator = "";
                if (states != null && states.length >= 11 && states[10] != null) {
                    roamingIndicator = states[10];
                }
                boolean isDataRoaming = regCodeIsRoaming(regState)
                    && !isRoamIndForHomeSystem(roamingIndicator);
                mNewSS.setDataRegState(dataRegState);
                mNewSS.setRilDataRadioTechnology(dataRadioTechnology);
                mNewSS.setDataRoaming(regCodeIsRoaming(regState));
                if (isDataRoaming) {
                    mNewSS.setRilDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
                    // "Registered, roaming"
                } else {
                    mNewSS.setRilDataRegState(regState == ServiceState.RIL_REG_STATE_ROAMING ?
                            ServiceState.RIL_REG_STATE_HOME : regState);
                }

                if (DBG) {
                    log("handlPollStateResultMessage: cdma setDataRegState=" + dataRegState
                            + " regState=" + regState
                            + " dataRadioTechnology=" + dataRadioTechnology
                            + " isDataRoaming=" + isDataRoaming
                            + " roamingIndicator=" + roamingIndicator);
                }
                break;
            }

            case EVENT_POLL_STATE_REGISTRATION_CDMA: // Handle RIL_REQUEST_REGISTRATION_STATE.
                states = (String[])ar.result;

                int registrationState = 4;     //[0] registrationState
                int radioTechnology = -1;      //[3] radioTechnology
                int baseStationId = -1;        //[4] baseStationId
                //[5] baseStationLatitude
                int baseStationLatitude = CdmaCellLocation.INVALID_LAT_LONG;
                //[6] baseStationLongitude
                int baseStationLongitude = CdmaCellLocation.INVALID_LAT_LONG;
                int cssIndicator = 0;          //[7] init with 0, because it is treated as a boolean
                int systemId = 0;              //[8] systemId
                int networkId = 0;             //[9] networkId
                int roamingIndicator = -1;     //[10] Roaming indicator
                int systemIsInPrl = 0;         //[11] Indicates if current system is in PRL
                int defaultRoamingIndicator = 0;  //[12] Is default roaming indicator from PRL
                int reasonForDenial = 0;       //[13] Denial reason if registrationState = 3

                if (states.length >= 14) {
                    try {
                        if (states[0] != null) {
                            registrationState = Integer.parseInt(states[0]);
                        }
                        if (states[3] != null) {
                            radioTechnology = Integer.parseInt(states[3]);
                        }
                        if (states[4] != null) {
                            baseStationId = Integer.parseInt(states[4]);
                        }
                        if (states[5] != null) {
                            baseStationLatitude = Integer.parseInt(states[5]);
                        }
                        if (states[6] != null) {
                            baseStationLongitude = Integer.parseInt(states[6]);
                        }
                        // Some carriers only return lat-lngs of 0,0
                        if (baseStationLatitude == 0 && baseStationLongitude == 0) {
                            baseStationLatitude  = CdmaCellLocation.INVALID_LAT_LONG;
                            baseStationLongitude = CdmaCellLocation.INVALID_LAT_LONG;
                        }
                        if (states[7] != null) {
                            cssIndicator = Integer.parseInt(states[7]);
                        }
                        if (states[8] != null) {
                            systemId = Integer.parseInt(states[8]);
                            /// M: c2k modify. @{
                            mSid = states[8];
                            log("handlePollStateResultMessage: mSid=" + mSid);
                            /// @}
                        }
                        if (states[9] != null) {
                            networkId = Integer.parseInt(states[9]);
                            /// M: c2k modify. @{
                            mNid = states[9];
                            log("handlePollStateResultMessage: mNid=" + mNid);
                            /// @}
                        }
                        if (states[10] != null) {
                            roamingIndicator = Integer.parseInt(states[10]);
                        }
                        if (states[11] != null) {
                            systemIsInPrl = Integer.parseInt(states[11]);
                        }
                        if (states[12] != null) {
                            defaultRoamingIndicator = Integer.parseInt(states[12]);
                        }
                        if (states[13] != null) {
                            reasonForDenial = Integer.parseInt(states[13]);
                        }
                    } catch (NumberFormatException ex) {
                        loge("EVENT_POLL_STATE_REGISTRATION_CDMA: error parsing: " + ex);
                    }
                } else {
                    throw new RuntimeException("Warning! Wrong number of parameters returned from "
                                         + "RIL_REQUEST_REGISTRATION_STATE: expected 14 or more "
                                         + "strings and got " + states.length + " strings");
                }

                mRegistrationState = registrationState;
                // When registration state is roaming and TSB58
                // roaming indicator is not in the carrier-specified
                // list of ERIs for home system, mCdmaRoaming is true.
                mCdmaRoaming =
                        regCodeIsRoaming(registrationState) && !isRoamIndForHomeSystem(states[10]);
                mNewSS.setVoiceRoaming(mCdmaRoaming);
                mNewSS.setState(regCodeToServiceState(registrationState));

                /// M: c2k modify. @{
                if (mCdmaRoaming) {
                    mNewSS.setRegState(ServiceState.RIL_REG_STATE_ROAMING); // "Registered, roaming"
                } else {
                    mNewSS.setRegState(registrationState == ServiceState.RIL_REG_STATE_ROAMING ?
                            ServiceState.RIL_REG_STATE_HOME : registrationState);
                }
                if (DBG) {
                    log("mNewSS.getRegState()=" + mNewSS.getRegState());
                }

                if (DBG) {
                    log("newSS.setCdmaNetworkMode;");
                }
                mNewSS.setCdmaNetworkMode(mCdmaNetWorkMode);
                /// @}

                mNewSS.setRilVoiceRadioTechnology(radioTechnology);

                mNewSS.setCssIndicator(cssIndicator);
                /// M: c2k modify. @{
                if (DBG) {
                    log("setSystemProperty(cdma.operator.sid)=" + Integer.toString(systemId));
                }
                /// M: Customize for swip via code.
                SystemProperties.set(mPlusCodeUtils.PROPERTY_OPERATOR_SID,
                        Integer.toString(systemId));
                /// @}
                mNewSS.setSystemAndNetworkId(systemId, networkId);
                mRoamingIndicator = roamingIndicator;
                mIsInPrl = (systemIsInPrl == 0) ? false : true;
                mDefaultRoamingIndicator = defaultRoamingIndicator;


                // Values are -1 if not available.
                mNewCellLoc.setCellLocationData(baseStationId, baseStationLatitude,
                        baseStationLongitude, systemId, networkId);

                if (reasonForDenial == 0) {
                    mRegistrationDeniedReason = ServiceStateTracker.REGISTRATION_DENIED_GEN;
                } else if (reasonForDenial == 1) {
                    mRegistrationDeniedReason = ServiceStateTracker.REGISTRATION_DENIED_AUTH;
                } else {
                    mRegistrationDeniedReason = "";
                }

                if (mRegistrationState == 3) {
                    if (DBG) log("Registration denied, " + mRegistrationDeniedReason);
                }
                break;

            case EVENT_POLL_STATE_OPERATOR_CDMA: // Handle RIL_REQUEST_OPERATOR
                String opNames[] = (String[])ar.result;

                if (opNames != null && opNames.length >= 3) {
                    // TODO: Do we care about overriding in this case.
                    // If the NUMERIC field isn't valid use PROPERTY_CDMA_HOME_OPERATOR_NUMERIC
                    if ((opNames[2] == null) || (opNames[2].length() < 5)
                            || ("00000".equals(opNames[2]))
                            || ("N/AN/A".equals(opNames[2]))) {
                        opNames[2] = SystemProperties.get(
                                CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, "");
                        if (DBG) {
                            log("RIL_REQUEST_OPERATOR.response[2], the numeric, " +
                                    " is bad. Using SystemProperties '" +
                                            CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC +
                                    "'= " + opNames[2]);
                        }
                    }

                    /// M: c2k modify. @{
                    if (DBG) {
                        log("RIL_REQUEST_OPERATOR.opNames[0] = " + opNames[0]
                                + ", opNames[1] = '" + opNames[1]
                                + ", opNames[2] = " + opNames[2]);
                    }

                    String numeric = opNames[2];
                    if (numeric.startsWith("2134") && numeric.length() == 7) {
                        /// M: Customize for swip via code.
                        String tempStr = mPlusCodeUtils.checkMccBySidLtmOff(numeric);
                        if (!tempStr.equals("0")) {
                            opNames[2] = tempStr + numeric.substring(4);
                            numeric = tempStr;
                            log("the result of checkMccBySidLtmOff: numeric =" + numeric
                                    + ", plmn =" + opNames[2]);
                        }
                        opNames[0] = SpnOverride.getInstance().lookupOperatorName(
                                mPhone.getSubId(), opNames[2], true, mPhone.getContext());
                        opNames[1] = SpnOverride.getInstance().lookupOperatorName(
                                mPhone.getSubId(), opNames[2], false, mPhone.getContext());
                    }
                    /// @}

                    if (!mIsSubscriptionFromRuim) {
                        // NV device (as opposed to CSIM)
                        mNewSS.setOperatorName(null, opNames[1], opNames[2]);
                    } else {
                        String brandOverride = mUiccController.getUiccCard(getPhoneId()) != null ?
                            mUiccController.getUiccCard(getPhoneId()).getOperatorBrandOverride() : null;
                        if (brandOverride != null) {
                            log("EVENT_POLL_STATE_OPERATOR_CDMA: use brandOverride="
                                + brandOverride);
                            mNewSS.setOperatorName(brandOverride, brandOverride, opNames[2]);
                        } else {
                            String strOperatorLong = null;
                            String strOperatorShort = null;
                            SpnOverride spnOverride = SpnOverride.getInstance();

                            strOperatorLong = mCi.lookupOperatorNameFromNetwork(
                                              SubscriptionManager.getSubIdUsingPhoneId(
                                              mPhone.getPhoneId()), opNames[2], true);
                            if (strOperatorLong != null) {
                                log("EVENT_POLL_STATE_OPERATOR_CDMA: OperatorLong use lookupOperatorNameFromNetwork");
                            } else {
                                strOperatorLong = spnOverride.lookupOperatorName(
                                                  SubscriptionManager.getSubIdUsingPhoneId(
                                                  mPhone.getPhoneId()), opNames[2], true,
                                                  mPhone.getContext());
                                if (strOperatorLong != null) {
                                    log("EVENT_POLL_STATE_OPERATOR_CDMA: OperatorLong use lookupOperatorName");
                                } else {
                                    log("EVENT_POLL_STATE_OPERATOR_CDMA: OperatorLong use value from ril");
                                    strOperatorLong = opNames[0];
                                }
                            }
                            strOperatorShort = mCi.lookupOperatorNameFromNetwork(
                                              SubscriptionManager.getSubIdUsingPhoneId(
                                              mPhone.getPhoneId()), opNames[2], false);
                            if (strOperatorShort != null) {
                                log("EVENT_POLL_STATE_OPERATOR_CDMA: OperatorShort use lookupOperatorNameFromNetwork");
                            } else {
                                strOperatorShort = spnOverride.lookupOperatorName(
                                                  SubscriptionManager.getSubIdUsingPhoneId(
                                                  mPhone.getPhoneId()),
                                                  opNames[2], false, mPhone.getContext());
                                if (strOperatorShort != null) {
                                    log("EVENT_POLL_STATE_OPERATOR_CDMA: OperatorShort use lookupOperatorName");
                                } else {
                                    log("EVENT_POLL_STATE_OPERATOR_CDMA: OperatorShort use value from ril");
                                    strOperatorShort = opNames[1];
                                }
                            }
                            log("EVENT_POLL_STATE_OPERATOR_CDMA: " + strOperatorLong + ", " + strOperatorShort);
                            mNewSS.setOperatorName(strOperatorLong, strOperatorShort, opNames[2]);
                        }
                    }

                    /// M: c2k modify. @{
                    if ((opNames[2] != null) && opNames[2].length() >= 5) {
                        String mccmnc = opNames[2];

                    if (opNames[2].length() == 7) {
                        if (DBG) {
                            log("for MCC = (1111), setSystemProperty(cdma.operator.mcc) = "
                                    + mccmnc.substring(0, 4));
                        }
                        /// M: Customize for swip via code.
                        SystemProperties.set(mPlusCodeUtils.PROPERTY_OPERATOR_MCC,
                                mccmnc.substring(0, 4));
                    } else {
                        if (DBG) {
                            log("setSystemProperty(cdma.operator.mcc) = " + mccmnc.substring(0, 3));
                        }
                        /// M: Customize for swip via code.
                        SystemProperties.set(mPlusCodeUtils.PROPERTY_OPERATOR_MCC,
                                mccmnc.substring(0, 3));
                    }

                    if (!mccmnc.equals(mSS.getOperatorNumeric())) {
                        if (DBG) {
                            log("broadcast " + TelephonyIntents.ACTION_MCC_MNC_CHANGED + ":"
                                    + mccmnc);
                        }
                        Intent intent = new Intent(TelephonyIntents.ACTION_MCC_MNC_CHANGED);
                        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                        intent.putExtra(TelephonyIntents.EXTRA_MCC_MNC_CHANGED_MCC,
                                mccmnc.substring(0, 3));
                        intent.putExtra(TelephonyIntents.EXTRA_MCC_MNC_CHANGED_MNC,
                                mccmnc.substring(3, mccmnc.length()));
                        mPhone.getContext().sendStickyBroadcast(intent);
                    }
                    }
                    /// @}
                } else {
                    if (DBG) log("EVENT_POLL_STATE_OPERATOR_CDMA: error parsing opNames");
                }
                break;

            default:
                loge("handlePollStateResultMessage: RIL response handle in wrong phone!"
                        + " Expected CDMA RIL request and get GSM RIL request.");
                break;
        }
    }

    /**
     * Handle the result of one of the pollState() - related requests
     */
    @Override
    protected void handlePollStateResult(int what, AsyncResult ar) {
        // Ignore stale requests from last poll.
        if (ar.userObj != mPollingContext) return;

        if (ar.exception != null) {
            CommandException.Error err=null;

            if (ar.exception instanceof CommandException) {
                err = ((CommandException)(ar.exception)).getCommandError();
            }

            if (err == CommandException.Error.RADIO_NOT_AVAILABLE) {
                // Radio has crashed or turned off.
                cancelPollState();
                return;
            }

            if (!mCi.getRadioState().isOn()) {
                // Radio has crashed or turned off.
                cancelPollState();
                return;
            }

            if (err != CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW) {
                loge("handlePollStateResult: RIL returned an error where it must succeed"
                        + ar.exception);
            }
        } else try {
            handlePollStateResultMessage(what, ar);
        } catch (RuntimeException ex) {
            loge("handlePollStateResult: Exception while polling service state. "
                    + "Probably malformed RIL response." + ex);
        }

        mPollingContext[0]--;

        if (mPollingContext[0] == 0) {
            boolean namMatch = false;
            if (!isSidsAllZeros() && isHomeSid(mNewSS.getSystemId())) {
                namMatch = true;
            }

            // Setting SS Roaming (general)
            if (mIsSubscriptionFromRuim) {
                mNewSS.setVoiceRoaming(isRoamingBetweenOperators(mNewSS.getVoiceRoaming(), mNewSS));
            }
            // For CDMA, voice and data should have the same roaming status
            final boolean isVoiceInService =
                    (mNewSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE);
            final int dataRegType = mNewSS.getRilDataRadioTechnology();
            if (isVoiceInService && ServiceState.isCdma(dataRegType)) {
                mNewSS.setDataRoaming(mNewSS.getVoiceRoaming());
            }

            /// M: c2k modify. @{
            //add by via show EccButton when PIN and PUK status
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                mEmergencyOnly = false;
                if (mCi.getRadioState().isOn()) {
                    if ((mNewSS.getVoiceRegState() == ServiceState.STATE_OUT_OF_SERVICE) &&
                            (mNewSS.getDataRegState() == ServiceState.STATE_OUT_OF_SERVICE)
                            && mNetworkExsit) {
                        log("handlePollStateResult: STATE_OUT_OF_SERVICE, mEmergencyOnly=true");
                        mEmergencyOnly = true;
                    }
                    if (isSimLockedOrAbsent() && mNetworkExsit) {
                        log("handlePollStateResult: card lock or absent, mEmergencyOnly=true");
                        mEmergencyOnly = true;
                    }
                }
                log("handlePollStateResult: set mEmergencyOnly=" + mEmergencyOnly
                    + ", mNetworkExsit=" + mNetworkExsit);
            }
            mNewSS.setEmergencyOnly(mEmergencyOnly);
            /// @}

            // Setting SS CdmaRoamingIndicator and CdmaDefaultRoamingIndicator
            mNewSS.setCdmaDefaultRoamingIndicator(mDefaultRoamingIndicator);
            mNewSS.setCdmaRoamingIndicator(mRoamingIndicator);
            boolean isPrlLoaded = true;
            if (TextUtils.isEmpty(mPrlVersion)) {
                isPrlLoaded = false;
            }
            if (!isPrlLoaded || (mNewSS.getRilVoiceRadioTechnology()
                                        == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN)) {
                log("Turn off roaming indicator if !isPrlLoaded or voice RAT is unknown");
                mNewSS.setCdmaRoamingIndicator(EriInfo.ROAMING_INDICATOR_OFF);
            } else if (!isSidsAllZeros()) {
                if (!namMatch && !mIsInPrl) {
                    // Use default
                    mNewSS.setCdmaRoamingIndicator(mDefaultRoamingIndicator);
                } else if (namMatch && !mIsInPrl) {
                    // TODO this will be removed when we handle roaming on LTE on CDMA+LTE phones
                    if (mNewSS.getRilVoiceRadioTechnology()
                            == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) {
                        log("Turn off roaming indicator as voice is LTE");
                        mNewSS.setCdmaRoamingIndicator(EriInfo.ROAMING_INDICATOR_OFF);
                    } else {
                        mNewSS.setCdmaRoamingIndicator(EriInfo.ROAMING_INDICATOR_FLASH);
                    }
                } else if (!namMatch && mIsInPrl) {
                    // Use the one from PRL/ERI
                    mNewSS.setCdmaRoamingIndicator(mRoamingIndicator);
                } else {
                    // It means namMatch && mIsInPrl
                    if ((mRoamingIndicator <= 2)) {
                        mNewSS.setCdmaRoamingIndicator(EriInfo.ROAMING_INDICATOR_OFF);
                    } else {
                        // Use the one from PRL/ERI
                        mNewSS.setCdmaRoamingIndicator(mRoamingIndicator);
                    }
                }
            }

            int roamingIndicator = mNewSS.getCdmaRoamingIndicator();
            mNewSS.setCdmaEriIconIndex(mPhone.mEriManager.getCdmaEriIconIndex(roamingIndicator,
                    mDefaultRoamingIndicator));
            mNewSS.setCdmaEriIconMode(mPhone.mEriManager.getCdmaEriIconMode(roamingIndicator,
                    mDefaultRoamingIndicator));

            // NOTE: Some operator may require overriding mCdmaRoaming
            // (set by the modem), depending on the mRoamingIndicator.

            if (DBG) {
                log("Set CDMA Roaming Indicator to: " + mNewSS.getCdmaRoamingIndicator()
                    + ". voiceRoaming = " + mNewSS.getVoiceRoaming()
                    + ". dataRoaming = " + mNewSS.getDataRoaming()
                    + ", isPrlLoaded = " + isPrlLoaded
                    + ". namMatch = " + namMatch + " , mIsInPrl = " + mIsInPrl
                    + ", mRoamingIndicator = " + mRoamingIndicator
                    + ", mDefaultRoamingIndicator= " + mDefaultRoamingIndicator);
            }
            pollStateDone();
        }

    }

    /**
     * Set both voice and data roaming type,
     * judging from the roaming indicator
     * or ISO country of SIM VS network.
     */
    protected void setRoamingType(ServiceState currentServiceState) {
        final boolean isVoiceInService =
                (currentServiceState.getVoiceRegState() == ServiceState.STATE_IN_SERVICE);
        if (isVoiceInService) {
            if (currentServiceState.getVoiceRoaming()) {
                // some carrier defines international roaming by indicator
                int[] intRoamingIndicators = mPhone.getContext().getResources().getIntArray(
                        com.android.internal.R.array.config_cdma_international_roaming_indicators);
                if ((intRoamingIndicators != null) && (intRoamingIndicators.length > 0)) {
                    // It's domestic roaming at least now
                    currentServiceState.setVoiceRoamingType(ServiceState.ROAMING_TYPE_DOMESTIC);
                    int curRoamingIndicator = currentServiceState.getCdmaRoamingIndicator();
                    for (int i = 0; i < intRoamingIndicators.length; i++) {
                        if (curRoamingIndicator == intRoamingIndicators[i]) {
                            currentServiceState.setVoiceRoamingType(
                                    ServiceState.ROAMING_TYPE_INTERNATIONAL);
                            break;
                        }
                    }
                } else {
                    // check roaming type by MCC
                    if (inSameCountry(currentServiceState.getVoiceOperatorNumeric())) {
                        currentServiceState.setVoiceRoamingType(
                                ServiceState.ROAMING_TYPE_DOMESTIC);
                    } else {
                        currentServiceState.setVoiceRoamingType(
                                ServiceState.ROAMING_TYPE_INTERNATIONAL);
                    }
                }
            } else {
                currentServiceState.setVoiceRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
            }
        }
        final boolean isDataInService =
                (currentServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE);
        final int dataRegType = currentServiceState.getRilDataRadioTechnology();
        if (isDataInService) {
            if (!currentServiceState.getDataRoaming()) {
                currentServiceState.setDataRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
            } else if (ServiceState.isCdma(dataRegType)) {
                if (isVoiceInService) {
                    // CDMA data should have the same state as voice
                    currentServiceState.setDataRoamingType(currentServiceState
                            .getVoiceRoamingType());
                } else {
                    // we can not decide CDMA data roaming type without voice
                    // set it as same as last time
                    currentServiceState.setDataRoamingType(ServiceState.ROAMING_TYPE_UNKNOWN);
                }
            } else {
                // take it as 3GPP roaming
                if (inSameCountry(currentServiceState.getDataOperatorNumeric())) {
                    currentServiceState.setDataRoamingType(ServiceState.ROAMING_TYPE_DOMESTIC);
                } else {
                    currentServiceState.setDataRoamingType(
                            ServiceState.ROAMING_TYPE_INTERNATIONAL);
                }
            }
        }
    }

    protected String getHomeOperatorNumeric() {
        String numeric = ((TelephonyManager) mPhone.getContext().
                getSystemService(Context.TELEPHONY_SERVICE)).
                getSimOperatorNumericForPhone(mPhoneBase.getPhoneId());
        if (TextUtils.isEmpty(numeric)) {
            numeric = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, "");
        }
        return numeric;
    }

    protected void setSignalStrengthDefaultValues() {
        mSignalStrength = new SignalStrength( false);
    }

    /**
     * A complete "service state" from our perspective is
     * composed of a handful of separate requests to the radio.
     *
     * We make all of these requests at once, but then abandon them
     * and start over again if the radio notifies us that some
     * event has changed
     */
    @Override
    public void pollState() {
        mPollingContext = new int[1];
        mPollingContext[0] = 0;

        log("pollState RadioState is " + mCi.getRadioState());
        switch (mCi.getRadioState()) {
        case RADIO_UNAVAILABLE:
            //M: added for [ALPS01802701]
            //mNewSS.setStateOutOfService();
            mNewSS.setStateOff();
            mNewCellLoc.setStateInvalid();
            setSignalStrengthDefaultValues();
            mGotCountryCode = false;

            pollStateDone();
            break;

        case RADIO_OFF:
            mNewSS.setStateOff();
            mNewCellLoc.setStateInvalid();
            setSignalStrengthDefaultValues();
            mGotCountryCode = false;

            if (ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN
                        != mSS.getRilDataRadioTechnology()) {
                pollStateDone();
            }
            break;

        default:
            // Issue all poll-related commands at once, then count
            // down the responses which are allowed to arrive
            // out-of-order.

            mPollingContext[0]++;
            // RIL_REQUEST_OPERATOR is necessary for CDMA
            mCi.getOperator(
                    obtainMessage(EVENT_POLL_STATE_OPERATOR_CDMA, mPollingContext));

            mPollingContext[0]++;
            // RIL_REQUEST_VOICE_REGISTRATION_STATE is necessary for CDMA
            mCi.getVoiceRegistrationState(
                    obtainMessage(EVENT_POLL_STATE_REGISTRATION_CDMA, mPollingContext));

            mPollingContext[0]++;
            // RIL_REQUEST_DATA_REGISTRATION_STATE
            mCi.getDataRegistrationState(obtainMessage(EVENT_POLL_STATE_GPRS,
                                        mPollingContext));
            break;
        }
    }

    protected void fixTimeZone(String isoCountryCode) {
        TimeZone zone = null;
        // If the offset is (0, false) and the time zone property
        // is set, use the time zone property rather than GMT.
        String zoneName = SystemProperties.get(TIMEZONE_PROPERTY);
        if (DBG) {
            log("fixTimeZone zoneName='" + zoneName +
                "' mZoneOffset=" + mZoneOffset + " mZoneDst=" + mZoneDst +
                " iso-cc='" + isoCountryCode +
                "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode));
        }
        if ((mZoneOffset == 0) && (mZoneDst == false) && (zoneName != null)
                && (zoneName.length() > 0)
                && (Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode) < 0)) {
            // For NITZ string without time zone,
            // need adjust time to reflect default time zone setting
            zone = TimeZone.getDefault();
            if (mNeedFixZone) {
                long ctm = System.currentTimeMillis();
                long tzOffset = zone.getOffset(ctm);
                if (DBG) {
                    log("fixTimeZone: tzOffset=" + tzOffset +
                            " ltod=" + TimeUtils.logTimeOfDay(ctm));
                }
                if (getAutoTime()) {
                    long adj = ctm - tzOffset;
                    if (DBG) log("fixTimeZone: adj ltod=" + TimeUtils.logTimeOfDay(adj));
                    setAndBroadcastNetworkSetTime(adj);
                } else {
                    // Adjust the saved NITZ time to account for tzOffset.
                    mSavedTime = mSavedTime - tzOffset;
                    if (DBG) log("fixTimeZone: adj mSavedTime=" + mSavedTime);
                }
            }
            if (DBG) log("fixTimeZone: using default TimeZone");
        } else if (isoCountryCode.equals("")) {
            // Country code not found. This is likely a test network.
            // Get a TimeZone based only on the NITZ parameters (best guess).
            zone = getNitzTimeZone(mZoneOffset, mZoneDst, mZoneTime);
            if (DBG) log("fixTimeZone: using NITZ TimeZone");
        } else {
            zone = TimeUtils.getTimeZone(mZoneOffset, mZoneDst, mZoneTime, isoCountryCode);
            if (DBG) log("fixTimeZone: using getTimeZone(off, dst, time, iso)");
        }

        mNeedFixZone = false;

        if (zone != null) {
            log("fixTimeZone: zone != null zone.getID=" + zone.getID());
            if (getAutoTimeZone()) {
                setAndBroadcastNetworkSetTimeZone(zone.getID());
            } else {
                log("fixTimeZone: skip changing zone as getAutoTimeZone was false");
            }
            saveNitzTimeZone(zone.getID());
        } else {
            log("fixTimeZone: zone == null, do nothing for zone");
        }
    }

    /**
     * Query the carrier configuration to determine if there are any network overrides
     * for roaming or not roaming for the current service state.
     */
    protected void updateRoamingState() {
        // Save the roaming state before carrier config possibly overrides it.
        mNewSS.setDataRoamingFromRegistration(mNewSS.getDataRoaming());

        ICarrierConfigLoader configLoader =
            (ICarrierConfigLoader) ServiceManager.getService(Context.CARRIER_CONFIG_SERVICE);
        if (configLoader != null) {
            try {
                PersistableBundle b = configLoader.getConfigForSubId(mPhone.getSubId());
                String systemId = Integer.toString(mNewSS.getSystemId());

                if (alwaysOnHomeNetwork(b)) {
                    log("updateRoamingState: carrier config override always on home network");
                    setRoamingOff();
                } else if (isNonRoamingInGsmNetwork(b, mNewSS.getOperatorNumeric())
                        || isNonRoamingInCdmaNetwork(b, systemId)) {
                    log("updateRoamingState: carrier config override set non-roaming:"
                            + mNewSS.getOperatorNumeric() + ", " + systemId);
                    setRoamingOff();
                } else if (isRoamingInGsmNetwork(b, mNewSS.getOperatorNumeric())
                        || isRoamingInCdmaNetwork(b, systemId)) {
                    log("updateRoamingState: carrier config override set roaming:"
                            + mNewSS.getOperatorNumeric() + ", " + systemId);
                    setRoamingOn();
                }
            } catch (RemoteException e) {
                loge("updateRoamingState: unable to access carrier config service");
            }
        } else {
            log("updateRoamingState: no carrier config service available");
        }

        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean(PROP_FORCE_ROAMING, false)) {
            mNewSS.setVoiceRoaming(true);
            mNewSS.setDataRoaming(true);
        }
    }

    private void setRoamingOn() {
        mNewSS.setVoiceRoaming(true);
        mNewSS.setDataRoaming(true);
        mNewSS.setCdmaEriIconIndex(EriInfo.ROAMING_INDICATOR_ON);
        mNewSS.setCdmaEriIconMode(EriInfo.ROAMING_ICON_MODE_NORMAL);
    }

    private void setRoamingOff() {
        mNewSS.setVoiceRoaming(false);
        mNewSS.setDataRoaming(false);
        mNewSS.setCdmaEriIconIndex(EriInfo.ROAMING_INDICATOR_OFF);
    }

    protected void pollStateDone() {
        updateRoamingState();

        useDataRegStateForDataOnlyDevices();
        resetServiceStateInIwlanMode();
        if (DBG) log("pollStateDone: cdma oldSS=[" + mSS + "] newSS=[" + mNewSS + "]");

        boolean hasRegistered =
            mSS.getVoiceRegState() != ServiceState.STATE_IN_SERVICE
            && mNewSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE;

        boolean hasDeregistered =
            mSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE
            && mNewSS.getVoiceRegState() != ServiceState.STATE_IN_SERVICE;

        boolean hasCdmaDataConnectionAttached =
            mSS.getDataRegState() != ServiceState.STATE_IN_SERVICE
            && mNewSS.getDataRegState() == ServiceState.STATE_IN_SERVICE;

        boolean hasCdmaDataConnectionDetached =
            mSS.getDataRegState() == ServiceState.STATE_IN_SERVICE
            && mNewSS.getDataRegState() != ServiceState.STATE_IN_SERVICE;

        boolean hasCdmaDataConnectionChanged =
                       mSS.getDataRegState() != mNewSS.getDataRegState();

        boolean hasRilVoiceRadioTechnologyChanged =
                mSS.getRilVoiceRadioTechnology() != mNewSS.getRilVoiceRadioTechnology();

        boolean hasRilDataRadioTechnologyChanged =
                mSS.getRilDataRadioTechnology() != mNewSS.getRilDataRadioTechnology();

        boolean hasChanged = !mNewSS.equals(mSS);

        boolean hasVoiceRoamingOn = !mSS.getVoiceRoaming() && mNewSS.getVoiceRoaming();

        boolean hasVoiceRoamingOff = mSS.getVoiceRoaming() && !mNewSS.getVoiceRoaming();

        boolean hasDataRoamingOn = !mSS.getDataRoaming() && mNewSS.getDataRoaming();

        boolean hasDataRoamingOff = mSS.getDataRoaming() && !mNewSS.getDataRoaming();

        boolean hasLocationChanged = !mNewCellLoc.equals(mCellLoc);

        resetServiceStateInIwlanMode();
        TelephonyManager tm =
                (TelephonyManager) mPhone.getContext().getSystemService(Context.TELEPHONY_SERVICE);

        /// M: c2k modify. @{
        boolean hasRegStateChanged = mSS.getRegState() != mNewSS.getRegState();
        log("pollStateDone: hasChanged=" + hasChanged + ", hasRegStateChanged=" + hasRegStateChanged
                + ", hasRegistered=" + hasRegistered + ", hasDeregistered=" + hasDeregistered
                + ", hasCdmaDataConnectionAttached=" + hasCdmaDataConnectionAttached
                + ", hasCdmaDataConnectionDetached=" + hasCdmaDataConnectionDetached
                + ", hasCdmaDataConnectionChanged=" + hasCdmaDataConnectionChanged
                + ", hasRilVoiceRadioTechnologyChanged=" + hasRilVoiceRadioTechnologyChanged
                + ", hasRilDataRadioTechnologyChanged=" + hasRilDataRadioTechnologyChanged
                + ", hasVoiceRoamingOn=" + hasVoiceRoamingOn
                + ", hasVoiceRoamingOff=" + hasVoiceRoamingOff
                + ", hasDataRoamingOn=" + hasDataRoamingOn
                + ", hasDataRoamingOff=" + hasDataRoamingOff
                + ", hasLocationChanged=" + hasLocationChanged
                );
        /// @}

        // Add an event log when connection state changes
        if (mSS.getVoiceRegState() != mNewSS.getVoiceRegState() ||
                mSS.getDataRegState() != mNewSS.getDataRegState()) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE,
                    mSS.getVoiceRegState(), mSS.getDataRegState(),
                    mNewSS.getVoiceRegState(), mNewSS.getDataRegState());
        }

        /// M: c2k modify. @{
        if (mNewSS.getState() == ServiceState.STATE_IN_SERVICE) {
            mInService = true;
        } else {
            mInService = false;
        }
        log("pollStateDone: mInService = " + mInService);
        /// @}

        ServiceState tss;
        tss = mSS;
        mSS = mNewSS;
        mNewSS = tss;
        // clean slate for next time
        mNewSS.setStateOutOfService();

        CdmaCellLocation tcl = mCellLoc;
        mCellLoc = mNewCellLoc;
        mNewCellLoc = tcl;

        if (hasRilVoiceRadioTechnologyChanged) {
            updatePhoneObject();
        }

        if (hasRilDataRadioTechnologyChanged) {
            tm.setDataNetworkTypeForPhone(mPhone.getPhoneId(), mSS.getRilDataRadioTechnology());
            if (ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN
                        == mSS.getRilDataRadioTechnology()) {
                log("pollStateDone: IWLAN enabled");
            }
            /// M: c2k modify. @{
            // query network time if Network Type is changed to a valid state
            if (mNewNetworkType != 0) {
                queryCurrentNitzTime();
            }
            /// @}
            mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE,
                    ServiceState.rilRadioTechnologyToString(mSS.getRilDataRadioTechnology()));
        }

        if (hasRegistered) {
            mNetworkAttachedRegistrants.notifyRegistrants();
        }

        if (hasChanged) {
            if ((mCi.getRadioState().isOn()) && (!mIsSubscriptionFromRuim)) {
                log("pollStateDone isSubscriptionFromRuim = " + mIsSubscriptionFromRuim);
                String eriText;
                // Now the CDMAPhone sees the new ServiceState so it can get the new ERI text
                if (mSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
                    eriText = mPhone.getCdmaEriText();
                } else {
                    // Note that ServiceState.STATE_OUT_OF_SERVICE is valid used for
                    // mRegistrationState 0,2,3 and 4
                    eriText = mPhone.getContext().getText(
                            com.android.internal.R.string.roamingTextSearching).toString();
                }
                mSS.setOperatorAlphaLong(eriText);
            }

            String operatorNumeric;

            tm.setNetworkOperatorNameForPhone(mPhone.getPhoneId(), mSS.getOperatorAlphaLong());

            String prevOperatorNumeric = tm.getNetworkOperatorForPhone(mPhone.getPhoneId());
            operatorNumeric = mSS.getOperatorNumeric();

            // try to fix the invalid Operator Numeric
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                int sid = mSS.getSystemId();
                operatorNumeric = fixUnknownMcc(operatorNumeric, sid);
            }

            tm.setNetworkOperatorNumericForPhone(mPhone.getPhoneId(), operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric,
                    prevOperatorNumeric, mPhone.getContext());

            if (isInvalidOperatorNumeric(operatorNumeric)) {
                if (DBG) log("operatorNumeric "+ operatorNumeric +"is invalid");
                tm.setNetworkCountryIsoForPhone(mPhone.getPhoneId(), "");
                mGotCountryCode = false;
            } else {
                String isoCountryCode = "";
                String mcc = operatorNumeric.substring(0, 3);
                try{
                    isoCountryCode = MccTable.countryCodeForMcc(Integer.parseInt(
                            operatorNumeric.substring(0,3)));
                } catch ( NumberFormatException ex){
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                } catch ( StringIndexOutOfBoundsException ex) {
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                }

                tm.setNetworkCountryIsoForPhone(mPhone.getPhoneId(), isoCountryCode);
                mGotCountryCode = true;

                setOperatorIdd(operatorNumeric);

                if (shouldFixTimeZoneNow(mPhone, operatorNumeric, prevOperatorNumeric,
                        mNeedFixZone)) {
                    fixTimeZone(isoCountryCode);
                }
            }

            tm.setNetworkRoamingForPhone(mPhone.getPhoneId(),
                    (mSS.getVoiceRoaming() || mSS.getDataRoaming()));

            updateSpnDisplay();
            // set roaming type
            setRoamingType(mSS);
            log("Broadcasting ServiceState : " + mSS);

            /// M: c2k modify. @{
            if (hasRegStateChanged) {
                if (mSS.getRegState() == ServiceState.REGISTRATION_STATE_UNKNOWN
                    && (1 == Settings.System.getInt(mPhone.getContext().getContentResolver(),
                            Settings.System.AIRPLANE_MODE_ON, -1))) {
                    int serviceState = mPhone.getServiceState().getState();
                    if (serviceState != ServiceState.STATE_POWER_OFF) {
                        mSS.setStateOff();
                    }
                }
            }
            /// @}

            mPhone.notifyServiceStateChanged(mSS);
        }

        if (hasCdmaDataConnectionAttached) {
            mAttachedRegistrants.notifyRegistrants();
        }

        if (hasCdmaDataConnectionDetached) {
            mDetachedRegistrants.notifyRegistrants();
        }

        if (hasCdmaDataConnectionChanged || hasRilDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN
                        == mSS.getRilDataRadioTechnology()) {
                mPhone.notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
            } else {
                mPhone.notifyDataConnection(null);
            }
        }

        if (hasVoiceRoamingOn) {
            mVoiceRoamingOnRegistrants.notifyRegistrants();
        }

        if (hasVoiceRoamingOff) {
            mVoiceRoamingOffRegistrants.notifyRegistrants();
        }

        if (hasDataRoamingOn) {
            mDataRoamingOnRegistrants.notifyRegistrants();
        }

        if (hasDataRoamingOff) {
            mDataRoamingOffRegistrants.notifyRegistrants();
        }

        if (hasLocationChanged) {
            mPhone.notifyLocationChanged();
        }
        // TODO: Add CdmaCellIdenity updating, see CdmaLteServiceStateTracker.
    }

    protected boolean isInvalidOperatorNumeric(String operatorNumeric) {
        return operatorNumeric == null || operatorNumeric.length() < 5 ||
                    operatorNumeric.startsWith(INVALID_MCC);
    }

    protected String fixUnknownMcc(String operatorNumeric, int sid) {
        if (sid <= 0) {
            // no cdma information is available, do nothing
            return operatorNumeric;
        }

        // resolve the mcc from sid;
        // if mSavedTimeZone is null, TimeZone would get the default timeZone,
        // and the fixTimeZone couldn't help, because it depends on operator Numeric;
        // if the sid is conflict and timezone is unavailable, the mcc may be not right.
        boolean isNitzTimeZone = false;
        int timeZone = 0;
        TimeZone tzone = null;
        if (mSavedTimeZone != null) {
             timeZone =
                     TimeZone.getTimeZone(mSavedTimeZone).getRawOffset()/MS_PER_HOUR;
             isNitzTimeZone = true;
        } else {
             tzone = getNitzTimeZone(mZoneOffset, mZoneDst, mZoneTime);
             if (tzone != null)
                     timeZone = tzone.getRawOffset()/MS_PER_HOUR;
        }

        int mcc = mHbpcdUtils.getMcc(sid,
                timeZone, (mZoneDst ? 1 : 0), isNitzTimeZone);
        if (mcc > 0) {
            operatorNumeric = Integer.toString(mcc) + DEFAULT_MNC;
        }
        return operatorNumeric;
    }

    protected void setOperatorIdd(String operatorNumeric) {
        // Retrieve the current country information
        // with the MCC got from opeatorNumeric.
        /// M: Use try catch to avoid Integer pars exception @{
        String idd = "";
        try {
            idd = mHbpcdUtils.getIddByMcc(
                    Integer.parseInt(operatorNumeric.substring(0, 3)));
        } catch (NumberFormatException ex) {
            loge("setOperatorIdd: idd error" + ex);
        } catch (StringIndexOutOfBoundsException ex) {
            loge("setOperatorIdd: idd error" + ex);
        }
        // @}
        if (idd != null && !idd.isEmpty()) {
            mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_IDP_STRING,
                     idd);
        } else {
            // use default "+", since we don't know the current IDP
            mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_IDP_STRING, "+");
        }
    }

    /**
     * Returns a TimeZone object based only on parameters from the NITZ string.
     */
    private TimeZone getNitzTimeZone(int offset, boolean dst, long when) {
        TimeZone guess = findTimeZone(offset, dst, when);
        if (guess == null) {
            // Couldn't find a proper timezone.  Perhaps the DST data is wrong.
            guess = findTimeZone(offset, !dst, when);
        }
        if (DBG) log("getNitzTimeZone returning " + (guess == null ? guess : guess.getID()));
        return guess;
    }

    private TimeZone findTimeZone(int offset, boolean dst, long when) {
        int rawOffset = offset;
        if (dst) {
            rawOffset -= MS_PER_HOUR;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        TimeZone guess = null;
        Date d = new Date(when);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(when) == offset &&
                    tz.inDaylightTime(d) == dst) {
                guess = tz;
                break;
            }
        }

        return guess;
    }

    /**
     * TODO: This code is exactly the same as in GsmServiceStateTracker
     * and has a TODO to not poll signal strength if screen is off.
     * This code should probably be hoisted to the base class so
     * the fix, when added, works for both.
     */
    /// M: Modify the policy for svlte @{
    protected void
    queueNextSignalStrengthPoll() {
        if (mDontPollSignalStrength) {
            // The radio is telling us about signal strength changes
            // we don't have to ask it
            return;
        }

        Message msg;

        msg = obtainMessage();
        msg.what = EVENT_POLL_SIGNAL_STRENGTH;

        // TODO Don't poll signal strength if screen is off
        sendMessageDelayed(msg, POLL_PERIOD_MILLIS);
    }
    /// @}
    /// M: c2k modify, poll NitzTime. @{
    protected void queryCurrentNitzTime() {
        Message msg;

        msg = obtainMessage();
        msg.what = EVENT_QUERY_NITZ_TIME;

        sendMessage(msg);
    }
    /// @}

    protected int radioTechnologyToDataServiceState(int code) {
        int retVal = ServiceState.STATE_OUT_OF_SERVICE;
        switch(code) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
            break;
        case 6: // RADIO_TECHNOLOGY_1xRTT
        case 7: // RADIO_TECHNOLOGY_EVDO_0
        case 8: // RADIO_TECHNOLOGY_EVDO_A
        case 12: // RADIO_TECHNOLOGY_EVDO_B
        case 13: // RADIO_TECHNOLOGY_EHRPD
            retVal = ServiceState.STATE_IN_SERVICE;
            break;
        default:
            loge("radioTechnologyToDataServiceState: Wrong radioTechnology code.");
        break;
        }
        return(retVal);
    }

    /** code is registration state 0-5 from TS 27.007 7.2 */
    protected int
    regCodeToServiceState(int code) {
        switch (code) {
        case 0: // Not searching and not registered
            return ServiceState.STATE_OUT_OF_SERVICE;
        case 1:
            return ServiceState.STATE_IN_SERVICE;
        case 2: // 2 is "searching", fall through
        case 3: // 3 is "registration denied", fall through
        case 4: // 4 is "unknown", not valid in current baseband
            return ServiceState.STATE_OUT_OF_SERVICE;
        case 5:// 5 is "Registered, roaming"
            return ServiceState.STATE_IN_SERVICE;

        default:
            loge("regCodeToServiceState: unexpected service state " + code);
        return ServiceState.STATE_OUT_OF_SERVICE;
        }
    }

    @Override
    public int getCurrentDataConnectionState() {
        return mSS.getDataRegState();
    }

    /**
     * code is registration state 0-5 from TS 27.007 7.2
     * returns true if registered roam, false otherwise
     */
    protected boolean
    regCodeIsRoaming (int code) {
        ///M: c2k modify. @{

        // 5 is  "in service -- roam"
        // return 5 == code;

        /// M: Customize for swip via code.
        String iccMcc = SystemProperties.get(mPlusCodeUtils.PROPERTY_ICC_CDMA_OPERATOR_MCC);
        String numeric = mNewSS.getOperatorNumeric();
        String mcc = null;
        // get mcc by sid and ltmoff, if operator numeric is (1111)
        if (numeric != null && numeric.startsWith("2134") && numeric.length() == 7) {
            /// M: Customize for swip via code.
            String tempStr = mPlusCodeUtils.checkMccBySidLtmOff(numeric);
            if (DBG) {
                log("regCodeIsRoaming: mccmnc =" + tempStr);
            }
            if (!tempStr.equals("0")) {
                mcc = tempStr.substring(0, 3);
            }
        }

        if (mcc == null) {
            /// M: Customize for swip via code.
            mcc = SystemProperties.get(mPlusCodeUtils.PROPERTY_OPERATOR_MCC);
        }
        if (DBG) {
            log("mcc=" + mcc + ", iccmcc=" + iccMcc);
        }

        boolean mccInValid = (mcc == null) || TextUtils.isEmpty(mcc)
                || mcc.equals("000") || mcc.equals("N/A");
        boolean iccMccInValid = (iccMcc == null) || TextUtils.isEmpty(iccMcc)
                || iccMcc.equals("000") || iccMcc.equals("N/A");
        if (DBG) {
            log("mccInValid=" + mccInValid + ", iccMccInValid=" + iccMccInValid);
        }

        // return roaming when service state is in service and mcc is different
        // between icccard and netwrok.
        return (regCodeToServiceState(code) == ServiceState.STATE_IN_SERVICE)
                && (!mccInValid) && (!iccMccInValid) && (!mcc.equals(iccMcc));

        /// @}
    }

    /**
     * Determine whether a roaming indicator is in the carrier-specified list of ERIs for
     * home system
     *
     * @param roamInd roaming indicator in String
     * @return true if the roamInd is in the carrier-specified list of ERIs for home network
     */
    private boolean isRoamIndForHomeSystem(String roamInd) {
        // retrieve the carrier-specified list of ERIs for home system
        String[] homeRoamIndicators = mPhone.getContext().getResources()
                .getStringArray(com.android.internal.R.array.config_cdma_home_system);

        if (homeRoamIndicators != null) {
            // searches through the comma-separated list for a match,
            // return true if one is found.
            for (String homeRoamInd : homeRoamIndicators) {
                if (homeRoamInd.equals(roamInd)) {
                    return true;
                }
            }
            // no matches found against the list!
            return false;
        }

        // no system property found for the roaming indicators for home system
        return false;
    }

    /**
     * Set roaming state when cdmaRoaming is true and ons is different from spn
     * @param cdmaRoaming TS 27.007 7.2 CREG registered roaming
     * @param s ServiceState hold current ons
     * @return true for roaming state set
     */
    private
    boolean isRoamingBetweenOperators(boolean cdmaRoaming, ServiceState s) {
        String spn = ((TelephonyManager) mPhone.getContext().
                getSystemService(Context.TELEPHONY_SERVICE)).
                getSimOperatorNameForPhone(mPhoneBase.getPhoneId());

        // NOTE: in case of RUIM we should completely ignore the ERI data file and
        // mOperatorAlphaLong is set from RIL_REQUEST_OPERATOR response 0 (alpha ONS)
        String onsl = s.getVoiceOperatorAlphaLong();
        String onss = s.getVoiceOperatorAlphaShort();

        boolean equalsOnsl = onsl != null && spn.equals(onsl);
        boolean equalsOnss = onss != null && spn.equals(onss);

        return cdmaRoaming && !(equalsOnsl || equalsOnss);
    }


    /**
     * nitzReceiveTime is time_t that the NITZ time was posted
     */

    private
    void setTimeFromNITZString (String nitz, long nitzReceiveTime)
    {
        // "yy/mm/dd,hh:mm:ss(+/-)tz"
        // tz is in number of quarter-hours

        long start = SystemClock.elapsedRealtime();
        if (DBG) {
            log("NITZ: " + nitz + "," + nitzReceiveTime +
                        " start=" + start + " delay=" + (start - nitzReceiveTime));
        }

        /// M: c2k modify. @{
        if (nitz.length() <= 0) {
            return;
        }
        /// @}

        try {
            /* NITZ time (hour:min:sec) will be in UTC but it supplies the timezone
             * offset as well (which we won't worry about until later) */
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

            c.clear();
            c.set(Calendar.DST_OFFSET, 0);

            String[] nitzSubs = nitz.split("[/:,+-]");

            /// M: c2k modify. @{
            // int year = 2000 + Integer.parseInt(nitzSubs[0]);
            int year = Integer.parseInt(nitzSubs[0]);
            /// @}
            if (year > MAX_NITZ_YEAR) {
                if (DBG) loge("NITZ year: " + year + " exceeds limit, skip NITZ time update");
                return;
            }
            c.set(Calendar.YEAR, year);

            // month is 0 based!
            int month = Integer.parseInt(nitzSubs[1]) - 1;
            c.set(Calendar.MONTH, month);

            int date = Integer.parseInt(nitzSubs[2]);
            c.set(Calendar.DATE, date);

            int hour = Integer.parseInt(nitzSubs[3]);
            c.set(Calendar.HOUR, hour);

            int minute = Integer.parseInt(nitzSubs[4]);
            c.set(Calendar.MINUTE, minute);

            int second = Integer.parseInt(nitzSubs[5]);
            c.set(Calendar.SECOND, second);

            log("NITZ: year = " + year + ", month = " + month + ", date = " + date
                    + ", hour = " + hour + ", minute = " + minute + ", second = " + second);

            boolean sign = (nitz.indexOf('-') == -1);

            int tzOffset = Integer.parseInt(nitzSubs[6]);

            /// M: c2k modify. @{
            int ltmoffset = (sign ? 1 : -1) * tzOffset;
            if (DBG) {
                log("setSystemProperty(cdma.operator.ltmoffset) = " + ltmoffset);
            }
            /// M: Customize for swip via code.
            SystemProperties.set(mPlusCodeUtils.PROPERTY_TIME_LTMOFFSET,
                    Integer.toString(ltmoffset));
            /// @}

            int dst = (nitzSubs.length >= 8 ) ? Integer.parseInt(nitzSubs[7])
                                              : 0;

            log("NITZ: tzOffset = " + tzOffset + ", dst = "  + dst);

            // The zone offset received from NITZ is for current local time,
            // so DST correction is already applied.  Don't add it again.
            //
            // tzOffset += dst * 4;
            //
            // We could unapply it if we wanted the raw offset.

            tzOffset = (sign ? 1 : -1) * tzOffset * 15 * 60 * 1000;

            TimeZone    zone = null;

            // As a special extension, the Android emulator appends the name of
            // the host computer's timezone to the nitz string. this is zoneinfo
            // timezone name of the form Area!Location or Area!Location!SubLocation
            // so we need to convert the ! into /
            if (nitzSubs.length >= 9) {
                String  tzname = nitzSubs[8].replace('!','/');
                zone = TimeZone.getTimeZone( tzname );
            }

            String iso = ((TelephonyManager) mPhone.getContext().
                    getSystemService(Context.TELEPHONY_SERVICE)).
                    getNetworkCountryIsoForPhone(mPhone.getPhoneId());

            if (zone == null) {
                if (mGotCountryCode) {
                    if (iso != null && iso.length() > 0) {
                        log("[NITZ] setTimeFromNITZString, TimeUtils.getTimeZone");
                        zone = TimeUtils.getTimeZone(tzOffset, dst != 0,
                                c.getTimeInMillis(),
                                iso);
                    } else {    //[ALPS02011091]
                        // We don't have a valid iso country code.  This is
                        // most likely because we're on a test network that's
                        // using a bogus MCC (eg, "001"), so get a TimeZone
                        // based only on the NITZ parameters.
                        log("[NITZ] setTimeFromNITZString, getNitzTimeZone");
                        zone = getNitzTimeZone(tzOffset, (dst != 0), c.getTimeInMillis());
                    }
                }
            }

            if ((zone == null) || (mZoneOffset != tzOffset) || (mZoneDst != (dst != 0))){
                // We got the time before the country or the zone has changed
                // so we don't know how to identify the DST rules yet.  Save
                // the information and hope to fix it up later.

                mNeedFixZone = true;
                mZoneOffset  = tzOffset;
                mZoneDst     = dst != 0;
                mZoneTime    = c.getTimeInMillis();
            }
            if (DBG) {
                log("NITZ: tzOffset=" + tzOffset + " dst=" + dst + " zone=" +
                        (zone!=null ? zone.getID() : "NULL") +
                        " iso=" + iso + " mGotCountryCode=" + mGotCountryCode +
                        " mNeedFixZone=" + mNeedFixZone);
            }

            /// M: c2k modify. @{
            /*if (zone != null) {
                if (getAutoTimeZone()) {
                    setAndBroadcastNetworkSetTimeZone(zone.getID());
                }
                saveNitzTimeZone(zone.getID());
            }*/
            /// @}

            String ignore = SystemProperties.get("gsm.ignore-nitz");
            if (ignore != null && ignore.equals("yes")) {
                if (DBG) log("NITZ: Not setting clock because gsm.ignore-nitz is set");
                return;
            }

            try {
                mWakeLock.acquire();

                /**
                 * Correct the NITZ time by how long its taken to get here.
                 */
                long millisSinceNitzReceived
                        = SystemClock.elapsedRealtime() - nitzReceiveTime;

                if (millisSinceNitzReceived < 0) {
                    // Sanity check: something is wrong
                    if (DBG) {
                        log("NITZ: not setting time, clock has rolled "
                                        + "backwards since NITZ time was received, "
                                        + nitz);
                    }
                    return;
                }

                if (millisSinceNitzReceived > Integer.MAX_VALUE) {
                    // If the time is this far off, something is wrong > 24 days!
                    if (DBG) {
                        log("NITZ: not setting time, processing has taken "
                                    + (millisSinceNitzReceived / (1000 * 60 * 60 * 24))
                                    + " days");
                    }
                    return;
                }

                // Note: with range checks above, cast to int is safe
                c.add(Calendar.MILLISECOND, (int)millisSinceNitzReceived);
                log("NITZ:millisSinceNitzReceived = " + millisSinceNitzReceived);

                /// M: c2k modify. @{
                // if (getAutoTime()) {
                if (getAutoTime() && mInService) {
                /// @}
                    /**
                     * Update system time automatically
                     */
                    long gained = c.getTimeInMillis() - System.currentTimeMillis();
                    long timeSinceLastUpdate = SystemClock.elapsedRealtime() - mSavedAtTime;
                    int nitzUpdateSpacing = Settings.Global.getInt(mCr,
                            Settings.Global.NITZ_UPDATE_SPACING, mNitzUpdateSpacing);
                    int nitzUpdateDiff = Settings.Global.getInt(mCr,
                            Settings.Global.NITZ_UPDATE_DIFF, mNitzUpdateDiff);

                    /// M: c2k modify, use updateZone with the check condition when occur
                    /// change time zone only. @{
                    boolean updateZone = false;
                    log("[NITZ] setTimeFromNITZString, OldTimeZone = " + mSavedTimeZone);
                    log("[NITZ] setTimeFromNITZString, NewTimeZone = "
                            + (zone != null ? zone.getID() : "NULL"));
                    if (null != zone) {
                        String newZoneId = zone.getID();
                        updateZone = TextUtils.isEmpty(mSavedTimeZone)
                            || !mSavedTimeZone.equals(newZoneId);
                    }
                    log("[NITZ] setTimeFromNITZString, updateZone = " + updateZone);
                    /// @}

                    if ((mSavedAtTime == 0) || (timeSinceLastUpdate > nitzUpdateSpacing)
                            /// M: c2k modify. @{
                            // || (Math.abs(gained) > nitzUpdateDiff)) {
                            || (Math.abs(gained) > nitzUpdateDiff) || mAutoTimeChanged
                            || updateZone) {
                            /// @}
                        if (DBG) {
                            log("NITZ: Auto updating time of day to " + c.getTime()
                                + " NITZ receive delay=" + millisSinceNitzReceived
                                + "ms gained=" + gained + "ms from " + nitz);
                        }

                        /// M: c2k modify. @{
                        if (zone != null) {
                            if (getAutoTimeZone()) {
                                setAndBroadcastNetworkSetTimeZone(zone.getID());
                            }
                            saveNitzTimeZone(zone.getID());
                        }
                        log("NITZ:before ===== modify zone c.getTimeInMillis() = " +
                                c.getTimeInMillis());
                        c.add(Calendar.MILLISECOND, (int) (-1) * tzOffset);
                        log("NITZ: (int)(-1)*tzOffset = " + (int) (-1) * tzOffset + ", dst = " +
                                dst);
                        /// @}

                        setAndBroadcastNetworkSetTime(c.getTimeInMillis());

                        /// M: c2k modify. @{
                        log("NITZ:after ====== modify zone c.getTimeInMillis() = " +
                                c.getTimeInMillis());
                        /// @}
                    } else {
                        if (DBG) {
                            log("NITZ: ignore, a previous update was "
                                + timeSinceLastUpdate + "ms ago and gained=" + gained + "ms");
                        }
                        return;
                    }
                }

                /**
                 * Update properties and save the time we did the update
                 */
                if (DBG) log("NITZ: update nitz time property");
                SystemProperties.set("gsm.nitz.time", String.valueOf(c.getTimeInMillis()));
                mSavedTime = c.getTimeInMillis();
                mSavedAtTime = SystemClock.elapsedRealtime();
            } finally {
                long end = SystemClock.elapsedRealtime();
                if (DBG) log("NITZ: end=" + end + " dur=" + (end - start));
                mWakeLock.release();
                /// M: c2k modify. @{
                mAutoTimeChanged = false;
                /// @}
            }
        } catch (RuntimeException ex) {
            loge("NITZ: Parsing NITZ time " + nitz + " ex=" + ex);
        }
    }

    private boolean getAutoTime() {
        try {
            return Settings.Global.getInt(mCr, Settings.Global.AUTO_TIME) > 0;
        } catch (SettingNotFoundException snfe) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Settings.Global.getInt(mCr, Settings.Global.AUTO_TIME_ZONE) > 0;
        } catch (SettingNotFoundException snfe) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        mSavedTimeZone = zoneId;
    }

    /**
     * Set the timezone and send out a sticky broadcast so the system can
     * determine if the timezone was set by the carrier.
     *
     * @param zoneId timezone set by carrier
     */
    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        if (DBG) log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        AlarmManager alarm =
            (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
        alarm.setTimeZone(zoneId);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time-zone", zoneId);
        mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /**
     * Set the time and Send out a sticky broadcast so the system can determine
     * if the time was set by the carrier.
     *
     * @param time time set by network
     */
    private void setAndBroadcastNetworkSetTime(long time) {
        if (DBG) log("setAndBroadcastNetworkSetTime: time=" + time + "ms");
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIME);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time", time);
        mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void revertToNitzTime() {
        if (Settings.Global.getInt(mCr, Settings.Global.AUTO_TIME, 0) == 0) {
            return;
        }
        if (DBG) {
            log("revertToNitzTime: mSavedTime=" + mSavedTime + " mSavedAtTime=" + mSavedAtTime);
        }
        if (mSavedTime != 0 && mSavedAtTime != 0) {
            setAndBroadcastNetworkSetTime(mSavedTime
                    + (SystemClock.elapsedRealtime() - mSavedAtTime));
        }
    }

    private void revertToNitzTimeZone() {
        if (Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                Settings.Global.AUTO_TIME_ZONE, 0) == 0) {
            return;
        }
        if (DBG) log("revertToNitzTimeZone: tz='" + mSavedTimeZone);
        if (mSavedTimeZone != null) {
            setAndBroadcastNetworkSetTimeZone(mSavedTimeZone);
        }
    }

    protected boolean isSidsAllZeros() {
        if (mHomeSystemId != null) {
            for (int i=0; i < mHomeSystemId.length; i++) {
                if (mHomeSystemId[i] != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check whether a specified system ID that matches one of the home system IDs.
     */
    private boolean isHomeSid(int sid) {
        if (mHomeSystemId != null) {
            for (int i=0; i < mHomeSystemId.length; i++) {
                if (sid == mHomeSystemId[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return true if phone is camping on a technology
     * that could support voice and data simultaneously.
     */
    @Override
    public boolean isConcurrentVoiceAndDataAllowed() {
        // Note: it needs to be confirmed which CDMA network types
        // can support voice and data calls concurrently.
        // For the time-being, the return value will be false.
        return false;
    }

    public String getMdnNumber() {
        return mMdn;
    }

    /// M: c2k modify. @{
    /**
     * Set MDN number.
     * @param mdn the MDN value.
     * @param onComplete the responding message.
     */
    public void setMdnNumber(String mdn, Message onComplete) {
        mCi.setMdnNumber(mdn, obtainMessage(EVENT_SET_MDN_DONE, onComplete));
    }
    /// @}

    public String getCdmaMin() {
         return mMin;
    }

    /** Returns null if NV is not yet ready */
    public String getPrlVersion() {
        if (DBG) log("getPrlVersion: prl=" + mPrlVersion);
        return mPrlVersion;
    }

    /// M: c2k modify. @{

    /**
     * @return the SID value, Returns null until receive EVENT_POLL_STATE_REGISTRATION_CDMA.
     */
    public String getSid() {
        if (DBG) {
            log("getSid: mSid=" + mSid);
        }
        return mSid;
    }

    /**
     * @return the NID value.
     */
    public String getNid() {
        if (DBG) {
            log("getNid: mNid=" + mNid);
        }
        return mNid;
    }

    /// @}

    /**
     * Returns IMSI as MCC + MNC + MIN
     */
    String getImsi() {
        // TODO: When RUIM is enabled, IMSI will come from RUIM not build-time props.
        String operatorNumeric = ((TelephonyManager) mPhone.getContext().
                getSystemService(Context.TELEPHONY_SERVICE)).
                getSimOperatorNumericForPhone(mPhoneBase.getPhoneId());

        if (!TextUtils.isEmpty(operatorNumeric) && getCdmaMin() != null) {
            return (operatorNumeric + getCdmaMin());
        } else {
            return null;
        }
    }

    /**
     * Check if subscription data has been assigned to mMin
     *
     * return true if MIN info is ready; false otherwise.
     */
    public boolean isMinInfoReady() {
        return mIsMinInfoReady;
    }

    /**
     * Returns OTASP_UNKNOWN, OTASP_NEEDED or OTASP_NOT_NEEDED
     */
    int getOtasp() {
        int provisioningState;
        // for ruim, min is null means require otasp.
        if (mIsSubscriptionFromRuim && mMin == null) {
            return OTASP_NEEDED;
        }
        if (mMin == null || (mMin.length() < 6)) {
            if (DBG) log("getOtasp: bad mMin='" + mMin + "'");
            provisioningState = OTASP_UNKNOWN;
        } else {
            if ((mMin.equals(UNACTIVATED_MIN_VALUE)
                    || mMin.substring(0,6).equals(UNACTIVATED_MIN2_VALUE))
                    || SystemProperties.getBoolean("test_cdma_setup", false)) {
                provisioningState = OTASP_NEEDED;
            } else {
                provisioningState = OTASP_NOT_NEEDED;
            }
        }
        if (DBG) log("getOtasp: state=" + provisioningState);
        return provisioningState;
    }

    @Override
    protected void hangupAndPowerOff() {
        // hang up all active voice calls
        mPhone.mCT.mRingingCall.hangupIfAlive();
        mPhone.mCT.mBackgroundCall.hangupIfAlive();
        mPhone.mCT.mForegroundCall.hangupIfAlive();
        configAndSetRadioPower(false);
    }

    protected void parseSidNid (String sidStr, String nidStr) {
        if (sidStr != null) {
            String[] sid = sidStr.split(",");
            mHomeSystemId = new int[sid.length];
            for (int i = 0; i < sid.length; i++) {
                try {
                    mHomeSystemId[i] = Integer.parseInt(sid[i]);
                } catch (NumberFormatException ex) {
                    loge("error parsing system id: " + ex);
                }
            }
        }
        if (DBG) log("CDMA_SUBSCRIPTION: SID=" + sidStr);

        if (nidStr != null) {
            String[] nid = nidStr.split(",");
            mHomeNetworkId = new int[nid.length];
            for (int i = 0; i < nid.length; i++) {
                try {
                    mHomeNetworkId[i] = Integer.parseInt(nid[i]);
                } catch (NumberFormatException ex) {
                    loge("CDMA_SUBSCRIPTION: error parsing network id: " + ex);
                }
            }
        }
        if (DBG) log("CDMA_SUBSCRIPTION: NID=" + nidStr);
    }

    protected void updateOtaspState() {
        int otaspMode = getOtasp();
        int oldOtaspMode = mCurrentOtaspMode;
        mCurrentOtaspMode = otaspMode;

        // Notify apps subscription info is ready
        if (mCdmaForSubscriptionInfoReadyRegistrants != null) {
            if (DBG) log("CDMA_SUBSCRIPTION: call notifyRegistrants()");
            mCdmaForSubscriptionInfoReadyRegistrants.notifyRegistrants();
        }
        if (oldOtaspMode != mCurrentOtaspMode) {
            if (DBG) {
                log("CDMA_SUBSCRIPTION: call notifyOtaspChanged old otaspMode=" +
                    oldOtaspMode + " new otaspMode=" + mCurrentOtaspMode);
            }
            mPhone.notifyOtaspChanged(mCurrentOtaspMode);
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
            return  mUiccController.getUiccCardApplication(mPhone.getPhoneId(),
                    UiccController.APP_FAM_3GPP2);
    }

    @Override
    protected void onUpdateIccAvailability() {
        if (mUiccController == null ) {
            return;
        }

        UiccCardApplication newUiccApplication = getUiccCardApplication();

        /// M: c2k modify, show EccButton when PIN and PUK status. @{
        if (newUiccApplication != null) {
            AppState appState = newUiccApplication.getState();
            log("onUpdateIccAvailability appstate = "
                + appState + ", mNetworkExsit = " + mNetworkExsit);
            if ((appState == AppState.APPSTATE_PIN || appState == AppState.APPSTATE_PUK)
                 && mNetworkExsit) {
                log("onUpdateIccAvailability mEmergencyOnly true");
                mEmergencyOnly = true;
            } else {
                log("onUpdateIccAvailability mEmergencyOnly false");
                mEmergencyOnly = false;
            }
        }
        /// @}

        if (mUiccApplcation != newUiccApplication) {
            if (mUiccApplcation != null) {
                log("Removing stale icc objects.");
                mUiccApplcation.unregisterForReady(this);
                if (mIccRecords != null) {
                    mIccRecords.unregisterForRecordsLoaded(this);
                }
                mIccRecords = null;
                mUiccApplcation = null;
            }
            if (newUiccApplication != null) {
                log("New card found");
                mUiccApplcation = newUiccApplication;
                mIccRecords = mUiccApplcation.getIccRecords();
                /// M: c2k modify. @{
                // delete by yangli,because can't get isSubscriptionFromRuim value from
                // cdmaSubscription. so, we have to delete it ,else we can't get ruim_ready event.
                // if (mIsSubscriptionFromRuim) {
                    mUiccApplcation.registerForReady(this, EVENT_RUIM_READY, null);
                    if (mIccRecords != null) {
                        mIccRecords.registerForRecordsLoaded(this, EVENT_RUIM_RECORDS_LOADED, null);
                    }
                // }
                /// @}
            /// M: c2k modify. @{
            } else if (newUiccApplication != null) {
                log("Uicc application is same");
                if (mIccRecords != newUiccApplication.getIccRecords()) {
                    log("But iccrecords is different, update iccrecords");
                    if (mIccRecords != null) {
                        mIccRecords.unregisterForRecordsLoaded(this);
                        mIccRecords = null;
                    }
                    if (newUiccApplication.getIccRecords() != null) {
                        mIccRecords = newUiccApplication.getIccRecords();
                        mIccRecords.registerForRecordsLoaded(this, EVENT_RUIM_RECORDS_LOADED, null);
                    }
                }
            }
            /// @}
        }
    }

    @Override
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[CdmaSST] " + s);
    }

    @Override
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[CdmaSST] " + s);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CdmaServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.flush();
        pw.println(" mPhone=" + mPhone);
        pw.println(" mSS=" + mSS);
        pw.println(" mNewSS=" + mNewSS);
        pw.println(" mCellLoc=" + mCellLoc);
        pw.println(" mNewCellLoc=" + mNewCellLoc);
        pw.println(" mCurrentOtaspMode=" + mCurrentOtaspMode);
        pw.println(" mRoamingIndicator=" + mRoamingIndicator);
        pw.println(" mIsInPrl=" + mIsInPrl);
        pw.println(" mDefaultRoamingIndicator=" + mDefaultRoamingIndicator);
        pw.println(" mRegistrationState=" + mRegistrationState);
        pw.println(" mNeedFixZone=" + mNeedFixZone);
        pw.flush();
        pw.println(" mZoneOffset=" + mZoneOffset);
        pw.println(" mZoneDst=" + mZoneDst);
        pw.println(" mZoneTime=" + mZoneTime);
        pw.println(" mGotCountryCode=" + mGotCountryCode);
        pw.println(" mSavedTimeZone=" + mSavedTimeZone);
        pw.println(" mSavedTime=" + mSavedTime);
        pw.println(" mSavedAtTime=" + mSavedAtTime);
        pw.println(" mWakeLock=" + mWakeLock);
        pw.println(" mCurPlmn=" + mCurPlmn);
        pw.println(" mMdn=" + mMdn);
        pw.println(" mHomeSystemId=" + mHomeSystemId);
        pw.println(" mHomeNetworkId=" + mHomeNetworkId);
        pw.println(" mMin=" + mMin);
        pw.println(" mPrlVersion=" + mPrlVersion);
        pw.println(" mIsMinInfoReady=" + mIsMinInfoReady);
        pw.println(" mIsEriTextLoaded=" + mIsEriTextLoaded);
        pw.println(" mIsSubscriptionFromRuim=" + mIsSubscriptionFromRuim);
        pw.println(" mCdmaSSM=" + mCdmaSSM);
        pw.println(" mRegistrationDeniedReason=" + mRegistrationDeniedReason);
        pw.println(" mCurrentCarrier=" + mCurrentCarrier);
        pw.flush();
    }

    @Override
    public void setImsRegistrationState(boolean registered) {
        log("ImsRegistrationState - registered : " + registered);

        if (mImsRegistrationOnOff && !registered) {
            if (mAlarmSwitch) {
                mImsRegistrationOnOff = registered;

                Context context = mPhone.getContext();
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                am.cancel(mRadioOffIntent);
                mAlarmSwitch = false;

                sendMessage(obtainMessage(EVENT_CHANGE_IMS_STATE));
                return;
            }
        }
        mImsRegistrationOnOff = registered;
    }

    /// M: c2k modify, functions. @{

    /**
     * Set CDMA connection type.
     * @param type the connection type.
     */
    public void setCdmaConnType(int type) {
        log("setCdmaConnType:" + type);
        /*removeGprsConnTypeRetry();
        if (FeatureOption.EVDO_DT_VIA_SUPPORT) {
            DataConnectionTracker dcTracker = mPhone.mDcTracker;
            if (type == 0) {
                // Not Gprs Attach (set mMasterDataEnabled as false)
                dcTracker.setDataEnabled(false);
            } else {
                // Auto Gprs Attach then activate the default apn type's pdp context
                // (set mMasterDataEnabled as true)
                dcTracker.setDataEnabled(true);
            }
        }

        cdmaConnType = type;

        if (type == 1) {
        //Modify by via begin [ALPS00420584]
            //mDataConnectionState = ServiceState.STATE_IN_SERVICE;
            int radio = mSS.getRadioTechnology();
            log("setCdmaConnType: getRadioTechnology(6,7,8,12,13 is STATE_IN_SERVICE) = " + radio);
            mDataConnectionState = radioTechnologyToDataServiceState(radio);
            //Modify by via end [ALPS00420584]
            mAttachedRegistrants.notifyRegistrants();
        } else if (type == 0) {
            mDataConnectionState = ServiceState.STATE_OUT_OF_SERVICE;
            mDetachedRegistrants.notifyRegistrants();
        }*/
    }

    /**
     * Remove GPRS connection type retry.
     */
    public void removeGprsConnTypeRetry() {
    }

    //Modify by gfzhu VIA begin [ALPS00396584][ALPS00369509]
    protected void setPendingRadioPowerOff() {
        if (mDesiredPowerState) {
            mPendingRadioPowerOffAfterDataOff = false;
        }
    }

    @Override
    public void setRadioPower(boolean power) {
        Rlog.d(LOG_TAG, "setRadioPower " + power +
            " mPendingRadioPowerOffAfterDataOff = " + mPendingRadioPowerOffAfterDataOff);
        mDesiredPowerState = power;
        setPendingRadioPowerOff();

        setPowerStateToDesired();
    }

    /**
     * Set service state notify mechanism if enabled.
     * @param enable true to open.
     */
    public void enableServiceStateNotify(boolean enable) {
        mEnableNotify = enable;
    }

    /**
     * Update the PRL version.
     * @param prlVersion the version value
     */
    public void updatePrlVersion(String prlVersion) {
        log("updatePrlVersion(), oldprl=" + mPrlVersion + ", newPrl=" + prlVersion
                + ", key=" + PRL_VERSION_KEY_NAME);
        final ContentResolver cr = mPhone.getContext().getContentResolver();
        android.provider.Settings.System.putString(cr, PRL_VERSION_KEY_NAME, prlVersion);
        mPrlVersion = prlVersion;
    }

    /// @}

    /// M: [C2K][SVLTE] Support 4G Uicc card. @{
    private boolean isICCIDReady() {
        String iccid = SystemProperties.get("ril.iccid.svltesim1");
        log("ICCID for CDMA Phone is " + iccid);
        boolean ret = false;
        if (iccid == null || "".equals(iccid)) {
            log("ICC read not ready for CDMA phone");
            ret = false;
        } else {
            log("ICC read ready");
            ret = true;
        }
        return ret;
    }

    private static boolean is4GUiccCard() {
        String cardType = SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[0]);
        Rlog.d(LOG_TAG, "is4GUicc cardType=" + cardType);
        String appType[] = cardType.split(",");
        for (int i = 0; i < appType.length; i++) {
            if ("USIM".equals(appType[i])) {
                Rlog.d(LOG_TAG, "is4GUiccCard: contain USIM");
                return true;
            }
        }
        Rlog.d(LOG_TAG, "is4GUiccCard: not contain USIM");
        return false;
    }

    /**
     * Get update spn phone id for service state tracker.
     * @param sstType 0 for mGSMSS, others for mCDMASS
     * @return phone id for the phone to update PLMN/SPN
     */
    public int getUpdateSvlteSpnPhoneId(int sstType) {
        return mPhone.getPhoneId();
    }

    /**
     * Set update spn service state tracker type.
     *
     * @param sstType 0 for mGSMSS, others for mCDMASS
     */
    public void setUpdateSvlteSpnSstType(final int sstType) {
        log("setUpdateSvlteSpnSstType, mSstType=" + mSstType + ", sstType=" + sstType);
        this.mSstType = sstType;
    }

    /**
     * Get update spn service state tracker type.
     *
     * @return sstType for to update PLMN/SPN
     */
    public int getUpdateSvlteSpnSstType() {
        return mSstType;
    }

    protected boolean isSimLockedOrAbsent() {
        boolean isLockedOrAbsent = false;
        UiccCard uiccCard = mUiccController.getUiccCard(mPhone.getPhoneId());
        if (null != uiccCard) {
            if (CardState.CARDSTATE_ABSENT == uiccCard.getCardState()) {
                isLockedOrAbsent = true;
                log("isSimLockedOrAbsent card state = " + uiccCard.getCardState());
            } else {
                UiccCardApplication svlteApp = SvlteUiccUtils.getInstance().getSvlteApplication(
                                mUiccController, mPhone.getPhoneId(), UiccController.APP_FAM_3GPP2);
                if (null != svlteApp) {
                    AppState uiccAppState = svlteApp.getState();
                    isLockedOrAbsent = AppState.APPSTATE_PIN == uiccAppState
                            || AppState.APPSTATE_PUK == uiccAppState
                            || AppState.APPSTATE_UNKNOWN == uiccAppState;
                    log("isSimLockedOrAbsent uiccAppState = " + uiccAppState);
                }
            }
        }
        Rlog.d(LOG_TAG, "isSimLockedOrAbsent isLockedOrAbsent = " + isLockedOrAbsent);
        return isLockedOrAbsent;
    }
    //
    ///M: For C2K OM solution2. @{
    private boolean needUpdateSpn() {

        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            return true;
        }

        int phoneId = mPhone.getPhoneId();

        int radioTechMode = SvlteModeController.
                getRadioTechnologyMode(SvlteUtils.getSlotId(phoneId));

        final int updateSpnPhoneId = getUpdateSvlteSpnPhoneId(SST_TYPE);
        log("radioTechMode is " + radioTechMode + ", phoneId = " + phoneId
                + ", updateSpnPhoneId = " + updateSpnPhoneId);

        if ((radioTechMode == SvlteModeController.RADIO_TECH_MODE_SVLTE)
                && (updateSpnPhoneId == mPhone.getPhoneId())) {
            return true;
        }

        return false;

    }
    ///@}
    /// M: Add for the svlte set radio power and config band mode.
    /**
     * Set radio power and config the band mode.
     * @param radioPower on or off for the cdma radio.
     */
    protected void configAndSetRadioPower(boolean radioPower) {
        mCi.setRadioPower(radioPower, null);
    }
    ///@}
}
