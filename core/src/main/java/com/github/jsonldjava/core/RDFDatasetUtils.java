package com.github.jsonldjava.core;

import static com.github.jsonldjava.core.JSONLDConsts.*;
import static com.github.jsonldjava.core.JSONLDUtils.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.jsonldjava.utils.JSONUtils;

public class RDFDatasetUtils {
	
	/**
	 * Creates an array of RDF triples for the given graph.
	 *
	 * @param graph the graph to create RDF triples for.
	 * @param namer a UniqueNamer for assigning blank node names.
	 *
	 * @return the array of RDF triples for the given graph.
	 */
	@Deprecated // use RDFDataset.graphToRDF
	static List<Object> graphToRDF(Map<String,Object> graph, UniqueNamer namer) {
		List<Object> rval = new ArrayList<Object>();
		for (String id : graph.keySet()) {
			Map<String,Object> node = (Map<String, Object>) graph.get(id);
			List<String> properties = new ArrayList<String>(node.keySet());
			Collections.sort(properties);
			for (String property : properties) {
				Object items = node.get(property);
				if ("@type".equals(property)) {
					property = RDF_TYPE;
				} else if (isKeyword(property)) {
					continue;
				}
				
				for (Object item : (List<Object>) items) {
					// RDF subjects
					Map<String,Object> subject = new LinkedHashMap<String,Object>();
					if (id.indexOf("_:") == 0) {
						subject.put("type", "blank node");
						subject.put("value", namer.getName(id));
					} else {
						subject.put("type", "IRI");
						subject.put("value", id);
					}
					
					// RDF predicates
					Map<String,Object> predicate = new LinkedHashMap<String,Object>();
					predicate.put("type", "IRI");
					predicate.put("value", property);
					
					// convert @list to triples
					if (isList(item)) {
						listToRDF((List<Object>) ((Map<String, Object>) item).get("@list"), namer, subject, predicate, rval);
					}
					// convert value or node object to triple
					else {
						Object object = objectToRDF(item, namer);
						Map<String,Object> tmp = new LinkedHashMap<String, Object>();
						tmp.put("subject", subject);
						tmp.put("predicate", predicate);
						tmp.put("object", object);
						rval.add(tmp);
					}
				}
			}
		}
		
		return rval;
	}

	/**
	 * Converts a @list value into linked list of blank node RDF triples
	 * (an RDF collection).
	 *
	 * @param list the @list value.
	 * @param namer a UniqueNamer for assigning blank node names.
	 * @param subject the subject for the head of the list.
	 * @param predicate the predicate for the head of the list.
	 * @param triples the array of triples to append to.
	 */
	private static void listToRDF(List<Object> list, UniqueNamer namer,
			Map<String, Object> subject, Map<String, Object> predicate,
			List<Object> triples) {
		Map<String,Object> first = new LinkedHashMap<String, Object>();
		first.put("type", "IRI");
		first.put("value", RDF_FIRST);
		Map<String,Object> rest = new LinkedHashMap<String, Object>();
		rest.put("type", "IRI");
		rest.put("value", RDF_REST);
		Map<String,Object> nil = new LinkedHashMap<String, Object>();
		nil.put("type", "IRI");
		nil.put("value", RDF_NIL);
		
		for (Object item : list) {
			Map<String,Object> blankNode = new LinkedHashMap<String, Object>();
			blankNode.put("type", "blank node");
			blankNode.put("value", namer.getName());
			
			{
				Map<String,Object> tmp = new LinkedHashMap<String, Object>();
				tmp.put("subject", subject);
				tmp.put("predicate", predicate);
				tmp.put("object", blankNode);
				triples.add(tmp);
			}
			
			subject = blankNode;
			predicate = first;
			Object object = objectToRDF(item, namer);
			
			{
				Map<String,Object> tmp = new LinkedHashMap<String, Object>();
				tmp.put("subject", subject);
				tmp.put("predicate", predicate);
				tmp.put("object", object);
				triples.add(tmp);
			}
			
			predicate = rest;
		}
		Map<String,Object> tmp = new LinkedHashMap<String, Object>();
		tmp.put("subject", subject);
		tmp.put("predicate", predicate);
		tmp.put("object", nil);
		triples.add(tmp);
	}
	
	/**
	 * Converts a JSON-LD value object to an RDF literal or a JSON-LD string or
	 * node object to an RDF resource.
	 *
	 * @param item the JSON-LD value or node object.
	 * @param namer the UniqueNamer to use to assign blank node names.
	 *
	 * @return the RDF literal or RDF resource.
	 */
	private static Object objectToRDF(Object item, UniqueNamer namer) {
		Map<String,Object> object = new LinkedHashMap<String, Object>();
		
		// convert value object to RDF
		if (isValue(item)) {
			object.put("type", "literal");
			Object value = ((Map<String, Object>) item).get("@value");
			Object datatype = ((Map<String, Object>) item).get("@type");
			
			// convert to XSD datatypes as appropriate
			if (value instanceof Boolean || value instanceof Number) {
				// convert to XSD datatype
				if (value instanceof Boolean) {
					object.put("value", value.toString());
					object.put("datatype", datatype == null ? XSD_BOOLEAN : datatype);
				} else if (value instanceof Double || value instanceof Float) {
					// canonical double representation
					DecimalFormat df = new DecimalFormat("0.0###############E0");
					object.put("value", df.format(value));
					object.put("datatype", datatype == null ? XSD_DOUBLE : datatype);
				} else {
					DecimalFormat df = new DecimalFormat("0");
					object.put("value", df.format(value));
					object.put("datatype", datatype == null ? XSD_INTEGER : datatype);
				}
			} else if (((Map<String, Object>) item).containsKey("@language")) {
				object.put("value", value);
				object.put("datatype", datatype == null ? RDF_LANGSTRING : datatype);
				object.put("language", ((Map<String, Object>) item).get("@language"));
			} else {
				object.put("value", value);
				object.put("datatype", datatype == null ? XSD_STRING : datatype);
			}
		}
		// convert string/node object to RDF
		else {
			String id = isObject(item) ? (String)((Map<String, Object>) item).get("@id") : (String)item;
			if (id.indexOf("_:") == 0) {
				object.put("type", "blank node");
				object.put("value", namer.getName(id));
			}
			else {
				object.put("type", "IRI");
				object.put("value", id);
			}
		}
		
		return object;
	}
	
	/**
	 * Converts an RDF triple object to a JSON-LD object.
	 *
	 * @param o the RDF triple object to convert.
	 * @param useNativeTypes true to output native types, false not to.
	 *
	 * @return the JSON-LD object.
	 */
	@Deprecated // use Node.toObject(useNativeTypes)
	static Map<String,Object> RDFToObject(final Map<String, Object> value, Boolean useNativeTypes) {
		// If value is an an IRI or a blank node identifier, return a new JSON object consisting 
		// of a single member @id whose value is set to value.
		if ("IRI".equals(value.get("type")) || "blank node".equals(value.get("type"))) {
			return new LinkedHashMap<String, Object>() {{
				put("@id", value.get("value"));
			}};
		};
		
		// convert literal object to JSON-LD
		Map<String,Object> rval = new LinkedHashMap<String, Object>() {{
			put("@value", value.get("value"));
		}};
		
		// add language
		if (value.containsKey("language")) {
			rval.put("@language", value.get("language"));
		}
		// add datatype
		else {
			String type;
			if (value.containsKey("datatype")) {
				type = (String) value.get("datatype");
			} else {
				// default datatype to string in the case that it hasn't been set
				type = XSD_STRING;
			}
			if (useNativeTypes) {
				// use native datatypes for certain xsd types
				if (XSD_STRING.equals(type)) {
					// don't add xsd:string 
				} else if (XSD_BOOLEAN.equals(type)) {
					if ("true".equals(rval.get("@value"))) {
						rval.put("@value", Boolean.TRUE);
					} else if ("false".equals(rval.get("@value"))) {
						rval.put("@value", Boolean.FALSE);
					}
				} else if (Pattern.matches("^[+-]?[0-9]+((?:\\.?[0-9]+((?:E?[+-]?[0-9]+)|)|))$", (String)rval.get("@value"))){
					try {
						Double d = Double.parseDouble((String)rval.get("@value"));
						if (!Double.isNaN(d) && !Double.isInfinite(d)) {
							if (XSD_INTEGER.equals(type)) {
								Integer i = d.intValue();
								if (i.toString().equals(rval.get("@value"))) {
									rval.put("@value", i);
								}
							} else if (XSD_DOUBLE.equals(type)) {
								rval.put("@value", d);
							} else {
								// we don't know the type, so we should add it to the JSON-LD
								rval.put("@type", type);
							}
						}
					} catch (NumberFormatException e) {
						// TODO: This should never happen since we match the value with regex!
						throw new RuntimeException(e);
					}
				}
				// do not add xsd:string type
				else {
					rval.put("@type", type);
				}
			} else {
				rval.put("@type", type);
			}
		}
		
		return rval;
	}

	public static String toNQuads(Map<String,Object> dataset) {
		//JSONLDTripleCallback callback = new NQuadTripleCallback();
		List<String> quads = new ArrayList<String>();
		for (String graphName : dataset.keySet()) {
			List<Map<String, Object>> triples = (List<Map<String,Object>>) dataset.get(graphName);
			for (Map<String,Object> triple : triples) {
				if ("@default".equals(graphName)) {
					graphName = null;
				}
				quads.add(toNQuad(triple, graphName));
			}
		}
		Collections.sort(quads);
		String rval = "";
		for (String quad : quads) {
			rval += quad;
		}
		return rval;
	}

	static String toNQuad(Map<String, Object> triple, String graphName, String bnode) {
		Map<String,Object> s = (Map<String, Object>) triple.get("subject");
		Map<String,Object> p = (Map<String, Object>) triple.get("predicate");
		Map<String,Object> o = (Map<String, Object>) triple.get("object");
		
		String quad = "";
		
		// subject is an IRI or bnode
		if ("IRI".equals(s.get("type"))) {
			quad += "<" + s.get("value") + ">";
		}
		// normalization mode
		else if (bnode != null) {
			quad += bnode.equals(s.get("value")) ? "_:a" : "_:z";
		}
		// normal mode
		else {
			quad += s.get("value");
		}
		
		// predicate is always an IRI
		quad += " <" + p.get("value") + "> ";
		
		// object is IRI, bnode or literal
		if ("IRI".equals(o.get("type"))) {
			quad += "<" + o.get("value") + ">";
		}
		else if ("blank node".equals(o.get("type"))) {
			// normalization mode
			if (bnode != null) {
				quad += bnode.equals(o.get("value")) ? "_:a" : "_:z";
			}
			// normal mode
			else {
				quad += o.get("value");
			}
		}
		else {
			String escaped = ((String)o.get("value"))
					.replaceAll("\\\\", "\\\\\\\\")
					.replaceAll("\\t", "\\\\t")
					.replaceAll("\\n", "\\\\n")
					.replaceAll("\\r", "\\\\r")
					.replaceAll("\\\"", "\\\\\"");
			quad += "\"" + escaped + "\"";
			if (RDF_LANGSTRING.equals(o.get("datatype"))) {
				quad += "@" + o.get("language");
			} else if (!XSD_STRING.equals(o.get("datatype"))) {
				quad += "^^<" + o.get("datatype") + ">";
			}
		}
		
		// graph
		if (graphName != null) {
			if (graphName.indexOf("_:") != 0) {
				quad += " <" + graphName + ">";
			}
			else if (bnode != null) {
				quad += " _:g";
			}
			else {
				quad += " " + graphName;
			}
		}
		
		quad += " .\n";
		return quad;
	}
	
	static String toNQuad(Map<String, Object> triple, String graphName) {
		return toNQuad(triple, graphName, null);
	}
	
	public static class Regex {
		// define partial regexes
		final public static Pattern IRI = Pattern.compile("(?:<([^:]+:[^>]*)>)");
		final public static Pattern BNODE = Pattern.compile("(_:(?:[A-Za-z][A-Za-z0-9]*))");
		final public static Pattern PLAIN = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
		final public static Pattern DATATYPE = Pattern.compile("(?:\\^\\^" + IRI + ")");
		final public static Pattern LANGUAGE = Pattern.compile("(?:@([a-z]+(?:-[a-z0-9]+)*))");
		final public static Pattern LITERAL = Pattern.compile("(?:" + PLAIN + "(?:" + DATATYPE + "|" + LANGUAGE + ")?)");
		final public static Pattern WS = Pattern.compile("[ \\t]+");
		final public static Pattern WSO = Pattern.compile("[ \\t]*");
		final public static Pattern EOLN = Pattern.compile("(?:\r\n)|(?:\n)|(?:\r)");
		final public static Pattern EMPTY = Pattern.compile("^" + WSO + "$");
		
		// define quad part regexes
		final public static Pattern SUBJECT = Pattern.compile("(?:" + IRI + "|" + BNODE + ")" + WS);
		final public static Pattern PROPERTY = Pattern.compile(IRI.pattern() + WS.pattern());
		final public static Pattern OBJECT = Pattern.compile("(?:" + IRI + "|" + BNODE + "|" + LITERAL + ")" + WSO);
		final public static Pattern GRAPH = Pattern.compile("(?:\\.|(?:(?:" + IRI + "|" + BNODE + ")" + WSO + "\\.))");
		
		// full quad regex
		final public static Pattern QUAD = Pattern.compile("^" + WSO + SUBJECT + PROPERTY + OBJECT + GRAPH + WSO + "$");
		
		// turtle prefix line
		final public static Pattern TTL_PREFIX_NS = Pattern.compile("(?:([a-zA-Z0-9\\.]*):)"); // TODO: chars can be more
		final public static Pattern TTL_PREFIX_ID = Pattern.compile("^@prefix" + WS + TTL_PREFIX_NS + WS + IRI + WSO + "\\." + WSO + "$");
		
		final public static Pattern IWSO = Pattern.compile("^" + WSO);
		final public static Pattern TTL_SUBJECT = Pattern.compile("^(?:" + TTL_PREFIX_NS + "([^ \\t]+)|" + BNODE + "|" + IRI + ")" + WS);
		final public static Pattern TTL_PREDICATE = Pattern.compile("^(?:" + TTL_PREFIX_NS + "([^ \\t]+)|" + IRI + ")"  + WS);
		final public static Pattern TTL_DATATYPE = Pattern.compile("(?:\\^\\^" + TTL_PREFIX_NS + "([^ \\t]+)|" + IRI + ")");
		final public static Pattern TTL_LITERAL = Pattern.compile("(?:" + PLAIN + "(?:" + TTL_DATATYPE + "|" + LANGUAGE + ")?)");
		final public static Pattern TTL_OBJECT = Pattern.compile("^(?:" + TTL_PREFIX_NS + "([^,; \\t]+)([,;\\.]?)|" + IRI + "|" + BNODE + "|" + TTL_LITERAL + ")" + WSO);
	}
	
	/**
	 * Parses RDF in the form of N-Quads.
	 *
	 * @param input the N-Quads input to parse.
	 *
	 * @return an RDF dataset.
	 */
	public static RDFDataset parseNQuads(String input) throws JSONLDProcessingError {
		// build RDF dataset
		RDFDataset dataset = new RDFDataset();
		
		// split N-Quad input into lines
		String[] lines = Regex.EOLN.split(input);
		int lineNumber = 0;
		for (String line : lines) {
			lineNumber++;
			
			// skip empty lines
			if (Regex.EMPTY.matcher(line).matches()) {
				continue;
			}
			
			// parse quad
			Matcher match = Regex.QUAD.matcher(line);
			if (!match.matches()) {
				throw new JSONLDProcessingError("Error while parsing N-Quads; invalid quad.")
					.setType(JSONLDProcessingError.Error.PARSE_ERROR)
					.setDetail("line", lineNumber);
			}
			
			// create RDF triple
			Map<String,Object> triple = new LinkedHashMap<String, Object>();
			
			// get subject
			if (match.group(1) != null) {
				final String value = match.group(1);
				triple.put("subject", new LinkedHashMap<String, Object>() {{
					put("type", "IRI");
					put("value", value);
				}});
			} else {
				final String value = match.group(2);
				triple.put("subject", new LinkedHashMap<String, Object>() {{
					put("type", "blank node");
					put("value", value);
				}});
			}
			
			// get predicate
			final String predval = match.group(3);
			triple.put("predicate", new LinkedHashMap<String, Object>() {{
				put("type", "IRI");
				put("value", predval);
			}});
			
			// get object
			if (match.group(4) != null) {
				final String value = match.group(4);
				triple.put("object", new LinkedHashMap<String, Object>() {{
					put("type", "IRI");
					put("value", value);
				}});
			} else if (match.group(5) != null) {
				final String value = match.group(5);
				triple.put("object", new LinkedHashMap<String, Object>() {{
					put("type", "blank node");
					put("value", value);
				}});
			} else {
				final String language = match.group(8);
				final String datatype = match.group(7) != null ? match.group(7) : match.group(8) != null ? RDF_LANGSTRING : XSD_STRING;
				final String unescaped = match.group(6)
						.replaceAll("\\\\\\\\", "\\\\")
						.replaceAll("\\\\t", "\\t")
						.replaceAll("\\\\n", "\\n")
						.replaceAll("\\\\r", "\\r")
						.replaceAll("\\\\\"", "\\\"");
				triple.put("object", new LinkedHashMap<String, Object>() {{
					put("type", "literal");
					put("datatype", datatype);
					if (language != null) {
						put("language", language);
					}
					put("value", unescaped);
				}});
			}
			
			// get graph name ('@default' is used for the default graph)
			String name = "@default";
			if (match.group(9) != null) {
				name = match.group(9);
			} else if (match.group(10) != null) {
				name = match.group(10);
			}
			
			// initialise graph in dataset
			if (!dataset.containsKey(name)) {
				List<Object> tmp = new ArrayList<Object>();
				tmp.add(triple);
				dataset.put(name, tmp);
			}
			// add triple if unique to its graph
			else {
				List<Map<String,Object>> triples = (List<Map<String, Object>>) dataset.get(name);
				if (!triples.contains(triple)) {
					triples.add(triple);
				}
			}
		}
		
		return dataset;
	}
}
