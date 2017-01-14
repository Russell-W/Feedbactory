
package com.feedbactory.server.network.component;


import com.feedbactory.server.core.log.SystemLogLevel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutorService;


public interface NetworkServerController
{
   public ExecutorService getChannelGroupThreadPool();
   public int getReceiveBufferSize();
   public int getBacklogSize();
   public void incrementActiveConnections();
   public boolean canAcceptNewConnection();
   public void newConnectionAccepted(final AsynchronousSocketChannel clientChannel);
   public void reportNetworkServerEvent(final SystemLogLevel logLevel, final String eventMessage);
   public void reportNetworkServerException(final Throwable throwable);
}