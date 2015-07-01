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

/**
 * @see <a href="http://api.zeromq.org/2-1:zmq-socket">zmq_socket()</a>
 */
public enum ZeromqSocketType {

    /**
     * A socket of type ZMQ_PUSH is used by a pipeline node to send messages to downstream pipeline nodes.
     * Messages are round-robined to all connected downstream nodes.
     * The zmq_recv() function is not implemented for this socket type.
     */
    PUSH(),
    /**
     * A socket of type ZMQ_PULL is used by a pipeline node to receive messages from upstream pipeline nodes.
     * Messages are fair-queued from among all connected upstream nodes.
     * The zmq_send() function is not implemented for this socket type.
     */
    PULL(),
    /**
     * A socket of type ZMQ_SUB is used by a subscriber to subscribe to data distributed by a publisher.
     * Initially a ZMQ_SUB socket is not subscribed to any messages, use the ZMQ_SUBSCRIBE option of zmq_setsockopt(3) to specify which messages to subscribe to.
     * The zmq_send() function is not implemented for this socket type.
     */
    SUBSCRIBE(),
    /**
     * A socket of type ZMQ_PUB is used by a publisher to distribute data.
     * Messages sent are distributed in a fan out fashion to all connected peers.
     * The zmq_recv(3) function is not implemented for this socket type.
     */
    PUBLISH(),
    /**
     * A socket of type ZMQ_DEALER is an advanced pattern used for extending request/reply sockets.
     * Each message sent is round-robined among all connected peers, and each message received is fair-queued from all connected peers.
     */
    DEALER(),
    /**
     * A socket of type ZMQ_ROUTER is an advanced pattern used for extending request/reply sockets.
     * When receiving messages a ZMQ_ROUTER socket shall prepend a message part containing the identity of the originating peer to the message before passing it to the application.
     * Messages received are fair-queued from among all connected peers.
     * When sending messages a ZMQ_ROUTER socket shall remove the first part of the message and use it to determine the identity of the peer the message shall be routed to.
     * If the peer does not exist anymore the message shall be silently discarded.
     */
    ROUTER(),
    /**
     * A socket of type ZMQ_REQ is used by a client to send requests to and receive replies from a service.
     * This socket type allows only an alternating sequence of zmq_send(request) and subsequent zmq_recv(reply) calls.
     * Each request sent is round-robined among all services, and each reply received is matched with the last issued request.
     */
    REQ(),
    /**
     * A socket of type ZMQ_REP is used by a service to receive requests from and send replies to a client.
     * This socket type allows only an alternating sequence of zmq_recv(request) and subsequent zmq_send(reply) calls.
     * Each request received is fair-queued from among all clients, and each reply sent is routed to the client that issued the last request.
     * If the original requester doesn't exist any more the reply is silently discarded.
     */
    REP()

}