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

package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkInfo;  //Add for ePDG
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Pair;
import android.util.LocalLog;
import android.view.WindowManager;
import android.telephony.Rlog;

import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.ArrayUtils;

/** M: start */
import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.IGsmDCTExt;
import com.mediatek.common.telephony.ITelephonyExt;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.dataconnection.FdManager;
import com.mediatek.internal.telephony.dataconnection.IaExtendParam;
/** M: end */

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Objects;
import java.lang.StringBuilder;

import com.android.internal.telephony.ServiceStateTracker;

import com.mediatek.internal.telephony.ltedc.IRilDcArbitrator;
import com.mediatek.internal.telephony.ltedc.LteDcConstants;
import com.mediatek.internal.telephony.ltedc.svlte.IratController;
import com.mediatek.internal.telephony.ltedc.svlte.IratDataSwitchHelper;
import com.mediatek.internal.telephony.ltedc.svlte.MdIratInfo;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteSstProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import android.provider.Settings.SettingNotFoundException; //20150619-zubin.chen-add-for-df105626
//zhiyuan 20151029 add isdm persist.data.cc33.support for task730295
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.TextView;
//End 20151029 add isdm persist.data.cc33.support for task730295
/*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
/*-END-20151014-add-by-qingtao.wen-for-task745079-*/
/**
 * {@hide}
 */
public final class DcTracker extends DcTrackerBase implements IratController.OnIratEventListener {
    protected final String LOG_TAG = "DCT";

    /**
     * List of messages that are waiting to be posted, when data call disconnect
     * is complete
     */
    private ArrayList<Message> mDisconnectAllCompleteMsgList = new ArrayList<Message>();

    private RegistrantList mAllDataDisconnectedRegistrants = new RegistrantList();

    protected int mDisconnectPendingCount = 0;

    /**
     * Handles changes to the APN db.
     */
    private class ApnChangeObserver extends ContentObserver {
        public ApnChangeObserver () {
            super(mDataConnectionTracker);
        }

        @Override
        public void onChange(boolean selfChange) {
            /// M:[C2K][IRAT] Post to handle APN change after IRAT finished.
            if (isDuringIrat()) {
                mHasPendingApnChange = true;
                return;
            }
            removeMessages(DctConstants.EVENT_APN_CHANGED);
            // M: De-bound the onApnChanged in threads trigger in the same time
            sendMessageDelayed(obtainMessage(DctConstants.EVENT_APN_CHANGED), APN_CHANGE_MILLIS);
            //sendMessage(obtainMessage(DctConstants.EVENT_APN_CHANGED));
        }
    }

    //***** Instance Variables

    private boolean mReregisterOnReconnectFailure = false;


    //***** Constants

    // Used by puppetmaster/*/radio_stress.py
    private static final String PUPPET_MASTER_RADIO_STRESS_TEST = "gsm.defaultpdpcontext.active";

    private static final int POLL_PDP_MILLIS = 5 * 1000;
    private static final int APN_CHANGE_MILLIS = 1 * 1000;

    private static final int PROVISIONING_SPINNER_TIMEOUT_MILLIS = 120 * 1000;

    static final Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID =
                        Uri.parse("content://telephony/carriers/preferapn_no_update/subId/");

    static final String APN_ID = "apn_id";

    private boolean mCanSetPreferApn = false;

    private AtomicBoolean mAttached = new AtomicBoolean(false);

    /** Watches for changes to the APN db. */
    private ApnChangeObserver mApnObserver;

    private final String mProvisionActionName;
    private BroadcastReceiver mProvisionBroadcastReceiver;
    private ProgressDialog mProvisioningSpinner;

    public boolean mImsRegistrationState = false;
    private ApnContext mWaitCleanUpApnContext = null;
    private boolean mDeregistrationAlarmState = false;
    private PendingIntent mImsDeregistrationDelayIntent = null;

    // M: Fast Dormancy
    protected FdManager mFdMgr;

    //M: For Plug in
    private static final boolean BSP_PACKAGE =
            SystemProperties.getBoolean("ro.mtk_bsp_package", false);
    private IGsmDCTExt mGsmDctExt;

    //M: Vsim
    private static final String PROPERTY_VSIM_ENABLE =
            TelephonyProperties.PROPERTY_EXTERNAL_SIM_ENABLED;

    private boolean mConflictApn = false;

    // VOLTE [start]
    private static final boolean MTK_IMS_SUPPORT = SystemProperties.get("ro.mtk_ims_support")
                                                            .equals("1") ? true : false;
    private static final boolean TC1_FEATURE = SystemProperties.get("ro.mtk_tc1_feature")
                                                            .equals("1") ? true : false;
    private static final String VOLTE_EMERGENCY_PDN_APN_NAME = "volte.emergency.pdn.name";
    private static final String VOLTE_EMERGENCY_PDN_PROTOCOL = "volte.emergency.pdn.protocol";
    private static final String VOLTE_DEFAULT_EMERGENCY_PDN_APN_NAME = "";
    private static final String VOLTE_DEFAULT_EMERGENCY_PDN_PROTOCOL = "IPV4V6";  //IP, IPV6, IPV4V6
    // VOLTE [end]

    ///M: ePDG feature support
    private static final boolean EPDG_FEATURE = SystemProperties.get("ro.mtk_epdg_support")
                                                            .equals("1") ? true : false;

    // M: start of throttling APN
    private static final boolean THROTTLING_APN_ENABLED =
            SystemProperties.get("ro.mtk_volte_support").equals("1");
    private static final String PROPERTY_THROTTLING_APN_ENABLED = "ril.throttling.enabled";
    private static final String HIGH_THROUGHPUT_APN[] = {
        PhoneConstants.APN_TYPE_ALL,
        PhoneConstants.APN_TYPE_DEFAULT,
        PhoneConstants.APN_TYPE_DUN,
        PhoneConstants.APN_TYPE_HIPRI,
        PhoneConstants.APN_TYPE_TETHERING
    };

    // VOLTE
    private static final String IMS_APN[] = {
        PhoneConstants.APN_TYPE_IMS,
        PhoneConstants.APN_TYPE_EMERGENCY,
    };

    private static final int PDP_CONNECTION_POOL_SIZE = 3;
    private static final int THROTTLING_MAX_PDP_SIZE = 8;

    private static final int MIN_ID_HIGH_TROUGHPUT = 0;
    private static final int MAX_ID_HIGH_TROUGHPUT = 1;
    private static final int MIN_ID_OTHERS_TROUGHPUT = 2;
    private static final int MAX_ID_OTHERS_TROUGHPUT = 3;
    private static final int MIN_ID_IMS_TROUGHPUT = 4;
    private static final int MAX_ID_IMS_TROUGHPUT = 6;

    private AtomicInteger mHighThroughputIdGenerator = new AtomicInteger(0);
    private AtomicInteger mOthersUniqueIdGenerator = new AtomicInteger(2);
    private AtomicInteger mImsUniqueIdGenerator = new AtomicInteger(4);
    // M: end of throttling APN

    //M: for prevent onApnChanged and onRecordLoaded happened at the same time
    private Integer mCreateApnLock = new Integer(0);

    // M:[C2K][IRAT] IRAT code start @{
    private static final String PROP_NAME_SET_TEST_RAT = "mtk.test.rat";

    // Set APP family to unknown when radio technology is not specified.
    public static final int APP_FAM_UNKNOWN = 0;

    private AtomicReference<IccRecords> mLteIccRecords = new AtomicReference<IccRecords>();

    private SvltePhoneProxy mSvltePhoneProxy;
    private IratController mIratController;
    private IratDataSwitchHelper mIratDataSwitchHelper;
    private SvlteSstProxy mSstProxy;

    // Whether there is pending APN change messages.
    private boolean mHasPendingApnChange;
    // Block setup data on connectable APN until clean up down.
    private boolean mSetupDataBlocked;
    // LTE record loaded
    private boolean mHasPendingLteRecordLoaded;
    // M:}@

    private Object mDeactivatingByNWLock = new Object();
    private boolean mDeactivatingByNW = false;

    /*begin-20151029-yangning.hong-add-for-task742145*/
    private boolean mIsHideFDN = false;
    /*end-20151029-yangning.hong-add-for-task742145*/

    /*begin zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
    private boolean mHasStar99Pound = false;
    private LocalQueryHandler mLocalQueryHandler = null;
    private static final String[] COLUMN_NAMES = new String[] {
        "name",
        "number",
    };
    private FdnChangeObserver mFdnObserver = null;
    private class FdnChangeObserver extends ContentObserver {
        public FdnChangeObserver () {
            super(mDataConnectionTracker);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (isFdnEnabled()) {
                log("fdn list onChange");
                queryFdnList();
            }
        }
    }
    private BroadcastReceiver mPhbStateChangedReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String action = intent.getAction();
            if (TelephonyIntents.ACTION_PHB_STATE_CHANGED.equals(action)
                    && intent.getBooleanExtra("ready", false)) {
                log("onReceive: ACTION_PHB_STATE_CHANGED and ready");
                if (isFdnEnabled()) {
                    queryFdnList();
                }
            }
        }
    };
    /*end zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/

    /*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
    //BEGIN-2016-01-22-modified-xiaodong.liu-for-defect1461258
    private String APN_TABLE_23430[][] = {
            {
                    "EE", "Internet", "everywhere"
            }, 
            {
                    "EE", "MMS", "eezone"
            }, 
            {
                    "Virgin", "Virgin Media Mobile Internet", "goto.virginmobile.uk"
            }, 
            {
                    "ASDA Mobile", "Asda Internet", "everywhere"
            }, 
            {
                "ASDA Mobile", "ASDA MMS", "eezone"
            },
            {
                "BT Mobile", "BT Internet", "btmobile.bt.com"
            },
            {
                "BT Mobile", "BT MMS", "mms.bt.com"
            },
            {
                    "BT OnePhone", "BT One Phone Internet", "internet.btonephone.com"
            },
            {
                    "BT OnePhone", "BT One Phone MMS", "mms.btonephone.com"
            },
            {
                "Virgin", "Virgin MMS", "goto.virginmobile.uk"
            },
    };

    private String APN_TABLE_23433[][] = {
            {
                    "EE", "Internet", "everywhere"
            }, 
            {
                    "EE", "MMS", "eezone"
            }, 
            {
                    "LIFE Mobile", "LIFE Mobile Data", "tslpaygnet"
            }, 
            {
                    "LIFE Mobile", "LIFE Mobile MMS", "tsmms"
            },
    };

    private String APN_TABLE_23410[][] = {
            {
                "O2 Pay Monthly", "O2 Mobile Web", "mobile.o2.co.uk"
            }, 
            {
                "O2 Pay Monthly", "O2 MMS", "wap.o2.co.uk"
            },
            {
                    "O2 Pay & Go", "O2 Pay & Go", "payandgo.o2.co.uk"
            }, 
            {
                    "TESCO", "TESCO Mobile", "prepay.tesco-mobile.com"
            },
            {
                "giffgaff", "giffgaff", "giffgaff.com"
            },
            {
                "O2 Pay & Go", "O2 MMS", "payandgo.o2.co.uk"
            },
            {
                "TESCO", "Tesco MMS", "prepay.tesco-mobile.com"
            },
    };

    private String APN_TABLE_23415[][] = {
            {
                "Vodafone Contract", "Vodafone Contract", "wap.vodafone.co.uk"
            }, 
            {
                "Vodafone PAYG", "Vodafone PAYG", "pp.vodafone.co.uk"
            },
            {
                "BT Mobile", "BT Mobile Internet", "btmobile.bt.com"
            },
            {
                "Sainsburys PAYG", "Sainsbury's PAYG", "payg.mobilebysainsburys.co.uk"
            },
            {
                "Lebara", "Lebara", "uk.lebara.mobi"
            },
            {
                "Sainsburys Contract", "Sainsbury's Contract", "mobilebysainsburys.co.uk"
            },
            {
                    "Talkmobile Contract", "Talkmob Internet", "talkmobile.co.uk"
            }, 
            {
                    "Talkmobile Contract", "Talkmob MMS", "talkmobile.co.uk"
            }, 
            {
                    "Talkmobile PAYG", "Talkmob PAYG Int", "payg.talkmobile.co.uk"
            }, 
            {
                    "Talkmobile PAYG", "Talkmob PAYG MMS", "payg.talkmobile.co.uk"
            }, 
            {
                    "TalkTalk", "TalkTalk WAP", "mobile.talktalk.co.uk"
            },
    };
  //END-2016-01-22-modified-xiaodong.liu-for-defect1461258

    private static final String[] SIMSERVICEGEMI = {
         Settings.Secure.SIM_SERVICE,
            Settings.Secure.SIM_SERVICE_GEMI   
    };
    /*-END-20151014-add-by-qingtao.wen-for-task745079-*/

    //***** Constructor
    public DcTracker(PhoneBase p) {
        super(p);
        if (DBG) log("DcTracker.constructor");

        mDataConnectionTracker = this;
        update();

        /** M: create worker handler to handle DB/IO access */
        createWorkerHandler();

        //M: Move register all event from update to construct, to avoid register more times.
        registerForAllEvents();

        mApnObserver = new ApnChangeObserver();
        p.getContext().getContentResolver().registerContentObserver(
                Telephony.Carriers.CONTENT_URI, true, mApnObserver);

        initApnContexts();

       IntentFilter filter = new IntentFilter();
        for (ApnContext apnContext : mApnContexts.values()) {
            // Register the reconnect and restart actions.
            filter.addAction(INTENT_RECONNECT_ALARM + '.' + apnContext.getApnType());
            filter.addAction(INTENT_RESTART_TRYSETUP_ALARM + '.' + apnContext.getApnType());
            mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);
        }

    /*begin zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
        mLocalQueryHandler = new LocalQueryHandler(mPhone.getContext().getContentResolver());
        filter.addAction(TelephonyIntents.ACTION_PHB_STATE_CHANGED);
        mPhone.getContext().registerReceiver(mPhbStateChangedReceiver, filter);
    /*end zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
        
        // M: Fast Dormancy init
        mFdMgr = FdManager.getInstance(p);

        //MTK START: Add Plug in
        if (!BSP_PACKAGE) {
            try {
                mGsmDctExt =
                    MPlugin.createInstance(IGsmDCTExt.class.getName(), mPhone.getContext());
            } catch (Exception e) {
                if (DBG) {
                    log("mGsmDctExt init fail");
                }
                e.printStackTrace();
            }
        }
        //MTK END

        mProvisionActionName = "com.android.internal.telephony.PROVISION" + p.getPhoneId();

        // M: Delay register to wait PhoneFactory to finish makeDefaultPhone.
        sendEmptyMessage(DctConstants.EVENT_POST_CREATE_PHONE);

        /*begin-20151029-yangning.hong-add-for-task742145*/
        //When FDN feature disable, always allow data connection
        mIsHideFDN = mPhone.getContext().getResources().getBoolean(com.android.internal.R.bool.def_remove_fdn_feature);
        /*end-20151029-yangning.hong-add-for-task742145*/
    }

    protected void registerForAllEvents() {
        if (DBG) {
            log("registerForAllEvents: mPhone = " + mPhone);
        }
        mPhone.mCi.registerForAvailable(this, DctConstants.EVENT_RADIO_AVAILABLE, null);
        mPhone.mCi.registerForOffOrNotAvailable(this,
               DctConstants.EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mPhone.mCi.registerForDataNetworkStateChanged(this,
               DctConstants.EVENT_DATA_STATE_CHANGED, null);
        // Note, this is fragile - the Phone is now presenting a merged picture
        // of PS (volte) & CS and by diving into its internals you're just seeing
        // the CS data.  This works well for the purposes this is currently used for
        // but that may not always be the case.  Should probably be redesigned to
        // accurately reflect what we're really interested in (registerForCSVoiceCallEnded).
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mPhone.getCallTracker().registerForVoiceCallEnded(this,
                    DctConstants.EVENT_VOICE_CALL_ENDED, null);
            mPhone.getCallTracker().registerForVoiceCallStarted(this,
                   DctConstants.EVENT_VOICE_CALL_STARTED, null);
        }
		//Begin zhiyuan 20151118 change for Defect513170,Pixi4_4
		/*
        mPhone.getServiceStateTracker().registerForDataRoamingOn(mWorkerHandler,
               DctConstants.EVENT_DATA_ROAMING_CHANGED, null);
        mPhone.getServiceStateTracker().registerForDataRoamingOff(mWorkerHandler,
               DctConstants.EVENT_DATA_ROAMING_CHANGED, null);
		*/		
		 mPhone.getServiceStateTracker().registerForDataRoamingOn(this,DctConstants.EVENT_ROAMING_ON, null);
         mPhone.getServiceStateTracker().registerForDataRoamingOff(this,DctConstants.EVENT_ROAMING_OFF, null);
		//End zhiyuan 20151118 change for Defect513170,Pixi4_4
        mPhone.getServiceStateTracker().registerForPsRestrictedEnabled(this,
                DctConstants.EVENT_PS_RESTRICT_ENABLED, null);
        mPhone.getServiceStateTracker().registerForPsRestrictedDisabled(this,
                DctConstants.EVENT_PS_RESTRICT_DISABLED, null);
     //   SubscriptionManager.registerForDdsSwitch(this,
     //          DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS, null);

        // M: [LTE][Low Power][UL traffic shaping] Start
        mPhone.mCi.registerForLteAccessStratumState(this,
                DctConstants.EVENT_LTE_ACCESS_STRATUM_STATE, null);
        // M: [LTE][Low Power][UL traffic shaping] End

        /// M:[C2K][IRAT] Don't regisger attach/detach events if IRAT supported. {@
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mPhone.getServiceStateTracker().registerForDataConnectionAttached(this,
                   DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null);
            mPhone.getServiceStateTracker().registerForDataConnectionDetached(this,
                   DctConstants.EVENT_DATA_CONNECTION_DETACHED, null);
            mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this,
                    DctConstants.EVENT_DATA_RAT_CHANGED, null);
            // M: cc33
            mPhone.mCi.registerForRemoveRestrictEutran(this,
                    DctConstants.EVENT_REMOVE_RESTRICT_EUTRAN, null);
            // M: Reset Attach Apn
            mPhone.mCi.registerForResetAttachApn(this, DctConstants.EVENT_RESET_ATTACH_APN, null);
        }
        /// @}
    }
    @Override
    public void dispose() {
        if (DBG) log("DcTracker.dispose");

        if (mProvisionBroadcastReceiver != null) {
            mPhone.getContext().unregisterReceiver(mProvisionBroadcastReceiver);
            mProvisionBroadcastReceiver = null;
        }
        if (mProvisioningSpinner != null) {
            mProvisioningSpinner.dismiss();
            mProvisioningSpinner = null;
        }

        cleanUpAllConnections(true, null);

        super.dispose();

        mPhone.getContext().getContentResolver().unregisterContentObserver(mApnObserver);


    /*begin zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
        if (mFdnObserver != null) {
            mPhone.getContext().getContentResolver().unregisterContentObserver(mFdnObserver);
        }
        if (mPhbStateChangedReceiver != null) {
            mPhone.getContext().unregisterReceiver(mPhbStateChangedReceiver);
        }
    /*end zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
        
        unregisterForAllEvents();

        // M: unregister events which registered after phone create.
        unregisterForEventsAfterPhoneCreated();

        mApnContexts.clear();
        mPrioritySortedApnContexts.clear();

        destroyDataConnections();

        /** M: exit worker thread */
        if (mWorkerHandler != null) {
            Looper looper = mWorkerHandler.getLooper();
            looper.quit();
        }
    }
    protected void unregisterForAllEvents() {
        if (DBG) log("unregisterForAllEvents: mPhone = " + mPhone);
         //Unregister for all events
        mPhone.mCi.unregisterForAvailable(this);
        mPhone.mCi.unregisterForOffOrNotAvailable(this);
        /// M:[C2K][IRAT] Don't unregisger attach/detach events if IRAT supported. {@
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            IccRecords r = mIccRecords.get();
            if (r != null) {
                r.unregisterForRecordsLoaded(this);
                mIccRecords.set(null);
            }
        }
        /// @}
        mPhone.mCi.unregisterForDataNetworkStateChanged(this);
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            unregisterForVoiceCallEventSvlte();
        } else {
            mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
            mPhone.getCallTracker().unregisterForVoiceCallStarted(this);
        }
        mPhone.getServiceStateTracker().unregisterForDataRoamingOn(mWorkerHandler);
        mPhone.getServiceStateTracker().unregisterForDataRoamingOff(mWorkerHandler);
        mPhone.getServiceStateTracker().unregisterForPsRestrictedEnabled(this);
        mPhone.getServiceStateTracker().unregisterForPsRestrictedDisabled(this);
        //SubscriptionManager.unregisterForDdsSwitch(this);

        /// M:[C2K][IRAT] Don't unregisger attach/detach events if IRAT supported. {@
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(this);
            mPhone.getServiceStateTracker().unregisterForDataConnectionAttached(this);
            mPhone.getServiceStateTracker().unregisterForDataConnectionDetached(this);

            // cc33 event
            mPhone.mCi.unregisterForRemoveRestrictEutran(this);
            // M: Reset Attach Apn
            mPhone.mCi.unregisterForResetAttachApn(this);
        }
        /// @}
    }

    @Override
    public void incApnRefCount(String name, LocalLog log) {
        ApnContext apnContext = mApnContexts.get(name);
        log.log("DcTracker.incApnRefCount on " + name + " found " + apnContext);
        if (apnContext != null) {
            log("incApnRefCount apnContext = " + apnContext);
            apnContext.incRefCount(log);
        }
    }

    @Override
    public void decApnRefCount(String name, LocalLog log) {
        ApnContext apnContext = mApnContexts.get(name);
        log.log("DcTracker.decApnRefCount on " + name + " found " + apnContext);
        if (apnContext != null) {
            log("decApnRefCount apnContext = " + apnContext);
            apnContext.decRefCount(log);
        }
    }

    @Override
    public boolean isApnSupported(String name) {
        if (name == null) {
            loge("isApnSupported: name=null");
            return false;
        }
        ApnContext apnContext = mApnContexts.get(name);
        if (apnContext == null) {
            loge("Request for unsupported mobile name: " + name);
            return false;
        }
        return true;
    }

    @Override
    public int getApnPriority(String name) {
        ApnContext apnContext = mApnContexts.get(name);
        if (apnContext == null) {
            loge("Request for unsupported mobile name: " + name);
        }
        return apnContext.priority;
    }

    // Turn telephony radio on or off.
    private void setRadio(boolean on) {
        final ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
        try {
            phone.setRadio(on);
        } catch (Exception e) {
            // Ignore.
        }
    }

    // Class to handle Intent dispatched with user selects the "Sign-in to network"
    // notification.
    private class ProvisionNotificationBroadcastReceiver extends BroadcastReceiver {
        private final String mNetworkOperator;
        // Mobile provisioning URL.  Valid while provisioning notification is up.
        // Set prior to notification being posted as URL contains ICCID which
        // disappears when radio is off (which is the case when notification is up).
        private final String mProvisionUrl;

        public ProvisionNotificationBroadcastReceiver(String provisionUrl, String networkOperator) {
            mNetworkOperator = networkOperator;
            mProvisionUrl = provisionUrl;
        }

        private void setEnableFailFastMobileData(int enabled) {
            sendMessage(obtainMessage(DctConstants.CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA, enabled, 0));
        }

        private void enableMobileProvisioning() {
            final Message msg = obtainMessage(DctConstants.CMD_ENABLE_MOBILE_PROVISIONING);
            msg.setData(Bundle.forPair(DctConstants.PROVISIONING_URL_KEY, mProvisionUrl));
            sendMessage(msg);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // Turning back on the radio can take time on the order of a minute, so show user a
            // spinner so they know something is going on.
            mProvisioningSpinner = new ProgressDialog(context);
            mProvisioningSpinner.setTitle(mNetworkOperator);
            mProvisioningSpinner.setMessage(
                    // TODO: Don't borrow "Connecting..." i18n string; give Telephony a version.
                    context.getText(com.android.internal.R.string.media_route_status_connecting));
            mProvisioningSpinner.setIndeterminate(true);
            mProvisioningSpinner.setCancelable(true);
            // Allow non-Activity Service Context to create a View.
            mProvisioningSpinner.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            mProvisioningSpinner.show();
            // After timeout, hide spinner so user can at least use their device.
            // TODO: Indicate to user that it is taking an unusually long time to connect?
            sendMessageDelayed(obtainMessage(DctConstants.CMD_CLEAR_PROVISIONING_SPINNER,
                    mProvisioningSpinner), PROVISIONING_SPINNER_TIMEOUT_MILLIS);
            // This code is almost identical to the old
            // ConnectivityService.handleMobileProvisioningAction code.
            setRadio(true);
            setEnableFailFastMobileData(DctConstants.ENABLED);
            enableMobileProvisioning();
        }
    }

    @Override
    public boolean isApnTypeActive(String type) {
        ApnContext apnContext = mApnContexts.get(type);
        if (apnContext == null) return false;

        return (apnContext.getDcAc() != null);
    }

    @Override
    public boolean isDataPossible(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        boolean apnContextIsEnabled = apnContext.isEnabled();
        DctConstants.State apnContextState = apnContext.getState();
        boolean apnTypePossible = !(apnContextIsEnabled &&
                (apnContextState == DctConstants.State.FAILED));
        boolean isEmergencyApn = apnContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY);
        // Set the emergency APN availability status as TRUE irrespective of conditions checked in
        // isDataAllowed() like IN_SERVICE, MOBILE DATA status etc.
        boolean dataAllowed = isEmergencyApn || isDataAllowed();
        boolean possible = dataAllowed && apnTypePossible;

        if ((apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                    || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IA))
                && (mPhone.getServiceState().getRilDataRadioTechnology()
                == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN)) {
            log("Default data call activation not possible in iwlan.");
            possible = false;
        }

        if (VDBG) {
            log(String.format("isDataPossible(%s): possible=%b isDataAllowed=%b " +
                    "apnTypePossible=%b apnContextisEnabled=%b apnContextState()=%s",
                    apnType, possible, dataAllowed, apnTypePossible,
                    apnContextIsEnabled, apnContextState));
        }
        return possible;
    }

    @Override
    protected void finalize() {
        if(DBG) log("finalize");
    }

    private ApnContext addApnContext(String type, NetworkConfig networkConfig) {
        ApnContext apnContext = new ApnContext(mPhone.getContext(), type, LOG_TAG, networkConfig,
                this);
        mApnContexts.put(type, apnContext);
        mPrioritySortedApnContexts.add(apnContext);
        return apnContext;
    }

    protected void initApnContexts() {
        log("initApnContexts: E");
        // Load device network attributes from resources
        String[] networkConfigStrings = mPhone.getContext().getResources().getStringArray(
                com.android.internal.R.array.networkAttributes);
        for (String networkConfigString : networkConfigStrings) {
            NetworkConfig networkConfig = new NetworkConfig(networkConfigString);
            ApnContext apnContext = null;

            switch (networkConfig.type) {
            case ConnectivityManager.TYPE_MOBILE:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_DEFAULT, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_MMS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_MMS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_SUPL, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_DUN:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_DUN, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_HIPRI, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_FOTA:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_FOTA, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_IMS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_IMS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_CBS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_CBS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_IA:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_IA, networkConfig);
                break;
            /** M: start */
            case ConnectivityManager.TYPE_MOBILE_DM:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_DM, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_NET:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_NET, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_WAP:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_WAP, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_CMMAIL:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_CMMAIL, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_RCSE:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_RCSE, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_XCAP:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_XCAP, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_RCS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_RCS, networkConfig);
                break;
            /** M: end*/
            case ConnectivityManager.TYPE_MOBILE_EMERGENCY:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_EMERGENCY, networkConfig);
                break;
            default:
                log("initApnContexts: skipping unknown type=" + networkConfig.type);
                continue;
            }
            log("initApnContexts: apnContext=" + apnContext);
        }

        //The implement of priorityQueue class is incorrect, we sort the list by ourself
        Collections.sort(mPrioritySortedApnContexts, new Comparator<ApnContext>() {
            public int compare(ApnContext c1, ApnContext c2) {
                return c2.priority - c1.priority;
            }
        });
        log("initApnContexts: mPrioritySortedApnContexts=" + mPrioritySortedApnContexts);
        log("initApnContexts: X mApnContexts=" + mApnContexts);
    }

    @Override
    public LinkProperties getLinkProperties(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            DcAsyncChannel dcac = apnContext.getDcAc();
            if (dcac != null) {
                if (DBG) log("return link properites for " + apnType);
                return dcac.getLinkPropertiesSync();
            }
        }
        if (DBG) log("return new LinkProperties");
        return new LinkProperties();
    }

    @Override
    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext!=null) {
            DcAsyncChannel dataConnectionAc = apnContext.getDcAc();
            if (dataConnectionAc != null) {
                if (DBG) {
                    log("get active pdp is not null, return NetworkCapabilities for " + apnType);
                }
                return dataConnectionAc.getNetworkCapabilitiesSync();
            }
        }
        if (DBG) log("return new NetworkCapabilities");
        return new NetworkCapabilities();
    }

    @Override
    // Return all active apn types
    public String[] getActiveApnTypes() {
        if (DBG) log("get all active apn types");
        ArrayList<String> result = new ArrayList<String>();

        for (ApnContext apnContext : mApnContexts.values()) {
            if (mAttached.get() && apnContext.isReady()) {
                result.add(apnContext.getApnType());
            }
        }

        return result.toArray(new String[0]);
    }

    @Override
    // Return active apn of specific apn type
    public String getActiveApnString(String apnType) {
        if (VDBG) log( "get active apn string for type:" + apnType);
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            ApnSetting apnSetting = apnContext.getApnSetting();
            if (apnSetting != null) {
                return apnSetting.apn;
            }
        }
        return null;
    }

    @Override
    public boolean isApnTypeEnabled(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        return apnContext.isEnabled();
    }

    @Override
    protected void setState(DctConstants.State s) {
        if (DBG) log("setState should not be used in GSM" + s);
    }

    // Return state of specific apn type
    @Override
    public DctConstants.State getState(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.getState();
        }
        return DctConstants.State.FAILED;
    }

    // Return if apn type is a provisioning apn.
    @Override
    protected boolean isProvisioningApn(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.isProvisioningApn();
        }
        return false;
    }

    // Return state of overall
    @Override
    public DctConstants.State getOverallState() {
        boolean isConnecting = false;
        boolean isFailed = true; // All enabled Apns should be FAILED.
        boolean isAnyEnabled = false;

        //M: For debug, dump overall state.
        StringBuilder builder = new StringBuilder();
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext != null) {
                builder.append(apnContext.toString() + ", ");
            }
        }
        if (DBG) log("overall state is " + builder);

        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.isEnabled()) {
                isAnyEnabled = true;
                switch (apnContext.getState()) {
                case CONNECTED:
                case DISCONNECTING:
                    if (DBG) log("overall state is CONNECTED");
                    return DctConstants.State.CONNECTED;
                case RETRYING:
                case CONNECTING:
                    isConnecting = true;
                    isFailed = false;
                    break;
                case IDLE:
                case SCANNING:
                    isFailed = false;
                    break;
                default:
                    isAnyEnabled = true;
                    break;
                }
            }
        }

        if (!isAnyEnabled) { // Nothing enabled. return IDLE.
            if (DBG) log( "overall state is IDLE");
            return DctConstants.State.IDLE;
        }

        if (isConnecting) {
            if (DBG) log( "overall state is CONNECTING");
            return DctConstants.State.CONNECTING;
        } else if (!isFailed) {
            if (DBG) log( "overall state is IDLE");
            return DctConstants.State.IDLE;
        } else {
            if (DBG) log( "overall state is FAILED");
            return DctConstants.State.FAILED;
        }
    }

    @Override
    protected boolean isApnTypeAvailable(String type) {
        if ((type.equals(PhoneConstants.APN_TYPE_DUN) && fetchDunApn() != null) ||
             type.equals(PhoneConstants.APN_TYPE_EMERGENCY)) {
            log("isApnTypeAvaiable, apn: " + type);
            return true;
        }

        if (mAllApnSettings != null) {
            for (ApnSetting apn : mAllApnSettings) {
                if (apn.canHandleType(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Report on whether data connectivity is enabled for any APN.
     * @return {@code false} if data connectivity has been explicitly disabled,
     * {@code true} otherwise.
     */
    @Override
    public boolean getAnyDataEnabled() {
        synchronized (mDataEnabledLock) {
            // M: work ard for google issue which setting value may not sync; try to sync again
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && !mUserDataEnabled) {
                mUserDataEnabled = getDataEnabled();
            }

            if (!(mInternalDataEnabled && mUserDataEnabled && mPolicyDataEnabled)) {
                log("getAnyDataEnabled1 return false. mInternalDataEnabled = " + mInternalDataEnabled
                    + ", mUserDataEnabled = " + mUserDataEnabled
                    + ", mPolicyDataEnabled = " + mPolicyDataEnabled);
                return false;
            }

            for (ApnContext apnContext : mApnContexts.values()) {
                // Make sure we don't have a context that is going down
                // and is explicitly disabled.
                if (isDataAllowed(apnContext)) {
                    log("getAnyDataEnabled1 return true, apn = " + apnContext.getApnType());
                    return true;
                }
            }

            log("getAnyDataEnabled1 return false");
            return false;
        }
    }

    public boolean getAnyDataEnabled(boolean checkUserDataEnabled) {
        synchronized (mDataEnabledLock) {
            // M: work ard for google issue which setting value may not sync; try to sync again
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                    && checkUserDataEnabled && !mUserDataEnabled) {
                mUserDataEnabled = getDataEnabled();
            }

            if (!(mInternalDataEnabled && (!checkUserDataEnabled || mUserDataEnabled)
                        && (!checkUserDataEnabled || mPolicyDataEnabled))) {
                log("getAnyDataEnabled2 return false. mInternalDataEnabled = " + mInternalDataEnabled
                    + ", checkUserDataEnabled = " + checkUserDataEnabled
                    + ", mUserDataEnabled = " + mUserDataEnabled
                    + ", mPolicyDataEnabled = " + mPolicyDataEnabled);
                return false;
            }

            for (ApnContext apnContext : mApnContexts.values()) {
                // Make sure we dont have a context that going down
                // and is explicitly disabled.
                if (isDataAllowed(apnContext)) {
                    log("getAnyDataEnabled2 return true, apn = " + apnContext.getApnType());
                    return true;
                }
            }

            log("getAnyDataEnabled2 return false");
            return false;
        }
    }

    private boolean isDataAllowed(ApnContext apnContext) {
        //If RAT is iwlan then dont allow default/IA PDP at all.
        //Rest of APN types can be evaluated for remaining conditions.
        if ((apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IA))
                && (mPhone.getServiceState().getRilDataRadioTechnology()
                == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN)) {
            log("Default data call activation not allowed in iwlan.");
            return false;
        } else {
            boolean checkDefaultData = true;
            boolean ignoreRoamingSetting = ignoreDataRoaming(apnContext.getApnType());

            // M
            if (PhoneConstants.APN_TYPE_MMS.equals(apnContext.getApnType())
                    || PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType())
                    || PhoneConstants.APN_TYPE_EMERGENCY.equals(apnContext.getApnType())
                    || (TextUtils.equals(apnContext.getApnType(), PhoneConstants.APN_TYPE_DEFAULT)
                    && isVsimActive(mPhone.getPhoneId()))) {
                checkDefaultData = false;
            }

            return apnContext.isReady() && isDataAllowed(checkDefaultData, ignoreRoamingSetting);
        }
    }

    //****** Called from ServiceStateTracker
    /**
     * Invoked when ServiceStateTracker observes a transition from GPRS
     * attach to detach.
     */
    protected void onDataConnectionDetached() {
        /*
         * We presently believe it is unnecessary to tear down the PDP context
         * when GPRS detaches, but we should stop the network polling.
         */
        if (DBG) log ("onDataConnectionDetached: stop polling and notify detached");
        stopNetStatPoll();
        stopDataStallAlarm();
        if (mAutoAttachOnCreationConfig) {
            mAutoAttachOnCreation.set(false);
        }
        notifyDataConnection(Phone.REASON_DATA_DETACHED);
        mAttached.set(false);
    }

    private void onDataConnectionAttached() {
        if (DBG) log("onDataConnectionAttached");

        // M: svlte: After IR, need to make sure the LTE side apn is correct
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && SvlteUtils.isActiveSvlteMode(mPhone)) {
            if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA
                && mIratController.getCurrentRat() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) {
                if (DBG) {
                    log("onDataConnectionAttached: cdma phone receive Lte attached,"
                        + "ignore and wait updateIccRecordAndApn");
                }
                return;
            }
            // Update IccRecord and APN with latest RAT to make APN right.
            int targetRat = getRilDataRadioTechnology(
                    SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId()).getPsPhone());
            if (DBG) {
                log("onDataConnectionAttached targetRat = " + targetRat);
            }
            updateIccRecordAndApn(ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN, targetRat);
        }

        mAttached.set(true);
        if (getOverallState() == DctConstants.State.CONNECTED) {
            if (DBG) log("onDataConnectionAttached: start polling notify attached");
            startNetStatPoll();
            startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
            notifyDataConnection(Phone.REASON_DATA_ATTACHED);
        } else {
            // update APN availability so that APN can be enabled.
            notifyOffApnsOfAvailability(Phone.REASON_DATA_ATTACHED);
        }
        if (mAutoAttachOnCreationConfig) {
            mAutoAttachOnCreation.set(true);
        }
        setupDataOnConnectableApns(Phone.REASON_DATA_ATTACHED);
    }

    @Override
    protected boolean isDataAllowed() {
        // M: Native code move to protected boolean isDataAllowed(boolean checkDefaultData).
        //      because MMS need to pass when default data not be set
        return isDataAllowed(true,false);
    }

    protected boolean isDataAllowed(boolean checkDefaultData, boolean ignoreRoamingSetting) {
        final boolean internalDataEnabled;
        synchronized (mDataEnabledLock) {
            internalDataEnabled = mInternalDataEnabled;
        }

        boolean attachedState = mAttached.get();
        boolean desiredPowerState = mPhone.getServiceStateTracker().getDesiredPowerState();
        int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
        if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN) {
            desiredPowerState = true;
        }

        IccRecords r = mIccRecords.get();
        boolean recordsLoaded = false;
        if (r != null) {
            recordsLoaded = r.getRecordsLoaded();
            if (DBG && !recordsLoaded) log("isDataAllowed getRecordsLoaded=" + recordsLoaded);
        }

        boolean bIsFdnEnabled = isFdnEnabled();

        //FIXME always attach
        boolean psRestricted = mIsPsRestricted;
        int phoneNum = TelephonyManager.getDefault().getPhoneCount();
        if (phoneNum > 1) {
//            attachedState = true;
            psRestricted = false;
        }
        int dataSub = SubscriptionManager.getDefaultDataSubId();
        boolean defaultDataSelected = SubscriptionManager.isValidSubscriptionId(dataSub);
        PhoneConstants.State state = PhoneConstants.State.IDLE;
        // Note this is explicitly not using mPhone.getState.  See b/19090488.
        // mPhone.getState reports the merge of CS and PS (volte) voice call state
        // but we only care about CS calls here for data/voice concurrency issues.
        // Calling getCallTracker currently gives you just the CS side where the
        // ImsCallTracker is held internally where applicable.
        // This should be redesigned to ask explicitly what we want:
        // voiceCallStateAllowDataCall, or dataCallAllowed or something similar.
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            // TODO: Remove it when apply CDMALTEPhone
            // M: Get CS phone's call state
            state = SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId()).getCallTracker().getState();
        } else {
            if (mPhone.getCallTracker() != null) {
                state = mPhone.getCallTracker().getState();
            }
        }
        //M: Add peer phone call state check
        PhoneConstants.State statePeer = getCallStatePeer();
        boolean allowed =
                    (attachedState || mAutoAttachOnCreation.get()) &&
                    recordsLoaded &&
                    (state == PhoneConstants.State.IDLE ||
                     mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) &&
                    (statePeer == PhoneConstants.State.IDLE ||
                     isConcurrentVoiceAndDataAllowedWithPeer()) &&
                    internalDataEnabled &&
                    (defaultDataSelected || !checkDefaultData) &&
                    ((!mPhone.getServiceState().getDataRoaming() &&
                        !mPhone.getServiceStateTracker().isPsRegStateRoamByUnsol()) ||
                        ignoreRoamingSetting ||
                        getDataOnRoamingEnabled()) &&
                    //!mIsPsRestricted &&
                    !psRestricted &&
                    desiredPowerState &&
                    !mDiskSpaceIsLower && /*heyan add for Task 742719 low memory notification 20151210*/
                    (!bIsFdnEnabled||mHasStar99Pound);/*zhiyuan 20151105 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated,Pixi4_35*/
        if (!allowed && DBG) {
            String reason = "";
            if (!(attachedState || mAutoAttachOnCreation.get())) {
                reason += " - Attached= " + attachedState;
            }
            if (!recordsLoaded) reason += " - SIM not loaded";
            if (state != PhoneConstants.State.IDLE &&
                    !mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                reason += " - PhoneState= " + state;
                reason += " - Concurrent voice and data not allowed";
            }
            if (statePeer != PhoneConstants.State.IDLE &&
                    !isConcurrentVoiceAndDataAllowedWithPeer()) {
                reason += " - PeerPhoneState= " + statePeer;
                reason += " - Concurrent voice and data with peer not allowed";
            }
            if (!internalDataEnabled) reason += " - mInternalDataEnabled= false";
            if (!defaultDataSelected && checkDefaultData) reason += " - defaultDataSelected= false";
            if ((mPhone.getServiceState().getDataRoaming() ||
                        mPhone.getServiceStateTracker().isPsRegStateRoamByUnsol()) &&
                        !ignoreRoamingSetting &&
                        !getDataOnRoamingEnabled()) {
                reason += " - Roaming and data roaming not enabled";
            }
            if (mIsPsRestricted) reason += " - mIsPsRestricted= true";
            if (!desiredPowerState) reason += " - desiredPowerState= false";
            if (bIsFdnEnabled && !mHasStar99Pound) reason += " - FDN enabled and has not *99# number";/*zhiyuan 20151105 add Task730582 for 
            isdm def_frameworks_allow_data_connection_with_FDN_activated,Pixi4_35*/
            /*Begin heyan add for Task 742719 low memory notification 20151210 */
            if (mDiskSpaceIsLower) reason += " - Disk space is lower";
            /*End heyan add for Task 742719 low memory notification 20151210 */
            if (DBG) log("isDataAllowed: not allowed due to" + reason);
        }

        /// M: [C2K] PCModem feature, disallow data if PCModem is connected.
        if (allowed && mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            allowed = allowed && (!mDcc.isPcModemConnected());
            if (!allowed && DBG) {
                log("isDataAllowed: phoneType = " + mPhone.getPhoneType() + ", PcModemConnected = "
                        + mDcc.isPcModemConnected());
            }
        }

        return allowed;
    }

    /*-begin-20150619-zubin.chen-add-for-df105626-*/
    private boolean isDomesticRoaming() {
        boolean roaming = mPhone.getServiceState().getRoaming();
        String operatorNumeric = mPhone.getServiceState().getOperatorNumeric();
        String imsi = mPhone.getSubscriberId();        
        boolean sameMcc = false;
        if(imsi == null || operatorNumeric == null)
        	return false;        
        try {
            sameMcc = imsi.substring(0, 3).equals(operatorNumeric.substring(0, 3));
        } catch (Exception e) {
        }

        if (DBG) log("getDataOnRoamingEnabled(): roaming=" + roaming + ", sameMcc=" + sameMcc);

        return (roaming && sameMcc);
    }

    @Override
    public boolean getDataOnRoamingEnabled() {
        return (super.getDataOnRoamingEnabled() ||
                (isDomesticRoaming() && isDomesticRoamingEnabled(mPhone.getSubId())));
    }

    public boolean isDomesticRoamingEnabled(int subId) {
        log("isDomesticRoamingEnabled");
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
        	log("[getSubId]- fail, subId = " + subId);
        	return false;
        }        
        try {
            final ContentResolver resolver = mPhone.getContext().getContentResolver();
            if (TelephonyManager.getDefault().getSimCount() == 1) {
                return Settings.Global.getInt(resolver,
                        Settings.Global.DATA_ROAMING) == SubscriptionManager.DATA_NATIONAL_ROAMING_ENABLE;
            } else {

                int slotID = SubscriptionManager.getSlotId(subId);
                if (slotID == 1) {
                    return Settings.Global.getInt(resolver,
                            Settings.Global.DATA_ROAMING_2) == SubscriptionManager.DATA_NATIONAL_ROAMING_ENABLE;
                } else if(slotID == 0) {
                    return Settings.Global.getInt(resolver,
                            Settings.Global.DATA_ROAMING) == SubscriptionManager.DATA_NATIONAL_ROAMING_ENABLE;
                }else{
                    return TelephonyManager.getIntWithSubId(resolver,
                            Settings.Global.DATA_ROAMING, subId) == SubscriptionManager.DATA_NATIONAL_ROAMING_ENABLE;
                }
            }
        } catch (SettingNotFoundException snfe) {
            log("isDomesticRoamingEnabled false");
            return false;
        }
    }
    /*-end-20150619-zubin.chen-add-for-df105626-*/
    
    // arg for setupDataOnConnectableApns
    private enum RetryFailures {
        // retry failed networks always (the old default)
        ALWAYS,
        // retry only when a substantial change has occured.  Either:
        // 1) we were restricted by voice/data concurrency and aren't anymore
        // 2) our apn list has change
        ONLY_ON_CHANGE
    };

    private void setupDataOnConnectableApns(String reason) {
        setupDataOnConnectableApns(reason, RetryFailures.ALWAYS);
    }

    private void setupDataOnConnectableApns(String reason, RetryFailures retryFailures) {
        if (DBG) log("setupDataOnConnectableApns: " + reason);

        /// M: [C2K][IRAT] DO NOT setup data until clean up finished for IRAT
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && SvlteUtils.isActiveSvlteMode(mPhone)
                && mSetupDataBlocked) {
            log("DO NOT setup data until clean up finished for IRAT.");
            return;
        }


        for (ApnContext apnContext : mPrioritySortedApnContexts) {
            ArrayList<ApnSetting> waitingApns = null;

            if (PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType())
                || PhoneConstants.APN_TYPE_EMERGENCY.equals(apnContext.getApnType())) {
                //for VoLTE, framework should not trigger IMS/Emergency bearer activation
                if (DBG) log("setupDataOnConnectableApns: ignore apnContext " + apnContext);
            } else {
                if (DBG) log("setupDataOnConnectableApns: apnContext " + apnContext);
                if (apnContext.getState() == DctConstants.State.FAILED
                        || apnContext.getState() == DctConstants.State.RETRYING) {
                    if (retryFailures == RetryFailures.ALWAYS) {
                        apnContext.releaseDataConnection(reason);
                    } else if (apnContext.isConcurrentVoiceAndDataAllowed() == false &&
                             mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                        // RetryFailures.ONLY_ON_CHANGE - check if voice concurrency has changed
                        apnContext.releaseDataConnection(reason);
                    } else {
                        // RetryFailures.ONLY_ON_CHANGE - check if the apns have changed
                        int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
                        ArrayList<ApnSetting> originalApns = apnContext.getOriginalWaitingApns();
                        if (originalApns != null && originalApns.isEmpty() == false) {
                            waitingApns = buildWaitingApns(apnContext.getApnType(), radioTech);
                            if (originalApns.size() != waitingApns.size() ||
                                    originalApns.containsAll(waitingApns) == false) {
                                apnContext.releaseDataConnection(reason);
                            }
                        }
                    }
                }
                if (apnContext.isConnectable()) {
                    log("setupDataOnConnectableApns: isConnectable() call trySetupData");
                    apnContext.setReason(reason);
                    trySetupData(apnContext, waitingApns);
                }
            }
        }
    }
    // add by wubin forTASK716716 begin     
    // [Mms][Android guidelines] Network control for MMS in roaming 
    public boolean isSupportedMmsWithoutDataService(PhoneBase mPhone, ApnContext apnContext) {
    	boolean supported = false;
    	if (mPhone.getContext().getResources().getBoolean(com.android.internal.R.bool.def_tctfw_mms_openDataForMms_enable)		                         
    			&& (apnContext.getApnType()== PhoneConstants.APN_TYPE_MMS)) {
    		             supported = true;
        }
    	return supported;     
    }
    // add by wubin forTASK716716 end

/*Begin heyan add for Task 742719 low memory notification 20151210*/
    boolean mDiskSpaceIsLower = false;

    @Override
    protected void onDiskSpaceLower(boolean diskSpaceIsLower) {
        if (DBG)
            log("onDiskSpaceLower");
        mDiskSpaceIsLower = diskSpaceIsLower;
        if (mUserDataEnabled == false)
            return;

        if (mDiskSpaceIsLower) {
            if (DBG) {
                log("onDiskSpaceLowerOn: Tear down data connection on disk space lower.");
            }
            cleanUpAllConnections(true, Phone.REASON_DISKSPACE_LOWER);
            notifyOffApnsOfAvailability(Phone.REASON_DISKSPACE_LOWER);
        } else {
            setupDataOnConnectableApns(Phone.REASON_DISKSPACE_LOWER);
            notifyDataConnection(Phone.REASON_DISKSPACE_LOWER);
        }
    }
/*End heyan add for Task 742719 low memory notification 20151210*/

    private boolean trySetupData(ApnContext apnContext) {
        return trySetupData(apnContext, null);
    }

    private boolean trySetupData(ApnContext apnContext, ArrayList<ApnSetting> waitingApns) {
        String apnType = apnContext.getApnType();

        if (DBG) {
            log("trySetupData for type:" + apnType +
                    " due to " + apnContext.getReason() + " apnContext=" + apnContext);
            log("trySetupData with mIsPsRestricted=" + mIsPsRestricted);
        }
        apnContext.requestLog("trySetupData due to " + apnContext.getReason());

        /// M: [C2K][IRAT] DO NOT setup data until clean up finished for IRAT
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && SvlteUtils.isActiveSvlteMode(mPhone)
                && mSetupDataBlocked) {
            log("DO NOT setup data until clean up finished for IRAT.");
            return false;
        }

        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            apnContext.setState(DctConstants.State.CONNECTED);
            mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());

            log("trySetupData: X We're on the simulator; assuming connected retValue=true");
            return true;
        }

        if (apnContext.getState() == DctConstants.State.DISCONNECTING) {
            if (PhoneConstants.APN_TYPE_IMS.equals(apnType) || PhoneConstants.APN_TYPE_EMERGENCY.equals(apnType)) {
                if (DBG) log("trySetupData:" + apnContext.getApnType() + " is DISCONNECTING, but no trun on reactive flag.");
            } else {
                if (DBG) log("trySetupData:" + apnContext.getApnType() + " is DISCONNECTING, trun on reactive flag.");
                // TODO: need to make sure this code needed or not in L
                //apnContext.setReactive(true);
            }
        }

        // Allow SETUP_DATA request for E-APN to be completed during emergency call
        // and MOBILE DATA On/Off cases as well.
        boolean isEmergencyApn = apnContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY);
        final ServiceStateTracker sst = mPhone.getServiceStateTracker();
        boolean desiredPowerState = sst.getDesiredPowerState();
        boolean checkUserDataEnabled = !(apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)
                /** M: enable MMS and SUPL even if data is disabled */
                || isDataAllowedAsOff(apnContext.getApnType()));

        if (apnContext.isConnectable() && (isEmergencyApn ||
                (isDataAllowed(apnContext) &&
                // modify by wubin forTASK716716 begin
                // getAnyDataEnabled(checkUserDataEnabled) && !isEmergency()))) {
                (getAnyDataEnabled(checkUserDataEnabled) || (!getAnyDataEnabled() &&
                		isSupportedMmsWithoutDataService(mPhone, apnContext))) && !isEmergency()))) {
        	// modify by wubin forTASK716716 end
            if (apnContext.getState() == DctConstants.State.FAILED) {
                String str ="trySetupData: make a FAILED ApnContext IDLE so its reusable";
                if (DBG) log(str);
                apnContext.requestLog(str);
                apnContext.setState(DctConstants.State.IDLE);
            }
            int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
            apnContext.setConcurrentVoiceAndDataAllowed(sst.isConcurrentVoiceAndDataAllowed());
            if (apnContext.getState() == DctConstants.State.IDLE ||
                (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_MMS) &&
                 apnContext.getState() == DctConstants.State.RETRYING)) {

                if (waitingApns == null) {
                    // M: ECC w/o SIM {
                    if (TextUtils.equals(apnContext.getApnType(),
                        PhoneConstants.APN_TYPE_EMERGENCY)) {
                        if (mAllApnSettings == null) {
                            log("mAllApnSettings is null, create first and add emergency one");
                            createAllApnList();
                        } else if (mAllApnSettings.isEmpty()) {
                            log("add mEmergencyApn: " + mEmergencyApn + " to mAllApnSettings");
                            addEmergencyApnSetting();
                        }
                    }
                    // M: ECC w/o SIM }
                    waitingApns = buildWaitingApns(apnContext.getApnType(), radioTech);
                }

                if (waitingApns.isEmpty()) {
                    notifyNoData(DcFailCause.MISSING_UNKNOWN_APN, apnContext);
                    notifyOffApnsOfAvailability(apnContext.getReason());
                    String str = "trySetupData: X No APN found retValue=false";
                    if (DBG) log(str);
                    apnContext.requestLog(str);
                    return false;
                } else {
                    apnContext.setWaitingApns(waitingApns);
                    if (DBG) {
                        log ("trySetupData: Create from mAllApnSettings : "
                                    + apnListToString(mAllApnSettings));
                    }
                }
            }

            if (DBG) {
                log("trySetupData: call setupData, waitingApns : "
                        + apnListToString(apnContext.getWaitingApns()));
            }
            boolean retValue = setupData(apnContext, radioTech);
            notifyOffApnsOfAvailability(apnContext.getReason());

            if (DBG) log("trySetupData: X retValue=" + retValue);
            return retValue;
        } else {
            if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                    && apnContext.isConnectable()) {
                if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_MMS)
                        && TelephonyManager.getDefault().isMultiSimEnabled() && !mAttached.get()) {
                    if (DBG) {
                        log("Wait for attach");
                    }
                    if (apnContext.getState() == DctConstants.State.IDLE) {
                        apnContext.setState(DctConstants.State.RETRYING);
                    }
                    return true;
                } else {
                    mPhone.notifyDataConnectionFailed(apnContext.getReason(),
                            apnContext.getApnType());
                }
            }
            notifyOffApnsOfAvailability(apnContext.getReason());
            String str = "trySetupData: X apnContext not 'ready' retValue=false";
            apnContext.requestLog(str);
            if (DBG) {
                log(str);
                if (!apnContext.isConnectable()) log("apnContext.isConnectable = false");
                if (!isDataAllowed(apnContext)) log("isDataAllowed = false");
                if (!getAnyDataEnabled(checkUserDataEnabled)) {
                    log("getAnyDataEnabled(" + checkUserDataEnabled + ") = false");
                }
            }
            return false;
        }
    }

    @Override
    // Disabled apn's still need avail/unavail notificiations - send them out
    protected void notifyOffApnsOfAvailability(String reason) {
        for (ApnContext apnContext : mApnContexts.values()) {
            if ((!mAttached.get() || !apnContext.isReady()) && apnContext.isNeedNotify()) {
                String apnType = apnContext.getApnType();
                boolean bNotifyOffApns = true;
                if (VDBG) log("notifyOffApnOfAvailability type:" + apnType + " reason: " + reason);
                if (MTK_IMS_SUPPORT) {
                    if (apnType.equals(PhoneConstants.APN_TYPE_EMERGENCY) &&
                        false == apnContext.isDisconnected()) {
                        bNotifyOffApns = false;
                        log ("skip notify, state: " + apnContext.getState());
                    }
                }

                if (bNotifyOffApns) {
                    mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(),
                                                apnType, PhoneConstants.DataState.DISCONNECTED);
                }
            } else {
                if (VDBG) {
                    log("notifyOffApnsOfAvailability skipped apn due to attached && isReady " +
                            apnContext.toString());
                }
            }
        }
    }

    /**
     * If tearDown is true, this only tears down a CONNECTED session. Presently,
     * there is no mechanism for abandoning an CONNECTING session,
     * but would likely involve cancelling pending async requests or
     * setting a flag or new state to ignore them when they came in
     * @param tearDown true if the underlying DataConnection should be
     * disconnected.
     * @param reason reason for the clean up.
     * @return boolean - true if we did cleanup any connections, false if they
     *                   were already all disconnected.
     */
    protected boolean cleanUpAllConnections(boolean tearDown, String reason) {
        if (DBG) log("cleanUpAllConnections: tearDown=" + tearDown + " reason=" + reason);
        boolean didDisconnect = false;
        boolean specificdisable = false;

        if (!TextUtils.isEmpty(reason)) {
            specificdisable = reason.equals(Phone.REASON_DATA_SPECIFIC_DISABLED);
            // /Ignore IMS PDN deactivation when WFC is On && Radio turned off @{
            if (SystemProperties.get("ro.mtk_flight_mode_power_off_md").equals("0")) {
                specificdisable = specificdisable || reason.equals(Phone.REASON_RADIO_TURNED_OFF);
            }
            // /@}
        }

        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.isDisconnected() == false) didDisconnect = true;
            if (specificdisable) {
                if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                    if (DBG) log("ApnConextType: " + apnContext.getApnType());
                    apnContext.setReason(reason);
                    cleanUpConnection(tearDown, apnContext);
                }
            } else {
                if (reason != null && reason.equals(Phone.REASON_ROAMING_ON)
                        && ignoreDataRoaming(apnContext.getApnType())) {
                    if (DBG) log("cleanUpConnection: Ignore Data Roaming for apnType = "
                            + apnContext.getApnType());
               } else {
                    // TODO - only do cleanup if not disconnected
                    apnContext.setReason(reason);
                    cleanUpConnection(tearDown, apnContext);
                }
            }
        }

        stopNetStatPoll();
        stopDataStallAlarm();

        // TODO: Do we need mRequestedApnType?
        mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;

        log("cleanUpConnection: mDisconnectPendingCount = " + mDisconnectPendingCount);
        if (tearDown && mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }

        return didDisconnect;
    }

    /**
     * Cleanup all connections.
     *
     * TODO: Cleanup only a specified connection passed as a parameter.
     *       Also, make sure when you clean up a conn, if it is last apply
     *       logic as though it is cleanupAllConnections
     *
     * @param cause for the clean up.
     */

    @Override
    protected void onCleanUpAllConnections(String cause) {
        cleanUpAllConnections(true, cause);
    }

    protected void cleanUpConnection(boolean tearDown, ApnContext apnContext) {

        if (apnContext == null) {
            if (DBG) log("cleanUpConnection: apn context is null");
            return;
        }

        DcAsyncChannel dcac = apnContext.getDcAc();
        String str = "cleanUpConnection: tearDown=" + tearDown + " reason=" +
                apnContext.getReason();
        if (DBG) log(str + " apnContext=" + apnContext);
        apnContext.requestLog(str);
        if (tearDown) {
            if (apnContext.isDisconnected()) {
                // The request is tearDown and but ApnContext is not connected.
                // If apnContext is not enabled anymore, break the linkage to the DCAC/DC.
                apnContext.setState(DctConstants.State.IDLE);
                if (!apnContext.isReady()) {
                    if (dcac != null) {
                        str = "cleanUpConnection: teardown, disconnectd, !ready";
                        if (DBG) log(str + " apnContext=" + apnContext);
                        apnContext.requestLog(str);
                        dcac.tearDown(apnContext, "", null);
                    }
                    apnContext.setDataConnectionAc(null);
                }
            } else {
                // Connection is still there. Try to clean up.
                if (dcac != null) {
                    if (apnContext.getState() != DctConstants.State.DISCONNECTING) {
                        boolean disconnectAll = false;
                        if (PhoneConstants.APN_TYPE_DUN.equals(apnContext.getApnType())) {
                            // CAF_MSIM is this below condition required.
                            // if (PhoneConstants.APN_TYPE_DUN.equals(PhoneConstants.APN_TYPE_DEFAULT)) {
                            if (teardownForDun()) {
                                if (DBG) {
                                    log("cleanUpConnection: disconnectAll DUN connection");
                                }
                                // we need to tear it down - we brought it up just for dun and
                                // other people are camped on it and now dun is done.  We need
                                // to stop using it and let the normal apn list get used to find
                                // connections for the remaining desired connections
                                disconnectAll = true;
                            }
                        }
                        str = "cleanUpConnection: tearing down" + (disconnectAll ? " all" : "");
                        if (DBG) log(str + "apnContext=" + apnContext);
                        apnContext.requestLog(str);
                        Message msg = obtainMessage(DctConstants.EVENT_DISCONNECT_DONE, apnContext);
                        if (disconnectAll) {
                            dcac.tearDownAll(apnContext.getReason(), msg);
                        } else {
                            dcac.tearDown(apnContext, apnContext.getReason(), msg);
                        }
                        apnContext.setState(DctConstants.State.DISCONNECTING);
                        mDisconnectPendingCount++;
                    }
                } else {
                    // apn is connected but no reference to dcac.
                    // Should not be happen, but reset the state in case.
                    apnContext.setState(DctConstants.State.IDLE);
                    apnContext.requestLog("cleanUpConnection: connected, bug no DCAC");
                    if (apnContext.isNeedNotify()) {
                        mPhone.notifyDataConnection(apnContext.getReason(),
                                apnContext.getApnType());
                    }
                }
            }
        } else {
            boolean needNotify = true;
            //TODO: remove phone count.
            int phoneCount = TelephonyManager.getDefault().getPhoneCount();
            if (apnContext.isDisconnected() && phoneCount > 2) {
                needNotify = false;
            }
            // force clean up the data connection.
            if (dcac != null) dcac.reqReset();
            apnContext.setState(DctConstants.State.IDLE);
            if (apnContext.isNeedNotify() && needNotify) {
                mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            }
            apnContext.setDataConnectionAc(null);
        }

        // Make sure reconnection alarm is cleaned up if there is no ApnContext
        // associated to the connection.
        if (dcac != null) {
            cancelReconnectAlarm(apnContext);
        }
        str = "cleanUpConnection: X tearDown=" + tearDown + " reason=" + apnContext.getReason();
        if (DBG) log(str + " apnContext=" + apnContext + " dcac=" + apnContext.getDcAc());
        apnContext.requestLog(str);
    }

    /**
     * Determine if DUN connection is special and we need to teardown on start/stop
     */
    private boolean teardownForDun() {
        // CDMA always needs to do this the profile id is correct
        final int rilRat = mPhone.getServiceState().getRilDataRadioTechnology();
        if (ServiceState.isCdma(rilRat)) return true;

        return (fetchDunApn() != null);
    }

    /**
     * Cancels the alarm associated with apnContext.
     *
     * @param apnContext on which the alarm should be stopped.
     */
    private void cancelReconnectAlarm(ApnContext apnContext) {
        if (apnContext == null) return;

        PendingIntent intent = apnContext.getReconnectIntent();

        if (intent != null) {
                AlarmManager am =
                    (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                am.cancel(intent);
                apnContext.setReconnectIntent(null);
        }
    }

    /**
     * @param types comma delimited list of APN types
     * @return array of APN types
     */
    private String[] parseTypes(String types) {
        String[] result;
        // If unset, set to DEFAULT.
        if (types == null || types.equals("")) {
            result = new String[1];
            result[0] = PhoneConstants.APN_TYPE_ALL;
        } else {
            result = types.split(",");
        }
        return result;
    }

    @Override
    protected boolean isPermanentFail(DcFailCause dcFailCause) {
        return (dcFailCause.isPermanentFail() &&
                (mAttached.get() == false || dcFailCause != DcFailCause.SIGNAL_LOST));
    }

    private ApnSetting makeApnSetting(Cursor cursor) {
        String[] types = parseTypes(
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.TYPE)));
        ApnSetting apn = new ApnSetting(
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NUMERIC)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.APN)),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.PROXY))),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PORT)),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSC))),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPROXY))),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPORT)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.USER)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PASSWORD)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.AUTH_TYPE)),
                types,
                /*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.SOURCE_TYPE)),
                /*-END-20151014-add-by-qingtao.wen-for-task745079-*/
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROTOCOL)),
                cursor.getString(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.ROAMING_PROTOCOL)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.CARRIER_ENABLED)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER_BITMASK)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROFILE_ID)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.MODEM_COGNITIVE)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.WAIT_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MTU)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_TYPE)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_MATCH_DATA)));
        return apn;
    }

    private ArrayList<ApnSetting> createApnList(Cursor cursor) {
        ArrayList<ApnSetting> mnoApns = new ArrayList<ApnSetting>();
        ArrayList<ApnSetting> mvnoApns = new ArrayList<ApnSetting>();
        IccRecords r = mIccRecords.get();
      /*-BEGIN-20151014-add-by-qingtao.wen-for-task745095-*/
        boolean use_imsi_to_filter = mPhone.getContext().getResources().getBoolean(com.android.internal.R.bool.def_use_imsi_distinguish_mvno);
      /*-END-20151014-add-by-qingtao.wen-for-task745095-*/
        if (cursor.moveToFirst()) {
            do {
                ApnSetting apn = makeApnSetting(cursor);
                if (apn == null) {
                    continue;
                }

              /*-BEGIN-20151014-add-by-qingtao.wen-for-task745095-*/
                if(use_imsi_to_filter){
                       if (apn.hasMvnoParams() && apn.numeric!=null && !apn.numeric.equals("732103")) {
                           if (r != null && ApnSetting.mvnoMatches(r, apn.mvnoType, apn.mvnoMatchData)) {
                               mvnoApns.add(apn);
                           }
                       } else {
                               if (DBG) log("createApnList: add mno APN");
                           mnoApns.add(apn);
                       }
                }else{                      
                if (apn.hasMvnoParams()) {
                    if (r != null && ApnSetting.mvnoMatches(r, apn.mvnoType, apn.mvnoMatchData)) {
                        mvnoApns.add(apn);
                    }
                } else {
                    mnoApns.add(apn);
                       }
                }
               /*-END-20151014-add-by-qingtao.wen-for-task745095-*/
            } while (cursor.moveToNext());
        }

        ArrayList<ApnSetting> result = mvnoApns.isEmpty() ? mnoApns : mvnoApns;
        if (DBG) log("createApnList: X result=" + result);
        return result;
    }

    private boolean dataConnectionNotInUse(DcAsyncChannel dcac) {
        if (DBG) log("dataConnectionNotInUse: check if dcac is inuse dcac=" + dcac);
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.getDcAc() == dcac) {
                if (DBG) log("dataConnectionNotInUse: in use by apnContext=" + apnContext);
                return false;
            }
        }
        /* To prevent that DataConnection is going to disconnect
        /* and we still need its information, not to do teardown here
        // TODO: Fix retry handling so free DataConnections have empty apnlists.
        // Probably move retry handling into DataConnections and reduce complexity
        // of DCT.
        if (DBG) log("dataConnectionNotInUse: tearDownAll");
        dcac.tearDownAll("No connection", null);
        */
        if (DBG) log("dataConnectionNotInUse: not in use return true");
        return true;
    }

    private DcAsyncChannel findFreeDataConnection(String reqApnType, ApnSetting apnSetting) {
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            if (dcac.isInactiveSync() && dataConnectionNotInUse(dcac)) {
                DcAsyncChannel dcacForTeardown = dcac;
                if (isSupportThrottlingApn()) {
                    for (String apn : HIGH_THROUGHPUT_APN) {
                        if (apnSetting != null && apnSetting.canHandleType(apn)
                                && !PhoneConstants.APN_TYPE_EMERGENCY.equals(reqApnType)
                                && !apnSetting.canHandleType(PhoneConstants.APN_TYPE_IMS)
                                && dcac != null) {
                            int id = dcac.getDataConnectionIdSync();
                            if (id < MIN_ID_HIGH_TROUGHPUT || id > MAX_ID_HIGH_TROUGHPUT) {
                                dcac = null;
                            }
                        }
                    }
                    for (String apn : IMS_APN) {
                        if (PhoneConstants.APN_TYPE_EMERGENCY.equals(apn)
                                && !PhoneConstants.APN_TYPE_EMERGENCY.equals(reqApnType)) {
                            //skip since not request emergency apn
                        } else {
                            if (apnSetting != null && apnSetting.canHandleType(apn)
                                    && dcac != null) {
                                int id = dcac.getDataConnectionIdSync();
                                if (id < MIN_ID_IMS_TROUGHPUT || id > MAX_ID_IMS_TROUGHPUT) {
                                    dcac = null;
                                }
                            }
                        }
                    }
                }

                if (DBG) {
                    log("findFreeDataConnection: tearDownAll is executed on un-used dcac");
                }
                dcacForTeardown.tearDownAll("No connection", null);

                if (dcac != null) {
                    if (DBG) {
                        log("findFreeDataConnection: found free DataConnection="
                                + " dcac=" + dcac);
                    }
                    return dcac;
                }
            }
        }
        log("findFreeDataConnection: NO free DataConnection");
        return null;
    }

    private boolean setupData(ApnContext apnContext, int radioTech) {
        if (DBG) log("setupData: apnContext=" + apnContext);
        apnContext.requestLog("setupData");
        ApnSetting apnSetting;
        DcAsyncChannel dcac = null;

        apnSetting = apnContext.getNextWaitingApn();
        if (apnSetting == null && !apnContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY)) {
            if (DBG) log("setupData: return for no apn found!");
            return false;
        }

        int profileId = apnSetting.profileId;
        if (profileId == 0) {
            profileId = getApnProfileID(apnContext.getApnType());
        }

        // On CDMA, if we're explicitly asking for DUN, we need have
        // a dun-profiled connection so we can't share an existing one
        // On GSM/LTE we can share existing apn connections provided they support
        // this type.
        if (apnContext.getApnType() != PhoneConstants.APN_TYPE_DUN ||
                teardownForDun() == false) {
            dcac = checkForCompatibleConnectedApnContext(apnContext);
            if (dcac != null) {
                // Get the dcacApnSetting for the connection we want to share.
                ApnSetting dcacApnSetting = dcac.getApnSettingSync();
                if (dcacApnSetting != null) {
                    // Setting is good, so use it.
                    apnSetting = dcacApnSetting;
                }
            }
        }
        if (dcac == null) {
            if (isOnlySingleDcAllowed(radioTech)) {
                if (isHigherPriorityApnContextActive(apnContext)) {
                    if (DBG) {
                        log("setupData: Higher priority ApnContext active.  Ignoring call");
                    }
                    return false;
                }

                // Only lower priority calls left.  Disconnect them all in this single PDP case
                // so that we can bring up the requested higher priority call (once we receive
                // repsonse for deactivate request for the calls we are about to disconnect
                if (cleanUpAllConnections(true, Phone.REASON_SINGLE_PDN_ARBITRATION)) {
                    // If any call actually requested to be disconnected, means we can't
                    // bring up this connection yet as we need to wait for those data calls
                    // to be disconnected.
                    if (DBG) log("setupData: Some calls are disconnecting first.  Wait and retry");
                    return false;
                }

                // No other calls are active, so proceed
                if (DBG) log("setupData: Single pdp. Continue setting up data call.");
            }

            /** M: throttling/high throughput APN start **/
            if (!isSupportThrottlingApn() && !isOnlySingleDcAllowed(radioTech)) {
                boolean isHighThroughputApn = false;
                for (String apn : HIGH_THROUGHPUT_APN) {
                    if (apnSetting.canHandleType(apn)) {
                        isHighThroughputApn = true;
                        break;
                    }
                }

                if (!isHighThroughputApn) {
                    boolean lastDcAlreadyInUse = false;
                    for (DcAsyncChannel asyncChannel : mDataConnectionAcHashMap.values()) {
                        if (asyncChannel.getDataConnectionIdSync() == getPdpConnectionPoolSize()) {
                            if (asyncChannel.isInactiveSync() && dataConnectionNotInUse(asyncChannel)) {
                                if (DBG)
                                    log("find the last data connection for non-high-throughput apn");
                                dcac = asyncChannel;

                                if (DBG) {
                                    log("setupData: tearDownAll is executed on un-used dcac");
                                }
                                asyncChannel.tearDownAll("No connection", null);
                            } else {
                                log("the last data connection is already in-use");
                                lastDcAlreadyInUse = true;
                            }
                        }
                    }
                    if (dcac == null && !lastDcAlreadyInUse) {
                        DataConnection conn = DataConnection.makeDataConnection(mPhone, getPdpConnectionPoolSize(),
                                          this, mDcTesterFailBringUpAll, mDcc);
                        mDataConnections.put(getPdpConnectionPoolSize(), conn);
                        dcac = new DcAsyncChannel(conn, LOG_TAG);
                        int status = dcac.fullyConnectSync(mPhone.getContext(), this, conn.getHandler());
                        if (status == AsyncChannel.STATUS_SUCCESSFUL) {
                            log("create the last data connection");
                            mDataConnectionAcHashMap.put(dcac.getDataConnectionIdSync(), dcac);
                        } else {
                            loge("createDataConnection (last): Could not connect to dcac=" + dcac + " status=" + status);
                        }
                    }
                }
            }
            /** M: throttling/high throughput APN end start **/

            if (dcac == null) {
                if (DBG) log("setupData: No ready DataConnection found!");
                // TODO: When allocating you are mapping type to id. If more than 1 free,
                // then could findFreeDataConnection get the wrong one??
                dcac = findFreeDataConnection(apnContext.getApnType(), apnSetting);

            }

            // M: Reuse DCAC if there is remain DCAC for the ApnContext.
            if (dcac == null) {
                if (apnContext.getApnType() == PhoneConstants.APN_TYPE_DEFAULT) {
                    DcAsyncChannel prevDcac = apnContext.getDcAc();
                    // There is already an inactive dcac, try to reuse it.
                    if (prevDcac != null && prevDcac.isInactiveSync()) {
                        dcac = prevDcac;
                        ApnSetting dcacApnSetting = dcac.getApnSettingSync();
                        log("setupData: reuse previous DCAC: dcacApnSetting = "
                                + dcacApnSetting);
                        if (dcacApnSetting != null) {
                            // Setting is good, so use it.
                            apnSetting = dcacApnSetting;
                        }
                    }
                }
            }

            if (dcac == null) {
                dcac = createDataConnection(apnContext.getApnType(), apnSetting);
            }

            if (dcac == null) {
                if (DBG) log("setupData: No free DataConnection and couldn't create one, WEIRD");
                return false;
            }
        }
        if (DBG) log("setupData: dcac=" + dcac + " apnSetting=" + apnSetting);

        // M: [epdg] query if handover
        if (EPDG_FEATURE) {
            checkEpdgHandover();
        }

        apnContext.setDataConnectionAc(dcac);
        apnContext.setApnSetting(apnSetting);
        apnContext.setState(DctConstants.State.CONNECTING);
        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());

        //MTK START
        if (handleApnConflict(apnContext, apnSetting)) {
            return false;
        }
        //MTK STOP

        Message msg = obtainMessage();
        msg.what = DctConstants.EVENT_DATA_SETUP_COMPLETE;
        msg.obj = apnContext;

        dcac.bringUp(apnContext, getInitialMaxRetry(), profileId, radioTech,
                mAutoAttachOnCreation.get(), msg);

        if (DBG) log("setupData: initing!");
        return true;
    }

    /**
     * Handles changes to the APN database.
     */
    private void onApnChanged() {
        if (mPhone instanceof GSMPhone) {
            // The "current" may no longer be valid.  MMS depends on this to send properly. TBD
            ((GSMPhone)mPhone).updateCurrentCarrierInProvider();
        }

        /** M: onApnChanged optimization
         *  keep current settings before create new apn list
         */
        ArrayList<ApnSetting> prevAllApns = mAllApnSettings;
        ApnSetting prevPreferredApn = mPreferredApn;
        if (DBG) log("onApnChanged: createAllApnList and set initial attach APN");
        createAllApnList();

        ApnSetting previousAttachApn = mInitialAttachApnSetting;

        /// M: we will do nothing if the apn is not changed or only the APN name
        /// is changed. Generally speaking, if PreferredApn and AttachApns are
        /// both not changed, it will be considered that APN not changed. But if both
        /// of them are not changed but any of them is null, then we double confirm it
        /// by compare preAllApns and curAllApns.
        final String prevPreferredApnString = apnToStringIgnoreName(prevPreferredApn);
        final String curPreferredApnString = apnToStringIgnoreName(mPreferredApn);
        final String prevAttachApnSettingString = apnToStringIgnoreName(previousAttachApn);
        final String curAttachApnSettingString = apnToStringIgnoreName(mInitialAttachApnSetting);
        if (TextUtils.equals(prevPreferredApnString, curPreferredApnString)
                && TextUtils.equals(prevAttachApnSettingString, curAttachApnSettingString)) {
            // If preferred APN or preferred initial APN is null, we need to check all APNs.
            if ((prevPreferredApnString == null || prevAttachApnSettingString == null)
                    && !TextUtils.equals(apnsToStringIgnoreName(prevAllApns),
                            apnsToStringIgnoreName(mAllApnSettings))) {
                log("onApnChanged: all APN setting changed.");
            } else {
                if (MTK_IMS_SUPPORT) {
                    if (isIMSApnSettingChanged(prevAllApns, mAllApnSettings)) {
                        sendOnApnChangedDone(true);
                        log("onApnChanged: IMS apn setting changed!!");
                        return;
                    }
                }
                log("onApnChanged: not changed, preferredApn = " + prevPreferredApnString);
                return;
            }
        }

        IccRecords r = mIccRecords.get();
        String operator = (r != null) ? r.getOperatorNumeric() : "";
        if (operator != null && operator.length() > 0) {
            // M: update initial attach APN for SVLTE since SVLTE use specific
            // APN for initial attach.
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                updateInitialAttachApnForSvlte();
            }
            setInitialAttachApn();
        } else {
            if (DBG) {
                log("onApnChanged: but no operator numeric");
            }
        }

        if (DBG) log("onApnChanged: cleanUpAllConnections and setup connectable APN");
        sendOnApnChangedDone(false);
    }

    private void sendOnApnChangedDone(boolean bImsApnChanged) {
        Message msg = obtainMessage(DctConstants.EVENT_APN_CHANGED_DONE);
        msg.arg1 = bImsApnChanged ? 1 : 0;
        sendMessage(msg);
    }

    private void onApnChangedDone() {
        //Fixed:[ALPS01670132] Data iocn cannot shows and data service cannot work after change default APN some times.
        DctConstants.State overallState = getOverallState();
        boolean isDisconnected = (overallState == DctConstants.State.IDLE ||
                overallState == DctConstants.State.FAILED);

        // match the current operator.
        if (DBG) {
            log("onApnChanged: createAllApnList and cleanUpAllConnections: isDisconnected = "
                    + isDisconnected);
        }

        cleanUpConnectionsOnUpdatedApns(!isDisconnected);

        if (DBG) {
            log("onApnChanged: phone.getsubId=" + mPhone.getSubId() + "getDefaultDataSubId()" +
                    + SubscriptionManager.getDefaultDataSubId());
        }
        // FIXME: See bug 17426028 maybe no conditional is needed.
        int phoneSubId = mPhone.getSubId();
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneSubId = SvlteUtils.getSvlteSubIdBySubId(phoneSubId);
        }

        if (phoneSubId == SubscriptionManager.getDefaultDataSubId()) {
            setupDataOnConnectableApns(Phone.REASON_APN_CHANGED);
        }
    }

    /**
     * @param cid Connection id provided from RIL.
     * @return DataConnectionAc associated with specified cid.
     */
    private DcAsyncChannel findDataConnectionAcByCid(int cid) {
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            if (dcac.getCidSync() == cid) {
                return dcac;
            }
        }
        return null;
    }

    // TODO: For multiple Active APNs not exactly sure how to do this.
    @Override
    protected void gotoIdleAndNotifyDataConnection(String reason) {
        if (DBG) log("gotoIdleAndNotifyDataConnection: reason=" + reason);
        notifyDataConnection(reason);
        mActiveApn = null;
    }

    /**
     * "Active" here means ApnContext isEnabled() and not in FAILED state
     * @param apnContext to compare with
     * @return true if higher priority active apn found
     */
    private boolean isHigherPriorityApnContextActive(ApnContext apnContext) {
        for (ApnContext otherContext : mPrioritySortedApnContexts) {
            if (apnContext.getApnType().equalsIgnoreCase(otherContext.getApnType())) return false;
            if (otherContext.isEnabled() && otherContext.getState() != DctConstants.State.FAILED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reports if we support multiple connections or not.
     * This is a combination of factors, based on carrier and RAT.
     * @param rilRadioTech the RIL Radio Tech currently in use
     * @return true if only single DataConnection is allowed
     */
    private boolean isOnlySingleDcAllowed(int rilRadioTech) {
        int[] singleDcRats = mPhone.getContext().getResources().getIntArray(
                com.android.internal.R.array.config_onlySingleDcAllowed);
        boolean onlySingleDcAllowed = false;

        // MTK START [ALPS01540105]
        if (!BSP_PACKAGE) {
            try {
                ITelephonyExt telExt =
                    MPlugin.createInstance(ITelephonyExt.class.getName(), mPhone.getContext());
                onlySingleDcAllowed = telExt.isOnlySingleDcAllowed(); // default is false
                if (DBG) {
                    log("isOnlySingleDcAllowed: " + onlySingleDcAllowed);
                }
                if (onlySingleDcAllowed == true) {
                    return true;
                }
            } catch (NullPointerException e) {
                loge("Fail to create or use plug-in");
                e.printStackTrace();
            }
        }
        // MTK END [ALPS01540105]

        if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
            if ((SystemProperties.getInt("ril.external.md", 0) - 1) == mPhone.getPhoneId()) {
                // M:[C2K] Only apply this for non SVLTE project, because the
                // phone will change to GSM phone in roaming for SVLTE and phone
                // ID matches the case, but it may support mulitple PDP.
                if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    if (DBG) {
                        log("isOnlySingleDcAllowed: external modem");
                    }
                    return true;
                }
            }
        }

        if (Build.IS_DEBUGGABLE &&
                SystemProperties.getBoolean("persist.telephony.test.singleDc", false)) {
            onlySingleDcAllowed = true;
        }
        if (singleDcRats != null) {
            for (int i=0; i < singleDcRats.length && onlySingleDcAllowed == false; i++) {
                if (rilRadioTech == singleDcRats[i]) onlySingleDcAllowed = true;
            }
        }

        if (DBG) log("isOnlySingleDcAllowed(" + rilRadioTech + "): " + onlySingleDcAllowed);
        return onlySingleDcAllowed;
    }

    @Override
    protected void restartRadio() {
        if (DBG) log("restartRadio: ************TURN OFF RADIO**************");
        cleanUpAllConnections(true, Phone.REASON_RADIO_TURNED_OFF);
        mPhone.getServiceStateTracker().powerOffRadioSafely(this);
        /* Note: no need to call setRadioPower(true).  Assuming the desired
         * radio power state is still ON (as tracked by ServiceStateTracker),
         * ServiceStateTracker will call setRadioPower when it receives the
         * RADIO_STATE_CHANGED notification for the power off.  And if the
         * desired power state has changed in the interim, we don't want to
         * override it with an unconditional power on.
         */

        int reset = Integer.parseInt(SystemProperties.get("net.ppp.reset-by-timeout", "0"));
        SystemProperties.set("net.ppp.reset-by-timeout", String.valueOf(reset+1));
    }

    /**
     * Return true if data connection need to be setup after disconnected due to
     * reason.
     *
     * @param reason the reason why data is disconnected
     * @return true if try setup data connection is need for this reason
     */
    private boolean retryAfterDisconnected(ApnContext apnContext) {
        boolean retry = true;
        String reason = apnContext.getReason();

        if ( Phone.REASON_RADIO_TURNED_OFF.equals(reason) ||
                Phone.REASON_FDN_ENABLED.equals(reason) ||
                (isOnlySingleDcAllowed(mPhone.getServiceState().getRilDataRadioTechnology())
                 && isHigherPriorityApnContextActive(apnContext))) {
            retry = false;
        }
        return retry;
    }

    private void startAlarmForReconnect(int delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();

        Intent intent = new Intent(INTENT_RECONNECT_ALARM + "." + apnType);
        intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON, apnContext.getReason());
        intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE, apnType);
        //intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, getSubId()); // M: add sub information
        int subId = SubscriptionManager.getDefaultDataSubId();
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);

        if (DBG) {
            log("startAlarmForReconnect: delay=" + delay + " action=" + intent.getAction()
                    + " apn=" + apnContext);
        }

        PendingIntent alarmIntent = PendingIntent.getBroadcast (mPhone.getContext(), 0,
                                        intent, PendingIntent.FLAG_UPDATE_CURRENT);
        apnContext.setReconnectIntent(alarmIntent);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, alarmIntent);
    }

    private void startAlarmForRestartTrySetup(int delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent(INTENT_RESTART_TRYSETUP_ALARM + "." + apnType);
        intent.putExtra(INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE, apnType);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, getSubId()); // M: add sub information

        if (DBG) {
            log("startAlarmForRestartTrySetup: delay=" + delay + " action=" + intent.getAction()
                    + " apn=" + apnContext);
        }
        PendingIntent alarmIntent = PendingIntent.getBroadcast (mPhone.getContext(), 0,
                                        intent, PendingIntent.FLAG_UPDATE_CURRENT);
        apnContext.setReconnectIntent(alarmIntent);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, alarmIntent);
    }

    private void notifyNoData(DcFailCause lastFailCauseCode,
                              ApnContext apnContext) {
        if (DBG) log( "notifyNoData: type=" + apnContext.getApnType());
        if (isPermanentFail(lastFailCauseCode)
            && (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT))) {
            mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
        }
    }

    private void onRecordsLoaded() {
        if (DBG) log("onRecordsLoaded: createAllApnList");
        mAutoAttachOnCreationConfig = mPhone.getContext().getResources()
                .getBoolean(com.android.internal.R.bool.config_auto_attach_data_on_creation);

        //M: resolve tethering on and after flight mode off, rild initial will auto-start FD
        if (mFdMgr != null) {
            mFdMgr.disableFdWhenTethering();
        }
        // M: cc33.
        if (isCctSmRetry()) {
            mPhone.mCi.setRemoveRestrictEutranMode(true, null);
            mPhone.mCi.setDataOnToMD(mUserDataEnabled, null);
        }

        mAutoAttachOnCreationConfig = true;
        createAllApnList();
        setInitialAttachApn();
        if (mPhone.mCi.getRadioState().isOn()) {
            if (DBG) log("onRecordsLoaded: notifying data availability");
            notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
        }

        boolean bGetDataCallList = true;
        /*
        if (phoneCount > 1) {
            boolean bDataEnabled = getAnyDataEnabled();
            boolean bIsDualTalkMode = PhoneFactory.isDualTalkMode();
            log("Data Setting on SIM: " + gprsDefaultSIM + ", current SIM Slot Id: " + nSlotId +
                " Data enabled: " + bDataEnabled + " DualTalkMode: " + bIsDualTalkMode);
            if (!bIsDualTalkMode && bDataEnabled && gprsDefaultSIM != nSlotId) {
                bGetDataCallList = false;
                // Gemini support (not dual talk) and still have data enabled, don't update data call list
            }
        }
        */
        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            bGetDataCallList = false;
        }

        // M: for IRAT start
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && SvlteUtils.isActiveSvlteMode(mPhone)
                && mSetupDataBlocked) {
            bGetDataCallList = false;
        }
        // M: for IRAT end

        if (bGetDataCallList) {
            mDcc.getDataCallListForSimLoaded();
        } else {
            sendMessage(obtainMessage(DctConstants.EVENT_SETUP_DATA_WHEN_LOADED));
        }

    }

    //MTK START: FDN Support

    private boolean isFdnEnableSupport() {
        boolean isFdnEnableSupport = false;
		/*Begin zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
       /*if (!BSP_PACKAGE && mGsmDctExt != null) {
            isFdnEnableSupport = mGsmDctExt.isFdnEnableSupport();
        }*/
        if((!mIsHideFDN) && //20151029-yangning.hong-add-for-task742145
            !mPhone.getContext().getResources().getBoolean(
                com.android.internal.R.bool.def_frameworks_allow_data_connection_with_FDN_activated) ) {
            isFdnEnableSupport = true;
        }
	   /*End zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/	
        return isFdnEnableSupport;
    }

    private boolean isFdnEnabled() {
        boolean bIsFdnEnabled = false;
        if (isFdnEnableSupport()) {
            bIsFdnEnabled = getFdnStatus();
        }
        return bIsFdnEnabled;
    }

    private boolean getFdnStatus() {
        boolean bIsFdnEnabled = false;
        ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));

        if (telephonyEx != null) {
            try {
                bIsFdnEnabled = telephonyEx.isFdnEnabled(mPhone.getSubId());
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        } else {
            loge("getFdnStatus get telephonyEx failed!!");
        }

        return bIsFdnEnabled;
    }

    private void onFdnChanged() {
        if (isFdnEnableSupport()) {
            log("onFdnChanged");
            if (getFdnStatus()) {
			/*Begin zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
               /* log("fdn enabled, cleanUpAllConnections!");
                cleanUpAllConnections(true, Phone.REASON_FDN_ENABLED);*/
                sendMessageDelayed(obtainMessage(DctConstants.EVENT_QUERY_FDN_LIST), 2000);
			/*End zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
            } else {
                log("fdn disabled, setupDataOnConnectableApns!");
                setupDataOnConnectableApns(Phone.REASON_FDN_DISABLED);
            }
        } else {
            log("not support fdn enabled, skip onFdnChanged");
        }
    }
    //MTK END: Support FDN

	/*Begin zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
    private void queryFdnList() {
        log("queryFDNList subId= " + mPhone.getSubId());
        int phoneSubId = mPhone.getSubId();
        if (SubscriptionManager.isValidSubscriptionId(phoneSubId)) {
            mHasStar99Pound = false;
            Uri mFdnUri = Uri.parse("content://icc/fdn/subId/" + phoneSubId);
            if (mFdnUri != null && mLocalQueryHandler != null) {
                mLocalQueryHandler.startQuery(0, null, mFdnUri, COLUMN_NAMES, null, null, null);
            }
        }
    }
    private class LocalQueryHandler extends AsyncQueryHandler {
        public LocalQueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (null != cursor) {
                log("fdn cusor count " + cursor.getCount());
                try {
                    while (cursor.moveToNext()) {
                        String number = cursor.getString(cursor.getColumnIndex("number"));
                        if (number != null && number.equals("*99#")) {
                            log("fdn enabled and has *99#");
                            mHasStar99Pound = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    log("error def_frameworks_allow_data_connection_with_FDN_activated onQueryComplete");
                } finally {
                    cursor.close();
                }
            }
            if (mHasStar99Pound) {
                log("has *99# number, setupDataOnConnectableApns");
                setupDataOnConnectableApns(Phone.REASON_FDN_DISABLED);
            } else {
                log("*99# number is not in FDN list, cleanUpAllConnections");
                cleanUpAllConnections(true, Phone.REASON_FDN_ENABLED);
            }
        }
    }
	/*End zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/

    private void onSimNotReady() {
        if (DBG) log("onSimNotReady");

        cleanUpAllConnections(true, Phone.REASON_SIM_NOT_READY);
        if (mAllApnSettings != null) {
            mAllApnSettings.clear();
        }
        mAutoAttachOnCreationConfig = false;
    }

    @Override
    protected void onSetDependencyMet(String apnType, boolean met) {
        // don't allow users to tweak hipri to work around default dependency not met
        if (PhoneConstants.APN_TYPE_HIPRI.equals(apnType)) return;

        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            loge("onSetDependencyMet: ApnContext not found in onSetDependencyMet(" +
                    apnType + ", " + met + ")");
            return;
        }
        applyNewState(apnContext, apnContext.isEnabled(), met);
        if (PhoneConstants.APN_TYPE_DEFAULT.equals(apnType)) {
            // tie actions on default to similar actions on HIPRI regarding dependencyMet
            apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_HIPRI);
            if (apnContext != null) applyNewState(apnContext, apnContext.isEnabled(), met);
        }
    }

    private void applyNewState(ApnContext apnContext, boolean enabled, boolean met) {
        boolean cleanup = false;
        boolean trySetup = false;
        String str ="applyNewState(" + apnContext.getApnType() + ", " + enabled +
                "(" + apnContext.isEnabled() + "), " + met + "(" +
                apnContext.getDependencyMet() +"))";
        if (DBG) log(str);
        apnContext.requestLog(str);

        if (apnContext.isReady()) {
            cleanup = true;
            if (enabled && met) {
                DctConstants.State state = apnContext.getState();
                switch(state) {
                    case CONNECTING:
                    case SCANNING:
                    case CONNECTED:
                    case DISCONNECTING:
                        // We're "READY" and active so just return
                        if (DBG) log("applyNewState: 'ready' so return");
                        apnContext.requestLog("applyNewState state=" + state + ", so return");
                        return;
                    case IDLE:
                        // fall through: this is unexpected but if it happens cleanup and try setup
                    case FAILED:
                    case RETRYING: {
                        // We're "READY" but not active so disconnect (cleanup = true) and
                        // connect (trySetup = true) to be sure we retry the connection.
                        trySetup = true;
                        apnContext.setReason(Phone.REASON_DATA_ENABLED);
                        break;
                    }
                }
            //TODO: Need handle dependency met and data not enable case
            } else if (!enabled) {
                cleanup = true;
                if (Phone.REASON_QUERY_PLMN.equals(mSetDataAllowedReason)) {
                    apnContext.setReason(Phone.REASON_QUERY_PLMN);
                } else {
                    apnContext.setReason(Phone.REASON_DATA_DISABLED);
                }
/*
            } else if (met) {
                apnContext.setReason(Phone.REASON_DATA_DISABLED);
                // If ConnectivityService has disabled this network, stop trying to bring
                // it up, but do not tear it down - ConnectivityService will do that
                // directly by talking with the DataConnection.
                //
                // This doesn't apply to DUN, however.  Those connections have special
                // requirements from carriers and we need stop using them when the dun
                // request goes away.  This applies to both CDMA and GSM because they both
                // can declare the DUN APN sharable by default traffic, thus still satisfying
                // those requests and not torn down organically.
                if (apnContext.getApnType() == PhoneConstants.APN_TYPE_DUN && teardownForDun()) {
                    cleanup = true;
                } else {
                    cleanup = false;
                }
*/
            } else {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_UNMET);
            }
        } else {
            if (enabled && met) {
                if (apnContext.isEnabled()) {
                    apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_MET);
                } else {
                    apnContext.setReason(Phone.REASON_DATA_ENABLED);
                }
                if (apnContext.getState() == DctConstants.State.FAILED) {
                    apnContext.setState(DctConstants.State.IDLE);
                }
                trySetup = true;
            }
        }
        apnContext.setEnabled(enabled);
        apnContext.setDependencyMet(met);
        // M:[C2K][IRAT] Block clean up connection and setup data call, only
        // apply the new state, the clean up and setup process will be done
        // later when IRAT end by setupDataOnConnectableApns().
        if (mSetupDataBlocked) {
            log("applyNewState: block during IRAT: apnContext = " + apnContext);
            return;
        }

        if (cleanup) cleanUpConnection(true, apnContext);
        if (trySetup) {
            apnContext.resetErrorCodeRetries();
            trySetupData(apnContext);
        }
    }

    private DcAsyncChannel checkForCompatibleConnectedApnContext(ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        ApnSetting dunSetting = null;
        boolean bIsRequestApnTypeEmergency = (PhoneConstants.APN_TYPE_EMERGENCY.equals(apnType)) ? true : false;

        if (PhoneConstants.APN_TYPE_DUN.equals(apnType)) {
            dunSetting = fetchDunApn();
        }
        if (DBG) {
            log("checkForCompatibleConnectedApnContext: apnContext=" + apnContext );
        }

        DcAsyncChannel potentialDcac = null;
        ApnContext potentialApnCtx = null;
        for (ApnContext curApnCtx : mApnContexts.values()) {
            DcAsyncChannel curDcac = curApnCtx.getDcAc();
            if (curDcac != null) {
                ApnSetting apnSetting = curApnCtx.getApnSetting();
                log("apnSetting: " + apnSetting);
                if (dunSetting != null) {
                    if (dunSetting.equals(apnSetting)) {
                        switch (curApnCtx.getState()) {
                            case CONNECTED:
                                if (DBG) {
                                    log("checkForCompatibleConnectedApnContext:"
                                            + " found dun conn=" + curDcac
                                            + " curApnCtx=" + curApnCtx);
                                }
                                return curDcac;
                            case RETRYING:
                            case CONNECTING:
                            case SCANNING:
                                potentialDcac = curDcac;
                                potentialApnCtx = curApnCtx;
                            default:
                                // Not connected, potential unchanged
                                break;
                        }
                    }
                } else if (apnSetting != null && apnSetting.canHandleType(apnType)) {
                    boolean bIsSkip = false;
                    if (bIsRequestApnTypeEmergency) {
                        if (!PhoneConstants.APN_TYPE_EMERGENCY.equals(curApnCtx.getApnType())) {
                            if (DBG) {
                                    log("checkForCompatibleConnectedApnContext:"
                                    + " found canHandle conn=" + curDcac
                                    + " curApnCtx=" + curApnCtx + ", but not emergency type (skip)");
                            }
                            bIsSkip = true;
                        }
                    }
                    switch (curApnCtx.getState()) {
                        case CONNECTED:
                            if (bIsSkip) break;
                            if (DBG) {
                                log("checkForCompatibleConnectedApnContext:"
                                        + " found canHandle conn=" + curDcac
                                        + " curApnCtx=" + curApnCtx);
                            }
                            return curDcac;
                        case RETRYING:
                        case CONNECTING:
                        case SCANNING:
                            if (bIsSkip) break;
                            potentialDcac = curDcac;
                            potentialApnCtx = curApnCtx;
                        default:
                            // Not connected, potential unchanged
                            break;
                    }
                }
            } else {
                if (VDBG) {
                    log("checkForCompatibleConnectedApnContext: not conn curApnCtx=" + curApnCtx);
                }
            }
        }
        if (potentialDcac != null) {
            if (DBG) {
                log("checkForCompatibleConnectedApnContext: found potential conn=" + potentialDcac
                        + " curApnCtx=" + potentialApnCtx);
            }
            return potentialDcac;
        }

        if (DBG) log("checkForCompatibleConnectedApnContext: NO conn apnContext=" + apnContext);
        return null;
    }

    @Override
    protected void onEnableApn(int apnId, int enabled) {
        ApnContext apnContext = mApnContexts.get(apnIdToType(apnId));
        if (apnContext == null) {
            loge("onEnableApn(" + apnId + ", " + enabled + "): NO ApnContext");
            return;
        }
        // TODO change our retry manager to use the appropriate numbers for the new APN
        if (DBG) log("onEnableApn: apnContext=" + apnContext + " call applyNewState");
        applyNewState(apnContext, enabled == DctConstants.ENABLED, apnContext.getDependencyMet());
    }

    @Override
    // TODO: We shouldnt need this.
    protected boolean onTrySetupData(String reason) {
        if (DBG) log("onTrySetupData: reason=" + reason);
        setupDataOnConnectableApns(reason);
        return true;
    }

    protected boolean onTrySetupData(ApnContext apnContext) {
        if (DBG) log("onTrySetupData: apnContext=" + apnContext);
        return trySetupData(apnContext);
    }

    @Override
    protected void onRoamingOff() {
        if (DBG) log("onRoamingOff mDataRoamingEnabled=" + mDataRoamingEnabled
                + ", mUserDataEnabled=" + mUserDataEnabled);

        if (!mUserDataEnabled) return;

        if (!getDataOnRoamingEnabled()) { //zhiyuan 20151118 changed here for defect513170,Pixi4_4
            notifyOffApnsOfAvailability(Phone.REASON_ROAMING_OFF);
            setupDataOnConnectableApns(Phone.REASON_ROAMING_OFF);
        } else {
            notifyDataConnection(Phone.REASON_ROAMING_OFF);
        }
    }

    @Override
    protected void onRoamingOn() {
        if (DBG) log("onRoamingOn mDataRoamingEnabled=" + mDataRoamingEnabled
                + ", mUserDataEnabled=" + mUserDataEnabled);

        if (!mUserDataEnabled) return;

        if (getDataOnRoamingEnabled()) {//zhiyuan 20151118 changed here for defect513170,Pixi4_4
            if (DBG) log("onRoamingOn: setup data on roaming");
            setupDataOnConnectableApns(Phone.REASON_ROAMING_ON);
            notifyDataConnection(Phone.REASON_ROAMING_ON);
        } else {
            if (DBG) log("onRoamingOn: Tear down data connection on roaming.");
            cleanUpAllConnections(true, Phone.REASON_ROAMING_ON);
            notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
        }
    }

    @Override
    protected void onRadioAvailable() {
        if (DBG) log("onRadioAvailable");
        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            // setState(DctConstants.State.CONNECTED);
            notifyDataConnection(null);

            log("onRadioAvailable: We're on the simulator; assuming data is connected");
        }

        IccRecords r = mIccRecords.get();
        if (r != null && r.getRecordsLoaded()) {
            notifyOffApnsOfAvailability(null);
        }

        if (getOverallState() != DctConstants.State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    @Override
    protected void onRadioOffOrNotAvailable() {
        // Make sure our reconnect delay starts at the initial value
        // next time the radio comes on

        mReregisterOnReconnectFailure = false;

        // to make sure the attach state is correct
        mAttached.set(false);
        mAutoAttachOnCreation.set(false);

        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            log("We're on the simulator; assuming radio off is meaningless");
            notifyOffApnsOfAvailability(null);
        } else {
            if (DBG) log("onRadioOffOrNotAvailable: is off and clean up all connections");
            cleanUpAllConnections(false, Phone.REASON_RADIO_TURNED_OFF);
        }
        //ALPS01769896: We don't notify off twice.
    }

    @Override
    protected void completeConnection(ApnContext apnContext) {
        boolean isProvApn = apnContext.isProvisioningApn();

        if (DBG) log("completeConnection: successful, notify the world apnContext=" + apnContext);

        if (mIsProvisioning && !TextUtils.isEmpty(mProvisioningUrl)) {
            if (DBG) {
                log("completeConnection: MOBILE_PROVISIONING_ACTION url="
                        + mProvisioningUrl);
            }
            Intent newIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_BROWSER);
            newIntent.setData(Uri.parse(mProvisioningUrl));
            newIntent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT |
                    Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                mPhone.getContext().startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                loge("completeConnection: startActivityAsUser failed" + e);
            }
        }
        mIsProvisioning = false;
        mProvisioningUrl = null;
        if (mProvisioningSpinner != null) {
            sendMessage(obtainMessage(DctConstants.CMD_CLEAR_PROVISIONING_SPINNER,
                    mProvisioningSpinner));
        }

        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        startNetStatPoll();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
    }

    /**
     * A SETUP (aka bringUp) has completed, possibly with an error. If
     * there is an error this method will call {@link #onDataSetupCompleteError}.
     */
    @Override
    protected void onDataSetupComplete(AsyncResult ar) {

        DcFailCause cause = DcFailCause.UNKNOWN;
        boolean handleError = false;
        ApnContext apnContext = null;

        if(ar.userObj instanceof ApnContext){
            apnContext = (ApnContext)ar.userObj;
        } else {
            throw new RuntimeException("onDataSetupComplete: No apnContext");
        }

        if (ar.exception == null) {
            DcAsyncChannel dcac = apnContext.getDcAc();

            if (RADIO_TESTS) {
                // Note: To change radio.test.onDSC.null.dcac from command line you need to
                // adb root and adb remount and from the command line you can only change the
                // value to 1 once. To change it a second time you can reboot or execute
                // adb shell stop and then adb shell start. The command line to set the value is:
                // adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "insert into system (name,value) values ('radio.test.onDSC.null.dcac', '1');"
                ContentResolver cr = mPhone.getContext().getContentResolver();
                String radioTestProperty = "radio.test.onDSC.null.dcac";
                if (Settings.System.getInt(cr, radioTestProperty, 0) == 1) {
                    log("onDataSetupComplete: " + radioTestProperty +
                            " is true, set dcac to null and reset property to false");
                    dcac = null;
                    Settings.System.putInt(cr, radioTestProperty, 0);
                    log("onDataSetupComplete: " + radioTestProperty + "=" +
                            Settings.System.getInt(mPhone.getContext().getContentResolver(),
                                    radioTestProperty, -1));
                }
            }
            if (dcac == null) {
                log("onDataSetupComplete: no connection to DC, handle as error");
                cause = DcFailCause.CONNECTION_TO_DATACONNECTIONAC_BROKEN;
                //M
                apnContext.setState(DctConstants.State.FAILED);
                handleError = true;
            } else {
                ApnSetting apn = apnContext.getApnSetting();
                if (DBG) {
                    log("onDataSetupComplete: success apn=" + (apn == null ? "unknown" : apn.apn));
                }
                if (apn != null && apn.proxy != null && apn.proxy.length() != 0) {
                    try {
                        String port = apn.port;
                        if (TextUtils.isEmpty(port)) port = "8080";
                        ProxyInfo proxy = new ProxyInfo(apn.proxy,
                                Integer.parseInt(port), null);
                        dcac.setLinkPropertiesHttpProxySync(proxy);
                    } catch (NumberFormatException e) {
                        loge("onDataSetupComplete: NumberFormatException making ProxyProperties (" +
                                apn.port + "): " + e);
                    }
                }

                // everything is setup
                if(TextUtils.equals(apnContext.getApnType(),PhoneConstants.APN_TYPE_DEFAULT)) {
                    SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "true");
                    if (mCanSetPreferApn && mPreferredApn == null) {
                        if (DBG) log("onDataSetupComplete: PREFERED APN is null");
                        mPreferredApn = apn;
                        if (mPreferredApn != null) {
                            setPreferredApn(mPreferredApn.id);
                        }
                    }

                    // M: Deactivate link down PDN for CT's requirement
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()
                            && SvlteUtils.isActiveSvlteMode(mPhone)
                            && mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                        deactivateLinkDownPdn(mPhone);
                    }
                } else {
                    SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                }

                // A connection is setup
                apnContext.setState(DctConstants.State.CONNECTED);
                boolean isProvApn = apnContext.isProvisioningApn();
                final ConnectivityManager cm = ConnectivityManager.from(mPhone.getContext());
                if (mProvisionBroadcastReceiver != null) {
                    mPhone.getContext().unregisterReceiver(mProvisionBroadcastReceiver);
                    mProvisionBroadcastReceiver = null;
                }
                if ((!isProvApn) || mIsProvisioning) {
                    // Hide any provisioning notification.
                    cm.setProvisioningNotificationVisible(false, ConnectivityManager.TYPE_MOBILE,
                            mProvisionActionName);
                    // Complete the connection normally notifying the world we're connected.
                    // We do this if this isn't a special provisioning apn or if we've been
                    // told its time to provision.
                    completeConnection(apnContext);
                } else {
                    // This is a provisioning APN that we're reporting as connected. Later
                    // when the user desires to upgrade this to a "default" connection,
                    // mIsProvisioning == true, we'll go through the code path above.
                    // mIsProvisioning becomes true when CMD_ENABLE_MOBILE_PROVISIONING
                    // is sent to the DCT.
                    if (DBG) {
                        log("onDataSetupComplete: successful, BUT send connected to prov apn as"
                                + " mIsProvisioning:" + mIsProvisioning + " == false"
                                + " && (isProvisioningApn:" + isProvApn + " == true");
                    }

                    // While radio is up, grab provisioning URL.  The URL contains ICCID which
                    // disappears when radio is off.
                    mProvisionBroadcastReceiver = new ProvisionNotificationBroadcastReceiver(
                            cm.getMobileProvisioningUrl(),
                            TelephonyManager.getDefault().getNetworkOperatorName());
                    mPhone.getContext().registerReceiver(mProvisionBroadcastReceiver,
                            new IntentFilter(mProvisionActionName));
                    // Put up user notification that sign-in is required.
                    cm.setProvisioningNotificationVisible(true, ConnectivityManager.TYPE_MOBILE,
                            mProvisionActionName);
                    // Turn off radio to save battery and avoid wasting carrier resources.
                    // The network isn't usable and network validation will just fail anyhow.
                    setRadio(false);

                    Intent intent = new Intent(
                            TelephonyIntents.ACTION_DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN);
                    intent.putExtra(PhoneConstants.DATA_APN_KEY, apnContext.getApnSetting().apn);
                    intent.putExtra(PhoneConstants.DATA_APN_TYPE_KEY, apnContext.getApnType());

                    String apnType = apnContext.getApnType();
                    LinkProperties linkProperties = getLinkProperties(apnType);
                    if (linkProperties != null) {
                        intent.putExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY, linkProperties);
                        String iface = linkProperties.getInterfaceName();
                        if (iface != null) {
                            intent.putExtra(PhoneConstants.DATA_IFACE_NAME_KEY, iface);
                        }
                    }
                    NetworkCapabilities networkCapabilities = getNetworkCapabilities(apnType);
                    if (networkCapabilities != null) {
                        intent.putExtra(PhoneConstants.DATA_NETWORK_CAPABILITIES_KEY,
                                networkCapabilities);
                    }

                    // M: add sub information
                    intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, getSubId());

                    mPhone.getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
                }
                if (DBG) {
                    log("onDataSetupComplete: SETUP complete type=" + apnContext.getApnType()
                        + ", reason:" + apnContext.getReason());
                }
            }
        } else {
            cause = (DcFailCause) (ar.result);
            if (DBG) {
                ApnSetting apn = apnContext.getApnSetting();
                log(String.format("onDataSetupComplete: error apn=%s cause=%s",
                        (apn == null ? "unknown" : apn.apn), cause));
            }
            if (cause.isEventLoggable()) {
                // Log this failure to the Event Logs.
                int cid = getCellLocationId();
                EventLog.writeEvent(EventLogTags.PDP_SETUP_FAIL,
                        cause.ordinal(), cid, TelephonyManager.getDefault().getNetworkType());
            }
            ApnSetting apn = apnContext.getApnSetting();
            mPhone.notifyPreciseDataConnectionFailed(apnContext.getReason(),
                    apnContext.getApnType(), apn != null ? apn.apn : "unknown", cause.toString());

            //begin zhiyuan 20151029 add isdm persist.data.cc33.support ,Task&30295
            if (isCctSmRetry()) {
                clearCodes33Or29Warning(cause);
            }
            //End zhiyuan 20151029 add isdm persist.data.cc33.support ,Task&30295
            // Count permanent failures and remove the APN we just tried
            if (isPermanentFail(cause) ||
                    (mGsmDctExt != null && mGsmDctExt.isIgnoredCause(cause))) {
                apnContext.decWaitingApnsPermFailCount();
            }

            apnContext.removeWaitingApn(apnContext.getApnSetting());
            if (DBG) {
                log(String.format("onDataSetupComplete: WaitingApns.size=%d" +
                        " WaitingApnsPermFailureCountDown=%d",
                        apnContext.getWaitingApns().size(),
                        apnContext.getWaitingApnsPermFailCount()));
            }
            handleError = true;
        }

        if (handleError) {
            onDataSetupCompleteError(ar);
        }

        /* If flag is set to false after SETUP_DATA_CALL is invoked, we need
         * to clean data connections.
         */
        if (!mInternalDataEnabled) {
            cleanUpAllConnections(null);
        }

    }

	

    /**
     * @return number of milli-seconds to delay between trying apns'
     */
    private int getApnDelay() {
        if (mFailFast) {
            return SystemProperties.getInt("persist.radio.apn_ff_delay",
                    APN_FAIL_FAST_DELAY_DEFAULT_MILLIS);
        } else {
            return SystemProperties.getInt("persist.radio.apn_delay", APN_DELAY_DEFAULT_MILLIS);
        }
    }

    /**
     * Error has occurred during the SETUP {aka bringUP} request and the DCT
     * should either try the next waiting APN or start over from the
     * beginning if the list is empty. Between each SETUP request there will
     * be a delay defined by {@link #getApnDelay()}.
     */
    @Override
    protected void onDataSetupCompleteError(AsyncResult ar) {
        String reason = "";
        ApnContext apnContext = null;

        if(ar.userObj instanceof ApnContext){
            apnContext = (ApnContext)ar.userObj;
        } else {
            throw new RuntimeException("onDataSetupCompleteError: No apnContext");
        }

        // See if there are more APN's to try
        if (apnContext.getWaitingApns().isEmpty()) {
            apnContext.setState(DctConstants.State.FAILED);
            mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, apnContext.getApnType());

            apnContext.setDataConnectionAc(null);

            if (apnContext.getWaitingApnsPermFailCount() == 0) {
                if (DBG) {
                    log("onDataSetupCompleteError: All APN's had permanent failures, stop retrying");
                }
            } else {
                int delay = getApnDelay();
                if (DBG) {
                    log("onDataSetupCompleteError: Not all APN's had permanent failures delay="
                            + delay);
                }
                startAlarmForRestartTrySetup(delay, apnContext);
            }
        } else {
            if (DBG) log("onDataSetupCompleteError: Try next APN");
            apnContext.setState(DctConstants.State.SCANNING);
            // Wait a bit before trying the next APN, so that
            // we're not tying up the RIL command channel
            startAlarmForReconnect(getApnDelay(), apnContext);
        }
    }

    /**
     * Called when EVENT_DISCONNECT_DONE is received.
     */
    @Override
    protected void onDisconnectDone(int connId, AsyncResult ar) {
        ApnContext apnContext = null;

        if (ar.userObj instanceof ApnContext) {
            apnContext = (ApnContext) ar.userObj;
        } else {
            loge("onDisconnectDone: Invalid ar in onDisconnectDone, ignore");
            return;
        }

        if(DBG) log("onDisconnectDone: EVENT_DISCONNECT_DONE apnContext=" + apnContext);

        if (mConflictApn == true) {
            if (DBG) {
                log("onDisconnectDone: conflict apn");
            }
            setupDataOnConnectableApns(Phone.REASON_APN_CONFLICT);
            mConflictApn = false;
        }


        apnContext.setState(DctConstants.State.IDLE);

        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());

        // if all data connection are gone, check whether Airplane mode request was
        // pending. (ignore only IMS or EIMS is connected)
        if (isDisconnected() || isOnlyIMSorEIMSPdnConnected()) {
            if (mPhone.getServiceStateTracker().processPendingRadioPowerOffAfterDataOff()) {
                if(DBG) log("onDisconnectDone: radio will be turned off, no retries");
                // Radio will be turned off. No need to retry data setup
                apnContext.setApnSetting(null);
                apnContext.setDataConnectionAc(null);

                // Need to notify disconnect as well, in the case of switching Airplane mode.
                // Otherwise, it would cause 30s delayed to turn on Airplane mode.
                if (mDisconnectPendingCount > 0)
                    mDisconnectPendingCount--;

                if (mDisconnectPendingCount == 0) {
                    notifyDataDisconnectComplete();
                    notifyAllDataDisconnected();
                }
                return;
            }
        }

        // If APN is still enabled, try to bring it back up automatically
        if (mAttached.get() && apnContext.isReady() && retryAfterDisconnected(apnContext)) {
            if (PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType()) ||
                PhoneConstants.APN_TYPE_EMERGENCY.equals(apnContext.getApnType())) {
                if (DBG) log("onDisconnectDone: not to retry for " + apnContext.getApnType() + " PDN");
            } else {
                SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                String reason = apnContext.getReason();
                int reconnectTimer = getDisconnectDoneRetryTimer(reason);
                // Wait a bit before trying the next APN, so that
                // we're not tying up the RIL command channel.
                // This also helps in any external dependency to turn off the context.
                // M: [C2K][IRAT] Not to auto reconnect during IRAT.
                if (mSetupDataBlocked) {
                    log("onDisconnectDone: not to retry for IRAT.");
                } else {
                    if (DBG) log("onDisconnectDone: attached, " +
                                    "ready and retry after disconnect, reason:" + reason);
                    startAlarmForReconnect(reconnectTimer, apnContext);
                }
            }
       } else {
            boolean restartRadioAfterProvisioning = mPhone.getContext().getResources().getBoolean(
                    com.android.internal.R.bool.config_restartRadioAfterProvisioning);

            if (apnContext.isProvisioningApn() && restartRadioAfterProvisioning) {
                log("onDisconnectDone: restartRadio after provisioning");
                restartRadio();
            }
            apnContext.setApnSetting(null);
            apnContext.setDataConnectionAc(null);
            if (isOnlySingleDcAllowed(mPhone.getServiceState().getRilDataRadioTechnology())) {
                if(DBG) log("onDisconnectDone: isOnlySigneDcAllowed true so setup single apn");
                setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
            } else {
                if(DBG) log("onDisconnectDone: not retrying");
            }
        }

        if (mDisconnectPendingCount > 0)
            mDisconnectPendingCount--;

        if (mDisconnectPendingCount == 0) {
            apnContext.setConcurrentVoiceAndDataAllowed(
                    mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed());
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }
    }

    /**
     * M: Called when EVENT_DISCONNECT_DONE is received.
     * Get retry timer for onDisconnectDone.
     */
    private int getDisconnectDoneRetryTimer(String reason) {
        int timer = getApnDelay();
        if (Phone.REASON_APN_CHANGED.equals(reason)) {
            // M: onApnChanged need retry quickly
            timer = 3000;
        } else if (!BSP_PACKAGE && mGsmDctExt != null) {
            // M: for other specific reason
            try {
                timer = mGsmDctExt.getDisconnectDoneRetryTimer(reason, getApnDelay());
            } catch (Exception e) {
                loge("GsmDCTExt.getDisconnectDoneRetryTimer fail!");
                e.printStackTrace();
            }
        }

        return timer;
    }

    /**
     * Called when EVENT_DISCONNECT_DC_RETRYING is received.
     */
    @Override
    protected void onDisconnectDcRetrying(int connId, AsyncResult ar) {
        // We could just do this in DC!!!
        ApnContext apnContext = null;

        if (ar.userObj instanceof ApnContext) {
            apnContext = (ApnContext) ar.userObj;
        } else {
            loge("onDisconnectDcRetrying: Invalid ar in onDisconnectDone, ignore");
            return;
        }

        apnContext.setState(DctConstants.State.RETRYING);
        if(DBG) log("onDisconnectDcRetrying: apnContext=" + apnContext);

        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
    }


    @Override
    protected void onVoiceCallStarted() {
        if (DBG) log("onVoiceCallStarted");
        mInVoiceCall = true;
        if (isConnected() && ! mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            if (DBG) log("onVoiceCallStarted stop polling");
            stopNetStatPoll();
            stopDataStallAlarm();
            notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
        }
    }

    @Override
    protected void onVoiceCallEnded() {
        if (DBG) log("onVoiceCallEnded");
        mInVoiceCall = false;

        if (!getDataEnabled()) {
            if (DBG) {
                log("onVoiceCallEnded: default data disable, cleanup default apn.");
            }
            onCleanUpConnection(true, DctConstants.APN_DEFAULT_ID, Phone.REASON_DATA_DISABLED);
        }

        if (isConnected()) {
            if (!mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                log("onVoiceCallEnded start polling");
                startNetStatPoll();
                startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
            } else {
                // clean slate after call end.
                resetPollStats();
            }
        }
        // reset reconnect timer
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }

    @Override
    protected void onVoiceCallStartedPeer() {
        if (DBG) {
            log("onVoiceCallStartedPeer mPhone=" + mPhone);
        }
        mInVoiceCall = true;
        if (isConnected() && !isConcurrentVoiceAndDataAllowedWithPeer()) {
            if (DBG) {
                log("onVoiceCallStartedPeer stop polling");
            }
            stopNetStatPoll();
            stopDataStallAlarm();
            notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
        }
    }

    @Override
    protected void onVoiceCallEndedPeer() {
        if (DBG) {
            log("onVoiceCallEndedPeer");
        }
        mInVoiceCall = false;
        if (isConnected() && !isConcurrentVoiceAndDataAllowedWithPeer()) {
            if (DBG) {
                log("onVoiceCallEndedPeer start polling");
            }
            startNetStatPoll();
            startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
            notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
        }
        // reset reconnect timer
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }

    // M: [LTE][Low Power][UL traffic shaping] Start
    protected void onSharedDefaultApnState(int newDefaultRefCount) {
        if (DBG) {
            log("onSharedDefaultApnState: newDefaultRefCount = " + newDefaultRefCount
                    + ", curDefaultRefCount = " + mDefaultRefCount);
        }

        if(newDefaultRefCount != mDefaultRefCount) {
            if(newDefaultRefCount > 1) {
                mSharedDefaultApn = true;
            } else {
                mSharedDefaultApn = false;
            }
            mDefaultRefCount = newDefaultRefCount;
            if (DBG) {
                log("onSharedDefaultApnState: mSharedDefaultApn = " + mSharedDefaultApn);
            }
            notifySharedDefaultApn(mSharedDefaultApn);
        }
    }

    @Override
    protected void notifySharedDefaultApn(boolean mSharedDefaultApn) {
        mPhone.notifySharedDefaultApnStateChanged(mSharedDefaultApn);
    }
    // M: [LTE][Low Power][UL traffic shaping] End

    @Override
    protected void onCleanUpConnection(boolean tearDown, int apnId, String reason) {
        if (DBG) log("onCleanUpConnection");
        ApnContext apnContext = mApnContexts.get(apnIdToType(apnId));
        if (apnContext != null) {
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }
    }

    @Override
    protected boolean isConnected() {
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.getState() == DctConstants.State.CONNECTED) {
                // At least one context is connected, return true
                return true;
            }
        }
        // There are not any contexts connected, return false
        return false;
    }

    @Override
    public boolean isDisconnected() {
        for (ApnContext apnContext : mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                // At least one context was not disconnected return false
                return false;
            }
        }
        // All contexts were disconnected so return true
        return true;
    }

    @Override
    protected void notifyDataConnection(String reason) {
        if (DBG) log("notifyDataConnection: reason=" + reason);
        for (ApnContext apnContext : mApnContexts.values()) {
            if (mAttached.get() && apnContext.isReady() && apnContext.isNeedNotify()) {
                if (DBG) log("notifyDataConnection: type:" + apnContext.getApnType());
                mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(),
                        apnContext.getApnType());
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    /**
     * Based on the sim operator numeric, create a list for all possible
     * Data Connections and setup the preferredApn.
     */
    private void createAllApnList() {
        boolean hasResult = false;
        IccRecords r = mIccRecords.get();
        String operator = (r != null) ? r.getOperatorNumeric() : "";
        if (operator != null) {
            String selection = "numeric = '" + operator + "'";
            String orderBy = "_id";
            // query only enabled apn.
            // carrier_enabled : 1 means enabled apn, 0 disabled apn.
            // selection += " and carrier_enabled = 1";
            if (DBG) log("createAllApnList: selection=" + selection);

            Cursor cursor = mPhone.getContext().getContentResolver().query(
                    Telephony.Carriers.CONTENT_URI, null, selection, null, orderBy);

            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    mAllApnSettings = createApnList(cursor);
                    hasResult = true;
                }
                cursor.close();
            }
        }

        if (!hasResult) {
            mAllApnSettings = new ArrayList<ApnSetting>();
        }

        addEmergencyApnSetting();

        dedupeApnSettings();
      /*-BEGIN-20151014-add-by-qingtao.wen-for-task745095-*/
        boolean use_imsi_to_filter = mPhone.getContext().getResources().getBoolean(com.android.internal.R.bool.def_use_imsi_distinguish_mvno);
        if (DBG) log("createAllApnList: use_imsi_to_filter:" + use_imsi_to_filter);
        if(use_imsi_to_filter && operator != null && operator.equals("732103") && (mAllApnSettings != null)){ 
               if (DBG) log("createAllApnList: special plmn 732103 sizeof mAllApnSettings:" + mAllApnSettings.size());
               if(mAllApnSettings.size()>1){
                       //each MVNO of 732103 only has one APN,so we only do imsi filter when filter by spn failed.
                       //may be > 2 here is better,because Tigo have two APN
                       int result = judgeMVNOByImsi();
                       ArrayList<ApnSetting> delAPN = new ArrayList<ApnSetting>();
                       if (DBG) log("createAllApnList: special plmn 732103:result is:" + result);
                       for (ApnSetting apn : mAllApnSettings) {
                               if(apn.sourcetype == 0 && delAPN != null){ //only for pre-defined APN
                                       if(result >0){
                               if(result == 1){//1:UNE
                                       if(apn.apn.toLowerCase().compareTo("www.une.net.co") != 0){
                                               if (DBG) log("createAllApnList: special plmn 732103:remove apn:" + apn.carrier);
                                               delAPN.add(apn);
                                       }
                                       }else if(result == 2){//2:ETB, 
                                               if(apn.apn.toLowerCase().compareTo("moviletb.net.co") != 0){
                                                       if (DBG) log("createAllApnList: special plmn 732103:remove apn:" + apn.carrier);
                                                       delAPN.add(apn);
                                       }
                               }else if(result == 3){// 3:Uff
                                       if(apn.apn.toLowerCase().compareTo("web.uffmovil.com") != 0){
                                               if (DBG) log("createAllApnList: special plmn 732103:remove apn:" + apn.carrier);
                                               delAPN.add(apn);
                                       }
                               }else if(result == 4){ //4:Éxito
                                       if(apn.apn.toLowerCase().compareTo("movilexito.net.co") != 0){
                                               if (DBG) log("createAllApnList: special plmn 732103:remove apn:" + apn.carrier);
                                               delAPN.add(apn);
                                       }
                               }else if(result == 5){ //5:Tigo
                                       if(apn.apn.toLowerCase().compareTo("web.colombiamovil.com.co") != 0 && apn.apn.toLowerCase().compareTo("mms.colombiamovil.com.co") != 0){
                                               if (DBG) log("createAllApnList: special plmn 732103:remove apn:" + apn.carrier);
                                               delAPN.add(apn);
                                       }
                               
                               }
                       }
                               }
                       }
                       
                       if(delAPN != null && delAPN.size()>0){
                               mAllApnSettings.removeAll(delAPN);
                               delAPN.clear();
                       }
               }
        }
      /*-END-20151014-add-by-qingtao.wen-for-task745095-*/
        if (mAllApnSettings.isEmpty()) {
            if (DBG) log("createAllApnList: No APN found for carrier: " + operator);
            mPreferredApn = null;
            // TODO: What is the right behavior?
            //notifyNoData(DataConnection.FailCause.MISSING_UNKNOWN_APN);
        } else {
            mPreferredApn = getPreferredApn();
            if (mPreferredApn != null && !mPreferredApn.numeric.equals(operator)) {
                mPreferredApn = null;
                setPreferredApn(-1);
            }
            if (DBG) log("createAllApnList: mPreferredApn=" + mPreferredApn);
        }
        if (DBG) log("createAllApnList: X mAllApnSettings=" + mAllApnSettings);

        setDataProfilesAsNeeded();
    }

    private void dedupeApnSettings() {
        ArrayList<ApnSetting> resultApns = new ArrayList<ApnSetting>();

        // coalesce APNs if they are similar enough to prevent
        // us from bringing up two data calls with the same interface
        int i = 0;
        while (i < mAllApnSettings.size() - 1) {
            ApnSetting first = mAllApnSettings.get(i);
            ApnSetting second = null;
            int j = i + 1;
            while (j < mAllApnSettings.size()) {
                second = mAllApnSettings.get(j);
                if (apnsSimilar(first, second)) {
                    ApnSetting newApn = mergeApns(first, second);
                    mAllApnSettings.set(i, newApn);
                    first = newApn;
                    mAllApnSettings.remove(j);
                } else {
                    j++;
                }
            }
            i++;
        }
    }

    //check whether the types of two APN same (even only one type of each APN is same)
    private boolean apnTypeSameAny(ApnSetting first, ApnSetting second) {
        if(VDBG) {
            StringBuilder apnType1 = new StringBuilder(first.apn + ": ");
            for(int index1 = 0; index1 < first.types.length; index1++) {
                apnType1.append(first.types[index1]);
                apnType1.append(",");
            }

            StringBuilder apnType2 = new StringBuilder(second.apn + ": ");
            for(int index1 = 0; index1 < second.types.length; index1++) {
                apnType2.append(second.types[index1]);
                apnType2.append(",");
            }
            log("APN1: is " + apnType1);
            log("APN2: is " + apnType2);
        }

        for(int index1 = 0; index1 < first.types.length; index1++) {
            for(int index2 = 0; index2 < second.types.length; index2++) {
                if(first.types[index1].equals(PhoneConstants.APN_TYPE_ALL) ||
                        second.types[index2].equals(PhoneConstants.APN_TYPE_ALL) ||
                        first.types[index1].equals(second.types[index2])) {
                    if(VDBG)log("apnTypeSameAny: return true");
                    return true;
                }
            }
        }

        if(VDBG)log("apnTypeSameAny: return false");
        return false;
    }

    // Check if neither mention DUN and are substantially similar
    private boolean apnsSimilar(ApnSetting first, ApnSetting second) {
        return (first.canHandleType(PhoneConstants.APN_TYPE_DUN) == false &&
                second.canHandleType(PhoneConstants.APN_TYPE_DUN) == false &&
                Objects.equals(first.apn, second.apn) &&
                !apnTypeSameAny(first, second) &&
                xorEquals(first.proxy, second.proxy) &&
                xorEquals(first.port, second.port) &&
                first.carrierEnabled == second.carrierEnabled &&
                first.bearerBitmask == second.bearerBitmask &&
                first.profileId == second.profileId &&
                Objects.equals(first.mvnoType, second.mvnoType) &&
                Objects.equals(first.mvnoMatchData, second.mvnoMatchData) &&
                xorEquals(first.mmsc, second.mmsc) &&
                xorEquals(first.mmsProxy, second.mmsProxy) &&
                xorEquals(first.mmsPort, second.mmsPort));
    }

    // equal or one is not specified
    private boolean xorEquals(String first, String second) {
        return (Objects.equals(first, second) ||
                TextUtils.isEmpty(first) ||
                TextUtils.isEmpty(second));
    }

    private ApnSetting mergeApns(ApnSetting dest, ApnSetting src) {
        int id = dest.id;
        ArrayList<String> resultTypes = new ArrayList<String>();
        resultTypes.addAll(Arrays.asList(dest.types));
        for (String srcType : src.types) {
            if (resultTypes.contains(srcType) == false) resultTypes.add(srcType);
            if (srcType.equals(PhoneConstants.APN_TYPE_DEFAULT)) id = src.id;
        }
        String mmsc = (TextUtils.isEmpty(dest.mmsc) ? src.mmsc : dest.mmsc);
        String mmsProxy = (TextUtils.isEmpty(dest.mmsProxy) ? src.mmsProxy : dest.mmsProxy);
        String mmsPort = (TextUtils.isEmpty(dest.mmsPort) ? src.mmsPort : dest.mmsPort);
        String proxy = (TextUtils.isEmpty(dest.proxy) ? src.proxy : dest.proxy);
        String port = (TextUtils.isEmpty(dest.port) ? src.port : dest.port);
        String protocol = src.protocol.equals("IPV4V6") ? src.protocol : dest.protocol;
        String roamingProtocol = src.roamingProtocol.equals("IPV4V6") ? src.roamingProtocol :
                dest.roamingProtocol;
        int bearerBitmask = (dest.bearerBitmask == 0 || src.bearerBitmask == 0) ?
                0 : (dest.bearerBitmask | src.bearerBitmask);

        return new ApnSetting(id, dest.numeric, dest.carrier, dest.apn,
                proxy, port, mmsc, mmsProxy, mmsPort, dest.user, dest.password,
                dest.authType, resultTypes.toArray(new String[0]), protocol,
                roamingProtocol, dest.carrierEnabled, 0, bearerBitmask, dest.profileId,
                (dest.modemCognitive || src.modemCognitive), dest.maxConns, dest.waitTime,
                dest.maxConnsTime, dest.mtu, dest.mvnoType, dest.mvnoMatchData);
    }

    /** Return the DC AsyncChannel for the new data connection */
    private DcAsyncChannel createDataConnection(String reqApnType, ApnSetting apnSetting) {
        if (DBG) log("createDataConnection E");

        int id = 0;
        if (isSupportThrottlingApn()) {
            id = generateDataConnectionId(reqApnType, apnSetting);
            if (id < 0) {
                return null;
            }
        } else {
            id = mUniqueIdGenerator.getAndIncrement();
            if (id >= getPdpConnectionPoolSize()) {
                loge("Max PDP count is " + getPdpConnectionPoolSize() + ",but request " + (id + 1));
                mUniqueIdGenerator.getAndDecrement();
                return null;
            }
        }

        DataConnection conn = DataConnection.makeDataConnection(mPhone, id,
                                                this, mDcTesterFailBringUpAll, mDcc);
        mDataConnections.put(id, conn);
        DcAsyncChannel dcac = new DcAsyncChannel(conn, LOG_TAG);
        int status = dcac.fullyConnectSync(mPhone.getContext(), this, conn.getHandler());
        if (status == AsyncChannel.STATUS_SUCCESSFUL) {
            mDataConnectionAcHashMap.put(dcac.getDataConnectionIdSync(), dcac);
        } else {
            loge("createDataConnection: Could not connect to dcac=" + dcac + " status=" + status);
        }

        if (DBG) log("createDataConnection() X id=" + id + " dc=" + conn);
        return dcac;
    }

    private void destroyDataConnections() {
        if(mDataConnections != null) {
            if (DBG) log("destroyDataConnections: clear mDataConnectionList");
            mDataConnections.clear();
        } else {
            if (DBG) log("destroyDataConnections: mDataConnecitonList is empty, ignore");
        }
    }
    /*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
    private String[][] getApnTableForUKOp(String operatorNum) {
        if ("23430".equals(operatorNum)) {
            return APN_TABLE_23430;
        } else if ("23433".equals(operatorNum)) {
            return APN_TABLE_23433;
        } else if ("23410".equals(operatorNum)) {
            return APN_TABLE_23410;
        } else if ("23415".equals(operatorNum)) {
            return APN_TABLE_23415;
        }
        return null;
    }
    /*-END-20151014-add-by-qingtao.wen-for-task745079-*/
    /**
     * Build a list of APNs to be used to create PDP's.
     *
     * @param requestedApnType
     * @return waitingApns list to be used to create PDP
     *          error when waitingApns.isEmpty()
     */
    private ArrayList<ApnSetting> buildWaitingApns(String requestedApnType, int radioTech) {
        if (DBG) log("buildWaitingApns: E requestedApnType=" + requestedApnType);
        ArrayList<ApnSetting> apnList = new ArrayList<ApnSetting>();

        if (requestedApnType.equals(PhoneConstants.APN_TYPE_DUN)) {
            ApnSetting dun = fetchDunApn();
            if (dun != null) {
                apnList.add(dun);
                if (DBG) log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + apnList);
                return apnList;
            }
        }

        IccRecords r = mIccRecords.get();
        String operator = (r != null) ? r.getOperatorNumeric() : "";

        // This is a workaround for a bug (7305641) where we don't failover to other
        // suitable APNs if our preferred APN fails.  On prepaid ATT sims we need to
        // failover to a provisioning APN, but once we've used their default data
        // connection we are locked to it for life.  This change allows ATT devices
        // to say they don't want to use preferred at all.
        boolean usePreferred = true;
        try {
            usePreferred = ! mPhone.getContext().getResources().getBoolean(com.android.
                    internal.R.bool.config_dontPreferApn);
        } catch (Resources.NotFoundException e) {
            if (DBG) log("buildWaitingApns: usePreferred NotFoundException set to true");
            usePreferred = true;
        }
        if (usePreferred) {
            mPreferredApn = getPreferredApn();
        }
        if (DBG) {
            log("buildWaitingApns: usePreferred=" + usePreferred
                    + " canSetPreferApn=" + mCanSetPreferApn
                    + " mPreferredApn=" + mPreferredApn
                    + " operator=" + operator + " radioTech=" + radioTech
                    + " IccRecords r=" + r);
        }
        /*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
        int slotId = SubscriptionManager.getSlotId(mPhone.getSubId());
        /*-END-20151014-add-by-qingtao.wen-for-task745079-*/
        if (usePreferred && mCanSetPreferApn && mPreferredApn != null &&
                mPreferredApn.canHandleType(requestedApnType)) {
            if (DBG) {
                log("buildWaitingApns: Preferred APN:" + operator + ":"
                        + mPreferredApn.numeric + ":" + mPreferredApn);
            }
            if (mPreferredApn.numeric.equals(operator)) {
                if (ServiceState.bitmaskHasTech(mPreferredApn.bearerBitmask, radioTech)) {

                    /*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
                    SharedPreferences sp = mPhone.getContext().getSharedPreferences("o2imsicheck",Context.MODE_WORLD_READABLE);
                                               
                        String oldIMSI = sp.getString("imsi"+slotId, "");
                        String curIMSI = r.getIMSI();
                       if ((("23430".compareTo(operator) == 0)
                                        || ("23433".compareTo(operator) == 0)
                                        || ("23410".compareTo(operator) == 0)
                                        || ("23415".compareTo(operator) == 0))
                                && (oldIMSI.compareTo(curIMSI) != 0)) {
                            Editor editor = sp.edit();
                            editor.putString("imsi"+slotId, curIMSI);                                  
                            editor.commit();
                            setPreferredApn(-1);
                            mPreferredApn = null;
                            if (DBG) log("buildWaitingApns:special PLMN setpreferredAPN,and o2imsicheck");                        
                    }else{
                    /*-END-20151014-add-by-qingtao.wen-for-task745079-*/   

                    if (mGsmDctExt != null) {
                        mPreferredApn = (ApnSetting) mGsmDctExt.customizeApn(mPreferredApn);
                    }

                    apnList.add(mPreferredApn);
                    if (DBG) log("buildWaitingApns: X added preferred apnList=" + apnList);
                    return apnList;
                    }//add 20151014 by qingtao.wen for-task745079
                } else {
                    if (DBG) log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    mPreferredApn = null;
                }
            } else {
                if (DBG) log("buildWaitingApns: no preferred APN");
                setPreferredApn(-1);
                mPreferredApn = null;
            }
        }
        if (mAllApnSettings != null) {
            if (DBG) log("buildWaitingApns: mAllApnSettings=" + mAllApnSettings);
            for (ApnSetting apn : mAllApnSettings) {
                if (apn.canHandleType(requestedApnType)) {
                    if (ServiceState.bitmaskHasTech(apn.bearerBitmask, radioTech)) {
                       /*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
                       if(SubscriptionManager.isValidSlotId(slotId)){
                               if (DBG) log("buildWaitingApns: zk before filter adding apn=" + apn.toString());
                               String[][] apnTable = getApnTableForUKOp(operator);
                               String currentOperator = Settings.Secure.getString(mPhone
                                       .getContext().getContentResolver(), SIMSERVICEGEMI[slotId]);
                               if (apnTable != null && currentOperator != null
                                       && !currentOperator.isEmpty()
                                       && mPhone.getContext().getResources().getBoolean(
                                               com.android.internal.R.bool.def_sim_service_for_openmarket)
                                       ) {
                                   boolean flag = false;
                                   for (String[] apn_record : apnTable) {
                                       if (currentOperator.toLowerCase().equals(apn_record[0].toLowerCase())
                                               && apn.apn.toLowerCase().equals(apn_record[2].toLowerCase())
                                               && apn.carrier.toLowerCase().equals(apn_record[1].toLowerCase())) {
                                           flag = true;
                                           break;
                                       }
                                   }
                                               if (DBG) log("apn.sourcetype=" + apn.sourcetype);
                                               if (apn.sourcetype == 1 ||apn.sourcetype == 2) {        
                                                  flag = true;
                                               }
                                   if (!flag) {
                                       continue;
                                   }
                               }  else {
                                   // For select Prepay/Postpay apn for O2 Sim
                                       if(apn.sourcetype == 0){
                                           log("zk--Numeric: " + operator);
                                           if ("23410".compareTo(operator) == 0) {
                                               String simOp = SystemProperties.get("gsm.sim.operator.paykind"+slotId);
                                               if (simOp != null && simOp.compareTo("") != 0) {
                                                       log("zk--DcTracker sim kind of paytype " + simOp);
                                                       
                                                       /*o2 prepay card,corresponding APN title is  "O2 pay&Go" and "O2 MMS"  both apn are "payandgo.o2.co.uk"*/
                                                       if (simOp.compareTo("prepay") == 0) {
                                                               if (apn.apn.toLowerCase().compareTo("payandgo.o2.co.uk") != 0){
                                                                        log("zk--DcTracker not o2prepay simcard apn,remove it");
                                                            continue;
                                                               }
                                                       }
                                                       /*o2 postpay card,corresponding 1:APN title is  "O2 Mobile Web" apn is "mobile.o2.co.uk" 
                                                                                                                   2:APN title is "O2 MMS"  apn is "wap.o2.co.uk"*/
                                                       else if(simOp.compareTo("postpay") == 0){
                                                       if ((apn.apn.toLowerCase().compareTo("mobile.o2.co.uk") != 0) && (apn.apn.toLowerCase().compareTo("wap.o2.co.uk") != 0)) {
                                                               log("zk--DcTracker not o2postpay simcard apn,remove it");
                                                           continue;
                                                       }
                                                       }
                                                       /*giffgaff sim card,only one APN related,APN title is "giffgaff" apn is "giffgaff.com"*/
                                                       else if(simOp.compareTo("giffgaff") == 0){
                                                               if (apn.apn.toLowerCase().compareTo("giffgaff.com") != 0){
                                                                               log("zk--DcTracker not giffgaff simcard apn,remove it");
                                                           continue;
                                                               }
                                                       }
                                                       /*tesco sim card,only one APN related,APN title is "TESCO Mobile" apn is "prepay.tesco-mobile.com"*/                            
                                                       else if(simOp.compareTo("tesco") == 0){
                                                               if (apn.apn.toLowerCase().compareTo("prepay.tesco-mobile.com") != 0){
                                                                               log("zk--DcTracker not tesco simcard apn,remove it");
                                                           continue;
                                                               }
                                                       }
                                                       /*Broadband sim card,only one APN related,APN title is "O2 Pay & Go Mobile Broadband" apn is "m-bb.o2.co.uk"*/                          
                                                       else if(simOp.compareTo("broadband") == 0){
                                                               if (apn.apn.toLowerCase().compareTo("m-bb.o2.co.uk") != 0){
                                                                               log("zk--DcTracker not broadband simcard apn,remove it");
                                                           continue;
                                                               }
                                                       }
                                               }
                                               
                                           }
                                       }
                               }
                       }
                       /*-END-20151014-add-by-qingtao.wen-for-task745079-*/
                        if (DBG) log("buildWaitingApns: adding apn=" + apn);
                        if (mGsmDctExt != null) {
                            apn = (ApnSetting) mGsmDctExt.customizeApn(apn);
                        }
                        apnList.add(apn);
                    } else {
                        if (DBG) {
                            log("buildWaitingApns: bearerBitmask:" + apn.bearerBitmask + " does " +
                                    "not include radioTech:" + radioTech);
                        }
                    }
                } else if (DBG) {
                    log("buildWaitingApns: couldn't handle requesedApnType="
                            + requestedApnType);
                }
            }
        } else {
            loge("mAllApnSettings is null!");
        }
        if (DBG) log("buildWaitingApns: X apnList=" + apnList);
        return apnList;
    }

    private String apnListToString (ArrayList<ApnSetting> apns) {
        StringBuilder result = new StringBuilder();
        try {
            for (int i = 0, size = apns.size(); i < size; i++) {
                result.append('[')
                  .append(apns.get(i).toString())
                  .append(']');
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            return null;
        }
        return result.toString();
    }

    private void setPreferredApn(int pos) {
        if (!mCanSetPreferApn) {
            log("setPreferredApn: X !canSEtPreferApn");
            return;
        }

        String subId = Long.toString(mPhone.getSubId());
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subId);
        log("setPreferredApn: delete subId = " + subId);
        ContentResolver resolver = mPhone.getContext().getContentResolver();
        resolver.delete(uri, null, null);

        if (pos >= 0) {
            log("setPreferredApn: insert pos = " + pos + ",subId =" + subId);
            ContentValues values = new ContentValues();
            values.put(APN_ID, pos);
            resolver.insert(uri, values);
        }
    }

    private ApnSetting getPreferredApn() {
        if (mAllApnSettings == null || mAllApnSettings.isEmpty()) {
            log("getPreferredApn: mAllApnSettings is " + ((mAllApnSettings == null)?"null":"empty"));
            return null;
        }

        String subId = Long.toString(mPhone.getSubId());
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subId);
        Cursor cursor = mPhone.getContext().getContentResolver().query(
                uri, new String[] { "_id", "name", "apn" },
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            mCanSetPreferApn = true;
        } else {
            mCanSetPreferApn = false;
        }
        log("getPreferredApn: mRequestedApnType=" + mRequestedApnType + " cursor=" + cursor
                + " cursor.count=" + ((cursor != null) ? cursor.getCount() : 0)
                + " subId = " + subId);

        if (mCanSetPreferApn && cursor.getCount() > 0) {
            int pos;
            cursor.moveToFirst();
            pos = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID));
            for(ApnSetting p : mAllApnSettings) {
                log("getPreferredApn: apnSetting=" + p + ", pos = " + pos + ",subId = " + subId);
                if (p.id == pos && p.canHandleType(mRequestedApnType)) {
                    log("getPreferredApn: X found apnSetting" + p);
                    cursor.close();
                    return p;
                }
            }
        }

        if (cursor != null) {
            cursor.close();
        }

        log("getPreferredApn: X not found");
        return null;
    }

    @Override
    public void handleMessage (Message msg) {
        if (DBG) log("handleMessage msg=" + msg);

        if (!mPhone.mIsTheCurrentActivePhone || mIsDisposed) {
            loge("handleMessage: Ignore GSM msgs since GSM phone is inactive");
            return;
        }

        switch (msg.what) {
            case DctConstants.EVENT_RECORDS_LOADED:
		/*Begin zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
                if (mFdnObserver == null
                        && SubscriptionManager.isValidSubscriptionId(mPhone.getSubId())) {
                    log("FdnChangeObserver " + mPhone.getSubId());
                    mFdnObserver = new FdnChangeObserver();
                    mPhone.getContext().getContentResolver().registerContentObserver(
                            Uri.parse("content://icc/fdn/subId/" + mPhone.getSubId()),
                            true,
                            mFdnObserver);
                    if (isFdnEnabled()) {
                        sendMessageDelayed(obtainMessage(DctConstants.EVENT_QUERY_FDN_LIST), 2000);
                    }
                }
		/*End zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
                onRecordsLoaded();
                break;

            case DctConstants.EVENT_SETUP_DATA_WHEN_LOADED:
                setupDataOnConnectableApns(Phone.REASON_SIM_LOADED);
                break;

            case DctConstants.EVENT_DATA_CONNECTION_DETACHED:
                onDataConnectionDetached();
                break;

            case DctConstants.EVENT_DATA_CONNECTION_ATTACHED:
                onDataConnectionAttached();
                break;

            case DctConstants.EVENT_DO_RECOVERY:
                doRecovery();
                break;

            case DctConstants.EVENT_APN_CHANGED:
                new Thread(new Runnable() {
                    public void run() {
                        synchronized (mCreateApnLock) {
                            onApnChanged();
                        }
                    }
                }).start();
                break;

            case DctConstants.EVENT_APN_CHANGED_DONE:
                boolean bIMSApnChanged = (msg.arg1 == 0) ? false : true;
                if (DBG) {
                    log("EVENT_APN_CHANGED_DONE");
                }
                if (bIMSApnChanged) {
                    log("ims apn changed");
                    ApnContext apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_IMS);
                    cleanUpConnection(true, apnContext);
                } else {
                    // default changed
                    onApnChangedDone();
                }
                break;

            case DctConstants.EVENT_PS_RESTRICT_ENABLED:
                /**
                 * We don't need to explicitly to tear down the PDP context
                 * when PS restricted is enabled. The base band will deactive
                 * PDP context and notify us with PDP_CONTEXT_CHANGED.
                 * But we should stop the network polling and prevent reset PDP.
                 */
                if (DBG) log("EVENT_PS_RESTRICT_ENABLED " + mIsPsRestricted);
                stopNetStatPoll();
                stopDataStallAlarm();
                mIsPsRestricted = true;
                break;

            case DctConstants.EVENT_PS_RESTRICT_DISABLED:
                /**
                 * When PS restrict is removed, we need setup PDP connection if
                 * PDP connection is down.
                 */
                //M: Wifi only
                ConnectivityManager cnnm = (ConnectivityManager) mPhone.getContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);

                if (DBG) log("EVENT_PS_RESTRICT_DISABLED " + mIsPsRestricted);
                mIsPsRestricted  = false;
                if (isConnected()) {
                    startNetStatPoll();
                    startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                } else {
                    // TODO: Should all PDN states be checked to fail?
                    if (mState == DctConstants.State.FAILED) {
                        cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
                        mReregisterOnReconnectFailure = false;
                    }
                    ApnContext apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_DEFAULT);
                    if (apnContext != null) {
                        apnContext.setReason(Phone.REASON_PS_RESTRICT_ENABLED);
                        trySetupData(apnContext);
                    } else {
                        loge("**** Default ApnContext not found ****");
                        //M: Wifi only
                        if (Build.IS_DEBUGGABLE && cnnm.isNetworkSupported(
                                ConnectivityManager.TYPE_MOBILE)) {
                            throw new RuntimeException("Default ApnContext not found");
                        }
                    }
                }
                break;

            case DctConstants.EVENT_TRY_SETUP_DATA:
                if (msg.obj instanceof ApnContext) {
                    onTrySetupData((ApnContext)msg.obj);
                } else if (msg.obj instanceof String) {
                    onTrySetupData((String)msg.obj);
                } else {
                    loge("EVENT_TRY_SETUP request w/o apnContext or String");
                }
                break;

            case DctConstants.EVENT_CLEAN_UP_CONNECTION:
                boolean tearDown = (msg.arg1 == 0) ? false : true;
                if (DBG) log("EVENT_CLEAN_UP_CONNECTION tearDown=" + tearDown);
                if (msg.obj instanceof ApnContext) {
                    cleanUpConnection(tearDown, (ApnContext)msg.obj);
                } else {
                    loge("EVENT_CLEAN_UP_CONNECTION request w/o apn context, call super");
                    super.handleMessage(msg);
                }
                break;
            case DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE:
                boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onSetInternalDataEnabled(enabled, (Message) msg.obj);
                break;

            case DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS:
                Message mCause = obtainMessage(DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS, null);
                if ((msg.obj != null) && (msg.obj instanceof String)) {
                    mCause.obj = msg.obj;
                }
                super.handleMessage(mCause);
                break;

            case DctConstants.EVENT_DATA_RAT_CHANGED:
                //May new Network allow setupData, so try it here
                if (DctController.getInstance().getActiveDataPhoneId() == mPhone.getPhoneId()) {
                    setupDataOnConnectableApns(Phone.REASON_NW_TYPE_CHANGED,
                            RetryFailures.ONLY_ON_CHANGE);
                }
                break;

            case DctConstants.CMD_CLEAR_PROVISIONING_SPINNER:
                // Check message sender intended to clear the current spinner.
                if (mProvisioningSpinner == msg.obj) {
                    mProvisioningSpinner.dismiss();
                    mProvisioningSpinner = null;
                }
                break;

            //M:[C2K][IRAT] Handle Rat change message every time Rat changed @{
            case DctConstants.EVENT_POST_CREATE_PHONE:
                // Fast Dormancy init
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                    SvltePhoneProxy svltePhoneProxy = SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId());
                    mFdMgr = FdManager.getInstance(svltePhoneProxy.getLtePhone());
                } else {
                    mFdMgr = FdManager.getInstance(mPhone);
                }
                registerForEventsAfterPhoneCreated();
                break;

            case LteDcConstants.EVENT_IRAT_DATA_RAT_CHANGED:
                AsyncResult ar = (AsyncResult) msg.obj;
                Pair<Integer, Integer> ratPair = (Pair<Integer, Integer>) ar.result;
                final int sourceRat = (ratPair.first).intValue();
                final int targetRat = (ratPair.second).intValue();
                onIratDataRatChanged(sourceRat, targetRat);
                break;

            case LteDcConstants.EVENT_LTE_RECORDS_LOADED:
                onLteRecordsLoaded();
                break;

            case LteDcConstants.EVENT_RETRY_SETUP_DATA_FOR_IRAT:
                log("[IRAT_DcTracker] Retry to setup data for IRAT.");
                mSetupDataBlocked = false;
                setupDataOnConnectableApns(Phone.REASON_CDMA_FALLBACK_HAPPENED);
                break;
            //M:}@

            //M: FDN Support
            case DctConstants.EVENT_FDN_CHANGED:
                onFdnChanged();
                break;

            case DctConstants.EVENT_RESET_PDP_DONE:
                log("EVENT_RESET_PDP_DONE cid=" + msg.arg1);
                break;

            case DctConstants.EVENT_REMOVE_RESTRICT_EUTRAN:
                if (isCctSmRetry() && !(mPhone.mCi.isGettingAvailableNetworks())) {
                    log("EVENT_REMOVE_RESTRICT_EUTRAN");
                    mReregisterOnReconnectFailure = false;
                    setupDataOnConnectableApns(Phone.REASON_PS_RESTRICT_DISABLED);
                }
                break;

            // M: [LTE][Low Power][UL traffic shaping] Start
            case DctConstants.EVENT_LTE_ACCESS_STRATUM_STATE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    int[] ints = (int[]) ar.result;
                    int lteAccessStratumDataState = ints[0];
                    if (lteAccessStratumDataState != mLteAsConnected) { //LTE AS Disconnected
                        int networkType = ints[1];
                        log("EVENT_LTE_ACCESS_STRATUM_STATE networkType = " + networkType);
                        notifyPsNetworkTypeChanged(networkType);
                    } else { // LTE AS Connected
                        mPhone.notifyPsNetworkTypeChanged(TelephonyManager.NETWORK_TYPE_LTE);
                    }
                    log("EVENT_LTE_ACCESS_STRATUM_STATE lteAccessStratumDataState = "
                            + lteAccessStratumDataState);
                    notifyLteAccessStratumChanged(lteAccessStratumDataState);
                } else {
                    Rlog.e(LOG_TAG, "LteAccessStratumState exception: " + ar.exception);
                }
                break;
            // M: [LTE][Low Power][UL traffic shaping] End
		/*Begin zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
            case DctConstants.EVENT_QUERY_FDN_LIST:
                queryFdnList();
                break;
		/*End zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
            default:
                // handle the message in the super class DataConnectionTracker
                super.handleMessage(msg);
                break;
        }
    }

    protected int getApnProfileID(String apnType) {
        if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_IMS)) {
            return RILConstants.DATA_PROFILE_IMS;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_FOTA)) {
            return RILConstants.DATA_PROFILE_FOTA;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_CBS)) {
            return RILConstants.DATA_PROFILE_CBS;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_IA)) {
            return RILConstants.DATA_PROFILE_DEFAULT; // DEFAULT for now
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_DUN)) {
            return RILConstants.DATA_PROFILE_TETHERED;
        } else {
            return RILConstants.DATA_PROFILE_DEFAULT;
        }
    }

    private int getCellLocationId() {
        int cid = -1;
        CellLocation loc = mPhone.getCellLocation();

        if (loc != null) {
            if (loc instanceof GsmCellLocation) {
                cid = ((GsmCellLocation)loc).getCid();
            } else if (loc instanceof CdmaCellLocation) {
                cid = ((CdmaCellLocation)loc).getBaseStationId();
            }
        }
        return cid;
    }

    private IccRecords getUiccRecords(int appFamily) {
        // M: [C2K][IRAT] Need to use slot ID to get IccRecord instead of phone ID.
        int phoneId = mPhone.getPhoneId();
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            phoneId = SvlteUtils.getSvltePhoneIdByPhoneId(phoneId);
        }
        return mUiccController.getIccRecords(phoneId, appFamily);
    }


    @Override
    protected void onUpdateIcc() {
        if (mUiccController == null ) {
            return;
        }

        /// M: [C2K] revise SIM application family for 3GPP2.
        final int family = getUiccFamily(mPhone);
        IccRecords newIccRecords = getUiccRecords(family);
        IccRecords r = mIccRecords.get();
        log("onUpdateIcc: family = " + family
                + ", newIccRecords = " + newIccRecords + ", r = " + r);

        if (r != newIccRecords) {
            if (r != null) {
                log("Removing stale icc objects.");
                //r.unregisterForRecordsLoaded(this);
                mIccRecords.set(null);
            }
            if (newIccRecords != null) {
                log("New records found");
                mIccRecords.set(newIccRecords);
                // M: SIM records loaded event needs to wait for subId ready,
                // so trigger onRecordsLoaded should be notified by SubscriptionInfoUpdater.
                //newIccRecords.registerForRecordsLoaded(
                //        this, DctConstants.EVENT_RECORDS_LOADED, null);
            } else {
                onSimNotReady();
            }
        }

        // M: [C2K][IRAT] Register for LTE records loaded.
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && SvlteUtils.isActiveSvlteMode(mPhone)) {
            IccRecords newLteIccRecords = getUiccRecords(UiccController.APP_FAM_3GPP);
            IccRecords oldLteIccRecords = mLteIccRecords.get();
            log("[IRAT_DcTracker] Register for LTE IccRecords: newLteIccRecords = "
                    + newLteIccRecords
                    + ", oldLteIccRecords = "
                    + oldLteIccRecords);

            // Do not judge whether the records is the same before registering
            // LTE records, because only single SIM record on Android, the SIM
            // record instance is always the same.
            if (oldLteIccRecords != null) {
                log("Removing stale LTE icc objects.");
                oldLteIccRecords.unregisterForRecordsLoaded(this);
                mLteIccRecords.set(null);

                // Register back the records loaded event if it is removed.
                if (oldLteIccRecords == newIccRecords) {
                    newIccRecords.registerForRecordsLoaded(this,
                            DctConstants.EVENT_RECORDS_LOADED, null);
                }
            }
            if (newLteIccRecords != null) {
                log("New LTE records found");
                mLteIccRecords.set(newLteIccRecords);
                newLteIccRecords.registerForRecordsLoaded(this,
                        LteDcConstants.EVENT_LTE_RECORDS_LOADED, null);
            }
        }

        if (mAllApnSettings != null && r == null && newIccRecords == null) {
            mAllApnSettings.clear();
        }

        //MTK START: FDN Support
        UiccCardApplication app = mUiccCardApplication.get();
        UiccCardApplication newUiccCardApp = mUiccController.getUiccCardApplication(
                mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA ?
                UiccController.APP_FAM_3GPP2 : UiccController.APP_FAM_3GPP);

        if (app != newUiccCardApp) {
            if (app != null) {
                log("Removing stale UiccCardApplication objects.");
                app.unregisterForFdnChanged(this);
                mUiccCardApplication.set(null);
            }

            if (newUiccCardApp != null) {
                log("New UiccCardApplication found");
                newUiccCardApp.registerForFdnChanged(this, DctConstants.EVENT_FDN_CHANGED, null);
                mUiccCardApplication.set(newUiccCardApp);
            }
        }
        //MTK END: FDN Support
    }

    public void update() {
        log("update sub = " + mPhone.getSubId());
        // M: remove for redundantly register that cause ANR
        //log("update(): Active DDS, register for all events now!");
        //registerForAllEvents();
        onUpdateIcc();

        mUserDataEnabled = getDataEnabled();

        if (mPhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone)mPhone).updateCurrentCarrierInProvider();
        } else if (mPhone instanceof GSMPhone) {
            ((GSMPhone)mPhone).updateCurrentCarrierInProvider();
        } else {
            log("Phone object is not MultiSim. This should not hit!!!!");
        }
    }

    @Override
    public void cleanUpAllConnections(String cause) {
        cleanUpAllConnections(cause, null);
    }

    public void updateRecords() {
        onUpdateIcc();
    }

    public void cleanUpAllConnections(String cause, Message disconnectAllCompleteMsg) {
        log("cleanUpAllConnections");
        if (disconnectAllCompleteMsg != null) {
            mDisconnectAllCompleteMsgList.add(disconnectAllCompleteMsg);
        }

        Message msg = obtainMessage(DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS);
        msg.obj = cause;
        sendMessage(msg);
    }

    protected void notifyDataDisconnectComplete() {
        log("notifyDataDisconnectComplete");
        for (Message m: mDisconnectAllCompleteMsgList) {
            m.sendToTarget();
        }
        mDisconnectAllCompleteMsgList.clear();
    }


    protected void notifyAllDataDisconnected() {
        sEnableFailFastRefCounter = 0;
        mFailFast = false;
        mAllDataDisconnectedRegistrants.notifyRegistrants();
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        mAllDataDisconnectedRegistrants.addUnique(h, what, obj);

        if (isDisconnected()) {
            log("notify All Data Disconnected");
            notifyAllDataDisconnected();
        }
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        mAllDataDisconnectedRegistrants.remove(h);
    }


    @Override
    protected void onSetInternalDataEnabled(boolean enable) {
        if (DBG) log("onSetInternalDataEnabled: enabled=" + enable);
        onSetInternalDataEnabled(enable, null);
    }

    protected void onSetInternalDataEnabled(boolean enabled, Message onCompleteMsg) {
        if (DBG) log("onSetInternalDataEnabled: enabled=" + enabled);
        boolean sendOnComplete = true;

        synchronized (mDataEnabledLock) {
            mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                sendOnComplete = false;
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(null, onCompleteMsg);
            }
        }

        if (sendOnComplete) {
            if (onCompleteMsg != null) {
                onCompleteMsg.sendToTarget();
            }
        }
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        if (DBG) log("setInternalDataEnabledFlag(" + enable + ")");

        if (mInternalDataEnabled != enable) {
            mInternalDataEnabled = enable;
        }
        return true;
    }

    @Override
    public boolean setInternalDataEnabled(boolean enable) {
        return setInternalDataEnabled(enable, null);
    }

    public boolean setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        if (DBG) log("setInternalDataEnabled(" + enable + ")");

        Message msg = obtainMessage(DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE, onCompleteMsg);
        msg.arg1 = (enable ? DctConstants.ENABLED : DctConstants.DISABLED);
        sendMessage(msg);
        return true;
    }

    //MTK START
    @Override
    protected boolean isDataAllowedAsOff(String apnType) {
        boolean isDataAllowedAsOff = false;
        if (!BSP_PACKAGE && mGsmDctExt != null) {
            isDataAllowedAsOff = mGsmDctExt.isDataAllowedAsOff(apnType);
        }

        // M: Vsim
        if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_DEFAULT)
                && isVsimActive(mPhone.getPhoneId())) {
            if (DBG) {
                log("Vsim is enabled, set isDataAllowedAsOff true");
            }
            isDataAllowedAsOff = true;
        }

        return isDataAllowedAsOff;
    }
    //MTK END
    public void setDataAllowed(boolean enable, Message response) {
         if (DBG) log("setDataAllowed: enable=" + enable);
         mIsCleanupRequired = !enable;

        // M: [C2K][IRAT] set data allow through RIL arbitrator in IRAT.
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport() || !SvlteUtils.isActiveSvlteMode(mPhone)) {
            mPhone.mCi.setDataAllowed(enable, response);
        } else {
            getRilDcArbitrator().setDataAllowed(enable, response);
        }
         mInternalDataEnabled = enable;
    }

    @Override
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }

    @Override
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DcTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mReregisterOnReconnectFailure=" + mReregisterOnReconnectFailure);
        pw.println(" canSetPreferApn=" + mCanSetPreferApn);
        pw.println(" mApnObserver=" + mApnObserver);
        pw.println(" getOverallState=" + getOverallState());
        pw.println(" mDataConnectionAsyncChannels=%s\n" + mDataConnectionAcHashMap);
        pw.println(" mAttached=" + mAttached.get());
		pw.println(" mFdnObserver=" + mFdnObserver);/*zhiyuan 20151021 add Task730582 for isdm def_frameworks_allow_data_connection_with_FDN_activated*/
    }

    @Override
    public String[] getPcscfAddress(String apnType) {
        log("getPcscfAddress()");
        ApnContext apnContext = null;

        if(apnType == null){
            log("apnType is null, return null");

            return null;
        }

        if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_EMERGENCY)) {
            apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_EMERGENCY);
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_IMS)) {
            apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_IMS);
        } else {
            log("apnType is invalid, return null");
            return null;
        }

        if (apnContext == null) {
            log("apnContext is null, return null");
            return null;
        }

        DcAsyncChannel dcac = apnContext.getDcAc();
        String[] result = null;

        if (dcac != null) {
            result = dcac.getPcscfAddr();

            for (int i = 0; i < result.length; i++) {
                log("Pcscf[" + i + "]: " + result[i]);
            }
            return result;
        }
        return null;
    }

    @Override
    public void setImsRegistrationState(boolean registered) {
        log("setImsRegistrationState - mImsRegistrationState(before): "+ mImsRegistrationState
                + ", registered(current) : " + registered);

        if (mPhone == null) return;

        ServiceStateTracker sst = mPhone.getServiceStateTracker();
        if (sst == null) return;

        sst.setImsRegistrationState(registered);
    }

    /**
     * Read APN configuration from Telephony.db for Emergency APN
     * All opertors recognize the connection request for EPDN based on APN type
     * PLMN name,APN name are not mandatory parameters
     */
    private void initEmergencyApnSetting() {
        // Operator Numeric is not available when sim records are not loaded.
        // Query Telephony.db with APN type as EPDN request does not
        // require APN name, plmn and all operators support same APN config.
        // DB will contain only one entry for Emergency APN
        String selection = "type=\"emergency\"";
        Cursor cursor = mPhone.getContext().getContentResolver().query(
                Telephony.Carriers.CONTENT_URI, null, selection, null, null);

        if (cursor != null) {
            if (cursor.getCount() > 0) {
                if (cursor.moveToFirst()) {
                    mEmergencyApn = makeApnSetting(cursor);
                }
            }
            cursor.close();
        }
    }

    /**
     * Add the Emergency APN settings to APN settings list
     */
    private void addEmergencyApnSetting() {
        if(mEmergencyApn != null) {
            if(mAllApnSettings == null) {
                mAllApnSettings = new ArrayList<ApnSetting>();
            } else {
                boolean hasEmergencyApn = false;
                for (ApnSetting apn : mAllApnSettings) {
                    if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_EMERGENCY)) {
                        hasEmergencyApn = true;
                        break;
                    }
                }

                if(hasEmergencyApn == false) {
                    mAllApnSettings.add(mEmergencyApn);
                } else {
                    log("addEmergencyApnSetting - E-APN setting is already present");
                }
            }
        }
    }

    private void cleanUpConnectionsOnUpdatedApns(boolean tearDown) {
        if (DBG) log("cleanUpConnectionsOnUpdatedApns: tearDown=" + tearDown);
        if (mAllApnSettings.isEmpty()) {
            cleanUpAllConnections(tearDown, Phone.REASON_APN_CHANGED);
        } else {
            for (ApnContext apnContext : mApnContexts.values()) {
                if (VDBG) log("cleanUpConnectionsOnUpdatedApns for "+ apnContext);

                boolean cleanUpApn = true;
                ArrayList<ApnSetting> currentWaitingApns = apnContext.getWaitingApns();

                if ((currentWaitingApns != null) && (!apnContext.isDisconnected())) {
                    int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
                    ArrayList<ApnSetting> waitingApns = buildWaitingApns(
                            apnContext.getApnType(), radioTech);
                    if (VDBG) log("new waitingApns:" + waitingApns);
                    if (waitingApns.size() == currentWaitingApns.size()) {
                        cleanUpApn = false;
                        for (int i = 0; i < waitingApns.size(); i++) {
                            if (!currentWaitingApns.get(i).equals(waitingApns.get(i))) {
                                if (VDBG) log("new waiting apn is different at " + i);
                                cleanUpApn = true;
                                apnContext.setWaitingApns(waitingApns);
                                break;
                            }
                        }
                    }
                }

                if (cleanUpApn) {
                    apnContext.setReason(Phone.REASON_APN_CHANGED);
                    cleanUpConnection(true, apnContext);
                }
            }
        }

        if (!isConnected()) {
            stopNetStatPoll();
            stopDataStallAlarm();
        }

        mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;

        if (DBG) log("mDisconnectPendingCount = " + mDisconnectPendingCount);
        if (tearDown && mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }
    }

    /** M: worker handler to handle DB/IO access */
    private void createWorkerHandler() {
        if (mWorkerHandler == null) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    Looper.prepare();
                    mWorkerHandler = new WorkerHandler();
                    mWorkerHandler.sendEmptyMessage(DctConstants.EVENT_INIT_EMERGENCY_APN_SETTINGS);
                    Looper.loop();
                }
            };
            thread.start();
        }
    }

    private class WorkerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DctConstants.EVENT_DATA_ROAMING_CHANGED:
                    mDataRoamingEnabled = getDataOnRoamingEnabled();
                    log("WorkerHandler received EVENT_DATA_ROAMING_CHANGED, data roaming="
                            + mDataRoamingEnabled + ", roaming="
                            + mPhone.getServiceState().getRoaming());
                    if (mPhone.getServiceState().getRoaming()) {
                        DcTracker.this.sendMessage(obtainMessage(DctConstants.EVENT_ROAMING_ON));
                    } else {
                        DcTracker.this.sendMessage(obtainMessage(DctConstants.EVENT_ROAMING_OFF));
                    }
                    break;
                case DctConstants.EVENT_INIT_EMERGENCY_APN_SETTINGS:
                    // Add Emergency APN to APN setting list by default to support EPDN in sim absent cases
                    log("WorkerHandler received EVENT_INIT_EMERGENCY_APN_SETTINGS");
                    initEmergencyApnSetting();
                    addEmergencyApnSetting();
                    break;
            }
        }
    }

    /** M: throttling/high throughput
     *  Used to specified the maximum concurrent data connections
     */
    protected int getPdpConnectionPoolSize() {
        //here we keep the last DataConnection for low throughput APN
        //so the pool size is the maximum value - 1
        if (isSupportThrottlingApn()) {
            return THROTTLING_MAX_PDP_SIZE;
        } else {
            //here we keep the last DataConnection for low throughput APN
            //so the pool size is the maximum value - 1
            return PDP_CONNECTION_POOL_SIZE - 1 > 0 ? PDP_CONNECTION_POOL_SIZE - 1 : 1;
        }
    }

    private boolean isSupportThrottlingApn() {
        return (THROTTLING_APN_ENABLED
                || (SystemProperties.getInt(PROPERTY_THROTTLING_APN_ENABLED, 0) == 1));
    }

    private int generateDataConnectionId(String reqApnType, ApnSetting apnSetting) {
        int id = -1;
        // 0: internet, 1: tethering, 2~3: others, 4~6: IMS (non-throttling), 7: eMBMS
        AtomicInteger idGenerator = mOthersUniqueIdGenerator;
        for (String apn : HIGH_THROUGHPUT_APN) {
            if (apnSetting != null && apnSetting.canHandleType(apn)
                    && !PhoneConstants.APN_TYPE_EMERGENCY.equals(reqApnType)
                    && !apnSetting.canHandleType(PhoneConstants.APN_TYPE_IMS)) {
                idGenerator = mHighThroughputIdGenerator;
                log("generateDataConnectionId use high throughput DataConnection id generator");
                break;
            }
        }
        if (idGenerator != mHighThroughputIdGenerator) {
            for (String apn : IMS_APN) {
                if (PhoneConstants.APN_TYPE_EMERGENCY.equals(apn)
                        && !PhoneConstants.APN_TYPE_EMERGENCY.equals(reqApnType)) {
                    //skip since not request emergency apn
                } else {
                    if (apnSetting != null && apnSetting.canHandleType(apn)) {
                        int idStart = MIN_ID_IMS_TROUGHPUT;
                        if (PhoneConstants.APN_TYPE_EMERGENCY.equals(apn)) {
                            idStart += 1;
                        }
                        // Set IMS: 4, EIMS: 5 for interface (fixed the interface)
                        mImsUniqueIdGenerator.set(idStart);
                        idGenerator = mImsUniqueIdGenerator;
                        log("generateDataConnectionId use ims DataConnection id generator");
                        break;
                    }
                }
            }
        }

        id = idGenerator.getAndIncrement();
        if (idGenerator == mHighThroughputIdGenerator && id > MAX_ID_HIGH_TROUGHPUT) {
            loge("Max id of highthrouthput is " + MAX_ID_HIGH_TROUGHPUT
                    + ", but generated id is " + id);
            idGenerator.getAndDecrement();
            id = -1;
        } else if (idGenerator == mOthersUniqueIdGenerator && id > MAX_ID_OTHERS_TROUGHPUT) {
            loge("Max id of others is " + MAX_ID_OTHERS_TROUGHPUT
                    + ", but generated id is " + id);
            idGenerator.getAndDecrement();
            id = -1;
        } else if (idGenerator == mImsUniqueIdGenerator && id > MAX_ID_IMS_TROUGHPUT) {
            loge("Max id of others is " + MAX_ID_IMS_TROUGHPUT
                    + ", but generated id is " + id);
            idGenerator.getAndDecrement();
            id = -1;
        }
        if (DBG) {
            log("generateDataConnectionId id = " + id);
        }
        return id;
    }

    /**
     * M: get the string of ims ApnSetting in the list.
     *
     * @param apnSettings
     * @return
     */
    private String getIMSApnSetting(ArrayList<ApnSetting> apnSettings) {
        if (apnSettings == null || apnSettings.size() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ApnSetting t : apnSettings) {
            if (t.canHandleType("ims")) {
                sb.append(apnToStringIgnoreName(t));
            }
        }
        log("getIMSApnSetting, apnsToStringIgnoreName: sb = " + sb.toString());
        return sb.toString();
    }

    private boolean isIMSApnSettingChanged(ArrayList<ApnSetting> prevApnList,
                                        ArrayList<ApnSetting> currApnList) {
        boolean bIMSApnChanged = false;
        String prevIMSApn = getIMSApnSetting(prevApnList);
        String currIMSApn = getIMSApnSetting(currApnList);

        if (!prevIMSApn.isEmpty()) {
            if (!TextUtils.equals(prevIMSApn, currIMSApn)) {
                bIMSApnChanged = true;
            }
        }

        return bIMSApnChanged;
    }

    /**
     * M: Concat the string of all ApnSetting in the list.
     *
     * @param apnSettings
     * @return
     */
    private String apnsToStringIgnoreName(ArrayList<ApnSetting> apnSettings) {
        if (apnSettings == null || apnSettings.size() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ApnSetting t : apnSettings) {
            sb.append(apnToStringIgnoreName(t));
        }
        log("apnsToStringIgnoreName: sb = " + sb.toString());
        return sb.toString();
    }

    /**
     * M: Similar as ApnSetting.toString except the carrier is not considerred
     * because some operator need to change the APN name when locale changed.
     *
     * @param apnSetting
     * @return
     */
    private String apnToStringIgnoreName(ApnSetting apnSetting) {
        if (apnSetting == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(apnSetting.id)
        .append(", ").append(apnSetting.numeric)
        .append(", ").append(apnSetting.apn)
        .append(", ").append(apnSetting.proxy)
        .append(", ").append(apnSetting.mmsc)
        .append(", ").append(apnSetting.mmsProxy)
        .append(", ").append(apnSetting.mmsPort)
        .append(", ").append(apnSetting.port)
        .append(", ").append(apnSetting.authType).append(", ");
        for (int i = 0; i < apnSetting.types.length; i++) {
            sb.append(apnSetting.types[i]);
            if (i < apnSetting.types.length - 1) {
                sb.append(" | ");
            }
        }
        sb.append(", ").append(apnSetting.protocol);
        sb.append(", ").append(apnSetting.roamingProtocol);
        sb.append(", ").append(apnSetting.carrierEnabled);
        sb.append(", ").append(apnSetting.bearerBitmask);
        log("apnToStringIgnoreName: sb = " + sb.toString());
        return sb.toString();
    }

    private boolean handleApnConflict(ApnContext apnContext, ApnSetting apnSetting) {
        if (DUALTALK_SPPORT) {
            int phoneCount = TelephonyManager.getDefault().getPhoneCount();
            log("handleApnConflict");

            for (int i = 0; i < phoneCount; i++) {
                ConcurrentHashMap<String, ApnContext> apnContexts =
                        DctController.getInstance().getApnContexts(i);

                if (apnContexts != null) {
                    for (ApnContext currApnCtx : apnContexts.values()) {
                        ApnSetting apn = currApnCtx.getApnSetting();

                        if (currApnCtx == apnContext) {
                            continue;
                        }
                        if ((apn != null) && !currApnCtx.isDisconnected()
                                    //&& !apn.equals(apnSetting)
                                    //&& !apn.apn.equals(apnSetting.apn)
                                    && (isSameProxy(apn, apnSetting))) {

                            if (DBG) {
                                log("Found conflict APN " + currApnCtx.getApnType());
                            }

                            apnContext.setState(DctConstants.State.IDLE);
                            if (apnContext.priority > currApnCtx.priority) {
                                if (DBG) {
                                    log("Clean up current apn context");
                                }
                                cleanUpConnection(true, currApnCtx);
                                mConflictApn = true;
                            } else {
                                if (DBG) {
                                    log("Waiting for high pripority apn context");
                                }
                                startAlarmForReconnect(getApnDelay(), apnContext);
                            }
                            return true;
                        }
                    }
                } else {
                    loge("Phone" + i + " apnContext is null");
                }
            }
        }
        return false;
    }

    /**
     * M:For Multiple PDP Context.
     *
     * @param apn1
     * @param apn2
     * @return true if the two input apn is the same
     */
    private boolean isSameProxy(ApnSetting apn1, ApnSetting apn2) {
        if (apn1 == null || apn2 == null) {
            return false;
        }
        String proxy1;
        if (apn1.canHandleType(PhoneConstants.APN_TYPE_MMS)) {
            proxy1 = apn1.mmsProxy;
        } else {
            proxy1 = apn1.proxy;
        }
        String proxy2;
        if (apn2.canHandleType(PhoneConstants.APN_TYPE_MMS)) {
            proxy2 = apn2.mmsProxy;
        } else {
            proxy2 = apn2.proxy;
        }
        /* Fix NULL Pointer Exception problem: proxy1 may be null */
        if (proxy1 != null && proxy2 != null && !proxy1.equals("") && !proxy2.equals("")) {
            return proxy1.equalsIgnoreCase(proxy2);
        } else {
            log("isSameProxy():proxy1=" + proxy1 + ",proxy2=" + proxy2);
            return false;
        }
    }

    public DcFailCause getLastDataConnectionFailCause(String apnType) {
        DcFailCause failCause = DcFailCause.NONE;
        ApnContext apnContext = mApnContexts.get(apnType);
        DcAsyncChannel dcac = apnContext.getDcAc();
        if (dcac == null) {
            loge("get dcac failed, apnType: " + apnType);
            failCause = DcFailCause.UNKNOWN;
        } else {
            failCause = dcac.getLastDataConnectionFailCauseSync();
        }
        return failCause;
    }

    public boolean isOnlyIMSorEIMSPdnConnected() {
        boolean bIsOnlyIMSorEIMSConnected = false;
        if (MTK_IMS_SUPPORT) {
            for (ApnContext apnContext : mApnContexts.values()) {
                String apnType = apnContext.getApnType();
                if (!apnContext.isDisconnected()) {
                    if (apnType.equals(PhoneConstants.APN_TYPE_IMS) == false &&
                        apnType.equals(PhoneConstants.APN_TYPE_EMERGENCY) == false) {
                        if (DBG) log("apnType: " + apnType + " is still conntected!!");
                    // At least one context (not ims or Emergency) was not disconnected return false
                        bIsOnlyIMSorEIMSConnected = false;
                        break;
                    } else { //IMS or/and Emergency is/are still connected
                        bIsOnlyIMSorEIMSConnected = true;
                    }
                }
            }
        }
        return bIsOnlyIMSorEIMSConnected;
    }

    private boolean isSkipIMSorEIMSforCleanUpDataConns(String reason, String apnType) {
        boolean bRet = false;
        if (PhoneConstants.APN_TYPE_IMS.equals(apnType) ||
            PhoneConstants.APN_TYPE_EMERGENCY.equals(apnType)) {
            if (Phone.REASON_RADIO_TURNED_OFF.equals(reason)  ||
                Phone.REASON_PDP_RESET.equals(reason)) {
                bRet = true;
                log("reason: " + reason + ", apnType: " + apnType);
            }
        }
        return bRet;
    }

    // M: cc33.
    protected boolean isCctSmRetry() {
        //M: CC33(Thirty-three) retry
        boolean cctSmRetry = false;

        if (!BSP_PACKAGE && mGsmDctExt != null) {

            try {
                cctSmRetry = mGsmDctExt.isCctSmRetry(); // default is false
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            int userSet = SystemProperties.getInt("persist.data.cc33.support", -1);
            if (userSet != -1) {
                cctSmRetry = (userSet == 1) ? true : false;
            }

            if (DBG) log("isCctSmRetry: " + cctSmRetry);
        }
        return cctSmRetry;
    }

    //begin zhiyuan 20151029 add isdm persist.data.cc33.support for task730295
    private void clearCodes33Or29Warning(DcFailCause pdpRejectCause) {
        log("clearCodes33Or29Warning : " + pdpRejectCause);

        String message = null;
        if (pdpRejectCause == DcFailCause.USER_AUTHENTICATION) {
            message = mPhone.getContext().getString(
                com.android.internal.R.string.user_authentication);
        } else if (pdpRejectCause == DcFailCause.SERVICE_OPTION_NOT_SUBSCRIBED) {
            message = mPhone.getContext().getString(
                com.android.internal.R.string.service_option_not_subscribed);
        }

        if (message != null) {
	        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mPhone.getContext());
	        alertDialogBuilder.setTitle(mPhone.getContext().getString(com.android.internal.R.string.warning));
	        alertDialogBuilder.setMessage(message);
	        alertDialogBuilder.setPositiveButton(android.R.string.ok, null);

	        AlertDialog DataRejectDialog = alertDialogBuilder.create();
	        DataRejectDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
	        DataRejectDialog.setCanceledOnTouchOutside(false);
	        DataRejectDialog.show();
            TextView textView = (TextView) DataRejectDialog.findViewById(android.R.id.message);
            if(textView != null){
                textView.setTextSize(14);
            }
        }
    }
    //end zhiyuan 20151029 add isdm persist.data.cc33.support for task730295

    // MTK
    public void deactivatePdpByCid(int cid) {
        mPhone.mCi.deactivateDataCall(cid, RILConstants.DEACTIVATE_REASON_PDP_RESET, obtainMessage(DctConstants.EVENT_RESET_PDP_DONE, cid, 0));
    }

    // M: [Epdg]
    private boolean checkEpdgHandover() {

        // only IMS needs hand over now
        NetworkInfo nwInfo = mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_IMS);
        if (nwInfo != null && nwInfo.isConnected()) {
            SystemProperties.set("ril.data.ishandover", "1");
            return true;
        }

        return false;
    }
    // M:[C2K][IRAT] code start @{
    // This will be triggered only C2K RAT(single mode) change and LTE first
    // register(the RAT change is blocked by RatManager), change between 3GPP
    // and 3GPP2 will be handled in IRAT scenario.
    private void onIratDataRatChanged(int sourceRat, int targetRat) {
        log("[IRAT_DcTrakcer] onIratDataRatChanged: mIsDuringIrat = "
                + mIsDuringIrat + ",sourceRat = "
                + ServiceState.rilRadioTechnologyToString(sourceRat)
                + ", targetRat = "
                + ServiceState.rilRadioTechnologyToString(targetRat));
        if (mIsDuringIrat) {
            log("[IRAT_DcTrakcer] Ignore RAT change during IRAT.");
            return;
        }

        if (targetRat == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            log("[MD_IRAT_DcTrakcer] De-register on C2K/LTE network, do thing.");
        } else if (sourceRat == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            log("[MD_IRAT_DcTrakcer] First register on C2K/LTE network.");
            // May need to update phone if phone type changed.
            updatePhoneIfNeeded(SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId()).getPsPhone());
            updateIccRecordAndApn(sourceRat, targetRat);
            setupDataOnConnectableApns(Phone.REASON_NW_TYPE_CHANGED);
        } else if (isCdmaFallbackCase(sourceRat, targetRat)) {
            // RAT change between eHRPD and HRPD.
            log("[MD_IRAT_DcTrakcer] Fallback happens.");
            Message disconnectMessage = Message.obtain(this,
                    LteDcConstants.EVENT_RETRY_SETUP_DATA_FOR_IRAT);
            cleanUpAllConnections(Phone.REASON_CDMA_FALLBACK_HAPPENED,
                    disconnectMessage);
            mSetupDataBlocked = true;
            updateIccRecordAndApn(sourceRat, targetRat);
            notifyDataConnection(Phone.REASON_CDMA_IRAT_ENDED);
        } else {
            log("[MD_IRAT_DcTrakcer] Normal data RAT change.");
            setupDataOnConnectableApns(Phone.REASON_NW_TYPE_CHANGED);
            notifyDataConnection(Phone.REASON_CDMA_IRAT_ENDED);
        }
    }

    @Override
    public void onIratStarted(Object info) {
        log("[IRAT_DcTracker] onIratStarted: info = " + info);
        mIsDuringIrat = true;
        if (isConnected()) {
            log("[IRAT_DcTracker] onIratStarted: stop polling");
            stopNetStatPoll();
            stopDataStallAlarm();
            // NOTE: DONOT notify disconnect status during IRAT
        }
    }

    // TODO: check if there is a message before IRAT ended message may cause
    // possible error, such as RatManager.IRAT_END -> APN_CHANGE ->
    // DcTracker.IRAT_END. Need to make it as function call if there is risks.
    @Override
    public void onIratEnded(Object info) {
        log("[IRAT_DcTracker] onIratEnded: info = " + info);

        MdIratInfo mdIratInfo = (MdIratInfo) info;
        mIsDuringIrat = false;
        // TODO: force to set it to attach state to notify data connected.
        if (!mdIratInfo.type.isFailCase()) {
            mAttached.set(true);
            if (mAutoAttachOnCreationConfig) {
                mAutoAttachOnCreation.set(true);
            }
        }

        updatePhoneIfNeeded(SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId()).getPsPhone());

        if (mdIratInfo.type.isFallbackCase()) {
            Message disconnectMessage = Message.obtain(this,
                    LteDcConstants.EVENT_RETRY_SETUP_DATA_FOR_IRAT);
            resetDcAndApnContext();
            cleanUpAllConnections(Phone.REASON_CDMA_IRAT_ENDED,
                    disconnectMessage);
            updateIccRecordAndApn(mdIratInfo.sourceRat, mdIratInfo.targetRat);
            // Block other setup data call
            mSetupDataBlocked = true;
            // We have handle APN change in update IccRecord.
            mHasPendingApnChange = false;
        }

        if (isConnected()) {
            log("[IRAT_DcTracker] onIratEnded: start polling");
            startNetStatPoll();
            startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
        }

        log("[IRAT_DcTracker] onIratEnded: mHasPendingApnChange: " + mHasPendingApnChange
            + ", mHasPendingInitialApnRequest: " + mHasPendingInitialApnRequest
            + ", mHasPendingLteRecordLoaded: " + mHasPendingLteRecordLoaded);

        // Handle pending operation during IRAT.
        if (mHasPendingApnChange) {
            removeMessages(DctConstants.EVENT_APN_CHANGED);
            sendMessage(obtainMessage(DctConstants.EVENT_APN_CHANGED));
            mHasPendingApnChange = false;
        }

        if (mHasPendingInitialApnRequest) {
            setInitialAttachApn();
            mHasPendingInitialApnRequest = false;
        }

        if (mHasPendingLteRecordLoaded) {
            onLteRecordsLoaded();
            mHasPendingLteRecordLoaded = false;
        }
    }

    private void onLteRecordsLoaded() {
        if (isDuringIrat()) {
            log("onLteRecordsLoaded setInitialApn: but isDuringIrat.");
            mHasPendingLteRecordLoaded = true;
            return;
        }

        IccRecords r = mLteIccRecords.get();
        String operatorNumeric = (r != null) ? r.getOperatorNumeric() : "";
        log("[IRAT_DcTracker] onLteRecordsLoaded: operatorNumeric = " + operatorNumeric);
        if (operatorNumeric == null || operatorNumeric.length() == 0) {
            log("onLteRecordsLoaded setInitialApn: but no operator numeric");
            return;
        }

        mInitialAttachApnSetting = makeInitialAttachApn();
        mSvlteOperatorNumeric = operatorNumeric;
        log("[IRAT_DcTracker] onLteRecordsLoaded: mInitialAttachApnSetting = "
                + mInitialAttachApnSetting);

        IaExtendParam param = new IaExtendParam(operatorNumeric);
        if (mInitialAttachApnSetting != null) {
            mSvlteIaApnSetting = mInitialAttachApnSetting;
            param.mCanHandleIms = mSvlteIaApnSetting.canHandleType(PhoneConstants.APN_TYPE_IMS);
            getRilDcArbitrator().setInitialAttachApn(
                    mInitialAttachApnSetting.apn,
                    mInitialAttachApnSetting.protocol,
                    mInitialAttachApnSetting.authType,
                    mInitialAttachApnSetting.user,
                    mInitialAttachApnSetting.password,
                    (Object) param, null);
        } else {
            log("[IRAT_DcTracker] onLteRecordsLoaded: There in no available apn, use empty");
            if (operatorNumeric != null) {
                getRilDcArbitrator().setInitialAttachApn("",
                        RILConstants.SETUP_DATA_PROTOCOL_IP, -1, "", "", (Object) param, null);
            }
        }

        // When LTE record loaded, we need to trigger setup data
        setupDataOnConnectableApns(Phone.REASON_SIM_LOADED);
    }

    private ApnSetting makeInitialAttachApn() {
        ApnSetting potentialApnSetting = null;
        ApnSetting iaApnSetting = null;
        IccRecords r = mLteIccRecords.get();
        String operator = (r != null) ? r.getOperatorNumeric() : "";
        log("[IRAT_DcTracker] makeInitialAttachApn with operator = " + operator);
        if (operator != null) {
            String selection = "numeric = '" + operator + "'";
            log("[IRAT_DcTracker] makeInitialAttachApn: selection=" + selection);

            Cursor cursor = mPhone
                    .getContext()
                    .getContentResolver()
                    .query(Telephony.Carriers.CONTENT_URI, null, selection,
                            null, null);

            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    ArrayList<ApnSetting> apnSettings = createApnListWithRecord(
                            cursor, r);
                    if (apnSettings != null && apnSettings.size() > 0) {
                        // Use the first IA type APN as IA, or else use the first default APN.
                        for (ApnSetting apnSetting : apnSettings) {
                            if (apnSetting.canHandleType(PhoneConstants.APN_TYPE_IA)) {
                                iaApnSetting = apnSetting;
                                break;
                            } else if (apnSetting
                                    .canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
                                if (potentialApnSetting == null) {
                                    potentialApnSetting = apnSetting;
                                }
                            }
                        }
                        log("[IRAT_DcTracker] makeInitialAttachApn: iaApnSetting = "
                                + iaApnSetting
                                + ",potentialApnSetting = "
                                + potentialApnSetting);
                        if (iaApnSetting == null) {
                            if (mPreferredApn != null) {
                                iaApnSetting = mPreferredApn;
                            } else if (potentialApnSetting != null) {
                                iaApnSetting = potentialApnSetting;
                            } else {
                                iaApnSetting = apnSettings.get(0);
                            }
                        }
                    }
                }
                cursor.close();
            }
        }

        // use the IA emergency apn if it is used as IA apn.
        if (iaApnSetting == null && mInitialAttachApnSetting != null &&
            mInitialAttachApnSetting.canHandleType(PhoneConstants.APN_TYPE_EMERGENCY)) {
            iaApnSetting = mInitialAttachApnSetting;
        }

        return iaApnSetting;
    }

    private void updateInitialAttachApnForSvlte() {
        ApnSetting newIaApnSetting = makeInitialAttachApn();
        log("[IRAT_DcTracker] updateInitialAttachForSvlte: newIaApnSetting="
                + newIaApnSetting);
        if (newIaApnSetting != null) {
            mInitialAttachApnSetting = newIaApnSetting;
            mSvlteIaApnSetting = newIaApnSetting;
        }
    }

    private IRilDcArbitrator getRilDcArbitrator() {
        SvltePhoneProxy phoneProxy = SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId());
        return phoneProxy.getRilDcArbitrator();
    }

    private void updateIccRecordAndApn(int sourceRat, int targetRat) {
        log("[IRAT_DcTracker] updateIccRecordAndApn: sourceRat = " + sourceRat
                + ", targetRat = " + targetRat);

        // Update UICC family with right MCCMNC.
        updateIccRecord(targetRat);

        if (mPhone instanceof CDMALTEPhone) {
            ((CDMALTEPhone) mPhone).updateCurrentCarrierInProvider();
        } else if (mPhone instanceof GSMPhone) {
            ((GSMPhone) mPhone).updateCurrentCarrierInProvider();
        } else if (mPhone instanceof CDMAPhone) {
            ((CDMAPhone) mPhone).updateCurrentCarrierInProvider();
        } else {
            log("Phone object is not MultiSim. This should not hit!!!!");
        }

        // Trigger a new APN change flow to re-create all apn list and set
        // initial attach APNs, call API directly instead of send message to
        // trigger APN change immediately.
        onApnChanged();
    }

    private void updateIccRecord(int targetRat) {
        if (mUiccController == null) {
            return;
        }

        final int family = getUiccFamilyByRat(targetRat);
        IccRecords newIccRecords = getUiccRecords(family);
        IccRecords r = mIccRecords.get();
        log("[IRAT_DcTracker] updateIccRecord: targetRat = " + targetRat
                + ", family = " + family + ", newIccRecords = " + newIccRecords
                + ", r = " + r);

        if (r != newIccRecords) {
            if (r != null) {
                log("[IRAT_DcTracker] Removing stale icc objects.");
                //r.unregisterForRecordsLoaded(this);
                mIccRecords.set(null);
            }
            if (newIccRecords != null) {
                log("[IRAT_DcTracker] New records found");
                mIccRecords.set(newIccRecords);
                // M: SIM records loaded event needs to wait for subId ready,
                // so trigger onRecordsLoaded should be notified by SubscriptionInfoUpdater.
                //newIccRecords.registerForRecordsLoaded(this,
                //        DctConstants.EVENT_RECORDS_LOADED, null);

                // Trigger records loaded during IRAT because the records are
                // loaded already.
                onRecordsLoaded();
            }
        }
    }

    /**
     * M: Reset DC and APN context, when LTE <-> HRPD irat end to release sourse
     * connection.
     */
    private void resetDcAndApnContext() {
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.getDcAc() != null) {
                log("[IRAT_DcTracker] resetDcAndApnContext: apnContext = "
                        + apnContext);
                apnContext.getDcAc().reqReset();
                apnContext.setDataConnectionAc(null);
                apnContext.setApnSetting(null);
                apnContext.setState(DctConstants.State.IDLE);
            }
        }
    }

    private void registerForEventsAfterPhoneCreated() {
        registerForVoiceCallEventPeer();
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            SvltePhoneProxy svltePhoneProxy = SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId());
            mIratController = svltePhoneProxy.getIratController();
            mIratController.registerForRatChanged(this,
                    LteDcConstants.EVENT_IRAT_DATA_RAT_CHANGED, null);
            mIratController.addOnIratEventListener(this);

            mIratDataSwitchHelper = svltePhoneProxy.getIratDataSwitchHelper();
            mIratDataSwitchHelper.registerForDataConnectionAttached(this,
                   DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null);
            mIratDataSwitchHelper.registerForDataConnectionDetached(this,
                   DctConstants.EVENT_DATA_CONNECTION_DETACHED, null);
            registerForVoiceCallEventSvlte();
            // Gsm only event
            PhoneBase pb = svltePhoneProxy.getLtePhone();
            pb.mCi.registerForRemoveRestrictEutran(this,
                    DctConstants.EVENT_REMOVE_RESTRICT_EUTRAN, null);
            pb.mCi.registerForResetAttachApn(this, DctConstants.EVENT_RESET_ATTACH_APN, null);

            log("[IRAT_DcTracker] registerForEventsAfterPhoneCreated: mIratController = "
                    + mIratController + ", mIratDataSwitchHelper = "
                    + mIratDataSwitchHelper);
        }
    }

    private void unregisterForEventsAfterPhoneCreated() {
        unregisterForVoiceCallEventPeer();
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            log("[IRAT_DcTracker] unregisterForEventsAfterPhoneCreated.");
            mIratController.unregisterForRatChanged(this);
            mIratController.removeOnIratEventListener(this);

            mIratDataSwitchHelper.unregisterForDataConnectionAttached(this);
            mIratDataSwitchHelper.unregisterForDataConnectionDetached(this);

            PhoneBase pb = SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId()).getLtePhone();
            // Gsm only event
            pb.mCi.unregisterForRemoveRestrictEutran(this);
            pb.mCi.unregisterForResetAttachApn(this);

            IccRecords r = mLteIccRecords.get();
            if (r != null) {
                r.unregisterForRecordsLoaded(this);
                mLteIccRecords.set(null);
            }
        }
    }

    // TODO: think a better way to optimize data fallback judgement.
    private boolean isCdmaFallbackCase(int sourceRat, int targetRat) {
        final int sourceFamily = getUiccFamilyByRat(sourceRat);
        final int targetFamily = getUiccFamilyByRat(targetRat);
        boolean isFallback = (sourceFamily != targetFamily);
        log("[IRAT_DcTracker]isIratFallbackCase: sourceFamily = "
                + sourceFamily + ", targetFamily = " + targetFamily);
        if ((sourceRat == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD
                && targetFamily == UiccController.APP_FAM_3GPP2)
                || (targetRat == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD
                && sourceFamily == UiccController.APP_FAM_3GPP2)) {
            return true;
        }

        return false;
    }

    private int getUiccFamily(PhoneBase phone) {
        int family = UiccController.APP_FAM_3GPP;
        if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport() || !SvlteUtils.isActiveSvlteMode(phone)) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                family = UiccController.APP_FAM_3GPP2;
            }
        } else {
            int radioTech = getRilDataRadioTechnology(phone);
            if (radioTech != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                family = getUiccFamilyByRat(radioTech);
            } else {
                // Return 3GPP2 family if the phone is CDMA.
                if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    family = UiccController.APP_FAM_3GPP2;
                }
            }
            log("[IRAT_DcTracker] getUiccFamily: radioTech = " + radioTech
                    + ", family=" + family + ", phone = " + phone);
        }
        return family;
    }

    private int getRilDataRadioTechnology(PhoneBase phone) {
        int testRat = SystemProperties.getInt(PROP_NAME_SET_TEST_RAT, 0);
        if (testRat != 0) {
            log("[IRAT_DcTracker] Use test RAT " + testRat + " instead of "
                    + phone.getServiceState().getRilDataRadioTechnology()
                    + " for test.");
            return testRat;
        }
        return phone.getServiceState().getRilDataRadioTechnology();
    }

    private ArrayList<ApnSetting> createApnListWithRecord(Cursor cursor,
            IccRecords r) {
        ArrayList<ApnSetting> mnoApns = new ArrayList<ApnSetting>();
        ArrayList<ApnSetting> mvnoApns = new ArrayList<ApnSetting>();

        if (cursor.moveToFirst()) {
            do {
                ApnSetting apn = makeApnSetting(cursor);
                if (apn == null) {
                    continue;
                }

                if (apn.hasMvnoParams()) {
                    if (r != null
                            && ApnSetting.mvnoMatches(r, apn.mvnoType, apn.mvnoMatchData)) {
                        mvnoApns.add(apn);
                    }
                } else {
                    mnoApns.add(apn);
                }
            } while (cursor.moveToNext());
        }

        ArrayList<ApnSetting> result = mvnoApns.isEmpty() ? mnoApns : mvnoApns;
        log("[IRAT_DcTracker] createApnListWithRecord: X result=" + result);
        return result;
    }

    /**
     * M: Update phone instance of DcTracker.
     * @param newPsPhone The new PS phone.
     */
    public void updatePhoneIfNeeded(PhoneBase newPsPhone) {
        log("[IRAT_DcTracker]updatePhoneIfNeeded: mPhone = " + mPhone
                + ", newPhone=" + newPsPhone + ", mIsPsRestricted = "
                + mIsPsRestricted);

        // Only need to update PS phone when phone type changed.
        if (mPhone.getPhoneType() == newPsPhone.getPhoneType()) {
            log("[IRAT_DcTracker] Doesn't need to update phone for same phone type.");
            return;
        }

        unregisterForAllEvents();
        mPhone = newPsPhone;
        registerForAllEvents();
        registerForVoiceCallEventSvlte();

        // Reset PS restricted state when phone updated.
        mIsPsRestricted = false;

        // update DcController's phone
        mDcc.updatePhone(newPsPhone);

        // update DataConnection's phone
        for (DataConnection dc : mDataConnections.values()) {
            dc.updatePhone(newPsPhone);
        }

        // update DcTesterDeactivateAll's phone
        mDcTesterFailBringUpAll.updatePhone(newPsPhone);

        // update DcTesterDeactivateAll's phone
        mDcTesterFailBringUpAll.updatePhone(newPsPhone);
    }

    /**
     * M: Get Uicc Family by radio technology.
     * @param radioTech Ratio technology.
     * @return APP family of the RAT.
     */
    private static int getUiccFamilyByRat(int radioTech) {
        if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            return APP_FAM_UNKNOWN;
        }

        if ((radioTech >= ServiceState.RIL_RADIO_TECHNOLOGY_IS95A
                && radioTech <= ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A)
                || radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B) {
            return UiccController.APP_FAM_3GPP2;
        } else {
            return UiccController.APP_FAM_3GPP;
        }
    }

    // M: Is concurrent voice and data allowed with peer phone.
    //    For common project, return dual talk support or not.
    //    For SRLTE, return false.
    //    For SVLTE, if CS and PS phone is the same type(data is on LTE, voice is on GSM),
    //               return false, else return true.
    private boolean isConcurrentVoiceAndDataAllowedWithPeer() {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (CdmaFeatureOptionUtils.isSrlteSupport()) {
                return false;
            } else if (CdmaFeatureOptionUtils.isSvlteSupport()) {
                if (isSamePhoneTypeWithPeer()) {
                    return false;
                } else {
                    return true;
                }
            }
        } else {
            if (SystemProperties.getInt("ro.mtk_dt_support", 0) == 1) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    // M: This is only for CDMA LTE DC architeture,
    //    return true if CS and PS phone is the same type.
    private boolean isSamePhoneTypeWithPeer() {
        PhoneBase peerCsPhone = null;
        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            Phone phone = PhoneFactory.getPhone(i);
            int phoneId = mPhone.getPhoneId();
            phoneId = SvlteUtils.getSvltePhoneIdByPhone(mPhone);
            if (i != phoneId && phone != null) {
                peerCsPhone = (PhoneBase) ((SvltePhoneProxy) phone).getCsPhone();
            }
        }
        if (peerCsPhone != null) {
            return mPhone.getPhoneType() == peerCsPhone.getPhoneType();
        }
        return false;
    }

    private void registerForVoiceCallEventSvlte() {
        CallTracker ct = SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId()).getCallTracker();
        registerForCallEvents(ct, DctConstants.EVENT_VOICE_CALL_STARTED,
                DctConstants.EVENT_VOICE_CALL_ENDED);
    }

    private void unregisterForVoiceCallEventSvlte() {
        CallTracker ct = SvlteUtils.getSvltePhoneProxy(mPhone.getPhoneId()).getCallTracker();
        unregisterCallEvents(ct);
    }

    private void registerForCallEvents(CallTracker ct, int callStartEvent, int callEndEvent) {
        if (ct != null) {
            ct.registerForVoiceCallStarted(this, callStartEvent, null);
            ct.registerForVoiceCallEnded(this, callEndEvent, null);
        }
    }

    private void unregisterCallEvents(CallTracker ct) {
        if (ct != null) {
            ct.unregisterForVoiceCallStarted(this);
            ct.unregisterForVoiceCallEnded(this);
        }
    }

    /**
     * M: Update CS Phone for SVLTE, register call event with new CS phone.
     * @param newCsPhone New CS phone.
     */
    public void updateCsPhoneForSvlte(PhoneBase newCsPhone) {
        // Un-Register previous registered call events.
        unregisterForVoiceCallEventSvlte();
        log("[IRAT_DcTracker] updateCsPhoneForSvlte: newCsPhone = " + newCsPhone);

        CallTracker ct = newCsPhone.getCallTracker();
        registerForCallEvents(ct, DctConstants.EVENT_VOICE_CALL_STARTED,
                DctConstants.EVENT_VOICE_CALL_ENDED);
    }

    private void registerForVoiceCallEventPeer() {
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            int phoneCount = TelephonyManager.getDefault().getPhoneCount();
            int phoneId = mPhone.getPhoneId();
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                phoneId = SvlteUtils.getSvltePhoneIdByPhone(mPhone);
            }
            for (int i = 0; i < phoneCount; i++) {
                if (i != phoneId) {
                    Phone phone = PhoneFactory.getPhone(i);
                    if (phone != null) {
                        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                            // Fix the problem when CT 4G card cs phone update,
                            // and it's peer phone register wrong calltracker.
                            log("Register both side for csPhone update");
                            CallTracker gsmCallTracker =
                                SvlteUtils.getSvltePhoneProxy(i).getLtePhone().getCallTracker();
                            registerForCallEvents(gsmCallTracker,
                                    DctConstants.EVENT_VOICE_CALL_STARTED_PEER,
                                    DctConstants.EVENT_VOICE_CALL_ENDED_PEER);

                            CallTracker cdmaCallTracker =
                                SvlteUtils.getSvltePhoneProxy(i).getNLtePhone().getCallTracker();
                            registerForCallEvents(cdmaCallTracker,
                                    DctConstants.EVENT_VOICE_CALL_STARTED_PEER,
                                    DctConstants.EVENT_VOICE_CALL_ENDED_PEER);
                        } else {
                            PhoneBase pb = (PhoneBase) ((PhoneProxy) phone).getActivePhone();
                            CallTracker ct = pb.getCallTracker();
                            registerForCallEvents(ct, DctConstants.EVENT_VOICE_CALL_STARTED_PEER,
                                    DctConstants.EVENT_VOICE_CALL_ENDED_PEER);
                        }
                    }
                }
            }
        }
    }

    private void unregisterForVoiceCallEventPeer() {
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            int phoneCount = TelephonyManager.getDefault().getPhoneCount();
            int phoneId = mPhone.getPhoneId();
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                phoneId = SvlteUtils.getSvltePhoneIdByPhone(mPhone);
            }
            for (int i = 0; i < phoneCount; i++) {
                if (i != phoneId) {
                    Phone phone = PhoneFactory.getPhone(i);
                    if (phone != null) {
                        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                            log("Unregister both side for csPhone update");
                            CallTracker gsmCallTracker =
                                SvlteUtils.getSvltePhoneProxy(i).getLtePhone().getCallTracker();
                            unregisterCallEvents(gsmCallTracker);

                            CallTracker cdmaCallTracker =
                                SvlteUtils.getSvltePhoneProxy(i).getNLtePhone().getCallTracker();
                            unregisterCallEvents(cdmaCallTracker);
                        } else {
                            PhoneBase pb = (PhoneBase) ((PhoneProxy) phone).getActivePhone();
                            CallTracker ct = pb.getCallTracker();
                            unregisterCallEvents(ct);
                        }
                    }
                }
            }
        }
    }

    /**
     * M: Get peer phone's call state.
     *    Return the call state of the peer phone which call state is not idle.
     *    Return idle, if all peer phones are idle.
     * @return return peer phone's call state
     */
    private PhoneConstants.State getCallStatePeer() {
        PhoneConstants.State callState = PhoneConstants.State.IDLE;
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            int phoneCount = TelephonyManager.getDefault().getPhoneCount();
            int phoneId = mPhone.getPhoneId();
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                phoneId = SvlteUtils.getSvltePhoneIdByPhone(mPhone);
            }
            for (int i = 0; i < phoneCount; i++) {
                if (i != phoneId) {
                    Phone phone = PhoneFactory.getPhone(i);
                    if (phone != null) {
                        PhoneConstants.State peerCallState = phone.getState();
                        if (peerCallState != PhoneConstants.State.IDLE) {
                            callState = peerCallState;
                            break;
                        }
                    }
                }
            }
        }
        return callState;
    }

    // M: isVsimActive.
    public boolean isVsimActive(int phoneId) {
        int phoneNum = TelephonyManager.getDefault().getPhoneCount();
        String vsimEnabled = null;
        int act = 0 ;
        log("check vsim request by phone " + phoneId);

        for (int id = 0 ; id < phoneNum ; id++) {
            if (id != phoneId) {
                vsimEnabled = TelephonyManager.getDefault().getTelephonyProperty(
                        id, PROPERTY_VSIM_ENABLE, "0");
                act = ((vsimEnabled.isEmpty()) ? 0 : Integer.parseInt(vsimEnabled));
                if (act == 1) {
                    log("Vsim enabled on phone " + id + " and downloaded by phone" + phoneId);
                    return true;
                }
            }
        }

        return false;
    }

    private void deactivateLinkDownPdn(PhoneBase phone) {
        log("[IRAT_DcTracker]deactivateLinkDownPdn...");
        phone.mCi.requestDeactivateLinkDownPdn(null);
    }
   /*-BEGIN-20151014-add-by-qingtao.wen-for-task745095-*/   
    private String IMSI_TABLE_UNE_3DIGITS[] = {"027","028","029","024"};
    private String IMSI_TABLE_ETB_3DIGITS[] = {"017"};
    private String IMSI_TABLE_UFF_3DIGITS[] = {"018","019"};
    private String IMSI_TABLE_EXITO_3DIGITS[] = {"054","055"};
    
    private String IMSI_TABLE_UFF_5DIGITS[] = {"02372","02373","02374","02375"};
    private String IMSI_TABLE_UFF_6DIGITS[] = {"023713","023714","023715","023716","023717","023718","023719","023760","023761"};
    
    private Boolean isBelongToTable(String para, String table[]){
       if(para==null || table ==null) return false;
       for(String ele : table){
               if(para.equals(ele)){
                       if (DBG) log("isBelongToTable get one para:" + para+" ele:"+ele);
                       return true;
               }
       }
       if (DBG) log("isBelongToTable not found for:" + para);
       return false;
       
    }
    private int judgeMVNOByImsi(){
       
       IccRecords r = mIccRecords.get();
       String imsi = r.getIMSI();      
       String operator = (r != null) ? r.getOperatorNumeric() : "";
       //result: 1:UNE, 2:ETB, 3:Uff ,4:Éxito ,5:Tigo
       int result = -1;
       
       if(TextUtils.isEmpty(imsi) || TextUtils.isEmpty(operator)){
               if (DBG) log("imsi or numeric invalid");
               return result;
       }
       if(!operator.equals("732103")){
               if (DBG) log("not 732103");
               return result;
       }
       if (DBG) log("judgeMVNOByImsi imsi:"+imsi);
       String three_digits_para = imsi.substring(6,9);
       String five_digits_para = imsi.substring(6,11);
       String six_digits_para = imsi.substring(6,12);
       
       if(isBelongToTable(three_digits_para,IMSI_TABLE_UNE_3DIGITS)){
               result = 1;
       }else if(isBelongToTable(three_digits_para,IMSI_TABLE_ETB_3DIGITS)){
               result = 2;
       }else if(isBelongToTable(three_digits_para,IMSI_TABLE_EXITO_3DIGITS)){
               result = 4;
       }else if(isBelongToTable(three_digits_para,IMSI_TABLE_UFF_3DIGITS) ||
                       isBelongToTable(five_digits_para,IMSI_TABLE_UFF_5DIGITS) ||
                       isBelongToTable(six_digits_para,IMSI_TABLE_UFF_6DIGITS)){
               result = 3;
               
       }else{
               result = 5;
       }
       return result;
       
    }
  /*-END-20151014-add-by-qingtao.wen-for-task745095-*/

}
