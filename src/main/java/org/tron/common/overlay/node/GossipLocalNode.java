/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.tron.common.overlay.node;

import io.scalecube.cluster.Cluster;
import io.scalecube.cluster.ClusterConfig;
import io.scalecube.cluster.Member;
import io.scalecube.cluster.membership.MembershipEvent.Type;
import io.scalecube.transport.Address;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tron.core.config.args.Args;
import org.tron.core.net.message.Message;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.net.peer.PeerConnectionDelegate;
import rx.Subscription;
import rx.subscriptions.CompositeSubscription;

public class GossipLocalNode implements LocalNode {

  private static final Logger logger = LoggerFactory.getLogger("GossipLocalNode");

  private Cluster cluster = null;

  private PeerConnectionDelegate peerDel;

  private static final GossipLocalNode INSTANCE = new GossipLocalNode();

  public HashMap<Integer, PeerConnection> listPeer = new HashMap<>();

  private ExecutorService executors;
  
  private CompositeSubscription subscriptions = new CompositeSubscription();

  @Override
  public void broadcast(Message message) {
    listPeer.forEach((id, peer) -> peer.sendMessage(message));
  }

  @Override
  public void start() {
    logger.info("listener message");

    ClusterConfig config = ClusterConfig.builder()
            .seedMembers(getAddresses())
            .portAutoIncrement(false)
        .port(Args.getInstance().getOverlay().getPort())
            .build();

    cluster = Cluster.joinAwait(config);

    for (Member member : cluster.otherMembers()) {
      listPeer.put(member.hashCode(), new PeerConnection(this.cluster, member));
    }

    Subscription membershipListener = cluster
            .listenMembership()
            .subscribe(event -> {
              if (event.type() == Type.REMOVED) {
                listPeer.remove(event.oldMember().hashCode());
              } else {
                listPeer.put(
                    event.newMember().hashCode(),
                    new PeerConnection(this.cluster,
                        event.newMember()));
              }
            });

    executors = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1000), new ThreadPoolExecutor.CallerRunsPolicy());

    Subscription messageSubscription = cluster.listen().subscribe(msg -> {
      executors.submit(new StartWorker(msg, peerDel, listPeer, cluster));
    });

    subscriptions.add(membershipListener);
    subscriptions.add(messageSubscription);
  }

  /**
   * stop gossip node.
   */
  public void stop() {
    cluster.shutdown();
    executors.shutdown();
    subscriptions.clear();
    listPeer.clear();

    cluster = null;
  }

  public void setPeerDel(PeerConnectionDelegate peerDel) {
    this.peerDel = peerDel;
  }

  public static GossipLocalNode getInstance() {
    return INSTANCE;
  }

  private List<Address> getAddresses() {
    List<Address> addresses = new ArrayList<>();

    List<String> ipList = Args.getInstance().getSeedNode().getIpList();

    ipList.forEach(ip -> {
      String[] ipSplit = ip.split(":");
      if (ipSplit.length > 1) {
        Address address = Address
            .create(ipSplit[0], Integer.valueOf(ipSplit[1]));
        addresses.add(address);
      }
    });

    return addresses;
  }
}
