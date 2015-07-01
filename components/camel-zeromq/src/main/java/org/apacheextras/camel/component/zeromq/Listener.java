/**************************************************************************************
 http://code.google.com/a/apache-extras.org/p/camel-extra

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation; either version 3
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.


 You should have received a copy of the GNU Lesser General Public
 License along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 02110-1301, USA.

 http://www.gnu.org/licenses/lgpl-3.0-standalone.html
 ***************************************************************************************/
package org.apacheextras.camel.component.zeromq;

import org.apache.camel.*;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

class Listener implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Listener.class);

    private volatile boolean running = true;
    private Socket socket;
    private Context context;
    private final ZeromqEndpoint endpoint;
    private final Processor processor;
    private final SocketFactory socketFactory;
    private final ContextFactory contextFactory;
    private AsyncCallback callback = new AsyncCallback() {

        @Override
        public void done(boolean doneSync) {
            // no-op
        }
    };
    private final MessageConverter messageConvertor;
    private final Object termination = new Object();
    private int shutdownWait;

    public Listener(ZeromqEndpoint endpoint, Processor processor, SocketFactory socketFactory, ContextFactory contextFactory) throws IllegalAccessException, InstantiationException {
        this.endpoint = endpoint;
        this.socketFactory = socketFactory;
        this.contextFactory = contextFactory;
        if (endpoint.isAsyncConsumer()) {
          this.processor = AsyncProcessorConverterHelper.convert(processor);
        }
        else {
          this.processor = processor;
        }
        this.messageConvertor = (MessageConverter) endpoint.getMessageConvertor().newInstance();
    }

    void connect() {
        context = contextFactory.createContext(1);
        // TODO: For socketType REP, socket should be created on-demand or pooled, to handle multiple requests at once
        socket = socketFactory.createConsumerSocket(context, endpoint.getSocketType());
        shutdownWait = Math.max(socket.getReceiveTimeOut(), socket.getSendTimeOut()) + 100;

        String addr = endpoint.getSocketAddress();
        LOGGER.info("Consuming from server [{}] {}", addr, endpoint.getSocketType());
        socket.connect(addr);
        LOGGER.info("Consumer {} {} connected", addr, endpoint.getSocketType());

        if (endpoint.getSocketType() == ZeromqSocketType.SUBSCRIBE) {
            subscribe();
        }
    }

    @Override
    public void run() {
        connect();
        while (running) {
            LOGGER.trace("Try receiving {} {} for {}ms... {}",
                    new Object[] { endpoint.getSocketAddress(), endpoint.getSocketType(), socket.getReceiveTimeOut(), running });
            byte[] msg = socket.recv(0);
            if (msg == null) {
              continue;
            }
            LOGGER.trace("Received message [length={}]", msg.length);
            final Exchange exchange = endpoint.createZeromqExchange(msg);
            LOGGER.trace("Created exchange [exchange={}]", new Object[] {exchange});
            try {
                if (processor instanceof AsyncProcessor) {
                    final AsyncCallback realCallback;
                    if (exchange.getPattern() == ExchangePattern.InOut) {
                        realCallback = new AsyncCallback() {
                            @Override
                            public void done(boolean doneSync) {
                                final Message outMsg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
                                byte[] body = messageConvertor.convert(exchange);
                                LOGGER.debug("Replying {} bytes {} {} for {}ms...",
                                        new Object[] { body.length, endpoint.getSocketAddress(), endpoint.getSocketType(), socket.getSendTimeOut() });
                                if (!socket.send(body, 0)) {
                                    throw new ZeromqException("ZMQ.Socket.send() reports error for " + endpoint.getSocketType() +
                                            " " + endpoint.getSocketAddress());
                                }
                                outMsg.setHeader(ZeromqConstants.HEADER_TIMESTAMP, System.currentTimeMillis());
                                outMsg.setHeader(ZeromqConstants.HEADER_SOURCE, endpoint.getSocketAddress());
                                outMsg.setHeader(ZeromqConstants.HEADER_SOCKET_TYPE, endpoint.getSocketType());
                                Listener.this.callback.done(doneSync);
                            }
                        };
                    } else {
                        realCallback = this.callback;
                    }
                    ((AsyncProcessor) processor).process(exchange, realCallback);
                } else {
                    processor.process(exchange);
                    if (exchange.getPattern() == ExchangePattern.InOut) {
                        final Message outMsg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
                        byte[] body = messageConvertor.convert(exchange);
                        LOGGER.debug("Replying {} bytes {} {} for {}ms...",
                                new Object[] { body.length, endpoint.getSocketAddress(), endpoint.getSocketType(), socket.getSendTimeOut() });
                        if (!socket.send(body, 0)) {
                            throw new ZeromqException("ZMQ.Socket.send() reports error for " + endpoint.getSocketType() +
                                    " " + endpoint.getSocketAddress());
                        }
                        outMsg.setHeader(ZeromqConstants.HEADER_TIMESTAMP, System.currentTimeMillis());
                        outMsg.setHeader(ZeromqConstants.HEADER_SOURCE, endpoint.getSocketAddress());
                        outMsg.setHeader(ZeromqConstants.HEADER_SOCKET_TYPE, endpoint.getSocketType());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Exception processing exchange", e);
            }
        }

        try {
            LOGGER.info("Closing socket {} {}", endpoint.getSocketAddress(), endpoint.getSocketType());
            socket.close();
        } catch (Exception e) {
          LOGGER.error("Could not close socket during run()", e);
        }
        try {
            LOGGER.info("Terminating context {} {}", endpoint.getSocketAddress(), endpoint.getSocketType());
            context.term();
        } catch (Exception e) {
          LOGGER.error("Could not terminate context during run()", e);
        }
        synchronized (termination) {
            termination.notifyAll();
        }
    }

    public void setCallback(AsyncCallback callback) {
        this.callback = callback;
    }

    void stop() {
        LOGGER.debug("Requesting shutdown of consumer thread {} {}",
                endpoint.getSocketAddress(), endpoint.getSocketType());
        running = false;
        synchronized (termination) {
            try {
                termination.wait(shutdownWait);
            } catch (InterruptedException e) {
                LOGGER.error("Unable to wait shutdown of consumer thread " +
                        endpoint.getSocketAddress() + " " + endpoint.getSocketType(), e);
            }
        }
//        // we have to term the context to interrupt the recv call
//        if (context != null) {
//          try {
//            context.term();
//          } catch (Exception e) {
//            LOGGER.error("Could not terminate context during stop()", e);
//          }
//        }
    }

    void subscribe() {
        if (endpoint.getTopics() == null) {
            // subscribe all by using
            // empty filter
            LOGGER.debug("Subscribing to all messages (topics option was not specified)",
                endpoint.getTopics());
            socket.subscribe("".getBytes());
        } else {
            LOGGER.debug("Subscribing to topics: {}", endpoint.getTopics());
            for (String topic : endpoint.getTopics().split(",")) {
                socket.subscribe(topic.getBytes());
            }
        }
    }
}