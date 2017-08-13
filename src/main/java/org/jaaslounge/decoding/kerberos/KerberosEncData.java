package org.jaaslounge.decoding.kerberos;

import org.apache.kerby.kerberos.kerb.KrbCodec;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.crypto.EncryptionHandler;
import org.apache.kerby.kerberos.kerb.type.base.KeyUsage;
import org.apache.kerby.kerberos.kerb.type.ticket.EncTicketPart;
import org.bouncycastle.asn1.*;
import org.jaaslounge.decoding.DecodingException;
import org.jaaslounge.decoding.DecodingUtil;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

// TODO: stop using org.apache.directory.server.kerberos.
public class KerberosEncData {

    private String userRealm;
    private String userPrincipalName;
    private ArrayList<InetAddress> userAddresses;
    private List<KerberosAuthData> userAuthorizations;

    public KerberosEncData(byte[] token, Key key) throws DecodingException {
        ASN1InputStream stream = new ASN1InputStream(new ByteArrayInputStream(token));
        DERApplicationSpecific derToken;
        try {
            derToken = DecodingUtil.as(DERApplicationSpecific.class, stream);
            if(!derToken.isConstructed())
                throw new DecodingException("kerberos.ticket.malformed", null, null);
            stream.close();
        } catch(IOException e) {
            throw new DecodingException("kerberos.ticket.malformed", null, e);
        }

        stream = new ASN1InputStream(new ByteArrayInputStream(derToken.getContents()));
        DERSequence sequence;
        try {
            sequence = DecodingUtil.as(DERSequence.class, stream);
            stream.close();
        } catch(IOException e) {
            throw new DecodingException("kerberos.ticket.malformed", null, e);
        }

        Enumeration<?> fields = sequence.getObjects();
        while(fields.hasMoreElements()) {
            ASN1TaggedObject tagged = DecodingUtil.as(ASN1TaggedObject.class, fields);

            switch (tagged.getTagNo()) {
            case 0: // Ticket Flags
                break;
            case 1: // Key
                break;
            case 2: // Realm
                DERGeneralString derRealm = DecodingUtil.as(DERGeneralString.class, tagged);
                userRealm = derRealm.getString();
                break;
            case 3: // Principal
                DERSequence principalSequence = DecodingUtil.as(DERSequence.class, tagged);
                DERSequence nameSequence = DecodingUtil.as(DERSequence.class, DecodingUtil.as(
                        DERTaggedObject.class, principalSequence, 1));

                StringBuilder nameBuilder = new StringBuilder();
                Enumeration<?> parts = nameSequence.getObjects();
                while(parts.hasMoreElements()) {
                    Object part = parts.nextElement();
                    DERGeneralString stringPart = DecodingUtil.as(DERGeneralString.class, part);
                    nameBuilder.append(stringPart.getString());
                    if(parts.hasMoreElements())
                        nameBuilder.append('/');
                }
                userPrincipalName = nameBuilder.toString();
                break;
            case 4: // Transited Encoding
                break;
            case 5: // Kerberos Time
                // DERGeneralizedTime derTime = KerberosUtil.readAs(tagged,
                // DERGeneralizedTime.class);
                break;
            case 6: // Kerberos Time
                // DERGeneralizedTime derTime = KerberosUtil.readAs(tagged,
                // DERGeneralizedTime.class);
                break;
            case 7: // Kerberos Time
                // DERGeneralizedTime derTime = KerberosUtil.readAs(tagged,
                // DERGeneralizedTime.class);
                break;
            case 8: // Kerberos Time
                // DERGeneralizedTime derTime = KerberosUtil.readAs(tagged,
                // DERGeneralizedTime.class);
                break;
            case 9: // Host Addresses
                DERSequence adressesSequence = DecodingUtil.as(DERSequence.class, tagged);
                Enumeration<?> adresses = adressesSequence.getObjects();
                while(adresses.hasMoreElements()) {
                    DERSequence addressSequence = DecodingUtil.as(DERSequence.class, adresses);
                    DERInteger addressType = DecodingUtil.as(DERInteger.class, addressSequence, 0);
                    DEROctetString addressOctets = DecodingUtil.as(DEROctetString.class,
                            addressSequence, 1);

                    userAddresses = new ArrayList<InetAddress>();
                    if(addressType.getValue().intValue() == KerberosConstants.AF_INTERNET) {
                        InetAddress userAddress = null;
                        try {
                            userAddress = InetAddress.getByAddress(addressOctets.getOctets());
                        } catch(UnknownHostException e) {}
                        userAddresses.add(userAddress);
                    }
                }
                break;
            case 10: // Authorization Data
                DERSequence authSequence = DecodingUtil.as(DERSequence.class, tagged);

                userAuthorizations = new ArrayList<KerberosAuthData>();
                Enumeration<?> authElements = authSequence.getObjects();
                while(authElements.hasMoreElements()) {
                    DERSequence authElement = DecodingUtil.as(DERSequence.class, authElements);
                    DERInteger authType = DecodingUtil.as(DERInteger.class, DecodingUtil.as(
                            DERTaggedObject.class, authElement, 0));
                    DEROctetString authData = DecodingUtil.as(DEROctetString.class, DecodingUtil
                            .as(DERTaggedObject.class, authElement, 1));

                    /*

                    // TODO: use snippet below to get PAC data using Kerby API

                    try {
                        AuthorizationDataEntry authorizationDataEntry = KrbCodec.decode(token, EncTicketPart.class).getAuthorizationData().getElements().get(0);
                        AuthorizationType authzType = authorizationDataEntry.getAuthzType();

                        if (AD_IF_RELEVANT == authzType) {
                            AuthorizationDataEntry authorizationDataEntry1 = authorizationDataEntry.getAuthzDataAs(AuthorizationData.class).getElements().get(0);
                            System.out.println(authorizationDataEntry1.getAuthzType());
                            System.out.println(Arrays.toString(authorizationDataEntry1.getAuthzData()));
                        } else if (AD_WIN2K_PAC == authzType) {
                            System.out.println(Arrays.toString(authorizationDataEntry.getAuthzData()));
                        }


                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                     */

                    userAuthorizations.addAll(KerberosAuthData.parse(
                            authType.getValue().intValue(), authData.getOctets(), key));
                }
                break;
            default:
                Object[] args = new Object[]{tagged.getTagNo()};
                throw new DecodingException("kerberos.field.invalid", args, null);
            }
        }
    }

    public static byte[] decrypt(byte[] data, Key key, int type) throws GeneralSecurityException {
        byte[] decrypt = null;

        if(type == KerberosConstants.RC4_ENC_TYPE)
        {
            byte[] code = DecodingUtil.asBytes(Cipher.DECRYPT_MODE);
            byte[] codeHmac = getHmac(code, key.getEncoded());

            byte[] dataChecksum = new byte[KerberosConstants.CHECKSUM_SIZE];
            System.arraycopy(data, 0, dataChecksum, 0, KerberosConstants.CHECKSUM_SIZE);

            byte[] dataHmac = getHmac(dataChecksum, codeHmac);
            SecretKeySpec dataKey = new SecretKeySpec(dataHmac, KerberosConstants.RC4_ALGORITHM);

            Cipher cipher = Cipher.getInstance(KerberosConstants.RC4_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, dataKey);

            int plainDataLength = data.length - KerberosConstants.CHECKSUM_SIZE;
            byte[] plainData = cipher.doFinal(data, KerberosConstants.CHECKSUM_SIZE,
                    plainDataLength);

            byte[] plainDataChecksum = getHmac(plainData, codeHmac);
            if(plainDataChecksum.length >= KerberosConstants.CHECKSUM_SIZE)
                for(int i = 0; i < KerberosConstants.CHECKSUM_SIZE; i++)
                    if(plainDataChecksum[i] != data[i])
                        throw new GeneralSecurityException("Checksum failed while decrypting.");

            int decryptLength = plainData.length - KerberosConstants.CONFOUNDER_SIZE;
            decrypt = new byte[decryptLength];
            System.arraycopy(plainData, KerberosConstants.CONFOUNDER_SIZE, decrypt, 0,
                    decryptLength);
        }
        else
        {
            try
            {
                decrypt = EncryptionHandler.getEncHandler(type).decrypt(data, key.getEncoded(), KeyUsage.KDC_REP_TICKET.getValue());
                EncTicketPart tgsRep = KrbCodec.decode(decrypt, EncTicketPart.class);
                System.out.println(tgsRep);
            } catch (KrbException e) {
                e.printStackTrace();
            }

        }
        return decrypt;
    }

    private static byte[] getHmac(byte[] data, byte[] key) throws GeneralSecurityException {
        Key macKey = new SecretKeySpec(key.clone(), KerberosConstants.HMAC_ALGORITHM);
        Mac mac = Mac.getInstance(KerberosConstants.HMAC_ALGORITHM);
        mac.init(macKey);

        return mac.doFinal(data);
    }

    public String getUserRealm() {
        return userRealm;
    }

    public String getUserPrincipalName() {
        return userPrincipalName;
    }

    public ArrayList<InetAddress> getUserAddresses() {
        return userAddresses;
    }

    public List<KerberosAuthData> getUserAuthorizations() {
        return userAuthorizations;
    }

}
