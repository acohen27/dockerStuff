/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server.quorum;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;

import org.apache.jute.BinaryInputArchive;
import org.apache.jute.BinaryOutputArchive;
import org.apache.jute.InputArchive;
import org.apache.jute.OutputArchive;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.ByteBufferInputStream;
import org.apache.zookeeper.server.ByteBufferOutputStream;
import org.apache.zookeeper.server.ServerCnxn;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZKDatabase;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeer.QuorumServer;
import org.apache.zookeeper.server.quorum.flexible.QuorumMaj;
import org.apache.zookeeper.server.util.ZxidUtils;
import org.apache.zookeeper.txn.CreateTxn;
import org.apache.zookeeper.txn.SetDataTxn;
import org.apache.zookeeper.txn.TxnHeader;
import org.junit.Assert;
import org.junit.Test;

public class Zab1_0Test {
    private static final class LeadThread extends Thread {
        private final Leader leader;

        private LeadThread(Leader leader) {
            this.leader = leader;
        }

        public void run() {
            try {
                leader.lead();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                leader.shutdown("lead ended");
            }
        }
    }

   public static final class FollowerMockThread extends Thread {
    	private final Leader leader;
    	private final long followerSid;
    	public long epoch = -1;
    	public String msg = null;
    	private boolean onlyGetEpochToPropose;
    	
    	private FollowerMockThread(long followerSid, Leader leader, boolean onlyGetEpochToPropose) {
            this.leader = leader;
            this.followerSid = followerSid;
            this.onlyGetEpochToPropose = onlyGetEpochToPropose;
        }

        public void run() {
            if (onlyGetEpochToPropose) {
	            try {
	            	epoch = leader.getEpochToPropose(followerSid, 0);
	            } catch (Exception e) {
	            }
            } else {	            
	            try{
	                leader.waitForEpochAck(followerSid, new StateSummary(0, 0)); 
	                msg = "FollowerMockThread (id = " + followerSid + ")  returned from waitForEpochAck";      
	            } catch (Exception e) {	            	
	            }
            }
        }       
    }
    @Test
    public void testLeaderInConnectingFollowers() throws Exception {    
        File tmpDir = File.createTempFile("test", "dir");
        tmpDir.delete();
        tmpDir.mkdir();
        Leader leader = null;
        try {
            QuorumPeer peer = createQuorumPeer(tmpDir);
            leader = createLeader(tmpDir, peer);
            peer.leader = leader;
            peer.setAcceptedEpoch(5);
            
            FollowerMockThread f1 = new FollowerMockThread(1, leader, true);
            FollowerMockThread f2 = new FollowerMockThread(2, leader, true);
            f1.start();
            f2.start();
            
            // wait until followers time out in getEpochToPropose - they shouldn't return
            // normally because the leader didn't execute getEpochToPropose and so its epoch was not
            // accounted for
            f1.join(leader.self.getInitLimit()*leader.self.getTickTime() + 5000);
            f2.join(leader.self.getInitLimit()*leader.self.getTickTime() + 5000);
                
            // even though followers timed out, their ids are in connectingFollowers, and their
            // epoch were accounted for, so the leader should not block and since it started with 
            // accepted epoch = 5 it should now have 6
            try {
            	long epoch = leader.getEpochToPropose(leader.self.getId(), leader.self.getAcceptedEpoch());
            	Assert.assertEquals("leader got wrong epoch from getEpochToPropose", 6, epoch);	
            } catch (Exception e){ 
            	Assert.fail("leader timed out in getEpochToPropose");
            }
        } finally {
            recursiveDelete(tmpDir);
            if (leader != null) {
                leader.shutdown("end of test");
            }
        }
    }
    
    @Test
    public void testLeaderInElectingFollowers() throws Exception {    
        File tmpDir = File.createTempFile("test", "dir");
        tmpDir.delete();
        tmpDir.mkdir();
        Leader leader = null;
        try {
            QuorumPeer peer = createQuorumPeer(tmpDir);
            leader = createLeader(tmpDir, peer);
            peer.leader = leader;            
            
            FollowerMockThread f1 = new FollowerMockThread(1, leader, false);
            FollowerMockThread f2 = new FollowerMockThread(2, leader, false);

            // things needed for waitForEpochAck to run (usually in leader.lead(), but we're not running leader here)
            leader.readyToStart = true;
            leader.leaderStateSummary = new StateSummary(leader.self.getCurrentEpoch(), leader.zk.getLastProcessedZxid());
            
            f1.start();
            f2.start();         
            
            // wait until followers time out in waitForEpochAck - they shouldn't return
            // normally because the leader didn't execute waitForEpochAck
            f1.join(leader.self.getInitLimit()*leader.self.getTickTime() + 5000);
            f2.join(leader.self.getInitLimit()*leader.self.getTickTime() + 5000);
                        
            // make sure that they timed out and didn't return normally  
            Assert.assertTrue(f1.msg + " without waiting for leader", f1.msg == null);            
            Assert.assertTrue(f2.msg + " without waiting for leader", f2.msg == null);
        } finally {
            recursiveDelete(tmpDir);
            if (leader != null) {
                leader.shutdown("end of test");
            }
        }
    }

    private static final class NullServerCnxnFactory extends ServerCnxnFactory {
        public void startup(ZooKeeperServer zkServer) throws IOException,
                InterruptedException {
        }
        public void start() {
        }
        public void shutdown() {
        }
        public void setMaxClientCnxnsPerHost(int max) {
        }
        public void join() throws InterruptedException {
        }
        public int getMaxClientCnxnsPerHost() {
            return 0;
        }
        public int getLocalPort() {
            return 0;
        }
        public InetSocketAddress getLocalAddress() {
            return null;
        }
        public Iterable<ServerCnxn> getConnections() {
            return null;
        }
        public void configure(InetSocketAddress addr, int maxClientCnxns)
                throws IOException {
        }
        public void closeSession(long sessionId) {
        }
        public void closeAll() {
        }
    }
    static Socket[] getSocketPair() throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.bind(null);
        InetSocketAddress endPoint = (InetSocketAddress) ss.getLocalSocketAddress();
        Socket s = new Socket(endPoint.getAddress(), endPoint.getPort());
        return new Socket[] { s, ss.accept() };
    }
    static void readPacketSkippingPing(InputArchive ia, QuorumPacket qp) throws IOException {
        while(true) {
            ia.readRecord(qp, null);
            if (qp.getType() != Leader.PING) {
                return;
            }
        }
    }
    
    static public interface LeaderConversation {
        void converseWithLeader(InputArchive ia, OutputArchive oa, Leader l) throws Exception;
    }
    
    static public interface FollowerConversation {
        void converseWithFollower(InputArchive ia, OutputArchive oa, Follower f) throws Exception;
    }
    
    public void testLeaderConversation(LeaderConversation conversation) throws Exception {
        Socket pair[] = getSocketPair();
        Socket leaderSocket = pair[0];
        Socket followerSocket = pair[1];
        File tmpDir = File.createTempFile("test", "dir");
        tmpDir.delete();
        tmpDir.mkdir();
        LeadThread leadThread = null;
        Leader leader = null;
        try {
            QuorumPeer peer = createQuorumPeer(tmpDir);
            leader = createLeader(tmpDir, peer);
            peer.leader = leader;
            leadThread = new LeadThread(leader);
            leadThread.start();

            while(!leader.readyToStart) {
                Thread.sleep(20);
            }
            
            LearnerHandler lh = new LearnerHandler(leaderSocket, leader);
            lh.start();
            leaderSocket.setSoTimeout(4000);

            InputArchive ia = BinaryInputArchive.getArchive(followerSocket
                    .getInputStream());
            OutputArchive oa = BinaryOutputArchive.getArchive(followerSocket
                    .getOutputStream());

            conversation.converseWithLeader(ia, oa, leader);
        } finally {
            recursiveDelete(tmpDir);
            if (leader != null) {
                leader.shutdown("end of test");
            }
            if (leadThread != null) {
                leadThread.interrupt();
                leadThread.join();
            }
        }
    }
    
    public void testFollowerConversation(FollowerConversation conversation) throws Exception {
        File tmpDir = File.createTempFile("test", "dir");
        tmpDir.delete();
        tmpDir.mkdir();
        Thread followerThread = null;
        ConversableFollower follower = null;
        QuorumPeer peer = null;
        try {
            peer = createQuorumPeer(tmpDir);
            follower = createFollower(tmpDir, peer);
            peer.follower = follower;
            
            ServerSocket ss = new ServerSocket();
            ss.bind(null);
            follower.setLeaderSocketAddress((InetSocketAddress)ss.getLocalSocketAddress());
            final Follower followerForThread = follower;
            
            followerThread = new Thread() {
                public void run() {
                    try {
                        followerForThread.followLeader();
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            followerThread.start();
            Socket leaderSocket = ss.accept();
            
            InputArchive ia = BinaryInputArchive.getArchive(leaderSocket
                    .getInputStream());
            OutputArchive oa = BinaryOutputArchive.getArchive(leaderSocket
                    .getOutputStream());

            conversation.converseWithFollower(ia, oa, follower);
        } finally {
            if (follower != null) {
                follower.shutdown();
            }
            if (followerThread != null) {
                followerThread.interrupt();
                followerThread.join();
            }
            if (peer != null) {
                peer.shutdown();
            }
            recursiveDelete(tmpDir);
        }
    }

    @Test
    public void testNormalFollowerRun() throws Exception {
        testFollowerConversation(new FollowerConversation() {
            @Override
            public void converseWithFollower(InputArchive ia, OutputArchive oa,
                    Follower f) throws Exception {
                File tmpDir = File.createTempFile("test", "dir");
                tmpDir.delete();
                tmpDir.mkdir();
                File logDir = f.fzk.getTxnLogFactory().getDataDir().getParentFile();
                File snapDir = f.fzk.getTxnLogFactory().getSnapDir().getParentFile();
                try {
                    Assert.assertEquals(0, f.self.getAcceptedEpoch());
                    Assert.assertEquals(0, f.self.getCurrentEpoch());

                    // Setup a database with a single /foo node
                    ZKDatabase zkDb = new ZKDatabase(new FileTxnSnapLog(tmpDir, tmpDir));
                    final long firstZxid = ZxidUtils.makeZxid(1, 1);
                    zkDb.processTxn(new TxnHeader(13, 1313, firstZxid, 33, ZooDefs.OpCode.create), new CreateTxn("/foo", "data1".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, false, 1));
                    Stat stat = new Stat();
                    Assert.assertEquals("data1", new String(zkDb.getData("/foo", stat, null)));

                    QuorumPacket qp = new QuorumPacket();
                    readPacketSkippingPing(ia, qp);
                    Assert.assertEquals(Leader.FOLLOWERINFO, qp.getType());
                    Assert.assertEquals(qp.getZxid(), 0);
                    LearnerInfo learnInfo = new LearnerInfo();
                    ByteBufferInputStream.byteBuffer2Record(ByteBuffer.wrap(qp.getData()), learnInfo);
                    Assert.assertEquals(learnInfo.getProtocolVersion(), 0x10000);
                    Assert.assertEquals(learnInfo.getServerid(), 0);
                
                    // We are simulating an established leader, so the epoch is 1
                    qp.setType(Leader.LEADERINFO);
                    qp.setZxid(ZxidUtils.makeZxid(1, 0));
                    byte protoBytes[] = new byte[4];
                    ByteBuffer.wrap(protoBytes).putInt(0x10000);
                    qp.setData(protoBytes);
                    oa.writeRecord(qp, null);
                
                    readPacketSkippingPing(ia, qp);
                    Assert.assertEquals(Leader.ACKEPOCH, qp.getType());
                    Assert.assertEquals(0, qp.getZxid());
                    Assert.assertEquals(ZxidUtils.makeZxid(0, 0), ByteBuffer.wrap(qp.getData()).getInt());
                    Assert.assertEquals(1, f.self.getAcceptedEpoch());
                    Assert.assertEquals(0, f.self.getCurrentEpoch());
                    
                    // Send the snapshot we created earlier
                    qp.setType(Leader.SNAP);
                    qp.setData(new byte[0]);
                    qp.setZxid(zkDb.getDataTreeLastProcessedZxid());
                    oa.writeRecord(qp, null);
                    zkDb.serializeSnapshot(oa);
                    oa.writeString("BenWasHere", null);
                    qp.setType(Leader.NEWLEADER);
                    qp.setZxid(ZxidUtils.makeZxid(1, 0));
                    oa.writeRecord(qp, null);

                    // Get the ack of the new leader
                    readPacketSkippingPing(ia, qp);
                    Assert.assertEquals(Leader.ACK, qp.getType());
                    Assert.assertEquals(ZxidUtils.makeZxid(1, 0), qp.getZxid());
                    Assert.assertEquals(1, f.self.getAcceptedEpoch());
                    Assert.assertEquals(1, f.self.getCurrentEpoch());
                    
                    Assert.assertEquals(firstZxid, f.fzk.getLastProcessedZxid());
                    
                    // Make sure the data was recorded in the filesystem ok
                    ZKDatabase zkDb2 = new ZKDatabase(new FileTxnSnapLog(logDir, snapDir));
                    long lastZxid = zkDb2.loadDataBase();
                    Assert.assertEquals("data1", new String(zkDb2.getData("/foo", stat, null)));
                    Assert.assertEquals(firstZxid, lastZxid);

                    // Propose an update
                    long proposalZxid = ZxidUtils.makeZxid(1, 1000);
                    proposeSetData(qp, proposalZxid, "data2", 2);
                    oa.writeRecord(qp, null);
                    
                    // We want to track the change with a callback rather than depending on timing
                    class TrackerWatcher implements Watcher {
                        boolean changed;
                        synchronized void waitForChange() throws InterruptedException {
                            while(!changed) {
                                wait();
                            }
                        }
                        @Override
                        public void process(WatchedEvent event) {
                            if (event.getType() == EventType.NodeDataChanged) {
                                synchronized(this) {
                                    changed = true;
                                    notifyAll();
                                }
                            }
                        }
                        synchronized public boolean changed() {
                            return changed;
                        }
                        
                    };
                    TrackerWatcher watcher = new TrackerWatcher();
                    
                    // The change should not have happened yet, since we haven't committed
                    Assert.assertEquals("data1", new String(f.fzk.getZKDatabase().getData("/foo", stat, watcher)));
                    
                    // The change should happen now
                    qp.setType(Leader.COMMIT);
                    qp.setZxid(proposalZxid);
                    oa.writeRecord(qp, null);
                    
                    qp.setType(Leader.UPTODATE);
                    qp.setZxid(0);
                    oa.writeRecord(qp, null);
                    
                    // Read the uptodate ack
                    readPacketSkippingPing(ia, qp);
                    Assert.assertEquals(Leader.ACK, qp.getType());
                    Assert.assertEquals(ZxidUtils.makeZxid(1, 0), qp.getZxid());
                    
                    readPacketSkippingPing(ia, qp);
                    Assert.assertEquals(Leader.ACK, qp.getType());
                    Assert.assertEquals(proposalZxid, qp.getZxid());
                    
                    watcher.waitForChange();
                    Assert.assertEquals("data2", new String(f.fzk.getZKDatabase().getData("/foo", stat, null)));
                    
                    // check and make sure the change is persisted
                    zkDb2 = new ZKDatabase(new FileTxnSnapLog(logDir, snapDir));
                    lastZxid = zkDb2.loadDataBase();
                    Assert.assertEquals("data2", new String(zkDb2.getData("/foo", stat, null)));
                    Assert.assertEquals(proposalZxid, lastZxid);
                } finally {
                    recursiveDelete(tmpDir);
                }
                
            }

            private void proposeSetData(QuorumPacket qp, long zxid, String data, int version) throws IOException {
                qp.setType(Leader.PROPOSAL);
                qp.setZxid(zxid);
                TxnHeader hdr = new TxnHeader(4, 1414, qp.getZxid(), 55, ZooDefs.OpCode.setData);
                SetDataTxn sdt = new SetDataTxn("/foo", data.getBytes(), version);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputArchive boa = BinaryOutputArchive.getArchive(baos);
                boa.writeRecord(hdr, null);
                boa.writeRecord(sdt, null);
                qp.setData(baos.toByteArray());
            }
        });
    }
    
    @Test
    public void testNormalRun() throws Exception {
        testLeaderConversation(new LeaderConversation() {
            public void converseWithLeader(InputArchive ia, OutputArchive oa, Leader l)
                    throws IOException {
                Assert.assertEquals(0, l.self.getAcceptedEpoch());
                Assert.assertEquals(0, l.self.getCurrentEpoch());
                
                /* we test a normal run. everything should work out well. */
                LearnerInfo li = new LearnerInfo(1, 0x10000);
                byte liBytes[] = new byte[12];
                ByteBufferOutputStream.record2ByteBuffer(li,
                        ByteBuffer.wrap(liBytes));
                QuorumPacket qp = new QuorumPacket(Leader.FOLLOWERINFO, 0,
                        liBytes, null);
                oa.writeRecord(qp, null);
                
                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.LEADERINFO, qp.getType());
                Assert.assertEquals(ZxidUtils.makeZxid(1, 0), qp.getZxid());
                Assert.assertEquals(ByteBuffer.wrap(qp.getData()).getInt(),
                        0x10000);
                Assert.assertEquals(1, l.self.getAcceptedEpoch());
                Assert.assertEquals(0, l.self.getCurrentEpoch());
                
                qp = new QuorumPacket(Leader.ACKEPOCH, 0, new byte[4], null);
                oa.writeRecord(qp, null);
                
                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.SNAP, qp.getType());
                deserializeSnapshot(ia);
               
                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.NEWLEADER, qp.getType());
                Assert.assertEquals(ZxidUtils.makeZxid(1, 0), qp.getZxid());
                Assert.assertEquals(1, l.self.getAcceptedEpoch());
                Assert.assertEquals(1, l.self.getCurrentEpoch());
                
                qp = new QuorumPacket(Leader.ACK, qp.getZxid(), null, null);
                oa.writeRecord(qp, null);

                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.NEWLEADER, qp.getType());
                Assert.assertEquals(ZxidUtils.makeZxid(1, 0), qp.getZxid());
                Assert.assertEquals(1, l.self.getAcceptedEpoch());
                Assert.assertEquals(1, l.self.getCurrentEpoch());
                
                qp = new QuorumPacket(Leader.ACK, qp.getZxid(), null, null);
                oa.writeRecord(qp, null);

                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.UPTODATE, qp.getType());
            }
        });
    }

    private void deserializeSnapshot(InputArchive ia)
            throws IOException {
        ZKDatabase zkdb = new ZKDatabase(null);
        zkdb.deserializeSnapshot(ia);
        String signature = ia.readString("signature");
        assertEquals("BenWasHere", signature);
    }

    @Test
    public void testLeaderBehind() throws Exception {
        testLeaderConversation(new LeaderConversation() {
            public void converseWithLeader(InputArchive ia, OutputArchive oa, Leader l)
                    throws IOException {
                /* we test a normal run. everything should work out well. */
                LearnerInfo li = new LearnerInfo(1, 0x10000);
                byte liBytes[] = new byte[12];
                ByteBufferOutputStream.record2ByteBuffer(li,
                        ByteBuffer.wrap(liBytes));
                /* we are going to say we last acked epoch 20 */
                QuorumPacket qp = new QuorumPacket(Leader.FOLLOWERINFO, ZxidUtils.makeZxid(20, 0),
                        liBytes, null);
                oa.writeRecord(qp, null);
                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.LEADERINFO, qp.getType());
                Assert.assertEquals(ZxidUtils.makeZxid(21, 0), qp.getZxid());
                Assert.assertEquals(ByteBuffer.wrap(qp.getData()).getInt(),
                        0x10000);
                qp = new QuorumPacket(Leader.ACKEPOCH, 0, new byte[4], null);
                oa.writeRecord(qp, null);
                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.SNAP, qp.getType());
                deserializeSnapshot(ia);

                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.NEWLEADER, qp.getType());
                Assert.assertEquals(ZxidUtils.makeZxid(21, 0), qp.getZxid());

                qp = new QuorumPacket(Leader.ACK, qp.getZxid(), null, null);
                oa.writeRecord(qp, null);

                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.NEWLEADER, qp.getType());
                Assert.assertEquals(ZxidUtils.makeZxid(21, 0), qp.getZxid());

                qp = new QuorumPacket(Leader.ACK, qp.getZxid(), null, null);
                oa.writeRecord(qp, null);

                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.UPTODATE, qp.getType());
            }
        });
    }

    /**
     * Tests that when a quorum of followers send LearnerInfo but do not ack the epoch (which is sent
     * by the leader upon receipt of LearnerInfo from a quorum), the leader does not start using this epoch
     * as it would in the normal case (when a quorum do ack the epoch). This tests ZK-1192
     * @throws Exception
     */
    @Test
    public void testAbandonBeforeACKEpoch() throws Exception {
        testLeaderConversation(new LeaderConversation() {
            public void converseWithLeader(InputArchive ia, OutputArchive oa, Leader l)
                    throws IOException, InterruptedException {
                /* we test a normal run. everything should work out well. */            	
                LearnerInfo li = new LearnerInfo(1, 0x10000);
                byte liBytes[] = new byte[12];
                ByteBufferOutputStream.record2ByteBuffer(li,
                        ByteBuffer.wrap(liBytes));
                QuorumPacket qp = new QuorumPacket(Leader.FOLLOWERINFO, 0,
                        liBytes, null);
                oa.writeRecord(qp, null);
                readPacketSkippingPing(ia, qp);
                Assert.assertEquals(Leader.LEADERINFO, qp.getType());
                Assert.assertEquals(ZxidUtils.makeZxid(1, 0), qp.getZxid());
                Assert.assertEquals(ByteBuffer.wrap(qp.getData()).getInt(),
                        0x10000);                
                Thread.sleep(l.self.getInitLimit()*l.self.getTickTime() + 5000);
                
                // The leader didn't get a quorum of acks - make sure that leader's current epoch is not advanced
                Assert.assertEquals(0, l.self.getCurrentEpoch());			
            }
        });
    }
    
    private void recursiveDelete(File file) {
        if (file.isFile()) {
            file.delete();
        } else {
            for(File c: file.listFiles()) {
                recursiveDelete(c);
            }
            file.delete();
        }
    }

    private Leader createLeader(File tmpDir, QuorumPeer peer)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        FileTxnSnapLog logFactory = new FileTxnSnapLog(tmpDir, tmpDir);
        peer.setTxnFactory(logFactory);
        Field addrField = peer.getClass().getDeclaredField("myQuorumAddr");
        addrField.setAccessible(true);
        addrField.set(peer, new InetSocketAddress(33556));
        ZKDatabase zkDb = new ZKDatabase(logFactory);
        LeaderZooKeeperServer zk = new LeaderZooKeeperServer(logFactory, peer, new ZooKeeperServer.BasicDataTreeBuilder(), zkDb);
        return new Leader(peer, zk);
    }

    static class ConversableFollower extends Follower {

        ConversableFollower(QuorumPeer self, FollowerZooKeeperServer zk) {
            super(self, zk);
        }

        InetSocketAddress leaderAddr;
        public void setLeaderSocketAddress(InetSocketAddress addr) {
            leaderAddr = addr;
        }
        
        @Override
        protected InetSocketAddress findLeader() {
            return leaderAddr;
        }
    }
    private ConversableFollower createFollower(File tmpDir, QuorumPeer peer)
    throws IOException {
        FileTxnSnapLog logFactory = new FileTxnSnapLog(tmpDir, tmpDir);
        peer.setTxnFactory(logFactory);
        ZKDatabase zkDb = new ZKDatabase(logFactory);
        FollowerZooKeeperServer zk = new FollowerZooKeeperServer(logFactory, peer, new ZooKeeperServer.BasicDataTreeBuilder(), zkDb);
        peer.setZKDatabase(zkDb);
        return new ConversableFollower(peer, zk);
    }

    private QuorumPeer createQuorumPeer(File tmpDir) throws IOException,
            FileNotFoundException {
        QuorumPeer peer = new QuorumPeer();
        peer.syncLimit = 2;
        peer.initLimit = 2;
        peer.tickTime = 2000;
        peer.quorumPeers = new HashMap<Long, QuorumServer>();
        peer.quorumPeers.put(1L, new QuorumServer(0, new InetSocketAddress(33221)));
        peer.quorumPeers.put(1L, new QuorumServer(1, new InetSocketAddress(33223)));
        peer.setQuorumVerifier(new QuorumMaj(3));
        peer.setCnxnFactory(new NullServerCnxnFactory());
        File version2 = new File(tmpDir, "version-2");
        version2.mkdir();
        new FileOutputStream(new File(version2, "currentEpoch")).write("0\n".getBytes());
        new FileOutputStream(new File(version2, "acceptedEpoch")).write("0\n".getBytes());
        return peer;
    }
}
