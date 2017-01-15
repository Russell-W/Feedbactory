# Feedbactory

## Overview

Feedbactory was a web browser enhancement that was designed to allow users to "rate stuff" as they browsed for products, services, and other items.

Longer term the goal was to build a platform that could also track item pricing and suggest savings options, similar to some browser extensions that are now floating around. Taking this further the browser or other program could provide its own shopping portal UI, aggregating items from different vendors that support a common e-commerce protocol.

The project had reached the feedback milestone and had definitely reached the limit of its potential as an application backed by a Java UI. Beyond this point any further work on the client end at least would best be focussed on a Chrome extension or similar.

Although I don't have any plans to push ahead with the platform myself, I've put the Java code onto GitHub because there are a bunch of components and code snippets that may be useful to people. Some are fairly tightly integrated with the rest of the framework while others are more standalone.

## Code snippets

Administrator console:
- [Projects/Server/src/com/feedbactory/server/core/FeedbactoryConsole.java](Projects/Server/src/com/feedbactory/server/core/FeedbactoryConsole.java)
- This is a prime candidate for being overhauled with Lambdas.

Database compacting task:
- [Projects/Server/src/com/feedbactory/server/feedback/FeedbackManager.java#HousekeepingTask](https://github.com/Russell-W/Feedbactory/blob/5014aad748343c571820d4ff3306b2fd90c58f61/Projects/Server/src/com/feedbactory/server/feedback/FeedbackManager.java#L178)

Database checkpointing task:
- [Projects/Server/src/com/feedbactory/server/core/CheckpointManager.java](Projects/Server/src/com/feedbactory/server/core/CheckpointManager.java)

Session management (RSA, AES, nonce and timestamp mechanism, housekeeping task):
- [Projects/Server/src/com/feedbactory/server/network/application/UserAccountSessionManager.java](Projects/Server/src/com/feedbactory/server/network/application/UserAccountSessionManager.java)
- [Projects/Client/src/com/feedbactory/client/core/useraccount/AccountSessionManager.java](Projects/Client/src/com/feedbactory/client/core/useraccount/AccountSessionManager.java)

Asynchronous server:
- [Projects/Server/src/com/feedbactory/server/network/application/ApplicationServerController.java](Projects/Server/src/com/feedbactory/server/network/application/ApplicationServerController.java)
- [Projects/Server/src/com/feedbactory/server/network/component/AsynchronousNetworkServer.java](Projects/Server/src/com/feedbactory/server/network/component/AsynchronousNetworkServer.java)
- [Projects/Server/src/com/feedbactory/server/network/component/ClientRequestReader.java](Projects/Server/src/com/feedbactory/server/network/component/ClientRequestReader.java)
- [Projects/Server/src/com/feedbactory/server/network/component/ClientResponseWriter.java](Projects/Server/src/com/feedbactory/server/network/component/ClientResponseWriter.java)

User account manager (account and auth code generation, database checkpointing, housekeeping task):
- [Projects/Server/src/com/feedbactory/server/useraccount/UserAccountManager.java](Projects/Server/src/com/feedbactory/server/useraccount/UserAccountManager.java)

Spam monitor:
- [Projects/Server/src/com/feedbactory/server/network/component/IPAddressRequestMonitor.java](Projects/Server/src/com/feedbactory/server/network/component/IPAddressRequestMonitor.java)

Bit-array set implementation:
- [Projects/Shared/src/com/feedbactory/shared/feedback/personal/PersonalFeedbackWebsiteSet.java](Projects/Shared/src/com/feedbactory/shared/feedback/personal/PersonalFeedbackWebsiteSet.java)

Byte buffer pool:
- [Projects/Server/src/com/feedbactory/server/network/component/buffer/ByteBufferPool.java](Projects/Server/src/com/feedbactory/server/network/component/buffer/ByteBufferPool.java)

Exception report mailer:
- [Projects/Client/src/com/feedbactory/client/core/ExceptionReportMailer.java](Projects/Client/src/com/feedbactory/client/core/ExceptionReportMailer.java)

Logging framework for separately handling security events and system events:
- [Projects/Server/src/com/feedbactory/server/core/log/FeedbactoryLogger.java](Projects/Server/src/com/feedbactory/server/core/log/FeedbactoryLogger.java)

Automatic updates:
- [Projects/Client/src/com/feedbactory/client/launch/core/ConfigurationManager.java](Projects/Client/src/com/feedbactory/client/launch/core/ConfigurationManager.java)

Paint Swing onto SWT (raster image processing, water effect algorithm):
- [Projects/Client/src/com/feedbactory/client/ui/component/graftable/GraftableComponentSwingFramework.java](Projects/Client/src/com/feedbactory/client/ui/component/graftable/GraftableComponentSwingFramework.java)
- [Projects/Client/src/com/feedbactory/client/ui/component/WateryPanel.java](Projects/Client/src/com/feedbactory/client/ui/component/WateryPanel.java)
- [Projects/Client/src/com/feedbactory/client/ui/component/SWTImageRippler.java](Projects/Client/src/com/feedbactory/client/ui/component/SWTImageRippler.java)

Cached image manager:
- [Projects/Client/src/com/feedbactory/client/ui/component/ImageLoader.java](Projects/Client/src/com/feedbactory/client/ui/component/ImageLoader.java)

## Dependencies

For the sake of professional development and learning a goal of the project was minimal reliance on third party libraries. Where possible functionality was built on top of Java SE, eg. server implementation using JRE 7+'s AsynchronousChannelGroup instead of Netty or similar. The end result is that there aren't many libraries needed to compile and run each project.

### Server
- Java SE 8+
- [Projects/Shared](Projects/Shared)
- [Libraries/JavaMail](Libraries/JavaMail)

### Client
- Java SE 6u10+
- [Projects/Shared](Projects/Shared)
- [Libraries/JAI](Libraries/JAI)
- [Libraries/JavaMail](Libraries/JavaMail)
- [SWT for your operating system](https://www.eclipse.org/swt/)

### Launcher
- Java SE 6u10+
- [Libraries/JavaMail](Libraries/JavaMail)

### Recent Feedback Updater
- Java SE 8+
- [Projects/Shared](Projects/Shared)
- [Libraries/JavaMail](Libraries/JavaMail)
- [AWS for Java SDK](https://aws.amazon.com/sdk-for-java/)

## Running

### Server
- java com.feedbactory.server.FeedbactoryServer
- From the administrator console:
  - server start
  - status
  - server
  - request
  - broadcast 1 Hello World!
  - server shutdown
  - exit
- Refer to [Projects/Server/src/com/feedbactory/server/core/FeedbactoryConsole.java](Projects/Server/src/com/feedbactory/server/core/FeedbactoryConsole.java) for other console commands and switches

### Client
- Windows:
  - java com.feedbactory.client.FeedbactoryClient
- macOS:
  - java -XstartOnFirstThread com.feedbactory.client.FeedbactoryClient
- Linux:
  - You may need to first install and configure WebKitGTK+ 1.2.x or newer, as per [https://www.eclipse.org/swt/faq.php#browserlinuxrcp](https://www.eclipse.org/swt/faq.php#browserlinuxrcp)
  - java com.feedbactory.client.FeedbactoryClient


The launcher project is used by the live Feedbactory instance to download and install automatic updates from a remote server. Similarly, the recent feedback updater project is used by the live instance to periodically refresh the recent feedback gallery hosted on AWS S3: http://feedbactory.com/recentFeedback/photography/index.html .