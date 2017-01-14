
package com.feedbactory.server.core.log;


public interface LogOutputHandler
{
   public void logSystemEvent(final SystemEvent event);
   public void logSecurityEvent(final SecurityEvent event);
   public void shutdown();
}