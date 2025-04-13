package no.hvl.dat110.util;

/**
 * exercise/demo purpose in dat110
 * @author tdoy
 *
 */

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {


	public static BigInteger hashOf(String entity) {

		BigInteger hashint = null;

		try {
			// Bruk MD5 for å hashe strengen
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] digest = md.digest(entity.getBytes());

			// Konverter byte-array til BigInteger (1 for å sikre at tallet er positivt)
			hashint = new BigInteger(1, digest);

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return hashint;
	}


	public static BigInteger addressSize() {

		// Task: compute the address size of MD5

		// compute the number of bits = bitSize()
		int bits = bitSize();

		// compute the address size = 2 ^ number of bits
		BigInteger addressSize = BigInteger.valueOf(2).pow(bits);

		// return the address size
		return addressSize;
	}


	public static int bitSize() {

		int digestlen = 0;

		try {
			// Hent MD5-instansen og finn lengden på digestet
			MessageDigest md = MessageDigest.getInstance("MD5");
			digestlen = md.getDigestLength(); // returnerer lengde i bytes (MD5 = 16)
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return digestlen * 8;  // 16 bytes * 8 = 128 bits
	}

	
	public static String toHex(byte[] digest) {
		StringBuilder strbuilder = new StringBuilder();
		for(byte b : digest) {
			strbuilder.append(String.format("%02x", b&0xff));
		}
		return strbuilder.toString();
	}

}
