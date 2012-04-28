/*
 * OccdocXMLReader.java
 * Copyright (C) 2011 Steven West, University of Kent <sw349@kent.ac.uk>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301 USA
 */
package occblocks.io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import occblocks.blocks.Import;
import occblocks.blocks.PROC;
import occblocks.blocks.VAR;
import occblocks.blocks.channel.ChannelEnd;
import occblocks.blocks.channel.EndType;
import occblocks.blocks.type.Type;
import org.xml.sax.SAXException;

/**
 * This class is responsible for reading the XML generated by occamdoc and
 * parsing it into usable Imports for later use. At the moment this only deals
 * with PROCS but once they are implemented in the code it will have to be
 * updated to load other things like constants and functions.
 * @author Steve "Uru" West <sw349@kent.ac.uk>
 * @version 2011-12-06
 */
public class OccdocXMLReader {

    /**
     * This method will take in a file path and attempt to read the XML to
     * produce a list of Imports containing PROCS.
     * @param filename The file to open
     * @return A list of all found imports containing PROC headers or null if
     * there was a problem
     */
    public List<Import> getDocumentContent(String filename) {

        try {
            Document doc = DocumentBuilderFactory.newInstance().
                    newDocumentBuilder().
                    parse(new File(filename));



            //Make sure everything is good
            doc.getDocumentElement().normalize();

            return getImportList(doc.getFirstChild().getChildNodes());

        } catch (SAXException ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();

        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();

        } catch (ParserConfigurationException ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }

        return null;
    }

    /**
     * Generates a list of Imports along with their PROCS from the given 
     * list of XML nodes
     * @param nl
     * @return 
     */
    private List<Import> getImportList(NodeList nl) {
        ArrayList<Import> imports = new ArrayList<Import>();

        //Loop through each libary
        for (int n = 0; n < nl.getLength(); n++) {

            //Grab the element
            Node current = nl.item(n);
            //Check it's useable
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                //Print out the name
                Element curEle = (Element) current;

                if (curEle.getAttribute("type").equals("module")) {
                    imports.add(getImportInfos(curEle));
                }
            }
        }

        return imports;
    }

    /**
     * Parses a set of XML nodes into a single Import containing PROCS
     * @param el
     * @return 
     */
    private Import getImportInfos(Element el) {
        //Get the name of the module
        Import imp = new Import(el.getAttribute("name") + ".module");

        NodeList decs = el.getElementsByTagName("declaration");

        //Find the PROC defs
        for (int n = 0; n < decs.getLength(); n++) {
            //Loop through each PROC and create a PROC object to add to the Import
            Node current = decs.item(n);
            
            if(current.getNodeType() == Node.ELEMENT_NODE){
                Element ele = (Element) current;
                //Check that we have a PROC
                if(ele.getAttribute("type").equals("proc")){
                    String procName = ele.getAttribute("name");
                    PROC proc = new PROC(procName);
                    
                    //Grab the definition to allow us to grab the channel directions later
                    NodeList defs = ele.getElementsByTagName("definition");
                    Element defElement = (Element) defs.item(defs.getLength()-1);
                    String procDefinition = defElement.getTextContent();
                    //Remove the "PROC <name>" part to make finding channel directions easer
                    procDefinition = procDefinition.substring(procDefinition.indexOf(procName)+procName.length());
                    
                    //get all the params
                    NodeList params = ele.getElementsByTagName("params");
                    for(int p=0; p<params.getLength(); p++){
                        Node part = params.item(p);
                        
                        if(part != null && part.getNodeType() == Node.ELEMENT_NODE){
                            //Get the param info
                            Element paramInfo = (Element) part;
                            
                            Element item = (Element) paramInfo.getElementsByTagName("item").item(0);
                            Element definition = (Element) paramInfo.getElementsByTagName("definition").item(0);
                            
                            String name = item.getAttribute("name");
                            String typeDef = definition.getTextContent();
                            
                            //Now we have the info work out if it's a VAR or CHAN
                            if(typeDef.startsWith("VAL")){
                                //Construct and add a new VAL
                                Type varType = new Type(typeDef.substring(3));
                                VAR val = new VAR(varType, name, null);
                                val.setParent(proc);
                                proc.addPROCParam(val);
                                
                            } else if(typeDef.startsWith("CHAN")){
                                //Construct and add a new CHAN
                                //TODO: load the right direction
                                Type chanType = new Type(typeDef.substring(5));
                                int typeDefIndex = procDefinition.indexOf(name)+name.length();
                                String direction = procDefinition.substring(typeDefIndex, typeDefIndex+1).trim();
                                EndType endType = null;
                                
                                if(direction.equals("?")){
                                    endType = EndType.READ;
                                } else if(direction.equals("!")){
                                    endType = EndType.WRITE;
                                }
                                
                                ChannelEnd ce = new ChannelEnd(chanType, endType, proc);
                                ce.setName(name);
                                proc.addPROCParam(ce);
                            }
                        }
                    }
                    
                    imp.addPROC(proc);
                }
            }
        }

        return imp;
    }
}
