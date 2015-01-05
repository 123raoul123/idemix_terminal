/**
 * TestSetup.java
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

import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.smartcardio.CardException;

import net.sourceforge.scuba.smartcards.CardService;

import org.irmacard.credentials.idemix.smartcard.CardChangedListener;
import org.irmacard.credentials.idemix.smartcard.IRMACard;
import org.irmacard.credentials.idemix.smartcard.IRMACardHelper;
import org.irmacard.credentials.idemix.smartcard.SmartCardEmulatorService;
import org.irmacard.idemix.IdemixService;

public class TestSetup {
    /** Actual location of the files. */
	/** TODO: keep this in mind, do we need BASE_LOCATION to point to .../parameter/
	 *  to keep idemix-library happy, i.e. so that it can find gp.xml and sp.xml?
	 */
    public static final URI BASE_LOCATION = new File(
            System.getProperty("user.dir")).toURI().resolve("irma_configuration/RU/");

    /** Actual location of the public issuer-related files. */
    public static final URI ISSUER_LOCATION = BASE_LOCATION;

    /** URIs and locations for issuer */
    public static final URI ISSUER_SK_LOCATION = ISSUER_LOCATION.resolve("private/isk.xml");
    public static final URI ISSUER_PK_LOCATION = ISSUER_LOCATION.resolve("ipk.xml");

    /** Credential location */
    public static final String CRED_STRUCT_NAME = "studentCard";
    public static final URI CRED_STRUCT_LOCATION = BASE_LOCATION
            .resolve("Issues/studentCard/structure.xml");

    /** Proof specification location */
    public static final URI PROOF_SPEC_LOCATION = BASE_LOCATION
                            .resolve("Verifies/studentCardAll/specification.xml");

    /** Ids used within the test files to identify the elements. */
    public static URI BASE_ID = null;
    public static URI ISSUER_ID = null;
    public static URI CRED_STRUCT_ID = null;
    static {
        try {
            BASE_ID = new URI("http://www.irmacard.org/credentials/phase1/RU/");
            ISSUER_ID = new URI("http://www.irmacard.org/credentials/phase1/RU/");
            CRED_STRUCT_ID = new URI("http://www.irmacard.org/credentials/phase1/RU/" + CRED_STRUCT_NAME + "/structure.xml");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    /** The identifier of the credential on the smartcard */
    public static short CRED_NR = (short) 4;

    /** Attribute values */
    public static final BigInteger ATTRIBUTE_VALUE_1 = BigInteger.valueOf(1313);
    public static final BigInteger ATTRIBUTE_VALUE_2 = BigInteger.valueOf(1314);
    public static final BigInteger ATTRIBUTE_VALUE_3 = BigInteger.valueOf(1315);
    public static final BigInteger ATTRIBUTE_VALUE_4 = BigInteger.valueOf(1316);
    public static final BigInteger ATTRIBUTE_VALUE_5 = BigInteger.valueOf(1317);

    /**
     * Default PIN of card.
     */
    public static final byte[] DEFAULT_CRED_PIN = "0000".getBytes();
    public static final byte[] DEFAULT_CARD_PIN = "000000".getBytes();

    public static final String PATH = "card.json";

    static CardService cs = null;
    public static CardService getCardService() throws CardException {
    	if (cs == null) {
    		final Path path = Paths.get(System.getProperty("user.dir"), PATH);
    		IRMACard card = IRMACardHelper.loadState(path);
    		SmartCardEmulatorService emu = new SmartCardEmulatorService(card);
    		emu.addListener(new CardChangedListener() {
    			@Override
				public void cardChanged(IRMACard card) {
					IRMACardHelper.storeState(card, path);
				}
			});
    		cs = emu;
    	}
    	return cs;
//    	return new InteractiveConsoleCardService();
    	/*
    	List<CardTerminal> terminalList = TerminalFactory.getDefault().terminals().list();
    	if(!terminalList.isEmpty()) {
    		CardTerminal terminal = terminalList.get(0);
    		return new TerminalCardService(terminal);
    	} else {
    		throw new NoCardReaderFoundException("Couldn't find card reader");
    	}*/
    }

    public static IdemixService getIdemixService() throws CardException {
    	return new IdemixService(getCardService());
    }
}