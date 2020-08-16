package com.paulmandal.atak.forwarder.comm.commhardware;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.atakmap.coremap.cot.event.CotEvent;
import com.paulmandal.atak.forwarder.Config;
import com.paulmandal.atak.forwarder.comm.MessageQueue;
import com.paulmandal.atak.forwarder.group.GroupTracker;
import com.paulmandal.atak.forwarder.group.UserInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class CommHardware {
    private static final String TAG = "ATAKDBG." + CommHardware.class.getSimpleName();

    private static final int DELAY_BETWEEN_POLLING_FOR_MESSAGES = Config.DELAY_BETWEEN_POLLING_FOR_MESSAGES;

    public interface MessageListener {
        void onMessageReceived(byte[] message);
    }

    public interface ScanListener {
        void onScanStarted();
        void onScanTimeout();
        void onDeviceConnected();
        void onDeviceDisconnected();
    }

    private MessageQueue mMessageQueue;
    private GroupTracker mGroupTracker;

    private List<CommHardware.ScanListener> mScanListeners = new CopyOnWriteArrayList<>();
    private List<MessageListener> mMessageListeners = new CopyOnWriteArrayList<>();

    private Thread mMessageWorkerThread;

    private boolean mConnected = false;
    private boolean mDestroyed = false;

    private UserInfo mSelfInfo;

    public CommHardware(MessageQueue messageQueue,
                        GroupTracker groupTracker) {
        mMessageQueue = messageQueue;
        mGroupTracker = groupTracker;
    }

    /**
     * External API
     */
    @CallSuper
    public void init(@NonNull Context context, @NonNull String callsign, long gId, String atakUid) {
        mSelfInfo = new UserInfo(callsign, gId, atakUid, mGroupTracker.getGroup() != null);
    }

    public abstract void broadcastDiscoveryMessage();
    public abstract void createGroup(List<Long> memberGids);
    public abstract void addToGroup(List<Long> allMemberGids, List<Long> newMemberGids);
    public abstract void connect();
    public abstract void forgetDevice();

    @CallSuper
    public void destroy() {
        mDestroyed = true;
    }

    public boolean isConnected() {
        return mConnected;
    }

    public boolean isInGroup() {
        return mGroupTracker.getGroup() != null;
    }

    /**
     * Listener Management
     */
    public void addMessageListener(MessageListener listener) {
        mMessageListeners.add(listener);
    }

    public void removeMessageListener(MessageListener listener) {
        mMessageListeners.remove(listener);
    }

    public void addScanListener(ScanListener listener) {
        mScanListeners.add(listener);
    }

    public void removeScanListener(ScanListener listener) {
        mScanListeners.remove(listener);
    }

    protected final List<MessageListener> getMessageListeners() {
        return mMessageListeners;
    }

    protected final List<ScanListener> getScanListeners() {
        return mScanListeners;
    }

    /**
     * Internal API
     */
    @CallSuper
    protected void startWorkerThreads() {
        mMessageWorkerThread = new Thread(() -> {
            while (!mDestroyed) {
                sleepForDelay(DELAY_BETWEEN_POLLING_FOR_MESSAGES);

                while (!mConnected) {
                    sleepForDelay(DELAY_BETWEEN_POLLING_FOR_MESSAGES);
                }

                MessageQueue.QueuedMessage queuedMessage = mMessageQueue.popHighestPriorityMessage(mGroupTracker.getGroup() != null);
                if (queuedMessage != null) {
                    sendMessage(queuedMessage);
                }
            }
        });
        mMessageWorkerThread.setName("CommHardware.MessageWorker");
        mMessageWorkerThread.start();
    }

    protected boolean isDestroyed() {
        return mDestroyed;
    }

    protected void setConnected(boolean isConnected) {
        mConnected = isConnected;
    }

    protected UserInfo getSelfInto() {
        return mSelfInfo;
    }

    protected void queueMessage(CotEvent cotEvent, byte[] message, String[] toUIDs, int priority, int xmitType, boolean overwriteSimilar) {
        mMessageQueue.queueMessage(cotEvent, message, toUIDs, priority, xmitType, overwriteSimilar);
    }

    protected void notifyMessageListeners(byte[] message) {
        for (MessageListener listener : mMessageListeners) {
            listener.onMessageReceived(message);
        }
    }

    /**
     * Utils
     */
    protected void sleepForDelay(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * For subclasses to implement
     */
    protected abstract void sendMessage(MessageQueue.QueuedMessage queuedMessage);
}
