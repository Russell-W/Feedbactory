package com.feedbactory.client.core.useraccount;


import com.feedbactory.shared.Message;


public interface AccountMessageListener
{
   public void userAccountMessageReceived(final Message message);
}