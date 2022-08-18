/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.ws.security.processor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSDocInfo;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.saml.SAML2Util;
import org.apache.ws.security.util.XMLUtils;

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wso2.carbon.identity.saml.common.util.SAMLInitializer;
import org.xml.sax.SAXException;

import javax.security.auth.callback.CallbackHandler;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is used to prcess a SAML2.0 Token and validate it.
 */
public class SAML2TokenProcessor implements Processor {

    private static final Log log = LogFactory.getLog(SAML2TokenProcessor.class.getName());

    private String id;
    private Element samlTokenElement;
    private static boolean isBootStrapped = false;
    private static Lock bootstrapLock = new ReentrantLock();

    public void handleToken(Element elem, Crypto crypto, Crypto decCrypto, CallbackHandler cb,
                            WSDocInfo wsDocInfo, Vector returnResults, WSSConfig config) throws WSSecurityException {
        Assertion assertion = buildAssertion(elem);
        // validate the signature of the SAML token
        if(assertion.getSignature() != null){
            SAML2Util.validateSignature(assertion, crypto);
        }

        id = assertion.getID();
        samlTokenElement = elem;

        WSSecurityEngineResult securityEngineResult = new WSSecurityEngineResult(
                WSConstants.ST_UNSIGNED, assertion);
        returnResults.add(0, securityEngineResult);

        // set the SAML version
        securityEngineResult.put(WSConstants.SAML_VERSION, WSConstants.SAML2_NS);
        // Adding a timeStamp element for validating the SAMLToken
        returnResults.add(0, new WSSecurityEngineResult(WSConstants.SAML_TIMESTAMP,
                                                        SAML2Util.getTimestampForSAMLAssertion(assertion)));
        // Adding the token issuer name
        securityEngineResult.put(WSConstants.SAML_ISSUER_NAME, assertion.getIssuer());
        // Adding the set of attributes included in a SAML assertion
        securityEngineResult.put(WSConstants.SAML_CLAIM_SET, SAML2Util.getClaims(assertion));
        // set whether the SAML assertion is signed or not
        securityEngineResult.put(WSConstants.SAML_TOKEN_SIGNED, Boolean.valueOf(assertion.isSigned()));
    }


    /**
     * This method is used to validate a SAML2.0 Token.
     * TODO At the moment it only validates by building an assertion similar to the SAMLTokenProcessor.
     * @param elem
     * @return SAML2.0 Assertion
     * @throws WSSecurityException
     */
    public Assertion buildAssertion(Element elem) throws WSSecurityException {
        Assertion samlAssertion;
        try {
            doBootstrap();
            // Unmarshall and build the assertion from the DOM element.
            String keyInfoElementString = elem.toString();
            DocumentBuilderFactory documentBuilderFactory = XMLUtils.getSecuredDocumentBuilder();
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(new ByteArrayInputStream(keyInfoElementString.trim().getBytes()));
            Element element = document.getDocumentElement();
            UnmarshallerFactory unmarshallerFactory = XMLObjectProviderRegistrySupport
                    .getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory
                    .getUnmarshaller(element);
            samlAssertion = (Assertion) unmarshaller
                    .unmarshall(element);
        }
        catch (InitializationException e) {
            throw new WSSecurityException(
                    WSSecurityException.FAILURE, "Failure in bootstrapping", null, e);
        } catch (UnmarshallingException e) {
            throw new WSSecurityException(
                    WSSecurityException.FAILURE, "Failure in unmarshelling the assertion", null, e);
        } catch (IOException e) {
            throw new WSSecurityException(
                    WSSecurityException.FAILURE, "Failure in unmarshelling the assertion", null, e);
        } catch (SAXException e) {
            throw new WSSecurityException(
                    WSSecurityException.FAILURE, "Failure in unmarshelling the assertion", null, e);
        } catch (ParserConfigurationException e) {
            throw new WSSecurityException(
                    WSSecurityException.FAILURE, "Failure in unmarshelling the assertion", null, e);
        }

        if (log.isDebugEnabled()) {
            log.debug("SAML2 Token was validated successfully.");
        }
        return samlAssertion;
    }

    public static void doBootstrap() throws InitializationException {
        if (!isBootStrapped) {
            bootstrapLock.lock();
            try {
                if (!isBootStrapped) {
                    SAMLInitializer.doBootstrap();
                    isBootStrapped = true;
                }
            } catch (Exception e) {
                throw new InitializationException("Error when Bootstrapping SAML2 library", e);
            } finally {
                bootstrapLock.unlock();
            }
        }
    }

    public Element getSamlTokenElement() {
        return samlTokenElement;
    }

    public void setSamlTokenElement(Element samlTokenElement) {
        this.samlTokenElement = samlTokenElement;
    }


    public String getId() {
        return id;
    }

}
