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

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Settings;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.ServiceState;

import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// MTK-START
import java.util.ArrayList;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.SmsRawData;
import java.util.List;

import static android.telephony.SmsManager.STATUS_ON_ICC_READ;
import static android.telephony.SmsManager.STATUS_ON_ICC_UNREAD;
import static android.telephony.SmsManager.STATUS_ON_ICC_SENT;
import static android.telephony.SmsManager.STATUS_ON_ICC_UNSENT;
import static android.telephony.SmsManager.RESULT_ERROR_SUCCESS;
import static android.telephony.SmsManager.RESULT_ERROR_SIM_MEM_FULL;
import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_NULL_PDU;
import static android.telephony.SmsManager.RESULT_ERROR_INVALID_ADDRESS;
import static android.telephony.SmsManager.EXTRA_PARAMS_VALIDITY_PERIOD;

import com.mediatek.internal.telephony.ltedc.svlte.SvlteDcPhone;
// MTK-END

public final class GsmSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "GsmSMSDispatcher";
    private static final boolean VDBG = false;
    protected UiccController mUiccController = null;
    private AtomicReference<IccRecords> mIccRecords = new AtomicReference<IccRecords>();
    private AtomicReference<UiccCardApplication> mUiccApplication =
            new AtomicReference<UiccCardApplication>();
    private GsmInboundSmsHandler mGsmInboundSmsHandler;

    /** Status report received */
    private static final int EVENT_NEW_SMS_STATUS_REPORT = 100;

    public GsmSMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor,
            ImsSMSDispatcher imsSMSDispatcher,
            GsmInboundSmsHandler gsmInboundSmsHandler) {
        super(phone, usageMonitor, imsSMSDispatcher);
        mCi.setOnSmsStatus(this, EVENT_NEW_SMS_STATUS_REPORT, null);
        mGsmInboundSmsHandler = gsmInboundSmsHandler;
        mUiccController = UiccController.getInstance();
        // MTK-START
        Integer phoneId = new Integer(mPhone.getPhoneId());
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, phoneId);
        // MTK-END
        Rlog.d(TAG, "GsmSMSDispatcher created");
    }

    @Override
    public void dispose() {
        super.dispose();
        mCi.unSetOnSmsStatus(this);
        mUiccController.unregisterForIccChanged(this);
    }

    @Override
    protected String getFormat() {
        return SmsConstants.FORMAT_3GPP;
    }

    /**
     * Handles 3GPP format-specific events coming from the phone stack.
     * Other events are handled by {@link SMSDispatcher#handleMessage}.
     *
     * @param msg the message to handle
     */
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case EVENT_NEW_SMS_STATUS_REPORT:
            handleStatusReport((AsyncResult) msg.obj);
            break;

        case EVENT_NEW_ICC_SMS:
        // pass to InboundSmsHandler to process
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, msg.obj);
        break;

        case EVENT_ICC_CHANGED:
            // MTK-START
            Integer phoneId = getUiccControllerPhoneId(msg);
            Rlog.d(TAG, "EVENT_ICC_CHANGED: phoneId = " + phoneId
                    + ", mPhone = " + mPhone + ", mPhone phoneId = " + mPhone.getPhoneId());
            if ((phoneId == UiccController.INDEX_SVLTE) && (mPhone instanceof SvlteDcPhone)) {
                Rlog.d(TAG, "EVENT_ICC_CHANGED: SvlteDcPhone match INDEX_SVLTE");
            } else if (phoneId != mPhone.getPhoneId()) {
                Rlog.d(TAG, "Wrong phone id event coming, PhoneId: " + phoneId);
                break;
            }
            // MTK-END
            onUpdateIccAvailability();
            break;

        // MTK-START
        // Handle the next wait to send message in the message queue
        case EVENT_DELAY_SEND_MESSAGE_QUEUE:
            Rlog.d(TAG, "EVENT_DELAY_SEND_MESSAGE_QUEUE: " + msg.obj);
            handleSendNextTracker((SmsTracker) msg.obj);
            break;
        // MTK-END

        default:
            super.handleMessage(msg);
        }
    }

    /**
     * Called when a status report is received.  This should correspond to
     * a previously successful SEND.
     *
     * @param ar AsyncResult passed into the message handler.  ar.result should
     *           be a String representing the status report PDU, as ASCII hex.
     */
    private void handleStatusReport(AsyncResult ar) {
        String pduString = (String) ar.result;
        SmsMessage sms = SmsMessage.newFromCDS(pduString);

        if (sms != null) {
            int tpStatus = sms.getStatus();
            int messageRef = sms.mMessageRef;
            for (int i = 0, count = deliveryPendingList.size(); i < count; i++) {
                SmsTracker tracker = deliveryPendingList.get(i);
                if (tracker.mMessageRef == messageRef) {
                    // Found it.  Remove from list and broadcast.
                    if(tpStatus >= Sms.STATUS_FAILED || tpStatus < Sms.STATUS_PENDING ) {
                       deliveryPendingList.remove(i);
                       // Update the message status (COMPLETE or FAILED)
                       tracker.updateSentMessageStatus(mContext, tpStatus);
                    }
                    PendingIntent intent = tracker.mDeliveryIntent;
                    Intent fillIn = new Intent();
                    fillIn.putExtra("pdu", IccUtils.hexStringToBytes(pduString));
                    fillIn.putExtra("format", getFormat());
                    try {
                        intent.send(mContext, Activity.RESULT_OK, fillIn);
                    } catch (CanceledException ex) {}

                    // Only expect to see one tracker matching this messageref
                    break;
                }
            }
        }
        mCi.acknowledgeLastIncomingGsmSms(true, Intents.RESULT_SMS_HANDLED, null);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        // MTK-START, DM operator feature
        if (isDmLock == true) {
            Rlog.d(TAG, "DM status: lock-on");
            return;
        }
        // MTK-END

        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, destPort, data, (deliveryIntent != null));
        if (pdu != null) {
            HashMap map = getSmsTrackerMap(destAddr, scAddr, destPort, data, pdu);
            SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat(),
                    null /*messageUri*/, false /*isExpectMore*/, null /*fullMessageText*/,
                    false /*isText*/, true /*persistMessage*/);

            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                DataSmsSender smsSender = new DataSmsSender(tracker);
                smsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(smsSender));
            } else {
                Rlog.v(TAG, "No carrier package.");
                sendRawPdu(tracker);
            }
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendData(): getSubmitPdu() returned null");
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent,
            PendingIntent deliveryIntent, Uri messageUri, String callingPkg,
            boolean persistMessage) {
        // MTK-START, DM operator feature
        if (isDmLock == true) {
            Rlog.d(TAG, "DM status: lock-on");
            return;
        }
        // MTK-END

        // begin modify by tao.luo for task716576 2015-10-31
        // old : SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, (deliveryIntent != null));
        int encodeMode = Settings.System.getInt(mContext.getContentResolver(),Settings.System.SMS_ENCODE_MODE, 0);
        int validPeriod = Settings.System.getInt(mContext.getContentResolver(),Settings.System.SMS_VALID_PERIOD, 0);
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, (deliveryIntent != null), encodeMode, validPeriod);                
        // end modify by tao.luo for task716576 2015-10-31        
        
        if (pdu != null) {
            HashMap map = getSmsTrackerMap(destAddr, scAddr, text, pdu);
            SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat(),
                    messageUri, false /*isExpectMore*/, text /*fullMessageText*/, true /*isText*/,
                    persistMessage);

            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                TextSmsSender smsSender = new TextSmsSender(tracker);
                smsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(smsSender));
            } else {
                Rlog.v(TAG, "No carrier package.");
                sendRawPdu(tracker);
            }
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendText(): getSubmitPdu() returned null");
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        throw new IllegalStateException("This method must be called only on ImsSMSDispatcher");
    }

    /** {@inheritDoc} */
    @Override
    protected GsmAlphabet.TextEncodingDetails calculateLength(CharSequence messageBody,
            boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    // begin add by tao.luo for task716576 2015-10-31
    /** {@inheritDoc} */
    @Override
    protected GsmAlphabet.TextEncodingDetails JRDcalculateLength(CharSequence messageBody,
            boolean use7bitOnly, int encodeMode) {
        Rlog.i(TAG,"GGS -> GsmSMSDispatcher JRDcalculateLength");
        return SmsMessage.JRDcalculateLength(messageBody, use7bitOnly, encodeMode);
    }

    /** {@inheritDoc} */
    @Override
    protected SmsTracker getNewSubmitPduTracker(String destinationAddress, String scAddress,
            String message, SmsHeader smsHeader, int encoding,
            PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart,
            AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri,
            String fullMessageText, Boolean isJrdFlg, int encodeMode, int validPeriod) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddress, destinationAddress,
                                                  message, deliveryIntent != null, SmsHeader.toByteArray(smsHeader),
                                                  encoding, smsHeader.languageTable, smsHeader.languageShiftTable, encodeMode, validPeriod);
        if (pdu != null) {
            HashMap map =  getSmsTrackerMap(destinationAddress, scAddress,
                    message, pdu);
            return getSmsTracker(map, sentIntent,
                    deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri,
                    smsHeader, !lastPart, fullMessageText, true /*isText*/,
                    // MTK-START, it should set true by default to ignore this variable
                    true /*persistMessage*/);
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.getNewSubmitPduTracker(): getSubmitPdu() returned null");
            return null;
        }
    }    
    // end add by tao.luo for task716576 2015-10-31
    
    /** {@inheritDoc} */
    @Override
    protected SmsTracker getNewSubmitPduTracker(String destinationAddress, String scAddress,
            String message, SmsHeader smsHeader, int encoding,
            PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart,
            AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri,
            String fullMessageText) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddress, destinationAddress,
                message, deliveryIntent != null, SmsHeader.toByteArray(smsHeader),
                encoding, smsHeader.languageTable, smsHeader.languageShiftTable);
        if (pdu != null) {
            HashMap map =  getSmsTrackerMap(destinationAddress, scAddress,
                    message, pdu);
            return getSmsTracker(map, sentIntent,
                    deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri,
                    smsHeader, !lastPart, fullMessageText, true /*isText*/,
                    // MTK-START, it should set true by default to ignore this variable
                    true /*persistMessage*/);
                    // MTK-END
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendNewSubmitPdu(): getSubmitPdu() returned null");
            return null;
        }
    }

    @Override
    protected void sendSubmitPdu(SmsTracker tracker) {
        sendRawPdu(tracker);
    }

    // MTK-START, support validity for operator feature
    /** {@inheritDoc} */
    @Override
    protected SmsTracker getNewSubmitPduTracker(String destinationAddress, String scAddress,
            String message, SmsHeader smsHeader, int encoding,
            PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart,
            AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri,
            String fullMessageText, int validityPeriod) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddress, destinationAddress,
                    message, (deliveryIntent != null), SmsHeader.toByteArray(smsHeader),
                    encoding, smsHeader.languageTable, smsHeader.languageShiftTable,
                    validityPeriod);
        if (pdu != null) {
            HashMap map =  getSmsTrackerMap(destinationAddress, scAddress,
                    message, pdu);
            return getSmsTracker(map, sentIntent,
                    deliveryIntent, getFormat(), unsentPartCount, anyPartFailed, messageUri,
                    smsHeader, !lastPart, fullMessageText, true /*isText*/,
                    true /*persistMessage*/);
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.getNewSubmitPduTracker(): getSubmitPdu() returned null");
            return null;
        }
    }
    // MTK-END

    /** {@inheritDoc} */
    @Override
    protected void sendSms(SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;

        byte pdu[] = (byte[]) map.get("pdu");

        // MTK-START
        boolean isReadySend = false;
        synchronized (mSTrackersQueue) {
            if (mSTrackersQueue.isEmpty() || mSTrackersQueue.get(0) != tracker) {
                Rlog.d(TAG, "Add tracker into the list: " + tracker);
                mSTrackersQueue.add(tracker);
            }
            if (mSTrackersQueue.get(0) == tracker) {
                isReadySend = true;
            }
        }

        if (!isReadySend) {
            Rlog.d(TAG, "There is another tracker in-queue and is sending");
            return;
        }
        // MTK-END

        if (tracker.mRetryCount > 0) {
            Rlog.d(TAG, "sendSms: "
                    + " mRetryCount=" + tracker.mRetryCount
                    + " mMessageRef=" + tracker.mMessageRef
                    + " SS=" + mPhone.getServiceState().getState());

            // per TS 23.040 Section 9.2.3.6:  If TP-MTI SMS-SUBMIT (0x01) type
            //   TP-RD (bit 2) is 1 for retry
            //   and TP-MR is set to previously failed sms TP-MR
            if (((0x01 & pdu[0]) == 0x01)) {
                pdu[0] |= 0x04; // TP-RD
                pdu[1] = (byte) tracker.mMessageRef; // TP-MR
            }
        }
        Rlog.d(TAG, "sendSms: "
                + " isIms()=" + isIms()
                + " mRetryCount=" + tracker.mRetryCount
                + " mImsRetry=" + tracker.mImsRetry
                + " mMessageRef=" + tracker.mMessageRef
                + " SS=" + mPhone.getServiceState().getState());

        sendSmsByPstn(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendSmsByPstn(SmsTracker tracker) {
        int ss = mPhone.getServiceState().getState();
        // if sms over IMS is not supported on data and voice is not available...
        // MTK-START
        // For the Wifi calling, We need to support sending SMS when radio is power off
        // and wifi calling is enabled. So we need to pass the SMS sending request to the
        // modem when radio is OFF.
        if (!isIms() && ss != ServiceState.STATE_IN_SERVICE
                && !mTelephonyManager.isWifiCallingAvailable()) {
        // MTK-END
            tracker.onFailed(mContext, getNotInServiceError(ss), 0/*errorCode*/);
            // MTK-START
            Message delay = obtainMessage(EVENT_DELAY_SEND_MESSAGE_QUEUE, tracker);
            // Delay 10 milliseconds to avoid ANR in lots of wait to send messages inside
            sendMessageDelayed(delay, 10);
            // MTK-END
            return;
        }

        HashMap<String, Object> map = tracker.mData;

        byte smsc[] = (byte[]) map.get("smsc");
        byte[] pdu = (byte[]) map.get("pdu");
        Message reply = obtainMessage(EVENT_SEND_SMS_COMPLETE, tracker);

        // sms over gsm is used:
        //   if sms over IMS is not supported AND
        //   this is not a retry case after sms over IMS failed
        //     indicated by mImsRetry > 0
        if (0 == tracker.mImsRetry && !isIms()) {
            if (tracker.mRetryCount > 0) {
                // per TS 23.040 Section 9.2.3.6:  If TP-MTI SMS-SUBMIT (0x01) type
                //   TP-RD (bit 2) is 1 for retry
                //   and TP-MR is set to previously failed sms TP-MR
                if (((0x01 & pdu[0]) == 0x01)) {
                    pdu[0] |= 0x04; // TP-RD
                    pdu[1] = (byte) tracker.mMessageRef; // TP-MR
                }
            }
            if (tracker.mRetryCount == 0 && tracker.mExpectMore) {
                mCi.sendSMSExpectMore(IccUtils.bytesToHexString(smsc),
                        IccUtils.bytesToHexString(pdu), reply);
            } else {
                mCi.sendSMS(IccUtils.bytesToHexString(smsc),
                        IccUtils.bytesToHexString(pdu), reply);
            }
        } else {
            mCi.sendImsGsmSms(IccUtils.bytesToHexString(smsc),
                    IccUtils.bytesToHexString(pdu), tracker.mImsRetry,
                    tracker.mMessageRef, reply);
            // increment it here, so in case of SMS_FAIL_RETRY over IMS
            // next retry will be sent using IMS request again.
            tracker.mImsRetry++;
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
            Rlog.d(TAG, "GsmSMSDispatcher: subId = " + mPhone.getSubId()
                    + " slotId = " + mPhone.getPhoneId());
                return mUiccController.getUiccCardApplication(mPhone.getPhoneId(),
                        UiccController.APP_FAM_3GPP);
    }

    private void onUpdateIccAvailability() {
        if (mUiccController == null ) {
            return;
        }

        UiccCardApplication newUiccApplication = getUiccCardApplication();

        UiccCardApplication app = mUiccApplication.get();
        if (app != newUiccApplication) {
            if (app != null) {
                Rlog.d(TAG, "Removing stale icc objects.");
                if (mIccRecords.get() != null) {
                    mIccRecords.get().unregisterForNewSms(this);
                }
                mIccRecords.set(null);
                mUiccApplication.set(null);
            }
            if (newUiccApplication != null) {
                Rlog.d(TAG, "New Uicc application found");
                mUiccApplication.set(newUiccApplication);
                mIccRecords.set(newUiccApplication.getIccRecords());
                if (mIccRecords.get() != null) {
                    mIccRecords.get().registerForNewSms(this, EVENT_NEW_ICC_SMS, null);
                }
            }
        }
    }

    // MTK-START
    /**
     * Get the correct phone id to identify the event sent from which slot.
     *
     * @param msg message event
     *
     * @return integer value of phone id
     *
     */
    private Integer getUiccControllerPhoneId(Message msg) {
        AsyncResult ar;
        Integer phoneId = new Integer(SubscriptionManager.INVALID_PHONE_INDEX);

        ar = (AsyncResult) msg.obj;
        if (ar != null && ar.result instanceof Integer) {
            phoneId = (Integer) ar.result;
        }
        return phoneId;
    }

    /** {@inheritDoc} */
    protected void sendData(String destAddr, String scAddr, int destPort, int originalPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        Rlog.d(TAG, "GsmSmsDispatcher.sendData: enter");
        // MTK-START, DM operator feature
        if (isDmLock == true) {
            Rlog.d(TAG, "DM status: lock-on");
            return;
        }
        // MTK-END
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, destPort, originalPort, data, (deliveryIntent != null));
        if (pdu != null) {
            HashMap map = getSmsTrackerMap(destAddr, scAddr, destPort, data, pdu);
            SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat(),
                    null /*messageUri*/, false /*isExpectMore*/, null /*fullMessageText*/,
                    false /*isText*/, true /*persistMessage*/);

            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                DataSmsSender smsSender = new DataSmsSender(tracker);
                smsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(smsSender));
            } else {
                Rlog.v(TAG, "No carrier package.");
                sendRawPdu(tracker);
            }
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendData(): getSubmitPdu() returned null");
        }
    }

    /** {@inheritDoc} */
    protected void sendMultipartData(
            String destAddr, String scAddr, int destPort,
            ArrayList<SmsRawData> data, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        // DM operator
        if (isDmLock == true) {
            Rlog.d(TAG, "DM status: lock-on");
            return;
        }

        int refNumber = getNextConcatenatedRef() & 0x00FF;
        int msgCount = data.size();

        SmsTracker[] trackers = new SmsTracker[msgCount];

        for (int i = 0; i < msgCount; i++) {
            byte[] smsHeader = SmsHeader.getSubmitPduHeader(
                    destPort, refNumber, i + 1, msgCount);   // 1-based sequence

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }

            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = deliveryIntents.get(i);
            }

            SmsMessage.SubmitPdu pdus = SmsMessage.getSubmitPdu(scAddr, destAddr,
                    data.get(i).getBytes() , smsHeader, deliveryIntent != null);

            HashMap map =  getSmsTrackerMap(destAddr, scAddr, destPort, data.get(i).getBytes(),
                    pdus);

            // FIXME: should use getNewSubmitPduTracker?
            trackers[i] =
                getSmsTracker(map, sentIntent, deliveryIntent, getFormat(), null/*messageUri*/,
                        false /*isExpectMore*/, null /*fullMessageText*/, false /*isText*/,
                        true /*persistMessage*/);
        }

        if (data == null || trackers == null || trackers.length == 0
                || trackers[0] == null) {
            Rlog.e(TAG, "Cannot send multipart data. parts=" + data + " trackers=" + trackers);
            return;
        }

        String carrierPackage = getCarrierAppPackageName();
        if (carrierPackage != null) {
            Rlog.d(TAG, "Found carrier package.");
            // FIXME: should extends to multipart data
            DataSmsSender smsSender = new DataSmsSender(trackers[0]);
            smsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(smsSender));
        } else {
            Rlog.v(TAG, "No carrier package.");
            for (SmsTracker tracker : trackers) {
                if (tracker != null) {
                    sendRawPdu(tracker);
                } else {
                    Rlog.e(TAG, "Null tracker.");
                }
            }
        }
    }

    /** {@inheritDoc} */
    protected void activateCellBroadcastSms(int activate, Message response) {
        Message reply = obtainMessage(EVENT_ACTIVATE_CB_COMPLETE, response);
        mCi.setGsmBroadcastActivation(activate == 0, reply);
    }

    /** {@inheritDoc} */
    protected void getCellBroadcastSmsConfig(Message response) {

        Message reply = obtainMessage(EVENT_GET_CB_CONFIG_COMPLETE, response);
        mCi.getGsmBroadcastConfig(reply);
    }

    /** {@inheritDoc} */
    protected  void setCellBroadcastConfig(int[] configValuesArray, Message response) {
        // Unless CBS is implemented for GSM, this point should be unreachable.
        Rlog.e(TAG, "Error! The functionality cell broadcast sms is not implemented for GSM.");
        response.recycle();
    }

    /**
     * Configure cell broadcast SMS.
     * @param chIdList
     *            Channel ID list, fill in the fromServiceId, toServiceId, and selected
     *            in the SmsBroadcastConfigInfo only
     * @param langList
     *            Channel ID list, fill in the fromCodeScheme, toCodeScheme, and selected
     *            in the SmsBroadcastConfigInfo only
     * @param response
     *            Callback message is empty on completion
     */
    protected void setCellBroadcastConfig(ArrayList<SmsBroadcastConfigInfo> chIdList,
            ArrayList<SmsBroadcastConfigInfo> langList, Message response) {
        Message reply = obtainMessage(EVENT_SET_CB_CONFIG_COMPLETE, response);

        chIdList.addAll(langList);
        mCi.setGsmBroadcastConfig(
                chIdList.toArray(new SmsBroadcastConfigInfo[1]), reply);
    }

    /**
     * Query if the Cell Broadcast is activated or not
     *
     * @param response
     *            Callback message contains the activated status
     */
    protected void queryCellBroadcastActivation(Message response) {
        Message reply = obtainMessage(EVENT_QUERY_CB_ACTIVATION_COMPLETE, response);
        mCi.getGsmBroadcastConfig(reply);
    }

    @Override
    public int copyTextMessageToIccCard(String scAddress, String address, List<String> text,
            int status, long timestamp) {
        Rlog.d(TAG, "GsmSMSDispatcher: copy text message to icc card");

        if (checkPhoneNumber(scAddress) == false) {
            Rlog.d(TAG, "[copyText invalid sc address");
            scAddress = null;
        }

        if (checkPhoneNumber(address) == false) {
            Rlog.d(TAG, "[copyText invalid dest address");
            return RESULT_ERROR_INVALID_ADDRESS;
        }

        mSuccess = true;

        boolean isDeliverPdu = true;

        int msgCount = text.size();
        // we should check the available storage of SIM here,
        // but now we suppose it always be true
        if (true) {
            Rlog.d(TAG, "[copyText storage available");
        } else {
            Rlog.d(TAG, "[copyText storage unavailable");
            return RESULT_ERROR_SIM_MEM_FULL;
        }

        if (status == STATUS_ON_ICC_READ || status == STATUS_ON_ICC_UNREAD) {
            Rlog.d(TAG, "[copyText to encode deliver pdu");
            isDeliverPdu = true;
        } else if (status == STATUS_ON_ICC_SENT || status == STATUS_ON_ICC_UNSENT) {
            isDeliverPdu = false;
            Rlog.d(TAG, "[copyText to encode submit pdu");
        } else {
            Rlog.d(TAG, "[copyText invalid status, default is deliver pdu");
            // isDeliverPdu = true;
            return RESULT_ERROR_GENERIC_FAILURE;
        }

        Rlog.d(TAG, "[copyText msgCount " + msgCount);
        if (msgCount > 1) {
            Rlog.d(TAG, "[copyText multi-part message");
        } else if (msgCount == 1) {
            Rlog.d(TAG, "[copyText single-part message");
        } else {
            Rlog.d(TAG, "[copyText invalid message count");
            return RESULT_ERROR_GENERIC_FAILURE;
        }

        int refNumber = getNextConcatenatedRef() & 0x00FF;
        int encoding = SmsConstants.ENCODING_UNKNOWN;
        TextEncodingDetails details[] = new TextEncodingDetails[msgCount];
        for (int i = 0; i < msgCount; i++) {
            details[i] = SmsMessage.calculateLength(text.get(i), false);
            if (encoding != details[i].codeUnitSize &&
                (encoding == SmsConstants.ENCODING_UNKNOWN ||
                 encoding == SmsConstants.ENCODING_7BIT)) {
                encoding = details[i].codeUnitSize;
            }
        }

        for (int i = 0; i < msgCount; ++i) {
            if (mSuccess == false) {
                Rlog.d(TAG, "[copyText Exception happened when copy message");
                return RESULT_ERROR_GENERIC_FAILURE;
            }
            int singleShiftId = -1;
            int lockingShiftId = -1;
            int language = details[i].shiftLangId;
            int encoding_method = encoding;

            if (encoding == SmsConstants.ENCODING_7BIT) {
                Rlog.d(TAG, "Detail: " + i + " ted" + details[i]);
                if (details[i].useLockingShift && details[i].useSingleShift) {
                    singleShiftId = language;
                    lockingShiftId = language;
                    encoding_method = SmsMessage.ENCODING_7BIT_LOCKING_SINGLE;
                } else if (details[i].useLockingShift) {
                    lockingShiftId = language;
                    encoding_method = SmsMessage.ENCODING_7BIT_LOCKING;
                } else if (details[i].useSingleShift) {
                    singleShiftId = language;
                    encoding_method = SmsMessage.ENCODING_7BIT_SINGLE;
                }
            }

            byte[] smsHeader = null;
            if (msgCount > 1) {
                Rlog.d(TAG, "[copyText get pdu header for multi-part message");
                // 1-based sequence
                smsHeader = SmsHeader.getSubmitPduHeaderWithLang(
                        -1, refNumber, i + 1, msgCount, singleShiftId, lockingShiftId);
            }

            if (isDeliverPdu) {
                SmsMessage.DeliverPdu pdu = SmsMessage.getDeliverPduWithLang(scAddress, address,
                    text.get(i), smsHeader, timestamp, encoding, language);

                if (pdu != null) {
                    Rlog.d(TAG, "[copyText write deliver pdu into SIM");
                    mCi.writeSmsToSim(status, IccUtils.bytesToHexString(pdu.encodedScAddress),
                            IccUtils.bytesToHexString(pdu.encodedMessage),
                            obtainMessage(EVENT_COPY_TEXT_MESSAGE_DONE));
                }
            } else {
                SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPduWithLang(scAddress, address,
                          text.get(i), false, smsHeader, encoding_method, language);

                if (pdu != null) {
                    Rlog.d(TAG, "[copyText write submit pdu into SIM");
                    mCi.writeSmsToSim(status, IccUtils.bytesToHexString(pdu.encodedScAddress),
                            IccUtils.bytesToHexString(pdu.encodedMessage),
                            obtainMessage(EVENT_COPY_TEXT_MESSAGE_DONE));
                }
            }

            synchronized (mLock) {
                try {
                    Rlog.d(TAG, "[copyText wait until the message be wrote in SIM");
                    mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.d(TAG, "[copyText interrupted while trying to copy text message into SIM");
                    return RESULT_ERROR_GENERIC_FAILURE;
                }
            }
            Rlog.d(TAG, "[copyText thread is waked up");
        }

        if (mSuccess == true) {
            Rlog.d(TAG, "[copyText all messages have been copied into SIM");
            return RESULT_ERROR_SUCCESS;
        }

        Rlog.d(TAG, "[copyText copy failed");
        return RESULT_ERROR_GENERIC_FAILURE;
    }

    private boolean isValidSmsAddress(String address) {
        String encodedAddress = PhoneNumberUtils.extractNetworkPortion(address);

        return (encodedAddress == null) ||
                (encodedAddress.length() == address.length());
    }

    private boolean checkPhoneNumber(final char c) {
        return (c >= '0' && c <= '9') || (c == '*') || (c == '+')
                || (c == '#') || (c == 'N') || (c == ' ') || (c == '-');
    }

    private boolean checkPhoneNumber(final String address) {
        if (address == null) {
            return true;
        }

        Rlog.d(TAG, "checkPhoneNumber: " + address);
        for (int i = 0, n = address.length(); i < n; ++i) {
            if (checkPhoneNumber(address.charAt(i))) {
                continue;
            } else {
                return false;
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void sendTextWithEncodingType(String destAddr, String scAddr, String text,
            int encodingType, PendingIntent sentIntent, PendingIntent deliveryIntent,
            Uri messageUri, String callingPkg, boolean persistMessage) {
        if (isDmLock == true) {
            Rlog.d(TAG, "DM status: lock-on");
            return;
        }

        int encoding = encodingType;
        // begin modify by luotao for pr1032842 2015-12-04
        // old : TextEncodingDetails details = SmsMessage.calculateLength(text, false);
        int encodeMode = Settings.System.getInt(mContext.getContentResolver(), Settings.System.SMS_ENCODE_MODE, 0);
        int validPeriod = Settings.System.getInt(mContext.getContentResolver(),Settings.System.SMS_VALID_PERIOD, 0);        
        TextEncodingDetails details = SmsMessage.JRDcalculateLength(text, false, encodeMode);
        //end modify by luotao for pr1032842 2015-12-04        
        if (encoding != details.codeUnitSize &&
                (encoding == SmsConstants.ENCODING_UNKNOWN ||
                encoding == SmsConstants.ENCODING_7BIT)) {
            Rlog.d(TAG, "[enc conflict between details[" + details.codeUnitSize
                    + "] and encoding " + encoding);
            details.codeUnitSize = encoding;
        }

        // begin modify by luotao for pr1032842 2015-12-04
        /* old :
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, text, (deliveryIntent != null),
                null, encoding, details.languageTable, details.languageShiftTable);
                */
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, text, (deliveryIntent != null),
                null, encoding, details.languageTable, details.languageShiftTable, encodeMode, validPeriod);        
        // end modify by luotao for pr1032842 2015-12-04       

        if (pdu != null) {
            HashMap map = getSmsTrackerMap(destAddr, scAddr, text, pdu);
            // begin modify by luotao for pr1032842 2015-12-04
            // old : SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat(),
            // old :       messageUri, false /*isExpectMore*/, text /*fullMessageText*/, true /*isText*/,
            // old :        persistMessage);                    
            SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat(),
                    messageUri, false /*isExpectMore*/, text /*fullMessageText*/, false /*isText*/,
                    persistMessage);                    
            // end modify by luotao for pr1032842 2015-12-04

            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                TextSmsSender smsSender = new TextSmsSender(tracker);
                smsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(smsSender));
            } else {
                Rlog.v(TAG, "No carrier package.");
                sendRawPdu(tracker);
            }
        } else {
            Rlog.e(TAG,
                    "GsmSMSDispatcher.sendTextWithEncodingType(): getSubmitPdu() returned null");
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NULL_PDU);
                } catch (CanceledException ex) {
                    Rlog.e(TAG, "failed to send back RESULT_ERROR_NULL_PDU");
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void sendMultipartTextWithEncodingType(String destAddr, String scAddr,
            ArrayList<String> parts, int encodingType, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg,
            boolean persistMessage) {
        // DM operator
        if (isDmLock == true) {
            Rlog.d(TAG, "DM status: lock-on");
            return;
        }

        final String fullMessageText = getMultipartMessageText(parts);
        int refNumber = getNextConcatenatedRef() & 0xff;
        int msgCount = parts.size();
        int encoding = encodingType;

	 /*begin-20151103-huawei.wang-add for task857633*/
        int encodeMode = Settings.System.getInt(mContext.getContentResolver(), Settings.System.SMS_ENCODE_MODE, 0);
        Rlog.d(TAG, "sendMultipartTextWithEncodingType() encodeMode is " + encodeMode);
        int validPeriod = Settings.System.getInt(mContext.getContentResolver(),Settings.System.SMS_VALID_PERIOD, 0);
        Rlog.d(TAG, "sendMultipartTextWithEncodingType() SMS_VALID_PERIOD is " + validPeriod);
        /*end-20151103-huawei.wang-add for task857633*/

        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        for (int i = 0; i < msgCount; ++i) {
			
	    /*begin-20151103-huawei.wang-add for task857633*/
            //old: TextEncodingDetails details = SmsMessage.calculateLength(parts.get(i), false);
            TextEncodingDetails details = SmsMessage.JRDcalculateLength(parts.get(i), false, encodeMode);
            /*end-20151103-huawei.wang-add for task857633*/
			
            if (encoding != details.codeUnitSize &&
                (encoding == SmsConstants.ENCODING_UNKNOWN ||
                 encoding == SmsConstants.ENCODING_7BIT)) {
                Rlog.d(TAG, "[enc conflict between details[" + details.codeUnitSize
                        + "] and encoding " + encoding);
                details.codeUnitSize = encoding;
            }
            encodingForParts[i] = details;
        }

        SmsTracker[] trackers = new SmsTracker[msgCount];

        // States to track at the message level (for all parts)
        final AtomicInteger unsentPartCount = new AtomicInteger(msgCount);
        final AtomicBoolean anyPartFailed = new AtomicBoolean(false);

        for (int i = 0; i < msgCount; ++i) {
            SmsHeader.ConcatRef concatRef = new SmsHeader.ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;  // 1-based sequence
            concatRef.msgCount = msgCount;
            // TODO: We currently set this to true since our messaging app will never
            // send more than 255 parts (it converts the message to MMS well before that).
            // However, we should support 3rd party messaging apps that might need 16-bit
            // references
            // Note:  It's not sufficient to just flip this bit to true; it will have
            // ripple effects (several calculations assume 8-bit ref).
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;
            if (encoding == SmsConstants.ENCODING_7BIT) {
                smsHeader.languageTable = encodingForParts[i].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i].languageShiftTable;
            }

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }

            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = deliveryIntents.get(i);
            }
	   /*begin-20151103-huawei.wang-add for task857633*/
            // old:
            /* trackers[i] =
                getNewSubmitPduTracker(destAddr, scAddr, parts.get(i), smsHeader, encoding,
                        sentIntent, deliveryIntent, (i == (msgCount - 1)),
                        unsentPartCount, anyPartFailed, messageUri, fullMessageText); */
            trackers[i] =
                getNewSubmitPduTracker(destAddr, scAddr, parts.get(i), smsHeader, encoding,
                        sentIntent, deliveryIntent, (i == (msgCount - 1)),unsentPartCount,
                        anyPartFailed, messageUri, fullMessageText, true, encodeMode, validPeriod);
            /*end-20151103-huawei.wang-add for task857633*/
            trackers[i].mPersistMessage = persistMessage;
        }

        if (parts == null || trackers == null || trackers.length == 0
                || trackers[0] == null) {
            Rlog.e(TAG, "Cannot send multipart text. parts=" + parts + " trackers=" + trackers);
            return;
        }

        String carrierPackage = getCarrierAppPackageName();
        if (carrierPackage != null) {
            Rlog.d(TAG, "Found carrier package.");
            MultipartSmsSender smsSender = new MultipartSmsSender(parts, trackers);
            smsSender.sendSmsByCarrierApp(carrierPackage,
                    new MultipartSmsSenderCallback(smsSender));
        } else {
            Rlog.v(TAG, "No carrier package.");
            for (SmsTracker tracker : trackers) {
                if (tracker != null) {
                    sendSubmitPdu(tracker);
                } else {
                    Rlog.e(TAG, "Null tracker.");
                }
            }
        }
    }

    /**
     * Called when IccSmsInterfaceManager update SIM card fail due to SIM_FULL.
     */
    protected void handleIccFull() {
        // broadcast SIM_FULL intent
        mGsmInboundSmsHandler.mStorageMonitor.handleIccFull();
    }

    /**
     * Called when a CB activation result is received.
     *
     * @param ar AsyncResult passed into the message handler.
     */
    protected void handleQueryCbActivation(AsyncResult ar) {

        Boolean result = null;

        if (ar.exception == null) {
            ArrayList<SmsBroadcastConfigInfo> list =
                    (ArrayList<SmsBroadcastConfigInfo>) ar.result;

            if (list.size() == 0) {
                result = new Boolean(false);
            } else {
                SmsBroadcastConfigInfo cbConfig = list.get(0);
                Rlog.d(TAG, "cbConfig: " + cbConfig.toString());

                if (cbConfig.getFromCodeScheme() == -1 &&
                    cbConfig.getToCodeScheme() == -1 &&
                    cbConfig.getFromServiceId() == -1 &&
                    cbConfig.getToServiceId() == -1 &&
                    cbConfig.isSelected() == false) {

                    result = new Boolean(false);
                } else {
                    result = new Boolean(true);
                }
            }
        }

        Rlog.d(TAG, "queryCbActivation: " + result);
        AsyncResult.forMessage((Message) ar.userObj, result, ar.exception);
        ((Message) ar.userObj).sendToTarget();
    }

    /** {@inheritDoc} */
    @Override
    public void sendTextWithExtraParams(String destAddr, String scAddr, String text,
            Bundle extraParams, PendingIntent sentIntent, PendingIntent deliveryIntent,
            Uri messageUri, String callingPkg, boolean persistMessage) {
        Rlog.d(TAG, "sendTextWithExtraParams");

        // DM operator
        if (isDmLock == true) {
            Rlog.d(TAG, "DM status: lock-on");
            return;
        }
        int validityPeriod = extraParams.getInt(EXTRA_PARAMS_VALIDITY_PERIOD, -1);
        Rlog.d(TAG, "validityPeriod is " + validityPeriod);

        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, text, (deliveryIntent != null),
                null, SmsConstants.ENCODING_UNKNOWN, 0, 0, validityPeriod);

        if (pdu != null) {
            HashMap map = getSmsTrackerMap(destAddr, scAddr, text, pdu);
            SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat(),
                    messageUri, false /*isExpectMore*/, text /*fullMessageText*/, true /*isText*/,
                    persistMessage);

            String carrierPackage = getCarrierAppPackageName();
            if (carrierPackage != null) {
                Rlog.d(TAG, "Found carrier package.");
                TextSmsSender smsSender = new TextSmsSender(tracker);
                smsSender.sendSmsByCarrierApp(carrierPackage, new SmsSenderCallback(smsSender));
            } else {
                Rlog.v(TAG, "No carrier package.");
                sendRawPdu(tracker);
            }
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendTextWithExtraParams(): getSubmitPdu() returned null");
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NULL_PDU);
                } catch (CanceledException ex) {
                    Rlog.e(TAG, "failed to send back RESULT_ERROR_NULL_PDU");
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void sendMultipartTextWithExtraParams(String destAddr, String scAddr,
            ArrayList<String> parts, Bundle extraParams, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg,
            boolean persistMessage) {
        Rlog.d(TAG, "sendMultipartTextWithExtraParams");

        // DM operator
        if (isDmLock == true) {
            Rlog.d(TAG, "DM status: lock-on");
            return;
        }

        int validityPeriod = extraParams.getInt(EXTRA_PARAMS_VALIDITY_PERIOD, -1);
        Rlog.d(TAG, "validityPeriod is " + validityPeriod);

        final String fullMessageText = getMultipartMessageText(parts);
        int refNumber = getNextConcatenatedRef() & 0x00FF;
        int msgCount = parts.size();
        int encoding = SmsConstants.ENCODING_UNKNOWN;

        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        for (int i = 0; i < msgCount; i++) {
            TextEncodingDetails details = calculateLength(parts.get(i), false);
            if (encoding != details.codeUnitSize &&
                (encoding == SmsConstants.ENCODING_UNKNOWN ||
                 encoding == SmsConstants.ENCODING_7BIT)) {
                encoding = details.codeUnitSize;
            }
            encodingForParts[i] = details;
        }

        SmsTracker[] trackers = new SmsTracker[msgCount];

        // States to track at the message level (for all parts)
        final AtomicInteger unsentPartCount = new AtomicInteger(msgCount);
        final AtomicBoolean anyPartFailed = new AtomicBoolean(false);

        for (int i = 0; i < msgCount; i++) {
            SmsHeader.ConcatRef concatRef = new SmsHeader.ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;  // 1-based sequence
            concatRef.msgCount = msgCount;
            // TODO: We currently set this to true since our messaging app will never
            // send more than 255 parts (it converts the message to MMS well before that).
            // However, we should support 3rd party messaging apps that might need 16-bit
            // references
            // Note:  It's not sufficient to just flip this bit to true; it will have
            // ripple effects (several calculations assume 8-bit ref).
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;

            // Set the national language tables for 3GPP 7-bit encoding, if enabled.
            if (encoding == SmsConstants.ENCODING_7BIT) {
                smsHeader.languageTable = encodingForParts[i].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i].languageShiftTable;
            }

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }

            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = deliveryIntents.get(i);
            }

            trackers[i] =
                getNewSubmitPduTracker(destAddr, scAddr, parts.get(i), smsHeader, encoding,
                        sentIntent, deliveryIntent, (i == (msgCount - 1)),
                        unsentPartCount, anyPartFailed, messageUri, fullMessageText,
                        validityPeriod);
            trackers[i].mPersistMessage = persistMessage;
        }

        if (parts == null || trackers == null || trackers.length == 0
                || trackers[0] == null) {
            Rlog.e(TAG, "Cannot send multipart text. parts=" + parts + " trackers=" + trackers);
            return;
        }

        String carrierPackage = getCarrierAppPackageName();
        if (carrierPackage != null) {
            Rlog.d(TAG, "Found carrier package.");
            MultipartSmsSender smsSender = new MultipartSmsSender(parts, trackers);
            smsSender.sendSmsByCarrierApp(carrierPackage,
                    new MultipartSmsSenderCallback(smsSender));
        } else {
            Rlog.v(TAG, "No carrier package.");
            for (SmsTracker tracker : trackers) {
                if (tracker != null) {
                    sendSubmitPdu(tracker);
                } else {
                    Rlog.e(TAG, "Null tracker.");
                }
            }
        }
    }
    // MTK-END
}
