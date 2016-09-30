package com.mediatek.settings.sim;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISimManagementExt;

/**
 * Radio power manager to control radio state.
 */
public class RadioPowerController {

    private static final String TAG = "RadioPowerController";
    private Context mContext;
    private static final int MODE_PHONE1_ONLY = 1;
    private ISimManagementExt mExt;
    private static RadioPowerController sInstance = null;

   /**
    * Constructor.
    * @param context Context
    */
    private RadioPowerController(Context context) {
        mContext = context;
        mExt = UtilsExt.getSimManagmentExtPlugin(mContext);
    }

    private static synchronized void createInstance(Context context) {
        if(sInstance == null) {
            sInstance = new RadioPowerController(context);
        }
    }

    public static RadioPowerController getInstance(Context context) {
        if(sInstance == null) {
            createInstance(context);
        }
        return sInstance;
    }

    public boolean needRadioSwitch(int subId, boolean turnOn) {
        boolean needSwitch = false;
        ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE_EX));
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            Bundle bundle = null;
            try {
                if (telephonyEx != null) {
                    bundle = telephonyEx.getServiceState(subId);
                } else {
                    Log.d(TAG, "mTelephonyEx is null, returen false");
                }
            } catch (RemoteException e) {
                Log.d(TAG, "getServiceState() error, subId: " + subId);
                e.printStackTrace();
            }
            if (bundle != null) {
                ServiceState serviceState = ServiceState.newFromBundle(bundle);
                Log.d(TAG, "needRadioSwitch: subId: " + subId + ", state: " + serviceState);
                if ((serviceState.getState() != ServiceState.STATE_POWER_OFF) != turnOn) {
                    needSwitch = true;
                }
            }
        }
        Log.d(TAG, "needRadioSwitch(" + subId + ")" + " : " + needSwitch + ", turnOn: " + turnOn);
        return needSwitch;
    }

    public boolean setRadionOn(int subId, boolean turnOn) {
        Log.d(TAG, "setRadionOn, turnOn: " + turnOn + ", subId = " + subId);
        boolean isSuccessful = false;
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return isSuccessful;
        }
        ITelephony telephony = ITelephony.Stub.asInterface(ServiceManager.getService(
                Context.TELEPHONY_SERVICE));
        try {
            if (telephony != null) {
                isSuccessful = telephony.setRadioForSubscriber(subId, turnOn);
                if (isSuccessful) {
                    updateRadioMsimDb(subId, turnOn);
                    /// M: for plug-in
                    mExt.setRadioPowerState(subId, turnOn);
                }
            } else {
                Log.d(TAG, "telephony is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "setRadionOn, isSuccessful: " + isSuccessful);
        return isSuccessful;
    }

    private void updateRadioMsimDb(int subId, boolean turnOn) {
        int priviousSimMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.MSIM_MODE_SETTING, -1);
        Log.i(TAG, "updateRadioMsimDb, The current dual sim mode is " + priviousSimMode
                + ", with subId = " + subId);
        int currentSimMode;
        boolean isPriviousRadioOn = false;
        int slot = SubscriptionManager.getSlotId(subId);
        int modeSlot = MODE_PHONE1_ONLY << slot;
        if ((priviousSimMode & modeSlot) > 0) {
            currentSimMode = priviousSimMode & (~modeSlot);
            isPriviousRadioOn = true;
        } else {
            currentSimMode = priviousSimMode | modeSlot;
            isPriviousRadioOn = false;
        }

        Log.d(TAG, "currentSimMode=" + currentSimMode + " isPriviousRadioOn =" + isPriviousRadioOn
                + ", turnOn: " + turnOn);
        if (turnOn != isPriviousRadioOn) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.MSIM_MODE_SETTING, currentSimMode);
        } else {
            Log.w(TAG, "quickly click don't allow.");
        }
    }
}