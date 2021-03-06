/*
 * LicenseManager.java
 *
 * Created on 22. Februar 2005, 13:27
 */
/*
 * Copyright 2005 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schlichtherle.license;

import de.schlichtherle.util.ObfuscatedString;
import de.schlichtherle.xml.*;

import java.beans.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.prefs.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.security.auth.x500.X500Principal;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.codec.binary.Base64;

/**
 * This is the top level class which manages all licensing aspects like for
 * instance the creation, installation and verification of license keys.
 * The license manager knows how to install, verify and uninstall full and
 * trial licenses for a given subject and ensures the privacy of the license
 * content in its persistent form (i.e. the <i>license key</i>).
 * For signing, verifying and validating licenses, this class cooperates with
 * a {@link LicenseNotary}.
 * <p>
 * This class is designed to be thread safe.
 *
 * @author Christian Schlichtherle
 */
public class LicenseManager implements LicenseCreator, LicenseVerifier {

    /** The timeout for the license content cache. */
    private static final long TIMEOUT = 30 * 60 * 1000; // half an hour

    /** The key in the preferences used to store the license key. */
    private static final String PREFERENCES_KEY
            = new ObfuscatedString(new long[] {
        0xD65FA96737AE2CB5L, 0xE804D1A38CF9A413L
    }).toString(); /* => "license" */

    /**
     * The suffix for files which hold license certificates.
     */
    public static final String LICENSE_SUFFIX
            = new ObfuscatedString(new long[] {
        0x97187B3A07E79CEEL, 0x469144B7E0D475E2L
    }).toString(); /* => ".lic" - must be lowercase! */
    static {
        assert LICENSE_SUFFIX.equals(LICENSE_SUFFIX.toLowerCase()); // paranoid
    }

    private static final String PARAM = LicenseNotary.PARAM;

    private static final String SUBJECT = new ObfuscatedString(new long[] {
        0xA1CB7D9B4D5E81E4L, 0xD9500F23E58132B6L
    }).toString(); /* => "subject" */

    private static final String KEY_STORE_PARAM = new ObfuscatedString(new long[] {
        0x449C8CDCBA1A80CEL, 0x6FEE3A101634D30BL, 0xD9D7B61A44A2606CL
    }).toString(); /* => "keyStoreParam" */

    private static final String CIPHER_PARAM = new ObfuscatedString(new long[] {
        0xCD54DEE1845B54E4L, 0x1AC47C8C827054BCL, 0x16E53B3A590D62B6L
    }).toString(); /* => "cipherParam" */

    protected static final String CN = new ObfuscatedString(new long[] {
        0x636F59E1FF007F64L, 0xAC9CE58690A43DD0L
    }).toString(); /* => "CN=" */

    private static final String CN_USER = CN + Resources.getString(
            new ObfuscatedString(new long[] {
        0xF3BE4EA2CCDD7EADL, 0x5B6A9F59A1183108L
    }).toString()); /* => "user" */

    private static final String USER = new ObfuscatedString(new long[] {
        0x9F89522C9F6F4A13L, 0xFFDB7A316241AC79L
    }).toString(); /* => "User" */

    private static final String SYSTEM = new ObfuscatedString(new long[] {
        0xEC006BE1C1F75BD6L, 0x54D650CDD244774BL
    }).toString(); /* => "System" */

    private static final String EXC_INVALID_SUBJECT = new ObfuscatedString(new long[] {
        0x8029CDF4E32A76ECL, 0x56FA623D9AEE8C1L, 0x99E7882A708663ACL,
        0x5888C0D72E548FF4L
    }).toString(); /* => "exc.invalidSubject" */

    private static final String EXC_HOLDER_IS_NULL = new ObfuscatedString(new long[] {
        0x6339FEFCDFD84427L, 0x57A2FA0735E47CBEL, 0xED1D06E6EED72950L
    }).toString(); /* => "exc.holderIsNull" */

    private static final String EXC_ISSUER_IS_NULL = new ObfuscatedString(new long[] {
        0xD5E29AC879334756L, 0xF1F7421CD6A06536L, 0x5E086D6468FECBF2L
    }).toString(); /* => "exc.issuerIsNull" */

    private static final String EXC_ISSUED_IS_NULL = new ObfuscatedString(new long[] {
        0xAB8FF89F2DA6C32CL, 0x2A089A9CA80D970EL, 0xCF15F8842FCCD9D5L
    }).toString(); /* => "exc.issuedIsNull" */

    private static final String EXC_LICENSE_IS_NOT_YET_VALID = new ObfuscatedString(new long[] {
        0x4B6BB2804EE7DDB1L, 0xD0BB0A33A41543C5L, 0x5FCEC6DF3725CEE4L,
        0xA165775BBD625344L
    }).toString(); /* => "exc.licenseIsNotYetValid" */

    private static final String EXC_LICENSE_HAS_EXPIRED = new ObfuscatedString(new long[] {
        0xDE2B2A7ACD6DA6DL, 0x9EE12DDECB3D4C0DL, 0xB3CF760B522E8688L,
        0x316BD3E92C17CC40L
    }).toString(); /* => "exc.licenseHasExpired" */

    private static final String EXC_CONSUMER_TYPE_IS_NULL = new ObfuscatedString(new long[] {
        0xD29019F7B1D95C66L, 0xE859C44ACC3EB2FEL, 0xF041027C9003B031L,
        0x27E84AD8870D6063L
    }).toString(); /* => "exc.consumerTypeIsNull" */

    private static final String EXC_CONSUMER_TYPE_IS_NOT_USER = new ObfuscatedString(new long[] {
        0xCE99D49CE98D1E47L, 0x7A3BA300A7DFCEABL, 0x2D2E4B624AD7C4E0L,
        0x2C86A28A075E71C6L, 0x79BCB920E5FB351DL
    }).toString(); /* => "exc.consumerTypeIsNotUser" */

    private static final String EXC_CONSUMER_AMOUNT_IS_NOT_ONE = new ObfuscatedString(new long[] {
        0x5F20CBB98126BB0AL, 0xE8BB696B25D24011L, 0x435CC3AA7263BAE7L,
        0x9DA3066F501717E4L, 0x62FFA4899FBBA3F8L
    }).toString(); /* => "exc.consumerAmountIsNotOne" */

    private static final String EXC_CONSUMER_AMOUNT_IS_NOT_POSITIVE = new ObfuscatedString(new long[] {
        0xB14EB6259B4D7249L, 0xCD02F577511528D8L, 0x39B8CF1E258756DDL,
        0x67488F05891DF916L, 0x4256DE0CFFF62DCAL
    }).toString(); /* => "exc.consumerAmountIsNotPositive" */

    private static final String FILE_FILTER_DESCRIPTION = new ObfuscatedString(new long[] {
        0x2BDDE408C7B71604L, 0xDFCA7DA8912DE4C1L, 0xADA1FC1C1D5F1047L,
        0xD08EAA6CCDC342F3L
    }).toString(); /* => "fileFilter.description" */

    private static final String FILE_FILTER_SUFFIX = new ObfuscatedString(new long[] {
        0xA4BCC907D9FD1290L, 0x614A0A9015D3D8DDL
    }).toString(); /* => " (*.lic)" */

    /**
     * Returns midnight local time today.
     */
    protected static final Date midnight() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        
        return cal.getTime();
    }

    private LicenseParam param; // initialized by setLicenseParam() - should be accessed via getLicenseParam() only!

    //
    // Data computed and cached from the license configuration parameters.
    //

    private LicenseNotary notary; // lazy initialized

    private PrivacyGuard guard; // lazy initialized

    /** The cached certificate of the current license key. */
    private GenericCertificate certificate; // lazy initialized

    /** The time when the certificate was last set. */
    private long certificateTime; // lazy initialized

    /** A suitable file filter for the subject of this license manager. */
    private FileFilter fileFilter; // lazy initialized

    /** The preferences node used to store the license key. */
    private Preferences preferences; // lazy initialized

    /**
     * Creates a new License Manager.
     * <p>
     * <b>Warning:</b> The manager created by this constructor is <em>not</em>
     * valid and cannot be used unless {@link #setLicenseParam(LicenseParam)}
     * is called!
     */
    protected LicenseManager() {
    }

    /**
     * Creates a new License Manager.
     *
     * @param param The license configuration parameters
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @throws NullPointerException If the given parameter object does not
     *         obey the contract of its interface due to a <tt>null</tt>
     *         pointer.
     * @throws IllegalPasswordException If any password in the parameter object
     *         does not comply to the current policy.
     */
    public LicenseManager(LicenseParam param)
    throws  NullPointerException,
            IllegalPasswordException {
        setLicenseParam(param);
    }

    /**
     * Returns the license configuration parameters.
     */
    public LicenseParam getLicenseParam() {
        return param;
    }

    /**
     * Sets the license configuration parameters.
     * Calling this method resets the manager as if it had been
     * newly created.
     * Some plausibility checks are applied to the given parameter object
     * to ensure that it adheres to the contract of the parameter interfaces.
     *
     * @param param The license configuration parameters
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @throws NullPointerException If the given parameter object does not
     *         obey the contract of its interface due to a <tt>null</tt>
     *         pointer.
     * @throws IllegalPasswordException If any password in the parameter object
     *         does not comply to the current policy.
     */
    public synchronized void setLicenseParam(LicenseParam param)
    throws  NullPointerException,
            IllegalPasswordException {
        // Check parameters to implement fail-fast behaviour.
        if (param == null)
            throw new NullPointerException(PARAM);
        if (param.getSubject() == null)
            throw new NullPointerException(SUBJECT);
        //if (param.getPreferences() == null)
        //    throw new NullPointerException("preferences");
        if (param.getKeyStoreParam() == null)
            throw new NullPointerException(KEY_STORE_PARAM);
        final CipherParam cipherParam = param.getCipherParam();
        if (cipherParam == null)
            throw new NullPointerException(CIPHER_PARAM);
        Policy.getCurrent().checkPwd(cipherParam.getKeyPwd());
        
        this.param = param;
        notary = null;
        certificate = null;
        certificateTime = 0;
        fileFilter = null;
        preferences = null;
    }

    //
    // Methods for license contents.
    //

    /**
     * Initializes and validates the license content, creates a new signed
     * license certificate for it and compresses, encrypts and stores it to
     * the given file as a license key.
     * <p>
     * As a side effect, the given license <tt>content</tt> is initialized
     * with some reasonable defaults unless the respective properties have
     * already been set.
     *
     * @param content The license content
     *        - may <em>not</em> be <tt>null</tt>.
     * @param keyFile The file to save the license key to
     *        - may <em>not</em> be <tt>null</tt>.
     *        This should have a <tt>LICENSE_SUFFIX</tt>.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons. Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #store(LicenseContent, LicenseNotary, File)
     * @see #initialize(LicenseContent)
     * @see #validate(LicenseContent)
     */
    public final synchronized void store(
            LicenseContent content,
            File keyFile)
    throws Exception {
        store(content, getLicenseNotary(), keyFile);
    }

    /**
     * Initializes and validates the license content, creates a new signed
     * license certificate for it and compresses, encrypts and stores it to
     * the given file as a license key.
     * <p>
     * As a side effect, the given license <tt>content</tt> is initialized
     * with some reasonable defaults unless the respective properties have
     * already been set.
     *
     * @param content The license content
     *        - may <em>not</em> be <tt>null</tt>.
     * @param notary The license notary used to sign the license key
     *        - may <em>not</em> be <tt>null</tt>.
     * @param keyFile The file to save the license key to
     *        - may <em>not</em> be <tt>null</tt>.
     *        This should have a <tt>LICENSE_SUFFIX</tt>.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #initialize(LicenseContent)
     * @see #validate(LicenseContent)
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected synchronized void store(
            final LicenseContent content,
            final LicenseNotary notary,
            final File keyFile)
    throws Exception {
        storeLicenseKey(create(content, notary), keyFile);
    }

    /**
     * Initializes and validates the license content, creates a new signed
     * license certificate for it and compresses, encrypts and returns it
     * as a license key.
     * <p>
     * As a side effect, the given license <tt>content</tt> is initialized
     * with some reasonable defaults unless the respective properties have
     * already been set.
     *
     * @param content The license content
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @return The license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #create(LicenseContent, LicenseNotary)
     * @see #initialize(LicenseContent)
     * @see #validate(LicenseContent)
     */
    public final synchronized byte[] create(
            final LicenseContent content)
    throws Exception {
        return create(content, getLicenseNotary());
    }

    /**
     * Initializes and validates the license content, creates a new signed
     * license certificate for it and compresses, encrypts and returns it
     * as a license key.
     * <p>
     * As a side effect, the given license <tt>content</tt> is initialized
     * with some reasonable defaults unless the respective properties have
     * already been set.
     *
     * @param content The license content
     *        - may <em>not</em> be <tt>null</tt>.
     * @param notary The license notary used to sign the license key
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @return The license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #initialize(LicenseContent)
     * @see #validate(LicenseContent)
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected synchronized byte[] create(
            final LicenseContent content,
            final LicenseNotary notary)
    throws Exception {
        initialize(content);
        validate(content);
        final GenericCertificate certificate = notary.sign(content);
        final byte[] key = getPrivacyGuard().cert2key(certificate);

        return key;
    }

    /**
     * Loads, decrypts, decompresses, decodes and verifies the license key in
     * <tt>keyFile</tt>, validates its license content and installs it
     * as the current license key.
     *
     * @param keyFile The file to load the license key from
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @return A clone of the verified and validated content of the license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #install(File, LicenseNotary)
     * @see #validate(LicenseContent)
     */
    public final synchronized LicenseContent install(File keyFile)
    throws Exception {
        return install(keyFile, getLicenseNotary());
    }

    /**
     * Loads, decrypts, decompresses, decodes and verifies the license key in
     * <tt>keyFile</tt>, validates its license content and installs it
     * as the current license key.
     *
     * @param keyFile The file to load the license key from
     *        - may <em>not</em> be <tt>null</tt>.
     * @param notary The license notary used to verify the license key
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @return A clone of the verified and validated content of the license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #validate(LicenseContent)
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected synchronized LicenseContent install(
            final File keyFile,
            final LicenseNotary notary)
    throws Exception {
        return install(loadLicenseKey(keyFile), notary);
    }

    /**
     * Decrypts, decompresses, decodes and verifies the license key in
     * <tt>key</tt>, validates its license content and installs it
     * as the current license key.
     *
     * @param key The license key
     *        - may <em>not</em> be <tt>null</tt>.
     * @param notary The license notary used to verify the license key
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @return A clone of the verified and validated content of the license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #validate(LicenseContent)
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected synchronized LicenseContent install(
            final byte[] key,
            final LicenseNotary notary)
    throws Exception {
        final GenericCertificate certificate = getPrivacyGuard().key2cert(key);
        notary.verify(certificate);
        final LicenseContent content = (LicenseContent) certificate.getContent();
        validate(content);
        setLicenseKey(key);
        setCertificate(certificate);

        return content;
    }

    /**
     * Decrypts, decompresses, decodes and verifies the current license key,
     * validates its license content and returns it.
     *
     * @return A clone of the verified and validated content of the license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws NoLicenseInstalledException If no license key is installed.
     * @throws Exception An instance of a subclass of this class for various
     *         other reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #validate(LicenseContent)
     */
    public final synchronized LicenseContent verify()
    throws Exception {
        return verify(getLicenseNotary());
    }

    /**
     * Decrypts, decompresses, decodes and verifies the current license key,
     * validates its license content and returns it.
     *
     * @param notary The license notary used to verify the current license key
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @return A clone of the verified and validated content of the license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws NoLicenseInstalledException If no license key is installed.
     * @throws Exception An instance of a subclass of this class for various
     *         other reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #validate(LicenseContent)
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected synchronized LicenseContent verify(final LicenseNotary notary)
    throws Exception {
        GenericCertificate certificate = getCertificate();
        if (certificate != null)
            return (LicenseContent) certificate.getContent();

        // Load license key from preferences, 
        final byte[] key = getLicenseKey();
        if (key == null)
            throw new NoLicenseInstalledException(getLicenseParam().getSubject());
        certificate = getPrivacyGuard().key2cert(key);
        notary.verify(certificate);
        final LicenseContent content = (LicenseContent) certificate.getContent();
        validate(content);
        setCertificate(certificate);

        return content;
    }
    
    /**
     * Decrypts, decompresses, decodes and verifies the given license key,
     * validates its license content and returns it.
     *
     * @param key The license key
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @return A clone of the verified and validated content of the license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #validate(LicenseContent)
     */
    public final synchronized LicenseContent verify(final byte[] key)
    throws Exception {
        return verify(key, getLicenseNotary());
    }
    
    /**
     * Decrypts, decompresses, decodes and verifies the given license key,
     * validates its license content and returns it.
     *
     * @param key The license key
     *        - may <em>not</em> be <tt>null</tt>.
     * @param notary The license notary used to verify the license key
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @return A clone of the verified and validated content of the license key
     *         - <tt>null</tt> is never returned.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #validate(LicenseContent)
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected synchronized LicenseContent verify(
            final byte[] key,
            final LicenseNotary notary)
    throws Exception {
        final GenericCertificate certificate = getPrivacyGuard().key2cert(key);
        notary.verify(certificate);
        final LicenseContent content = (LicenseContent) certificate.getContent();
        validate(content);

        return content;
    }

    /**
     * Uninstalls the current license key.
     *
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     */
    public synchronized void uninstall()
    throws Exception {
        setLicenseKey(null);
        setCertificate(null);
    }

    /**
     * Initializes the given license <tt>content</tt> with some reasonable
     * defaults unless the respective properties have already been set.
     *
     * @see #validate(LicenseContent)
     */
    protected synchronized void initialize(final LicenseContent content) {
        if (content.getHolder() == null)
            content.setHolder(new X500Principal(CN_USER));
        if (content.getSubject() == null)
            content.setSubject(getLicenseParam().getSubject());
        if (content.getConsumerType() == null) {
            final Preferences prefs = getLicenseParam().getPreferences();
            if (prefs != null) {
                if (prefs.isUserNode())
                    content.setConsumerType(USER);
                else
                    content.setConsumerType(SYSTEM);
                content.setConsumerAmount(1);
            }
        }
        if (content.getIssuer() == null)
            content.setIssuer(new X500Principal(
                    CN + getLicenseParam().getSubject()));
        if (content.getIssued() == null)
            content.setIssued(new Date());
        if (content.getNotBefore() == null)
            content.setNotBefore(midnight());
    }

    /**
     * Validates the license content.
     * This method is called whenever a license certificate is created,
     * installed or verified.
     * <p>
     * Validation consists of the following plausability checks for the
     * properties of this class:
     * <p>
     * <ul>
     * <li>'subject' must match the subject required by the application
     *     via the {@link LicenseParam} interface.
     * <li>'holder', 'issuer' and 'issued' must be provided (i.e. not
     *     <tt>null</tt>).
     * <li>If 'notBefore' or 'notAfter' are provided, the current date and
     *     time must match their restrictions.
     * <li>'consumerType' must be provided and 'consumerAmount' must be
     *     positive. If a user preference node is provided in the license
     *     parameters, 'consumerType' must also match User (whereby case
     *     is ignored) and 'consumerAmount' must equal 1.
     * </ul>
     * <p>
     * If you need more or less rigid restrictions, you should override this
     * method in a subclass.
     * 
     * @param content The license content
     *        - may <em>not</em> be <tt>null</tt>.
     *
     * @throws NullPointerException If <tt>content</tt> is <tt>null</tt>.
     * @throws LicenseContentException If any validation test fails.
     *         Note that you should always use
     *         {@link Throwable#getLocalizedMessage()} to get a (possibly
     *         localized) meaningful detail message.
     *
     * @see #initialize(LicenseContent)
     */
    protected synchronized void validate(final LicenseContent content)
    throws LicenseContentException {
        final LicenseParam param = getLicenseParam();
        if (!param.getSubject().equals(content.getSubject()))
            throw new LicenseContentException(EXC_INVALID_SUBJECT);
        if (content.getHolder() == null)
            throw new LicenseContentException(EXC_HOLDER_IS_NULL);
        if (content.getIssuer() == null)
            throw new LicenseContentException(EXC_ISSUER_IS_NULL);
        if (content.getIssued() == null)
            throw new LicenseContentException(EXC_ISSUED_IS_NULL);
        final Date now = new Date();
        final Date notBefore = content.getNotBefore();
        if (notBefore != null && now.before(notBefore))
            throw new LicenseContentException(EXC_LICENSE_IS_NOT_YET_VALID);
        final Date notAfter = content.getNotAfter();
        if (notAfter != null && now.after(notAfter))
            throw new LicenseContentException(EXC_LICENSE_HAS_EXPIRED);
        final String consumerType = content.getConsumerType();
        if (consumerType == null)
            throw new LicenseContentException(EXC_CONSUMER_TYPE_IS_NULL);
        final Preferences prefs = param.getPreferences();
        if (prefs != null && prefs.isUserNode()) {
            // if (!USER.equalsIgnoreCase(consumerType))
            //    throw new LicenseContentException(EXC_CONSUMER_TYPE_IS_NOT_USER);
            // if (content.getConsumerAmount() != 1)
             //   throw new LicenseContentException(EXC_CONSUMER_AMOUNT_IS_NOT_ONE);
        } else {
            if (content.getConsumerAmount() <= 0)
                throw new LicenseContentException(EXC_CONSUMER_AMOUNT_IS_NOT_POSITIVE);
        }
    }

    //
    // Methods for license certificates.
    //

    /**
     * Returns the license certificate cached from the
     * last installation/verification of a license key
     * or <tt>null</tt> if there wasn't an installation/verification
     * or a timeout has occured.
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected /*synchronized*/ GenericCertificate getCertificate() {
        if (certificate != null
                && System.currentTimeMillis() < certificateTime + TIMEOUT)
            return certificate; // use cached certificate until timeout
        else
            return null;
    }

    /**
     * Sets the given license certificate as installed or verified.
     *
     * @param certificate The license certificate
     *        - may be <tt>null</tt> to clear.
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected synchronized void setCertificate(GenericCertificate certificate) {
        this.certificate = certificate;
        certificateTime = System.currentTimeMillis(); // set cache timeout
    }

    //
    // Methods for license keys.
    // Note that in contrast to the methods of the privacy guard,
    // the following methods may have side effects (preferences, file system).
    //

    /**
     * Returns the current license key.
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected /*synchronized*/ byte[] getLicenseKey() {
        return getLicenseParam().getPreferences().getByteArray(PREFERENCES_KEY, null);
    }

    /**
     * Installs the given license key as the current license key.
     * If <tt>key</tt> is <tt>null</tt>, the current license key gets
     * uninstalled (but the cached license certificate is not cleared).
     *
     * @deprecated <b>Experimental:</b> Methods marked with this note have
     *             been tested to be functional but may change or disappear
     *             at will in one of the next releases because they are still
     *             a topic for research on extended functionality.
     *             Most likely the methods will prevail however and this note
     *             will just vanish, so you may use them with a certain risk.
     */
    protected synchronized void setLicenseKey(final byte[] key) {
        final Preferences prefs = getLicenseParam().getPreferences();
        if (key != null)
            prefs.putByteArray(PREFERENCES_KEY, key);
        else
            prefs.remove(PREFERENCES_KEY);
    }

    /**
     * Stores the given license key to the given file.
     * 
     * @param key The license key
     *        - may <em>not</em> be <tt>null</tt>.
     * @param keyFile The file to save the license key to
     *        - may <em>not</em> be <tt>null</tt>.
     *        This should have a <tt>LICENSE_SUFFIX</tt>.
     * 
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     */
    protected static void storeLicenseKey(
            final byte[] key,
            final File keyFile)
    throws IOException {
        final OutputStream out = new FileOutputStream(keyFile);
        try {
            out.write(key);
        } finally {
            try { out.close(); }
            catch (IOException weDontCare) { }
        }
    }

    /**
     * Loads and returns the first megabyte of content from <tt>keyFile</tt>
     * as license key in a newly created byte array.
     * 
     * @param keyFile The file holding the license key
     *        - may <em>not</em> be <tt>null</tt>.
     * 
     * @throws Exception An instance of a subclass of this class for various
     *         reasons.
     */
    protected static byte[] loadLicenseKey(final File keyFile)
    throws IOException {
        // Allow max 1MB size files and let the decrypter detect a partial read
        final int size = Math.min((int) keyFile.length(), 1024 * 1024);
        final InputStream in = new FileInputStream(keyFile);
        final byte[] b = new byte[size];
        try {
            // Let the verifier detect a partial read as an error
            in.read(b);
        } finally {
            try { in.close(); }
            catch (IOException weDontCare) { }
        }
        
        // READ TO SEE IF IT IS BASE64 ENCODED AND DECODE
        if ( (b[b.length - 1] == '=' && b[b.length - 2] == '=') 
        	|| keyFile.getName().toLowerCase().endsWith(".asc")) {
            return Base64.decodeBase64(b);
        }
        
        return b;
    }

    //
    // Various stuff.
    //

    /**
     * Returns a license notary configured to use the keystore parameters
     * contained in the current license parameters
     * - <tt>null</tt> is never returned.
     */
    protected synchronized LicenseNotary getLicenseNotary() {
        if (notary == null)
            notary = new LicenseNotary(getLicenseParam().getKeyStoreParam());
        
        return notary;
    }

    /**
     * Returns a privacy guard configured to use the cipher parameters
     * contained in the current license parameters
     * - <tt>null</tt> is never returned.
     */
    protected synchronized PrivacyGuard getPrivacyGuard() {
        if (guard == null)
            guard = new PrivacyGuard(getLicenseParam().getCipherParam());
        
        return guard;
    }

    /**
     * Returns a suitable file filter for the subject of this license manager.
     * On Windows systems, the case of the suffix is ignored when browsing
     * directories.
     *
     * @return A valid <tt>FileFilter</tt>.
     */
    public synchronized FileFilter getFileFilter() {
        if (fileFilter != null)
            return fileFilter;
        
        final String description
                = Resources.getString(FILE_FILTER_DESCRIPTION,
                    getLicenseParam().getSubject());
        if (File.separatorChar == '\\') {
            fileFilter = new FileFilter() {
                public boolean accept(File f) {
                    return f.isDirectory()
                        || f.getPath().toLowerCase().endsWith(LICENSE_SUFFIX);
                }

                public String getDescription() {
                    return description + FILE_FILTER_SUFFIX;
                }
            };
        } else {
            fileFilter = new FileFilter() {
                public boolean accept(File f) {
                    return f.isDirectory()
                        || f.getPath().endsWith(LICENSE_SUFFIX);
                }

                public String getDescription() {
                    return description + FILE_FILTER_SUFFIX;
                }
            };
        }
        
        return fileFilter;
    }
}
