package com.formreturn.license;

import javax.security.auth.x500.X500Principal;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringEscapeUtils;

import de.schlichtherle.license.DefaultCipherParam;
import de.schlichtherle.license.DefaultKeyStoreParam;
import de.schlichtherle.license.DefaultLicenseParam;
import de.schlichtherle.license.LicenseContent;
import de.schlichtherle.license.LicenseManager;
import de.schlichtherle.util.ObfuscatedString;

public class LicenseGenerator {

	/* "FormReturn Server" */
	private static final String SERVER_SUBJECT = new ObfuscatedString(
			new long[]{0xAA8E9BF5141B5EA6L, 0x330205F66657E666L,
					0x2986E7B8667A95DDL, 0x9CCB4BBEDBD2C4B8L}).toString();

	/* "FormReturn OMR Timed" */
	private static final String TIMED_SUBJECT = new ObfuscatedString(
			new long[]{0x354050D714C2BB02L, 0x43214750FDE2093EL,
					0x4D0F0DB9CBAAD3EBL, 0xB445AAC352B5B872L}).toString();

	/* "FormReturn OMR" */
	private static final String SUBJECT = new ObfuscatedString(new long[]{
			0x396BDEEF85427FD5L, 0x1277484037F66A8AL, 0x361BFFEDA50D277AL})
			.toString();

	/* "/com/ebstrada/formreturn/keystore/privateKeys.store" */
	private static final String KEYSTORE_RESOURCE = new ObfuscatedString(
			new long[]{0x7F7CFE0A268BB318L, 0xBFC2BD8EA598ACE9L,
					0xEADCE53C64382C07L, 0x1261FB5B92799574L,
					0x339F5832C79917C6L, 0xBF091508939E79D4L,
					0x1E1FD402982F1FD4L, 0xA16959CFE02591E0L}).toString();

	/* "1TcHPynWNVI68sandWLEIarG4SHSRxuLnMzQ6ZqytX0HcNrmT63xavXGasmNXEs" */
	private static final String KEYSTORE_STORE_PWD = new ObfuscatedString(
			new long[]{0x532563A82AE66B08L, 0xCACD2BF259414BD6L,
					0xF02279CE8EDF8502L, 0xFB36F7301CB0E1D1L,
					0x40C5CF6572312155L, 0x8D2CD59A8476FCAFL,
					0xCF9C705BF1C8095L, 0x5F46243D0AFCAACEL,
					0x526D236827033CECL}).toString();

	/* "tnROIsGQ68puRlwqIEYhIbgYHDsAVIybhpAvaO5xFod7tBNhBju5deXr2tyUsSR" */
	private static final String KEYSTORE_KEY_PWD = new ObfuscatedString(
			new long[]{0x62244AF9DC286766L, 0x83484AF4B7D7ED85L,
					0x9B19892A6CA6E1AEL, 0x4EF991B0AD143E93L,
					0xA280277A30E063E4L, 0x9C614F4A94CE4C1L,
					0xC60A6A9309523386L, 0x8A53C17BE274CC78L,
					0xDFCE1B2D2CB353D6L}).toString();

	/* "3Pv8aNFZdbkyT5vIm4eZzo6UVeOTJWKShh1Tt2qFNlY6blB4FiDY6g9RiswLqk1" */
	private static final String CIPHER_KEY_PWD = new ObfuscatedString(
			new long[]{0xF4020D83C8F8DF47L, 0x61B0FED124553447L,
					0x8E3D9F55CD80A7EFL, 0xC1FF9D4DF5E35B35L,
					0xAC2EDDDCB8609FL, 0xBE51643ABB639467L,
					0x1102E66CBBB68F2DL, 0xC49301FBDB95EE6BL,
					0x6560331D1EDFCFF7L}).toString();

	protected LicenseManager manager;

	private License license;

	public LicenseGenerator(License license) {
		this.license = license;
	}

	private String getIssuer() {
		return "O=EB Strada Pty Ltd,STREET=10 / 53 Bilyana Street,L=Balmoral,ST=Queensland,C=AU";
	}

	private String getSubject() {
		switch (this.license.cert) {
			case License.TIMED_LICENSE:
				return TIMED_SUBJECT;
			case License.SERVER_LICENSE:
				return SERVER_SUBJECT;
			case License.USER_LICENSE:
			default:
				return SUBJECT;
		}
	}

	public String generateLicense() throws Exception {

		manager = new LicenseManager(
				new DefaultLicenseParam(getSubject(), null, new DefaultKeyStoreParam(
						LicenseGenerator.class, KEYSTORE_RESOURCE, getSubject(),
						KEYSTORE_STORE_PWD, KEYSTORE_KEY_PWD),
						new DefaultCipherParam(CIPHER_KEY_PWD)));

		final StringBuffer dn = new StringBuffer();
		if (this.license.firstName != null && this.license.lastName != null) {
			addAttribute(dn, "CN", this.license.firstName.trim() + ' ' + this.license.lastName.trim());
		}
		if (dn.length() == 0 && this.license.canonicalName != null) {
			addAttribute(dn, "CN", this.license.canonicalName.trim());
		}
		if (this.license.organization != null) {
			addAttribute(dn, "O", this.license.organization.trim());
		}
		if (this.license.street != null) {
			addAttribute(dn, "STREET", this.license.street.trim());
		}
		if (this.license.city != null) {
			addAttribute(dn, "L", this.license.city.trim());
		}
		if (this.license.state != null) {
			addAttribute(dn, "ST", this.license.state.trim());
		}
		if (this.license.country != null) {
			addAttribute(dn, "C", this.license.country.trim());
		}
		if (this.license.purchaseId != null) {
			addAttribute(dn, "UID", this.license.purchaseId.trim());
		}

		final X500Principal holder = new X500Principal(dn.toString());

		final X500Principal issuer = new X500Principal(getIssuer());

		final LicenseContent content = new LicenseContent();
		content.setIssued(this.license.issued);
		if (this.license.expire != null) {
			content.setNotAfter(this.license.expire);
		}
		content.setHolder(holder);
		content.setIssuer(issuer);
		content.setConsumerType(this.license.licenseType);
		content.setConsumerAmount(this.license.quantity);

		byte[] key = manager.create(content);

		this.license.key = Base64.encodeBase64String(key);

		return this.license.key;

	}

	private static void addAttribute(
			final StringBuffer dn,
			final String oid,
			String value) {

		if (value == null || value.trim().length() <= 0) {
			return;
		}

		// See http://www.ietf.org/rfc/rfc2253.txt
		if (dn.length() != 0) {
			dn.append(',');
		}
		dn.append(oid);
		dn.append('=');
		// NOTE: this was deprecated when moved to commons.text
		dn.append(StringEscapeUtils.escapeCsv(value.trim()));
	}

}
