/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.os.ServiceManager;


import com.android.internal.telephony.uicc.AdnRecord;

import java.util.List;


import com.mediatek.internal.telephony.uicc.AlphaTag;
import com.mediatek.internal.telephony.uicc.UsimGroup;
import com.mediatek.internal.telephony.uicc.UsimPBMemInfo;

/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */
public class IccPhoneBookInterfaceManagerProxy {
    private IccPhoneBookInterfaceManager mIccPhoneBookInterfaceManager;

    public IccPhoneBookInterfaceManagerProxy(IccPhoneBookInterfaceManager
            iccPhoneBookInterfaceManager) {
        mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public void setmIccPhoneBookInterfaceManager(
            IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public boolean
    updateAdnRecordsInEfBySearch (int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber,
            String pin2) {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(
                efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    public int updateAdnRecordsInEfBySearchWithError(int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber, String pin2) {

        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearchWithError(
                efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    public int updateUsimPBRecordsInEfBySearchWithError(int efid,
            String oldTag, String oldPhoneNumber, String oldAnr, String oldGrpIds,
            String[] oldEmails,
            String newTag, String newPhoneNumber, String newAnr, String newGrpIds,
            String[] newEmails) {

        return mIccPhoneBookInterfaceManager.updateUsimPBRecordsInEfBySearchWithError(
                efid, oldTag, oldPhoneNumber, oldAnr, oldGrpIds, oldEmails, newTag, newPhoneNumber,
                newAnr, newGrpIds, newEmails);
    }

    public int updateUsimPBRecordsBySearchWithError(int efid, AdnRecord oldAdn, AdnRecord newAdn) {

        return mIccPhoneBookInterfaceManager.updateUsimPBRecordsBySearchWithError(efid, oldAdn,
                newAdn);
    }

    public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex(efid,
                newTag, newPhoneNumber, index, pin2);
    }

    public int updateAdnRecordsInEfByIndexWithError(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {

        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndexWithError(efid,
                newTag, newPhoneNumber, index, pin2);

    }

    public int updateUsimPBRecordsInEfByIndexWithError(int efid, String newTag,
            String newPhoneNumber, String newAnr, String newGrpIds, String[] newEmails, int index) {

        return mIccPhoneBookInterfaceManager.updateUsimPBRecordsInEfByIndexWithError(efid,
                newTag, newPhoneNumber, newAnr, newGrpIds, newEmails, index);

    }

    public int updateUsimPBRecordsByIndexWithError(int efid, AdnRecord record, int index) {

        return mIccPhoneBookInterfaceManager.updateUsimPBRecordsByIndexWithError(efid, record,
                index);

    }

    public int[] getAdnRecordsSize(int efid) {
        return mIccPhoneBookInterfaceManager.getAdnRecordsSize(efid);
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        return mIccPhoneBookInterfaceManager.getAdnRecordsInEf(efid);
    }

    public boolean isPhbReady() {

        return mIccPhoneBookInterfaceManager.isPhbReady();
    }

    public List<UsimGroup> getUsimGroups() {
        return mIccPhoneBookInterfaceManager.getUsimGroups();
    }

    public String getUsimGroupById(int nGasId) {
        return mIccPhoneBookInterfaceManager.getUsimGroupById(nGasId);
    }

    public boolean removeUsimGroupById(int nGasId) {
        return mIccPhoneBookInterfaceManager.removeUsimGroupById(nGasId);
    }

    public int insertUsimGroup(String grpName) {
        return mIccPhoneBookInterfaceManager.insertUsimGroup(grpName);
    }

    public int updateUsimGroup(int nGasId, String grpName) {
        return mIccPhoneBookInterfaceManager.updateUsimGroup(nGasId, grpName);
    }

    public boolean addContactToGroup(int adnIndex, int grpIndex) {
        return mIccPhoneBookInterfaceManager.addContactToGroup(adnIndex, grpIndex);
    }

    public boolean removeContactFromGroup(int adnIndex, int grpIndex) {
        return mIccPhoneBookInterfaceManager.removeContactFromGroup(adnIndex, grpIndex);
    }

    public boolean updateContactToGroups(int adnIndex, int[] grpIdList) {
        return mIccPhoneBookInterfaceManager.updateContactToGroups(adnIndex, grpIdList);
    }

    public boolean moveContactFromGroupsToGroups(int adnIndex, int[] fromGrpIdList, int[] toGrpIdList) {
        return mIccPhoneBookInterfaceManager.moveContactFromGroupsToGroups(adnIndex, fromGrpIdList, toGrpIdList);
    }

    public int hasExistGroup(String grpName) {
        return mIccPhoneBookInterfaceManager.hasExistGroup(grpName);
    }

    public int getUsimGrpMaxNameLen() {
        return mIccPhoneBookInterfaceManager.getUsimGrpMaxNameLen();
    }

    public int getUsimGrpMaxCount() {
        return mIccPhoneBookInterfaceManager.getUsimGrpMaxCount();
    }

    public List<AlphaTag> getUsimAasList() {
        return mIccPhoneBookInterfaceManager.getUsimAasList();
    }

    public String getUsimAasById(int index) {
        return mIccPhoneBookInterfaceManager.getUsimAasById(index);
    }

    public boolean removeUsimAasById(int index, int pbrIndex) {
        return mIccPhoneBookInterfaceManager.removeUsimAasById(index, pbrIndex);
    }

    public int insertUsimAas(String aasName) {
        return mIccPhoneBookInterfaceManager.insertUsimAas(aasName);
    }

    public boolean updateUsimAas(int index, int pbrIndex, String aasName) {
        return mIccPhoneBookInterfaceManager.updateUsimAas(index, pbrIndex, aasName);
    }

    public boolean updateAdnAas(int adnIndex, int aasIndex) {
        return mIccPhoneBookInterfaceManager.updateAdnAas(adnIndex, aasIndex);
    }

    public int getAnrCount() {
        return mIccPhoneBookInterfaceManager.getAnrCount();
    }

    public int getEmailCount() {
        return mIccPhoneBookInterfaceManager.getEmailCount();
    }

    public int getUsimAasMaxCount() {
        return mIccPhoneBookInterfaceManager.getUsimAasMaxCount();
    }

    public int getUsimAasMaxNameLen() {
        return mIccPhoneBookInterfaceManager.getUsimAasMaxNameLen();
    }

    public boolean hasSne() {
        return mIccPhoneBookInterfaceManager.hasSne();
    }

    public int getSneRecordLen() {
        return mIccPhoneBookInterfaceManager.getSneRecordLen();
    }

    public boolean isAdnAccessible() {
        return mIccPhoneBookInterfaceManager.isAdnAccessible();
    }

    /**
     * M for LGE APIs request--get phonebook mem storage ext like record
     * (length, total, used, type)
     *
     * @return UsimPBMemInfo list
     */
    public UsimPBMemInfo[] getPhonebookMemStorageExt() {
        return mIccPhoneBookInterfaceManager.getPhonebookMemStorageExt();
    }
    // MTK-END [mtk80601][111215][ALPS00093395]

    public boolean isUICCCard() {
        return mIccPhoneBookInterfaceManager.isUICCCard();
    }
}
