/**
 * TestVerifyCredential.java
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) Wouter Lueks, Radboud University Nijmegen, July 2012.
 */

package org.irmacard.credentials.idemix;

import static org.junit.Assert.fail;

import java.io.File;
import java.math.BigInteger;
import java.net.URI;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;

import net.sourceforge.scuba.smartcards.CardService;
import net.sourceforge.scuba.smartcards.CardServiceException;
import net.sourceforge.scuba.smartcards.ProtocolCommands;
import net.sourceforge.scuba.smartcards.ProtocolResponses;
import net.sourceforge.scuba.smartcards.TerminalCardService;

import org.irmacard.credentials.Attributes;
import org.irmacard.credentials.CredentialsException;
import org.irmacard.credentials.Nonce;
import org.irmacard.credentials.idemix.spec.IdemixVerifySpecification;
import org.irmacard.credentials.idemix.util.CredentialInformation;
import org.irmacard.credentials.idemix.util.VerifyCredentialInformation;
import org.irmacard.credentials.info.DescriptionStore;
import org.irmacard.credentials.info.InfoException;
import org.irmacard.idemix.IdemixService;
import org.irmacard.idemix.IdemixSmartcard;
import org.junit.Before;
import org.junit.Test;

import com.ibm.zurich.idmx.showproof.Proof;
import com.ibm.zurich.idmx.showproof.ProofSpec;
import com.ibm.zurich.idmx.showproof.Verifier;
import com.ibm.zurich.idmx.utils.SystemParameters;


public class TestVerifyCredential {
	
	@Before
	public void setupIdemix() {
    	TestSetup.setupIssuer();
    	TestSetup.setupCredentialStructure();
    	TestSetup.setupIssuanceSpec();
	}

	@Test
	public void verifyCredentialWithoutAPI() {
        // load the proof specification
        ProofSpec spec = TestSetup.setupProofSpec();
        System.out.println(spec.toStringPretty());

        SystemParameters sp = spec.getGroupParams().getSystemParams();

        // first get the nonce (done by the verifier)
        System.out.println("Getting nonce.");
        BigInteger nonce = Verifier.getNonce(sp);

        IdemixService prover = null;
        try {
            CardTerminal terminal = TerminalFactory.getDefault().terminals().list().get(0);
			prover = new IdemixService(new TerminalCardService(terminal), TestSetup.CRED_NR);
            prover.open();
        } catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
        }

        // create the proof
        Proof p = prover.buildProof(nonce, spec);

        // now p is sent to the verifier
        Verifier verifier = new Verifier(spec, p, nonce);
        if (!verifier.verify()) {
            fail("The proof does not verify");
        } else {
            System.out.println("Proof verified");
        }
	}

	@Test
	public void verifyCredentialWithCardService() throws CredentialsException, CardException {
		IdemixVerifySpecification vspec = IdemixVerifySpecification
				.fromIdemixProofSpec(TestSetup.PROOF_SPEC_LOCATION, TestSetup.CRED_NR);

		CardService cs = TestSetup.getCardService();

		IdemixCredentials ic = new IdemixCredentials(cs);

		Attributes attr = ic.verify(vspec);

		if (attr == null) {
			fail("The proof does not verify");
		} else {
			System.out.println("Proof verified");
		}
	}

	@Test
	public void verifyCredentialAsync() throws CredentialsException, CardException, CardServiceException {
		IdemixCredentials ic = new IdemixCredentials(null);

		IdemixVerifySpecification vspec = IdemixVerifySpecification
				.fromIdemixProofSpec(TestSetup.PROOF_SPEC_LOCATION, TestSetup.CRED_NR);

		IdemixService service = TestSetup.getIdemixService();
		service.open();

		System.out.println("Running ASync test now");
		Nonce nonce = ic.generateNonce(vspec);
		ProtocolCommands commands = ic.requestProofCommands(vspec, nonce);
		// FIXME: verify that this actually helps
		commands.add(0, IdemixSmartcard.selectApplicationCommand);
		ProtocolResponses responses = service.execute(commands);
		Attributes attr = ic.verifyProofResponses(vspec, nonce, responses);

		if (attr == null) {
			fail("The proof does not verify");
		} else {
			System.out.println("Proof verified");
		}
	}

	@Test
	public void doubleVerify() throws CardException, CredentialsException, InfoException, CardServiceException {
		URI core = new File(System
				.getProperty("user.dir")).toURI()
				.resolve("irma_configuration/");
		CredentialInformation.setCoreLocation(core);
		DescriptionStore.setCoreLocation(core);
		DescriptionStore.getInstance();

		VerifyCredentialInformation vci1 = new VerifyCredentialInformation(
				"IRMATube", "memberType");
		IdemixVerifySpecification vspec1 = vci1.getIdemixVerifySpecification();
		VerifyCredentialInformation vci2 = new VerifyCredentialInformation(
				"IRMATube", "ageLowerOver16");
		IdemixVerifySpecification vspec2 = vci2.getIdemixVerifySpecification();

		IdemixService service = TestSetup.getIdemixService();
		IdemixCredentials ic = new IdemixCredentials(null);
		service.open();

		Nonce nonce1 = ic.generateNonce(vspec1);
		ProtocolCommands commands = ic.requestProofCommands(vspec1, nonce1);
		Nonce nonce2 = ic.generateNonce(vspec2);
		ProtocolCommands commands2 = ic.requestProofCommands(vspec2, nonce2);

		commands.add(0, IdemixSmartcard.selectApplicationCommand);
		commands.addAll(commands2);
		service.execute(commands);
	}


}
