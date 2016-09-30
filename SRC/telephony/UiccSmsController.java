/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.annotation.Nullable;
import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.util.List;

// MTK-START
import android.os.Bundle;
import android.telephony.SmsParameters;
import com.mediatek.internal.telephony.IccSmsStorageStatus;
import android.telephony.SimSmsInsertStatus;
import com.mediatek.internal.telephony.SmsCbConfigInfo;
import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;
// MTK-END

/**
 * UiccSmsController to provide an inter-process communication to
 * access Sms in Icc.
 */
public class UiccSmsController extends ISms.Stub {
    static final String LOG_TAG = "RIL_UiccSmsController";

    protected Phone[] mPhone;

    protected UiccSmsController(Phone[] phone){
        mPhone = phone;

        if (ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    @Override
    public boolean
    updateMessageOnIccEfForSubscriber(int subId, String callingPackage, int index, int status,
                byte[] pdu) throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.updateMessageOnIccEf(callingPackage, index, status, pdu);
        } else {
            Rlog.e(LOG_TAG,"updateMessageOnIccEfForSubscriber iccSmsIntMgr is null" +
                          " for Subscription: " + subId);
            return false;
        }
    }

    @Override
    public boolean copyMessageToIccEfForSubscriber(int subId, String callingPackage, int status,
            byte[] pdu, byte[] smsc) throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.copyMessageToIccEf(callingPackage, status, pdu, smsc);
        } else {
            Rlog.e(LOG_TAG,"copyMessageToIccEfForSubscriber iccSmsIntMgr is null" +
                          " for Subscription: " + subId);
            return false;
        }
    }

    @Override
    public List<SmsRawData> getAllMessagesFromIccEfForSubscriber(int subId, String callingPackage)
                throws android.os.RemoteException {
        // MTK-START
        if (!isSmsReadyForSubscriber(subId)) {
            Rlog.e(LOG_TAG, "getAllMessagesFromIccEf SMS not ready");
            return null;
        }
        // MTK-END

        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getAllMessagesFromIccEf(callingPackage);
        } else {
            Rlog.e(LOG_TAG,"getAllMessagesFromIccEfForSubscriber iccSmsIntMgr is" +
                          " null for Subscription: " + subId);
            return null;
        }
    }

    @Override
    public void sendDataForSubscriber(int subId, String callingPackage, String destAddr,
            String scAddr, int destPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendData(callingPackage, destAddr, scAddr, destPort, data,
                    sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG,"sendDataForSubscriber iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
            // TODO: Use a more specific error code to replace RESULT_ERROR_GENERIC_FAILURE.
            sendErrorInPendingIntent(sentIntent, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        }
    }

    public void sendDataForSubscriberWithSelfPermissions(int subId, String callingPackage,
            String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendDataWithSelfPermissions(callingPackage, destAddr, scAddr, destPort, data,
                    sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG,"sendText iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
    }

    public void sendText(String callingPackage, String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendTextForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr,
            text, sentIntent, deliveryIntent, true /* persistMessageForNonDefaultSmsApp*/);
    }

    @Override
    public void sendTextForSubscriber(int subId, String callingPackage, String destAddr,
            String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent,
            boolean persistMessageForNonDefaultSmsApp) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendText(callingPackage, destAddr, scAddr, text, sentIntent,
                    deliveryIntent, persistMessageForNonDefaultSmsApp);
        } else {
            Rlog.e(LOG_TAG,"sendTextForSubscriber iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
            sendErrorInPendingIntent(sentIntent, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        }
    }

    public void sendTextForSubscriberWithSelfPermissions(int subId, String callingPackage,
            String destAddr, String scAddr, String text, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendTextWithSelfPermissions(callingPackage, destAddr, scAddr, text,
                    sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG,"sendText iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
    }

    public void sendMultipartText(String callingPackage, String destAddr, String scAddr,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents) throws android.os.RemoteException {
         sendMultipartTextForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr,
                 scAddr, parts, sentIntents, deliveryIntents,
                 true /* persistMessageForNonDefaultSmsApp */);
    }

    @Override
    public void sendMultipartTextForSubscriber(int subId, String callingPackage, String destAddr,
            String scAddr, List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents, boolean persistMessageForNonDefaultSmsApp)
            throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            iccSmsIntMgr.sendMultipartText(callingPackage, destAddr, scAddr, parts, sentIntents,
                    deliveryIntents, persistMessageForNonDefaultSmsApp);
        } else {
            Rlog.e(LOG_TAG,"sendMultipartTextForSubscriber iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
            sendErrorInPendingIntents(sentIntents, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        }
    }

    @Override
    public boolean enableCellBroadcastForSubscriber(int subId, int messageIdentifier, int ranType)
                throws android.os.RemoteException {
        return enableCellBroadcastRangeForSubscriber(subId, messageIdentifier, messageIdentifier,
                ranType);
    }

    @Override
    public boolean enableCellBroadcastRangeForSubscriber(int subId, int startMessageId,
            int endMessageId, int ranType) throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.enableCellBroadcastRange(startMessageId, endMessageId, ranType);
        } else {
            Rlog.e(LOG_TAG,"enableCellBroadcastRangeForSubscriber iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
        return false;
    }

    @Override
    public boolean disableCellBroadcastForSubscriber(int subId, int messageIdentifier, int ranType)
                throws android.os.RemoteException {
        return disableCellBroadcastRangeForSubscriber(subId, messageIdentifier, messageIdentifier,
                ranType);
    }

    @Override
    public boolean disableCellBroadcastRangeForSubscriber(int subId, int startMessageId,
            int endMessageId, int ranType) throws android.os.RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.disableCellBroadcastRange(startMessageId, endMessageId, ranType);
        } else {
            Rlog.e(LOG_TAG,"disableCellBroadcastRangeForSubscriber iccSmsIntMgr is null for" +
                          " Subscription:"+subId);
        }
       return false;
    }

    @Override
    public int getPremiumSmsPermission(String packageName) {
        return getPremiumSmsPermissionForSubscriber(getPreferredSmsSubscription(), packageName);
    }

    @Override
    public int getPremiumSmsPermissionForSubscriber(int subId, String packageName) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.getPremiumSmsPermission(packageName);
        } else {
            Rlog.e(LOG_TAG, "getPremiumSmsPermissionForSubscriber iccSmsIntMgr is null");
        }
        //TODO Rakesh
        return 0;
    }

    @Override
    public void setPremiumSmsPermission(String packageName, int permission) {
         setPremiumSmsPermissionForSubscriber(getPreferredSmsSubscription(), packageName, permission);
    }

    @Override
    public void setPremiumSmsPermissionForSubscriber(int subId, String packageName, int permission) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            iccSmsIntMgr.setPremiumSmsPermission(packageName, permission);
        } else {
            Rlog.e(LOG_TAG, "setPremiumSmsPermissionForSubscriber iccSmsIntMgr is null");
        }
    }

    @Override
    public boolean isImsSmsSupportedForSubscriber(int subId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.isImsSmsSupported();
        } else {
            Rlog.e(LOG_TAG, "isImsSmsSupportedForSubscriber iccSmsIntMgr is null");
        }
        return false;
    }

    @Override
    public boolean isSmsSimPickActivityNeeded(int subId) {
        final Context context = ActivityThread.currentApplication().getApplicationContext();
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        List<SubscriptionInfo> subInfoList;
        final long identity = Binder.clearCallingIdentity();
        try {
            subInfoList = SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }

        if (subInfoList != null) {
            final int subInfoLength = subInfoList.size();

            for (int i = 0; i < subInfoLength; ++i) {
                final SubscriptionInfo sir = subInfoList.get(i);
                if (sir != null && sir.getSubscriptionId() == subId) {
                    // The subscription id is valid, sms sim pick activity not needed
                    return false;
                }
            }

            // If reached here and multiple SIMs and subs present, sms sim pick activity is needed
            if (subInfoLength > 0 && telephonyManager.getSimCount() > 1) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getImsSmsFormatForSubscriber(int subId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            return iccSmsIntMgr.getImsSmsFormat();
        } else {
            Rlog.e(LOG_TAG, "getImsSmsFormatForSubscriber iccSmsIntMgr is null");
        }
        return null;
    }

    @Override
    public void injectSmsPduForSubscriber(
            int subId, byte[] pdu, String format, PendingIntent receivedIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.injectSmsPdu(pdu, format, receivedIntent);
        } else {
            Rlog.e(LOG_TAG, "injectSmsPduForSubscriber iccSmsIntMgr is null");
            // RESULT_SMS_GENERIC_ERROR is documented for injectSmsPdu
            sendErrorInPendingIntent(receivedIntent, Intents.RESULT_SMS_GENERIC_ERROR);
        }
    }

    /**
     * get sms interface manager object based on subscription.
     **/
    private @Nullable IccSmsInterfaceManager getIccSmsInterfaceManager(int subId) {
        if (!isActiveSubId(subId)) {
            Rlog.e(LOG_TAG, "Subscription " + subId + " is inactive.");
            return null;
        }

        int phoneId = SubscriptionController.getInstance().getPhoneId(subId) ;
        //Fixme: for multi-subscription case
        if (!SubscriptionManager.isValidPhoneId(phoneId)
                || phoneId == SubscriptionManager.DEFAULT_PHONE_INDEX) {
            phoneId = 0;
        }

        try {
            return (IccSmsInterfaceManager)
                ((PhoneProxy)mPhone[(int)phoneId]).getIccSmsInterfaceManager();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Exception is :"+e.toString()+" For subscription :"+subId );
            e.printStackTrace();
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.e(LOG_TAG, "Exception is :"+e.toString()+" For subscription :"+subId );
            e.printStackTrace();
            return null;
        }
    }

    /**
       Gets User preferred SMS subscription */
    @Override
    public int getPreferredSmsSubscription() {
        return SubscriptionController.getInstance().getDefaultSmsSubId();
    }

    /**
     * Get SMS prompt property,  enabled or not
     **/
    @Override
    public boolean isSMSPromptEnabled() {
        return PhoneFactory.isSMSPromptEnabled();
    }

    @Override
    public void sendStoredText(int subId, String callingPkg, Uri messageUri, String scAddress,
            PendingIntent sentIntent, PendingIntent deliveryIntent) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendStoredText(callingPkg, messageUri, scAddress, sentIntent,
                    deliveryIntent);
        } else {
            Rlog.e(LOG_TAG,"sendStoredText iccSmsIntMgr is null for subscription: " + subId);
            sendErrorInPendingIntent(sentIntent, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        }
    }

    @Override
    public void sendStoredMultipartText(int subId, String callingPkg, Uri messageUri,
            String scAddress, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents)
            throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null ) {
            iccSmsIntMgr.sendStoredMultipartText(callingPkg, messageUri, scAddress, sentIntents,
                    deliveryIntents);
        } else {
            Rlog.e(LOG_TAG,"sendStoredMultipartText iccSmsIntMgr is null for subscription: "
                    + subId);
            sendErrorInPendingIntents(sentIntents, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        }
    }

    /*
     * @return true if the subId is active.
     */
    private boolean isActiveSubId(int subId) {
        return SubscriptionController.getInstance().isActiveSubId(subId);
    }

    private void sendErrorInPendingIntent(@Nullable PendingIntent intent, int errorCode) {
        if (intent != null) {
            try {
                intent.send(errorCode);
            } catch (PendingIntent.CanceledException ex) {
            }
        }
    }

    private void sendErrorInPendingIntents(List<PendingIntent> intents, int errorCode) {
        for (PendingIntent intent : intents) {
            sendErrorInPendingIntent(intent, errorCode);
        }
    }

    // MTK-START
    /**
     * Retrieves all messages currently stored on ICC based on different mode.
     * Ex. CDMA mode or GSM mode for international cards.
     * @param subId the subId id.
     * @param callingPackage the calling packages
     * @param mode the GSM mode or CDMA mode
     *
     * @return list of SmsRawData of all sms on ICC
     */
    public List<SmsRawData> getAllMessagesFromIccEfByModeForSubscriber(int subId,
            String callingPackage, int mode) {
        // MTK-START
        if (!isSmsReadyForSubscriber(subId)) {
            Rlog.e(LOG_TAG, "getAllMessagesFromIccEf SMS not ready");
            return null;
        }
        // MTK-END

        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getAllMessagesFromIccEfByMode(callingPackage, mode);
        } else {
            Rlog.e(LOG_TAG, "getAllMessagesFromIccEfByModeForSubscriber iccSmsIntMgr is null for" +
                          " Subscription: " + subId);
        }
        return null;
    }

    /**
     * Copy a text SMS to the ICC.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param scAddress Service center address
     * @param address   Destination address or original address
     * @param text      List of message text
     * @param status    message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *                  STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param timestamp Timestamp when service center receive the message
     * @return success or not
     *
     */
    public int copyTextMessageToIccCardForSubscriber(int subId, String callingPackage,
            String scAddress, String address, List<String> text, int status, long timestamp) {
        int result = RESULT_ERROR_GENERIC_FAILURE;
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            result = iccSmsIntMgr.copyTextMessageToIccCard(callingPackage, scAddress, address, text,
                    status, timestamp);
        } else {
            Rlog.e(LOG_TAG, "sendStoredMultipartText iccSmsIntMgr is null for subscription: "
                    + subId);
        }

        return result;
    }

    /**
     * Send a data message with original port
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param destAddr the destination address
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param destPort destination port
     * @param originalPort origianl sender port
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    public void sendDataWithOriginalPortForSubscriber(int subId, String callingPackage,
            String destAddr, String scAddr, int destPort, int originalPort, byte[] data,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendDataWithOriginalPort(callingPackage, destAddr, scAddr, destPort,
                    originalPort, data, sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG, "sendDataWithOriginalPortForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }
    }

    /**
     * Judge if SMS subsystem is ready or not
     *
     * @param subId subscription identity
     *
     * @return true for success
     */
    public boolean isSmsReadyForSubscriber(int subId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.isSmsReady();
        } else {
            Rlog.e(LOG_TAG, "isSmsReady iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return false;
    }

    /**
     * Set the memory storage status of the SMS.
     * This function is used for FTA test only
     *
     * @param subId subscription identity
     * @param status false for storage full, true for storage available
     *
     */
    public void setSmsMemoryStatusForSubscriber(int subId, boolean status) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.setSmsMemoryStatus(status);
        } else {
            Rlog.e(LOG_TAG, "setSmsMemoryStatus iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }
    }

    /**
     * Get SMS SIM Card memory's total and used number
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     *
     * @return <code>IccSmsStorageStatus</code> object
     */
    public IccSmsStorageStatus getSmsSimMemoryStatusForSubscriber(int subId,
            String callingPackage) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getSmsSimMemoryStatus(callingPackage);
        } else {
            Rlog.e(LOG_TAG, "setSmsMemoryStatus iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return null;
    }

    /**
     * Send an SMS with specified encoding type.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param destAddr the address to send the message to
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param encodingType the encoding type of content of message(GSM 7-bit, Unicode or Automatic)
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param persistMessageForNonDefaultSmsApp whether to save the sent message into SMS DB for a
     *   non-default SMS app.
     */
    public void sendTextWithEncodingTypeForSubscriber(int subId, String callingPackage,
            String destAddr, String scAddr, String text, int encodingType, PendingIntent sentIntent,
            PendingIntent deliveryIntent, boolean persistMessageForNonDefaultSmsApp) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendTextWithEncodingType(callingPackage, destAddr, scAddr, text,
                    encodingType, sentIntent, deliveryIntent, persistMessageForNonDefaultSmsApp);
        } else {
            Rlog.e(LOG_TAG, "sendTextWithEncodingTypeForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }
    }

    /**
     * Send a multi-part text based SMS with specified encoding type.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param encodingType the encoding type of content of message(GSM 7-bit, Unicode or Automatic)
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     * @param persistMessageForNonDefaultSmsApp whether to save the sent message into SMS DB for a
     *   non-default SMS app.
     */
    public void sendMultipartTextWithEncodingTypeForSubscriber(int subId, String callingPackage,
            String destAddr, String scAddr, List<String> parts, int encodingType,
            List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents,
            boolean persistMessageForNonDefaultSmsApp) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendMultipartTextWithEncodingType(callingPackage, destAddr, scAddr, parts,
                    encodingType, sentIntents, deliveryIntents, persistMessageForNonDefaultSmsApp);
        } else {
            Rlog.e(LOG_TAG, "sendMultipartTextWithEncodingTypeForSubscriber iccSmsIntMgr is null"
                     + " for subscription: " + subId);
        }
    }

    /**
     * Copy a text SMS to the ICC.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param scAddress Service center address
     * @param address   Destination address or original address
     * @param text      List of message text
     * @param status    message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *                  STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param timestamp Timestamp when service center receive the message
     * @return SimSmsInsertStatus
     *
     */
    public SimSmsInsertStatus insertTextMessageToIccCardForSubscriber(int subId,
            String callingPackage, String scAddress, String address, List<String> text, int status,
            long timestamp) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.insertTextMessageToIccCard(callingPackage, scAddress, address, text,
                    status, timestamp);
        } else {
            Rlog.e(LOG_TAG, "sendMultipartTextWithEncodingTypeForSubscriber iccSmsIntMgr is null"
                    + " for subscription: " + subId);
        }

        return null;
    }

    /**
     * Copy a raw SMS PDU to the ICC.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param pdu the raw PDU to store
     * @param smsc encoded smsc service center
     * @return SimSmsInsertStatus
     *
     */
    public SimSmsInsertStatus insertRawMessageToIccCardForSubscriber(int subId,
            String callingPackage, int status, byte[] pdu, byte[] smsc) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);

        SimSmsInsertStatus ret = null;
        if (iccSmsIntMgr != null) {
            ret = iccSmsIntMgr.insertRawMessageToIccCard(callingPackage, status, pdu, smsc);
        } else {
            Rlog.e(LOG_TAG, "insertRawMessageToIccCardForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return ret;
    }

    /**
     * Send an SMS with specified encoding type.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param destAddr the address to send the message to
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param extraParams extra parameters, such as validity period, encoding type
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param persistMessageForNonDefaultSmsApp whether to save the sent message into SMS DB for a
     *   non-default SMS app.
     */
    public void sendTextWithExtraParamsForSubscriber(int subId, String callingPackage,
            String destAddr, String scAddr, String text, Bundle extraParams,
            PendingIntent sentIntent, PendingIntent deliveryIntent,
            boolean persistMessageForNonDefaultSmsApp) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendTextWithExtraParams(callingPackage, destAddr, scAddr, text,
                    extraParams, sentIntent, deliveryIntent, persistMessageForNonDefaultSmsApp);
        } else {
            Rlog.e(LOG_TAG, "sendTextWithExtraParamsForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }
    }

    /**
     * Send a multi-part text based SMS with specified encoding type.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param extraParams extra parameters, such as validity period, encoding type
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     * @param persistMessageForNonDefaultSmsApp whether to save the sent message into SMS DB for a
     *   non-default SMS app.
     */
    public void sendMultipartTextWithExtraParamsForSubscriber(int subId, String callingPackage,
            String destAddr, String scAddr, List<String> parts, Bundle extraParams,
            List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents,
            boolean persistMessageForNonDefaultSmsApp) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendMultipartTextWithExtraParams(callingPackage, destAddr, scAddr, parts,
                    extraParams, sentIntents, deliveryIntents, persistMessageForNonDefaultSmsApp);
        } else {
            Rlog.e(LOG_TAG, "sendTextWithExtraParamsForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }
    }

    /**
     * Get sms parameters from EFsmsp.
     * Such as the validity period & its format, Protocol identifier and decode char set value.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     *
     * @return sms parameter stored on EF like valid period
     */
    public SmsParameters getSmsParametersForSubscriber(int subId, String callingPackage) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getSmsParameters(callingPackage);
        } else {
            Rlog.e(LOG_TAG, "getSmsParametersForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return null;
    }

    /**
     * Save sms parameters into EFsmsp.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param params sms EFsmsp values
     *
     * @return true if set completed; false if set failed
     */
    public boolean setSmsParametersForSubscriber(int subId, String callingPackage,
            SmsParameters params) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.setSmsParameters(callingPackage, params);
        } else {
            Rlog.e(LOG_TAG, "setSmsParametersForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return false;
    }

    /**
     * Retrieves message currently stored on ICC by index.
     *
     * @param subId subscription identity
     * @param callingPackage the calling packages
     * @param index the index of sms save in EFsms
     *
     * @return SmsRawData of sms on ICC
     */
    public SmsRawData getMessageFromIccEfForSubscriber(int subId, String callingPackage,
            int index) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getMessageFromIccEf(callingPackage, index);
        } else {
            Rlog.e(LOG_TAG, "getMessageFromIccEfForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return null;
    }

    /**
     * Get the cell broadcast config.
     *
     * @param subId subscription identity
     *
     * @return Cell broadcast config.
     */
    public SmsCbConfigInfo[] getCellBroadcastSmsConfigForSubscriber(int subId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getCellBroadcastSmsConfig();
        } else {
            Rlog.e(LOG_TAG, "getCellBroadcastSmsConfigForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return null;
    }

    /**
     * Set the cell broadcast config.
     *
     * @param subId subscription identity
     * @param channels the channels setting
     * @param languages the language setting
     *
     * @return true if set successfully; false if set failed
     */
    public boolean setCellBroadcastSmsConfigForSubscriber(int subId,
            SmsCbConfigInfo[] channels, SmsCbConfigInfo[] languages) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.setCellBroadcastSmsConfig(channels, languages);
        } else {
            Rlog.e(LOG_TAG, "setCellBroadcastSmsConfigForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return false;
    }

    /**
     * Query the activation status of cell broadcast.
     *
     * @param subId subscription identity
     *
     * @return true if activate; false if inactivate.
     */
    public boolean queryCellBroadcastSmsActivationForSubscriber(int subId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.queryCellBroadcastSmsActivation();
        } else {
            Rlog.e(LOG_TAG, "setCellBroadcastSmsConfigForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return false;
    }

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param subId subscription identity
     * @param activate 0 = activate, 1 = deactivate
     *
     * @return true if activate successfully; false if activate failed
     */
    public boolean activateCellBroadcastSmsForSubscriber(int subId, boolean activate) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.activateCellBroadcastSms(activate);
        } else {
            Rlog.e(LOG_TAG, "activateCellBroadcastSmsForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return false;
    }

    /**
     * Remove specified channel and serial of cb message.
     *
     * @param subId subscription identity
     * @param channelId removed channel id
     * @param serialId removed serial id
     *
     * @return true process successfully; false process failed.
     *
     * @hide
     */
    public boolean removeCellBroadcastMsgForSubscriber(int subId, int channelId, int serialId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.removeCellBroadcastMsg(channelId, serialId);
        } else {
            Rlog.e(LOG_TAG, "removeCellBroadcastMsg iccSmsIntMgr is null for subscription: "
                    + subId);
        }

        return false;
    }

    /**
     * Set the specified ETWS mode to modem.
     *
     * @param subId subscription identity
     * @param mode a bit mask value. bit0: enable ETWS. bit1: enable receiving ETWS with security
     *         check. bit2: enable receiving test purpose ETWS
     *
     * @return true process successfully; false process failed.
     */
    public boolean setEtwsConfigForSubscriber(int subId, int mode) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.setEtwsConfig(mode);
        } else {
            Rlog.e(LOG_TAG, "setEtwsConfigForSubscriber iccSmsIntMgr is null for" +
                    "subscription: " + subId);
        }

        return false;
    }
    // MTK-END
}
