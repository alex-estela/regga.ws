package reggaws.generator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Main class in order to generate the reggaws-entries.js file required by reggaws-explorer.
 * Beta version with some refactoring required here and there, but it works.
 * @author estela
 */
public class EntriesGenerator {
	
	private static class Entity {
		private String name;
		private Map<String, JsonNode> attributes = new TreeMap<String, JsonNode>(); // jsonNode contains type, format, description
		private List<String> parents = new ArrayList<String>();
		private List<String> children = new ArrayList<String>();
		private List<String> compositions = new ArrayList<String>();
		private List<String> aggregations = new ArrayList<String>();
		private Map<String, PathEntity> paths = new TreeMap<String, PathEntity>();

		@Override
		public String toString() {
			return name;
		}		
	}
	
	@SuppressWarnings("unused")
	private static class PathEntity {
		private boolean inResponse;
		private boolean inRequestBody;
		private boolean inRequestPath;
	}

	private static boolean isUsableNode(JsonNode node) {
		return node != null && !node.isMissingNode() && !node.isNull();
	}
	
	private static void assignPath(Entity entity, String fullPath, Boolean inRequestBody, Boolean inRequestPath, Boolean inResponse) {
		PathEntity pathEntity = entity.paths.get(fullPath);
		if (pathEntity == null) {
			pathEntity = new PathEntity();
			entity.paths.put(fullPath, pathEntity);
		}
		if (inRequestBody != null && inRequestBody) pathEntity.inRequestBody = true;
		if (inRequestPath != null && inRequestPath) pathEntity.inRequestPath = true;
		if (inResponse != null && inResponse) pathEntity.inResponse = true;
	}
	
	private static void assignPathToChildren(TreeMap<String, Entity> entities, Entity entity, String fullPath, Boolean inRequestBody, Boolean inRequestPath, Boolean inResponse) {
		for (String childName : entity.children) {
			Entity child = entities.get(childName);
			assignPath(child, fullPath, inRequestBody, inRequestPath, inResponse);
			assignPathToChildren(entities, child, fullPath, inRequestBody, inRequestPath, inResponse);
		}
	}

	public static void main(String[] args) throws JsonProcessingException, IOException {
		
		if (args.length != 1 || !(new File(args[0])).isFile()) {
			System.out.println("Program argument should be a readable Swagger-compliant file");
			return;
		}
		
		String jsonInputFilePath = args[0];
		String jsOutputFilePath = "reggaws-entries.js";
		boolean debug = false;
		
		TreeMap<String, Entity> entities = new TreeMap<String, Entity>();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(jsonInputFilePath), "UTF8"));		
		ObjectMapper m = new ObjectMapper();
		JsonNode rootNode = m.readTree(in);
		JsonNode definitions = rootNode.path("definitions");
		
		for (Iterator<Entry<String, JsonNode>> i1 = definitions.fields(); i1.hasNext();) {
			
			Entry<String, JsonNode> definition = i1.next();			
			Entity entity = new Entity();
			entity.name = definition.getKey();
			entities.put(entity.name, entity);			
			if (debug) System.out.println("Found entity " + entity.name);
			JsonNode types = definition.getValue().get("allOf");
			
			for (Iterator<JsonNode> i2 = types.elements(); i2.hasNext();) {
				
				JsonNode type = i2.next();
				JsonNode properties = type.get("properties");
				
				// definition de la classe
				if (isUsableNode(properties))  {				
					for (Iterator<Entry<String, JsonNode>> i3 = properties.fields(); i3.hasNext();) {						
						Entry<String, JsonNode> property = i3.next();
						if (debug) System.out.println("Found attribute " + property.getKey());
						entity.attributes.put(property.getKey(), property.getValue());
					}
				}
				// definition des parents
				else {
					JsonNode parentType = type.get("$ref");
					if (isUsableNode(parentType))  {
						String parentName = parseEntityName(parentType.textValue());
						entity.parents.add(parentName);
					}
				}
			}
		}
		
		// nettoyage des parents superflus
		// TO BE REFACTORED
		for (Map.Entry<String, Entity> entityEntry : entities.entrySet()) {
			Entity entity =  entityEntry.getValue();
			for (int i=entity.parents.size()-1; i>=0; i--) {
				Entity entity2 = entities.get(entity.parents.get(i));
				for (int j=0; j<entity2.parents.size(); j++) {
					Entity entity3 = entities.get(entity2.parents.get(j));
					for (int h=entity.parents.size()-1; h>=0; h--) {
						Entity entity4 = entities.get(entity.parents.get(h));
						if (entity4.name.equals(entity3.name)) {
							entity.parents.remove(h);			
							if (debug) System.out.println("Removed parent " + entity4.name + " of " + entity.name);								
							break;
						}
					}
				}
			}
		}
		
		// post-attribution des enfants
		for (Map.Entry<String, Entity> entityEntry : entities.entrySet()) {
			Entity entity =  entityEntry.getValue();
			for (String parentName : entity.parents) {
				entities.get(parentName).children.add(entity.name);
			}
		}
		
		// post-attribution des compositions et aggregations
		TreeMap<String, Entity> tempEntities = new TreeMap<String, EntriesGenerator.Entity>();
		for (Map.Entry<String, Entity> entityEntry : entities.entrySet()) {
			Entity entity =  entityEntry.getValue();
			for (Map.Entry<String, JsonNode> attribute : entity.attributes.entrySet()) {
				JsonNode attributeDef = attribute.getValue();
				if (isUsableNode(attributeDef.get("$ref"))) {
					String entityName = parseEntityName(attributeDef.get("$ref").textValue());
					//Entity targetEntity = getOrCreateEntity(entityName, entities, tempEntities);
					if (debug) System.out.println("Case1: Adding composition " + entityName + " for " + entity.name);
					entity.compositions.add(entityName);
				}
				else if (isUsableNode(attributeDef.get("x-identifier-for-schema-ref"))) {
					String entityName = parseEntityName(attributeDef.get("x-identifier-for-schema-ref").textValue());
					//Entity targetEntity = getOrCreateEntity(entityName, entities, tempEntities);
					if (debug) System.out.println("Case2: Adding aggregation " + entityName + " for " + entity.name);
					entity.aggregations.add(entityName);
				}
				else if (isUsableNode(attributeDef.get("items")) && isUsableNode(attributeDef.get("items").get("$ref"))) {
					String entityName = parseEntityName(attributeDef.get("items").get("$ref").textValue());
					//Entity targetEntity = getOrCreateEntity(entityName, entities, tempEntities);
					if (debug) System.out.println("Case3: Adding composition " + entityName + " for " + entity.name);
					entity.compositions.add(entityName);
				}
				else if (isUsableNode(attributeDef.get("items")) && isUsableNode(attributeDef.get("items").get("x-identifier-for-schema-ref"))) {
					String entityName = parseEntityName(attributeDef.get("items").get("x-identifier-for-schema-ref").textValue());
					//Entity targetEntity = getOrCreateEntity(entityName, entities, tempEntities);
					if (debug) System.out.println("Case4: Adding aggregation " + entityName + " for " + entity.name);
					entity.compositions.add(entityName);
				}
			}
		}
		entities.putAll(tempEntities);
		
		// recherche des URI pour chaque ressource
		JsonNode paths = rootNode.path("paths");
		
		for (Iterator<Entry<String, JsonNode>> i1 = paths.fields(); i1.hasNext();) {
			
			Entry<String, JsonNode> path = i1.next();
			String uri = path.getKey();
			JsonNode methods = path.getValue();
			
			for (Iterator<Entry<String, JsonNode>> i2 = methods.fields(); i2.hasNext();) {
				
				Entry<String, JsonNode> method = i2.next();
				String methodName = method.getKey();
				JsonNode methodDetails = method.getValue();
				
				JsonNode methodResponses = methodDetails.get("responses");
				if (isUsableNode(methodResponses)) {
					for (Iterator<Entry<String, JsonNode>> i3 = methodResponses.fields(); i3.hasNext();) {
						
						Entry<String, JsonNode> response = i3.next();
						JsonNode responseDetails = response.getValue();
						String entityName = null;
						if (isUsableNode(responseDetails.get("schema")) && isUsableNode(responseDetails.get("schema").get("items"))  
								&& isUsableNode(responseDetails.get("schema").get("items").get("$ref"))) {
							entityName = parseEntityName(responseDetails.get("schema").get("items").get("$ref").textValue());							
						}
						else if (isUsableNode(responseDetails.get("schema")) && isUsableNode(responseDetails.get("schema").get("$ref"))) {
							entityName = parseEntityName(responseDetails.get("schema").get("$ref").textValue());
						}
						if (entityName != null) {
							// entity itself
							Entity entity = entities.get(entityName);
							String fullPath = methodName.toUpperCase() + " " + uri;
							assignPath(entity, fullPath, null, null, true);
							// children
							assignPathToChildren(entities, entity, fullPath, null, null, true);
						}
					}
				}				

				JsonNode methodParameters = methodDetails.get("parameters");
				if (isUsableNode(methodParameters)) {
					for (Iterator<JsonNode> i3 = methodParameters.elements(); i3.hasNext();) {
						
						JsonNode parameterDetails = i3.next();
						
						if (isUsableNode(parameterDetails.get("schema")) && isUsableNode(parameterDetails.get("schema").get("$ref"))) {
							String entityName = parseEntityName(parameterDetails.get("schema").get("$ref").textValue());
							Entity entity = entities.get(entityName);
							String fullPath = methodName.toUpperCase() + " " + uri;
							assignPath(entity, fullPath, true, null, null);
							// children
							assignPathToChildren(entities, entity, fullPath, true, null, null);
						}
						if (isUsableNode(parameterDetails.get("x-identifier-for-schema-ref"))) {
							String entityName = parseEntityName(parameterDetails.get("x-identifier-for-schema-ref").textValue());
							Entity entity = entities.get(entityName);
							String fullPath = methodName.toUpperCase() + " " + uri;
							assignPath(entity, fullPath, null, true, null);
							// children
							assignPathToChildren(entities, entity, fullPath, null, true, null);
						}
					}
				}
				
			}			
		}
		
		// affichage
		if (debug)  {
			for (Map.Entry<String, Entity> entityEntry : entities.entrySet()) {
				Entity entity =  entityEntry.getValue();
				System.out.println("Entity: " + entity.name);
				System.out.println("-- Attributes: " + toString(entity.attributes.keySet().toArray()));
				System.out.println("-- Parents: " + toString(entity.parents));
				System.out.println("-- Children: " + toString(entity.children));
				System.out.println("-- Aggregations: " + toString(entity.aggregations));
				System.out.println("-- Compositions: " + toString(entity.compositions));
			}
		}
		
		System.out.println(entities.size() + " entities found");
		
		FileOutputStream fos = new FileOutputStream(jsOutputFilePath);
		try {
			fos.write("var reggaWsEntityMap = ".getBytes());
			fos.flush();
			fos.close();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		
		fos = new FileOutputStream(jsOutputFilePath, true);
		ObjectMapper mapper = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
		mapper.writeValue(fos, entities);
		
		System.out.println("Entities written");		
		
		StringBuilder sb = new StringBuilder();
		sb.append(";" + System.getProperty("line.separator"));
		sb.append("var reggaWsEntryList = [" + System.getProperty("line.separator"));
		int i=0;
		for (Map.Entry<String, Entity> entityEntry : entities.entrySet()) {
			Entity entity =  entityEntry.getValue();
			int j=0;
			sb.append("{ resource: '" + entity.name + "', attribute: '', key: '' }");
			if (entity.attributes.entrySet().size() > 0 || i < entities.size()-1) sb.append(",");
			for (Map.Entry<String, JsonNode> attributeEntry : entity.attributes.entrySet()) {
				String baseAttrTokenStr = "{ resource: '" + entity.name + "', attribute: '" + attributeEntry.getKey() + "', key: '";
				String[] words = attributeEntry.getKey().split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
				for (int h=0; h<words.length; h++) {
					String word = words[h];
					sb.append(baseAttrTokenStr + word + "'}");
					if (i < entities.size()-1 || j < entity.attributes.size()-1 || h < words.length-1) sb.append("," + System.getProperty("line.separator"));
				}
				j++;
			}
			//sb.append(System.getProperty("line.separator"));
			i++;
		}
		sb.append("];" + System.getProperty("line.separator"));
		try {
			fos.write(sb.toString().getBytes());
			fos.flush();
			fos.close();
	 
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Entries written");
	}

//	private static Entity getOrCreateEntity(String entityName, TreeMap<String, Entity> entities, TreeMap<String, Entity> tempEntities) {
//		Entity entity = entities.get(entityName);
//		if (entity == null) entity = tempEntities.get(entityName);
//		if (entity == null) {
//			entity = new Entity();
//			entity.name = entityName;
//			tempEntities.put(entityName, entity);
//		}
//		return entity;
//	}

	private static String parseEntityName(String fullPath) {
		if (fullPath.contains("#/definitions/")) return fullPath.substring("#/definitions/".length());
		return fullPath;
	}
	
	private static String toString(Object[] array) {
		String str = "";
		for (int i=0; i<array.length; i++) {
			str += array[i].toString();
			if (i != array.length-1) str += ", ";
		}
		return str;
	}
	
	private static String toString(List<String> list) {
		String str = "";
		for (int i=0; i<list.size(); i++) {
			str += list.get(i);
			if (i != list.size()-1) str += ", ";
		}
		return str;
	}

}
