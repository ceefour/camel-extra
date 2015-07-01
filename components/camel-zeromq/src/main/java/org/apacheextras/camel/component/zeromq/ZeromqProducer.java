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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class ZeromqProducer extends DefaultProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZeromqProducer.class);

    private final ZeromqEndpoint endpoint;
    private Socket socket;
    private Context context;
    private final MessageConverter messageConvertor;
    private final SocketFactory socketFactory;
    private final ContextFactory contextFactory;
    private int shutdownWait = 5000;
    private String[] topics;

    public ZeromqProducer(ZeromqEndpoint endpoint, SocketFactory socketFactory, ContextFactory contextFactory)
            throws InstantiationException, IllegalAccessException {
        super(endpoint);
        this.endpoint = endpoint;
        this.socketFactory = socketFactory;
        this.contextFactory = contextFactory;
        this.messageConvertor = (MessageConverter) endpoint.getMessageConvertor().newInstance();
    }

    public ContextFactory getContextFactory() {
        return contextFactory;
    }

    public SocketFactory getSocketFactory() {
        return socketFactory;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        byte[] body = messageConvertor.convert(exchange);
        if (topics == null) {
            if (!socket.send(body, 0)) {
                throw new ZeromqException("ZMQ.Socket.send() reports error for " + endpoint.getSocketType() +
                        " " + endpoint.getSocketAddress());
            }
        } else {
            for (String topic : topics) {
                byte[] t = topic.getBytes();
                byte[] prefixed = new byte[t.length + body.length];
                System.arraycopy(t, 0, prefixed, 0, t.length);
                System.arraycopy(body, 0, prefixed, t.length, body.length);
                if (!socket.send(prefixed, 0)) {
                    throw new ZeromqException("ZMQ.Socket.send() reports error for " + endpoint.getSocketType() +
                            " " + endpoint.getSocketAddress());
                }
            }
        }
        exchange.getIn().setHeader(ZeromqConstants.HEADER_TIMESTAMP, System.currentTimeMillis());
        exchange.getIn().setHeader(ZeromqConstants.HEADER_SOURCE, endpoint.getSocketAddress());
        exchange.getIn().setHeader(ZeromqConstants.HEADER_SOCKET_TYPE, endpoint.getSocketType());

        if (endpoint.getSocketType() == ZeromqSocketType.REQ) {
            exchange.setPattern(ExchangePattern.InOut);
            LOGGER.debug("Waiting reply {} {} for {}ms...",
                    new Object[]{endpoint.getSocketAddress(), endpoint.getSocketType(), socket.getReceiveTimeOut()});
            byte[] msg = socket.recv(0);
            if (msg == null) {
                throw new ZeromqException("ZMQ.Socket.recv() returned null for " + endpoint.getSocketType() +
                        " " + endpoint.getSocketAddress());
            }
            LOGGER.trace("Received message [length={}]", msg.length);
            exchange.getIn().setHeader(ZeromqConstants.HEADER_TIMESTAMP, System.currentTimeMillis());
            exchange.getIn().setHeader(ZeromqConstants.HEADER_SOURCE, endpoint.getSocketAddress());
            exchange.getIn().setHeader(ZeromqConstants.HEADER_SOCKET_TYPE, endpoint.getSocketType());
            exchange.getIn().setBody(msg);
            LOGGER.trace("Updated exchange from reply [exchange={}]", exchange);
        }
    }

    public void setShutdownWait(int shutdownWait) {
        this.shutdownWait = shutdownWait;
    }

    @Override
    public void start() throws Exception {

        this.context = contextFactory.createContext(1);
        // TODO: For socketType REQ, socket should be created on-demand or pooled, to handle multiple requests at once
        this.socket = socketFactory.createProducerSocket(context, endpoint.getSocketType());
        this.topics = endpoint.getTopics() == null ? null : endpoint.getTopics().split(",");

        String addr = endpoint.getSocketAddress();
        LOGGER.info("Binding client to [{}] {}", addr, endpoint.getSocketType());
        socket.bind(addr);
        LOGGER.info("Client {} {} bound", addr, endpoint.getSocketType());
    }

    @Override
    public void stop() throws Exception {
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    socket.close();
                } catch (Exception e) {
                  LOGGER.error("Could not close socket during stop() [{}]", e);
                }
            }
        });
        t.start();
        LOGGER.debug("Waiting {}ms for producer socket {} {} to close",
                new Object[] { shutdownWait, endpoint.getSocketAddress(), endpoint.getSocketType() });
        t.join(shutdownWait);
        try {
            context.term();
        } catch (Exception e) {
          LOGGER.error("Could not terminate the context during stop() [{}]", e);
        }
    }
}
