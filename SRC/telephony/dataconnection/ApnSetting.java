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

import android.telephony.ServiceState;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.uicc.IccRecords;

import com.mediatek.common.telephony.IApnSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * This class represents a apn setting for create PDP link.
 */
public class ApnSetting implements IApnSetting {
    private static final String LOG_TAG = "DCT";
    private static final boolean DBG = true;

    static final String V2_FORMAT_REGEX = "^\\[ApnSettingV2\\]\\s*";
    static final String V3_FORMAT_REGEX = "^\\[ApnSettingV3\\]\\s*";

    public final String carrier;
    public final String apn;
    public final String proxy;
    public final String port;
    public final String mmsc;
    public final String mmsProxy;
    public final String mmsPort;
    public final String user;
    public final String password;
    public final int authType;
    public final String[] types;
    public final int id;
    public final String numeric;
    public final String protocol;
    public final String roamingProtocol;
    public final int mtu;
    /*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
    public final int sourcetype;
    /*-END-20151014-add-by-qingtao.wen-for-task745079-*/
    /**
      * Current status of APN
      * true : enabled APN, false : disabled APN.
      */
    public final boolean carrierEnabled;
    /**
     * Radio Access Technology info
     * To check what values can hold, refer to ServiceState.java.
     * This should be spread to other technologies,
     * but currently only used for LTE(14) and EHRPD(13).
     */
    public final int bearer;
    /**
      * Radio Access Technology info
      * To check what values can hold, refer to ServiceState.java. This is a bitmask of radio
      * technologies in ServiceState.
      * This should be spread to other technologies,
      * but currently only used for LTE(14) and EHRPD(13).
      */
    public final int bearerBitmask;

    /* ID of the profile in the modem */
    public final int profileId;
    public final boolean modemCognitive;
    public final int maxConns;
    public final int waitTime;
    public final int maxConnsTime;

    /**
      * MVNO match type. Possible values:
      *   "spn": Service provider name.
      *   "imsi": IMSI.
      *   "gid": Group identifier level 1.
      */
    public final String mvnoType;
    /**
      * MVNO data. Examples:
      *   "spn": A MOBILE, BEN NL
      *   "imsi": 302720x94, 2060188
      *   "gid": 4E, 33
      */
    public final String mvnoMatchData;

    public ApnSetting(int id, String numeric, String carrier, String apn,
            String proxy, String port,
            String mmsc, String mmsProxy, String mmsPort,
            String user, String password, int authType, String[] types,
            String protocol, String roamingProtocol, boolean carrierEnabled, int bearer,
            int bearerBitmask, int profileId, boolean modemCognitive, int maxConns, int waitTime,
            int maxConnsTime, int mtu, String mvnoType, String mvnoMatchData) {
        this.id = id;
        this.numeric = numeric == null ? "" : numeric;
        this.carrier = carrier == null ? "" : carrier;
        this.apn = apn == null ? "" : apn;
        this.proxy = proxy == null ? "" : proxy;
        this.port = port == null ? "" : port;
        this.mmsc = mmsc == null ? "" : mmsc;
        this.mmsProxy = mmsProxy == null ? "" : mmsProxy;
        this.mmsPort = mmsPort == null ? "" : mmsPort;
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
        this.authType = authType;
        this.types = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            this.types[i] = types[i] == null ? "" : types[i].toLowerCase(Locale.ROOT);
        }
        this.protocol = protocol == null ? "" : protocol;
        this.roamingProtocol = roamingProtocol == null ? "" : roamingProtocol;
        this.carrierEnabled = carrierEnabled;
        this.bearer = bearer;
        this.bearerBitmask = (bearerBitmask | ServiceState.getBitmaskForTech(bearer));
        this.sourcetype = 0;//20151014-add-by-qingtao.wen-for-task745079
        this.profileId = profileId;
        this.modemCognitive = modemCognitive;
        this.maxConns = maxConns;
        this.waitTime = waitTime;
        this.maxConnsTime = maxConnsTime;
        this.mtu = mtu;
        this.mvnoType = mvnoType == null ? "" : mvnoType;
        this.mvnoMatchData = mvnoMatchData == null ? "" : mvnoMatchData;

    }
  /*-BEGIN-20151014-add-by-qingtao.wen-for-task745079-*/
    public ApnSetting(int id, String numeric, String carrier, String apn,
            String proxy, String port,
            String mmsc, String mmsProxy, String mmsPort,
            String user, String password, int authType, String[] types,int sourcetype,
            String protocol, String roamingProtocol, boolean carrierEnabled, int bearer,
            int bearerBitmask, int profileId, boolean modemCognitive, int maxConns, int waitTime,
            int maxConnsTime, int mtu, String mvnoType, String mvnoMatchData) {
        this.id = id;
        this.numeric = numeric == null ? "" : numeric;
        this.carrier = carrier == null ? "" : carrier;
        this.apn = apn == null ? "" : apn;
        this.proxy = proxy == null ? "" : proxy;
        this.port = port == null ? "" : port;
        this.mmsc = mmsc == null ? "" : mmsc;
        this.mmsProxy = mmsProxy == null ? "" : mmsProxy;
        this.mmsPort = mmsPort == null ? "" : mmsPort;
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
        this.authType = authType;
        this.types = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            this.types[i] = types[i] == null ? "" : types[i].toLowerCase(Locale.ROOT);
        }
        this.protocol = protocol == null ? "" : protocol;
        this.roamingProtocol = roamingProtocol == null ? "" : roamingProtocol;
        this.carrierEnabled = carrierEnabled;
        this.bearer = bearer;
        this.bearerBitmask = (bearerBitmask | ServiceState.getBitmaskForTech(bearer));
        this.sourcetype = sourcetype;
        this.profileId = profileId;
        this.modemCognitive = modemCognitive;
        this.maxConns = maxConns;
        this.waitTime = waitTime;
        this.maxConnsTime = maxConnsTime;
        this.mtu = mtu;
        this.mvnoType = mvnoType == null ? "" : mvnoType;
        this.mvnoMatchData = mvnoMatchData == null ? "" : mvnoMatchData;

    }
   /*-END-20151014-add-by-qingtao.wen-for-task745079-*/

    /**
     * Creates an ApnSetting object from a string.
     *
     * @param data the string to read.
     *
     * The string must be in one of two formats (newlines added for clarity,
     * spaces are optional):
     *
     * v1 format:
     *   <carrier>, <apn>, <proxy>, <port>, <user>, <password>, <server>,
     *   <mmsc>, <mmsproxy>, <mmsport>, <mcc>, <mnc>, <authtype>,
     *   <type>[| <type>...],
     *
     * v2 format:
     *   [ApnSettingV2] <carrier>, <apn>, <proxy>, <port>, <user>, <password>, <server>,
     *   <mmsc>, <mmsproxy>, <mmsport>, <mcc>, <mnc>, <authtype>,
     *   <type>[| <type>...], <protocol>, <roaming_protocol>, <carrierEnabled>, <bearerBitmask>,
     *
     * v3 format:
     *   [ApnSettingV3] <carrier>, <apn>, <proxy>, <port>, <user>, <password>, <server>,
     *   <mmsc>, <mmsproxy>, <mmsport>, <mcc>, <mnc>, <authtype>,
     *   <type>[| <type>...], <protocol>, <roaming_protocol>, <carrierEnabled>, <bearerBitmask>,
     *   <profileId>, <modemCognitive>, <maxConns>, <waitTime>, <maxConnsTime>, <mtu>,
     *   <mvnoType>, <mvnoMatchData>
     *
     * Note that the strings generated by toString() do not contain the username
     * and password and thus cannot be read by this method.
     */
    public static ApnSetting fromString(String data) {
        if (data == null) return null;

        int version;
        // matches() operates on the whole string, so append .* to the regex.
        if (data.matches(V3_FORMAT_REGEX + ".*")) {
            version = 3;
            data = data.replaceFirst(V3_FORMAT_REGEX, "");
        } else if (data.matches(V2_FORMAT_REGEX + ".*")) {
            version = 2;
            data = data.replaceFirst(V2_FORMAT_REGEX, "");
        } else {
            version = 1;
        }

        String[] a = data.split("\\s*,\\s*");
        if (a.length < 14) {
            return null;
        }

        int authType;
        try {
            authType = Integer.parseInt(a[12]);
        } catch (NumberFormatException e) {
            authType = 0;
        }

        String[] typeArray;
        int sourcetype=0;//20151014-add-by-qingtao.wen-for-task745079
        String protocol;
        String roamingProtocol;
        boolean carrierEnabled;
        int bearerBitmask = 0;
        int profileId = 0;
        boolean modemCognitive = false;
        int maxConns = 0;
        int waitTime = 0;
        int maxConnsTime = 0;
        int mtu = PhoneConstants.UNSET_MTU;
        String mvnoType = "";
        String mvnoMatchData = "";
        if (version == 1) {
            typeArray = new String[a.length - 13];
            System.arraycopy(a, 13, typeArray, 0, a.length - 13);
            protocol = RILConstants.SETUP_DATA_PROTOCOL_IP;
            roamingProtocol = RILConstants.SETUP_DATA_PROTOCOL_IP;
            carrierEnabled = true;
        } else {
            if (a.length < 18) {
                return null;
            }
            typeArray = a[13].split("\\s*\\|\\s*");
            protocol = a[14];
            roamingProtocol = a[15];
            carrierEnabled = Boolean.parseBoolean(a[16]);

            bearerBitmask = ServiceState.getBitmaskFromString(a[17]);

            if (a.length > 22) {
                modemCognitive = Boolean.parseBoolean(a[19]);
                try {
                    profileId = Integer.parseInt(a[18]);
                    maxConns = Integer.parseInt(a[20]);
                    waitTime = Integer.parseInt(a[21]);
                    maxConnsTime = Integer.parseInt(a[22]);
                } catch (NumberFormatException e) {
                    Log.e(LOG_TAG, "profileId...etc parse NumberFormatException");
                }
            }
            if (a.length > 23) {
                try {
                    mtu = Integer.parseInt(a[23]);
                } catch (NumberFormatException e) {
                    Log.e(LOG_TAG, "mtu parse NumberFormatException");
                }
            }
            if (a.length > 25) {
                mvnoType = a[24];
                mvnoMatchData = a[25];
            }
        }

        return new ApnSetting(-1,a[10]+a[11],a[0],a[1],a[2],a[3],a[7],a[8],
                a[9], a[4], a[5], authType, typeArray, sourcetype, protocol, roamingProtocol, carrierEnabled,0,//20151014-add-by-qingtao.wen-for-task745079,add "sourcetype" in this line
                bearerBitmask, profileId, modemCognitive, maxConns, waitTime, maxConnsTime, mtu,
                mvnoType, mvnoMatchData);
    }

    /**
     * Creates an array of ApnSetting objects from a string.
     *
     * @param data the string to read.
     *
     * Builds on top of the same format used by fromString, but allows for multiple entries
     * separated by "; ".
     */
    public static List<ApnSetting> arrayFromString(String data) {
        List<ApnSetting> retVal = new ArrayList<ApnSetting>();
        if (TextUtils.isEmpty(data)) {
            return retVal;
        }
        String[] apnStrings = data.split("\\s*;\\s*");
        for (String apnString : apnStrings) {
            ApnSetting apn = fromString(apnString);
            if (apn != null) {
                retVal.add(apn);
            }
        }
        return retVal;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ApnSettingV3] ")
        .append(carrier)
        .append(", ").append(id)
        .append(", ").append(numeric)
        .append(", ").append(apn)
        .append(", ").append(proxy)
        .append(", ").append(mmsc)
        .append(", ").append(mmsProxy)
        .append(", ").append(mmsPort)
        .append(", ").append(port)
        .append(", ").append(authType).append(", ");
        for (int i = 0; i < types.length; i++) {
            sb.append(types[i]);
            if (i < types.length - 1) {
                sb.append(" | ");
            }
        }
        sb.append(", ").append(protocol);
        sb.append(", ").append(roamingProtocol);
        sb.append(", ").append(carrierEnabled);
        sb.append(", ").append(bearer);
        sb.append(", ").append(bearerBitmask);
        sb.append(", ").append(profileId);
        sb.append(", ").append(modemCognitive);
        sb.append(", ").append(maxConns);
        sb.append(", ").append(waitTime);
        sb.append(", ").append(maxConnsTime);
        sb.append(", ").append(mtu);
        sb.append(", ").append(mvnoType);
        sb.append(", ").append(mvnoMatchData);
        return sb.toString();
    }

    /**
     * Returns true if there are MVNO params specified.
     */
    public boolean hasMvnoParams() {
        return !TextUtils.isEmpty(mvnoType) && !TextUtils.isEmpty(mvnoMatchData);
    }

    public boolean canHandleType(String type) {
        if (!carrierEnabled) return false;
        for (String t : types) {
            Log.d(LOG_TAG, "canHandleType(): entry in types=" + t + ", reqType=" + type);
            if (type.equalsIgnoreCase(PhoneConstants.APN_TYPE_DUN)) {
                if (t.equalsIgnoreCase(PhoneConstants.APN_TYPE_TETHERING)
                    || t.equalsIgnoreCase(PhoneConstants.APN_TYPE_DUN)) {
                    if (types.length == 1) {
                        Log.d(LOG_TAG, "canHandleType(): use TETHERING for HIPRI type");
                        return true;
                    } else {
                        Log.d(LOG_TAG, "canHandleType(): not TETHERING only APN settings");
                        return false;
                    }
                }
            } else {
                if (t.equalsIgnoreCase(type) ||
                    (t.equalsIgnoreCase(PhoneConstants.APN_TYPE_ALL) &&
                      !(type.equalsIgnoreCase(PhoneConstants.APN_TYPE_IMS) ||
                       type.equalsIgnoreCase(PhoneConstants.APN_TYPE_EMERGENCY)))) {
                       // M: Let the apn *  skip the "IMS" & "EMERGENCY" apn
                    return true;
                } else if (t.equalsIgnoreCase(PhoneConstants.APN_TYPE_DEFAULT)
                            && type.equalsIgnoreCase(PhoneConstants.APN_TYPE_HIPRI)) {
                    Log.d(LOG_TAG, "canHandleType(): use DEFAULT for HIPRI type");
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean imsiMatches(String imsiDB, String imsiSIM) {
        // Note: imsiDB value has digit number or 'x' character for seperating USIM information
        // for MVNO operator. And then digit number is matched at same order and 'x' character
        // could replace by any digit number.
        // ex) if imsiDB inserted '310260x10xxxxxx' for GG Operator,
        //     that means first 6 digits, 8th and 9th digit
        //     should be set in USIM for GG Operator.
        int len = imsiDB.length();
        int idxCompare = 0;

        if (len <= 0) return false;
        if (len > imsiSIM.length()) return false;

        for (int idx=0; idx<len; idx++) {
            char c = imsiDB.charAt(idx);
            if ((c == 'x') || (c == 'X') || (c == imsiSIM.charAt(idx))) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean mvnoMatches(IccRecords r, String mvnoType, String mvnoMatchData) {
        if (mvnoType.equalsIgnoreCase("spn")) {
            if ((r.getServiceProviderName() != null) &&
                    r.getServiceProviderName().equalsIgnoreCase(mvnoMatchData)) {
                return true;
            }
        } else if (mvnoType.equalsIgnoreCase("imsi")) {
            String imsiSIM = r.getIMSI();
            if ((imsiSIM != null) && imsiMatches(mvnoMatchData, imsiSIM)) {
                return true;
            }
        } else if (mvnoType.equalsIgnoreCase("gid")) {
            String gid1 = r.getGid1();
            int mvno_match_data_length = mvnoMatchData.length();
            if ((gid1 != null) && (gid1.length() >= mvno_match_data_length) &&
                    gid1.substring(0, mvno_match_data_length).equalsIgnoreCase(mvnoMatchData)) {
                return true;
            }
        }
        return false;
    }

    // TODO - if we have this function we should also have hashCode.
    // Also should handle changes in type order and perhaps case-insensitivity
    @Override
    public boolean equals(Object o) {
        if (o instanceof ApnSetting == false) return false;
        return (toString().equals(o.toString()));
    }

    /** M: get APN string except name since we do not care about it. */
    public String toStringIgnoreName() {
        StringBuilder sb = new StringBuilder();
        sb.append(id)
        .append(", ").append(numeric)
        .append(", ").append(apn)
        .append(", ").append(proxy)
        .append(", ").append(mmsc)
        .append(", ").append(mmsProxy)
        .append(", ").append(mmsPort)
        .append(", ").append(port)
        .append(", ").append(authType).append(", ");
        for (int i = 0; i < types.length; i++) {
            sb.append(types[i]);
            if (i < types.length - 1) {
                sb.append(" | ");
            }
        }
        sb.append(", ").append(protocol);
        sb.append(", ").append(roamingProtocol);
        sb.append(", ").append(carrierEnabled);
        sb.append(", ").append(bearer);
        return sb.toString();
    }

    static public String toStringIgnoreNameForList(List<ApnSetting> apnSettings) {
        if (apnSettings == null || apnSettings.size() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ApnSetting t : apnSettings) {
            sb.append(t.toStringIgnoreName());
        }
        return sb.toString();
    }
}
