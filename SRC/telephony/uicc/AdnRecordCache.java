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

package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;

import android.telephony.Rlog;

import com.android.internal.telephony.gsm.UsimPhoneBookManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.RILConstants;

import com.mediatek.internal.telephony.uicc.AlphaTag;
import com.mediatek.internal.telephony.uicc.UsimGroup;
import com.mediatek.internal.telephony.uicc.UsimPBMemInfo;

/**
 * {@hide}
 */
public final class AdnRecordCache extends Handler implements IccConstants {

    static final String LOG_TAG = "AdnRecordCache";

    //***** Instance Variables

    private IccFileHandler mFh;
    private UsimPhoneBookManager mUsimPhoneBookManager;
    private CommandsInterface mCi;
    private UiccCardApplication mCurrentApp;

    // Indexed by EF ID
    SparseArray<ArrayList<AdnRecord>> mAdnLikeFiles
        = new SparseArray<ArrayList<AdnRecord>>();

    // People waiting for ADN-like files to be loaded
    SparseArray<ArrayList<Message>> mAdnLikeWaiters
        = new SparseArray<ArrayList<Message>>();

    // People waiting for adn record to be updated
    SparseArray<Message> mUserWriteResponse = new SparseArray<Message>();

    //***** Event Constants

    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
    static final int EVENT_UPDATE_ADN_DONE = 2;

    //***** Constructor

    public static int MAX_PHB_NAME_LENGTH = 60;
    public static int MAX_PHB_NUMBER_LENGTH = 40;
    public static int MAX_PHB_NUMBER_ANR_LENGTH = 20;
    public static int MAX_PHB_NUMBER_ANR_COUNT = 3;

    private final Object mLock = new Object();
    boolean mSuccess = false;
    private boolean mLocked = false;

    private static int ADN_FILE_SIZE = 250;

    AdnRecordCache(IccFileHandler fh) {
        mFh = fh;
        mCi = null;
        mCurrentApp = null;
        mUsimPhoneBookManager = new UsimPhoneBookManager(mFh, this);
    }

    AdnRecordCache(IccFileHandler fh, CommandsInterface ci, UiccCardApplication app) {
        mFh = fh;
        mCi = ci;
        mCurrentApp = app;
        mUsimPhoneBookManager = new UsimPhoneBookManager(mFh, this, ci, app);
    }

    //***** Called from SIMRecords

    /**
     * Called from SIMRecords.onRadioNotAvailable and SIMRecords.handleSimRefresh.
     */
    public void reset() {
        logd("reset");
        mAdnLikeFiles.clear();
        mUsimPhoneBookManager.reset();

        clearWaiters();
        clearUserWriters();

    }

    private void clearWaiters() {
        int size = mAdnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            ArrayList<Message> waiters = mAdnLikeWaiters.valueAt(i);
            AsyncResult ar = new AsyncResult(null, null, new RuntimeException("AdnCache reset"));
            notifyWaiters(waiters, ar);
        }
        mAdnLikeWaiters.clear();
    }

    private void clearUserWriters() {
        logd("clearUserWriters,mLocked " + mLocked);
        if (mLocked) {
            synchronized (mLock) {
                mLock.notifyAll();
            }
            mLocked = false;
        }
        int size = mUserWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse(mUserWriteResponse.valueAt(i), "AdnCace reset");
        }
        mUserWriteResponse.clear();
    }

    /**
     * @return List of AdnRecords for efid if we've already loaded them this
     *         radio session, or null if we haven't
     */
    public ArrayList<AdnRecord> getRecordsIfLoaded(int efid) {
        return mAdnLikeFiles.get(efid);
    }

    /**
     * Returns extension ef associated with ADN-like EF or -1 if
     * we don't know.
     *
     * See 3GPP TS 51.011 for this mapping
     */
    public int extensionEfForEf(int efid) {
        switch (efid) {
            case EF_MBDN: return EF_EXT6;
            case EF_ADN: return EF_EXT1;
            case EF_SDN: return EF_EXT3;
            case EF_FDN: return EF_EXT2;
            case EF_MSISDN: return EF_EXT1;
            case EF_PBR: return 0; // The EF PBR doesn't have an extension record
            default: return -1;
        }
    }

    private void sendErrorResponse(Message response, String errString) {

        sendErrorResponse(
                response,
                errString,
                RILConstants.GENERIC_FAILURE);
    }

    private void sendErrorResponse(Message response, String errString, int ril_errno) {

        CommandException e = CommandException.fromRilErrno(ril_errno);

        if (response != null) {
            logd(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    /**
     * Update an ADN-like record in EF by record index
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param adn is the new adn to be stored
     * @param recordIndex is the 1-based adn record index
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public synchronized void updateAdnByIndex(int efid, AdnRecord adn, int recordIndex, String pin2,
            Message response) {

        int extensionEF = extensionEfForEf(efid);
        int i = 0;
        String anr = null;
        int adnIndex = ((recordIndex - 1) % ADN_FILE_SIZE) + 1;

        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }
        // MTK-START [mtk80601][111215][ALPS00093395]
        if (adn.mAlphaTag.length() > MAX_PHB_NAME_LENGTH) {

            sendErrorResponse(
                    response,
                    "the input length of mAlphaTag is too long: " + adn.mAlphaTag,
                    RILConstants.TEXT_STRING_TOO_LONG);
            return;
        }

        for (i = 0; i < MAX_PHB_NUMBER_ANR_COUNT; i++) {
            anr = adn.getAdditionalNumber(i);
            if (anr != null && anr.length() > MAX_PHB_NUMBER_ANR_LENGTH) {

                sendErrorResponse(
                        response,
                        "the input length of additional number is too long: " + anr,
                        RILConstants.ADDITIONAL_NUMBER_STRING_TOO_LONG);
                return;
            }
        }

        int num_length = adn.mNumber.length();
        if (adn.mNumber.indexOf('+') != -1) {
            num_length--;
        }

        if (num_length > MAX_PHB_NUMBER_LENGTH) {

            sendErrorResponse(
                    response,
                    "the input length of phoneNumber is too long: " + adn.mNumber,
                    RILConstants.DIAL_STRING_TOO_LONG);

            return;
        }


        //add for uicc card start. the efid is EF_PBR if only it is uicc card.
        ArrayList<AdnRecord>  oldAdnList;

        if (efid == IccConstants.EF_PBR) {
            oldAdnList = mUsimPhoneBookManager.loadEfFilesFromUsim();

            if (oldAdnList == null) {
                sendErrorResponse(
                        response,
                        "Adn list not exist for EF:" + efid,
                        RILConstants.ADN_LIST_NOT_EXIST);
                return;
            }

            AdnRecord foundAdn = oldAdnList.get(recordIndex - 1);
            efid = foundAdn.mEfid; //change into adn file id
            extensionEF = foundAdn.mExtRecord;

            adn.mEfid = efid; //update efid in adn
        }
        //add for uicc card end.

        for (i = 0; i < MAX_PHB_NUMBER_ANR_COUNT; i++) {
            anr = adn.getAdditionalNumber(i);
            if (!mUsimPhoneBookManager.isAnrCapacityFree(anr, recordIndex, i)) {
                sendErrorResponse(
                        response,
                        "drop the additional number for the update fail: " + anr,
                        RILConstants.ADDITIONAL_NUMBER_SAVE_FAILURE);
                return;
            }
        }

        // MTK-END [mtk80601][111215][ALPS00093395]
        Message pendingResponse = mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }

        mUserWriteResponse.put(efid, response);
        if (efid == IccConstants.EF_ADN || efid == IccConstants.EF_PBR || efid == 0x4F3A || efid == 0x4F3B || efid == 0x4F3C || efid == 0x4F3D) {
            if (adn.mAlphaTag.length() == 0 && adn.mNumber.length() == 0) {
                // delete the group info
                mUsimPhoneBookManager.removeContactGroup(recordIndex);
            }
        }
        synchronized (mLock) {
            mSuccess = false;
            mLocked = true;
            if (mFh instanceof CsimFileHandler) {
                new AdnRecordLoader(mFh).updateEF(adn, efid, extensionEF,
                    adnIndex, pin2,
                    obtainMessage(EVENT_UPDATE_ADN_DONE, efid, adnIndex, adn));
            } else {
                new AdnRecordLoader(mFh).updateEF(adn, efid, extensionEF,
                    recordIndex, pin2,
                    obtainMessage(EVENT_UPDATE_ADN_DONE, efid, recordIndex, adn));
            }
            // MTK-START [mtk80601][111215][ALPS00093395]

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                return;
            }
        }
        if (!mSuccess) {
            return;
        }
        // update anr/grpIds/emails if necessary
        if (efid == IccConstants.EF_ADN || efid == IccConstants.EF_PBR || efid == 0x4F3A || efid == 0x4F3B || efid == 0x4F3C || efid == 0x4F3D) {
            try {
                if (mUsimPhoneBookManager.isSupportSne()) {
                    mUsimPhoneBookManager.updateSneByAdnIndex(adn.sne, recordIndex);
                }
                for (i = 0; i < MAX_PHB_NUMBER_ANR_COUNT; i++) {
                    anr = adn.getAdditionalNumber(i);
                    mUsimPhoneBookManager.updateAnrByAdnIndex(anr, recordIndex, i);
                }
                int success = mUsimPhoneBookManager.updateEmailsByAdnIndex(adn.mEmails, recordIndex);
                if (-1 == success) {
                    sendErrorResponse(
                            response,
                            "drop the email for the limitation of the SIM card",
                            RILConstants.EMAIL_SIZE_LIMIT);
                } else if (-2 == success) {
                    sendErrorResponse(
                            response,
                            "the email string is too long",
                            RILConstants.EMAIL_NAME_TOOLONG);
                    Rlog.e(LOG_TAG, "haman, by index email too long");
                } else {
                    AsyncResult.forMessage(response, null, null);
                    response.sendToTarget();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Rlog.e(LOG_TAG, "exception occured when update anr and email "
                // + e);
                return;
            }
        } else if (efid == IccConstants.EF_FDN) {
            AsyncResult.forMessage(response, null, null);
            response.sendToTarget();
        }
        // MTK-END [mtk80601][111215][ALPS00093395]
    }

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * The ADN-like records must be read through requestLoadAllAdnLike() before
     *
     * @param efid must be one of EF_ADN, EF_FDN, and EF_SDN
     * @param oldAdn is the adn to be replaced
     *        If oldAdn.isEmpty() is ture, it insert the newAdn
     * @param newAdn is the adn to be stored
     *        If newAdn.isEmpty() is true, it delete the oldAdn
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public synchronized int updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn,
            String pin2, Message response) {
        logd("updateAdnBySearch efid:" + efid + "pin2:" + pin2 + ", oldAdn [" + oldAdn
                + "], new Adn[" + newAdn + "]");
        int index = -1;
        int extensionEF;
        int i = 0;
        String anr = null;
        extensionEF = extensionEfForEf(efid);
        int adnIndex = 0;

        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return index;
        }
        // MTK-START [mtk80601][111215][ALPS00093395]
        if (newAdn.mAlphaTag.length() > MAX_PHB_NAME_LENGTH) {

            sendErrorResponse(
                    response,
                    "the input length of mAlphaTag is too long: " + newAdn.mAlphaTag,
                    RILConstants.TEXT_STRING_TOO_LONG);
            return index;
        }

        int num_length = newAdn.mNumber.length();
        if (newAdn.mNumber.indexOf('+') != -1) {
            num_length--;
        }

        if (num_length > MAX_PHB_NUMBER_LENGTH) {

            sendErrorResponse(
                    response,
                    "the input length of phoneNumber is too long: " + newAdn.mNumber,
                    RILConstants.DIAL_STRING_TOO_LONG);

            return index;
        }

        for (i = 0; i < MAX_PHB_NUMBER_ANR_COUNT; i++) {
            anr = newAdn.getAdditionalNumber(i);
            if (anr != null) {
                num_length = anr.length();
                if (anr.indexOf('+') != -1) {
                num_length--;
                }

                if (num_length > MAX_PHB_NUMBER_ANR_LENGTH) {
                    sendErrorResponse(
                            response,
                            "the input length of additional number is too long: "
                                + anr,
                            RILConstants.ADDITIONAL_NUMBER_STRING_TOO_LONG);
                    return index;
                }
            }
        }
        // MTK-END [mtk80601][111215][ALPS00093395]

        if (!mUsimPhoneBookManager.checkEmailLength(newAdn.mEmails)) {
            sendErrorResponse(
                    response,
                    "the email string is too long",
                    RILConstants.EMAIL_NAME_TOOLONG);
            return index;
        }

        ArrayList<AdnRecord>  oldAdnList;

        if (efid == EF_PBR) {
            oldAdnList = mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            oldAdnList = getRecordsIfLoaded(efid);
        }

        if (oldAdnList == null) {
            sendErrorResponse(
                    response,
                    "Adn list not exist for EF:" + efid,
                    RILConstants.ADN_LIST_NOT_EXIST);
            return index;
        }

        /* Move to upper for more error handle.
        int index = -1;
        */
        int count = 1;
        for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext(); ) {
            if (oldAdn.isEqual(it.next())) {
                index = count;
                break;
            }
            count++;
        }
        logd("updateAdnBySearch index " + index);
        if (index == -1) {
            if (oldAdn.mAlphaTag.length() == 0 && oldAdn.mNumber.length() == 0) {
                sendErrorResponse(
                        response,
                        "Adn record don't exist for " + oldAdn,
                        RILConstants.SIM_MEM_FULL);
            } else {
                sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            }
            return index;
        }

        if (efid == EF_PBR) {
            //if the index is more than 250, the the adn is belong to the file which EFid is 4F3B
            AdnRecord foundAdn = oldAdnList.get(index - 1);
            efid = foundAdn.mEfid;
            extensionEF = foundAdn.mExtRecord;
            index = foundAdn.mRecordNumber; //so we change the corrected index. ex the index is 251, then it will be the first adn in the file 4F3B, so change it into mRecordNumber which is 1.

            newAdn.mEfid = efid;
            newAdn.mExtRecord = extensionEF;
            newAdn.mRecordNumber = index;
        }

        Message pendingResponse = mUserWriteResponse.get(efid);

        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return index;
        }
        if (0 == efid) {
            sendErrorResponse(response, "Abnormal efid: " + efid);
            return index;
        }
        if (!mUsimPhoneBookManager.checkEmailCapacityFree(index, newAdn.mEmails)) {
            sendErrorResponse(
                    response,
                    "drop the email for the limitation of the SIM card",
                    RILConstants.EMAIL_SIZE_LIMIT);
            return index;
        }
        for (i = 0; i < MAX_PHB_NUMBER_ANR_COUNT; i++) {
            anr = newAdn.getAdditionalNumber(i);
            if (!mUsimPhoneBookManager.isAnrCapacityFree(anr, index, i)) {
                sendErrorResponse(
                        response,
                        "drop the additional number for the write fail: " + anr,
                        RILConstants.ADDITIONAL_NUMBER_SAVE_FAILURE);
                return index;
            }
        }

        mUserWriteResponse.put(efid, response);

        synchronized (mLock) {
            mSuccess = false;
            mLocked = true;
            adnIndex = ((index - 1) % ADN_FILE_SIZE) + 1;
            if (mFh instanceof CsimFileHandler) {
                logd("updateAdnBySearch CsimFileHandler index " + index);
                  newAdn.setRecordIndex(adnIndex);
                new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF,
                    adnIndex, pin2,
                    obtainMessage(EVENT_UPDATE_ADN_DONE, efid, adnIndex, newAdn));
            } else {
                new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF,
                    index, pin2,
                    obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));
            }
            // MTK-START [mtk80601][111215][ALPS00093395]

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                return index;
            }
        }
        if (!mSuccess) {
            logd("updateAdnBySearch mSuccess:" + mSuccess);
            return index;
        }
        int success = 0;
        if (efid == EF_ADN || efid == EF_PBR || efid == 0x4F3A || efid == 0x4F3B || efid == 0x4F3C || efid == 0x4F3D) {
            if (mUsimPhoneBookManager.isSupportSne()) {
                mUsimPhoneBookManager.updateSneByAdnIndex(newAdn.sne, index);
            }
            for (i = 0; i < MAX_PHB_NUMBER_ANR_COUNT; i++) {
                anr = newAdn.getAdditionalNumber(i);
                mUsimPhoneBookManager.updateAnrByAdnIndex(anr, index, i);
            }
            success = mUsimPhoneBookManager.updateEmailsByAdnIndex(newAdn.mEmails, index);
        }

        if (-1 == success) {
            sendErrorResponse(response,
                    "drop the email for the limitation of the SIM card",
                    RILConstants.EMAIL_SIZE_LIMIT);
        } else if (-2 == success) {
            sendErrorResponse(
                    response,
                    "the email string is too long",
                    RILConstants.EMAIL_NAME_TOOLONG);
            Rlog.e(LOG_TAG, "haman, by search email too long");
        } else {
            logd("updateAdnBySearch response:" + response);
            AsyncResult.forMessage(response, null, null);
            response.sendToTarget();
        }
        return index;
        // MTK-END [mtk80601][111215][ALPS00093395]

    }

    /**
     * Responds with exception (in response) if efid is not a known ADN-like
     * record
     */
    public void
    requestLoadAllAdnLike (int efid, int extensionEf, Message response) {
        ArrayList<Message> waiters;
        ArrayList<AdnRecord> result;
        logd("requestLoadAllAdnLike efid = " + efid);
        logd("requestLoadAllAdnLike extensionEf = " + extensionEf);
        if (efid == EF_PBR) {
            result = mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            result = getRecordsIfLoaded(efid);
        }
        logd("requestLoadAllAdnLike result = null ?" + (result == null));

        // Have we already loaded this efid?
        if (result != null) {
            if (response != null) {
                AsyncResult.forMessage(response).result = result;
                response.sendToTarget();
            }

            return;
        }

        // Have we already *started* loading this efid?

        waiters = mAdnLikeWaiters.get(efid);

        if (waiters != null) {
            // There's a pending request for this EF already
            // just add ourselves to it

            waiters.add(response);
            return;
        }

        // Start loading efid

        waiters = new ArrayList<Message>();
        waiters.add(response);

        mAdnLikeWaiters.put(efid, waiters);

        if (extensionEf < 0) {
            // respond with error if not known ADN-like record

            if (response != null) {
                AsyncResult.forMessage(response).exception
                    = new RuntimeException("EF is not known ADN-like EF:" + efid);
                response.sendToTarget();
            }

            return;
        }

        new AdnRecordLoader(mFh).loadAllFromEF(efid, extensionEf,
            obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, 0));
    }

    //***** Private methods

    private void
    notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {

        if (waiters == null) {
            return;
        }

        for (int i = 0, s = waiters.size() ; i < s ; i++) {
            Message waiter = waiters.get(i);

            if (waiter != null) {
                AsyncResult.forMessage(waiter, ar.result, ar.exception);
                waiter.sendToTarget();
            }
        }
    }

    //***** Overridden from Handler

    @Override
    public void
    handleMessage(Message msg) {
        AsyncResult ar;
        int efid;
        boolean flag = false;

        switch(msg.what) {
            case EVENT_LOAD_ALL_ADN_LIKE_DONE:
                /* arg1 is efid, obj.result is ArrayList<AdnRecord>*/
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                ArrayList<Message> waiters;

                waiters = mAdnLikeWaiters.get(efid);
                mAdnLikeWaiters.delete(efid);

                if (ar.exception == null) {
                    mAdnLikeFiles.put(efid, (ArrayList<AdnRecord>) ar.result);
                } else {
                    Rlog.d(LOG_TAG, "EVENT_LOAD_ALL_ADN_LIKE_DONE exception", ar.exception);
                }
                notifyWaiters(waiters, ar);


                Rlog.d(LOG_TAG, "load all adns and set flag into ture");
                flag = true;
                break;
            case EVENT_UPDATE_ADN_DONE:
                logd("EVENT_UPDATE_ADN_DONE");
                if (mLocked) {
                    synchronized (mLock) {
                        ar = (AsyncResult) msg.obj;
                        efid = msg.arg1;
                        int index = msg.arg2;
                        AdnRecord adn = (AdnRecord) (ar.userObj);

                        if (ar.exception == null) {
                            if (null != adn) {
                                adn.setRecordIndex(index);
                                if (adn.mEfid <= 0) {
                                    adn.mEfid = efid;
                                }
                            }

                            logd("mAdnLikeFiles changed index:" + index + ",adn:" + adn);
                            if (mFh instanceof CsimFileHandler) {
                                if (null != mAdnLikeFiles && null != mAdnLikeFiles.get(efid)) {
                                    if (efid == 0x4F3B) {
                                        //the value of index in mUsimPhoneBookManager is 250+index, the 1~250 records come form 0x4F3A
                                        index += 250;
                                        adn.setRecordIndex(index);
                                        mAdnLikeFiles.get(efid).set(index - 250 - 1, adn);
                                        logd("mAdnLikeFiles after add changed index:" + index + ",adn:" + adn + " efid:" + efid);
                                    } else {
                                        mAdnLikeFiles.get(efid).set(index - 1, adn);
                                    }
                                }
                                if ((null != mUsimPhoneBookManager) && (efid != IccConstants.EF_FDN)) {
                                    mUsimPhoneBookManager.updateUsimPhonebookRecordsList(index - 1, adn);
                                }
                            } else {
                                if (null != mAdnLikeFiles && null != mAdnLikeFiles.get(efid)) {
                                    mAdnLikeFiles.get(efid).set(index - 1, adn);
                                }
                                if ((null != mUsimPhoneBookManager) && (efid != IccConstants.EF_FDN)) {
                                    if (efid == 0x4F3B) {
                                        //the value of index in mUsimPhoneBookManager is 250+index, the 1~250 records come form 0x4F3A
                                        index += 250;
                                    }
                                    mUsimPhoneBookManager.updateUsimPhonebookRecordsList(index - 1, adn);
                                }
                            }
                        }

                        Message response = mUserWriteResponse.get(efid);
                        mUserWriteResponse.delete(efid);

                        logd("AdnRecordCacheEx: " + ar.exception);

                        if (ar.exception != null && response != null) {
                            AsyncResult.forMessage(response, null, ar.exception);
                            response.sendToTarget();
                        }
                        mSuccess = ar.exception == null;
                        mLock.notifyAll();
                        mLocked = false;

                        Rlog.d(LOG_TAG, "update  adn and set flag into ture");
                        flag = true;
                    }
                }
                break;
            default:
                break;
        }

        if ((mFh instanceof CsimFileHandler) && (msg.arg1 == 0x4F3A || msg.arg1 == 0x4F3B || msg.arg1 == 0x4F3C || msg.arg1 == 0x4F3D) && flag) {
            ArrayList<AdnRecord> adnList = mUsimPhoneBookManager.getAdnListFromUsim();

            if (adnList != null) {
                int totalSize = adnList.size();
                int usedRecord = 0;
                int i = 0;

                for (i = 0; i < totalSize; i++) {
                    if (!adnList.get(i).isEmpty()) {
                        usedRecord++;
                        Rlog.d(LOG_TAG, "print userRecord: " + adnList.get(i));
                    }
                }

                Rlog.d(LOG_TAG, "setPhbRecordStorageInfo totalSize = " + totalSize + " usedRecord = " + usedRecord);
                ((CsimFileHandler) mFh).setPhbRecordStorageInfo(totalSize, usedRecord);
            }
        }

    }

    // MTK-START [mtk80601][111215][ALPS00093395]
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, "[AdnRecordCache] " + msg);
    }

    public List<UsimGroup> getUsimGroups() {
        return mUsimPhoneBookManager.getUsimGroups();
    }

    public String getUsimGroupById(int nGasId) {
        return mUsimPhoneBookManager.getUsimGroupById(nGasId);
    }

    public boolean removeUsimGroupById(int nGasId) {
        return mUsimPhoneBookManager.removeUsimGroupById(nGasId);
    }

    public int insertUsimGroup(String grpName) {
        return mUsimPhoneBookManager.insertUsimGroup(grpName);
    }

    public int updateUsimGroup(int nGasId, String grpName) {
        return mUsimPhoneBookManager.updateUsimGroup(nGasId, grpName);
    }

    public boolean addContactToGroup(int adnIndex, int grpIndex) {
        return mUsimPhoneBookManager.addContactToGroup(adnIndex, grpIndex);
    }

    public boolean removeContactFromGroup(int adnIndex, int grpIndex) {
        return mUsimPhoneBookManager.removeContactFromGroup(adnIndex, grpIndex);
    }

    public boolean updateContactToGroups(int adnIndex, int[] grpIdList) {
        return mUsimPhoneBookManager.updateContactToGroups(adnIndex, grpIdList);
    }

    public boolean moveContactFromGroupsToGroups(int adnIndex, int[] fromGrpIdList, int[] toGrpIdList) {
        return mUsimPhoneBookManager.moveContactFromGroupsToGroups(adnIndex, fromGrpIdList, toGrpIdList);
    }

    public int hasExistGroup(String grpName) {
        return mUsimPhoneBookManager.hasExistGroup(grpName);
    }

    public int getUsimGrpMaxNameLen() {
        return mUsimPhoneBookManager.getUsimGrpMaxNameLen();
    }

    public int getUsimGrpMaxCount() {
        return mUsimPhoneBookManager.getUsimGrpMaxCount();
    }

    private void dumpAdnLikeFile() {
        int size = mAdnLikeFiles.size();
        logd("dumpAdnLikeFile size " + size);
        int key;
        for (int i = 0; i < size; i++) {
            key = mAdnLikeFiles.keyAt(i);

            ArrayList<AdnRecord> records = mAdnLikeFiles.get(key);
            logd("dumpAdnLikeFile index " + i + " key " + key + "records size " + records.size());
            for (int j = 0; j < records.size(); j++) {
                AdnRecord record = records.get(j);
                logd("mAdnLikeFiles[" + j + "]=" + record);
            }
        }
    }

    public ArrayList<AlphaTag> getUsimAasList() {
        return mUsimPhoneBookManager.getUsimAasList();
    }

    public String getUsimAasById(int index) {
        // TODO
        return mUsimPhoneBookManager.getUsimAasById(index, 0);
    }

    public boolean removeUsimAasById(int index, int pbrIndex) {
        return mUsimPhoneBookManager.removeUsimAasById(index, pbrIndex);
    }

    public int insertUsimAas(String aasName) {
        return mUsimPhoneBookManager.insertUsimAas(aasName);
    }

    public boolean updateUsimAas(int index, int pbrIndex, String aasName) {
        return mUsimPhoneBookManager.updateUsimAas(index, pbrIndex, aasName);
    }

    /**
     * @param adnIndex: ADN index
     * @param aasIndex: change AAS to the value refered by aasIndex, -1 means
     *            remove
     * @return
     */
    public boolean updateAdnAas(int adnIndex, int aasIndex) {
        return mUsimPhoneBookManager.updateAdnAas(adnIndex, aasIndex);
    }

    public int getAnrCount() {
        return mUsimPhoneBookManager.getAnrCount();
    }

    public int getEmailCount() {
        return mUsimPhoneBookManager.getEmailCount();
    }

    public int getUsimAasMaxCount() {
        return mUsimPhoneBookManager.getUsimAasMaxCount();
    }

    public int getUsimAasMaxNameLen() {
        return mUsimPhoneBookManager.getUsimAasMaxNameLen();
    }

    public boolean hasSne() {
        return mUsimPhoneBookManager.hasSne();
    }

    public int getSneRecordLen() {
        return mUsimPhoneBookManager.getSneRecordLen();
    }

    public boolean isAdnAccessible() {
        return mUsimPhoneBookManager.isAdnAccessible();
    }

    public boolean isUsimPhbEfAndNeedReset(int fileId) {
        return mUsimPhoneBookManager.isUsimPhbEfAndNeedReset(fileId);
    }

    public UsimPBMemInfo[] getPhonebookMemStorageExt() {
        return mUsimPhoneBookManager.getPhonebookMemStorageExt();
    }
    // MTK-END [mtk80601][111215][ALPS00093395]
    public boolean isUICCCard() {
        if (mFh instanceof CsimFileHandler) {
            return true;
        }
        return false;
    }
}
