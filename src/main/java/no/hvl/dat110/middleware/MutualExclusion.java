/**
 * 
 */
package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class MutualExclusion {
		
	private static final Logger logger = LogManager.getLogger(MutualExclusion.class);
	/** lock variables */
	private boolean CS_BUSY = false;						// indicate to be in critical section (accessing a shared resource) 
	private boolean WANTS_TO_ENTER_CS = false;				// indicate to want to enter CS
	private List<Message> queueack; 						// queue for acknowledged messages
	private List<Message> mutexqueue;						// queue for storing process that are denied permission. We really don't need this for quorum-protocol
	
	private LamportClock clock;								// lamport clock
	private Node node;
	
	public MutualExclusion(Node node) throws RemoteException {
		this.node = node;
		
		clock = new LamportClock();
		queueack = new ArrayList<Message>();
		mutexqueue = new ArrayList<Message>();
	}
	
	public synchronized void acquireLock() {
		CS_BUSY = true;
	}
	
	public void releaseLocks() {
		WANTS_TO_ENTER_CS = false;
		CS_BUSY = false;
	}

	public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {

		logger.info(node.nodename + " wants to access CS");

		queueack.clear();        // Step 1: clear ack queue
		mutexqueue.clear();      // Step 2: clear mutex queue
		clock.increment();       // Step 3: increment Lamport clock
		message.setClock(clock.getClock());  // attach clock to message

		WANTS_TO_ENTER_CS = true;  // Step 4: set intent to enter CS

		// Step 5: run ME algorithm
		List<Message> activenodes = removeDuplicatePeersBeforeVoting();
		multicastMessage(message, activenodes);  // send REQUEST

		// Step 6: wait until all ACKs received
		while (!areAllMessagesReturned(activenodes.size())) {
			try {
				Thread.sleep(100);  // avoid busy waiting
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Step 7: enter critical section
		acquireLock();
		node.broadcastUpdatetoPeers(updates);
		mutexqueue.clear();
		releaseLocks();  // release own locks after update
		return true;
	}



	// multicast message to other processes including self
	private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {

		logger.info("Number of peers to vote = " + activenodes.size());

		for (Message m : activenodes) {
			try {
				NodeInterface stub = Util.getProcessStub(m.getNodeName(), m.getPort());
				if (stub != null) {
					stub.onMutexRequestReceived(message);  // remote request
				}
			} catch (Exception e) {
				logger.error("Error sending mutex request to " + m.getNodeName());
			}
		}
	}


	public void onMutexRequestReceived(Message message) throws RemoteException {
		clock.increment();  // logical clock tick

		if (message.getNodeID().equals(node.getNodeID())) {
			node.onMutexAcknowledgementReceived(message);  // self-request
			return;
		}

		int caseid = -1;

		if (!CS_BUSY && !WANTS_TO_ENTER_CS) {
			caseid = 0;
		} else if (CS_BUSY) {
			caseid = 1;
		} else if (WANTS_TO_ENTER_CS) {
			caseid = 2;
		}

		doDecisionAlgorithm(message, mutexqueue, caseid);
	}


	public void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {

		String procName = message.getNodeName();
		int port = message.getPort();
		NodeInterface stub = Util.getProcessStub(procName, port);

		switch (condition) {
			case 0: { // not accessing CS and not interested
				if (stub != null) {
					stub.onMutexAcknowledgementReceived(message);
				}
				break;
			}

			case 1: { // in CS — defer reply
				queue.add(message);
				break;
			}

			case 2: { // competing for CS — compare timestamps
				int senderClock = message.getClock();
				int ownClock = clock.getClock();

				if (senderClock < ownClock || (senderClock == ownClock && message.getNodeID().compareTo(node.getNodeID()) < 0)) {
					if (stub != null) {
						stub.onMutexAcknowledgementReceived(message);
					}
				} else {
					queue.add(message);  // sender loses, queue it
				}
				break;
			}

			default: break;
		}
	}


	public void onMutexAcknowledgementReceived(Message message) throws RemoteException {
		if (!queueack.contains(message)) {
			queueack.add(message);
		}
	}


	// multicast release locks message to other processes including self
	public void multicastReleaseLocks(Set<Message> activenodes) {

		logger.info("Releasing locks from = " + activenodes.size());

		for (Message m : activenodes) {
			try {
				NodeInterface stub = Util.getProcessStub(m.getNodeName(), m.getPort());
				if (stub != null) {
					stub.releaseLocks();  // notify peers
				}
			} catch (Exception e) {
				logger.error("Error releasing lock for " + m.getNodeName());
			}
		}
	}


	private boolean areAllMessagesReturned(int numvoters) {
		logger.info(node.getNodeName() + ": size of queueack = " + queueack.size());
		return queueack.size() == numvoters;
	}



	private List<Message> removeDuplicatePeersBeforeVoting() {
		
		List<Message> uniquepeer = new ArrayList<Message>();
		for(Message p : node.activenodesforfile) {
			boolean found = false;
			for(Message p1 : uniquepeer) {
				if(p.getNodeName().equals(p1.getNodeName())) {
					found = true;
					break;
				}
			}
			if(!found)
				uniquepeer.add(p);
		}		
		return uniquepeer;
	}
}
