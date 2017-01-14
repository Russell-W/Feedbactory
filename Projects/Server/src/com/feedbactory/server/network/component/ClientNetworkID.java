
package com.feedbactory.server.network.component;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;


final public class ClientNetworkID
{
   final public AsynchronousSocketChannel clientChannel;
   final public InetSocketAddress inetSocketAddress;


   public ClientNetworkID(final AsynchronousSocketChannel clientChannel) throws IOException
   {
      this.clientChannel = clientChannel;
      this.inetSocketAddress = (InetSocketAddress) clientChannel.getRemoteAddress();
   }
}