package com.formreturn.license;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class License {

	public static final int USER_LICENSE = 0;
	public static final int TIMED_LICENSE = 1;
	public static final int SERVER_LICENSE = 2;

	// Primary key (json file name)
	public String activationCode = "";

	// current license replaced by an upgrade with code...
	public String replacedBy;

	// current license was upgraded from an existing license with code...
	public String upgradedFrom;

	// current license is a site license based on user license codes (comma separated)...
	public String siteLicense;

	public LicenseField[] defaults;

	public LicenseField firstNameDefaults = new LicenseField(
		"firstName",
		"First Name",
			null,
			null
	);
	public String firstName;

	public LicenseField lastNameDefaults = new LicenseField(
			"lastName",
			"Last Name",
			null,
			null
	);
	public String lastName;

	public LicenseField canonicalNameDefaults = new LicenseField(
			"canonicalName",
			"First Name and Last Name (Full Name - Required if no First/Last Name Given)",
			null,
			null
	);
	public String canonicalName;

	public LicenseField organizationDefaults = new LicenseField(
			"organization",
			"Organization",
			null,
			null
	);
	public String organization;

	public LicenseField streetDefaults = new LicenseField(
			"street",
			"Street Address",
			null,
			null
	);
	public String street;

	public LicenseField cityDefaults = new LicenseField(
			"city",
			"City",
			null,
			null
	);
	public String city;

	public LicenseField stateDefaults = new LicenseField(
			"state",
			"State/Province",
			null,
			null
	);
	public String state;

	public LicenseField countryDefaults = new LicenseField(
			"country",
			"Country Code",
			null,
			null
	);
	public String country;

	public LicenseField purchaseIdDefaults = new LicenseField(
			"purchaseId",
			"Purchase ID",
			null,
			null
	);
	public String purchaseId;


	public LicenseField emailDefaults = new LicenseField(
			"email",
			"E-mail Address",
			null,
			null
	);
	public String email;

	public LicenseField issuedDefaults = new LicenseField(
			"issued",
			"License Issued Date",
			new String[] {"yyyy-MM-dd"},
			null
	);
	public Date issued;

	public LicenseField expireDefaults = new LicenseField(
			"expire",
			"License Expiry Date",
			new String[] {"yyyy-MM-dd"},
			null
	);
	public Date expire;

	public int quantity = 1;

	public LicenseField licenseTypeDefaults = new LicenseField(
			"licenseType",
			"License Type",
			new String[] {"User", "Academic", "NFP"},
			"User"
	);
	public String licenseType = "User";

	public LicenseField certDefaults = new LicenseField(
			"cert",
			"Certificate Type",
			new String[] {USER_LICENSE + " (User License)", TIMED_LICENSE + " (Timed License)", SERVER_LICENSE + " (Service License)"},
			USER_LICENSE + ""
	);
	public int cert = USER_LICENSE;

	public String key;

	public License() {

		// set license field defaults
		this.defaults = new LicenseField[] {
				firstNameDefaults,
				lastNameDefaults,
				canonicalNameDefaults,
				organizationDefaults,
				streetDefaults,
				cityDefaults,
				stateDefaults,
				countryDefaults,
				purchaseIdDefaults,
				emailDefaults,
				issuedDefaults,
				expireDefaults,
				licenseTypeDefaults,
				certDefaults
		};

	}

	public String validateActivationCode(String code) throws Exception {
		String[] parts = code.split("-");
		if (parts.length != 5) {
			throw new Exception("Activation code not five parts long");
		}
		for (int i = 0; i < 5; i++) {
			if (parts[i].length() != 5 && i != 4) {
				throw new Exception("Activation code part " + (i + 1) + " not 5 characters long");
			}
			if (parts[i].length() != 6 && i == 4) {
				throw new Exception("Activation code part " + (i + 1) + " not 6 characters long");
			}
			for (int j = 0; j < parts[i].length(); j++) {
				if (!Character.isLetterOrDigit(parts[i].charAt(j))) {
					throw new Exception("Activation code part " + (i + 1) + " position " + (j + 1) + " is not alpha-numeric");
				}
			}
		}
		return code;
	}

	public String upgradeActivationCode(License license) throws Exception {
		this.upgradedFrom = license.activationCode;
		this.licenseType = license.licenseType;
		this.cert = license.cert;
		this.quantity = license.quantity;
		this.generateActivationCode();
		license.replacedBy = this.activationCode;
		license.save();
		return this.activationCode;
	}

	public String generateActivationCode() throws Exception {
		this.activationCode = Misc.generateActivationCode();
		this.save();
		return this.activationCode;
	}

	public String generateSiteActivationCode(String codes) throws Exception {
		this.siteLicense = codes;
		this.activationCode = Misc.generateActivationCode();
		this.licenseType = "Site";
		this.save();
		return this.activationCode;
	}

	public void load(String activationCode) throws Exception {
		this.validateActivationCode(activationCode);
		File licenseFile = new File(Misc.getPath("licenses") + File.separator + activationCode + ".json");
		if (!licenseFile.exists()) {
			throw new Exception("Activation code not found");
		}
		String json = FileUtils.readFileToString(licenseFile, "UTF-8");
		this.fromJson(json);
	}

	@SuppressWarnings("all")
	public void save() throws Exception {

		HashMap<String, String> map = new HashMap<String, String>();

		// BASIC SETTINGS
		map.put("activationCode", this.activationCode);
		map.put("licenseType", this.licenseType);
		map.put("cert", this.cert + "");
		map.put("quantity", this.quantity + "");

		// UPGRADE SETTINGS
		if (this.replacedBy != null) {
			map.put("replacedBy", this.replacedBy);
		}
		if (this.upgradedFrom != null) {
			map.put("upgradedFrom", this.upgradedFrom);
		}

		// SITE LICENSE SETTINGS
		if (this.siteLicense != null) {
			map.put("siteLicense", this.siteLicense);
		}

		// REDEEM SETTINGS
		if (this.firstName != null) {
			map.put("firstName", this.firstName);
		}

		if (this.lastName != null) {
			map.put("lastName", this.lastName);
		}

		if (this.canonicalName != null) {
			map.put("canonicalName", this.canonicalName);
		}

		if (this.organization != null) {
			map.put("organization", this.organization);
		}

		if (this.street != null) {
			map.put("street", this.street);
		}

		if (this.city != null) {
			map.put("city", this.city);
		}

		if (this.state != null) {
			map.put("state", this.state);
		}

		if (this.country != null) {
			map.put("country", this.country);
		}

		if (this.purchaseId != null) {
			map.put("purchaseId", this.purchaseId);
		}

		if (this.email != null) {
			map.put("email", this.email);
		}

		if (this.issued != null) {
			map.put("issued", DateFormatUtils.format(this.issued, "yyyy-MM-dd"));
		}

		if (this.expire != null) {
			map.put("expire", DateFormatUtils.format(this.expire, "yyyy-MM-dd"));
		}

		if (this.key != null) {
			map.put("key", this.key);
		}

		Gson gson = new Gson();
		String json = gson.toJson(map, Map.class);

		File licenseFile = new File(Misc.getPath("licenses") + File.separator + this.activationCode + ".json");
		FileUtils.writeStringToFile(licenseFile, json, "UTF-8");
	}

	public Date getDate(String dateStr) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		return formatter.parse(dateStr);
	}

	public void fromMap(Map map) throws Exception {

		if (map.containsKey("activationCode")) {
			this.activationCode = this.validateActivationCode((String) map.get("activationCode"));
		}

		// UPGRADE SETTINGS
		if (map.containsKey("replacedBy")) {
			this.replacedBy = (String) map.get("replacedBy");
		}
		if (map.containsKey("upgradedFrom")) {
			this.upgradedFrom = (String) map.get("upgradedFrom");
		}

		// SITE LICENSE SETTINGS
		if (map.containsKey("siteLicense")) {
			this.siteLicense = (String) map.get("siteLicense");
		}

		if (map.containsKey("firstName")) {
			this.firstName = (String) map.get("firstName");
		}

		if (map.containsKey("lastName")) {
			this.lastName = (String) map.get("lastName");
		}

		if (map.containsKey("canonicalName")) {
			this.canonicalName = (String) map.get("canonicalName");
		}

		if (map.containsKey("cert")) {
			this.cert = Integer.parseInt((String) map.get("cert"));
		} else {
			this.cert = License.USER_LICENSE;
		}

		if (map.containsKey("licenseType")) {
			this.licenseType = (String) map.get("licenseType");
		} else {
			this.licenseType = "User";
		}

		if (map.containsKey("organization")) {
			this.organization = (String) map.get("organization");
		}

		if (map.containsKey("street")) {
			this.street = (String) map.get("street");
		}

		if (map.containsKey("city")) {
			this.city = (String) map.get("city");
		}

		if (map.containsKey("state")) {
			this.state = (String) map.get("state");
		}

		if (map.containsKey("country")) {
			this.country = (String) map.get("country");
		}

		if (map.containsKey("purchaseId")) {
			this.purchaseId = (String) map.get("purchaseId");
		} else if (this.activationCode.length() > 0) {
			this.purchaseId = Misc.getMD5Sum(this.activationCode);
		}

		if (map.containsKey("email")) {
			this.email = (String) map.get("email");
		}

		if (map.containsKey("issued")) {
			this.issued = getDate((String) map.get("issued"));
		} else {
			this.issued = new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
		}

		if (map.containsKey("expire")) {
			this.expire = getDate((String) map.get("expire"));
		}

		if (map.containsKey("quantity")) {
			this.quantity = Integer.parseInt((String) map.get("quantity"));
		} else {
			this.quantity = 1;
		}

		if (map.containsKey("key")) {
			this.key = (String) map.get("key");
		}

	}

	public void fromJson(String json) throws Exception {
		Map map = new Gson().fromJson(json, Map.class);
		this.fromMap(map);
	}

}
