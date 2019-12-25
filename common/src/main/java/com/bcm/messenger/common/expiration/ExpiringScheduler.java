package com.bcm.messenger.common.expiration;

import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.database.records.MessageRecord;
import com.bcm.messenger.common.database.repositories.PrivateChatRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.provider.AMESelfData;
import com.bcm.messenger.common.utils.AppUtil;
import com.bcm.messenger.utility.logger.ALog;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ExpiringScheduler implements IExpiringScheduler {
  private static final String TAG = "ExpiringScheduler";

  private String uid;

  private final TreeSet<ExpiringMessageReference> expiringMessageReferences = new TreeSet<>(new ExpiringMessageComparator());
  private final Executor                          executor                  = Executors.newSingleThreadExecutor();

  private final Context     context;
  private final PrivateChatRepo chatRepo;

  public ExpiringScheduler(Context context, String uid) {
    this.uid = uid;
    this.context     = context.getApplicationContext();
    this.chatRepo = Repository.getChatRepo();

    executor.execute(new LoadTask());
    executor.execute(new ProcessTask());
  }

  @Override
  public String getUid() {
    return this.uid;
  }

  @Override
  public void scheduleDeletion(long id, boolean mms, long expiresInMillis) {
    ALog.d(TAG, "scheduleDeletion id:" + id + " time:" + expiresInMillis);
    scheduleDeletion(id, mms, System.currentTimeMillis(), expiresInMillis);
  }

  @Override
  public void scheduleDeletion(long id, boolean mms, long startedAtTimestamp, long expiresInMillis) {
    ALog.d(TAG, "scheduleDeletion id:" + id + " time:" + expiresInMillis + " start:" + startedAtTimestamp);
    long expiresAtMillis = startedAtTimestamp + expiresInMillis;

    synchronized (expiringMessageReferences) {
      expiringMessageReferences.add(new ExpiringMessageReference(id, mms, expiresAtMillis));
      expiringMessageReferences.notifyAll();
    }
  }

  @Override
  public void checkSchedule() {
    synchronized (expiringMessageReferences) {
      expiringMessageReferences.notifyAll();
    }
  }

  private class LoadTask implements Runnable {
    public void run() {
      List<MessageRecord> messageRecords = chatRepo.getExpirationStartedMessages();
      for (MessageRecord record : messageRecords) {
        expiringMessageReferences.add(new ExpiringMessageReference(record.getId(), record.isMediaMessage(), record.getExpiresStartTime() + record.getExpiresTime()));
      }
    }
  }

  private class ProcessTask implements Runnable {
    public void run() {
      while (true) {
        ExpiringMessageReference expiredMessage = null;
        if (!AMESelfData.INSTANCE.getUid().equals(uid)) {
          ALog.w(TAG, "login state changed");
          break;
        }

        synchronized (expiringMessageReferences) {
          try {
            while (expiringMessageReferences.isEmpty()) expiringMessageReferences.wait();

            ExpiringMessageReference nextReference = expiringMessageReferences.first();
            long                     waitTime      = nextReference.expiresAtMillis - System.currentTimeMillis();

            if (waitTime > 0) {
              ExpirationListener.setAlarm(context, waitTime);
              expiringMessageReferences.wait(waitTime);
            } else {
              expiredMessage = nextReference;
              expiringMessageReferences.remove(nextReference);
            }

          } catch (InterruptedException e) {
            Log.w(TAG, e);
          }
        }

        if (expiredMessage != null) {
          chatRepo.deleteMessage(expiredMessage.id);
          if (!AppUtil.INSTANCE.isReleaseBuild()) {
            ALog.d(TAG, "destroy message id:" + expiredMessage.id);
          }
        }
      }
    }
  }

  private static class ExpiringMessageReference {
    private final long    id;
    private final boolean mms;
    private final long    expiresAtMillis;

    private ExpiringMessageReference(long id, boolean mms, long expiresAtMillis) {
      this.id = id;
      this.mms = mms;
      this.expiresAtMillis = expiresAtMillis;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) return false;
      if (!(other instanceof ExpiringMessageReference)) return false;

      ExpiringMessageReference that = (ExpiringMessageReference)other;
      return this.id == that.id && this.mms == that.mms && this.expiresAtMillis == that.expiresAtMillis;
    }

    @Override
    public int hashCode() {
      return (int)this.id ^ (mms ? 1 : 0) ^ (int)expiresAtMillis;
    }
  }

  private static class ExpiringMessageComparator implements Comparator<ExpiringMessageReference> {
    @Override
    public int compare(ExpiringMessageReference lhs, ExpiringMessageReference rhs) {
      if      (lhs.expiresAtMillis < rhs.expiresAtMillis) return -1;
      else if (lhs.expiresAtMillis > rhs.expiresAtMillis) return 1;
      else if (lhs.id < rhs.id)                           return -1;
      else if (lhs.id > rhs.id)                           return 1;
      else if (!lhs.mms && rhs.mms)                       return -1;
      else if (lhs.mms && !rhs.mms)                       return 1;
      else                                                return 0;
    }
  }
}
