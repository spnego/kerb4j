package com.kerb4j.client;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;

import javax.security.auth.Subject;
import java.io.Closeable;
import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;

public class SpnegoContext implements Closeable {

    private static final byte[] EMPTY_BYTE = new byte[0];

    private final SpnegoClient spnegoClient;
    private final GSSContext gssContext; // TODO: how it should be renewed ?

    public SpnegoContext(SpnegoClient spnegoClient, GSSContext gssContext) {
        this.spnegoClient = spnegoClient;
        this.gssContext = gssContext;
    }

    public void requestCredentialsDelegation() throws GSSException {
        gssContext.requestCredDeleg(true);
    }

    public byte[] createToken() throws PrivilegedActionException {
        return Subject.doAs(spnegoClient.getSubject(), (PrivilegedExceptionAction<byte[]>) () ->
                gssContext.initSecContext(EMPTY_BYTE, 0, 0)
        );
    }

    public String createTokenAsAuthroizationHeader() throws PrivilegedActionException {
        return "Negotiate " + Base64.getEncoder().encodeToString(createToken());
    }

    public void processMutualAuthorization(byte[] data, int offset, int length) throws PrivilegedActionException {
        Subject.doAs(spnegoClient.getSubject(), (PrivilegedExceptionAction<byte[]>) () ->
                gssContext.initSecContext(data, offset, length)
        );
    }

    public boolean isEstablished() {
        return gssContext.isEstablished();
    }

    public void close() throws IOException {
        try {
            gssContext.dispose();
        } catch (GSSException e) {
            throw new IOException(e);
        }
    }



}
