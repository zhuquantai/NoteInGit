/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.Rlog;

import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Called at boot time to clean out the raw table, collecting all acknowledged messages and
 * deleting any partial message segments older than 30 days. Called from a worker thread to
 * avoid delaying phone app startup. The last step is to broadcast the first pending message
 * from the main thread, then the remaining pending messages will be broadcast after the
 * previous ordered broadcast completes.
 */
public class SmsBroadcastUndelivered implements Runnable {
    private static final String TAG = "SmsBroadcastUndelivered";
    private static final boolean DBG = InboundSmsHandler.DBG;

    /** Delete any partial message segments older than 30 days. */
    static final long PARTIAL_SEGMENT_EXPIRE_AGE = (long) (60 * 60 * 1000) * 24 * 30;

    /**
     * Query projection for dispatching pending messages at boot time.
     * Column order must match the {@code *_COLUMN} constants in {@link InboundSmsHandler}.
     */
    // MTK-START
    private static final String[] PDU_PENDING_MESSAGE_PROJECTION = {
            "pdu",
            "sequence",
            "destination_port",
            "date",
            "reference_number",
            "count",
            "address",
            "_id",
            "sub_id"
    };
    // MTK-END

    /** URI for raw table from SmsProvider. */
    private static final Uri sRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");

    /** Content resolver to use to access raw table from SmsProvider. */
    private final ContentResolver mResolver;

    /** Handler for 3GPP-format messages (may be null). */
    private final GsmInboundSmsHandler mGsmInboundSmsHandler;

    /** Handler for 3GPP2-format messages (may be null). */
    private final CdmaInboundSmsHandler mCdmaInboundSmsHandler;

    // MTK-START
    private final PhoneBase mPhone;
    // MTK-END

    // MTK-START
    // Pass phone instacne to get the sub id
    public SmsBroadcastUndelivered(Context context, GsmInboundSmsHandler gsmInboundSmsHandler,
            CdmaInboundSmsHandler cdmaInboundSmsHandler, PhoneBase phone) {
    // MTK-END
        mResolver = context.getContentResolver();
        mGsmInboundSmsHandler = gsmInboundSmsHandler;
        mCdmaInboundSmsHandler = cdmaInboundSmsHandler;
        mPhone = phone;
    }

    @Override
    public void run() {
        if (DBG) Rlog.d(TAG, "scanning raw table for undelivered messages");
        scanRawTable();
        // tell handlers to start processing new messages
        if (mGsmInboundSmsHandler != null) {
            mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_START_ACCEPTING_SMS);
        }
        if (mCdmaInboundSmsHandler != null) {
            mCdmaInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_START_ACCEPTING_SMS);
        }
    }

    /**
     * Scan the raw table for complete SMS messages to broadcast, and old PDUs to delete.
     */
    private void scanRawTable() {
        long startTime = System.nanoTime();
        HashMap<SmsReferenceKey, Integer> multiPartReceivedCount =
                new HashMap<SmsReferenceKey, Integer>(4);
        HashSet<SmsReferenceKey> oldMultiPartMessages = new HashSet<SmsReferenceKey>(4);
        Cursor cursor = null;
        try {
            cursor = mResolver.query(sRawUri, PDU_PENDING_MESSAGE_PROJECTION, null, null, null);
            if (cursor == null) {
                Rlog.e(TAG, "error getting pending message cursor");
                return;
            }

            boolean isCurrentFormat3gpp2 = InboundSmsHandler.isCurrentFormat3gpp2();
            while (cursor.moveToNext()) {
                InboundSmsTracker tracker;
                try {
                    tracker = new InboundSmsTracker(cursor, isCurrentFormat3gpp2);
                } catch (IllegalArgumentException e) {
                    Rlog.e(TAG, "error loading SmsTracker: " + e);
                    continue;
                }

                if (tracker.getMessageCount() == 1) {
                    // deliver single-part message
                    // MTK-START
                    // Check the Sub id if the same
                    if (tracker.getSubId() == mPhone.getSubId()) {
                        if (DBG) Rlog.d(TAG, "New sms on raw table, subId: " + tracker.getSubId());
                        broadcastSms(tracker);
                    }
                    // MTK-END
                } else {
                    SmsReferenceKey reference = new SmsReferenceKey(tracker);
                    Integer receivedCount = multiPartReceivedCount.get(reference);
                    if (receivedCount == null) {
                        multiPartReceivedCount.put(reference, 1);    // first segment seen
                        if (tracker.getTimestamp() <
                                (System.currentTimeMillis() - PARTIAL_SEGMENT_EXPIRE_AGE)) {
                            // older than 30 days; delete if we don't find all the segments
                            oldMultiPartMessages.add(reference);
                        }
                    } else {
                        int newCount = receivedCount + 1;
                        if (newCount == tracker.getMessageCount()) {
                            // looks like we've got all the pieces; send a single tracker
                            // to state machine which will find the other pieces to broadcast
                            if (DBG) Rlog.d(TAG, "found complete multi-part message");
                            // MTK-START
                            // Check the Sub id if the same
                            if (tracker.getSubId() == mPhone.getSubId()) {
                                if (DBG) Rlog.d(TAG, "New sms on raw table, subId: " +
                                        tracker.getSubId());
                                broadcastSms(tracker);
                            }
                            // MTK-END
                            // don't delete this old message until after we broadcast it
                            oldMultiPartMessages.remove(reference);
                        } else {
                            multiPartReceivedCount.put(reference, newCount);
                        }
                    }
                }
            }
            // Delete old incomplete message segments
            for (SmsReferenceKey message : oldMultiPartMessages) {
                int rows = mResolver.delete(sRawUri, InboundSmsHandler.SELECT_BY_REFERENCE,
                        message.getDeleteWhereArgs());
                if (rows == 0) {
                    Rlog.e(TAG, "No rows were deleted from raw table!");
                } else if (DBG) {
                    Rlog.d(TAG, "Deleted " + rows + " rows from raw table for incomplete "
                            + message.mMessageCount + " part message");
                }
            }
        } catch (SQLException e) {
            Rlog.e(TAG, "error reading pending SMS messages", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (DBG) Rlog.d(TAG, "finished scanning raw table in "
                    + ((System.nanoTime() - startTime) / 1000000) + " ms");
        }
    }

    /**
     * Send tracker to appropriate (3GPP or 3GPP2) inbound SMS handler for broadcast.
     */
    private void broadcastSms(InboundSmsTracker tracker) {
        InboundSmsHandler handler;
        if (tracker.is3gpp2()) {
            handler = mCdmaInboundSmsHandler;
        } else {
            handler = mGsmInboundSmsHandler;
        }
        if (handler != null) {
            handler.sendMessage(InboundSmsHandler.EVENT_BROADCAST_SMS, tracker);
        } else {
            Rlog.e(TAG, "null handler for " + tracker.getFormat() + " format, can't deliver.");
        }
    }

    /**
     * Used as the HashMap key for matching concatenated message segments.
     */
    private static class SmsReferenceKey {
        final String mAddress;
        final int mReferenceNumber;
        final int mMessageCount;
        // MTK-START
        final long mSubId;
        // MTK-END

        SmsReferenceKey(InboundSmsTracker tracker) {
            mAddress = tracker.getAddress();
            mReferenceNumber = tracker.getReferenceNumber();
            mMessageCount = tracker.getMessageCount();
            // MTK-START
            mSubId = tracker.getSubId();
            // MTK-END
        }

        String[] getDeleteWhereArgs() {
            // MTK-START
            return new String[]{mAddress, Integer.toString(mReferenceNumber),
                    Integer.toString(mMessageCount), Long.toString(mSubId)};
            // MTK-END
        }

        @Override
        public int hashCode() {
            // MTK-START
            return ((((int) mSubId * 63) + mReferenceNumber * 31) + mMessageCount) * 31 +
                    mAddress.hashCode();
            // MTK-END
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof SmsReferenceKey) {
                SmsReferenceKey other = (SmsReferenceKey) o;
                // MTK-START
                return other.mAddress.equals(mAddress)
                        && (other.mReferenceNumber == mReferenceNumber)
                        && (other.mMessageCount == mMessageCount)
                        && (other.mSubId == mSubId);
                // MTK-END
            }
            return false;
        }
    }
}
