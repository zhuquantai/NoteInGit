/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.Environment;
// MTK-START
import android.os.SystemProperties;
// MTK-END
import android.telephony.Rlog;
import android.util.Xml;

import com.android.internal.util.XmlUtils;

// MTK-START
import java.util.ArrayList;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneConstants;
import android.telephony.SubscriptionManager;
import android.content.Context;



import java.io.BufferedReader;


import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
// MTK-END

public class SpnOverride {
    private HashMap<String, String> mCarrierSpnMap;


    static final String LOG_TAG = "SpnOverride";
    static final String PARTNER_SPN_OVERRIDE_PATH ="etc/spn-conf.xml";
    static final String OEM_SPN_OVERRIDE_PATH = "telephony/spn-conf.xml";

    // MTK-START
    private static SpnOverride sInstance;
    static final Object sInstSync = new Object();

    // MVNO-API START
    // EF_SPN
    private static HashMap<String, String> CarrierVirtualSpnMapByEfSpn;
    private static final String PARTNER_VIRTUAL_SPN_BY_EF_SPN_OVERRIDE_PATH = "etc/virtual-spn-conf-by-efspn.xml";

    // IMSI
    private ArrayList CarrierVirtualSpnMapByImsi;
    private static final String PARTNER_VIRTUAL_SPN_BY_IMSI_OVERRIDE_PATH = "etc/virtual-spn-conf-by-imsi.xml";

    // EF_PNN
    private static HashMap<String, String> CarrierVirtualSpnMapByEfPnn;
    private static final String PARTNER_VIRTUAL_SPN_BY_EF_PNN_OVERRIDE_PATH = "etc/virtual-spn-conf-by-efpnn.xml";

    // EF_GID1
    private static HashMap<String, String> CarrierVirtualSpnMapByEfGid1;
    private static final String PARTNER_VIRTUAL_SPN_BY_EF_GID1_OVERRIDE_PATH = "etc/virtual-spn-conf-by-efgid1.xml";

    public class VirtualSpnByImsi {
        public String pattern;
        public String name;
        public VirtualSpnByImsi(String pattern, String name) {
            this.pattern = pattern;
            this.name = name;
        }
    }
    // MVNO-API END

    public static SpnOverride getInstance() {
        synchronized (sInstSync) {
            if (sInstance == null) {
                sInstance = new SpnOverride();
            }
        }
        return sInstance;
    }
    // MTK-END

    SpnOverride () {
        mCarrierSpnMap = new HashMap<String, String>();
        loadSpnOverrides();

        // MTK-START
        // MVNO-API
        // EF_SPN
        CarrierVirtualSpnMapByEfSpn = new HashMap<String, String>();
        loadVirtualSpnOverridesByEfSpn();

        // IMSI
        this.CarrierVirtualSpnMapByImsi = new ArrayList();
        this.loadVirtualSpnOverridesByImsi();

        // EF_PNN
        CarrierVirtualSpnMapByEfPnn = new HashMap<String, String>();
        loadVirtualSpnOverridesByEfPnn();

        // EF_GID1
        CarrierVirtualSpnMapByEfGid1 = new HashMap<String, String>();
        loadVirtualSpnOverridesByEfGid1();
        // MTK-END
    }
    //begin wangkai 20151021 add for task730500
    final String persoPlmnFile = "/system/custpack/plmn-list.conf";
	private static ArrayList<PersoPlmn> persoPlmnList = new ArrayList<PersoPlmn>();
	private class PersoPlmn {
		String numeric;
		String shortName;
		String fullName;

		public String toString() {
			return "numeric:" + numeric
				+ " short:" +shortName
				+ " full:" + fullName;
		}
	}
    //end wangkai 20151021 add for task730500
    boolean containsCarrier(String carrier) {
        return mCarrierSpnMap.containsKey(carrier);
    }

    String getSpn(String carrier) {
        return mCarrierSpnMap.get(carrier);
    }

    private void loadSpnOverrides() {
        FileReader spnReader;
        // MTK-START
        Rlog.d(LOG_TAG, "loadSpnOverrides");
        File spnFile = null;
        if ("OP09".equals(SystemProperties.get("ro.operator.optr", ""))
                || CdmaFeatureOptionUtils.isCT6MSupport()) {
            spnFile = new File(Environment.getRootDirectory(),
                    "etc/spn-conf-op09.xml");
            if (!spnFile.exists()) {
                Rlog.d(LOG_TAG, "No spn-conf-op09.xml file");
                spnFile = new File(Environment.getRootDirectory(),
                        PARTNER_SPN_OVERRIDE_PATH);
            }
        } else {
        // MTK-END
            spnFile = new File(Environment.getRootDirectory(),
                    PARTNER_SPN_OVERRIDE_PATH);
        // MTK-START
        }
        // MTK-END
        File oemSpnFile = new File(Environment.getOemDirectory(),
                OEM_SPN_OVERRIDE_PATH);

        if (oemSpnFile.exists()) {
            // OEM image exist SPN xml, get the timestamp from OEM & System image for comparison.
            long oemSpnTime = oemSpnFile.lastModified();
            long sysSpnTime = spnFile.lastModified();
            Rlog.d(LOG_TAG, "SPN Timestamp: oemTime = " + oemSpnTime + " sysTime = " + sysSpnTime);

            // To get the newer version of SPN from OEM image
            if (oemSpnTime > sysSpnTime) {
                Rlog.d(LOG_TAG, "SPN in OEM image is newer than System image");
                spnFile = oemSpnFile;
            }
        } else {
            // No SPN in OEM image, so load it from system image.
            Rlog.d(LOG_TAG, "No SPN in OEM image = " + oemSpnFile.getPath() +
                " Load SPN from system image");
        }

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can not open " + spnFile.getAbsolutePath());
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "spnOverrides");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"spnOverride".equals(name)) {
                    break;
                }

                String numeric = parser.getAttributeValue(null, "numeric");
                String data    = parser.getAttributeValue(null, "spn");

                mCarrierSpnMap.put(numeric, data);
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e);
        }
    }

    // MTK-START
    // MVNO-API START
    private static void loadVirtualSpnOverridesByEfSpn() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadVirtualSpnOverridesByEfSpn");
        final File spnFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_SPN_BY_EF_SPN_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_SPN_BY_EF_SPN_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "virtualSpnOverridesByEfSpn");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"virtualSpnOverride".equals(name)) {
                    break;
                }

                String mccmncspn = parser.getAttributeValue(null, "mccmncspn");
                String spn = parser.getAttributeValue(null, "name");
                Rlog.w(LOG_TAG, "test mccmncspn = " + mccmncspn + ", name = " + spn);
                CarrierVirtualSpnMapByEfSpn.put(mccmncspn, spn);
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efspn parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efspn parser " + e);
        }
    }

    public String getSpnByEfSpn(String mccmnc, String spn) {
        if (mccmnc == null || spn == null || mccmnc.isEmpty() || spn.isEmpty())
            return null;

        return CarrierVirtualSpnMapByEfSpn.get(mccmnc + spn);
    }

    private void loadVirtualSpnOverridesByImsi() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadVirtualSpnOverridesByImsi");
        final File spnFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_SPN_BY_IMSI_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_SPN_BY_IMSI_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "virtualSpnOverridesByImsi");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"virtualSpnOverride".equals(name)) {
                    break;
                }

                String imsipattern = parser.getAttributeValue(null, "imsipattern");
                String spn = parser.getAttributeValue(null, "name");
                Rlog.w(LOG_TAG, "test imsipattern = " + imsipattern + ", name = " + spn);
                this.CarrierVirtualSpnMapByImsi.add(new VirtualSpnByImsi(imsipattern, spn));
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-imsi parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-imsi parser " + e);
        }
    }

    public String getSpnByImsi(String mccmnc, String imsi) {
        if (mccmnc == null || imsi == null || mccmnc.isEmpty() || imsi.isEmpty())
            return null;

        VirtualSpnByImsi vsbi;
        for (int i = 0; i < this.CarrierVirtualSpnMapByImsi.size(); i++) {
            vsbi = (VirtualSpnByImsi) (this.CarrierVirtualSpnMapByImsi.get(i));
            Rlog.w(LOG_TAG, "getSpnByImsi(): mccmnc = " + mccmnc + ", imsi = " +
                    imsi + ", pattern = " + vsbi.pattern);

            if (imsiMatches(vsbi.pattern, mccmnc + imsi) == true) {
                return vsbi.name;
            }
        }
        return null;
    }

    public String isOperatorMvnoForImsi(String mccmnc, String imsi) {
        if (mccmnc == null || imsi == null || mccmnc.isEmpty() || imsi.isEmpty())
            return null;

        VirtualSpnByImsi vsbi;
        String pattern;
        for (int i = 0; i < this.CarrierVirtualSpnMapByImsi.size(); i++) {
            vsbi = (VirtualSpnByImsi) (this.CarrierVirtualSpnMapByImsi.get(i));
            Rlog.w(LOG_TAG, "isOperatorMvnoForImsi(): mccmnc = " + mccmnc +
                    ", imsi = " + imsi + ", pattern = " + vsbi.pattern);

            if (imsiMatches(vsbi.pattern, mccmnc + imsi) == true) {
                return vsbi.pattern;
            }
        }
        return null;
    }

   private boolean imsiMatches(String imsiDB, String imsiSIM) {
        // Note: imsiDB value has digit number or 'x' character for seperating USIM information
        // for MVNO operator. And then digit number is matched at same order and 'x' character
        // could replace by any digit number.
        // ex) if imsiDB inserted '310260x10xxxxxx' for GG Operator,
        //     that means first 6 digits, 8th and 9th digit
        //     should be set in USIM for GG Operator.
        int len = imsiDB.length();
        int idxCompare = 0;

        Rlog.w(LOG_TAG, "mvno match imsi = " + imsiSIM + "pattern = " + imsiDB);
        if (len <= 0) return false;
        if (len > imsiSIM.length()) return false;

        for (int idx = 0; idx < len; idx++) {
            char c = imsiDB.charAt(idx);
            if ((c == 'x') || (c == 'X') || (c == imsiSIM.charAt(idx))) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }
    private static void loadVirtualSpnOverridesByEfPnn() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadVirtualSpnOverridesByEfPnn");
        final File spnFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_SPN_BY_EF_PNN_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_SPN_BY_EF_PNN_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "virtualSpnOverridesByEfPnn");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"virtualSpnOverride".equals(name)) {
                    break;
                }

                String mccmncpnn = parser.getAttributeValue(null, "mccmncpnn");
                String spn = parser.getAttributeValue(null, "name");
                Rlog.w(LOG_TAG, "test mccmncpnn = " + mccmncpnn + ", name = " + spn);
                CarrierVirtualSpnMapByEfPnn.put(mccmncpnn, spn);
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efpnn parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efpnn parser " + e);
        }
        addPersoMVNOPNNList();//by wangkai 20151026 add for task742193
    }
    //begin wangkai 20151026 add for task742193
    private static void addPersoMVNOPNNList() {
        String mvnoOperatorsPnnLists = SystemProperties.get("def.mvno.operators.pnn.list");
        if (mvnoOperatorsPnnLists == null || mvnoOperatorsPnnLists.isEmpty()) {
            return;
        }
        String[] PnnLists = mvnoOperatorsPnnLists.split(";");
        for (String operator : PnnLists) {
            if (operator != null && !operator.isEmpty()) {
                String[] pnn_pairs = operator.split(",");
                if (pnn_pairs.length != 2) {
                    continue;
                }
                String mccmncpnn = pnn_pairs[0];
                String name = pnn_pairs[1];

                Rlog.w(LOG_TAG, "addPersoMVNOPNNList mccmncpnn = " + mccmncpnn);
                Rlog.w(LOG_TAG, "addPersoMVNOPNNList name = " + name);

                CarrierVirtualSpnMapByEfPnn.put(mccmncpnn, name);
            } else {
                Rlog.w(LOG_TAG, "addPersoMVNOPNNList has operator is null");
            }
        }
    }
    //end wangkai 20151026 add for task742193
    public String getSpnByEfPnn(String mccmnc, String pnn) {
        if (mccmnc == null || pnn == null || mccmnc.isEmpty() || pnn.isEmpty())
            return null;

        return CarrierVirtualSpnMapByEfPnn.get(mccmnc + pnn);
    }

    private static void loadVirtualSpnOverridesByEfGid1() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadVirtualSpnOverridesByEfGid1");
        final File spnFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_SPN_BY_EF_GID1_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_SPN_BY_EF_GID1_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "virtualSpnOverridesByEfGid1");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"virtualSpnOverride".equals(name)) {
                    break;
                }

                String mccmncgid1 = parser.getAttributeValue(null, "mccmncgid1");
                String spn = parser.getAttributeValue(null, "name");
                Rlog.w(LOG_TAG, "test mccmncgid1 = " + mccmncgid1 + ", name = " + spn);
                CarrierVirtualSpnMapByEfGid1.put(mccmncgid1, spn);
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efgid1 parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efgid1 parser " + e);
        }
    }

    public String getSpnByEfGid1(String mccmnc, String gid1) {
        if (mccmnc == null || gid1 == null || mccmnc.isEmpty() || gid1.isEmpty())
            return null;

        return CarrierVirtualSpnMapByEfGid1.get(mccmnc + gid1);
    }

    public String lookupOperatorName(int subId, String numeric, boolean desireLongName, Context context) {
        String operName = numeric;

        boolean needPersoPlmn = false;//by wangkai 20151021 add for task730500

        Phone phone = null;
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (subId == SubscriptionManager.LTE_DC_SUB_ID_1) {
                phone = SvlteUtils.getSvltePhoneProxy(PhoneConstants.SIM_ID_1)
                    .getLtePhone();
            } else if (subId == SubscriptionManager.LTE_DC_SUB_ID_2) {
                phone = SvlteUtils.getSvltePhoneProxy(PhoneConstants.SIM_ID_2)
                    .getLtePhone();
            } else {
                phone = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
            }
        } else {
            phone = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
        }

        // MVNO-API
        String mvnoOperName = null;

        if (phone == null) {
            Rlog.w(LOG_TAG, "lookupOperatorName getPhone null");
            return operName;
        }

        mvnoOperName = getSpnByEfSpn(numeric,
                phone.getMvnoPattern(PhoneConstants.MVNO_TYPE_SPN));
        Rlog.w(LOG_TAG, "the result of searching mvnoOperName by EF_SPN: " + mvnoOperName);

        if (mvnoOperName == null) // determine by IMSI
            mvnoOperName = getSpnByImsi(numeric, phone.getSubscriberId());
        Rlog.w(LOG_TAG, "the result of searching mvnoOperName by IMSI: " + mvnoOperName);

        if (mvnoOperName == null)
            mvnoOperName = getSpnByEfPnn(numeric,
                    phone.getMvnoPattern(PhoneConstants.MVNO_TYPE_PNN));
        Rlog.w(LOG_TAG, "the result of searching mvnoOperName by EF_PNN: " + mvnoOperName);

        if (mvnoOperName == null)
            mvnoOperName = getSpnByEfGid1(numeric,
                    phone.getMvnoPattern(PhoneConstants.MVNO_TYPE_GID));
        Rlog.w(LOG_TAG, "the result of searching mvnoOperName by EF_GID1: " + mvnoOperName);

        if (mvnoOperName != null)
            operName = mvnoOperName;

        boolean getFromResource = false;
        String ctName = context.getText(com.mediatek.internal.R.string.ct_name).toString();
        Rlog.d(LOG_TAG, "ctName:" + ctName);
        if (ctName != null && ctName.equals(mvnoOperName)) {
            Rlog.d(LOG_TAG, "Get from resource.");
            getFromResource = true;
            mvnoOperName = null;
        }

        if (mvnoOperName == null && desireLongName) { // MVNO-API
            // ALFMS00040828 - add "46008"
            if ((numeric.equals("46000")) || (numeric.equals("46002")) || (numeric.equals("46007")) || (numeric.equals("46008"))) {
                operName = context.getText(com.mediatek.R.string.oper_long_46000).toString();
            } else if ((numeric.equals("46001")) || (numeric.equals("46009"))) {
                operName = context.getText(com.mediatek.R.string.oper_long_46001).toString();
            } else if ((numeric.equals("46003")) || (numeric.equals("46011")) || getFromResource) {
                operName = context.getText(com.mediatek.R.string.oper_long_46003).toString();
            } else if (numeric.equals("46601")) {
                operName = context.getText(com.mediatek.R.string.oper_long_46601).toString();
            } else if (numeric.equals("46692")) {
                operName = context.getText(com.mediatek.R.string.oper_long_46692).toString();
            } else if (numeric.equals("46697")) {
                operName = context.getText(com.mediatek.R.string.oper_long_46697).toString();
            } else if (numeric.equals("99998")) {
                operName = context.getText(com.mediatek.R.string.oper_long_99998).toString();
            } else if (numeric.equals("99999")) {
                operName = context.getText(com.mediatek.R.string.oper_long_99999).toString();
            } else {
                //begin wangkai 20151021 add for task730500
                // If can't found corresspoding operator in string resource, lookup from spn_conf.xml
                //if (containsCarrier(numeric)) {
                //    operName = getSpn(numeric);
                //} else {
                needPersoPlmn = true;
                Rlog.w(LOG_TAG, "Can't find long operator name for " + numeric);
                //}
                //end wangkai 20151021 add for task730500
            }
        }
        else if (mvnoOperName == null && desireLongName == false) // MVNO-API
        {
            // ALFMS00040828 - add "46008"
            if ((numeric.equals("46000")) || (numeric.equals("46002")) || (numeric.equals("46007")) || (numeric.equals("46008"))) {
                operName = context.getText(com.mediatek.R.string.oper_short_46000).toString();
            } else if ((numeric.equals("46001")) || (numeric.equals("46009"))) {
                operName = context.getText(com.mediatek.R.string.oper_short_46001).toString();
            } else if ((numeric.equals("46003")) || (numeric.equals("46011")) || getFromResource) {
                operName = context.getText(com.mediatek.R.string.oper_short_46003).toString();
            } else if (numeric.equals("46601")) {
                operName = context.getText(com.mediatek.R.string.oper_short_46601).toString();
            } else if (numeric.equals("46692")) {
                operName = context.getText(com.mediatek.R.string.oper_short_46692).toString();
            } else if (numeric.equals("46697")) {
                operName = context.getText(com.mediatek.R.string.oper_short_46697).toString();
            } else if (numeric.equals("99997")) {
                operName = context.getText(com.mediatek.R.string.oper_short_99997).toString();
            } else if (numeric.equals("99999")) {
                operName = context.getText(com.mediatek.R.string.oper_short_99999).toString();
            } else {
                //begin wangkai 20151021 add for task730500
                needPersoPlmn = true;
                //end wangkai 20151021 add for task730500
                Rlog.w(LOG_TAG, "Can't find short operator name for " + numeric);
            }
        }

        //begin wangkai 20151021 add for task730500
        if (needPersoPlmn) {
            String PLMNFromJRD[] = getPLMNNameByNumeric(numeric);
            if (null != PLMNFromJRD){
                operName = desireLongName ? PLMNFromJRD[0] : PLMNFromJRD[1];
            }
        }
        //end wangkai 20151021 add for task730500

        return operName;
    }
    //begin wangkai 20151021 add for task730500
    private String[] getPLMNNameByNumeric(String numeric) {
      if (numeric == null || persoPlmnList == null || persoPlmnList.size() == 0) {
        return null;
      }
      String longName = null;
      String shortName = null;

      for (int i = 0; i < persoPlmnList.size(); i++) {
        PersoPlmn plmn = persoPlmnList.get(i);
        if(numeric.equals(plmn.numeric)) {
          longName = plmn.fullName;
          shortName = plmn.shortName;
          break;
        }
      }
      if ((null == longName) && (null == shortName)) {
        Rlog.d(LOG_TAG, "[RIL] Don't find out PLMN from JRD table");
        return null;
      } else {
        String plmnName[] = new String[2];
        plmnName[0] = longName;
        plmnName[1] = shortName;
        Rlog.d(LOG_TAG, "[RIL] PLMN found out in JRD table, and long name : "+ longName +"short name : "+shortName);
        return plmnName;
      }
    }

    public void getPlmnFromFile() throws Exception {
      BufferedReader raf = new BufferedReader(new FileReader(persoPlmnFile));
      try {
        String line;
        while ((line = raf.readLine()) != null) {
          parseLine(line);
        }
      }
      finally {
        if(raf != null) {
          raf.close();
        }
      }
    }

     /* parse line to struct, a example of line:
      *   { 460,   0x00F, "CMCC", "CHINA MOBILE" },
      * There could be comma in full name but can't be
      * in short name, or it will leads to parse error.
      */
    private void parseLine(String line) throws Exception {
	    if(line == null || line.matches("\\s*[{].*[}].*") == false) {
	        Rlog.d(LOG_TAG, "matches fail:" + line);
	        return;
	    }
	    line = line.substring(line.indexOf("{") + 1, line.lastIndexOf("}"));
	    String[] argu = line.split(",", 4);
	    if(argu.length != 4) {
	        Rlog.d(LOG_TAG,"wrong argu numbers in line " + argu.length);
	        return;
	    }

	    argu[0] = argu[0].trim();
	    argu[1] = argu[1].trim();
	    if(argu[1].startsWith("0x") || argu[1].startsWith("0X")) {
	        argu[1] = argu[1].substring(2);
	    }
	    if(argu[1].endsWith("F")) {
	        argu[1] = argu[1].substring(0, argu[1].length() - 1);
	    }
	    argu[2] = argu[2].trim();
	    argu[3] = argu[3].trim();
	    PersoPlmn plmn = new PersoPlmn();
	    if (plmn != null && persoPlmnList != null) {
	        plmn.numeric = argu[0] + argu[1];
	        plmn.shortName = parseQuotes(argu[2]);
	        plmn.fullName = parseQuotes(argu[3]);
	        persoPlmnList.add(plmn);
	        Rlog.d(LOG_TAG,"PersoPlmn " + plmn);
	    }
    }

    private String parseQuotes(String str) throws Exception {
      if(str != null && str.startsWith("\"") && str.endsWith("\""))
        return str.substring(str.indexOf("\"") + 1, str.lastIndexOf("\""));
      else
        return str;
    }
    //end wangkai 20151021 add for task730500
    public String lookupOperatorNameForDisplayName(int subId,
            String numeric, boolean desireLongName, Context context) {
        String operName = null;
        Phone phone = null;
        boolean needPersoPlmn = false;//by wangkai 20151027 add for task742220
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (subId == SubscriptionManager.LTE_DC_SUB_ID_1) {
                phone = SvlteUtils.getSvltePhoneProxy(PhoneConstants.SIM_ID_1)
                    .getLtePhone();
            } else if (subId == SubscriptionManager.LTE_DC_SUB_ID_2) {
                phone = SvlteUtils.getSvltePhoneProxy(PhoneConstants.SIM_ID_2)
                    .getLtePhone();
            } else {
                phone = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
            }
        } else {
            phone = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
        }

        // MVNO-API
        String mvnoOperName = null;

        if (phone == null) {
            Rlog.w(LOG_TAG, "lookupOperatorName getPhone null");
            return operName;
        }

        mvnoOperName = getSpnByEfSpn(numeric,
                phone.getMvnoPattern(PhoneConstants.MVNO_TYPE_SPN));
        Rlog.w(LOG_TAG, "the result of searching mvnoOperName by EF_SPN: " + mvnoOperName);

        if (mvnoOperName == null) // determine by IMSI
            mvnoOperName = getSpnByImsi(numeric, phone.getSubscriberId());
        Rlog.w(LOG_TAG, "the result of searching mvnoOperName by IMSI: " + mvnoOperName);

        if (mvnoOperName == null)
            mvnoOperName = getSpnByEfPnn(numeric,
                    phone.getMvnoPattern(PhoneConstants.MVNO_TYPE_PNN));
        Rlog.w(LOG_TAG, "the result of searching mvnoOperName by EF_PNN: " + mvnoOperName);

        if (mvnoOperName == null)
            mvnoOperName = getSpnByEfGid1(numeric,
                    phone.getMvnoPattern(PhoneConstants.MVNO_TYPE_GID));
        Rlog.w(LOG_TAG, "the result of searching mvnoOperName by EF_GID1: " + mvnoOperName);

        if (mvnoOperName != null)
            operName = mvnoOperName;

        boolean getFromResource = false;
        String ctName = context.getText(com.mediatek.internal.R.string.ct_name).toString();
        Rlog.d(LOG_TAG, "ctName:" + ctName);
        if (ctName != null && ctName.equals(mvnoOperName)) {
            Rlog.d(LOG_TAG, "Get from resource.");
            getFromResource = true;
            mvnoOperName = null;
        }

        if (mvnoOperName == null && desireLongName) { // MVNO-API
            // ALFMS00040828 - add "46008"
            if ((numeric.equals("46000")) || (numeric.equals("46002")) || (numeric.equals("46007")) || (numeric.equals("46008"))) {
                operName = context.getText(com.mediatek.R.string.oper_long_46000).toString();
            } else if ((numeric.equals("46001")) || (numeric.equals("46009"))) {
                operName = context.getText(com.mediatek.R.string.oper_long_46001).toString();
            } else if ((numeric.equals("46003")) || (numeric.equals("46011")) || getFromResource) {
                operName = context.getText(com.mediatek.R.string.oper_long_46003).toString();
            } else if (numeric.equals("46601")) {
                operName = context.getText(com.mediatek.R.string.oper_long_46601).toString();
            } else if (numeric.equals("46692")) {
                operName = context.getText(com.mediatek.R.string.oper_long_46692).toString();
            } else if (numeric.equals("46697")) {
                operName = context.getText(com.mediatek.R.string.oper_long_46697).toString();
            } else if (numeric.equals("99998")) {
                operName = context.getText(com.mediatek.R.string.oper_long_99998).toString();
            } else if (numeric.equals("99999")) {
                operName = context.getText(com.mediatek.R.string.oper_long_99999).toString();
            } else {
                //begin wangkai 20151027 modify for task742220
                // If can't found corresspoding operator in string resource, lookup from spn_conf.xml
                //if (containsCarrier(numeric)) {
                //    operName = getSpn(numeric);
                //} else {
                needPersoPlmn = true;
                Rlog.w(LOG_TAG, "Can't find long operator name for " + numeric);
                //}
                //end wangkai 20151027 modify for task742220
            }
        }
        else if (mvnoOperName == null && desireLongName == false) // MVNO-API
        {
            // ALFMS00040828 - add "46008"
            if ((numeric.equals("46000")) || (numeric.equals("46002")) || (numeric.equals("46007")) || (numeric.equals("46008"))) {
                operName = context.getText(com.mediatek.R.string.oper_short_46000).toString();
            } else if ((numeric.equals("46001")) || (numeric.equals("46009"))) {
                operName = context.getText(com.mediatek.R.string.oper_short_46001).toString();
            } else if ((numeric.equals("46003")) || (numeric.equals("46011")) || getFromResource) {
                operName = context.getText(com.mediatek.R.string.oper_short_46003).toString();
            } else if (numeric.equals("46601")) {
                operName = context.getText(com.mediatek.R.string.oper_short_46601).toString();
            } else if (numeric.equals("46692")) {
                operName = context.getText(com.mediatek.R.string.oper_short_46692).toString();
            } else if (numeric.equals("46697")) {
                operName = context.getText(com.mediatek.R.string.oper_short_46697).toString();
            } else if (numeric.equals("99997")) {
                operName = context.getText(com.mediatek.R.string.oper_short_99997).toString();
            } else if (numeric.equals("99999")) {
                operName = context.getText(com.mediatek.R.string.oper_short_99999).toString();
            } else {
                needPersoPlmn = true;//by wangkai 20151027 add for task742220
                Rlog.w(LOG_TAG, "Can't find short operator name for " + numeric);
            }
        }
        //begin wangkai 20151027 add for task742220
        if (needPersoPlmn) {
            String PLMNFromJRD[] = getPLMNNameByNumeric(numeric);
            if (null != PLMNFromJRD){
                operName = desireLongName ? PLMNFromJRD[0] : PLMNFromJRD[1];
            }
        }
        //end wangkai 20151027 add for task742220
        return operName;
    }
    // MVNO-API END

    public boolean containsCarrierEx(String carrier) {
        return containsCarrier(carrier);
    }

    public String getSpnEx(String carrier) {
        return getSpn(carrier);
    }
    // MTK-END
}
