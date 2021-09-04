/**
 * OWASP Benchmark Project
 *
 * <p>This file is part of the Open Web Application Security Project (OWASP) Benchmark Project For
 * details, please see <a
 * href="https://owasp.org/www-project-benchmark/">https://owasp.org/www-project-benchmark/</a>.
 *
 * <p>The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, version 2.
 *
 * <p>The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * @author Dave Wichers
 * @created 2018
 */
package org.owasp.benchmark.score.parsers;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.owasp.benchmark.score.BenchmarkScore;
import org.owasp.benchmark.score.TestCaseResult;
import org.owasp.benchmark.score.TestSuiteResults;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class AppScanDynamicReader2 extends Reader {

    // This is the new AppScan Dynamic reader, where they generate ".xml" files.

    public TestSuiteResults parse(File f) throws Exception {

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        // Prevent XXE
        docBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        InputSource is = new InputSource(new FileInputStream(f));
        Document doc = docBuilder.parse(is);

        Node root = doc.getDocumentElement();
        Node scanInfo = getNamedChild("scan-information", root);
        
        Node scanConfiguration = getNamedChild("scan-configuration", root);
        String startingUrl = getNamedChild("starting-url", scanConfiguration).getTextContent();
        System.out.println("Starting URL is: " + startingUrl);
        
        TestSuiteResults tr =
                new TestSuiteResults("IBM AppScan Dynamic", true, TestSuiteResults.ToolType.DAST);

        // version is usually like 9.3.0 but sometimes like 9.3.0 iFix005. We trim off the part
        // after the space char.
        Node version = getNamedChild("product-version", scanInfo);
        //    System.out.println("Product version is: " + version.getTextContent());
        if (version != null) {
            tr.setToolVersion(version.getTextContent().split(" ")[0]);
        }

        Node allIssues = getNamedChild("url-group", root);
        List<Node> vulnerabilities = getNamedChildren("item", allIssues);
        
        Node allIssueVariants = getNamedChild("issue-group", root);
        List<Node> variants = getNamedChildren("item", allIssueVariants);
        
        

        // Loop through all the vulnerabilities
        for (Node vulnerability : vulnerabilities) {
        	String issueType = getNamedChild("issue-type", vulnerability).getTextContent();
        	
        	String url = getNamedChild("name", vulnerability).getTextContent();
            // to give DAST tools some credit, if they report a similar vuln in a different area, we
            // count it.
            // e.g., SQLi in the XPATHi tests. To do that, we have to pull out the vuln type from
            // the URL.
            
        	NamedNodeMap itemNode = vulnerability.getAttributes();
        	String variantItemID = itemNode.getNamedItem("id").getNodeValue();
        	
        	List<String> testCaseElementsFromVariants = variantLookup(issueType, variantItemID, startingUrl, variants);
        	if(testCaseElementsFromVariants.isEmpty()) {
        		//Handle non-variant issue types , Older xml format as in 9.x release versions and before
        		// First get the type of vuln, and if we don't care about that type, move on                                
                TestCaseResult tcr = TestCaseLookup(issueType, url);                
                 tr.put(tcr);        		
        	}
        	else {
        		//Handle issues which are Variants, new xml format after 10.x release
        		for (String testArea : testCaseElementsFromVariants ) {
        			TestCaseResult tcr = TestCaseLookup(issueType, testArea);
        			tr.put(tcr);     
        		}
        	}
        }
        
        return tr;
    }
    
    /// Issues which are not variants
    private static TestCaseResult TestCaseLookup(String issueType, String url) {
    	TestCaseResult tcr = new TestCaseResult();
    	String urlElements[] = url.split("/");
        String testArea =
                urlElements[urlElements.length - 2].split("-")[0]; // .split strips off the -##
        
    	int vtype = cweLookup(issueType, testArea);
        
        // Then get the filename containing the vuln. And if not in a test case, skip it.
        // Parse out test number from:
        // https://localhost:port/benchmark/testarea-##/BenchmarkTest02603
        int startOfTestCase = url.lastIndexOf("/") + 1;
        String testcase = url.substring(startOfTestCase, url.length());
        testcase =
                testcase.split("\\.")[
                        0]; // if test case has extension (e.g., BenchmarkTestCase#####.html),
        // strip it off.
        // System.out.println("Candidate test case is: " + testcase);
        if (testcase.startsWith(BenchmarkScore.TESTCASENAME)) {
            int tn = -1;
            String testno = testcase.substring(BenchmarkScore.TESTCASENAME.length());
            try {
                tn = Integer.parseInt(testno);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }

            //				if (tn == -1) System.out.println("Found vuln outside of test case of type: " +
            // issueType);

            // Add the vuln found in a test case to the results for this tool            
            tcr.setNumber(tn);
            tcr.setCategory(issueType); // TODO: Is this right?
            tcr.setCWE(vtype);
            tcr.setEvidence(issueType);
        }
        return tcr;
    }
    
    
    //Fetch Issues listed as variants, to cater to post 10.x release xml format
    private static List<String> variantLookup(String issueType, String itemID,String startingUrl, List<Node> variants) {
    	List<String> testCaseElementsFromVariants = new ArrayList<String>();
    	
    	//System.out.println("Variant Lookup Item ID: " + itemID);
        
    	for (Node variant : variants) {
        	String variantUrlRefId = getNamedChild("url", variant).getTextContent().trim();
        	String variantIssueType = getNamedChild("issue-type", variant).getTextContent().trim();
        	//System.out.println("Variant Url Ref ID: " + variantUrlRefId);
        	
        	
        	// Add the record only if the issue type matches for the relevant variants
        	if (issueType.equals(variantIssueType) && itemID.equals(variantUrlRefId)) {        		
        		Node variantNodes = getNamedChild("variant-group", variant);
            	List<Node> variantNodeChildren = getNamedChildren("item", variantNodes);
            	for (Node variantNodeChild : variantNodeChildren) {
            		String httpTraffic = getNamedChild("test-http-traffic", variantNodeChild).getTextContent();
            		String[] variantUrl = httpTraffic.split(" ");
            		
            		String benchMarkTestCase = variantUrl[1].trim();            		
            		
            		if (benchMarkTestCase.contains("BenchmarkTest")) {
            			String urlElements[] = benchMarkTestCase.split("/");
                		
                		String testAreaUrl = startingUrl + urlElements[urlElements.length - 2] + "/" + urlElements[urlElements.length - 1];
                        String testArea = testAreaUrl.split("\\?")[0]; // .split strips off the -##
                		
                        if (testArea.contains("BenchmarkTest"))
                        	testCaseElementsFromVariants.add(testArea);
            		}                    
            	}        		
        	}        	
        }    	
    	
    	return testCaseElementsFromVariants;
    }

    // e.g., 3 Hour(s) 7 Minute(s) 58 Second(s)
    /*	private String parseTime(String message) {
    	    String[] parts = message.split( "\\) ");
            String hours = parts[0].substring( 0, parts[0].indexOf(' ') ).trim();
            if ( hours.length() < 2 ) hours = "0" + hours;
            String mins = parts[1].substring( 0, parts[1].indexOf(' ') ).trim();
            if ( mins.length() < 2 ) mins = "0" + mins;
            String secs = parts[2].substring( 0, parts[2].indexOf(' ') ).trim();
            if ( secs.length() < 2 ) secs = "0" + secs;
    	    return hours + ":" + mins + ":" + secs;
        }
    */
    private static int cweLookup(String vtype, String testArea) {
        int cwe = cweLookup(vtype);
        if ("xpathi".equals(testArea) && cwe == 89)
        	cwe = 643; // CWE for xpath injection
        if ("ldapi".equals(testArea) && cwe == 89)
        	cwe = 90; // CWE for xpath injection

        return cwe;
    }

    private static int cweLookup(String vtype) {
        switch (vtype) {
        	case "attDirectoryFound" : return 22;
        	case "attDirOptions" : return 22;
        	case "attApplicationRemoteCodeExecutionAdns" : return 78; // Score worse or
        	case "attLDAPInjection2" : return 90;
        	case "attLDAPInjection" : return 90;
        	case "attXPathInjection" : return 643;
        	case "attBlindXpathInjectionSingleQuote" : return 643;
        	case "attFileParamPipe" : return 78;
        	case "attContentSecurityPolicyObjectSrc" : return 00;
        	case "attContentSecurityPolicyScriptSrc" : return 00;
        	case "attBlindLDAPInjection":
        		return 90;
        	case "attCommandInjectionAdns":
        		return 78;
        	case "attCommandInjectionUnixTws":
        		return 78;
        	case "attBlindXPathInjection":
        		return 643;
        	case "attFileUnix":
        		return 22;
        	case "GV_SQLErr": return 00;
        	case "passParamGET":
        		return 523;
        	case "attBlindSqlInjectionStrings":
                return 89; // Score worse or better?
            case "attCachedSSL":
                return 00;
            case "attCodeInjectionInSystemCall":
                return 78; // Score worse or better?
            case "attCrossSiteScripting":
                return 79;
            case "attJSCookie":
                return 00;
                //			case "attLinkInjection" : return 00;
            case "attRespCookieNotSecureSSL":
                return 614;
            case "attSqlInjectionChecks":
                return 89; // Score worse or better?
            case "attUndefinedState":
                return 00;
            case "bodyParamsInQuery":
                return 00;
            case "ContentSecurityPolicy":
                return 00;
            case "ContentTypeOptions":
                return 00;

            case "GD_EmailAddress" : return 00;
            case "GETParamOverSSL":
                return 00;
                //			case "GV_SQLErr" : return 89; // Score worse or better with this or 00?
                //			case "HSTS" : return 00;

                // Microsoft MHTML XSS - Giving AppScan 'credit' for this introduces ~2.4% False
                // Positives and no real ones so I disabled it instead
            case "MHTMLXSS":
                return 00;

                //			case "OpenSource" : return 00;  // Known vuln in open source lib.
                //			case "phishingInFrames" : return 00;
            case "SHA1CipherSuites":
                return 327; // Better if set to 327?
            case "ShellShockCheck":
                return 00; // don't care
            case "SriSupport":
                return 00; // don't care
                //			case "SSL_CertWithBadCN" : return 00; // don't care
                //			case "XSSProtectionHeader" : return 00;

            default:
                System.out.println("Identified unknown type of: " + vtype);
        }
        return 0;
    }
}
