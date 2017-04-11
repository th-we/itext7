package com.itextpdf.signatures.testutils.builder;

import com.itextpdf.io.util.DateTimeUtil;
import com.itextpdf.io.util.SystemUtil;
import com.itextpdf.signatures.DigestAlgorithms;
import java.io.IOException;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

public class TestTimestampTokenBuilder {
    private static final String SIGN_ALG = "SHA256withRSA";

    private List<Certificate> tsaCertificateChain;
    private PrivateKey tsaPrivateKey;

    public TestTimestampTokenBuilder(List<Certificate> tsaCertificateChain, PrivateKey tsaPrivateKey) {
        if (tsaCertificateChain.isEmpty()) {
            throw new IllegalArgumentException("tsaCertificateChain shall not be empty");
        }
        this.tsaCertificateChain = tsaCertificateChain;
        this.tsaPrivateKey = tsaPrivateKey;
    }

    public byte[] createTimeStampToken(TimeStampRequest request) throws OperatorCreationException, TSPException, IOException, CertificateEncodingException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALG).build(tsaPrivateKey);
        DigestCalculatorProvider digestCalcProviderProvider = new JcaDigestCalculatorProviderBuilder().build();

        SignerInfoGenerator siGen =
                new JcaSignerInfoGeneratorBuilder(digestCalcProviderProvider)
                        .build(signer, (X509Certificate) tsaCertificateChain.get(0));

        // just a more or less random oid of timestamp policy
        ASN1ObjectIdentifier policy = new ASN1ObjectIdentifier("1.3.6.1.4.1.45794.1.1");

        String digestForTsSigningCert = DigestAlgorithms.getAllowedDigest("SHA1");
        DigestCalculator dgCalc = digestCalcProviderProvider.get(new AlgorithmIdentifier(new ASN1ObjectIdentifier(digestForTsSigningCert)));
        TimeStampTokenGenerator tsTokGen = new TimeStampTokenGenerator(siGen, dgCalc, policy);
        tsTokGen.setAccuracySeconds(1);

        // TODO setting this is somewhat wrong. Acrobat and openssl recognize timestamp tokens generated with this line as corrupted
        // openssl error message: 2304:error:2F09506F:time stamp routines:INT_TS_RESP_VERIFY_TOKEN:tsa name mismatch:ts_rsp_verify.c:476:
//        tsTokGen.setTSA(new GeneralName(new X500Name(PrincipalUtil.getIssuerX509Principal(tsCertificate).getName())));

        tsTokGen.addCertificates(new JcaCertStore(tsaCertificateChain));

        // should be unique for every timestamp
        BigInteger serialNumber = new BigInteger(String.valueOf(SystemUtil.getSystemTimeMillis()));
        Date genTime = DateTimeUtil.getCurrentTimeDate();
        TimeStampToken tsToken = tsTokGen.generate(request, serialNumber, genTime);
        return tsToken.getEncoded();
    }
}