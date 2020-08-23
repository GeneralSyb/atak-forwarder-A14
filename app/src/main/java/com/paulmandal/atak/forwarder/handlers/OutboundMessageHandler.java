package com.paulmandal.atak.forwarder.handlers;

import android.util.Log;

import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotEvent;
import com.paulmandal.atak.forwarder.comm.CotMessageCache;
import com.paulmandal.atak.forwarder.comm.commhardware.CommHardware;
import com.paulmandal.atak.forwarder.comm.protobuf.CotProtobufConverter;
import com.paulmandal.atak.forwarder.comm.protobuf.MinimalCotProtobufConverter;
import com.paulmandal.atak.forwarder.comm.queue.CommandQueue;
import com.paulmandal.atak.forwarder.comm.queue.commands.QueuedCommand;
import com.paulmandal.atak.forwarder.comm.queue.commands.QueuedCommandFactory;

import static com.paulmandal.atak.forwarder.cotutils.CotMessageTypes.TYPE_CHAT;
import static com.paulmandal.atak.forwarder.cotutils.CotMessageTypes.TYPE_PLI;

public class OutboundMessageHandler implements CommsMapComponent.PreSendProcessor {
    private static final String TAG = "ATAKDBG." + OutboundMessageHandler.class.getSimpleName();

    private CommsMapComponent mCommsMapComponent;
    private CommHardware mCommHardware;
    private CommandQueue mCommandQueue;
    private QueuedCommandFactory mQueuedCommandFactory;
    private CotMessageCache mCotMessageCache;
    private MinimalCotProtobufConverter mMinimalCotProtobufConverter;
    private CotProtobufConverter mCotProtobufConverter;

    public OutboundMessageHandler(CommsMapComponent commsMapComponent,
                                  CommHardware commHardware,
                                  CommandQueue commandQueue,
                                  QueuedCommandFactory queuedCommandFactory,
                                  CotMessageCache cotMessageCache,
                                  MinimalCotProtobufConverter minimalCotProtobufConverter,
                                  CotProtobufConverter cotProtobufConverter) {
        mCommsMapComponent = commsMapComponent;
        mCommHardware = commHardware;
        mCommandQueue = commandQueue;
        mQueuedCommandFactory = queuedCommandFactory;
        mCotMessageCache = cotMessageCache;
        mMinimalCotProtobufConverter = minimalCotProtobufConverter;
        mCotProtobufConverter = cotProtobufConverter;

        commsMapComponent.registerPreSendProcessor(this);
    }

    public void destroy() {
        mCommsMapComponent.registerPreSendProcessor(null);
    }

    @Override
    public void processCotEvent(CotEvent cotEvent, String[] toUIDs) {
        Log.d("ATAKDBG.processCotEvent", cotEvent.toString());
        String eventType = cotEvent.getType();
        if (mCommHardware.isConnected()
            && (toUIDs != null || mCommHardware.isInGroup())
            && !eventType.equals(TYPE_PLI)
            && !eventType.equals(TYPE_CHAT)) {
            if (mCotMessageCache.checkIfRecentlySent(cotEvent)) {
                Log.d(TAG, "Discarding recently sent event: " + cotEvent.toString()); // TODO: remove this
                return;
            }
            mCotMessageCache.cacheEvent(cotEvent);
        }

        byte[] cotProtobuf;
        if (mMinimalCotProtobufConverter.isSupportedType(eventType)) {
            cotProtobuf = mMinimalCotProtobufConverter.toByteArray(cotEvent);
        } else {
            cotProtobuf = mCotProtobufConverter.toByteArray(cotEvent);
        }
        boolean overwriteSimilar = eventType.equals(TYPE_PLI) || !eventType.equals(TYPE_CHAT);
        mCommandQueue.queueSendMessage(mQueuedCommandFactory.createSendMessageCommand(determineMessagePriority(cotEvent), cotEvent, cotProtobuf, toUIDs), overwriteSimilar);

        // TODO: remove this debugging
        Log.d(TAG, "from-atak: " + cotEvent);
        Log.d(TAG, "from-protobuf: " + mCotProtobufConverter.toCotEvent(cotProtobuf));
        Log.d(TAG, "largess protobuf len: " + mCotProtobufConverter.toByteArray(cotEvent).length);
        Log.d(TAG, "minimal protobuf len: " + mMinimalCotProtobufConverter.toByteArray(cotEvent).length);
    }

    private int determineMessagePriority(CotEvent cotEvent) {
        switch (cotEvent.getType()) {
            case TYPE_CHAT:
                return QueuedCommand.PRIORITY_MEDIUM;
            default:
                return QueuedCommand.PRIORITY_LOW;
        }
    }
}
