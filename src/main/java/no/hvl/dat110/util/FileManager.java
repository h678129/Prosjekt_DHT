package no.hvl.dat110.util;


/**
 * @author tdoy
 * dat110 - project 3
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.rpc.interfaces.NodeInterface;

public class FileManager {
	
	private static final Logger logger = LogManager.getLogger(FileManager.class);
	
	private BigInteger[] replicafiles;							// array stores replicated files for distribution to matching nodes
	private int numReplicas;									// let's assume each node manages nfiles (5 for now) - can be changed from the constructor
	private NodeInterface chordnode;
	private String filepath; 									// absolute filepath
	private String filename;									// only filename without path and extension
	private BigInteger hash;
	private byte[] bytesOfFile;
	private String sizeOfByte;
	
	private Set<Message> activeNodesforFile = null;
	
	public FileManager(NodeInterface chordnode) throws RemoteException {
		this.chordnode = chordnode;
	}
	
	public FileManager(NodeInterface chordnode, int N) throws RemoteException {
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}
	
	public FileManager(NodeInterface chordnode, String filepath, int N) throws RemoteException {
		this.filepath = filepath;
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}

	public void createReplicaFiles() {

		// Task: replicate file by appending index to filename and hash each one

		for (int i = 0; i < numReplicas; i++) {
			String replica = filename + i; // f.eks. test0, test1, ...
			BigInteger hash = Hash.hashOf(replica); // bruk MD5 fra Task 1
			replicafiles[i] = hash; // lagre i arrayet
		}
	}

	
    /**
     * 
     * @param bytesOfFile
     * @throws RemoteException 
     */
	public int distributeReplicastoPeers() throws RemoteException {
		Random rnd = new Random();
		int index = rnd.nextInt(Util.numReplicas);

		int counter = 0;

		createReplicaFiles();

		activeNodesforFile = new HashSet<>(); // reset the set

		for (int i = 0; i < numReplicas; i++) {
			BigInteger replicaHash = replicafiles[i];

			NodeInterface succ = chordnode.findSuccessor(replicaHash);

			if (succ != null) {
				succ.addKey(replicaHash);

				boolean isPrimary = (i == index);

				succ.saveFileContent(filename + i, replicaHash, bytesOfFile, isPrimary);

				// Fix: use getFilesMetadata to get the Message, then add to the Set<Message>
				Message metadata = succ.getFilesMetadata(replicaHash);
				if (metadata != null) {
					activeNodesforFile.add(metadata);
				}

				counter++;
			}
		}

		return counter;
	}


	
	/**
	 * 
	 * @param filename
	 * @return list of active nodes having the replicas of this file
	 * @throws RemoteException 
	 */
	public Set<Message> requestActiveNodesForFile(String filename) throws RemoteException {

		this.filename = filename;
		activeNodesforFile = new HashSet<>();

		// generate the N replicas from the filename
		createReplicaFiles();

		// iterate over the replicas of the file
		for (int i = 0; i < numReplicas; i++) {
			BigInteger replicaHash = replicafiles[i];

			// find the successor for each replica hash
			NodeInterface succ = chordnode.findSuccessor(replicaHash);

			if (succ != null) {
				// get metadata (if any) from the successor
				Message metadata = succ.getFilesMetadata(replicaHash);

				// add to the set of active nodes
				if (metadata != null) {
					activeNodesforFile.add(metadata);
				}
			}
		}

		return activeNodesforFile;
	}

	
	/**
	 * Find the primary server - Remote-Write Protocol
	 * @return 
	 */
	public NodeInterface findPrimaryOfItem() throws RemoteException {
		// get all nodes storing replicas of this file
		Set<Message> messages = requestActiveNodesForFile(filename);

		for (Message msg : messages) {
			if (msg.isPrimaryServer()) {
				// found the primary
				return Util.getProcessStub(msg.getNodeName(), msg.getPort());
			}
		}

		return null; // should never happen if a primary was assigned
	}


	
    /**
     * Read the content of a file and return the bytes
     * @throws IOException 
     * @throws NoSuchAlgorithmException 
     */
    public void readFile() throws IOException, NoSuchAlgorithmException {
    	
    	File f = new File(filepath);
    	
    	byte[] bytesOfFile = new byte[(int) f.length()];
    	
		FileInputStream fis = new FileInputStream(f);
        
        fis.read(bytesOfFile);
		fis.close();
		
		//set the values
		filename = f.getName().replace(".txt", "");		
		hash = Hash.hashOf(filename);
		this.bytesOfFile = bytesOfFile;
		double size = (double) bytesOfFile.length/1000;
		NumberFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		sizeOfByte = nf.format(size);
		
		logger.info("filename="+filename+" size="+sizeOfByte);
    	
    }
    
    public void printActivePeers() {
    	
    	activeNodesforFile.forEach(m -> {
    		String peer = m.getNodeName();
    		String id = m.getNodeID().toString();
    		String name = m.getNameOfFile();
    		String hash = m.getHashOfFile().toString();
    		int size = m.getBytesOfFile().length;
    		
    		logger.info(peer+": ID = "+id+" | filename = "+name+" | HashOfFile = "+hash+" | size ="+size);
    		
    	});
    }

	/**
	 * @return the numReplicas
	 */
	public int getNumReplicas() {
		return numReplicas;
	}
	
	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}
	/**
	 * @param filename the filename to set
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}
	/**
	 * @return the hash
	 */
	public BigInteger getHash() {
		return hash;
	}
	/**
	 * @param hash the hash to set
	 */
	public void setHash(BigInteger hash) {
		this.hash = hash;
	}
	/**
	 * @return the bytesOfFile
	 */ 
	public byte[] getBytesOfFile() {
		return bytesOfFile;
	}
	/**
	 * @param bytesOfFile the bytesOfFile to set
	 */
	public void setBytesOfFile(byte[] bytesOfFile) {
		this.bytesOfFile = bytesOfFile;
	}
	/**
	 * @return the size
	 */
	public String getSizeOfByte() {
		return sizeOfByte;
	}
	/**
	 * @param size the size to set
	 */
	public void setSizeOfByte(String sizeOfByte) {
		this.sizeOfByte = sizeOfByte;
	}

	/**
	 * @return the chordnode
	 */
	public NodeInterface getChordnode() {
		return chordnode;
	}

	/**
	 * @return the activeNodesforFile
	 */
	public Set<Message> getActiveNodesforFile() {
		return activeNodesforFile;
	}

	/**
	 * @return the replicafiles
	 */
	public BigInteger[] getReplicafiles() {
		return replicafiles;
	}

	/**
	 * @param filepath the filepath to set
	 */
	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}
}
