package com.formreturn.license;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Scanner;

public final class Main {

	private static Main instance;

	public static void main(String[] args) {
		Main.instance = new Main();
		if (args.length > 0) {
			for (String arg: args) {
				File file = new File(arg);
				if (file.exists() && file.canRead()) {
					try {
						Main.instance.importLicenses(file);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		} else {
			Main.instance.init();
		}
	}

	private void importLicenses(File importFile) throws Exception {
		String json = FileUtils.readFileToString(importFile, "UTF-8");
		LinkedTreeMap[] items = new Gson().fromJson(json, LinkedTreeMap[].class);
		for (LinkedTreeMap map: items) {
			if (map == null) {
				continue;
			}
			createLicenseFromImportMap(map);
		}
	}

	private void createLicenseFromImportMap(LinkedTreeMap map) throws Exception {
		License license = new License();
		license.activationCode = (String) map.get("couponCode");

		if (map.containsKey("certType")) {
			license.cert = Integer.parseInt((String) map.get("certType"));
		}

		if (map.containsKey("licenseType")) {
			license.licenseType = (String) map.get("licenseType");
		}

		if (map.containsKey("firstname")) {
			license.firstName = (String) map.get("firstname");
		}

		if (map.containsKey("lastname")) {
			license.lastName = (String) map.get("lastname");
		}

		if (map.containsKey("organization")) {
			license.organization = (String) map.get("organization");
		}

		if (map.containsKey("city")) {
			license.city = (String) map.get("city");
		}

		if (map.containsKey("state")) {
			license.state = (String) map.get("state");
		}

		if (map.containsKey("country")) {
			license.country = (String) map.get("country");
		}

		if (map.containsKey("orderRef")) {
			license.purchaseId = (String) map.get("orderRef");
		}

		if (map.containsKey("email")) {
			license.email = (String) map.get("email");
		}

		if (map.containsKey("redeemed")) {
			try {
				license.issued = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse((String) map.get("redeemed"));
			} catch (ParseException pex) {
				pex.printStackTrace();
			}
		}

		license.save();
	}

	private void init() {
		String command;
		try {
			do {
				System.out.print(Main.getPrompt());
				command = Main.getCommand();
				if (command.trim().equals("l")) {
					System.out.println(this.createLicense() + "\n\n");
				} else if (command.trim().equals("c")) {
					System.out.println(this.createActivationCode() + "\n\n");
				} else if (command.trim().equals("u")) {
					System.out.println(this.upgradeActivationCode() + "\n\n");
				} else if (command.trim().equals("v")) {
					System.out.println(this.validateUpgradability() + "\n\n");
				} else if (command.trim().equals("s")) {
					System.out.println(this.createSiteLicenseCode() + "\n\n");
				} else if (command.trim().equals("r")) {
					System.out.println(this.redeemActivationCode() + "\n\n");
				}
			} while (command.trim().length() > 0);
		} catch (InterruptedException iex) {
			System.exit(0);
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
		}
	}

	@SuppressWarnings("all")
	private String createActivationCode() throws Exception {
		Scanner scan = new Scanner(System.in);
		License license = new License();

		// Prompt to ask for type of license (User, Academic, NFP)
		System.out.print(license.licenseTypeDefaults.description + ": ");
		if (license.licenseTypeDefaults.values != null) {
			System.out.println("Options: " + StringUtils.join(license.licenseTypeDefaults.values, ", "));
		}
		license.licenseType = scan.nextLine().trim() + "";
		if (license.licenseType.trim().length() == 0) {
			license.licenseType = license.licenseTypeDefaults.defaultValue;
		}

		// Prompt for how many to create
		System.out.println("How Many? (1-500): ");
		String s = scan.nextLine().trim();
		int count = 1;
		if (s.length() > 0) {
			count = Integer.parseInt(s);
			if (!(count > 0 && count <= 500)) {
				count = 1;
			}
		}

		String codes = "";

		for (int i = 0; i < count; i++) {
			codes += license.generateActivationCode() + "\n";
		}

		return codes;
	}

	private String createSiteLicenseCode() throws Exception {
		System.out.print("Enter 5 activation codes (comma separated): ");
		Scanner scan = new Scanner(System.in);
		String codes = scan.nextLine().trim();
		License license = new License();
		return license.generateSiteActivationCode(codes);
	}

	private String redeemActivationCode() throws Exception {
		Scanner scan = new Scanner(System.in);
		License license = new License();
		HashMap<String, String> licenseMap = new HashMap<String, String>();
		this.inputLicenseInfo(license, licenseMap);
		license.fromMap(licenseMap);
		System.out.print("Enter an activation code: ");
		license.load(scan.nextLine().trim() + "");
		if (license.key == null) {
			LicenseGenerator generator = new LicenseGenerator(license);
			String licenseCode = generator.generateLicense();
			license.save();
			return licenseCode;
		} else {
			return license.key;
		}
	}


	public String validateUpgradability() throws Exception {
		System.out.print("Enter activation code: ");
		Scanner scan = new Scanner(System.in);
		try {
			if (this.isActivationCodeUpgradeable(scan.nextLine().trim().toUpperCase())) {
				return "Activation code is upgradable.";
			} else {
				return "Cannot upgrade activation code.";
			}
		} catch (Exception ex) {
			return "Activation code not found.";
		}
	}

	private boolean isActivationCodeUpgradeable(String activationCode) throws Exception {
		License license = new License();
		license.load(activationCode);
		return license.replacedBy == null;
	}

	private String upgradeActivationCode() throws Exception {
		Scanner scan = new Scanner(System.in);
		License license = new License();
		System.out.print("Enter an activation code to upgrade: ");
		license.load(scan.nextLine().trim() + "");
		License upgrade = new License();
		return upgrade.upgradeActivationCode(license);
	}

	@SuppressWarnings("all")
	private void inputLicenseInfo(License license, HashMap<String, String> licenseMap) {
		for (int i = 0; i < license.defaults.length; i++) {
			LicenseField field = license.defaults[i];
			System.out.print(field.description + ": ");
			if (field.values != null) {
				System.out.println("Options: " + StringUtils.join(field.values, ", "));
			}
			Scanner scan = new Scanner(System.in);
			String s = scan.nextLine().trim();
			if (s.length() == 0 && field.defaultValue != null) {
				licenseMap.put(field.fieldname, field.defaultValue);
			} else if (s.length() > 0) {
				licenseMap.put(field.fieldname, s);	
			}
		}
	}

	private String createLicense() throws Exception {
		HashMap<String, String> licenseMap = new HashMap<String, String>();
		License license = new License();
		this.inputLicenseInfo(license, licenseMap);
		license.fromMap(licenseMap);
		LicenseGenerator generator = new LicenseGenerator(license);
		return generator.generateLicense();
	}

	private static String getPrompt() {
		return "What would you like to do?\n" +
				"l - Create License\n" +
				"c - Create Activation Code\n" +
				"u - Create Upgrade Activation Code\n" +
				"v - Validate Upgradability\n" +
				"s - Create Site License Activation Code\n" +
				"r - Redeem Activation Code\n" +
				"q - quit\n";
	}

	private static String getCommand() throws InterruptedException {
		Scanner scan = new Scanner(System.in);
		String s = scan.nextLine();
		if (s.toLowerCase().trim().equals("q")) {
			throw new InterruptedException();
		}
		return s;
	}

}
