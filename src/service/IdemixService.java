/**
 * IdemixService.java
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
 * Copyright (C) Pim Vullers, Radboud University Nijmegen, June 2011.
 */

package service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Vector;


import net.sourceforge.scuba.smartcards.CardService;
import net.sourceforge.scuba.smartcards.CardServiceException;
import net.sourceforge.scuba.smartcards.CommandAPDU;
import net.sourceforge.scuba.smartcards.ResponseAPDU;
import net.sourceforge.scuba.util.Hex;

import com.ibm.zurich.idmx.api.ProverInterface;
import com.ibm.zurich.idmx.api.RecipientInterface;
import com.ibm.zurich.idmx.dm.Credential;
import com.ibm.zurich.idmx.dm.Values;
import com.ibm.zurich.idmx.dm.structure.AttributeStructure;
import com.ibm.zurich.idmx.issuance.IssuanceSpec;
import com.ibm.zurich.idmx.issuance.Message;
import com.ibm.zurich.idmx.showproof.Proof;
import com.ibm.zurich.idmx.showproof.ProofSpec;

/**
 * Idemix Smart Card Interface based on a SCUBA Card Service.
 *  
 * @author Pim Vullers
 * @version $Revision: 554 $ by $Author: pim $
 *          $LastChangedDate: 2011-04-28 16:31:47 +0200 (Thu, 28 Apr 2011) $
 */
public class IdemixService extends CardService 
implements ProverInterface, RecipientInterface {
	
    /**
     * Universal version identifier to match versions during deserialisation.
     */
    private static final long serialVersionUID = -6317383635196413L;

    /**
     * Control the amount of output generated by this class.
     */
    private static final boolean VERBOSE = true;
    
	/**
	 * SCUBA service to communicate with the card.
	 */
	protected CardService service;
	
	/**
	 * Credential issuance specification.
	 */
	protected IssuanceSpec issuanceSpec;
	
	/**
	 * Credential identifier.
	 */
	protected short credentialId;
	
	
    /**************************************************************************/
    /* SCUBA / Smart Card Setup                                               */
    /**************************************************************************/

    /**
     * Construct a new Idemix service based on some CardService, which will be
     * used for the actual communication with an Idemix applet.
     * 
     * @param service the service to use for communication with the applet.
     */
    public IdemixService(CardService service) {
        this.service = service;
    }

    /**
     * Construct a new Idemix service based on some CardService, which will be
     * used for the actual communication with an Idemix applet.
     * 
     * @param service the service to use for communication with the applet.
     * @param credential identifier.
     */    
	public IdemixService(CardService service, short credentialId) {
		this.service = service;
		this.credentialId = credentialId; 
	}
	
    /**
     * Open a communication channel to an Idemix applet.
     */
    public void open() 
    throws CardServiceException {
        if (!isOpen()) {
            service.open();
        }
        selectApplet();
    }

    /**
     * Check whether a communication channel with a smart card exists.
     * 
     * @return whether the channel is open or not.
     */
    public boolean isOpen() {
        return service.isOpen();
    }

    /**
     * Send an APDU over the communication channel to the smart card.
     * 
     * @param apdu the APDU to be send to the smart card.
     * @return ResponseAPDU the response from the smart card.
     * @throws CardServiceException if some error occurred while transmitting.
     */
    public ResponseAPDU transmit(CommandAPDU capdu) 
    throws CardServiceException { 

        if (VERBOSE) {
            System.out.println();
            System.out.println("C: " + Hex.bytesToHexString(capdu.getBytes()));    		
        }

        long start = System.nanoTime();
        ResponseAPDU rapdu = service.transmit(capdu);
        long duration = (System.nanoTime() - start)/1000000;

        if (VERBOSE) {
            System.out.println(" duration: " + duration + " ms");
            System.out.println("R: " + Hex.bytesToHexString(rapdu.getBytes()));
        }

        return rapdu;
    }

    /**
     * Close the communication channel with the Idemix applet.
     */
    public void close() {
        if (service != null) {
            service.close();
        }
    }
    
    /**
     * Execute a protocol command on the smart card.
     * 
     * @param command to be executed on the card.
     * @return the response received from the card.
     * @throws CardServiceException if an error occurred.
     */
    public ProtocolResponse execute(ProtocolCommand command)
    throws CardServiceException {
    	ResponseAPDU response = transmit(command.getAPDU());
    	
    	if (response.getSW() != 0x00009000) {
    		// don't bother with the rest of the commands...
    		throw new CardServiceException(String.format(
    				"Command failed: \"%s\", SW: %04x (%s)",
    				command.getDescription(), response.getSW(), 
    				command.getErrorMessage(response.getSW())));
    	}
    	
    	return new ProtocolResponse(command.getKey(), response);
    }
    
    /**
     * Execute a list of protocol commands on the smart card.
     * 
     * @param commands to be executed on the card.
     * @return the responses received from the card.
     * @throws CardServiceException if an error occurred.
     */
    public ProtocolResponses execute(ProtocolCommands commands) 
    throws CardServiceException {
    	ProtocolResponses responses = new ProtocolResponses();
        
    	for (ProtocolCommand command: commands) {
    		ProtocolResponse response = execute(command);
        	responses.put(response.getKey(), response);
        }
        
    	return responses;
    }
    
    /**
     * Set the credential to interact with.
     * 
     * @param identifier of the credential.
     */
    public void setCredential(short identifier) {
    	credentialId = identifier;
    }
    
    /**
     * Select the Idemix applet on the smart card.
     * 
     * @throws CardServiceException if an error occurred.
     */
    public void selectApplet() 
    throws CardServiceException {
    	execute(IdemixSmartcard.selectAppletCommand);
    }

    /**
     * Send the pin to the card.
     *
     * @throws CardServiceException if an error occurred.
     */
    public void sendPin(byte[] pin)
    throws CardServiceException {
        sendPin(IdemixSmartcard.PIN_CRED, pin);
    }

    /**
     * Send the pin to the card.
     *
     * @throws CardServiceException if an error occurred.
     */
    public void sendPin(byte pinID, byte[] pin)
    throws CardServiceException {
        execute(IdemixSmartcard.sendPinCommand(pinID, pin));
    }
    
    /**
	 * Update the pin on the card
	 *
	 * Note that to use this function one first needs to establish an
	 * authenticated connection to the card.
	 */
    public void updatePin(byte[] pin)
    throws CardServiceException {
    	updatePin(IdemixSmartcard.PIN_CRED, pin);
    }

    /**
	 * Update the pin on the card
	 *
	 * Note that to use this function one first needs to establish an
	 * authenticated connection to the card.
	 */
    public void updatePin(byte pinID, byte[] pin)
    throws CardServiceException {
    	execute(IdemixSmartcard.updatePinCommand(pinID, pin));
    }

    /**
     * Generate the master secret: 
     * 
     * <pre>
     *   m_0
     * </pre>
     * 
     * @throws CardServiceException if an error occurred.
     */
    public void generateMasterSecret() 
    throws CardServiceException {
    	execute(IdemixSmartcard.generateMasterSecretCommand);
    }
    
    /**
     * @param theNonce1
     *            Nonce provided by the verifier.
     * @return Message containing the proof about the hidden and committed
     *         attributes sent to the Issuer.
     */
    public Message round1(final Message msg) {
        // Hide CardServiceExceptions, instead return null on failure
        try {
        	ProtocolCommands commands = IdemixSmartcard.round1Commands(issuanceSpec, msg);
        	ProtocolResponses responses = execute(commands);
			return IdemixSmartcard.processRound1Responses(responses);

        // Report caught exceptions
        } catch (CardServiceException e) {
            System.err.println(e.getMessage() + "\n");
            e.printStackTrace();
            return null;
		}
    }
    
    
    /**
     * Called with the second protocol flow as input, outputs the Credential.
     * This is the last step of the issuance protocol, where the Recipient
     * verifies that the signature is valid and outputs it.
     * 
     * @param msg
     *            the second flow of the protocol, a message from the Issuer
     * @return null
     */
    public Credential round3(final Message msg) {
        // Hide CardServiceExceptions, instead return null on failure
        try {
            // send Signature
        	execute(IdemixSmartcard.round3Commands(issuanceSpec, msg));
            
            // Do NOT return the generated Idemix credential
            return null;
            
        // Report caught exceptions
        } catch (CardServiceException e) {
            System.err.println(e.getMessage() + "\n");
            e.printStackTrace();
            return null;
        }
    }    
    
    /**
     * Builds an Identity mixer show-proof data structure, which can be passed
     * to the verifier for verification.
     * 
     * @return Identity mixer show-proof data structure.
     */
    public Proof buildProof(final BigInteger nonce, final ProofSpec spec) {
        // Hide CardServiceExceptions, instead return null on failure
        try {            
        	ProtocolCommands commands = IdemixSmartcard.buildProofCommands(
        			nonce, spec, credentialId);
        	ProtocolResponses responses = execute(commands);
        	return IdemixSmartcard.processBuildProofResponses(responses, spec);
        // Report caught exceptions
        } catch (CardServiceException e) {
            System.err.println(e.getMessage() + "\n");
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Set the specification of a certificate issuance: 
     * 
     * <ul>
     *   <li> issuer public key, and 
     *   <li> context. 
     * </ul>
     * 
     * @param spec the specification to be set.
     * @throws CardServiceException if an error occurred.
     */
    public void setIssuanceSpecification(IssuanceSpec spec) throws CardServiceException {
    	issuanceSpec = spec;
    	execute(IdemixSmartcard.setIssuanceSpecificationCommands(spec, credentialId));
    }
    
    /**
     * Set the attributes: 
     * 
     * <pre>
     *   m_1, ..., m_l
     * </pre>
     * 
     * @param spec the issuance specification for the ordering of the values.
     * @param values the attributes to be set.
     * @throws CardServiceException if an error occurred.
     */
    public void setAttributes(IssuanceSpec spec, Values values)
    throws CardServiceException {
        execute(IdemixSmartcard.setAttributesCommands(spec, values));
    }
    
    public Vector<Integer> getCredentials() 
    throws CardServiceException {
    	Vector<Integer> list = new Vector<Integer>();
    	
    	ProtocolResponse response = execute(IdemixSmartcard.getCredentialsCommand());
    	byte[] data = response.getData();
    	
    	for (int i = 0; i < data.length; i = i+2) {
    		int id = ((data[i] & 0xff) << 8) | data[i + 1];
    		if (id != 0) {
    			list.add(id);
    		}
    	}
    	
    	return list;
    }
    
    public HashMap<String, BigInteger> getAttributes(IssuanceSpec spec)
    throws CardServiceException {
    	HashMap<String, BigInteger> attributes = new HashMap<String, BigInteger>();
    	ProtocolCommands commands = IdemixSmartcard.getAttributesCommands(spec);
        ProtocolResponses responses = execute(commands);
        for (AttributeStructure attribute : spec.getCredentialStructure().getAttributeStructs()) {
        	String attName = attribute.getName();
            attributes.put(attName,
            		new BigInteger(1, responses.get("attr_" + attName).getData()));
        }
        return attributes;
    }
    
    public void removeCredential(short id)
    throws CardServiceException {
    	execute(IdemixSmartcard.removeCredentialCommand(id));
    }
    
    public short getCredentialFlags()
    throws CardServiceException {
    	ProtocolResponse response = execute(IdemixSmartcard.getCredentialFlagsCommand());
    	byte[] data = response.getData();
    	return (short) ((data[0] << 8) | data[1]);
    }
    
    public void setCredentialFlags(short flags)
    throws CardServiceException {
    	execute(IdemixSmartcard.setCredentialFlagsCommand(flags));
    }
}
