/*
Regga.ws - Swagger-based API Explorer 
Code subjected to the terms of the Mozilla Public License, v. 2.0.
See http://mozilla.org/MPL/2.0/.
*/
var ReggaWs = {
		
	// shared attributes
		
	entityMap: null,

	jsonDiv: null,

	pathDiv: null,
	
	umlDiv: null,
	
	coltitleClass: null,
		
	store: {},
	
	index: lunr(function() {
	    this.field('resource');
	    this.field('attribute');
	    this.field('key');
	    this.ref('id');
	}),

	// init function
		
	init: function(entityMap, entryList, searchField, resultDiv, noresultClass, showresultFunction, coltitleClass, jsonDiv, pathDiv, umlDiv) {
		
		ReggaWs.entityMap = entityMap;
		ReggaWs.jsonDiv = jsonDiv;
		ReggaWs.pathDiv = pathDiv;
		ReggaWs.umlDiv = umlDiv;
		ReggaWs.coltitleClass = coltitleClass;
		
		var index = ReggaWs.index;
		var store = ReggaWs.store;		
		var i = 0;
		
		entryList.forEach(function(entry) {
			i++;
			entry.id = i;
			index.add({
		        id: entry.id,
		        resource: entry.resource,
		        attribute: entry.attribute,
		        key: entry.key
		    });
			store[entry.id] = entry;
		});
		
		jQuery(function($) {
		    searchField.keyup(function() {

	            $(resultDiv).show();
	            resultDiv.empty();
		    	var titleStr = "<div class='" + ReggaWs.coltitleClass + "'>Search results</div>";		    	
		    	
		        var query = $(this).val();
		        if(query === '' || query.length < 2) {
	            	resultDiv.append(titleStr + "<div class='" + noresultClass + "'>No results found</div>");
		        }
		        else {
		            var results = index.search(query);
		            $(resultDiv).show();
		            resultDiv.empty();
		            if (!results.length) {
		            	resultDiv.append(titleStr + "<div class='" + noresultClass + "'>No results found</div>");
		            	return;
		            }            
		            var resultList = [];
		            for (var r in results) {
		            	var labelStr = store[results[r].ref].resource + (store[results[r].ref].attribute.length > 0 ? (" > " + store[results[r].ref].attribute) : '');
		            	var found = false;
		            	for (var r2 in resultList) {
		            		if (resultList[r2].label == labelStr) {
		            			found = true; break;
		            		}
		            	}
		            	if (!found) {
			            	resultList.push({
			            		label: labelStr,
			            		resourceName: store[results[r].ref].resource
			            	});
		            	}
		            }
		            resultList.sort(function(e1, e2) {
		            	return e1.label.localeCompare(e2.label);
		            });
		            var resultStr = titleStr + "<ul>";
		            for (var r in resultList) {            
		            	resultStr += "<li><a href='#' onclick=\"" + showresultFunction + "('" + resultList[r].resourceName + "')\">" + resultList[r].label + "</a></li>";
		            }
		            resultStr += "</ul>";
		            resultDiv.append(resultStr);
		        }
		    }); 
		});
	},

	// json rendering

	_exampleAttrName: "default",
	
	_identifierForSchemaRefAttrName: "x-identifier-for-schema-ref",
	
	_suppIndent: "&nbsp;&nbsp;&nbsp;&nbsp;",

	_getTypeExampleValue: function(attrObj) {
		var type = attrObj["format"] ? attrObj["format"] : attrObj["type"];
		if (type === 'string') {
			if (attrObj[ReggaWs._exampleAttrName]) return "'" + attrObj[ReggaWs._exampleAttrName] + "'";
			return "'example'";
		}
		if (type === 'boolean') {
			if (attrObj[ReggaWs._exampleAttrName]) return attrObj[ReggaWs._exampleAttrName];
			return "true";
		}
		if (type === 'number' || type === 'int32' || type === 'int64') {
			if (attrObj[ReggaWs._exampleAttrName]) return attrObj[ReggaWs._exampleAttrName];
			return "12345";
		}
		if (type === 'float') {
			if (attrObj[ReggaWs._exampleAttrName]) return attrObj[ReggaWs._exampleAttrName];
			return "123.45";
		}
		if (type === 'date') {
			if (attrObj[ReggaWs._exampleAttrName]) return "'" + attrObj[ReggaWs._exampleAttrName] + "'";
			return "'2010-10-10'";
		}
		console.log("Unknow type: " + type);
		return null;
	},

	_getEntityAttributeMap: function(selectedEntityName) {
		var attributeMap = {};
		var entity = ReggaWs.entityMap[selectedEntityName];
		for (var p in entity.parents) {
			var parent = ReggaWs.entityMap[entity.parents[p]];
			var tmpMap = ReggaWs._getEntityAttributeMap(parent.name);
			for (var a in tmpMap) {
				attributeMap[a] = tmpMap[a];
			}
		}
		for (var a in entity.attributes) {
			var attrObj = entity.attributes[a];
			if (attrObj["items"]) {
				if (attrObj["items"]["$ref"]) {
					var embedded = attrObj["items"]["$ref"].substring("#/definitions/".length);
					if (embedded != entity.name) {
						attributeMap[a] = [];
						attributeMap[a][0] = ReggaWs._getEntityAttributeMap(embedded);
					}
					else continue;
				}
				else {
					attributeMap[a] = [];
					attributeMap[a][0] = ReggaWs._getTypeExampleValue(attrObj["items"]);
				}
			}
			else {
				if (attrObj["$ref"]) {
					var embedded = attrObj["$ref"].substring("#/definitions/".length);
					if (embedded != entity.name) {
						attributeMap[a] = ReggaWs._getEntityAttributeMap(embedded);
					}
					else continue;
				}
				else {
					attributeMap[a] = ReggaWs._getTypeExampleValue(attrObj);
				}
			}		
		}
		return attributeMap;
	},

	_getEntityJsonHtml: function(attributeMap, indentation) {
		var count = 0;
		for (var a in attributeMap) {
			count++;
		}
		var html = "";
		var i = 0;
		for (var a in attributeMap) {
			var attrObj = attributeMap[a];
			var attrStr = indentation + a + ": ";
			if (attrObj.constructor === Array) {
				var attrObjReal = attrObj[0];
				if ($.type(attrObjReal) === "object") attrStr += "[ {<br/>" + ReggaWs._getEntityJsonHtml(attrObjReal, indentation + ReggaWs._suppIndent) + indentation + "} ]";
				else attrStr += "[ " + attrObjReal + " ]";
			}
			else {
				if ($.type(attrObj) === "object") attrStr += "{<br/>" + ReggaWs._getEntityJsonHtml(attrObj, indentation + ReggaWs._suppIndent) + indentation + "}";
				else attrStr += attrObj;			
			}
			if (i < count-1) html += attrStr + ",<br/>";
			else html += attrStr + "<br/>";
			i++;
		}
		return html;
	},
	
	renderEntityJson: function (selectedEntityName, element) {
		if (!element) element = ReggaWs.jsonDiv;
		element.empty();
		element.append("<div class='" + ReggaWs.coltitleClass + "'>Example of " + selectedEntityName + "</div>{<br/>" + ReggaWs._getEntityJsonHtml(ReggaWs._getEntityAttributeMap(selectedEntityName), ReggaWs._suppIndent) + "}");
	},
	
	// path rendering
	
	renderPaths: function (selectedEntityName, element) {
		if (!element) element = ReggaWs.pathDiv;
		element.empty();
		var entity = ReggaWs.entityMap[selectedEntityName];
		var str = "<div class='" + ReggaWs.coltitleClass + "'>Uses of " + selectedEntityName + "</div><ul>";
		for (var p in entity.paths) {
			var path = entity.paths[p];
			var arrowStr = "";
			if (path.inRequestBody && path.inResponse) arrowStr += "&#8644; ";
			else if (path.inRequestBody) arrowStr += "&#8594; ";
			else if (path.inResponse) arrowStr += "&#8592; ";
			//else if (path.inRequestPath) arrowStr += "[ID] ";
			str += "<li>" + arrowStr + p + "</li>";
		}
		str += "</ul>";
		element.append(str);
	},
	
	// uml rendering
	
	_getComplexityScore: function(e) {
		return e.children.length + e.aggregations.length + e.compositions.length;
	},
	
	_addEntityAndRelationsToScope: function(e, someScopedEntityMap) {
		if (someScopedEntityMap[e.name]) return;
		else someScopedEntityMap[e.name] = e;
		for (var e2 in e.parents) ReggaWs._addEntityAndRelationsToScope(ReggaWs.entityMap[e.parents[e2]], someScopedEntityMap);
		for (var e2 in e.children) ReggaWs._addEntityAndRelationsToScope(ReggaWs.entityMap[e.children[e2]], someScopedEntityMap);
		for (var e2 in e.compositions) ReggaWs._addEntityAndRelationsToScope(ReggaWs.entityMap[e.compositions[e2]], someScopedEntityMap);
		for (var e2 in e.aggregations) ReggaWs._addEntityAndRelationsToScope(ReggaWs.entityMap[e.aggregations[e2]], someScopedEntityMap);
	},
	
	renderUmlDiagram: function (selectedEntityName, element) {
		if (!element) element = ReggaWs.umlDiv;
		// settings
		var frameW = $(element).width();
		var defaultW = 200;
		var baseH = 30;
		var additionalAttrH = 10;
		var spacingBonus = 10;
		var spacingX = (frameW - (defaultW * 3) - spacingBonus) / 2;
		var spacingY = 50;
		// context
		var uml = joint.shapes.uml;
		var classes = {};
		var relations = [];
		var scopedEntityMap = {};
		var scopedEntityList = [];
		var maxHeight = 0;
		var currentX = 0;
		var currentY = 0;	
		
		// show only selected entity and relations
		var selectedEntity = ReggaWs.entityMap[selectedEntityName];
		ReggaWs._addEntityAndRelationsToScope(selectedEntity, scopedEntityMap);
		
		// show all entities and relations
		//for (var e in ReggaWs.entityMap) {
		//	ReggaWs._addEntityAndRelationsToScope(ReggaWs.entityMap[e], scopedEntityMap);
		//}
		
		for (var e in scopedEntityMap) {
			scopedEntityList.push(scopedEntityMap[e])
		}
		scopedEntityList.sort(function(e1, e2) {
			if (e1.name === selectedEntityName) return -1;
			return ReggaWs._getComplexityScore(e1) < ReggaWs._getComplexityScore(e2);
		});
		for (var e in scopedEntityList) {
			var entity = scopedEntityList[e];
			var entityAttributes = [];
			for (var a in entity.attributes) {
				var attrObj = entity.attributes[a];
				var attrStr = a + ": ";
				if (attrObj["items"]) {
					if (attrObj["items"]["$ref"]) attrStr += "array of <obj>";
					else if (attrObj["items"][ReggaWs._identifierForSchemaRefAttrName]) attrStr += "array of <id>";
					else {
						var type = attrObj["items"]["format"] ? attrObj["items"]["format"] : attrObj["items"]["type"];
						attrStr += "array of " + type;
					}
				}
				else {
					if (attrObj["$ref"]) attrStr += "<obj>";
					else if (attrObj[ReggaWs._identifierForSchemaRefAttrName]) attrStr += "<id>";
					else {
						var type = attrObj["format"] ? attrObj["format"] : attrObj["type"];
						attrStr += type;
					}
				}
				entityAttributes.push(attrStr);
			}
			var entityStyle;
			if (entity.name === selectedEntityName) entityStyle = {
				'.uml-class-name-rect, .uml-class-attrs-rect': {
		        	fill: '#ffcc00'
		        },
		        '.uml-class-name-text': {
		        	'font-family': '"Helvetica Neue", Helvetica, Arial, sans-serif',
			    	'font-size': '12px'
		        },
		        '.uml-class-attrs-text': {
			    	'font-family': '"Helvetica Neue", Helvetica, Arial, sans-serif',
			    	'font-size': '10px'
		        },
		        '.uml-class-methods-rect': {
		        	display: 'none'
		        }
			}
			else entityStyle = {
				'.uml-class-name-rect, .uml-class-attrs-rect': {
			    	fill: '#ffff66'
		        },
		        '.uml-class-name-text': {
		        	'font-family': '"Helvetica Neue", Helvetica, Arial, sans-serif',
			    	'font-size': '12px'
		        },
		        '.uml-class-attrs-text': {
			    	'font-family': '"Helvetica Neue", Helvetica, Arial, sans-serif',
			    	'font-size': '10px'
		        },
		        '.uml-class-methods-rect': {
		        	display: 'none'
		        }
			}
			var classHeight = (baseH + entityAttributes.length * additionalAttrH);
			if (classHeight > maxHeight) maxHeight = classHeight;
			classes[entity.name] = new uml.Class({
				position: { x: currentX  , y: currentY },
				size: { width: defaultW, height: classHeight },
				name: entity.name,
				attributes: entityAttributes,
		        attrs: entityStyle
			});
			currentX += defaultW + spacingX;
			if (currentX > frameW - defaultW) {
				currentY += maxHeight + spacingY;
				currentX = 0;
				maxHeight = 0;
			}
		}
		for (var e in scopedEntityMap) {
			for (var e2 in scopedEntityMap[e].parents) relations.push(new uml.Generalization({ source: { id: classes[scopedEntityMap[e].name].id }, target: { id: classes[scopedEntityMap[e].parents[e2]].id }}));
			for (var e2 in scopedEntityMap[e].aggregations) relations.push(new uml.Aggregation({ source: { id: classes[scopedEntityMap[e].aggregations[e2]].id }, target: { id: classes[scopedEntityMap[e].name].id }}));
			for (var e2 in scopedEntityMap[e].compositions) relations.push(new uml.Composition({ source: { id: classes[scopedEntityMap[e].compositions[e2]].id }, target: { id: classes[scopedEntityMap[e].name].id }}));
		}
		// render
		element.empty();
		var graph = new joint.dia.Graph();
		var viewer = new joint.dia.Paper({
		 el: element,
			width: frameW,
			height: currentY + maxHeight + spacingBonus,
			gridSize: 1,
			model: graph
		});
		_.each(classes, function(c) { graph.addCell(c); });
		_.each(relations, function(r) { graph.addCell(r); });
	}
};