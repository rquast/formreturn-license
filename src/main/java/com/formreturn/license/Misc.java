package com.formreturn.license;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang3.RandomStringUtils;

public class Misc {

	public static String getMD5Sum(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] messageDigest = md.digest(input.getBytes());
			BigInteger number = new BigInteger(1, messageDigest);
			return number.toString(16);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static String getPath(String directory) {
		if (directory == null) {
			directory = "licenses";
		}
		String home = System.getProperty("user.home") + "";
		File path = new File(home + File.separator + directory);
		if (!path.exists()) {
			path.mkdirs();
		}
		return home + File.separator + directory;
	}

	public static String generateActivationCode() {
		String randomString = RandomStringUtils.randomAlphanumeric(30)
				.toUpperCase();
		StringBuffer sb = new StringBuffer();
		sb.append(randomString.substring(0, 5));
		sb.append('-');
		sb.append(randomString.substring(6, 11));
		sb.append('-');
		sb.append(randomString.substring(12, 17));
		sb.append('-');
		sb.append(randomString.substring(17, 22));
		sb.append('-');
		sb.append(randomString.substring(23, 29));

		// COLLISION CHECK
		// TODO: must check to see if that the activation code doesn't already exist first
		// if it does, create another code.

		return sb.toString();
	}

}
