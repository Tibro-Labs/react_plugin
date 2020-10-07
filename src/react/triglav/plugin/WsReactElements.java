package react.triglav.plugin;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.joda.time.DateTime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.prtech.svarog.CodeList;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvComplexCache;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvConversation;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvGeometry;
import com.prtech.svarog.SvLink;
import com.prtech.svarog.SvMessage;
import com.prtech.svarog.SvParameter;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvRelationCache;
import com.prtech.svarog.SvSDITile;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbQueryExpression;
import com.prtech.svarog_common.DbQueryObject;
import com.prtech.svarog_common.DbQueryObject.DbJoinType;
import com.prtech.svarog_common.DbQueryObject.LinkType;
import com.prtech.svarog_common.DbSearch;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchExpression;
import com.prtech.svarog_common.IDbFilter;
import com.prtech.svarog_common.DbSearch.DbLogicOperand;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.geojson.GeoJsonReader;
import com.vividsolutions.jts.io.geojson.GeoJsonWriter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Path("/ReactElements")
public class WsReactElements {

	static final Logger log4j = LogManager.getLogger(WsReactElements.class.getName());

	public static String quotedstring(Object str) {
		return '"' + str.toString() + '"';
	}

	/**
	 * Method for finding locale id per user, If not set returns default
	 * 
	 * @param svr
	 *            SvReader instance
	 */

	private static String getLocaleId(SvReader svr) {
		String locale = SvConf.getDefaultLocale();
		try {
			if (svr.getUserLocale(SvReader.getUserBySession(svr.getSessionId())) != null) {
				DbDataObject localeObj = svr.getUserLocale(SvReader.getUserBySession(svr.getSessionId()));
				if (localeObj.getVal("LOCALE_ID").toString() != null)
					locale = localeObj.getVal("LOCALE_ID").toString();
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
		}
		return locale;
	}

	/**
	 * procedure to translate ID of the table into name of the table
	 * 
	 * @param tableID
	 *            Id of the table that needs to be translated
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return String of the table name, null if that table is not found
	 */
	public static String getTableNameById(Long tableID, SvReader svr) {
		String ret = "";
		try {
			DbDataObject vControlFormsObject = svr.getObjectById(tableID, svCONST.OBJECT_TYPE_TABLE, null);
			ret = vControlFormsObject.getVal(Rc.TABLE_NAME).toString();
			ret = ret.toUpperCase();
		} catch (SvException e) {
			debugSvException(e);
		}
		return ret;
	}

	/**
	 * procedure to to release SvReader, SvWriter, SvParameter and probably some
	 * more
	 * 
	 * @param svc
	 *            connected SvCore
	 */
	public static void releaseAll(SvCore svc) {
		if (svc != null)
			svc.release();
	}

	private static void debugSvException(SvException e) {
		if (log4j.isDebugEnabled())
			log4j.debug(e.getFormattedMessage(), e);
	}

	public static void debugException(Exception e) {
		if (log4j.isDebugEnabled())
			log4j.debug(e.getMessage(), e);
	}

	/**
	 * procedure to try to find a link with given name and one of the tables the
	 * link is connected to
	 * 
	 * @param objectName
	 *            String to what table the link is connected
	 * @param linkName
	 *            String what is the name of the link
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return DbDataObject of the link or null in case of multiple links or no
	 *         link found
	 */
	private DbDataObject findLink(String objectName, String linkName, SvReader svr) {
		DbSearchExpression expr = null;
		DbSearchCriterion critM = null;
		DbSearchCriterion crit1 = null;
		DbDataArray vData = null;
		DbDataObject vLink = null;
		try {
			Long objectTypeId = SvCore.getTypeIdByName(objectName);
			expr = new DbSearchExpression();
			critM = new DbSearchCriterion(Rc.LINK_TYPE, DbCompareOperand.EQUAL, linkName);
			critM.setNextCritOperand(DbLogicOperand.AND.toString());
			crit1 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE1, DbCompareOperand.EQUAL, objectTypeId);
			expr.addDbSearchItem(critM);
			expr.addDbSearchItem(crit1);
			vData = svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
			// if not found try reverse link
			if (vData == null || vData.getItems().isEmpty()) {
				expr = new DbSearchExpression();
				critM = new DbSearchCriterion(Rc.LINK_TYPE, DbCompareOperand.EQUAL, linkName);
				critM.setNextCritOperand(DbLogicOperand.AND.toString());
				crit1 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE2, DbCompareOperand.EQUAL, objectTypeId);
				expr.addDbSearchItem(critM);
				expr.addDbSearchItem(crit1);
				vData = svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
			}
			// in case there is only one link found return it, if the re are
			// none or multiple links return null
			if (vData.getItems().size() == 1)
				vLink = vData.getItems().get(0);
		} catch (SvException e) {
			debugSvException(e);
			vLink = null;
		}
		return vLink;
	}

	/**
	 * procedure to try to find a link with given name and one of the tables the
	 * link is connected to, according additional check for matching the
	 * left/right link object type with the object type of
	 * objToSearchLinkedItemsFor
	 * 
	 * @param objectName
	 *            String to what table the link is connected
	 * @param linkName
	 *            String what is the name of the link
	 * @param objToSearchLinkedItemsFor
	 *            Long objectId value of the object for which we want to get the
	 *            linked items
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return DbDataObject of the link or null in case of multiple links or no
	 *         link found
	 */
	private DbDataObject findLinkWithAdditionalCheck(String objectName, String linkName, Long objToSearchLinkedItemsFor,
			SvReader svr) {
		DbSearchExpression expr = null;
		DbSearchCriterion critM = null;
		DbSearchCriterion crit1 = null;
		DbDataArray vData = null;
		DbDataArray vData2 = null;
		DbDataObject vLink = null;
		try {
			Long objectTypeId = SvCore.getTypeIdByName(objectName);
			expr = new DbSearchExpression();
			critM = new DbSearchCriterion(Rc.LINK_TYPE, DbCompareOperand.EQUAL, linkName);
			critM.setNextCritOperand(DbLogicOperand.AND.toString());
			crit1 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE1, DbCompareOperand.EQUAL, objectTypeId);
			expr.addDbSearchItem(critM);
			expr.addDbSearchItem(crit1);
			vData = svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
			// second check for dual oriented link type
			expr = new DbSearchExpression();
			crit1 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE2, DbCompareOperand.EQUAL, objectTypeId);
			expr.addDbSearchItem(critM);
			expr.addDbSearchItem(crit1);
			vData2 = svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
			// check if there is dual oriented link configuration
			if (vData.size() > 0 && vData2.size() > 0) {
				// if exist, find the right one according the object type of the
				// param ObjToSearchLinkedItemsFor
				DbDataObject addCheckForObjToSearchLinkedItemsFor = svr.getObjectById(objToSearchLinkedItemsFor,
						Long.valueOf(vData.get(0).getVal(Rc.LINK_OBJECT_TYPE2).toString()), null);
				if (addCheckForObjToSearchLinkedItemsFor == null) {
					vData = vData2;
				}

			}
			if (vData == null || vData.getItems().isEmpty()) {
				vData = vData2;
			}
			// in case there is only one link found return it, if the re are
			// none or multiple links return null
			if (vData.getItems().size() == 1)
				vLink = vData.getItems().get(0);
		} catch (SvException e) {
			debugSvException(e);
			vLink = null;
		}
		return vLink;
	}

	/**
	 * procedure to try to find a link with given name and one of the tables the
	 * link is connected to
	 * 
	 * @param objectName
	 *            String to what table the link is connected
	 * @param linkName
	 *            String what is the name of the link
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return DbDataObject of the link or null in case of multiple links or no
	 *         link found
	 */
	private DbDataObject findLink(String objectName1, String linkName, String objectName2, SvReader svr) {
		DbSearchExpression expr = null;
		DbSearchCriterion critM = null;
		DbSearchCriterion crit1 = null;
		DbSearchCriterion crit2 = null;
		DbDataArray vData = null;
		DbDataObject vLink = null;
		try {
			Long objectTypeId1 = SvCore.getTypeIdByName(objectName1);
			Long objectTypeId2 = SvCore.getTypeIdByName(objectName2);
			expr = new DbSearchExpression();
			critM = new DbSearchCriterion(Rc.LINK_TYPE, DbCompareOperand.EQUAL, linkName);
			critM.setNextCritOperand(DbLogicOperand.AND.toString());
			crit1 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE1, DbCompareOperand.EQUAL, objectTypeId1);
			crit1.setNextCritOperand(DbLogicOperand.AND.toString());
			crit2 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE2, DbCompareOperand.EQUAL, objectTypeId2);
			expr.addDbSearchItem(critM);
			expr.addDbSearchItem(crit1);
			expr.addDbSearchItem(crit2);
			vData = svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
			// if not found try reverse link
			if (vData == null || vData.getItems().isEmpty()) {
				expr = new DbSearchExpression();
				critM = new DbSearchCriterion(Rc.LINK_TYPE, DbCompareOperand.EQUAL, linkName);
				critM.setNextCritOperand(DbLogicOperand.AND.toString());
				crit1 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE2, DbCompareOperand.EQUAL, objectTypeId1);
				crit1.setNextCritOperand(DbLogicOperand.AND.toString());
				crit2 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE1, DbCompareOperand.EQUAL, objectTypeId2);
				expr.addDbSearchItem(critM);
				expr.addDbSearchItem(crit1);
				expr.addDbSearchItem(crit2);
				vData = svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
			}
			// in case there is only one link found return it, if the re are
			// none or multiple links return null
			if (vData.getItems().size() == 1)
				vLink = vData.getItems().get(0);
		} catch (SvException e) {
			debugSvException(e);
			vLink = null;
		}
		return vLink;
	}

	/**
	 * procedure to find the type of the object, so we can use table_name or
	 * object_type when calling any Web Service
	 * 
	 * @param objectName
	 *            String that could be number (ID) of the table or the name of
	 *            the table
	 * 
	 * @return Long with ObjectType ID, 0 if we could not find the table
	 */
	public static Long findTableType(String objectName) {
		Long pobjectType = 0L;
		try {
			if (objectName != null && objectName.matches("\\d*")) {
				pobjectType = Long.parseLong(objectName);
			} else if (objectName != null) {
				pobjectType = SvCore.getTypeIdByName(objectName, null);
			}
		} catch (Exception e) {
			debugException(e);
			log4j.error("object type (table) not found: " + objectName, e);
		}
		return pobjectType;
	}

	/**
	 * procedure to find the form object with given ID or label code of the form
	 * 
	 * @param objectName
	 *            String that could be number (ID) of the table or the name of
	 *            the table
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return DbDataObject of type FROM_TYPE or null if object was not found
	 */
	public static DbDataObject findFormObject(String formName, SvReader svr) {
		DbDataObject formObject = null;
		Boolean formFound = false;
		DbDataArray ret = null;
		DbDataObject formObjecttmp = SvCore.getDbtByName(Rc.FORM_TYPE);
		// try to find the form with ID
		try {
			formObject = svr.getObjectById(Long.parseLong(formName), formObjecttmp.getObject_id(), null);
			if (formObject == null)
				formFound = false;
			else
				formFound = true;
		} catch (SvException | NumberFormatException e) {
			if (log4j.isDebugEnabled())
				log4j.debug(e);
			formFound = false;
		}
		// try to find form with the lable_code
		if (!formFound) {
			DbSearchExpression expr = null;
			DbSearchCriterion critU = null;
			try {
				expr = new DbSearchExpression();
				critU = new DbSearchCriterion(Rc.LABEL_CODE, DbCompareOperand.EQUAL, formName);
				expr.addDbSearchItem(critU);
				ret = svr.getObjects(expr, formObjecttmp.getObject_id(), null, 0, 0);
			} catch (SvException e) {
				debugSvException(e);
				formObject = null;
			}
			if (ret != null && !ret.getItems().isEmpty())
				formObject = ret.getItems().get(0);
		}
		return formObject;
	}

	/**
	 * procedure to check the name of the field, we are not able to return or
	 * process fields : PKID, since this is the connection to SVAROG table,
	 * GUI_METADATA it has lots of JSON in it and configurations, CENTROID and
	 * GEOM are complex data-type and can't be displayed as any other similar
	 * format
	 * 
	 * @param fieldName
	 *            String field name that we like to process
	 * 
	 * @return true if the field is "normal" and can be processed, false if its
	 *         complex or funny field
	 */
	public static Boolean processField(String fieldName) {
		Boolean retVal = false;
		if (!Rc.PKID.equalsIgnoreCase(fieldName) && !Rc.GUI_METADATA.equalsIgnoreCase(fieldName)
				&& !"CENTROID".equalsIgnoreCase(fieldName) && !"GEOM".equalsIgnoreCase(fieldName))
			retVal = true;
		return retVal;
	}

	/**
	 * procedure to translate all data from JsonObject, or put default if there
	 * is no GUI metadata for the field
	 * 
	 * @param jsonreactGUI
	 *            JsonObject that contains all metadata for the field like
	 *            editable, sort, resize, default size
	 * 
	 * @return JsonObject with all data packed for display Json
	 * 
	 *         if there is not any info in GUI_METADATA we set default options:
	 *         field has filter and resize. if there is a GUI_METADATA for some
	 *         field we try to process it usually get the filter of the column,
	 *         or if it should be editable or resize. if the field is set not
	 *         visible we actually don't put anything for this field, so it will
	 *         stay hidden
	 */
	public static JsonObject prepareJsonGUI1(JsonObject jsonreactGUI) {
		JsonObject jData = new JsonObject();
		if (jsonreactGUI == null) {
			jData.addProperty(Rc.FILTERABLE, true);
			jData.addProperty(Rc.RESIZABLE, true);
		} else {
			jData = jsonreactGUI;
			if (jData.has(Rc.INLINE_EDITABLE) && jData.get(Rc.INLINE_EDITABLE).getAsBoolean()) {
				jData.addProperty(Rc.EDITABLE, true);
			} else
				jData.addProperty(Rc.EDITABLE, false);
			jData.remove(Rc.INLINE_EDITABLE);
			jData.remove(Rc.UISCHEMA);
		}
		return jData;
	}

	/**
	 * procedure to produce JsonArray with list of IDs and values with given
	 * CodeID of the list
	 * 
	 * @param plistCodeId
	 *            Long Id of the list
	 * @param fieldType
	 *            String type of the field, NUMBER or NVARCHAR
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonArray with JsonObjects
	 */
	private static JsonArray prepareJsonCodeListById(Long plistCodeId, String fieldType, SvReader svr) {
		/*
		 * we get new CodeList and read the list of codes by the given code ID ,
		 * then we create array of JosnObjects, each object has 4 items ,ID,
		 * value, text, title. Value is saved according to fieldType paramter as
		 * string or number
		 */
		JsonArray jarr = new JsonArray();
		CodeList cl = null;
		if (plistCodeId != null)
			try {
				cl = new CodeList(svr);
				HashMap<String, String> listMap;
				listMap = cl.getCodeList(getLocaleId(svr), plistCodeId, true);
				Iterator<Entry<String, String>> it = listMap.entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, String> pair = it.next();
					it.remove();
					JsonObject jLeaf = new JsonObject();
					if (fieldType.equals(Rc.NVARCHAR)) {
						jLeaf.addProperty(Rc.ID, (String) pair.getKey());
						jLeaf.addProperty(Rc.VALUE_LC, (String) pair.getKey());
					} else if (fieldType.equals(Rc.NUMERIC)) {
						jLeaf.addProperty(Rc.ID, Long.parseLong((String) pair.getKey()));
						jLeaf.addProperty(Rc.VALUE_LC, Long.parseLong((String) pair.getKey()));
					} else if (fieldType.equals(Rc.BOOLEAN)) {
						if ("true".equalsIgnoreCase(pair.getKey())) { // if true

							if ("mk_MK".equalsIgnoreCase(SvConf.getDefaultLocale())) {// if
																						// mk

								jLeaf.addProperty(Rc.ID, I18n.getText(SvConf.getDefaultLocale(), "mk.yes"));
								jLeaf.addProperty(Rc.VALUE_LC, I18n.getText(SvConf.getDefaultLocale(), "mk.yes"));
							} else {
								jLeaf.addProperty(Rc.ID, I18n.getText(SvConf.getDefaultLocale(), "yes"));
								jLeaf.addProperty(Rc.VALUE_LC, I18n.getText(SvConf.getDefaultLocale(), "yes"));
							}

						} else { // if false
							if ("mk_MK".equalsIgnoreCase(SvConf.getDefaultLocale())) {
								jLeaf.addProperty(Rc.ID, I18n.getText(SvConf.getDefaultLocale(), "mk.no"));
								jLeaf.addProperty(Rc.VALUE_LC, I18n.getText(SvConf.getDefaultLocale(), "mk.no"));
							} else {
								jLeaf.addProperty(Rc.ID, I18n.getText(SvConf.getDefaultLocale(), "no"));
								jLeaf.addProperty(Rc.VALUE_LC, I18n.getText(SvConf.getDefaultLocale(), "no"));
							}

						}
					}
					jLeaf.addProperty(Rc.TITLE, (String) pair.getValue());
					jLeaf.addProperty(Rc.TEXT, (String) pair.getValue());
					jarr.add(jLeaf);
				}
			} catch (Exception e) {
				debugException(e);
			} finally {
				if (cl != null)
					cl.release();
			}
		return jarr;
	}

	/**
	 * procedure to produce JsonArray with list of IDs and values with given
	 * GUI_METADATA to translate IDs CodeID of the list
	 * 
	 * @param guiMetadata
	 *            String GUI_METADATA from the field that we want to generate
	 *            list of dropdowns
	 * @param fieldType
	 *            String type of the field, NUMBER or NVARCHAR
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return String
	 */
	private static JsonArray prepareJsonCodeListByMetadata(String guiMetadata, String fieldType, SvReader svr) {
		/*
		 * we create the code-list by gui_metadata that is saved in GUI_METADATA
		 * of the field that we process. First we read if there is "react"
		 * JsonObject, and then IDTABLE/IDGETFIELD pair that will tell us from
		 * what table and what is the name of the field that we want to show.
		 * last part is IDFIELD/IDVALUE that works as extra filter ex. we want
		 * to display all USERNAME(idgetfield) from table USERS(idtable) that
		 * are of TYPE(idfield) INTERNAL(idvalue)
		 */
		JsonArray jarr = new JsonArray();
		JsonObject jLeaf = null;
		Gson gson = new Gson();
		DbSearchExpression expr = null;
		if (guiMetadata != null && !"".equals(guiMetadata))
			try {
				JsonObject jsonObj = gson.fromJson(guiMetadata, JsonObject.class);
				JsonObject jsonreactGUI = null;
				if (jsonObj != null && jsonObj.has(Rc.REACT))
					jsonreactGUI = (JsonObject) jsonObj.get(Rc.REACT);
				if (jsonreactGUI != null && jsonreactGUI.has(Rc.IDTABLE) && jsonreactGUI.has(Rc.IDGETFIELD)) {
					DbDataObject tableObject = SvCore.getDbtByName(jsonreactGUI.get(Rc.IDTABLE).getAsString());
					if (jsonreactGUI.has("idfield") && jsonreactGUI.has("idvalue")) {
						DbSearchCriterion critU = new DbSearchCriterion(jsonreactGUI.get("idfield").getAsString(),
								DbCompareOperand.EQUAL, jsonreactGUI.get("idvalue").getAsString());
						expr = new DbSearchExpression();
						expr.addDbSearchItem(critU);
					}
					DbDataArray vData = svr.getObjects(expr, tableObject.getObject_id(), null, 0, 0);
					String tmpLocale = getLocaleId(svr);
					String tmpStrValue = jsonreactGUI.get(Rc.IDGETFIELD).getAsString();
					Long dontTranslate1 = SvCore.getTypeIdByName("MINERALS_SUBJECTS");
					Long dontTranslate2 = SvCore.getTypeIdByName("LAND_USE_CODE");
					for (int j = 0; j < vData.getItems().size(); j++) {
						jLeaf = new JsonObject();
						String tmpString = vData.getItems().get(j).getObject_id().toString();
						if (fieldType.equals(Rc.NVARCHAR)) {
							jLeaf.addProperty(Rc.ID, tmpString);
							jLeaf.addProperty(Rc.VALUE_LC, tmpString);
						} else if (fieldType.equals(Rc.NUMERIC)) {
							jLeaf.addProperty(Rc.ID, Long.parseLong(tmpString));
							jLeaf.addProperty(Rc.VALUE_LC, Long.parseLong(tmpString));
						}
						if (tableObject.getObject_id().equals(dontTranslate1)
								|| tableObject.getObject_id().equals(dontTranslate2)) {
							tmpString = vData.getItems().get(j).getVal(tmpStrValue).toString();
						} else
							tmpString = I18n.getText(tmpLocale, vData.getItems().get(j).getVal(tmpStrValue).toString());
						jLeaf.addProperty(Rc.TITLE, tmpString);
						jLeaf.addProperty(Rc.TEXT, tmpString);
						jarr.add(jLeaf);
					}
				}
			} catch (Exception e) {
				debugException(e);
			}
		return jarr;
	}

	/**
	 * procedure to produce JsonObject with list of codes from SVAROG_CODES with
	 * DbDataObject
	 * 
	 * create list of drop-down pairs Id - Label to be displayed as translation
	 * to the ID that we have in database. if the Code exist we get all the
	 * options that we need to display and put them in a string
	 * (optionsBuilder). we can use 2 types for drop-down but atm
	 * DropDownFormatter is the best. if the field is editable we must also add
	 * editorType with the same options that we used for Formatter
	 * 
	 * @param tmpField
	 *            DbDataObject Object for which we want to get the code-list
	 * @param formaterType
	 *            int Formatter type 1 is Auto-complete, 2 is drop down
	 *            formatter
	 * @param editableField
	 *            Boolean if set to true the field will be editable and the
	 *            dropdowns will be available, if set to false it will work as
	 *            translator from ID to display name
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonObject
	 */
	public static JsonObject prepareJsonCodeList1(DbDataObject tmpField, int formaterType, Boolean editableField,
			SvReader svr) {
		JsonObject jData = new JsonObject();
		if (tmpField != null) {
			Long plistCodeId = (Long) tmpField.getVal(Rc.CODE_LIST_ID);
			/*
			 * when using forms/documents type is string, but on normal table
			 * fields type could be number , or something else. we try to get
			 * list by code, if we get 0 results we also try by gui_metadata. if
			 * field can be edited we must include all codes in editorOptions,
			 * and put editorType
			 */
			String fieldType = Rc.NVARCHAR;
			if (tmpField.getVal(Rc.FIELD_TYPE) != null)
				fieldType = tmpField.getVal(Rc.FIELD_TYPE).toString();
			String guiMetadata = "";
			if (tmpField.getVal(Rc.GUI_METADATA) != null)
				guiMetadata = tmpField.getVal(Rc.GUI_METADATA).toString();
			JsonArray jarr = prepareJsonCodeListById(plistCodeId, fieldType, svr);
			if (jarr.size() == 0)
				jarr = prepareJsonCodeListByMetadata(guiMetadata, fieldType, svr);
			if (jarr.size() > 0) {
				switch (formaterType) {
				case 1:
					jData.addProperty("editorType", "AutoCompleteEditor");
					break;
				case 2:
					jData.addProperty("formatterType", "DropDownFormatter");
					if (editableField)
						jData.addProperty("editorType", "DropDownEditor");
					break;
				default:
				}
				if (editableField)
					jData.add("editorOptions", jarr);
				jData.add("formatterOptions", jarr);
			}
		}
		return jData;
	}

	/**
	 * procedure to produce string of list of codes from SVAROG_CODES with given
	 * ID. it looks like it has similar data as prepareJsonCodeList, but the
	 * point is that order of IDs and values must be preserved, we take that ID
	 * of the list is valid number, we check if field is number, string or
	 * Boolean and generate the list of IDs
	 * 
	 * @param tmpField
	 *            DbDataObject field from SVAROG_FIELDS that we like to process,
	 *            generate list of IDs/names
	 * @param jsonObj
	 *            JsonObject Json Object that contain the generated object
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonObject with added 2 extra lists, od same object if there was
	 *         an error
	 */
	private JsonObject prepareFormJsonCodeListByID(DbDataObject tmpField, JsonObject jsonObj, SvReader svr) {
		Long plistCodeId = (Long) tmpField.getVal(Rc.CODE_LIST_ID);
		String fieldType = tmpField.getVal(Rc.FIELD_TYPE).toString();
		ArrayList<Long> listIDNumber = new ArrayList<>();
		ArrayList<String> listIDString = new ArrayList<>();
		ArrayList<String> listNames = new ArrayList<>();
		ArrayList<Boolean> listBoolean = new ArrayList<>();
		ArrayList<String> sortList = new ArrayList<>();
		JsonArray arrJson = new JsonArray();
		JsonArray arrNames = new JsonArray();
		Gson gson = new Gson();
		CodeList cl = null;
		JsonObject jsonObjRet = jsonObj;
		HashMap<String, String> listMap = null;
		try {
			cl = new CodeList(svr);

			listMap = cl.getCodeList(getLocaleId(svr), plistCodeId, true);
			Iterator<Entry<String, String>> it = listMap.entrySet().iterator();
			while (it.hasNext()) {
				HashMap.Entry pair = it.next();
				sortList.add(pair.getValue().toString());
			}
			// do not sort these types of dropdowns they will stay sorted by
			// sort order
			if (!tmpField.getVal(Rc.LABEL_CODE).toString().toLowerCase().contains(".month_milk")
					&& !tmpField.getVal(Rc.LABEL_CODE).toString().toLowerCase().contains(".month_laying_hens"))
				Collections.sort(sortList);
		} catch (SvException e) {
			debugSvException(e);
		} finally {
			if (cl != null)
				cl.release();
		}
		if (listMap != null && listMap.size() > 0) { // 04.09.2017
			Iterator<Entry<String, String>> it = listMap.entrySet().iterator();
			for (String temp : sortList) {
				it = listMap.entrySet().iterator();
				while (it.hasNext()) {
					Entry<String, String> pair = it.next();
					if (temp.equalsIgnoreCase(pair.getValue())) {
						it.remove();
						if ("true".equalsIgnoreCase(pair.getKey())) {
							listBoolean.add(true);
						} else if ("false".equalsIgnoreCase(pair.getKey())) {

							listBoolean.add(false);

						} else
							try {
								if (fieldType.equals(Rc.NUMERIC))
									listIDNumber.add(Long.parseLong(pair.getKey()));
								if (fieldType.equals(Rc.NVARCHAR))
									listIDString.add(pair.getKey());
							} catch (Exception ex) {
								debugException(ex);

								listIDString.add(pair.getKey());
							}
						listNames.add(pair.getValue());
					}
				}
			}
			if (!listNames.isEmpty()) {
				arrNames = (JsonArray) gson.toJsonTree(listNames);
				if (!listBoolean.isEmpty())
					arrJson = (JsonArray) gson.toJsonTree(listBoolean);
				if (!listIDNumber.isEmpty())
					arrJson = (JsonArray) gson.toJsonTree(listIDNumber);
				if (!listIDString.isEmpty())
					arrJson = (JsonArray) gson.toJsonTree(listIDString);
				if (arrJson.size() > 0) {
					String tmpLeaf = jsonObj.toString();
					tmpLeaf = tmpLeaf.substring(0, tmpLeaf.length() - 1) + ",\"enum\":" + arrJson.toString()
							+ ",\"enumNames\":" + arrNames.toString() + '}';
					jsonObjRet = gson.fromJson(tmpLeaf, JsonObject.class);
				}
			}
		}

		return jsonObjRet;
	}

	/**
	 * procedure to produce string of list of codes from given table with one
	 * filter. it looks like it has similar data as prepareJsonCodeList, but the
	 * point is that order of IDs and values must be preserved
	 * 
	 * @param guiMetadata
	 *            String metadata for the field that may contain some codelist
	 * @param jsonObj
	 *            JsonObject object has all previous data for the field that we
	 *            are processing
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonObject
	 */
	private JsonObject prepareFormJsonCodeListByMetadata(String guiMetadata, JsonObject jsonObj, SvReader svr) {
		JsonObject jsonGUI = null;
		JsonObject jsonreactGUI = null;
		DbSearchExpression expr = new DbSearchExpression();
		ArrayList<Long> listIDs = new ArrayList<>();
		ArrayList<String> listNames = new ArrayList<>();
		Gson gson = new Gson();
		JsonObject jsonObjRet = jsonObj;
		try {
			jsonGUI = gson.fromJson(guiMetadata, JsonObject.class);
		} catch (Exception e) {
			debugException(e);
		}
		if (jsonGUI != null && jsonGUI.has(Rc.REACT))
			jsonreactGUI = (JsonObject) jsonGUI.get(Rc.REACT);
		if ((jsonreactGUI != null) && (jsonreactGUI.has(Rc.IDTABLE)) && (jsonreactGUI.has(Rc.IDGETFIELD))) {
			DbDataObject tableObject = SvCore.getDbtByName(jsonreactGUI.get(Rc.IDTABLE).getAsString());
			// TODO replace try catch with joson.has
			try {
				DbSearchCriterion critU = new DbSearchCriterion(jsonreactGUI.get("idfield").getAsString(),
						DbCompareOperand.EQUAL, jsonreactGUI.get("idvalue").getAsString());
				expr.addDbSearchItem(critU);
			} catch (Exception e) {
				debugException(e);
				expr = null;
			}
			DbDataArray vData = null;
			try {
				vData = svr.getObjects(expr, tableObject.getObject_id(), null, 0, 0);
			} catch (SvException e) {
				debugSvException(e);
			}
			if (vData != null && !vData.getItems().isEmpty()) {
				for (int j = 0; j < vData.getItems().size(); j++) {
					listIDs.add(vData.getItems().get(j).getObject_id());
					listNames.add(I18n.getText(getLocaleId(svr),
							vData.getItems().get(j).getVal(jsonreactGUI.get(Rc.IDGETFIELD).getAsString()).toString()));
				}
				JsonArray arrLongJson = (JsonArray) gson.toJsonTree(listIDs);
				JsonArray arrStrJson = (JsonArray) gson.toJsonTree(listNames);
				String tmpLeaf = jsonObj.toString();
				tmpLeaf = tmpLeaf.substring(0, tmpLeaf.length() - 1) + ",\"enum\":" + arrLongJson.toString()
						+ ",\"enumNames\":" + arrStrJson.toString() + '}';
				jsonObjRet = gson.fromJson(tmpLeaf, JsonObject.class);
			}
		}
		return jsonObjRet;
	}

	/**
	 * procedure get the code-list for the field. depending on what we have for
	 * field we call it by LIST_ID or GUI_METADATA
	 * 
	 * @param tmpFiled
	 *            DbDataObject field from SVAROG_FIELDS for which we generate
	 *            code-list (drop-down)
	 * @param jsonObj
	 *            JsonObject object has all previous data for the field that we
	 *            are processing, we just add some more to it and return it
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonObject with new added list
	 */
	private JsonObject prepareFormJsonCodeList1(DbDataObject tmpFiled, JsonObject jsonObj, SvReader svr) {
		// prepare the list from LIST_ID on the field, or from GUI_METADATA
		if (tmpFiled.getVal(Rc.CODE_LIST_ID) != null && (long) tmpFiled.getVal(Rc.CODE_LIST_ID) > 0)
			return prepareFormJsonCodeListByID(tmpFiled, jsonObj, svr);
		else if (tmpFiled.getVal(Rc.GUI_METADATA) != null)
			return prepareFormJsonCodeListByMetadata(tmpFiled.getVal(Rc.GUI_METADATA).toString(), jsonObj, svr);
		return jsonObj;
	}

	/**
	 * procedure to add fields for inserting geometry if there is field called
	 * GEOM in the table
	 * 
	 * @param jsonObj
	 *            JsonObject with UISchema that need geometry fields added
	 * 
	 * @return JsonObject with new added list
	 */
	private JsonObject prepareFormJsonGeometry(JsonObject jsonObj) {
		Gson gson = new Gson();
		try {
			String tmpJson = "{\"type\":\"array\",\"title\":";
			tmpJson = tmpJson + quotedstring(I18n.getText(SvConf.getDefaultLocale(), "geometry.polygon"));
			tmpJson = tmpJson
					+ ",\"items\":{\"type\":\"object\",\"properties\":{\"cordxvals\":{\"type\":\"string\",\"title\":";
			tmpJson = tmpJson + quotedstring(I18n.getText(SvConf.getDefaultLocale(), "geometry.coordx"));
			tmpJson = tmpJson + "},\"cordyvals\":{\"type\":\"string\",\"title\":";
			tmpJson = tmpJson + quotedstring(I18n.getText(SvConf.getDefaultLocale(), "geometry.coordy"));
			tmpJson = tmpJson + "}}}}";
			JsonObject jFields = gson.fromJson(tmpJson, JsonObject.class);
			jsonObj.add(Rc.MULTYPOLYARRAY, jFields);
		} catch (Exception e) {
			debugException(e);
		}
		return jsonObj;
	}

	/**
	 * procedure to add grouping of fields when table is displayed in
	 * form/document for editing
	 * 
	 * 
	 * @param jFields
	 *            JsonObject with fields for the entire table, we need this in
	 *            case group exist, so we add new values to it
	 * @param jLeaf
	 *            JsonObject for field that we are processing atm, we add tigs
	 *            to existing group and add some extra grouping stuff to it
	 * 
	 * @return JsonObject with added group for display in document/form
	 */
	private JsonObject prepareFormJsonGroup(DbDataObject tmpObject, JsonObject jFields, JsonObject jLeaf) {
		Gson gson = new Gson();
		String tmpField = tmpObject.getVal(Rc.FIELD_NAME).toString();
		Boolean grouppathfound = false;
		JsonObject jsonreactGUI = null;
		String groupPath = null;
		JsonObject groupValues = new JsonObject();
		JsonObject groupProperties;
		JsonObject guiMetadata = null;
		try {
			if (tmpObject.getVal(Rc.GUI_METADATA) != null)
				guiMetadata = gson.fromJson(tmpObject.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
		} catch (Exception e) {
			debugException(e);
		}
		// try to load already existing group /part
		if (guiMetadata != null && guiMetadata.has(Rc.REACT)) {
			jsonreactGUI = (JsonObject) guiMetadata.get(Rc.REACT);
			if (jsonreactGUI != null && jsonreactGUI.has(Rc.GROUPPATH))
				groupPath = jsonreactGUI.get(Rc.GROUPPATH).getAsString();
			if (groupPath != null) {
				if (jFields != null && jFields.has(groupPath))
					groupValues = (JsonObject) jFields.get(groupPath);
				if ((groupValues != null) && (groupValues.has(Rc.PROPERTIES)))
					groupProperties = (JsonObject) groupValues.get(Rc.PROPERTIES);
				else {
					groupValues = new JsonObject();
					groupProperties = new JsonObject();
				}
				grouppathfound = true;
				groupValues.addProperty(Rc.TYPE, Rc.OBJECT);
				groupValues.addProperty(Rc.TITLE, I18n.getText(SvConf.getDefaultLocale(), groupPath));
				groupProperties.add(tmpField, jLeaf);
				groupValues.add(Rc.PROPERTIES, groupProperties);
			}
		}
		if (jFields != null)
			if (grouppathfound)
				jFields.add(groupPath, groupValues);
			else
				jFields.add(tmpField, jLeaf);
		return jFields;
	}

	/**
	 * procedure to generate part of the Json strong for the SVAROG fields,
	 * fields are read from SVAROG_FIELDS, and set visible /editable by the
	 * GUI_METADATA field if there is codelist set in CODE_LIST_ID fielf we pull
	 * it and set it too
	 * 
	 * @param table_name
	 *            name of the table that needs to be translated
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonArray with all SVAROG fields
	 */
	public static JsonArray prapareSvarogFields1(String tableName, SvReader svr) {
		/*
		 * prepare all that can be displayed on the grid, we loop over all
		 * fields, read their GUI_METADATA and make them visible or hidden
		 * according to it, we also translate the label_code into label_text for
		 * better display
		 */
		return prapareSvarogFieldsFull(tableName, false, svr);
	}

	/**
	 * procedure to generate part of the Json strong for the SVAROG fields,
	 * fields are read from SVAROG_FIELDS, and set visible /editable by the
	 * GUI_METADATA field if there is codelist set in CODE_LIST_ID fielf we pull
	 * it and set it too
	 * 
	 * @param table_name
	 *            String name of the table that needs to be translated
	 * @param overrideShow
	 *            Boolean override the GUI_METADATA and show all fields (used
	 *            for object history)
	 * 
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonArray with all SVAROG fields
	 */
	public static JsonArray prapareSvarogFieldsFull(String tableName, Boolean overrideShow, SvReader svr) {
		/*
		 * prepare all that can be displayed on the grid, we loop over all
		 * fields, read their GUI_METADATA and make them visible or hidden
		 * according to it, we also translate the label_code into label_text for
		 * better display
		 */
		JsonArray jArray = new JsonArray();
		DbDataArray vsvarogfieldsArray = SvCore.getRepoDbtFields();
		JsonObject jsonObj = null;
		JsonObject jsonreactGUI = null;
		Gson gson = new Gson();
		if (vsvarogfieldsArray != null)
			for (DbDataObject field : vsvarogfieldsArray.getItems()) {
				JsonObject jLeaf = new JsonObject();
				jsonObj = null;
				jsonreactGUI = null;
				Boolean editableField = false;
				jLeaf.addProperty("key", tableName + "." + field.getVal(Rc.FIELD_NAME).toString());
				jLeaf.addProperty(Rc.TABLE_NAME, tableName);
				jLeaf.addProperty(Rc.FIELD_NAME, field.getVal(Rc.FIELD_NAME).toString());

				if ("DATE".equalsIgnoreCase(field.getVal(Rc.FIELD_TYPE).toString())
						|| "DATETIME".equalsIgnoreCase(field.getVal(Rc.FIELD_TYPE).toString())
						|| "TIMESTAMP".equalsIgnoreCase(field.getVal(Rc.FIELD_TYPE).toString()))
					jLeaf.addProperty("datetype", "longdate");

				// if field is found get the GUI metadata for it
				jLeaf.addProperty("name", I18n.getText(getLocaleId(svr), field.getVal(Rc.LABEL_CODE).toString()));
				try {
					if (field.getVal(Rc.GUI_METADATA) != null)
						jsonObj = gson.fromJson(field.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
				} catch (Exception e) {
					// there is no GUI_METADATA
				}
				if (jsonObj != null && jsonObj.has(Rc.REACT)) {
					jsonreactGUI = (JsonObject) jsonObj.get(Rc.REACT);
					if (jsonreactGUI != null && jsonreactGUI.has(Rc.EDITABLE))
						editableField = jsonreactGUI.get(Rc.EDITABLE).getAsBoolean();
					if (jsonreactGUI != null && jsonreactGUI.has(Rc.WIDTH))
						jLeaf.addProperty(Rc.WIDTH, jsonreactGUI.get(Rc.WIDTH).getAsLong());
					if (jsonreactGUI != null && jsonreactGUI.has(Rc.SORTABLE))
						jLeaf.addProperty(Rc.SORTABLE, jsonreactGUI.get(Rc.SORTABLE).getAsBoolean());
				} else {
					editableField = false;
				}
				// find if there is codelist on the field and translate it
				// to name/code if there is nothing return value is
				// empty string
				for (Map.Entry<String, JsonElement> entry : prepareJsonCodeList1(field, 2, editableField, svr)
						.entrySet())
					jLeaf.add(entry.getKey(), entry.getValue());
				// prepare and translate any GUI metadata that we found, if
				// there is none we prepare standard view/display
				for (Map.Entry<String, JsonElement> entry : prepareJsonGUI1(jsonreactGUI).entrySet()) {
					jLeaf.add(entry.getKey(), entry.getValue());
				}
				if (overrideShow || (jsonreactGUI != null && jsonreactGUI.has(Rc.VISIBLE)
						&& jsonreactGUI.get(Rc.VISIBLE).getAsBoolean()))
					jArray.add(jLeaf);
			}
		return jArray;
	}

	public static JsonObject prapareSvarogData(DbDataObject dbo, String tableName, JsonObject jBo) {
		/*
		 * get all svarog data for some object, for now we dont return the
		 * META_PKID since it is same as PKID, DT_DELETE since is always 9999,
		 * USER_ID since there is no need for him atm, this will generate
		 * smaller JSON response and it will be faster to generate
		 */
		jBo.addProperty(tableName + "." + Rc.PKID, dbo.getPkid());
		jBo.addProperty(tableName + "." + Rc.OBJECT_ID, dbo.getObject_id());
		jBo.addProperty(tableName + "." + Rc.PARENT_ID, dbo.getParent_id());
		jBo.addProperty(tableName + "." + Rc.OBJECT_TYPE, dbo.getObject_type());
		jBo.addProperty(tableName + "." + Rc.STATUS, dbo.getStatus());
		return jBo;
	}

	public static JsonObject prapareSvarogDataFull(DbDataObject dbo, String tableName, JsonObject jBo) {
		/*
		 * get all svarog data for some object, for now we dont return the
		 * META_PKID since it is same as PKID, DT_DELETE since is always 9999,
		 * USER_ID since there is no need for him atm, this will generate
		 * smaller JSON response and it will be faster to generate
		 */
		JsonObject jBo1 = prapareSvarogData(dbo, tableName, jBo);
		jBo1.addProperty(tableName + "." + Rc.USER_ID, dbo.getUser_id());
		jBo1.addProperty(tableName + "." + Rc.DT_INSERT, dbo.getDt_insert().toString());
		jBo1.addProperty(tableName + "." + Rc.DT_DELETE, dbo.getDt_delete().toString());
		return jBo1;
	}

	public static JsonObject prapareSvarogData(DbDataObject dbo, String tableName, int i, JsonObject jBo) {
		/*
		 * if table is part of query with multiple tables we get the data with
		 * TBL prefix, but if its no query we use normal table name from other
		 * prapareSvarogData procedure
		 */
		JsonObject jData = jBo;
		String tmpStr = Rc.TBL + i + "_";
		if (dbo.getVal(tmpStr + Rc.PKID) == null)
			jData = prapareSvarogData(dbo, tableName, jBo);
		else {
			if (dbo.getVal(tmpStr + Rc.PKID) != null)
				jData.addProperty(tableName + "." + Rc.PKID, (Long) dbo.getVal(tmpStr + Rc.PKID));
			if (dbo.getVal(tmpStr + Rc.OBJECT_ID) != null)
				jData.addProperty(tableName + "." + Rc.OBJECT_ID, (Long) dbo.getVal(tmpStr + Rc.OBJECT_ID));
			if (dbo.getVal(tmpStr + Rc.PARENT_ID) != null)
				jData.addProperty(tableName + "." + Rc.PARENT_ID, (Long) dbo.getVal(tmpStr + Rc.PARENT_ID));
			if (dbo.getVal(tmpStr + Rc.OBJECT_TYPE) != null)
				jData.addProperty(tableName + "." + Rc.OBJECT_TYPE, (Long) dbo.getVal(tmpStr + Rc.OBJECT_TYPE));
			if (dbo.getVal(tmpStr + Rc.STATUS) != null)
				jData.addProperty(tableName + "." + Rc.STATUS, dbo.getVal(tmpStr + Rc.STATUS).toString());
		}
		return jData;
	}

	public static JsonObject prapareSvarogDataFull(DbDataObject dbo, String tableName, int i, JsonObject jBo) {
		/*
		 * if table is part of query with multiple tables we get the data with
		 * TBL prefix, but if its no query we use normal table name from other
		 * prapareSvarogData procedure
		 */
		JsonObject jData = jBo;
		String tmpStr = Rc.TBL + i + "_";
		if (dbo.getVal(tmpStr + Rc.PKID) == null)
			jData = prapareSvarogDataFull(dbo, tableName, jBo);
		else {
			if (dbo.getVal(tmpStr + Rc.PKID) != null)
				jData.addProperty(tableName + "." + Rc.PKID, (Long) dbo.getVal(tmpStr + Rc.PKID));
			if (dbo.getVal(tmpStr + Rc.META_PKID) != null)
				jData.addProperty(tableName + "." + Rc.META_PKID, (Long) dbo.getVal(tmpStr + Rc.META_PKID));
			if (dbo.getVal(tmpStr + Rc.OBJECT_ID) != null)
				jData.addProperty(tableName + "." + Rc.OBJECT_ID, (Long) dbo.getVal(tmpStr + Rc.OBJECT_ID));
			if (dbo.getVal(tmpStr + Rc.PARENT_ID) != null)
				jData.addProperty(tableName + "." + Rc.PARENT_ID, (Long) dbo.getVal(tmpStr + Rc.PARENT_ID));
			if (dbo.getVal(tmpStr + Rc.OBJECT_TYPE) != null)
				jData.addProperty(tableName + "." + Rc.OBJECT_TYPE, (Long) dbo.getVal(tmpStr + Rc.OBJECT_TYPE));
			if (dbo.getVal(tmpStr + Rc.STATUS) != null)
				jData.addProperty(tableName + "." + Rc.STATUS, dbo.getVal(tmpStr + Rc.STATUS).toString());
			if (dbo.getVal(tmpStr + Rc.USER_ID) != null)
				jData.addProperty(tableName + "." + Rc.USER_ID, (Long) dbo.getVal(tmpStr + Rc.USER_ID));
			if (dbo.getVal(tmpStr + Rc.DT_DELETE) != null)
				jData.addProperty(tableName + "." + Rc.DT_DELETE, dbo.getVal(tmpStr + Rc.DT_DELETE).toString());
			if (dbo.getVal(tmpStr + Rc.DT_INSERT) != null)
				jData.addProperty(tableName + "." + Rc.DT_INSERT, dbo.getVal(tmpStr + Rc.DT_INSERT).toString());
		}
		return jData;
	}

	/**
	 * procedure to generate part of the Json string for the data that is part
	 * of SVAROG core
	 * 
	 * @param dbo
	 *            DbDataObject Object that needs to be displayed
	 * @param table_nme
	 *            String name of the table that will be added as prefix to field
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return String for Json with all SVAROG data returned
	 */
	/*
	 * get all svarog data for some object, for now we dont return the META_PKID
	 * since it is same as PKID, DT_DELETE since is always 9999, USER_ID since
	 * there is no need for him atm, this will generate smaller JSON response
	 * and it will be faster to generate
	 */
	static final String prapareSvarogData(DbDataObject dbo, String tableName) {
		String retString = quotedstring(tableName + ".PKID") + ": " + quotedstring(dbo.getPkid()) + ", ";
		retString = retString + quotedstring(tableName + ".OBJECT_ID") + ": " + quotedstring(dbo.getObject_id()) + ", ";
		retString = retString + quotedstring(tableName + ".PARENT_ID") + ": " + quotedstring(dbo.getParent_id()) + ", ";
		retString = retString + quotedstring(tableName + ".OBJECT_TYPE") + ": " + quotedstring(dbo.getObject_type())
				+ ", ";
		retString = retString + quotedstring(tableName + ".STATUS") + ": " + quotedstring(dbo.getStatus()) + ", ";
		return retString;
	}

	/**
	 * procedure to generate part of the Json for the fieldList that will be
	 * used when editing fields of forms/documents this will also get all codes
	 * for the dropdowns and get all relevant data if there is GUI_METADATA for
	 * the field
	 * 
	 * @param key
	 *            String used when transposing fields of the form, it is the key
	 *            for all fields of that type
	 * @param oField
	 *            DbDataObject from SVAROG_FIELDS that we want to be displayed
	 * @param forceEdit
	 *            Boolean override make the field editable even if there is no
	 *            such thing in GUI_METADATA
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonObject with data from oData Object
	 */
	/*
	 * used by forms when we transpose the fields of every form/document , based
	 * on what is the access to the form/document ( normal , control) we
	 * override the edit options for this type of field
	 */
	public static JsonObject prapareFormField1(String key, DbDataObject oField, Boolean forceEdit, SvReader svr) {
		JsonObject jsonreactGUI = null;
		JsonObject jObjRet = null;
		Gson gson = new Gson();
		JsonObject jsonObj = null;
		try {
			if (oField.getVal(Rc.GUI_METADATA) != null)
				jsonObj = gson.fromJson(oField.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
		} catch (Exception e) {
			debugException(e);
		}
		if (jsonObj != null && jsonObj.has("react")) {
			jsonreactGUI = (JsonObject) jsonObj.get("react");
			if (jsonreactGUI != null && jsonreactGUI.has("visible") && jsonreactGUI.get("visible").getAsBoolean()) {
				jObjRet = new JsonObject();
				jObjRet.addProperty("key", key.toUpperCase());
				String displayname = I18n.getText(getLocaleId(svr), oField.getVal(Rc.LABEL_CODE).toString());
				if (key.toUpperCase().contains("_1ST"))
					displayname = displayname + I18n.getText(getLocaleId(svr), "form.field.first_check");
				if (key.toUpperCase().contains("_2ND"))
					displayname = displayname + I18n.getText(getLocaleId(svr), "form.field.second_check");
				jObjRet.addProperty("name", displayname);
				jObjRet.addProperty("editable", forceEdit);
				if (Rc.DATE.equalsIgnoreCase(oField.getVal(Rc.FIELD_TYPE).toString()))
					jObjRet.addProperty("datetype", "shortdate");
				if (Rc.DATETIME.equalsIgnoreCase(oField.getVal(Rc.FIELD_TYPE).toString())
						|| Rc.TIMESTAMP.equalsIgnoreCase(oField.getVal(Rc.FIELD_TYPE).toString()))
					jObjRet.addProperty("datetype", "longdate");
				JsonObject tmpJObj = WsReactElements.prepareJsonCodeList1(oField, 2, true, svr);
				for (Entry<String, JsonElement> entry : tmpJObj.entrySet())
					jObjRet.add(entry.getKey(), entry.getValue());
				tmpJObj = WsReactElements.prepareJsonGUI1(jsonreactGUI);
				for (Entry<String, JsonElement> entry : tmpJObj.entrySet())
					jObjRet.add(entry.getKey(), entry.getValue());
			}
		}
		return jObjRet;
	}

	/**
	 * procedure to generate part of the Json for the fieldList that will be on
	 * top of every grid, this will also get all codes for the dropdowns and get
	 * all relevant data if there is GUI_METADATA for te field
	 * 
	 * @param table_name
	 *            String what is the table name that this filed is part of
	 * @param oField
	 *            DbDataObject from SVAROG_FIELDS that can
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JsonObject with data from oData Object
	 */
	public static JsonObject prapareObjectField1(String tableName, DbDataObject oField, SvReader svr) {
		/*
		 * prepare the FieldList part of the field object, all the drop-downs or
		 * edit options, and all properties that can be read from GUI_METADATA
		 * of the field or from CODE_LIST_ID if it exist for the field, in the
		 * end
		 */
		JsonObject jsonreactGUI = null;
		Long width = 0L;
		Gson gson = new Gson();
		Boolean visiblefield = true;
		Boolean sortablefield = false;
		JsonObject jData = new JsonObject();
		// try to get react metadata and visibility of the field
		JsonObject jsonObj = null;
		try {
			if (oField.getVal(Rc.GUI_METADATA) != null && !"".equals(oField.getVal(Rc.GUI_METADATA)))
				jsonObj = gson.fromJson(oField.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
		} catch (Exception e) {
			debugException(e);
		}
		if (jsonObj != null && jsonObj.has(Rc.REACT)) {
			jsonreactGUI = (JsonObject) jsonObj.get(Rc.REACT);
			if (jsonreactGUI != null && jsonreactGUI.has(Rc.VISIBLE))
				visiblefield = jsonreactGUI.get(Rc.VISIBLE).getAsBoolean();
			if (jsonreactGUI != null && jsonreactGUI.has(Rc.WIDTH))
				width = jsonreactGUI.get(Rc.WIDTH).getAsLong();
			if (jsonreactGUI != null && jsonreactGUI.has(Rc.SORTABLE))
				sortablefield = jsonreactGUI.get(Rc.SORTABLE).getAsBoolean();
		} else {
			// if there is no react metadata we just show the field :(
			jsonreactGUI = null;
			visiblefield = true;
		}
		if (visiblefield) {
			jData.addProperty("key", tableName + "." + oField.getVal(Rc.FIELD_NAME));
			jData.addProperty(Rc.TABLE_NAME, tableName);
			jData.addProperty(Rc.FIELD_NAME, oField.getVal(Rc.FIELD_NAME).toString());
			String fieldType = "";
			if (oField.getVal(Rc.FIELD_TYPE) != null)
				fieldType = oField.getVal(Rc.FIELD_TYPE).toString();
			switch (fieldType.toUpperCase()) {
			case "DATE":
				jData.addProperty("datetype", "shortdate");
				break;
			case "TIMESTAMP":
			case "DATETIME":
				jData.addProperty("datetype", "longdate");
				break;
			default:
			}
			jData.addProperty("name", I18n.getText(getLocaleId(svr), oField.getVal(Rc.LABEL_CODE).toString()));
			if (width > 0)
				jData.addProperty(Rc.WIDTH, width);
			if (sortablefield)
				jData.addProperty(Rc.SORTABLE, sortablefield);
			for (Map.Entry<String, JsonElement> entry : prepareJsonCodeList1(oField, 2, true, svr).entrySet()) {
				jData.add(entry.getKey(), entry.getValue());
			}
			for (Map.Entry<String, JsonElement> entry : prepareJsonGUI1(jsonreactGUI).entrySet()) {
				jData.add(entry.getKey(), entry.getValue());
			}
		}
		return jData;
	}

	/**
	 * procedure to generate part of the Json string for the data that is part
	 * of SVAROG core, overloaded version used when we make join query
	 * 
	 * @param vData
	 *            ArrayList<DbDataObject> this is where all data from the query
	 *            is stored, it works best for sorted list
	 * @param tablesUsedArray
	 *            Array of String array of tables used in the query, that MUST
	 *            be in same order as used in building the query
	 * @param tableShowArray
	 *            Array of Boolean , to save on some time and string/Json size,
	 *            we can hide full tables that don't have anything for display
	 * @param tablesusedCount
	 *            int , when we make svarog join every table is renamed to
	 *            TBL[i] , this will tell us how many tables are we joining so
	 *            we don't go out of index
	 * @param doTranslate
	 *            Boolean if set to TRUE it will translate all label_codes, so
	 *            it should be TRUE most of the time except in administrative
	 *            console where we have to set the codes
	 * @param svr
	 *            connected SvReader
	 * @param isfullSvarogData
	 *            Boolean if set to TRUE it will return full REPO field data,
	 *            FALSE will return whatever is set in the REPO fields
	 *            GUI_METADATA
	 * 
	 * @return JsonArray with all data
	 */
	public static JsonArray prapareTableQueryDataV1(List<DbDataObject> vData, String[] tablesUsedArray,
			Boolean[] tableShowArray, int tablesusedCount, Boolean doTranslate, SvReader svr,
			Boolean isfullSvarogData) {
		JsonArray jarr = new JsonArray();
		if (vData != null && !vData.isEmpty())
			for (int j = 0; j < vData.size(); j++) {
				/*
				 * DbDataObject obj1 = vData.get(j); JsonObject jData = new
				 * JsonObject(); for (int k = 0; k < tablesusedCount; k++) if
				 * (tableShowArray[k]) { DbDataObject tableObject =
				 * SvCore.getDbtByName(tablesUsedArray[k]); DbDataArray
				 * typetoGet = SvCore.getFields(tableObject.getObject_id()); if
				 * (isfullSvarogData) jData = prapareSvarogDataFull(obj1,
				 * tablesUsedArray[k], k, jData); else jData =
				 * prapareSvarogData(obj1, tablesUsedArray[k], k, jData); for
				 * (int i = 0; i < typetoGet.getItems().size(); i++) { String
				 * tmpField =
				 * typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString();
				 * String fieldToRead = ""; if (tablesusedCount != 1)
				 * fieldToRead = "tbl" + k + "_"; fieldToRead = fieldToRead +
				 * tmpField; String readField = tablesUsedArray[k] + "." +
				 * tmpField; jData = addValueToJsonObject2(jData, obj1,
				 * typetoGet.getItems().get(i), fieldToRead, readField,
				 * doTranslate, svr); } }
				 */
				jarr.add(prapareTableQueryJsonObject(vData.get(j), tablesUsedArray, tableShowArray, tablesusedCount,
						doTranslate, svr, isfullSvarogData));
			}
		return jarr;
	}

	/**
	 * procedure to generate part of the Json string for the data that is part
	 * of SVAROG core, overloaded version used when we make join query
	 * 
	 * @param vData
	 *            DbDataArray this is where all data from the query is stored
	 * @param tablesUsedArray
	 *            Array of String array of tables used in the query, that MUST
	 *            be in same order as used in building the query
	 * @param tableShowArray
	 *            Array of Boolean , to save on some time and string/Json size,
	 *            we can hide full tables that don't have anything for display
	 * @param tablesusedCount
	 *            int , when we make svarog join every table is renamed to
	 *            TBL[i] , this will tell us how many tables are we joining so
	 *            we don't go out of index
	 * @param doTranslate
	 *            Boolean if set to TRUE it will translate all label_codes, so
	 *            it should be TRUE most of the time except in administrative
	 *            console where we have to set the codes
	 * @param svr
	 *            connected SvReader
	 * @param isfullSvarogData
	 *            Boolean if set to TRUE it will return full REPO field data,
	 *            FALSE will return whatever is set in the REPO fields
	 *            GUI_METADATA
	 * 
	 * @return JSON JsonArray with data from VData array
	 */
	public static JsonArray prapareTableQueryData(DbDataArray vData, String[] tablesUsedArray, Boolean[] tableShowArray,
			int tablesusedCount, Boolean doTranslate, SvReader svr, Boolean isfullSvarogData) {
		JsonArray jarr = new JsonArray();
		if (vData != null && vData.size() > 0)
			for (int j = 0; j < vData.getItems().size(); j++) {
				/*
				 * JsonObject jData = new JsonObject(); for (int k = 0; k <
				 * tablesusedCount; k++) if (tableShowArray[k]) { DbDataObject
				 * tableObject = SvCore.getDbtByName(tablesUsedArray[k]);
				 * DbDataArray typetoGet =
				 * SvCore.getFields(tableObject.getObject_id()); if
				 * (isfullSvarogData) jData = prapareSvarogDataFull(obj1,
				 * tablesUsedArray[k], k, jData); else jData =
				 * prapareSvarogData(obj1, tablesUsedArray[k], k, jData); for
				 * (int i = 0; i < typetoGet.getItems().size(); i++) { String
				 * tmpField =
				 * typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString();
				 * String fieldToRead = ""; if (tablesusedCount != 1)
				 * fieldToRead = "tbl" + k + "_"; fieldToRead = fieldToRead +
				 * tmpField; String readField = tablesUsedArray[k] + "." +
				 * tmpField; jData = addValueToJsonObject2(jData, obj1,
				 * typetoGet.getItems().get(i), fieldToRead, readField,
				 * doTranslate, svr); } }
				 */
				jarr.add(prapareTableQueryJsonObject(vData.getItems().get(j), tablesUsedArray, tableShowArray,
						tablesusedCount, doTranslate, svr, isfullSvarogData));
			}
		return jarr;
	}

	private static JsonObject prapareTableQueryJsonObject(DbDataObject obj1, String[] tablesUsedArray,
			Boolean[] tableShowArray, int tablesusedCount, Boolean doTranslate, SvReader svr,
			Boolean isfullSvarogData) {
		JsonObject jData = new JsonObject();
		for (int k = 0; k < tablesusedCount; k++)
			if (tableShowArray[k]) {
				DbDataObject tableObject = SvCore.getDbtByName(tablesUsedArray[k]);
				DbDataArray typetoGet = SvCore.getFields(tableObject.getObject_id());
				if (isfullSvarogData)
					jData = prapareSvarogDataFull(obj1, tablesUsedArray[k], k, jData);
				else
					jData = prapareSvarogData(obj1, tablesUsedArray[k], k, jData);
				for (int i = 0; i < typetoGet.getItems().size(); i++) {
					String tmpField = typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString();
					String fieldToRead = "";
					if (tablesusedCount != 1)
						fieldToRead = "tbl" + k + "_";
					fieldToRead = fieldToRead + tmpField;
					String readField = tablesUsedArray[k] + "." + tmpField;
					jData = addValueToJsonObject2(jData, obj1, typetoGet.getItems().get(i), fieldToRead, readField,
							doTranslate, svr);
				}
			}
		return jData;
	}

	/**
	 * procedure to generate part of the Json string for the data that is part
	 * of SVAROG core, overloaded version used when we make join query
	 * 
	 * @param vData
	 *            DbDataArray this is where all data from the query is stored
	 * @param tablesUsedArray
	 *            Array of String array of tables used in the query, that MUST
	 *            be in same order as used in building the query
	 * @param tableShowArray
	 *            Array of Boolean , to save on some time and string/Json size,
	 *            we can hide full tables that don't have anything for display
	 * @param i
	 *            int , when we make svarog join every table is renamed to
	 *            TBL[i] , this will tell us how many tables are we joining so
	 *            we don't go out of index
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JSON String with data from VData array
	 */
	public static String prapareTableQueryData(DbDataArray vData, String[] tablesUsedArray, Boolean[] tableShowArray,
			int tablesusedCount, Boolean doTranslate, SvReader svr) {
		/*
		 * this will loop over all rows, all tables used in every row, all
		 * fields in tables and generate json string with all data that we found
		 * so we can display it in the grid . if there is no data for the field,
		 * we dont add key for the field, so we save on some space ; vData can
		 * be simple table or query so try to get the field value, if we have a
		 * query of more tables use the TBL[i]_tmpField else try to get via
		 * tmpField only
		 */
		return prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, doTranslate, svr, false)
				.toString();
		/*
		 * JsonObject jData = null; JsonArray jarr = new JsonArray();
		 * DbDataObject tableObject = null; DbDataArray typetoGet = null; if
		 * (vData != null && vData.size() > 0) for (int j = 0; j <
		 * vData.getItems().size(); j++) { jData = new JsonObject(); for (int k
		 * = 0; k < tablesusedCount; k++) if (tableShowArray[k]) { tableObject =
		 * SvCore.getDbtByName(tablesUsedArray[k]); typetoGet =
		 * SvCore.getFields(tableObject.getObject_id()); jData =
		 * prapareSvarogData(vData.getItems().get(j), tablesUsedArray[k], k,
		 * jData); for (int i = 0; i < typetoGet.getItems().size(); i++) {
		 * String tmpField =
		 * typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString(); String
		 * fieldToRead = ""; if (tablesusedCount != 1) fieldToRead = "tbl" + k +
		 * "_"; fieldToRead = fieldToRead + tmpField; String readField =
		 * tablesUsedArray[k] + "." + tmpField; jData =
		 * addValueToJsonObject2(jData, vData.getItems().get(j),
		 * typetoGet.getItems().get(i), fieldToRead, readField, doTranslate,
		 * svr); } } jarr.add(jData); } return jarr.toString();
		 */
	}

	/**
	 * procedure to generate part of the Json string for the data that is part
	 * of SVAROG core, overloaded version used when we make join query, this
	 * will also return all 9 svarog fields
	 * 
	 * @param vData
	 *            DbDataArray this is where all data from the query is stored
	 * @param tablesUsedArray
	 *            Array of String array of tables used in the query, that MUST
	 *            be in same order as used in building the query
	 * @param tableShowArray
	 *            Array of Boolean , to save on some time and string/Json size,
	 *            we can hide full tables that don't have anything for display
	 * @param i
	 *            int , when we make svarog join every table is renamed to
	 *            TBL[i] , this will tell us how many tables are we joining so
	 *            we don't go out of index
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return JSON String with data from VData array
	 */
	public static String prapareTableQueryDataFull(DbDataArray vData, String[] tablesUsedArray,
			Boolean[] tableShowArray, int tablesusedCount, Boolean doTranslate, SvReader svr) {
		/*
		 * this will loop over all rows, all tables used in every row, all
		 * fields in tables and generate json string with all data that we found
		 * so we can display it in the grid . if there is no data for the field,
		 * we dont add key for the field, so we save on some space ; vData can
		 * be simple table or query so try to get the field value, if we have a
		 * query of more tables use the TBL[i]_tmpField else try to get via
		 * tmpField only
		 */
		return prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, doTranslate, svr, true)
				.toString();
		/*
		 * JsonObject jData = null; JsonArray jarr = new JsonArray();
		 * DbDataObject tableObject = null; DbDataArray typetoGet = null; if
		 * (vData != null && vData.size() > 0) for (int j = 0; j <
		 * vData.getItems().size(); j++) { jData = new JsonObject(); for (int k
		 * = 0; k < tablesusedCount; k++) if (tableShowArray[k]) { tableObject =
		 * SvCore.getDbtByName(tablesUsedArray[k]); typetoGet =
		 * SvCore.getFields(tableObject.getObject_id()); jData =
		 * prapareSvarogDataFull(vData.getItems().get(j), tablesUsedArray[k], k,
		 * jData); for (int i = 0; i < typetoGet.getItems().size(); i++) {
		 * String tmpField =
		 * typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString(); String
		 * fieldToRead = ""; if (tablesusedCount != 1) fieldToRead = "tbl" + k +
		 * "_"; fieldToRead = fieldToRead + tmpField; String readField =
		 * tablesUsedArray[k] + "." + tmpField; jData =
		 * addValueToJsonObject2(jData, vData.getItems().get(j),
		 * typetoGet.getItems().get(i), fieldToRead, readField, doTranslate,
		 * svr); } } jarr.add(jData); } return jarr.toString();
		 */
	}

	/**
	 * procedure to generate all the fields for X tables and Fields that had to
	 * be shown in the top Row
	 * 
	 * @param tablesUsedArray
	 *            String[] list of all the table names that have to be displayed
	 * @param svarogShowArray
	 *            String[] boolean list , of all the SVAROG parts that have o be
	 *            displayed
	 * @param tableShowArray
	 *            String[] boolean list if the table data should be shown od
	 *            hidden
	 * 
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return DbDataObject that is master of the drop-down true/false codelist
	 */
	public JsonArray prepareJsonArrayFromGrid(String[] tablesUsedArray, Boolean[] svarogShowArray,
			Boolean[] tableShowArray, SvReader svr) {
		JsonArray jArray = new JsonArray();
		int tablesusedCount = 0;
		for (int k = 0; k < tablesUsedArray.length; k++)
			if (tablesUsedArray[k] != null)
				tablesusedCount++;
		try {
			DbDataArray typetoGet = null;
			// loop the 3 tables
			for (int k = 0; k < tablesusedCount; k++)
				if (tableShowArray[k]) {
					if (svarogShowArray[k]) {
						JsonArray tmpJArray = prapareSvarogFields1(tablesUsedArray[k], svr);
						for (int i = 0; i < tmpJArray.size(); i++)
							jArray.add((JsonObject) tmpJArray.get(i));
					}
					// loop the fields of every table
					typetoGet = svr.getObjectsByParentId(SvCore.getDbtByName(tablesUsedArray[k]).getObject_id(),
							SvCore.getTypeIdByName("SVAROG_FIELDS", null), null, 0, 0);
					for (int i = 0; i < typetoGet.getItems().size(); i++)
						if (processField(typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString()))
							jArray.add(prapareObjectField1(tablesUsedArray[k], typetoGet.getItems().get(i), svr));
				}
		} catch (SvException e) {
			debugSvException(e);
		}
		return jArray;
	}

	/**
	 * procedure to add value to JsonObject one field value from record from
	 * table, this will not process svarog repo fields
	 * 
	 * @param jsonData
	 *            JsonObject object in which we want to save the data
	 * @param recordObject
	 *            DbDataObject object from which we read the data
	 * @param tmpField
	 *            DbDataObject object from SVAROG_FIELDS that will describe the
	 *            field that we try to save
	 * 
	 * @return JSON String with data from oData Object
	 */
	private JsonObject addValueToJsonObject1(JsonObject jsonData, DbDataObject recordObject, DbDataObject tmpField) {
		/*
		 * as input we get empty or json with some filled fields (svarog core)
		 * then we save that object for future use ,and we try to process only
		 * the field that is specified with tmpField. New added feature is split
		 * the table into sub-parts and add fields to groups , so if this field
		 * is first in its group it will create the group and be added as group
		 * to the "object saved for future use", if its not part of the group it
		 * will be added in the root of the json
		 */
		JsonObject saveData = jsonData;
		JsonObject jsonDataForWork = jsonData;
		String fieldName = tmpField.getVal(Rc.FIELD_NAME).toString();
		Long scale = (Long) tmpField.getVal(Rc.FIELD_SCALE);
		String fieldType = tmpField.getVal(Rc.FIELD_TYPE).toString();
		String pathString = "";
		try {
			Gson gson = new Gson();
			JsonObject guiMetadata = null;
			if (tmpField.getVal(Rc.GUI_METADATA) != null) {
				guiMetadata = gson.fromJson(tmpField.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
				JsonObject jsonreactGUI = (JsonObject) guiMetadata.get(Rc.REACT);
				pathString = jsonreactGUI.get(Rc.GROUPPATH).getAsString();
				jsonDataForWork = (JsonObject) saveData.get(pathString);
			}
			if (jsonDataForWork == null)
				jsonDataForWork = new JsonObject();
		} catch (Exception e) {
			debugException(e);
			pathString = "";
		}
		if (jsonDataForWork != null)
			try {
				if (recordObject.getVal(fieldName) != null) {
					switch (fieldType) {
					case Rc.NVARCHAR:
						if (recordObject.getVal(fieldName) != null)
							jsonDataForWork.addProperty(fieldName, recordObject.getVal(fieldName).toString());
						break;
					case Rc.NUMERIC:
						if (scale == null || scale <= 0) {
							Long tmpL = Long.valueOf(recordObject.getVal(fieldName).toString());
							if (tmpL != null)
								jsonDataForWork.addProperty(fieldName, tmpL);
						} else {
							Double tmpD = Double.valueOf(recordObject.getVal(fieldName).toString());
							if (tmpD != null)
								jsonDataForWork.addProperty(fieldName, tmpD);
						}
						break;
					case Rc.BOOLEAN:
						Boolean tmpB = (Boolean) recordObject.getVal(fieldName);
						if (tmpB != null)
							jsonDataForWork.addProperty(fieldName, tmpB);
						break;
					case Rc.DATE: // short date
						DateTime tmpDsh = null;
						if (recordObject.getVal(fieldName) != null) {
							tmpDsh = new DateTime(recordObject.getVal(fieldName));
						}

						if (tmpDsh != null) {
							int monthInt = tmpDsh.monthOfYear().get();
							int dayInt = tmpDsh.dayOfMonth().get();
							String monthStr = ((monthInt < 10) ? "0" : "") + String.valueOf(monthInt);
							String dayStr = ((dayInt < 10) ? "0" : "") + String.valueOf(dayInt);
							jsonDataForWork.addProperty(fieldName, tmpDsh.year().get() + "-" + monthStr + "-" + dayStr);
						}
						break;
					case Rc.TIMESTAMP:
					case Rc.DATETIME: // long date with time
						DateTime tmpDl = (DateTime) recordObject.getVal(fieldName);
						if (tmpDl != null)
							jsonDataForWork.addProperty(fieldName, tmpDl.toString());
						break;
					default:
					}
				}
			} catch (Exception e) {
				debugException(e);
			}
		if (pathString != "" && saveData != null) {
			saveData.add(pathString, jsonDataForWork);
			jsonDataForWork = saveData;
		}
		return jsonDataForWork;
	}

	/**
	 * procedure to add value to JsonObject one field value from record from
	 * table, this will not process svarog repo fields, this is very similar to
	 * addValueToJsonObject1 but works on data that is part of a query or a
	 * simple table
	 * 
	 * @param jsonData
	 *            JsonObject object in which we want to save the data
	 * @param recordObject
	 *            DbDataObject object from which we read the data
	 * @param tmpField
	 *            DbDataObject object from SVAROG_FIELDS that will describe the
	 *            field that we try to save
	 * @param saveField
	 *            String under what name we want to insert the value, if we have
	 *            query data is saved as TBL0, TBL1, TB2, so with this we
	 *            translate those with correct table names
	 * @param doTranslate
	 *            Boolean if set to TRUE it will translate all label_codes, so
	 *            it should be TRUE most of the time except in administrative
	 *            console where we have to set the codes
	 * 
	 * @return JSON String with data from oData Object
	 */
	private static JsonObject addValueToJsonObject2(JsonObject jsonData, DbDataObject recordObject,
			DbDataObject tmpField, String readField, String saveField, Boolean doTranslate, SvReader svr) {
		JsonObject jsonDataForWork = jsonData;
		CodeList cl = null;
		String nameField = tmpField.getVal(Rc.FIELD_NAME).toString();
		// no need to display PKID and GUI_METADATA fields
		if (processField(nameField))
			try {
				if (recordObject.getVal(readField) == null) {
					return jsonDataForWork;
				}
				switch (tmpField.getVal(Rc.FIELD_TYPE).toString()) {
				case Rc.NVARCHAR:
					String tmpS = null;
					if (recordObject.getVal(readField) != null)
						tmpS = recordObject.getVal(readField).toString();
					// tmpS = (String) recordObject.getVal(readField);
					if (tmpField.getVal("SV_ISLABEL") != null && tmpField.getVal("SV_ISLABEL").equals(true)) {
						tmpS = I18n.getText(getLocaleId(svr), tmpS);
						jsonDataForWork.addProperty(saveField, tmpS);
						break;
					}
					if (tmpField.getVal("SV_MUTLISELECT") != null && tmpField.getVal("SV_MUTLISELECT").equals(true)) {
						String translatedResult = "";
						StringBuilder trResBuild = new StringBuilder();

						if (tmpField.getVal("CODE_LIST_ID") != null) {
							cl = new CodeList(svr);
							HashMap<String, String> listMap = cl.getCodeList(getLocaleId(svr),
									Long.valueOf(tmpField.getVal("CODE_LIST_ID").toString()), true);
							String[] cArray = tmpS.split(SvConf.getMultiSelectSeparator());
							// if SvConf.getMultiSelectSeparator() is empty, set
							// default to ,
							String multiSelectOperator = SvConf.getMultiSelectSeparator() == null ? ","
									: SvConf.getMultiSelectSeparator();
							for (String tempCodeListKey : cArray) {
								translatedResult = translatedResult
										+ I18n.getText(getLocaleId(svr), listMap.get(tempCodeListKey))
										+ multiSelectOperator;
								trResBuild.append(I18n.getText(getLocaleId(svr), listMap.get(tempCodeListKey))
										+ multiSelectOperator);
							}
						}
						trResBuild.substring(0, trResBuild.length() - 1);
						translatedResult = translatedResult.substring(0, translatedResult.length() - 1);
						jsonDataForWork.addProperty(saveField, trResBuild.toString());
						break;
					}
					if (tmpS != null) {
						if (Rc.LABEL_CODE.equalsIgnoreCase(nameField) && doTranslate)
							tmpS = I18n.getText(getLocaleId(svr), tmpS);

						jsonDataForWork.addProperty(saveField, tmpS);
					}
					break;
				case Rc.NUMERIC:
					Number tmpN = null;
					Long isFloat = (Long) tmpField.getVal(Rc.FIELD_SCALE);
					if (isFloat == null || isFloat == 0) {
						Long tmpL = null;
						tmpL = Long.valueOf(recordObject.getVal(readField).toString());
						tmpN = tmpL;
					} else {
						Double tmpDo = null;
						tmpDo = Double.valueOf(recordObject.getVal(nameField).toString());
						tmpN = tmpDo;
					}
					if (tmpN != null)
						jsonDataForWork.addProperty(saveField, tmpN);
					break;
				case Rc.BOOLEAN:
					jsonDataForWork = addBoleanParse(jsonData, recordObject, readField, saveField);
					break;
				case Rc.DATE: // for some reason date was saved as datetime
					DateTime tmpDsh = new DateTime(recordObject.getVal(readField));

					int monthInt = tmpDsh.monthOfYear().get();
					int dayInt = tmpDsh.dayOfMonth().get();
					String monthStr = ((monthInt < 10) ? "0" : "") + String.valueOf(monthInt);
					String dayStr = ((dayInt < 10) ? "0" : "") + String.valueOf(dayInt);
					jsonDataForWork.addProperty(saveField, tmpDsh.year().get() + "-" + monthStr + "-" + dayStr);

					break;
				case Rc.TIMESTAMP:
				case Rc.DATETIME:
					DateTime tmpDlg = null;
					tmpDlg = (DateTime) recordObject.getVal(readField);
					if (tmpDlg != null)
						jsonDataForWork.addProperty(saveField, tmpDlg.toString());
					break;
				default:
				}
			} catch (Exception e) {
				debugException(e);
			} finally {
				if (cl != null)
					cl.release();
			}
		return jsonDataForWork;
	}

	private static JsonObject addBoleanParse(JsonObject jsonData, DbDataObject recordObject, String readField,
			String saveField) {

		Boolean tmpB = null;
		try {
			tmpB = (Boolean) recordObject.getVal(readField);
		} catch (Exception e) {
			// error getting boolean
		}
		if (tmpB == null) {
			String tmpStr = null;
			tmpStr = (String) recordObject.getVal(readField);
			if (tmpStr != null) {
				if (tmpStr.equalsIgnoreCase("true") || tmpStr.equalsIgnoreCase("t") || tmpStr.equalsIgnoreCase("y")
						|| tmpStr.equalsIgnoreCase("1") || tmpStr.equalsIgnoreCase("yes")
						|| tmpStr.equalsIgnoreCase(I18n.getText(SvConf.getDefaultLocale(), "mk.yes"))
						|| tmpStr.equalsIgnoreCase(I18n.getText(SvConf.getDefaultLocale(), "yes")))
					tmpB = true;
				if (tmpStr.equalsIgnoreCase("false") || tmpStr.equalsIgnoreCase("f") || tmpStr.equalsIgnoreCase("n")
						|| tmpStr.equalsIgnoreCase("0") || tmpStr.equalsIgnoreCase("no")
						|| tmpStr.equalsIgnoreCase(I18n.getText(SvConf.getDefaultLocale(), "mk.no"))
						|| tmpStr.equalsIgnoreCase(I18n.getText(SvConf.getDefaultLocale(), "no")))
					tmpB = false;

			}
		}

		if (tmpB != null) {
			if (tmpB) { // true
				if ("mk_MK".equalsIgnoreCase(SvConf.getDefaultLocale())) // if
																			// mk
					jsonData.addProperty(saveField, I18n.getText(SvConf.getDefaultLocale(), "mk.yes"));
				else
					jsonData.addProperty(saveField, I18n.getText(SvConf.getDefaultLocale(), "yes"));
			} else {
				if ("mk_MK".equalsIgnoreCase(SvConf.getDefaultLocale())) // if
																			// mk
					jsonData.addProperty(saveField, I18n.getText(SvConf.getDefaultLocale(), "mk.no"));
				else
					jsonData.addProperty(saveField, I18n.getText(SvConf.getDefaultLocale(), "no"));
			}

		}
		return jsonData;
	}

	private static JsonObject nParseDouble(DbDataObject recordObject, JsonObject jsonData, String tmpLabel) {
		JsonObject jsonDataRet = jsonData;
		try {
			Double tmpD = Double.valueOf((String) recordObject.getVal(tmpLabel));
			if (tmpD != null)
				jsonDataRet.addProperty(tmpLabel, tmpD);
		} catch (Exception e) {
			debugException(e);
		}
		return jsonDataRet;
	}

	private static JsonObject addNumericParse1(DbDataObject recordObject, JsonObject jsonData, String tmpLabel) {
		int found = 0;
		JsonObject tmpJsonData = jsonData;
		try {
			Long tmpL = Long.valueOf((String) recordObject.getVal(tmpLabel));
			if (tmpL != null)
				jsonData.addProperty(tmpLabel, tmpL);
			found = 1;
		} catch (Exception e) {
			debugException(e);
		}

		try {
			Long tmpLAdm = Long.valueOf((String) recordObject.getVal(tmpLabel + "_1ST"));
			if (tmpLAdm != null)
				jsonData.addProperty(tmpLabel + "_1ST", tmpLAdm);
			found = 1;
		} catch (Exception e) {
			debugException(e);
		}

		try {
			Long tmpLAdm = Long.valueOf((String) recordObject.getVal(tmpLabel + "_2ND"));
			if (tmpLAdm != null)
				jsonData.addProperty(tmpLabel + "_2ND", tmpLAdm);
			found = 1;
		} catch (Exception e) {
			debugException(e);
		}

		if (found == 0) {
			JsonObject tmpJsonData1 = nParseDouble(recordObject, tmpJsonData, tmpLabel);
			JsonObject tmpJsonData2 = nParseDouble(recordObject, tmpJsonData1, tmpLabel + "_1ST");
			JsonObject tmpJsonData3 = nParseDouble(recordObject, tmpJsonData2, tmpLabel + "_2ND");
			tmpJsonData = tmpJsonData3;
			/*
			 * try { Double tmpD = Double.valueOf((String)
			 * recordObject.getVal(tmpLabel)); if (tmpD != null) {
			 * jsonData.addProperty(tmpLabel, tmpD); } } catch (Exception e) {
			 * debugException(e); }
			 * 
			 * try { Double tmpD = Double.valueOf((String)
			 * recordObject.getVal(tmpLabel + "_1ST")); if (tmpD != null)
			 * jsonData.addProperty(tmpLabel + "_1ST", tmpD); } catch (Exception
			 * e) { debugException(e); }
			 * 
			 * try { Double tmpD = Double.valueOf((String)
			 * recordObject.getVal(tmpLabel + "_2ND")); if (tmpD != null)
			 * jsonData.addProperty(tmpLabel + "_2ND", tmpD); } catch (Exception
			 * e) { debugException(e); }
			 */
		}
		return tmpJsonData;
	}

	private static JsonObject addStringParse1(DbDataObject recordObject, JsonObject jsonData, String tmpLabel) {
		try {
			String tmpS = (String) recordObject.getVal(tmpLabel);
			if (tmpS != null)
				jsonData.addProperty(tmpLabel, tmpS);
		} catch (Exception e) {
			debugException(e);
		}
		try {
			String tmpS = (String) recordObject.getVal(tmpLabel + "_1ST");
			if (tmpS != null)
				jsonData.addProperty(tmpLabel + "_1ST", tmpS);
		} catch (Exception e) {
			debugException(e);
		}
		try {
			String tmpS = (String) recordObject.getVal(tmpLabel + "_2ND");
			if (tmpS != null)
				jsonData.addProperty(tmpLabel + "_2ND", tmpS);
		} catch (Exception e) {
			debugException(e);
		}
		return jsonData;
	}

	/**
	 * procedure to add value to JsonObject one field value from record from
	 * table, this will not process svarog repo fields
	 * 
	 * @param jsonData
	 *            JsonObject object in which we want to save the data
	 * @param recordObject
	 *            DbDataObject object from which we read the data
	 * @param tmpField
	 *            DbDataObject object from SVAROG_FIELDS that will describe the
	 *            field that we try to save
	 * 
	 * @return JSON String with data from oData Object
	 */
	private static JsonObject addValueToJsonObjectForm1(JsonObject jsonData, DbDataObject recordObject,
			DbDataObject tmpField) {
		JsonObject jsonDataWorkWith = jsonData;
		String tmpType = tmpField.getVal(Rc.FIELD_TYPE).toString();
		String tmpLabel = tmpField.getVal(Rc.LABEL_CODE).toString();
		try {
			switch (tmpType) {
			case Rc.NUMERIC:
				jsonDataWorkWith = addNumericParse1(recordObject, jsonData, tmpLabel);
				break;
			case Rc.BOOLEAN:
				Boolean tmpB = null;
				tmpB = (Boolean) recordObject.getVal(tmpLabel);
				if (tmpB != null)
					jsonDataWorkWith.addProperty(tmpLabel, tmpB);
				break;
			case Rc.NVARCHAR:
			case Rc.DATE:
			case Rc.TIMESTAMP:
			case Rc.DATETIME:
				jsonDataWorkWith = addStringParse1(recordObject, jsonData, tmpLabel);
				break;
			default:
			}
		} catch (Exception e) {
			debugException(e);
		}
		return jsonDataWorkWith;
	}

	/**
	 * procedure to add JSONSchema values into the Json schema object, this is
	 * working for tables and forms
	 * 
	 * @param fieldType
	 *            DbDataObject one field that we like to add to the JSON Schema
	 * @param jLeaf
	 *            JsonObject Object that already has some of the fields that are
	 *            in same table/form
	 * @param isTable
	 *            Boolean is true if we process table, false if we process form,
	 *            since fields are not the same
	 * 
	 * @return JsonObject with new type of field added
	 */

	private JsonObject addFieldTypeToJsonObject(DbDataObject fieldType, JsonObject jLeaf, Boolean isTable) {
		// if numeric field is part of table, we have to check the scale, so we
		// know if its integer or float, and if its form, we set to float all
		// the time
		Gson gson = new Gson();
		JsonObject jsonreactGUI = null;
		JsonObject guiMetadata = null;
		switch (fieldType.getVal(Rc.FIELD_TYPE).toString()) {
		case Rc.NVARCHAR:
			jLeaf.addProperty(Rc.TYPE, Rc.STRING);
			break;
		case Rc.NUMERIC:
			if (!isTable) {
				jLeaf.addProperty(Rc.TYPE, "integer"); // will always be whole
														// number
			} else {
				Long tmpL = (Long) fieldType.getVal(Rc.FIELD_SCALE);
				if (tmpL != null && tmpL > 0) {
					jLeaf.addProperty(Rc.TYPE, "number");
				} else {
					jLeaf.addProperty(Rc.TYPE, "integer");
				}
			}
			break;
		case Rc.DATE:
			jLeaf.addProperty(Rc.TYPE, Rc.STRING);
			jLeaf.addProperty("format", "date");
			jLeaf.addProperty("datetype", "shortdate");
			break;
		case Rc.TIMESTAMP:
		case Rc.DATETIME:
			jLeaf.addProperty(Rc.TYPE, Rc.STRING);
			jLeaf.addProperty("format", "date-time");
			jLeaf.addProperty("datetype", "longdate");
			break;
		case Rc.BOOLEAN:
			jLeaf.addProperty(Rc.TYPE, "boolean");
			break;
		default:
		}
		try {
			if (fieldType.getVal(Rc.GUI_METADATA) != null)
				guiMetadata = gson.fromJson(fieldType.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
		} catch (Exception e) {
			debugException(e);
		}
		if (guiMetadata != null && guiMetadata.has(Rc.REACT)) {
			jsonreactGUI = (JsonObject) guiMetadata.get(Rc.REACT);
		}
		if (jsonreactGUI != null && jsonreactGUI.has("default"))
			switch (fieldType.getVal(Rc.FIELD_TYPE).toString()) {
			case Rc.NUMERIC:
				if (!isTable) {
					jLeaf.addProperty("default", jsonreactGUI.get("default").getAsNumber());
				} else {
					Long tmpL = (Long) fieldType.getVal(Rc.FIELD_SCALE);
					if (tmpL != null && tmpL > 0) {
						jLeaf.addProperty("default", jsonreactGUI.get("default").getAsNumber());
					} else {
						jLeaf.addProperty("default", jsonreactGUI.get("default").getAsInt());
					}
				}
				break;
			case Rc.NVARCHAR:
			case Rc.DATE:
			case Rc.TIMESTAMP:
			case Rc.DATETIME:
				jLeaf.addProperty("default", jsonreactGUI.get("default").getAsString());
				break;
			case Rc.BOOLEAN:
				jLeaf.addProperty("default", jsonreactGUI.get("default").getAsBoolean());
				break;
			default:
				jLeaf.addProperty("default", jsonreactGUI.get("default").getAsString());
			}
		return jLeaf;
	}

	/**
	 * procedure to add new value in DbDataObject from JsonObject
	 * 
	 * @param vdataObject
	 *            DbDataObject in which we want to save the new value
	 * @param fieldName
	 *            String name of the field, it has to be same value in
	 *            JsonObject and in DbDataObject
	 * @param vfieldObject
	 *            DbDataObject we can read the type of the object and scale from
	 *            this: NUMERIC, NVARCHAR, BOOLEAN, DATE, DATETIME, TIMESTAMP,
	 * @param jsonData
	 *            JsonObject type of the field that we want to read/save:
	 *            string, bool, number, date
	 * 
	 * @return DbDataObject with new field value added
	 */
	private DbDataObject addValueToDataObject(DbDataObject vdataObject, String fieldName, DbDataObject vfieldObject,
			JsonObject jsonData) {
		String fieldType = vfieldObject.getVal(Rc.FIELD_TYPE).toString();
		Long scale = (Long) vfieldObject.getVal(Rc.FIELD_SCALE);
		Gson gson = new Gson();
		JsonObject jsonObjRet = jsonData;
		// if object is inside group path, get it out
		JsonObject guiMetadata = null;
		try {
			if (vfieldObject.getVal(Rc.GUI_METADATA) != null)
				guiMetadata = gson.fromJson(vfieldObject.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
		} catch (Exception e) {
			// could be null, we don't care
			debugException(e);
		}

		if (guiMetadata != null && guiMetadata.has(Rc.REACT)) {
			JsonObject jsonreactGUI = (JsonObject) guiMetadata.get(Rc.REACT);
			if (jsonreactGUI != null && jsonreactGUI.has(Rc.GROUPPATH))
				jsonObjRet = (JsonObject) jsonData.get(jsonreactGUI.get(Rc.GROUPPATH).getAsString());
		}
		// V2.1 fix if field is null 29.09.2017
		if (jsonObjRet != null) {

			if (jsonObjRet.has(fieldName)) {// V 2.1 fix for null values
				switch (fieldType) {
				case Rc.NUMERIC:
					if (scale == null || scale <= 0) {
						Long tmpLong = jsonObjRet.get(fieldName).getAsLong();
						vdataObject.setVal(fieldName, tmpLong);
					} else {
						Double tmpDouble = jsonObjRet.get(fieldName).getAsDouble();
						vdataObject.setVal(fieldName, tmpDouble);
					}
					break;
				case Rc.NVARCHAR:
					vdataObject.setVal(fieldName, jsonObjRet.get(fieldName).getAsString());
					break;
				case Rc.BOOLEAN:
					vdataObject.setVal(fieldName, jsonObjRet.get(fieldName).getAsBoolean());
					break;
				case Rc.DATE:
					Date pickD = Date.valueOf(jsonObjRet.get(fieldName).getAsString());
					vdataObject.setVal(fieldName, pickD);
					break;
				case Rc.TIMESTAMP:
				case Rc.DATETIME:
					String tempDateTime = jsonObjRet.get(fieldName).getAsString();
					DateTime pickDate = null;
					if (!isValidDateTimeFormat(tempDateTime) && tempDateTime.length() > 10) {
						pickDate = new DateTime(tempDateTime.substring(0, tempDateTime.length() - 5).trim());
					} else {
						pickDate = new DateTime(tempDateTime);
					}
					vdataObject.setVal(fieldName, pickDate);
					break;
				default:
				}
			} else
				vdataObject.setVal(fieldName, null);
		}
		return vdataObject;
	}

	/**
	 * Method that checks if date format is valid
	 * 
	 * @param dateTime
	 * @return true/false
	 */
	private Boolean isValidDateTimeFormat(String dateTime) {
		Boolean result = true;
		DateTime dt = null;
		try {
			dt = new DateTime(dateTime);
		} catch (Exception e) {
			result = false;
		}
		return result;
	}

	/**
	 * procedure to generate geometry ( multipolygon) element out of JsonArray
	 * of coordinates , array will have JsonObject with 2 elements "cordxvals"
	 * and "cordyvals" for now they are separated by coma, but we must also
	 * implement newline separator
	 * 
	 * @param multiCoord
	 *            JsonArray of coordinates
	 * 
	 * @return Geometry object Multipolygon type
	 */
	private Geometry createGeomFromJsonPolygons(JsonArray multiCoord) {
		Geometry gm = null;
		GeometryFactory geometryFactory1 = new GeometryFactory();
		Polygon[] polyarray = new Polygon[multiCoord.size()];
		Coordinate[] coords = null;
		for (int j = 0; j < multiCoord.size(); j++) {
			JsonObject elementXY = (JsonObject) multiCoord.get(j);
			// read the strings and replace the new line with comma, so we can
			// use both as separators
			String coordX = elementXY.get("cordxvals").getAsString();
			String coordY = elementXY.get("cordyvals").getAsString();
			coordX = coordX.replaceAll("/n", ",");
			coordY = coordY.replaceAll("/n", ",");
			coordX = coordX.replaceAll("\n", ",");
			coordY = coordY.replaceAll("\n", ",");
			String[] arrayX = coordX.split(",", -1);
			String[] arrayY = coordY.split(",", -1);
			if (arrayX.length == arrayY.length) {
				// the last point in the polygon has to be the same as
				// the first, if its not make the array one point longer
				// and add the first point to the end
				if ((arrayY[0].equals(arrayY[arrayY.length - 1])) && (arrayX[0].equals(arrayX[arrayX.length - 1])))
					coords = new Coordinate[arrayX.length];
				else {
					coords = new Coordinate[arrayX.length + 1];
					coords[arrayX.length] = new Coordinate(Long.parseLong(arrayY[0]), Long.parseLong(arrayX[0]));
				}
				for (int i = 0; i < arrayY.length; i++)
					coords[i] = new Coordinate(Long.parseLong(arrayY[i]), Long.parseLong(arrayX[i]));
				geometryFactory1 = new GeometryFactory();
				LinearRing ring = geometryFactory1.createLinearRing(coords);
				LinearRing[] holes = null;
				polyarray[j] = geometryFactory1.createPolygon(ring, holes);
			} else {
				log4j.error("wrong number of X/Y coordinates");
			}
		}
		gm = geometryFactory1.createMultiPolygon(polyarray);
		return gm;
	}

	private DbSearchCriterion createCriterion(DbDataObject fieldObject, String fieldName,
			DbCompareOperand compareOperand, String fieldValue) {
		DbSearchCriterion critU = null;
		try {
			switch (fieldObject.getVal(Rc.FIELD_TYPE).toString()) {
			case Rc.NVARCHAR:
				critU = new DbSearchCriterion(fieldName, compareOperand, fieldValue);
				break;
			case Rc.NUMERIC:
				Long tmpLong = Long.valueOf(fieldValue);
				critU = new DbSearchCriterion(fieldName, compareOperand, tmpLong);
				break;
			case Rc.DATE:
				Date tmpDate = Date.valueOf(fieldValue);
				critU = new DbSearchCriterion(fieldName, compareOperand, tmpDate);
				break;
			case Rc.DATETIME:
			case Rc.TIMESTAMP:
				DateTime tmpDatetime = DateTime.parse(fieldValue);
				critU = new DbSearchCriterion(fieldName, compareOperand, tmpDatetime);
				break;
			case Rc.BOOLEAN:
				Boolean tmpBool = Boolean.valueOf(fieldValue);
				critU = new DbSearchCriterion(fieldName, compareOperand, tmpBool);
				break;
			default:
				critU = new DbSearchCriterion(fieldName, compareOperand, fieldValue);
			}
		} catch (SvException e) {
			debugSvException(e);
		}
		return critU;
	}

	private DbSearchCriterion createCriterion2(DbDataObject fieldObject, String fieldName, String dbCompareOperand,
			String fieldValue) {
		DbSearchCriterion critU = null;
		DbCompareOperand dboc = DbCompareOperand.EQUAL;
		switch (dbCompareOperand.toUpperCase()) {
		case "NOTEQUAL":
			dboc = DbCompareOperand.NOTEQUAL;
			break;
		case "GREATER":
			dboc = DbCompareOperand.GREATER;
			break;
		default:
		}
		try {
			switch (fieldObject.getVal(Rc.FIELD_TYPE).toString()) {
			case Rc.NVARCHAR:
				critU = new DbSearchCriterion(fieldName, dboc, fieldValue);
				break;
			case Rc.NUMERIC:
				Long tmpLong = Long.valueOf(fieldValue);
				critU = new DbSearchCriterion(fieldName, dboc, tmpLong);
				break;
			case Rc.DATE:
				Date tmpDate = Date.valueOf(fieldValue);
				critU = new DbSearchCriterion(fieldName, dboc, tmpDate);
				break;
			case Rc.DATETIME:
			case Rc.TIMESTAMP:
				DateTime tmpDatetime = DateTime.parse(fieldValue);
				critU = new DbSearchCriterion(fieldName, dboc, tmpDatetime);
				break;
			case Rc.BOOLEAN:
				Boolean tmpBool = Boolean.valueOf(fieldValue);
				critU = new DbSearchCriterion(fieldName, dboc, tmpBool);
				break;
			default:
				critU = new DbSearchCriterion(fieldName, dboc, fieldValue);
			}
		} catch (SvException e) {
			debugSvException(e);
		}
		return critU;
	}

	/**
	 * procedure to return all documents for application that are of type yes/no
	 * dopdown specified , and have parent of support_type
	 * 
	 * @param papplication_id
	 *            Long Id of the application
	 * @param psupport_type
	 *            Long Id of the support (merka)
	 * @param pform_category
	 *            String name of the form category
	 * @param svr
	 *            SvReader connected to database
	 * 
	 * @return DbDataArray
	 */
	public static DbDataArray getYesNoDocuments(Long pApplicationID, Long pSupportType, SvReader svr) {
		DbDataArray ret = null;
		try {
			// Database object types that we will need
			DbDataObject repoDbt = svr.getObjectById(svCONST.OBJECT_TYPE_REPO, svCONST.OBJECT_TYPE_TABLE, null);
			DbDataObject databaseTypeFORMTYPE = svr.getObjectById(svCONST.OBJECT_TYPE_FORM_TYPE,
					svCONST.OBJECT_TYPE_TABLE, null);
			DbDataObject databaseTypeFORM = svr.getObjectById(svCONST.OBJECT_TYPE_FORM, svCONST.OBJECT_TYPE_TABLE,
					null);
			// Fill the field arrays with config from database
			DbDataArray vrepoFields = SvCore.getFields(repoDbt.getObject_id());
			DbDataArray vFormTypeFields = SvCore.getFields(databaseTypeFORMTYPE.getObject_id());
			DbDataArray vFormFields = SvCore.getFields(databaseTypeFORM.getObject_id());
			// build the query
			DbSearchCriterion crit1 = new DbSearchCriterion(Rc.PARENT_ID, DbCompareOperand.EQUAL, pSupportType);
			crit1.setNextCritOperand(DbLogicOperand.AND.toString());
			DbSearchCriterion crit2 = new DbSearchCriterion(Rc.LABEL_CODE, DbCompareOperand.LIKE, "%ynd%");
			DbSearchExpression expr = new DbSearchExpression();
			expr.addDbSearchItem(crit1);
			expr.addDbSearchItem(crit2);
			DbQueryObject dbqControlForms = new DbQueryObject(repoDbt, vrepoFields, databaseTypeFORMTYPE,
					vFormTypeFields, expr, null, null);
			dbqControlForms.setJoinToNext(DbJoinType.INNER);
			dbqControlForms.setLinkToNextType(LinkType.CUSTOM);
			dbqControlForms.addCustomJoinLeft(Rc.OBJECT_ID);
			dbqControlForms.addCustomJoinRight("FORM_TYPE_ID");
			DbSearch getParentIdFRM = new DbSearchCriterion(Rc.PARENT_ID, DbCompareOperand.EQUAL, pApplicationID);
			DbQueryObject dbqControlObjects = new DbQueryObject(repoDbt, vrepoFields, databaseTypeFORM, vFormFields,
					getParentIdFRM, null, null);
			DbQueryExpression q = new DbQueryExpression();
			// add the items
			q.addItem(dbqControlForms);
			q.addItem(dbqControlObjects);
			ret = svr.getObjects(q, null, null);
		} catch (SvException ex) {
			WsReactElements.debugException(ex);
		}
		return ret;
	}

	/**
	 * procedure to generate drop-down list of true/false
	 * 
	 * @param dbo
	 *            Long Object that needs to be displayed
	 * @param svr
	 *            connected SvReader
	 * 
	 * @return DbDataObject that is master of the drop-down true/false codelist
	 */
	private DbDataObject prepareCodeObject(Long vCodesType, SvReader svr) {
		DbDataObject codeObject = null;
		try {
			DbSearchExpression expr1 = new DbSearchExpression();
			DbSearchCriterion critU = new DbSearchCriterion("CODE_VALUE", DbCompareOperand.EQUAL, "BOOLEAN_TRUE_FALSE");
			expr1.addDbSearchItem(critU);
			DbDataArray ret1 = svr.getObjects(expr1, vCodesType, null, 0, 0);
			if (ret1.getItems().size() == 1)
				codeObject = ret1.getItems().get(0);
		} catch (SvException e) {
			debugSvException(e);
		}
		return codeObject;
	}

	private SvException intersectsPolygon(DbDataObject vdataObject, SvGeometry svg) {
		SvException retval = null;
		Geometry geomPrim = SvGeometry.getGeometry(vdataObject);
		List<Geometry> tiles = SvGeometry.getTileGeomtries(geomPrim.getEnvelopeInternal());
		// Check for intersections. The
		ArrayList<Geometry> intersectResult = new ArrayList<>();
		for (Geometry tileGeom : tiles) {
			SvSDITile currtile = SvGeometry.getTile(vdataObject.getObject_type(), (String) tileGeom.getUserData(),
					null);
			List<Geometry> geoms = currtile.getInternalGeometries();
			intersectResult.addAll(geoms);
		}
		for (Geometry intResEle : intersectResult) {
			DbDataObject dboRes = (DbDataObject) intResEle.getUserData();
			Long dboResId = dboRes.getObject_id();

			Geometry ireBuff = intResEle.buffer(-0.01);
			boolean intersectCheck = false;
			// exclude self of relation check, do check on same type
			// (parcel/cover)
			if (!dboResId.equals(vdataObject.getObject_id()) && geomPrim.intersects(ireBuff)
					&& dboRes.getObject_type().equals(vdataObject.getObject_type())) {

				if (geomPrim.touches(ireBuff)) {
					intersectCheck = false;
				} else {
					intersectCheck = true;
				}

			}
			if (intersectCheck) {
				retval = new SvException("perun.main.sdi.intersection_found", svg.getInstanceUser());
			}
		}
		return retval;
	}

	private SvException isContainedByParent(DbDataObject vdataObject, SvGeometry svg, SvReader svr) {
		SvException retval = null;
		Geometry geomPrim = SvGeometry.getGeometry(vdataObject);
		Long parentId = vdataObject.getParent_id();
		DbDataObject parentObject = null;

		DbDataObject parentType = SvCore.getDbtByName("PARCEL");
		if (vdataObject.getObject_type() == SvCore.getDbtByName("SDI_COVER").getObject_id()) {

			try {
				svr.setIncludeGeometries(true);
				parentObject = svr.getObjectById(parentId, parentType, null);
				Geometry parentGeom = SvGeometry.getGeometry(parentObject);

				Geometry parentGeomBuff = parentGeom.buffer(0.01);
				if (!geomPrim.coveredBy(parentGeomBuff)) {
					retval = new SvException("perun.main.sdi.parent_no_contain_polygon", svg.getInstanceUser());
				}
			} catch (SvException e) {
				return e;
			}
		}
		// Check for intersections. The
		return retval;
	}

	private Geometry getMultiPolygonType(Geometry opGeom) {
		GeometryFactory gf = SvUtil.sdiFactory;
		String opGeomType = opGeom.getGeometryType();
		MultiPolygon geom = null;
		if (opGeomType == "MultiPolygon") {
			geom = (MultiPolygon) opGeom;
		} else if (opGeomType == "Polygon") {
			geom = gf.createMultiPolygon(new Polygon[] { (Polygon) opGeom });
		}
		return geom;
	}

	private Geometry getCoverGeometry(DbDataObject vdataObject, SvReader svr) throws SvException {

		Geometry opGeom = null;
		DbDataObject parentParcel = null;
		svr.setIncludeGeometries(true);

		parentParcel = svr.getObjectById(vdataObject.getParent_id(), SvCore.getDbtByName("PARCEL"), null);
		Geometry parentGeom = SvGeometry.getGeometry(parentParcel);
		Geometry vdataGeom = SvGeometry.getGeometry(vdataObject);
		// get intersection geometry of cover and parcel, so contain rule is
		// applied, check validity of result (so intersection polygon actually
		// exists)
		opGeom = parentGeom.intersection(vdataGeom);
		if (opGeom.getNumPoints() > 2) {
			// if intersection result is valid, cut with all other intersecting
			// covers,
			Envelope env = opGeom.getEnvelopeInternal();
			List<Geometry> tileGeom = SvGeometry.getTileGeomtries(env);
			for (Geometry tg : tileGeom) {
				String tileID = (String) tg.getUserData();
				SvSDITile tile = SvGeometry.getTile(vdataObject.getObject_type(), tileID, null);
				ArrayList<Geometry> geomSet = tile.getInternalGeometries();
				// REMOVE SELF!
				for (Geometry gs : geomSet) {
					DbDataObject gsDbo = (DbDataObject) gs.getUserData();
					if (!vdataObject.getObject_id().equals(gsDbo.getObject_id())) {
						opGeom = opGeom.difference(gs.buffer(0.01));
					}
				}
			}
		}
		return this.getMultiPolygonType(opGeom);
	}

	private void setParcelLinks(DbDataObject vdataObject, List<Geometry> tileGeomList, SvWriter svw)
			throws SvException {

		DbDataArray intParcelSet = new DbDataArray();
		Long vdataObjId = vdataObject.getObject_id();
		Geometry vdataGeom = SvGeometry.getGeometry(vdataObject);

		// Generate intersecting parcel array
		for (Geometry tgl : tileGeomList) {
			String tileID = (String) tgl.getUserData();
			SvSDITile tile = SvGeometry.getTile(vdataObject.getObject_type(), tileID, null);
			ArrayList<Geometry> geomSet = tile.getInternalGeometries();
			for (Geometry gs : geomSet) {
				DbDataObject gsDbo = (DbDataObject) gs.getUserData();
				// exclude self since we already saved the parcel
				if (!gsDbo.getObject_id().equals(vdataObjId)) {
					Geometry gsGeom = SvGeometry.getGeometry(gsDbo);
					if (vdataGeom.intersects(gsGeom)) {
						intParcelSet.addDataItem(gsDbo);
					}
				}
			}
		}

		// Generate links for intParcelSet.items and vdataObject
		if (intParcelSet != null && !intParcelSet.getItems().isEmpty()) {
			SvLink svLink = new SvLink(svw);
			svLink.dbSetAutoCommit(true);
			Long linkObjId = SvCore.getTypeIdByName("PARCEL");
			DbDataObject linkType = SvCore.getLinkType("INTERSECT_PARCEL_LINK", linkObjId, linkObjId);
			for (DbDataObject ips : intParcelSet.getItems()) {
				Long ipsObjId = ips.getObject_id();
				String linkNote = vdataObjId.toString() + " intersects with " + ipsObjId.toString();
				String linkNoteRev = ipsObjId.toString() + " intersects with " + vdataObjId.toString();
				svLink.linkObjects(vdataObjId, ipsObjId, linkType.getObject_id(), linkNote);
				svLink.linkObjects(ipsObjId, vdataObjId, linkType.getObject_id(), linkNoteRev);
			}
			if (svLink != null) {
				svLink.release();
			}
		}

	}

	public void setPointFromLatLng(SvReader svr, DbDataObject dbo) throws SQLException, SvException {
		PreparedStatement cst = null;
		ResultSet rs = null;

		try {
			GeometryFactory gf = new GeometryFactory();
			Coordinate coord = new Coordinate();

			String[] dmsLat = dbo.getVal("GPS_NORTH").toString().split("[°']+");
			String[] dmsLon = dbo.getVal("GPS_EAST").toString().split("[°']+");

			Double ddLat = Double.valueOf(dmsLat[0]) + Double.valueOf(dmsLat[1]) / 60
					+ Double.valueOf(dmsLat[2]) / 3600;
			Double ddLon = Double.valueOf(dmsLon[0]) + Double.valueOf(dmsLon[1]) / 60
					+ Double.valueOf(dmsLon[2]) / 3600;

			cst = svr.dbGetConn().prepareStatement(
					"SELECT 	ST_X (ST_TRANSFORM( ST_Transform(ST_SetSRID(ST_MakePoint(?, ?),?),	?) , ?) ),ST_Y (ST_TRANSFORM( ST_Transform(ST_SetSRID(ST_MakePoint(?, ?),?), ?), ?) );");
			// x params
			cst.setDouble(1, ddLon);
			cst.setDouble(2, ddLat);
			cst.setInt(3, 4326);
			cst.setInt(4, 32638);
			cst.setInt(5, 32638);
			// y params
			cst.setDouble(6, ddLon);
			cst.setDouble(7, ddLat);
			cst.setInt(8, 4326);
			cst.setInt(9, 32638);
			cst.setInt(10, 32638);

			rs = cst.executeQuery();
			while (rs.next()) {
				coord.x = rs.getDouble(1);
				coord.y = rs.getDouble(2);
			}

			Point point = gf.createPoint(coord);
			SvGeometry.setGeometry(dbo, point);

		} catch (SvException e) {
			log4j.info(e);
			throw new SvException("naits.error.setGeomFromLatLng_Failed with: " + e, svCONST.systemUser);
		} catch (SQLException e) {
			log4j.info(e);
			throw new SQLException("setGeomFromLatLng SQL statement failed with: " + e);
		} finally {
			SvCore.closeResource((AutoCloseable) cst, svr.getInstanceUser());
			SvCore.closeResource((AutoCloseable) rs, svr.getInstanceUser());
		}
	}

	public DbDataArray listOfYNDocForSupportType(Long supportTypeId, SvReader svr) throws SvException {
		Long formType = SvCore.getDbtByName("SVAROG_FORM_TYPE").getObject_id();
		DbDataArray tempBySupportType = svr.getObjectsByParentId(supportTypeId, formType, null, 0, 0);
		DbDataArray hasYNDoc = new DbDataArray();
		if (tempBySupportType != null && !tempBySupportType.getItems().isEmpty())
			for (DbDataObject docObj : tempBySupportType.getItems()) {
				if ("2".equalsIgnoreCase(docObj.getVal("FORM_CATEGORY").toString()))
					hasYNDoc.addDataItem(docObj);
			}
		return hasYNDoc;
	}

	public DbDataArray listOfYNDocForApp(Long applicationId, SvReader svr) throws SvException {
		String uniqueCacheId = "YN-DOCS-" + applicationId;
		DbDataArray result = SvComplexCache.getData(uniqueCacheId, svr);
		if (result != null) {
			return result;
		}
		DbSearchCriterion search = new DbSearchCriterion(Rc.PARENT_ID, DbCompareOperand.EQUAL, applicationId);
		DbSearchCriterion search1 = new DbSearchCriterion("FORM_CATEGORY", DbCompareOperand.EQUAL, "2");
		SvRelationCache src = new SvRelationCache(SvCore.getDbtByName("SVAROG_FORM"), search, "SF", null,
				"FORM_TYPE_ID", null, null);
		SvRelationCache lnkd1 = new SvRelationCache(SvCore.getDbtByName("SVAROG_FORM_TYPE"), search1, "SFT",
				LinkType.DENORMALIZED_FULL, Rc.OBJECT_ID, null, null);
		lnkd1.setJoinToParent(DbJoinType.INNER);
		src.addCache(lnkd1);
		SvComplexCache.addRelationCache(uniqueCacheId, src, false);
		return SvComplexCache.getData(uniqueCacheId, svr);
	}

	/**
	 * method to get list of all messages for given conversation , ordered by
	 * obejct_id , so that will be in good time-line created order
	 * 
	 * @param svr
	 *            SvReader connected to database
	 * @param conversationObject
	 *            conversationObject from SVAROG_CONVERSATION table
	 * @return DbDataArray of all messages for that conversation
	 * @throws SvException
	 */
	private DbDataArray listOfMessagesinConversation(SvReader svr, DbDataObject conversationObject) {
		DbDataArray sorted = new DbDataArray();
		if (svr != null && conversationObject != null) {
			try {
				// TODO after this add conversation history between messages
				DbDataArray messages = svr.getObjectsByParentId(conversationObject.getObject_id(),
						SvCore.getDbtByName("SVAROG_MESSAGE").getObject_id(), null, 0, 0, null);
				// sort the the items by the object_id ( date of created ) and
				// put replies under the message that its a reply to by grouping
				// them for column REPLY_TO
				String[] var = { "REPLY_TO" };
				if (messages != null && !messages.getItems().isEmpty()) {
					for (DbDataObject a : messages.getItems())
						if (a.getVal("REPLY_TO") == null)
							a.setVal("REPLY_TO", 0L);
					HashMap<String, DbDataArray> arrGroupItems = messages.groupItemsByColumn(var);
					for (Map.Entry<String, DbDataArray> mvSet : arrGroupItems.entrySet()) {
						if (mvSet.getKey().equals("0")) {
							DbDataArray pom = arrGroupItems.get(mvSet.getKey());
							ArrayList<DbDataObject> asd = pom.getSortedItems(Rc.OBJECT_ID, true);
							for (DbDataObject dbo : asd) {
								sorted.addDataItem(dbo);
								for (Entry<String, DbDataArray> groupedSet : arrGroupItems.entrySet())
									if (dbo.getObject_id().toString().equals(groupedSet.getKey())) {
										DbDataArray pom1 = arrGroupItems.get(groupedSet.getKey());
										ArrayList<DbDataObject> asd1 = pom1.getSortedItems(Rc.OBJECT_ID, true);
										for (DbDataObject tmp : asd1)
											sorted.addDataItem(tmp);
									}
							}
						}
					}
				}
			} catch (SvException e) {
				debugSvException(e);
			}
		}
		return sorted;
	}

	/**
	 * method to return all messages to a conversation and make them ordered by
	 * time of entry and put replies for the message under each
	 * 
	 * @param svr
	 *            SvReader conected to database
	 * @param conversationObject
	 *            DbDataObject from SVAROG_CONVERSATION table
	 * @return JsonArray of ordered messages
	 */
	public JsonArray processListOfMessages(SvReader svr, DbDataObject conversationObject) {
		DbDataArray ret = null;
		if (conversationObject != null)
			ret = listOfMessagesinConversation(svr, conversationObject);
		DbDataObject myUser = svr.getInstanceUser();
		JsonArray messArray = new JsonArray();
		DbDataObject userDbo = null;
		SvWriter svw = null;
		try {
			if (ret != null && !ret.getItems().isEmpty())
				for (DbDataObject messageDbo : ret.getItems()) {
					JsonObject asd = new JsonObject();
					asd.addProperty("MESSAGE.OBJECT_ID", messageDbo.getObject_id());
					asd.addProperty("MESSAGE.OBJECT_TYPE", messageDbo.getObject_type());
					asd.addProperty("MESSAGE.PKID", messageDbo.getPkid());
					asd.addProperty("canDelete", false);
					asd.addProperty("canEdit", false);
					asd.addProperty("canReply", true);
					asd.addProperty("text", (String) messageDbo.getVal("MESSAGE_TEXT"));
					if (messageDbo.getVal("CREATED_BY") != null) {
						userDbo = svr.getObjectById((Long) messageDbo.getVal("CREATED_BY"), svCONST.OBJECT_TYPE_USER,
								null);
						if (userDbo != null) {
							if (userDbo.getObject_id().equals(myUser.getObject_id())) {
								asd.addProperty("canDelete", true);
								asd.addProperty("canEdit", true);
							}
							asd.addProperty("createdBy", (String) userDbo.getVal("USER_NAME"));
						}
					}
					if (messageDbo.getVal("REPLY_TO") == null)
						asd.addProperty("level", 1);
					else
						asd.addProperty("level", 2);
					userDbo = svr.getObjectById((Long) messageDbo.getUser_id(), svCONST.OBJECT_TYPE_USER, null);
					if (userDbo != null) {
						asd.addProperty("changedBy", (String) userDbo.getVal("USER_NAME"));
					}
					if (messageDbo.getVal("ASSIGNED_TO") != null) {
						userDbo = svr.getObjectById((Long) messageDbo.getVal("ASSIGNED_TO"), svCONST.OBJECT_TYPE_USER,
								null);

					}
					messArray.add(asd);
				}

			if (conversationObject.getVal("ASSIGNED_TO") != null) {
				userDbo = svr.getObjectById((Long) conversationObject.getVal("ASSIGNED_TO"), svCONST.OBJECT_TYPE_USER,
						null);
				if (myUser.getObject_id().equals(userDbo.getObject_id()) && conversationObject.getVal("IS_READ") != null
						&& !((Boolean) conversationObject.getVal("IS_READ"))) {
					svw = new SvWriter(svr);
					conversationObject.setVal("IS_READ", true);
					svw.saveObject(conversationObject);
					svw.dbCommit();
				}

			}

		} catch (SvException e) {
			debugException(e);
		} finally {
			releaseAll(svw);
		}
		return messArray;
	}

	/**
	 * method to generate the configuration for the header of conversation
	 * 
	 * @param svr
	 *            SvReader connected to database
	 * @param conversationObject
	 *            DbDataObject of type SVAROG_CONVERSATION
	 * @return JsonObject with all fields and available attachment types
	 * @throws SvException
	 */
	public JsonObject prepareConversationHeader(DbDataObject conversationObject, SvReader svr) throws SvException {
		DbDataObject myUser = svr.getInstanceUser();
		DbDataArray fields1 = SvCore.getFields(svCONST.OBJECT_TYPE_CONVERSATION);
		JsonObject convObject = new JsonObject();
		JsonArray fieldsArray = new JsonArray();
		CodeList cl = new CodeList(svr);
		Boolean canEdit = false;
		if (conversationObject == null || conversationObject.getVal("ASSIGNED_TO") == null
				|| myUser.getObject_id().equals((Long) conversationObject.getVal("ASSIGNED_TO")))
			canEdit = true;
		if (svr.isAdmin())
			canEdit = true;
		for (DbDataObject fieldObj : fields1.getSortedItems("SORT_ORDER")) {
			String fieldName = ((String) fieldObj.getVal(Rc.FIELD_NAME)).toUpperCase();
			if ((fieldName.equals("MODULE_NAME") || fieldName.equals("CATEGORY") || fieldName.equals("TITLE")
					|| fieldName.equals("PRIORITY") || fieldName.equals("CONVERSATION_STATUS")
					|| fieldName.equals("ASSIGNED_TO_USERNAME") || fieldName.equals("CONTACT_INFO"))
					|| ((fieldName.equals("ID") || fieldName.equals("CREATED_BY_USERNAME"))
							&& conversationObject != null)) {
				JsonObject addObject = new JsonObject();
				addObject.addProperty("fieldName", fieldName);
				addObject.addProperty("type", "string");
				if (fieldName.equals("MODULE_NAME") || fieldName.equals("CATEGORY") || fieldName.equals("TITLE")
						|| fieldName.equals("PRIORITY") || fieldName.equals("CONVERSATION_STATUS")
						|| fieldName.equals("ASSIGNED_TO_USERNAME") || fieldName.equals("CONTACT_INFO"))
					addObject.addProperty("canEdit", canEdit);
				addObject.addProperty("label",
						I18n.getText(getLocaleId(svr), fieldObj.getVal(Rc.LABEL_CODE).toString()));
				if (fieldObj.getVal("CODE_LIST_ID") != null) {
					JsonArray dropdownList = new JsonArray();
					HashMap<String, String> listMap;
					listMap = cl.getCodeList(SvConf.getDefaultLocale(), (Long) fieldObj.getVal("CODE_LIST_ID"), true);
					Iterator<Entry<String, String>> it = listMap.entrySet().iterator();
					while (it.hasNext()) {
						Entry<String, String> pair = it.next();
						JsonObject itemObject = new JsonObject();
						itemObject.addProperty("key", pair.getKey());
						itemObject.addProperty("value", pair.getValue());
						it.remove();
						dropdownList.add(itemObject);
					}
					addObject.addProperty("type", "dropdown");
					addObject.add("values", dropdownList);
				}
				fieldsArray.add(addObject);
			}
		}
		convObject.add("fields", fieldsArray);
		DbDataArray attachArray = getConversationAttachmentsType(svr);
		JsonArray attachmentsList = new JsonArray();
		if (attachArray != null && !attachArray.getItems().isEmpty())
			for (DbDataObject itemAttachment : attachArray.getItems()) {
				JsonObject itemJson = new JsonObject();
				itemJson.addProperty("label", itemAttachment.getVal("LINK_TYPE_DESCRIPTION").toString());
				itemJson.addProperty("objectType", (Long) itemAttachment.getVal(Rc.LINK_OBJECT_TYPE2));
				itemJson.addProperty("tableName",
						getTableNameById((Long) itemAttachment.getVal(Rc.LINK_OBJECT_TYPE2), svr));
				itemJson.addProperty("key", itemAttachment.getObject_id());
				attachmentsList.add(itemJson);
			}
		convObject.add("attachments", attachmentsList);
		if (cl != null)
			cl.release();
		return convObject;
	}

	private static DbDataArray getConversationAttachmentsType(SvReader svr) throws SvException {
		DbSearchExpression expr = new DbSearchExpression();
		DbSearchCriterion crit1 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE1, DbCompareOperand.EQUAL,
				svCONST.OBJECT_TYPE_CONVERSATION);
		DbSearchCriterion crit2 = new DbSearchCriterion(Rc.LINK_TYPE, DbCompareOperand.EQUAL,
				"LINK_CONVERSATION_ATTACHMENT");
		expr.addDbSearchItem(crit1);
		expr.setNextCritOperand(DbLogicOperand.AND.toString());
		expr.addDbSearchItem(crit2);
		return svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
	}

	public JsonObject prepareConversationData(SvReader svr, DbDataObject conversationObject) {
		JsonObject convObject = new JsonObject();
		JsonArray attachmentsList = new JsonArray();
		if (svr != null && conversationObject != null) {
			convObject.addProperty(Rc.OBJECT_ID, conversationObject.getObject_id());
			convObject.addProperty(Rc.OBJECT_TYPE, conversationObject.getObject_type());
			convObject.addProperty(Rc.PKID, conversationObject.getPkid());
			DbDataArray fields1 = SvCore.getFields(svCONST.OBJECT_TYPE_CONVERSATION);
			for (DbDataObject fieldObj : fields1.getItems()) {
				String fieldName = ((String) fieldObj.getVal(Rc.FIELD_NAME)).toUpperCase();
				if (conversationObject.getVal(fieldName) != null)
					convObject.addProperty(fieldName, conversationObject.getVal(fieldName).toString());
			}
			try {
				DbDataArray vData = getConversationAttachmentsType(svr);
				if (vData != null && !vData.getItems().isEmpty())
					for (DbDataObject linkType : vData.getItems()) {
						DbSearchExpression expr = new DbSearchExpression();
						DbSearchCriterion crit1 = new DbSearchCriterion("LINK_OBJ_ID_1", DbCompareOperand.EQUAL,
								conversationObject.getObject_id());
						DbSearchCriterion crit2 = new DbSearchCriterion("LINK_TYPE_ID", DbCompareOperand.EQUAL,
								linkType.getObject_id());
						expr.addDbSearchItem(crit1);
						expr.setNextCritOperand(DbLogicOperand.AND.toString());
						expr.addDbSearchItem(crit2);
						DbDataArray ret = svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK, null, 0, 0);
						Gson gson = new Gson();
						if (ret != null && !ret.getItems().isEmpty())
							for (DbDataObject linkedAttachment : ret.getItems()) {
								DbDataObject checkObect = svr.getObjectById(
										(Long) linkedAttachment.getVal("LINK_OBJ_ID_2"),
										(Long) linkType.getVal(Rc.LINK_OBJECT_TYPE2), null);
								DbDataObject dbTable = SvReader.getDbt(checkObect.getObject_type());
								String labelTableName = I18n.getText(dbTable.getVal("LABEL_CODE").toString());
								if (checkObect != null) {
									JsonObject newAttachment = new JsonObject();
									newAttachment.addProperty("objectName", labelTableName);
									newAttachment.addProperty("objectId", checkObect.getObject_id());
									newAttachment.addProperty("objectType", checkObect.getObject_type());
									newAttachment.addProperty("objectPkid", checkObect.getPkid());
									DbDataArray fields = svr.getObjectsByParentId(checkObect.getObject_type(),
											svCONST.OBJECT_TYPE_FIELD, null, 0, 0);
									if (fields != null && !fields.getItems().isEmpty())
										for (DbDataObject fieldType : fields.getItems())
											if (fieldType != null && fieldType.getVal(Rc.GUI_METADATA) != null
													&& fieldType.getVal(Rc.GUI_METADATA) != "") {
												String guiMetadata = fieldType.getVal(Rc.GUI_METADATA).toString();
												try {
													JsonObject jsonData = gson.fromJson(guiMetadata, JsonObject.class);
													if (jsonData.has("react")) {
														JsonObject jsonReact = (JsonObject) jsonData.get("react");
														if (jsonReact.has("conversationInfo")
																&& jsonReact.get("conversationInfo").getAsBoolean()
																&& checkObect.getVal(fieldType.getVal(Rc.FIELD_NAME)
																		.toString()) != null)
															newAttachment.addProperty(
																	I18n.getText(SvConf.getDefaultLocale(),
																			fieldType.getVal(Rc.LABEL_CODE).toString()),
																	checkObect.getVal(
																			fieldType.getVal(Rc.FIELD_NAME).toString())
																			.toString());
													}
												} catch (JsonSyntaxException e) {
													debugException(e);
												}
											}
									attachmentsList.add(newAttachment);
								}
							}
					}
			} catch (SvException e) {
				// something went wrong with search
			}
		}
		convObject.add("attachments", attachmentsList);
		return convObject;
	}

	public DbDataArray getConversationGridData(SvReader svr, String convType, String userName, Boolean isUnread)
			throws SvException {
		DbDataObject dbUSer = null;
		if (userName == null || userName == "")
			throw (new SvException("system.error.user_not_found", svr.getInstanceUser()));
		if (svr.isAdmin()) {
			DbSearchCriterion crit1 = new DbSearchCriterion("USER_NAME", DbCompareOperand.EQUAL,
					userName.toUpperCase());
			DbDataArray vData = svr.getObjects(crit1, svCONST.OBJECT_TYPE_USER, null, 0, 0);
			if (vData != null && vData.getItems().size() == 1)
				dbUSer = vData.getItems().get(0);
		}

		DbDataArray conversations = new DbDataArray();
		String sortField = "ID";
		if (convType != null) {
			SvConversation svCon = new SvConversation();
			switch (convType.toUpperCase()) {
			case "MY_CREATED":
				conversations = svCon.getCreatedConversations(svr, dbUSer);
				break;
			case "ASSIGNED_TO_ME":
				conversations = svCon.getAssignedConversations(svr, isUnread, dbUSer);
				break;
			case "WITH_MY_MESSAGE":
				sortField = "TBL0_ID";
				conversations = svCon.getConversationsWithMyMessage(svr, dbUSer);
				break;
			default:
			}
		}
		ArrayList<DbDataObject> conversations1 = conversations.getSortedItems(sortField);
		if (!conversations1.isEmpty()) {
			conversations = new DbDataArray();
			for (int i = conversations1.size() - 1; i >= 0; i--)
				conversations.addDataItem(conversations1.get(i));
		}
		return conversations;
	}

	/**
	 * method to search any table for any field value search fields and values
	 * are passed by formVals, numeric can be search by equal only , and string
	 * can be equal or like if used % in value to search
	 * 
	 * @param svr
	 *            SvReader connected to database
	 * @param tableName
	 *            name of the table or table ID
	 * @param formVals
	 *            MultivaluedMap<String, String> as get from GUI form
	 * @param recordNumber
	 *            how many records we want to return, if set to 0, return all
	 * @return DbDataArray
	 * @throws SvException
	 */
	public DbDataArray searchTable(SvReader svr, String tableName, MultivaluedMap<String, String> formVals,
			Integer recordNumber) throws SvException {
		String jsonObjString = "{}";
		if (formVals != null)
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					jsonObjString = key;
				}
			}
		Gson gson = new Gson();
		JsonObject jsonData = null;
		try {
			jsonData = gson.fromJson(jsonObjString, JsonObject.class);
		} catch (JsonSyntaxException e) {
			jsonData = new JsonObject();
		}
		Long tableID = findTableType(tableName);
		DbSearchExpression expr = new DbSearchExpression();
		if (tableID > 0 && jsonData != null) {
			DbDataArray dataFields = SvCore.getFields(tableID);
			for (DbDataObject fieldObj : dataFields.getItems()) {
				String fieldName = (String) fieldObj.getVal(Rc.FIELD_NAME);
				if (jsonData.has(fieldName) && jsonData.get(fieldName) != null) {
					DbSearchCriterion cr = null;
					if (fieldObj.getVal(Rc.FIELD_TYPE).toString().equals("NUMERIC"))
						cr = new DbSearchCriterion(fieldName, DbCompareOperand.EQUAL,
								jsonData.get(fieldName).getAsLong(), DbLogicOperand.AND);
					else {
						DbCompareOperand op = DbCompareOperand.EQUAL;
						if (jsonData.get(fieldName).getAsString().contains("%"))
							op = DbCompareOperand.LIKE;
						cr = new DbSearchCriterion(fieldName, op, jsonData.get(fieldName).getAsString(),
								DbLogicOperand.AND);
					}
					if (cr != null)
						expr.addDbSearchItem(cr);
				}
			}
		}
		return svr.getObjects(expr, tableID, null, recordNumber, 0);

	}

	/**
	 * Web service to return Json list of the fields that are fields in the
	 * table, it will also add all necessary SVAROG fields, it will also look
	 * for display names from SVAROG_LABELS
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            table that we try to display so we need the field names
	 * 
	 * @return Json with list of fields
	 */
	@Path("/getTableFieldList/{session_id}/{table_name}")
	@GET
	@Produces("application/json")
	public Response getTableFieldList(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @Context HttpServletRequest httpRequest) {
		/*
		 * we try to find all the fields in the table, we generate the
		 * configuration for all svarog fields and all not svarog fields
		 */
		SvReader svr = null;
		JsonArray jArray = new JsonArray();
		try {
			svr = new SvReader(sessionId);
			DbDataObject tableObject = SvCore.getDbtByName(tableName);
			DbDataArray typetoGet = svr.getObjectsByParentId(tableObject.getObject_id(), svCONST.OBJECT_TYPE_FIELD,
					null, 0, 0, Rc.SORT_ORDER);
			if (!typetoGet.getItems().isEmpty()) {
				JsonArray tmpJArray = prapareSvarogFields1(tableName, svr);
				for (int i = 0; i < tmpJArray.size(); i++) {
					jArray.add((JsonObject) tmpJArray.get(i));
				}
			}
			for (int i = 0; i < typetoGet.getItems().size(); i++) {
				String tmpField = typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString();
				if (processField(tmpField)) {
					JsonObject tryObject = prapareObjectField1(tableName, typetoGet.getItems().get(i), svr);
					if (tryObject.toString().length() > 5)
						jArray.add(tryObject);
				}
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jArray.toString()).build();
	}

	/**
	 * Web service to return Json list of the fields that are fields in the
	 * table, it will also add all necessary SVAROG fields, it will also look
	 * for display names from SVAROG_LABELS
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            table that we try to display so we need the field names
	 * 
	 * @return Json with list of fields
	 */
	@Path("/getTableFieldListFull/{session_id}/{table_name}/{withData}")
	@GET
	@Produces("application/json")
	public Response getTableFieldListFull(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("withData") Boolean withData,
			@Context HttpServletRequest httpRequest) {
		/*
		 * we try to find all the fields in the table, we generate the
		 * configuration for all svarog fields and all not svarog fields
		 */
		SvReader svr = null;
		JsonArray jArray = new JsonArray();
		try {
			svr = new SvReader(sessionId);
			DbDataObject tableObject = SvCore.getDbtByName(tableName);
			DbDataArray typetoGet = svr.getObjectsByParentId(tableObject.getObject_id(), svCONST.OBJECT_TYPE_FIELD,
					null, 0, 0, Rc.SORT_ORDER);
			if (!typetoGet.getItems().isEmpty()) {
				JsonArray tmpJArray = prapareSvarogFieldsFull(tableName, true, svr);
				for (int i = 0; i < tmpJArray.size(); i++) {
					jArray.add((JsonObject) tmpJArray.get(i));
				}
			}
			if (withData) {
				for (int i = 0; i < typetoGet.getItems().size(); i++) {
					String tmpField = typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString();
					if (processField(tmpField)) {
						JsonObject tryObject = prapareObjectField1(tableName, typetoGet.getItems().get(i), svr);
						if (tryObject.toString().length() > 5)
							jArray.add(tryObject);
					}
				}
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jArray.toString()).build();
	}

	/**
	 * Web service to return any table with svarog repo fields
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table from which we want to get data
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * 
	 * @return Json with all objects found
	 */
	@Path("/getTableData/{session_id}/{table_name}/{no_rec}")
	@GET
	@Produces("application/json")
	public Response getTableSampleData(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("no_rec") Integer recordNumber,
			@Context HttpServletRequest httpRequest) {
		return getTableSampleData(sessionId, tableName, recordNumber, true, httpRequest);
	}

	/**
	 * Web service to return any table with svarog repo fields
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table from which we want to get data
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * @param doTranslate
	 *            Boolean should we translate the label codes ( use FALSE for
	 *            admin console so we can actually see the SVAROG_CODES)
	 * 
	 * @return Json with all objects found
	 */
	@Path("/getTableData/{session_id}/{table_name}/{no_rec}/{do_translate}")
	@GET
	@Produces("application/json")
	public Response getTableSampleData(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("no_rec") Integer recordNumber,
			@PathParam("do_translate") Boolean doTranslate, @Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		String retString = "";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		try {
			svr = new SvReader(sessionId);
			Long tableID = findTableType(tableName);
			DbDataArray vData = svr.getObjects(null, tableID, null, recordNumber, 0);
			tablesUsedArray[0] = getTableNameById(tableID, svr);
			tableShowArray[0] = true;
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, doTranslate,
					svr);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service to return any table using one filter "equals" for one field
	 * only , it will also return svarog repo fields
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table from which we want to get data
	 * @param fieldNAme
	 *            String name of the field that we try to filter
	 * @param fieldValue
	 *            String value that we are trying to find, will be cast to
	 *            Integer for numeric values
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * 
	 * @return Json with all objects found
	 */
	@Path("/getTableWithFilter/{session_id}/{table_name}/{fieldNAme}/{fieldValue}/{no_rec}")
	@GET
	@Produces("application/json")
	public Response getTableWithFilter(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("fieldNAme") String fieldName,
			@PathParam("fieldValue") String fieldValue, @PathParam("no_rec") Integer recordNumber,
			@Context HttpServletRequest httpRequest) {
		return getTableWithFilter(sessionId, tableName, fieldName, fieldValue, null, null, null, recordNumber,
				httpRequest);
	}

	/**
	 * Web service to return any table using filter "equals" for two fields , it
	 * will also return svarog repo fields, you can use AND/OR for concatenation
	 * of filters
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table from which we want to get data
	 * @param fieldNAme
	 *            String name of the field that we try to filter
	 * @param fieldValue
	 *            String value that we are trying to find, will be cast to
	 *            Integer for numeric values
	 * @param fieldName1
	 *            String name of the field that we try to filter
	 * @param fieldValue1
	 *            String value that we are trying to find, will be cast to
	 *            Integer for numeric values
	 * @param criterumConjuction
	 *            String AND/OR
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * 
	 * @return Json with all objects found
	 */
	@Path("/getTableWithFilter/{session_id}/{table_name}/{fieldNAme}/{fieldValue}/{fieldName1}/{fieldValue1}/{criterumConjuction}/{no_rec}")
	@GET
	@Produces("application/json")
	public Response getTableWithFilter(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("fieldNAme") String fieldName1,
			@PathParam("fieldValue") String fieldValue1, @PathParam("fieldName1") String fieldName2,
			@PathParam("fieldValue1") String fieldValue2, @PathParam("criterumConjuction") String criterumConjuction,
			@PathParam("no_rec") Integer recordNumber, @Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		String retString = "[ ]";
		String fieldWithSpecialCharacter1 = fieldValue1.trim();
		String fieldWithSpecialCharacter2 = fieldValue2 != null ? fieldValue2.trim() : null;
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		DbDataObject fieldObject1 = null;
		try {
			svr = new SvReader(sessionId);
			Long tableID = findTableType(tableName);
			fieldObject1 = SvCore.getFieldByName(tableName, fieldName1);
			if (fieldName1.equals("STATUS") && fieldObject1 == null) {
				fieldObject1 = SvCore.getFieldByName("SVAROG", fieldName1);
			}
			if (fieldValue1 != null && fieldValue1.contains("%2F")) {
				fieldWithSpecialCharacter1 = java.net.URLDecoder.decode(fieldValue1.trim(),
						StandardCharsets.UTF_8.name());
			}
			if (fieldValue2 != null && fieldValue2.contains("%2F")) {
				fieldWithSpecialCharacter2 = java.net.URLDecoder.decode(fieldValue2.trim(),
						StandardCharsets.UTF_8.name());
			}

			if (fieldObject1 != null) {
				DbSearchExpression expr = new DbSearchExpression();
				DbSearchCriterion crit1 = createCriterion(fieldObject1, fieldName1, DbCompareOperand.EQUAL,
						fieldWithSpecialCharacter1);
				if (fieldWithSpecialCharacter1.equals("RETIRED")) {
					crit1 = createCriterion2(fieldObject1, fieldName1, "NOTEQUAL", "VALID");
				}
				expr.addDbSearchItem(crit1);
				if (fieldName2 != null && fieldValue2 != null) {
					DbDataObject fieldObject2 = SvCore.getFieldByName(tableName, fieldName2);
					if (fieldName2.equals(Rc.PARENT_ID) && fieldObject2 == null) {
						fieldObject2 = SvCore.getFieldByName("SVAROG", fieldName2);
					}
					if ("AND".equalsIgnoreCase(criterumConjuction))
						expr.setNextCritOperand(DbLogicOperand.AND.toString());
					else if ("OR".equalsIgnoreCase(criterumConjuction))
						expr.setNextCritOperand(DbLogicOperand.OR.toString());
					DbSearchCriterion crit2 = createCriterion(fieldObject2, fieldName2, DbCompareOperand.EQUAL,
							fieldWithSpecialCharacter2);
					expr.addDbSearchItem(crit2);
				}
				DbDataArray vData = new DbDataArray();
				DbDataObject dboPoaLinkForObject = getPoaLinkIfExistsBetweenSearchedObjectWithOrgUnit(tableID, svr);
				DbDataArray linkedOrgUnitsPerUser = getLinkedOrgUnitsPerUser(svr);
				if (linkedOrgUnitsPerUser.size() == 0 || dboPoaLinkForObject == null) {
					vData = svr.getObjects(expr, tableID, null, recordNumber, 0);
				} else {
					DbDataArray vData1 = getObjectsWithPoa(tableName, svr);
					DbDataArray vData2 = svr.getObjects(expr, tableID, null, recordNumber, 0);
					for (DbDataObject tempVData1 : vData1.getItems()) {
						for (DbDataObject tempVData2 : vData2.getItems()) {
							if (tempVData1.getObject_id().equals(tempVData2.getObject_id())) {
								vData.addDataItem(tempVData1);
							}
						}
					}
				}
				tablesUsedArray[0] = getTableNameById(tableID, svr);
				tableShowArray[0] = true;
				retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} catch (UnsupportedEncodingException e) {
			log4j.error(e.toString(), e);
			return Response.status(401).entity(e.toString()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service to return any table using filter "equals" for one or multiple
	 * fields , it will also return svarog repo fields *(STATUS and PARENT_ID),
	 * you can use AND/OR for concatenation of filters
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table from which we want to get data
	 * @param fieldNames
	 *            String - names of fields that should be used in the search
	 *            criteria. The names are comma separated
	 * @param criterumConjuctions
	 *            String - logic operands that should be used in the search
	 *            between criteria. The names are comma separated
	 * @param fieldValues
	 *            String - names of values that should be used in the search
	 *            criteria. The values are comma separated
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * 
	 * @return Json with all objects found
	 */
	@Path("/getTableWithMultipleFilters/{session_id}/{table_name}/{fieldNames}/{criterumConjuctions}/{fieldValues}/{no_rec}")
	@GET
	@Produces("application/json")
	public Response getTableWithMultipleFilters(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("fieldNames") String fieldNames,
			@PathParam("criterumConjuctions") String criterumConjuctions, @PathParam("fieldValues") String fieldValues,
			@PathParam("no_rec") Integer recordNumber, @Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		String retString = "[ ]";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		List<String> lstFields = null;
		List<String> lstCriterias = null;
		List<String> lstValues = null;
		List<String> lstSubExprValues = null;
		Boolean hasSubExpr;
		DbSearchExpression expr = null;
		DbSearchExpression subExpr = null;
		Long tableID = null;
		try {
			svr = new SvReader(sessionId);
			tableID = findTableType(tableName);

			if (fieldNames.length() > 0 && criterumConjuctions.length() >= 0 && fieldValues.length() > 0) {
				lstFields = Arrays.asList(fieldNames.split(","));
				lstCriterias = Arrays.asList(criterumConjuctions.split(","));
				lstValues = Arrays.asList(fieldValues.split(","));
			}
			if (lstFields.size() == lstValues.size() && (lstValues.size() - 1 == lstCriterias.size()
					|| (lstValues.size() == 1 && lstCriterias.size() == 1))) {
				expr = new DbSearchExpression();
				for (int i = 0; i < lstFields.size(); i++) {
					hasSubExpr = false;
					DbDataObject fieldObject = SvCore.getFieldByName(tableName, lstFields.get(i));
					DbSearchCriterion crit = null;
					if ((lstFields.get(i).equalsIgnoreCase(Rc.STATUS)
							|| lstFields.get(i).equalsIgnoreCase(Rc.PARENT_ID)) && fieldObject == null) {
						fieldObject = SvCore.getFieldByName("SVAROG", lstFields.get(i));
					}
					if (fieldObject != null) {
						DbCompareOperand dbc = DbCompareOperand.EQUAL;
						DbLogicOperand dbl = DbLogicOperand.OR;
						String comparisonValue = lstValues.get(i).trim();
						if (comparisonValue.startsWith("NOT") && !comparisonValue.startsWith("NOTIN-")) {
							dbc = DbCompareOperand.NOTEQUAL;
							comparisonValue = comparisonValue.replaceAll("NOT", "");
							crit = createCriterion(fieldObject, lstFields.get(i), dbc, comparisonValue);
						} else if (comparisonValue.startsWith("IN-") || comparisonValue.startsWith("NOTIN-")) {
							hasSubExpr = true;
							subExpr = new DbSearchExpression();
							dbc = DbCompareOperand.EQUAL;
							if (comparisonValue.startsWith("NOTIN-")) {
								dbc = DbCompareOperand.NOTEQUAL;
								dbl = DbLogicOperand.AND;
							}
							comparisonValue = lstValues.get(i).trim();
							if (comparisonValue.startsWith("IN-")) {
								comparisonValue = comparisonValue.replaceAll("IN-", "");
							} else if (comparisonValue.startsWith("NOTIN-")) {
								comparisonValue = comparisonValue.replaceAll("NOTIN-", "");
							}
							comparisonValue = comparisonValue.replaceAll("-", ",");
							lstSubExprValues = Arrays.asList(comparisonValue.split(","));
							for (int j = 0; j < lstSubExprValues.size(); j++) {
								crit = createCriterion(fieldObject, lstFields.get(i), dbc, lstSubExprValues.get(j));
								subExpr.addDbSearchItem(crit);
								if (j < lstSubExprValues.size() - 1 && crit != null)
									crit.setNextCritOperand(dbl.toString());
							}
							expr.addDbSearchItem(subExpr);
						} else {
							crit = createCriterion(fieldObject, lstFields.get(i), dbc, comparisonValue);
						}
					}
					if (crit != null && !hasSubExpr) {
						expr.addDbSearchItem(crit);
					}
					if (expr != null && lstCriterias.size() > i && (lstCriterias.get(i).equalsIgnoreCase("AND")
							|| lstCriterias.get(i).equalsIgnoreCase("OR"))) {
						if ("AND".equalsIgnoreCase(lstCriterias.get(i)))
							expr.setNextCritOperand(DbLogicOperand.AND.toString());
						else if ("OR".equalsIgnoreCase(lstCriterias.get(i)))
							expr.setNextCritOperand(DbLogicOperand.OR.toString());
					}
				}
				if (expr != null) {
					DbDataArray vData = svr.getObjects(expr, tableID, null, recordNumber, 0);
					tablesUsedArray[0] = getTableNameById(tableID, svr);
					tableShowArray[0] = true;
					retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true,
							svr);

				}
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service to return any table using with search for fields that are
	 * passed by form
	 * 
	 * @param session_id
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param formVals
	 *            String table from which we want to get data
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * 
	 * 
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * 
	 * 
	 * @return Json with all objects found
	 */
	@Path("/searchTable/{session_id}/{table_name}/{no_rec}")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response searchTable(@PathParam("session_id") String sessionId, @PathParam("table_name") String tableName,
			@PathParam("no_rec") Integer recordNumber, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		String retString = "[ ]";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		try {
			svr = new SvReader(sessionId);
			Long tableID = findTableType(tableName);
			DbDataArray vData = searchTable(svr, tableName, formVals, recordNumber);
			tablesUsedArray[0] = getTableNameById(tableID, svr);
			tableShowArray[0] = true;
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service to return any table using one filter "like" for one field
	 * only , it will also return svarog repo fields
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table from which we want to get data
	 * @param fieldNAme
	 *            String name of the field that we try to filter
	 * @param fieldValue
	 *            String value that we are trying to find, will be cast to
	 *            Integer for numeric values
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * 
	 * @return Json with all objects found
	 */
	@Path("/getTableWithLike/{session_id}/{table_name}/{fieldNAme}/{fieldValue}/{no_rec}")
	@GET
	@Produces("application/json")
	public Response getTableWithLike(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("fieldNAme") String fieldName,
			@PathParam("fieldValue") String fieldValue, @PathParam("no_rec") Integer recordNumber,
			@Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		String retString = "";
		String fieldWithSpecialCharacter = fieldValue.trim();
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		try {
			svr = new SvReader(sessionId);
			Long tableID = findTableType(tableName);
			if (fieldValue != null && fieldValue.contains("%2F")) {
				fieldWithSpecialCharacter = java.net.URLDecoder.decode(fieldValue.trim(),
						StandardCharsets.UTF_8.name());
			}
			DbSearchExpression expr = new DbSearchExpression();
			DbSearchCriterion critU = new DbSearchCriterion(fieldName.toUpperCase(), DbCompareOperand.LIKE,
					'%' + fieldWithSpecialCharacter + '%');
			if (fieldName.toUpperCase().contains("DT") || fieldName.toUpperCase().contains("DATE")) {
				critU = new DbSearchCriterion(fieldName.toUpperCase(), DbCompareOperand.EQUAL,
						new DateTime(fieldWithSpecialCharacter));
			}
			expr.addDbSearchItem(critU);
			DbDataArray vData = svr.getObjects(expr, tableID, null, recordNumber, 0);
			tablesUsedArray[0] = getTableNameById(tableID, svr);
			tableShowArray[0] = true;
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} catch (UnsupportedEncodingException e) {
			log4j.error(e.toString(), e);
			return Response.status(401).entity(e.toString()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service to return any table using one filter "ilike" for one field
	 * only , it will also return svarog repo fields DEDICATE for: case
	 * insensitive search
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table from which we want to get data
	 * @param fieldNAme
	 *            String name of the field that we try to filter
	 * @param fieldValue
	 *            String value that we are trying to find, will be cast to
	 *            Integer for numeric values
	 * @param no_rec
	 *            Integer how many records we want to pull from the table
	 * 
	 * @return Json with all objects found
	 */
	@Path("/getTableWithILike/{session_id}/{table_name}/{fieldNAme}/{fieldValue}/{no_rec}")
	@GET
	@Produces("application/json")
	public Response getTableWithILike(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("fieldNAme") String fieldName,
			@PathParam("fieldValue") String fieldValue, @PathParam("no_rec") Integer recordNumber,
			@Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		String retString = "";
		String fieldWithSpecialCharacter = fieldValue.trim();
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		try {
			svr = new SvReader(sessionId);
			Long tableID = findTableType(tableName);
			if (fieldValue != null && fieldValue.contains("%2F")) {
				fieldWithSpecialCharacter = java.net.URLDecoder.decode(fieldValue.trim(),
						StandardCharsets.UTF_8.name());
			}
			DbSearchExpression expr = new DbSearchExpression();
			DbSearchCriterion critU = new DbSearchCriterion(fieldName.toUpperCase(), DbCompareOperand.ILIKE,
					'%' + fieldWithSpecialCharacter + '%');
			if (fieldName.toUpperCase().contains("DT") || fieldName.toUpperCase().contains("DATE")) {
				critU = new DbSearchCriterion(fieldName.toUpperCase(), DbCompareOperand.EQUAL,
						new DateTime(fieldWithSpecialCharacter));
			}
			expr.addDbSearchItem(critU);
			DbDataArray vData = new DbDataArray();
			DbDataObject dboPoaLinkForObject = getPoaLinkIfExistsBetweenSearchedObjectWithOrgUnit(tableID, svr);
			DbDataArray linkedOrgUnitsPerUser = getLinkedOrgUnitsPerUser(svr);
			// TODO linkedOrgUnitsPerUser.size() == 0 should be excluded when
			// this will be implemented in svarog getObjects cause it disturbs
			// the POA model
			if (linkedOrgUnitsPerUser.size() == 0 || dboPoaLinkForObject == null) {
				vData = svr.getObjects(expr, tableID, null, recordNumber, 0);
			} else {
				DbDataArray vData1 = getObjectsWithPoa(tableName, svr);
				DbDataArray vData2 = svr.getObjects(expr, tableID, null, recordNumber, 0);
				for (DbDataObject tempVData1 : vData1.getItems()) {
					for (DbDataObject tempVData2 : vData2.getItems()) {
						if (tempVData1.getObject_id().equals(tempVData2.getObject_id())) {
							vData.addDataItem(tempVData1);
						}
					}
				}
			}
			tablesUsedArray[0] = getTableNameById(tableID, svr);
			tableShowArray[0] = true;
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} catch (UnsupportedEncodingException e) {
			log4j.error(e.toString(), e);
			return Response.status(401).entity(e.toString()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service version of SvReader.getObjectsByParentId that can return
	 * objects of objectType that are children to object with ID parentId
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param parentId
	 *            ID of the Object for which we like to get all children objects
	 * @param objectName
	 *            String Id or the name of the table for the child objects
	 * @param rowLimit
	 *            How many items we want per page
	 * @param sortField
	 *            Name of the field of type "objectType" that we want to sort by
	 *            (asc only)
	 * 
	 * @return Json Array of objects of type objectType, children of object with
	 *         ID parentId
	 */
	@Path("/getObjectsByParentId/{sessionId}/{parentId}/{objectName}/{rowLimit}/{sortByField}")
	@GET
	@Produces("application/json")
	public Response getObjectsByParentId(@PathParam("sessionId") String sessionId, @PathParam("parentId") Long parentId,
			@PathParam("objectName") String objectName, @PathParam("refDateString") String refDateString,
			@PathParam("rowLimit") Integer rowLimit, @PathParam("sortByField") String sortByField,
			@Context HttpServletRequest httpRequest) {
		String retString = "";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		SvReader svr = null;
		Long pobjectType = 0L;
		try {
			// try to find the type with ID
			pobjectType = findTableType(objectName);
			svr = new SvReader(sessionId);
			tablesUsedArray[0] = getTableNameById(pobjectType, svr);
			tableShowArray[0] = true;
			DbDataArray vData = svr.getObjectsByParentId(parentId, pobjectType, null, rowLimit, 0, sortByField);
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service version of SvReader.getObjectByObjectId that can return
	 * complete data for the objectId
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param objectId
	 *            ID of the Object for which we like to get complete data
	 * @param objectName
	 *            String Id or the name of the table for the child objects
	 * @return Json Array of objects of type objectType, children of object with
	 *         ID parentId
	 */
	@Path("/getRowDataByObjectId/{sessionId}/{objectId}/{objectName}")
	@GET
	@Produces("application/json")
	public Response getRowDataByObjectId(@PathParam("sessionId") String sessionId, @PathParam("objectId") Long objectId,
			@PathParam("objectName") String objectName, @Context HttpServletRequest httpRequest) {
		String retString = "";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		SvReader svr = null;
		Long pobjectType = 0L;
		try {
			// try to find the type with ID
			pobjectType = findTableType(objectName);
			svr = new SvReader(sessionId);
			tablesUsedArray[0] = getTableNameById(pobjectType, svr);
			tableShowArray[0] = true;
			DbDataArray vData = new DbDataArray();
			DbDataObject dboObjFound = svr.getObjectById(objectId, pobjectType, new DateTime());
			if (dboObjFound != null) {
				vData.addDataItem(dboObjFound);
			}
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service version of SvReader.getObjectsByParentId that can return
	 * objects of objectType that are children to object with ID parentId
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param parentId
	 *            ID of the Object for which we like to get all children objects
	 * @param objectName
	 *            String Id or the name of the table for the child objects
	 * @param rowLimit
	 *            How many items we want per page
	 * 
	 * @return Json Array of objects of type objectType, children of object with
	 *         ID parentId
	 */
	@Path("/getObjectsByParentId/{sessionId}/{parentId}/{objectName}/{rowLimit}")
	@GET
	@Produces("application/json")
	public Response getObjectsByParentId(@PathParam("sessionId") String sessionId, @PathParam("parentId") Long parentId,
			@PathParam("objectName") String objectName, @PathParam("refDateString") String refDateString,
			@PathParam("rowLimit") Integer rowLimit, @Context HttpServletRequest httpRequest) {
		return getObjectsByParentId(sessionId, parentId, objectName, refDateString, rowLimit, null, httpRequest);
	}

	/**
	 * Web service version of SvReader.getObjectsByParentId that can return
	 * objects of objectType that are children to object with ID parentId
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param parentId
	 *            ID of the Object for which we like to get all children objects
	 * @param objectName
	 *            String Id or the name of the table for the child objects
	 * @param rowLimit
	 *            How many items we want per page
	 * 
	 * @return Json Array of objects of type objectType, children of object with
	 *         ID parentId
	 */
	@Path("/getHistoryObjectsByParentId/{sessionId}/{parentId}/{objectName}/{rowLimit}")
	@GET
	@Produces("application/json")
	public Response getHistoryObjectsByParentId(@PathParam("sessionId") String sessionId,
			@PathParam("parentId") Long parentId, @PathParam("objectName") String objectName,
			@PathParam("refDateString") String refDateString, @PathParam("rowLimit") Integer rowLimit,
			@Context HttpServletRequest httpRequest) {
		String retString = "";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		SvReader svr = null;
		Long pobjectType = 0L;
		try {
			// try to find the type with ID
			pobjectType = findTableType(objectName);
			svr = new SvReader(sessionId);
			tablesUsedArray[0] = getTableNameById(pobjectType, svr);
			tableShowArray[0] = true;
			DbSearchExpression dbse = new DbSearchExpression();
			DbSearchCriterion dbc1 = new DbSearchCriterion(Rc.PARENT_ID, DbCompareOperand.EQUAL, parentId);
			dbse.addDbSearchItem(dbc1);
			DbDataArray vData = svr.getObjectsHistory(dbse, SvReader.getTypeIdByName(objectName), 0, 0);
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service version of SvReader.getHistoryObjectsByObjectId that can
	 * return for certain object_id
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param parentId
	 *            ID of the Object for which we like to get all versions
	 * @param objectName
	 *            String Id or the name of the table for the child objects
	 * @param rowLimit
	 *            How many items we want per page
	 * 
	 * @return Json Array of objects of type objectType, children of object with
	 *         ID parentId
	 */
	@Path("/getHistoryObjectsByObjectId/{sessionId}/{objectId}/{objectName}/{rowLimit}")
	@GET
	@Produces("application/json")
	public Response getHistoryObjectsByObjectId(@PathParam("sessionId") String sessionId,
			@PathParam("objectId") Long objectId, @PathParam("objectName") String objectName,
			@PathParam("refDateString") String refDateString, @PathParam("rowLimit") Integer rowLimit,
			@Context HttpServletRequest httpRequest) {
		String retString = "";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		SvReader svr = null;
		Long pobjectType = 0L;
		try {
			// try to find the type with ID
			pobjectType = findTableType(objectName);
			svr = new SvReader(sessionId);
			tablesUsedArray[0] = getTableNameById(pobjectType, svr);
			tableShowArray[0] = true;
			DbSearchExpression dbse = new DbSearchExpression();
			DbSearchCriterion dbc1 = new DbSearchCriterion(Rc.OBJECT_ID, DbCompareOperand.EQUAL, objectId);
			dbse.addDbSearchItem(dbc1);
			DbDataArray vData = svr.getObjectsHistory(dbse, SvReader.getTypeIdByName(objectName), 0, 0);
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	@Path("/getObjectByLink/{sessionId}/{objectId}/{table_name}/{linkName}/{rowLimit}")
	@GET
	@Produces("application/json")
	public Response getObjectsByLink(@PathParam("sessionId") String sessionId, @PathParam("objectId") Long objectId,
			@PathParam("table_name") String tableName, @PathParam("linkName") String linkName,
			@PathParam("rowLimit") Integer rowLimit, @Context HttpServletRequest httpRequest) {
		return getObjectsByLink(sessionId, objectId, tableName, linkName, null, rowLimit, httpRequest);
	}

	/**
	 * Web service version of SvReader.getObjectByLink that can return objects
	 * of objectType that are children to object with ID parentId
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param objectId1
	 *            ID of the Object for which we like to get all linked objects
	 * @param table_name
	 *            String Name of the table that returning objects are
	 * @param linkName
	 *            String name of the link
	 * @param linkStatus
	 *            String status of the link
	 * @param rowLimit
	 *            How many items we want for return
	 * 
	 * @return Json Array of objects of type objectType, children of object with
	 *         ID parentId
	 */
	@Path("/getObjectByLink/{sessionId}/{objectId}/{table_name}/{linkName}/{rowLimit}/{link_status}")
	@GET
	@Produces("application/json")
	public Response getObjectsByLink(@PathParam("sessionId") String sessionId, @PathParam("objectId") Long objectId,
			@PathParam("table_name") String tableName, @PathParam("linkName") String linkName,
			@PathParam("link_status") String linkStatus, @PathParam("rowLimit") Integer rowLimit,
			@Context HttpServletRequest httpRequest) {
		String retString = prepareRetStringPerGetObjectsByLink(sessionId, objectId, null, tableName, linkName,
				linkStatus, rowLimit);
		return Response.status(200).entity(retString).build();
	}

	@Path("/getObjectsByLinkPerStatuses/{sessionId}/{objectId}/{statuses}/{table_name}/{linkName}/{rowLimit}/{link_status}")
	@GET
	@Produces("application/json")
	public Response getObjectsByLinkPerStatuses(@PathParam("sessionId") String sessionId,
			@PathParam("objectId") Long objectId, @PathParam("table_name") String tableName,
			@PathParam("statuses") String statuses, @PathParam("linkName") String linkName,
			@PathParam("link_status") String linkStatus, @PathParam("rowLimit") Integer rowLimit,
			@Context HttpServletRequest httpRequest) {
		String retString = prepareRetStringPerGetObjectsByLink(sessionId, objectId, statuses, tableName, linkName,
				linkStatus, rowLimit);
		return Response.status(200).entity(retString).build();
	}

	public String prepareRetStringPerGetObjectsByLink(String sessionId, Long objectId, String statuses,
			String tableName, String linkName, String linkStatus, Integer rowLimit) {
		String retString = "";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		SvReader svr = null;
		Long obj1Type = 0L;
		Boolean isReverse = true;
		DbDataArray vData = null;
		Long tableID = findTableType(tableName);
		try {
			svr = new SvReader(sessionId);
			tablesUsedArray[0] = getTableNameById(tableID, svr);
			tableShowArray[0] = true;
			DbDataObject dbLink = findLinkWithAdditionalCheck(getTableNameById(tableID, svr), linkName, objectId, svr);
			if (dbLink != null) {
				if (tableID.equals(dbLink.getVal(Rc.LINK_OBJECT_TYPE1))) {
					isReverse = true; // Gjorgi made me swap them
					obj1Type = (Long) dbLink.getVal(Rc.LINK_OBJECT_TYPE2);
				} else {
					isReverse = false; // Gjorgi made me swap them
					obj1Type = (Long) dbLink.getVal(Rc.LINK_OBJECT_TYPE1);
				}
				vData = svr.getObjectsByLinkedId(objectId, obj1Type, dbLink, SvCore.getTypeIdByName(tablesUsedArray[0]),
						isReverse, null, rowLimit, 0, linkStatus);
				if (statuses != null && statuses.trim().length() > 0) {
					vData = filterDataByStatus(statuses, vData);
				}
				retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
			} else
				retString = "LINK NOT FOUND IN DATABASE";
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
		} finally {
			releaseAll(svr);
		}
		return retString;
	}

	public DbDataArray filterDataByStatus(String statuses, DbDataArray arrayToFilter) {
		DbDataArray result = new DbDataArray();
		if (statuses != null && statuses.trim().length() > 0) {
			List<String> statusesList = new ArrayList<>(Arrays.asList(statuses.replaceAll(" ", "").trim().split(",")));
			if (!statusesList.isEmpty()) {
				for (DbDataObject tempObject : arrayToFilter.getItems()) {
					if (tempObject.getStatus() != null && statusesList.contains(tempObject.getStatus())) {
						result.addDataItem(tempObject);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Web service for adding new record in a table , return is JSON
	 * react-jsonschema-form compatible string
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table name for which we want to insert new element
	 *            (record)
	 * 
	 * @return Json string with all fields ( field names pulled from database)
	 *         and drop-down values translated
	 */
	@Path("/getTableJSONSchema/{sessionId}/{table_name}")
	@GET
	@Produces("application/json")
	public Response getTableJSONSchema(@PathParam("sessionId") String sessionId,
			@PathParam("table_name") String tableName, @Context HttpServletRequest httpRequest) {
		JsonObject jData = new JsonObject();
		SvReader svr = null;
		SvParameter svp = null;
		Gson gson = new Gson();
		try {
			ArrayList<String> listRequired = new ArrayList<>();
			HashMap<String, String> listRequiredWithLink = new HashMap<>();
			Set<String> set2 = new LinkedHashSet<>();
			Set<String> set1 = new LinkedHashSet<>();
			svr = new SvReader(sessionId);
			svp = new SvParameter(svr);
			DbDataObject tableObject = SvCore.getDbtByName(tableName);
			jData.addProperty(Rc.TITLE, I18n.getText(getLocaleId(svr), tableObject.getVal(Rc.LABEL_CODE).toString()));
			jData.addProperty(Rc.TYPE, Rc.OBJECT);
			DbDataArray dboFieldsPerTable = svr.getObjectsByParentId(tableObject.getObject_id(),
					svCONST.OBJECT_TYPE_FIELD, null, 0, 0, Rc.SORT_ORDER);
			JsonObject jFields = new JsonObject();
			for (DbDataObject tempDboField : dboFieldsPerTable.getItems()) {
				String tmpField = tempDboField.getVal(Rc.FIELD_NAME).toString();
				if (processField(tmpField)) {
					JsonObject guiMetadata = null;
					if (tempDboField.getVal(Rc.GUI_METADATA) != null)
						guiMetadata = gson.fromJson(tempDboField.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
					JsonObject jsonreactGUI = null;
					if (guiMetadata != null && guiMetadata.has(Rc.REACT))
						jsonreactGUI = (JsonObject) guiMetadata.get(Rc.REACT);
					JsonObject jLeaf = new JsonObject();
					// make a list and set of required fields, list of groups
					// (paths) and list of combination of those 2
					if ((("false").equalsIgnoreCase(tempDboField.getVal(Rc.ISNULL).toString())) && (!(("SVAROG_FILES")
							.equalsIgnoreCase(tableName)
							&& (("FILE_NAME").equalsIgnoreCase(tempDboField.getVal(Rc.FIELD_NAME).toString())
									|| ("FILE_SIZE").equalsIgnoreCase(tempDboField.getVal(Rc.FIELD_NAME).toString())
									|| ("FILE_DATE").equalsIgnoreCase(tempDboField.getVal(Rc.FIELD_NAME).toString())
									|| ("FILE_ID").equalsIgnoreCase(tempDboField.getVal(Rc.FIELD_NAME).toString()))))) {
						listRequired.add(tmpField);
						set2.add(tmpField);
						if (jsonreactGUI != null && jsonreactGUI.has(Rc.GROUPPATH)) {
							String pathString = jsonreactGUI.get(Rc.GROUPPATH).getAsString();
							set1.add(pathString);
							listRequiredWithLink.put(tmpField, pathString);
						}
					}
					jLeaf = addFieldTypeToJsonObject(tempDboField, jLeaf, true);
					jLeaf.addProperty(Rc.TITLE,
							I18n.getText(getLocaleId(svr), tempDboField.getVal(Rc.LABEL_CODE).toString()));
					if (Rc.NVARCHAR.equals(tempDboField.getVal(Rc.FIELD_TYPE).toString())
							&& ((Long) tempDboField.getVal(Rc.FIELD_SIZE)) != null
							&& ((Long) tempDboField.getVal(Rc.FIELD_SIZE)) > 0)
						jLeaf.addProperty("maxLength", (Long) tempDboField.getVal(Rc.FIELD_SIZE));
					if (jsonreactGUI != null && jsonreactGUI.has("minLength"))
						jLeaf.addProperty("minLength", jsonreactGUI.get("minLength").getAsNumber());
					if (Rc.NUMERIC.equals(tempDboField.getVal(Rc.FIELD_TYPE).toString()))
						jLeaf.addProperty("maximum", 999999999999999L);
					// prepare drop-down if not a boolean field
					// if
					// (!"BOOLEAN".equalsIgnoreCase(typetoGet.getItems().get(i).getVal("FIELD_TYPE").toString()))
					jLeaf = prepareFormJsonCodeList1(tempDboField, jLeaf, svr);
					jFields = prepareFormJsonGroup(tempDboField, jFields, jLeaf);

				}
				if ("GEOM".equalsIgnoreCase(tmpField)) {
					String useRealGeom = svp.getParamString("param.useRealGeom");
					if (useRealGeom != null && "Y".equalsIgnoreCase(useRealGeom)) {
						// we are using realGeoms so no need to show them
					} else {
						jFields = prepareFormJsonGeometry(jFields);
					}
				}
			}
			jData.add(Rc.PROPERTIES, jFields);
			/*
			 * parse the lists of required elements, create JsonElement for
			 * every group and add in proper level for the JsonData
			 */
			if (!listRequired.isEmpty()) {
				Iterator itr = set1.iterator();
				while (itr.hasNext()) {
					ArrayList<String> listPathRequired = new ArrayList<>();
					String vPath = itr.next().toString();
					itr.remove();
					Iterator<Entry<String, String>> it = listRequiredWithLink.entrySet().iterator();
					while (it.hasNext()) {
						Entry<String, String> pair = it.next();
						String tmpStr1 = pair.getValue();
						if (vPath.equals(tmpStr1)) {
							it.remove();
							listPathRequired.add(pair.getKey());
							String tmpStr2 = pair.getKey();
							for (int k = 0; k < listRequired.size(); k++)
								if (tmpStr2.equals(listRequired.get(k)))
									listRequired.remove(k);
						}
					}
					JsonElement element1 = gson.toJsonTree(listPathRequired, new TypeToken<List<String>>() {
					}.getType());
					JsonObject tmpData = (JsonObject) jData.get(Rc.PROPERTIES);
					JsonObject tmpData1 = (JsonObject) tmpData.get(vPath);
					tmpData1.add(Rc.REQUIRED, element1);
					tmpData.add(vPath, tmpData1);
					jData.add(Rc.PROPERTIES, tmpData);
				}
				JsonElement element = gson.toJsonTree(listRequired, new TypeToken<List<String>>() {
				}.getType());
				if (element.isJsonArray()) {
					jData.add(Rc.REQUIRED, element);
				}
			}
		} catch (

		SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
			releaseAll(svp);
		}
		return Response.status(200).entity(jData.toString()).build();
	}

	/**
	 * Web service to get the schema for react UI , UI schema to be stored in
	 * SVAROG_FIELDS , field GUI_METADATA sub_object "react" , sub_object
	 * "uischema", it will just read the full object as it is and add it to
	 * return string with the same field name to be paired to the object
	 * returned by getTableJSONSchema WS
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String table name for which we want to insert new element
	 *            (record)
	 * 
	 * @return Json string with UI json for all fields in the table
	 */
	@Path("/getTableUISchema/{sessionId}/{table_name}")
	@GET
	@Produces("application/json")
	public Response getTableUISchema(@PathParam("sessionId") String sessionId,
			@PathParam("table_name") String tableName, @Context HttpServletRequest httpRequest) {
		JsonObject jsonData = new JsonObject();
		Gson gson = new Gson();
		SvReader svr = null;
		try {
			svr = new SvReader(sessionId);
			Long tableID = findTableType(tableName);
			DbDataArray typetoGet = svr.getObjectsByParentId(tableID, svCONST.OBJECT_TYPE_FIELD, null, 0, 0,
					Rc.SORT_ORDER);
			for (int i = 0; i < typetoGet.getItems().size(); i++) {
				JsonObject jsonreactGUI = null;
				JsonObject jsonObj = null;
				JsonObject jsonUISchema = null;
				String tmpField = typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString();
				if (processField(tmpField)) {
					if (typetoGet.getItems().get(i).getVal(Rc.GUI_METADATA) != null)
						jsonObj = gson.fromJson(typetoGet.getItems().get(i).getVal(Rc.GUI_METADATA).toString(),
								JsonObject.class);
					if (jsonObj != null && jsonObj.has(Rc.REACT))
						jsonreactGUI = (JsonObject) jsonObj.get(Rc.REACT);
					if (jsonreactGUI != null && jsonreactGUI.has(Rc.UISCHEMA))
						jsonUISchema = (JsonObject) jsonreactGUI.get(Rc.UISCHEMA);
					// if this is first object in group create the group obect,
					// if not, retreve it, add it to exising and put it back
					if (jsonUISchema != null) {
						String groupPath = null;
						if (jsonreactGUI != null && jsonreactGUI.has(Rc.GROUPPATH)) { // grouppath
																						// found
							groupPath = jsonreactGUI.get(Rc.GROUPPATH).getAsString();
							JsonObject groupValues = null;
							if (jsonData.has(groupPath))
								groupValues = (JsonObject) jsonData.get(groupPath);
							if (groupValues == null)
								groupValues = new JsonObject();
							groupValues.add(tmpField, jsonUISchema);
							jsonData.add(groupPath, groupValues);
						} else // no grouppath found
							jsonData.add(tmpField, jsonUISchema);
					}
				}
				if ("GEOM".equalsIgnoreCase(tmpField)) {
					jsonObj = gson.fromJson(
							"{\"ui:options\":{\"orderable\":false,\"rows\":15},\"items\":{\"cordxvals\":{\"ui:widget\":\"textarea\"},\"cordyvals\":{\"ui:widget\":\"textarea\"}}}",
							JsonObject.class);
					jsonData.add(Rc.MULTYPOLYARRAY, jsonObj);
				}
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jsonData.toString()).build();
	}

	/**
	 * Web service (very similar to getObjectbyID) to get one record from one
	 * table so it can be easy displayed in the custom Forms and then edited and
	 * saved, the point of pulling the record again is to fill the data
	 * automagic and get new PKID
	 * 
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param object_id
	 *            Long object ID of the record that we want to read
	 * @param table_name
	 *            String table name or table ID for which we want to to get the
	 *            record from
	 * 
	 * @return Json string with UI json for all fields in the table
	 */
	@Path("/getTableFormData/{sessionId}/{object_id}/{table_name}")
	@GET
	@Produces("application/json")
	public Response getTableFormData(@PathParam("sessionId") String sessionId, @PathParam("object_id") Long objectid,
			@PathParam("table_name") String tableName, @Context HttpServletRequest httpRequest) {
		JsonObject jsonData = new JsonObject();
		DbDataObject reqObject = null;
		SvReader svr = null;
		SvParameter svp = null;
		try {
			svr = new SvReader(sessionId);
			svp = new SvParameter(svr);
			svr.setIncludeGeometries(true);
			Long tableID = findTableType(tableName);
			reqObject = svr.getObjectById(objectid, tableID, null);
			DbDataArray typetoGet = new DbDataArray();
			typetoGet = svr.getObjectsByParentId(tableID, svCONST.OBJECT_TYPE_FIELD, null, 0, 0, Rc.SORT_ORDER);
			if (reqObject != null)
				for (int i = 0; i < typetoGet.getItems().size(); i++) {
					String tmpField = typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString();
					jsonData.addProperty(Rc.OBJECT_ID, reqObject.getObject_id());
					jsonData.addProperty(Rc.OBJECT_TYPE, reqObject.getObject_type());
					jsonData.addProperty(Rc.PKID, reqObject.getPkid());
					jsonData.addProperty(Rc.PARENT_ID, reqObject.getParent_id());
					if (processField(tmpField)) {
						jsonData = addValueToJsonObject1(jsonData, reqObject, typetoGet.getItems().get(i));
					}
					Geometry geom = SvGeometry.getGeometry(reqObject);
					JsonArray jArray = new JsonArray();
					if ("GEOM".equalsIgnoreCase(tmpField)) {
						String useRealGeom = svp.getParamString("param.useRealGeom");
						if (useRealGeom != null && "Y".equalsIgnoreCase(useRealGeom) && geom != null) {
							GeoJsonWriter geoj = new GeoJsonWriter();
							String stringedPoly = geoj.write(geom);
							JsonObject jsonedPoly = new JsonObject();
							Gson gs = new Gson();
							jsonedPoly = gs.fromJson(stringedPoly, JsonObject.class);
							jsonData.add(Rc.MULTIPOLYGEOMETRY, jsonedPoly);
						} else {
							if (geom != null)
								for (int j = 0; j < geom.getNumGeometries(); j++) {
									Geometry gm = geom.getGeometryN(j);
									Polygon[] polyarray = new Polygon[gm.getNumGeometries()];
									JsonObject elementXY = new JsonObject();
									for (int k = 0; k < gm.getNumGeometries(); k++) {
										polyarray[k] = (Polygon) gm.getGeometryN(k);
										Polygon tmpPoly = polyarray[k];
										Coordinate[] arrayX = tmpPoly.getCoordinates();
										StringBuilder coordBuilderX = new StringBuilder();
										StringBuilder coordBuilderY = new StringBuilder();
										for (int m = 0; m < arrayX.length; m++) {
											Long xL = (Long) Math.round(arrayX[m].y);
											Long yL = (Long) Math.round(arrayX[m].x);
											coordBuilderX.append(xL.toString() + "\n");
											coordBuilderY.append(yL.toString() + "\n");
										}
										coordBuilderX.delete(coordBuilderX.length() - 1, coordBuilderX.length());
										coordBuilderY.delete(coordBuilderY.length() - 1, coordBuilderY.length());
										elementXY.addProperty("cordxvals", coordBuilderX.toString());
										elementXY.addProperty("cordyvals", coordBuilderY.toString());
									}
									jArray.add(elementXY);
								}
							jsonData.add(Rc.MULTYPOLYARRAY, jArray);
						}
					}
				}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
			releaseAll(svp);
		}
		return Response.status(200).entity(jsonData.toString()).build();
	}

	/**
	 * Web service for adding new form/document , return is JSON
	 * react-jsonschema-form compatible string
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param form_name
	 *            String/Long form code SVAROG_FORM_TYPE.LABEL_CODE or object id
	 *            SVAROG_FORM_TYPE.OBJECT_ID that describes the document best
	 * 
	 * @return Json string with all fields ( field names pulled from database)
	 *         and drop-down values translated and mandatory fields marked with
	 *         star
	 * 
	 */
	@Path("/getFormJSONSchema/{sessionId}/{form_name}/{scenario}")
	@GET
	@Produces("application/json")
	public Response getFormJSONSchema(@PathParam("sessionId") String sessionId, @PathParam("form_name") String formName,
			@PathParam("scenario") Long scenario, @Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		DbDataArray typetoGet = null;
		DbDataObject formObject = null;
		JsonObject jData = new JsonObject();
		ArrayList<String> listRequired = new ArrayList<>();
		Gson gson = new Gson();
		try {
			svr = new SvReader(sessionId);
			formObject = findFormObject(formName, svr);
			if (formObject != null) {
				jData.addProperty(Rc.TITLE,
						I18n.getText(getLocaleId(svr), formObject.getVal(Rc.LABEL_CODE).toString()));
				jData.addProperty(Rc.TYPE, Rc.OBJECT);
				JsonObject jFields = new JsonObject();
				DbDataObject dbLink = SvCore.getLinkType(Rc.FORM_FIELD_LINK, svCONST.OBJECT_TYPE_FORM_TYPE,
						svCONST.OBJECT_TYPE_FORM_FIELD_TYPE);
				typetoGet = svr.getObjectsByLinkedId(formObject.getObject_id(), svCONST.OBJECT_TYPE_FORM_TYPE, dbLink,
						svCONST.OBJECT_TYPE_FORM_FIELD_TYPE, false, null, 0, 0);
				for (DbDataObject itemI : typetoGet.getSortedItems("SORT_ORDER")) {
					JsonObject jLeaf = new JsonObject();
					switch (scenario.toString()) {
					case "1":
						if ("false".equalsIgnoreCase(itemI.getVal(Rc.ISNULL).toString()))
							listRequired.add(itemI.getVal(Rc.LABEL_CODE).toString());
						jLeaf = addFieldTypeToJsonObject(itemI, jLeaf, false);
						jLeaf = prepareFormJsonCodeList1(itemI, jLeaf, svr);
						jLeaf.addProperty(Rc.TITLE,
								I18n.getText(getLocaleId(svr), itemI.getVal(Rc.LABEL_CODE).toString()));
						jFields.add(itemI.getVal(Rc.LABEL_CODE).toString(), jLeaf);
						break;
					case "2":
						jLeaf = addFieldTypeToJsonObject(itemI, jLeaf, false);
						jLeaf = prepareFormJsonCodeList1(itemI, jLeaf, svr);
						jLeaf.addProperty(Rc.TITLE,
								I18n.getText(getLocaleId(svr), itemI.getVal(Rc.LABEL_CODE).toString()));
						jFields.add(itemI.getVal(Rc.LABEL_CODE).toString(), jLeaf);
						jLeaf = new JsonObject();
						jLeaf = addFieldTypeToJsonObject(itemI, jLeaf, false);
						jLeaf = prepareFormJsonCodeList1(itemI, jLeaf, svr);
						jLeaf.addProperty(Rc.TITLE,
								I18n.getText(getLocaleId(svr), itemI.getVal(Rc.LABEL_CODE).toString())
										+ I18n.getText("form.field.first_check"));
						jFields.add(itemI.getVal(Rc.LABEL_CODE).toString() + "_1ST", jLeaf);
						break;
					case "3":
						jLeaf = addFieldTypeToJsonObject(itemI, jLeaf, false);
						jLeaf = prepareFormJsonCodeList1(itemI, jLeaf, svr);
						jLeaf.addProperty(Rc.TITLE,
								I18n.getText(getLocaleId(svr), itemI.getVal(Rc.LABEL_CODE).toString()));
						jFields.add(itemI.getVal(Rc.LABEL_CODE).toString(), jLeaf);
						jLeaf = new JsonObject();
						jLeaf = addFieldTypeToJsonObject(itemI, jLeaf, false);
						jLeaf = prepareFormJsonCodeList1(itemI, jLeaf, svr);
						jLeaf.addProperty(Rc.TITLE,
								I18n.getText(getLocaleId(svr), itemI.getVal(Rc.LABEL_CODE).toString())
										+ I18n.getText("form.field.second_check"));
						jFields.add(itemI.getVal(Rc.LABEL_CODE).toString() + "_2ND", jLeaf);
						break;
					case "4":
						jLeaf = addFieldTypeToJsonObject(itemI, jLeaf, false);
						jLeaf = prepareFormJsonCodeList1(itemI, jLeaf, svr);
						jLeaf.addProperty(Rc.TITLE,
								I18n.getText(getLocaleId(svr), itemI.getVal(Rc.LABEL_CODE).toString()));
						jFields.add(itemI.getVal(Rc.LABEL_CODE).toString(), jLeaf);
						jLeaf = new JsonObject();
						jLeaf = addFieldTypeToJsonObject(itemI, jLeaf, false);
						jLeaf = prepareFormJsonCodeList1(itemI, jLeaf, svr);
						jLeaf.addProperty(Rc.TITLE,
								I18n.getText(getLocaleId(svr), itemI.getVal(Rc.LABEL_CODE).toString())
										+ I18n.getText("form.field.first_check"));
						jFields.add(itemI.getVal(Rc.LABEL_CODE).toString() + "_1ST", jLeaf);
						jLeaf = new JsonObject();
						jLeaf = addFieldTypeToJsonObject(itemI, jLeaf, false);
						jLeaf = prepareFormJsonCodeList1(itemI, jLeaf, svr);
						jLeaf.addProperty(Rc.TITLE,
								I18n.getText(getLocaleId(svr), itemI.getVal(Rc.LABEL_CODE).toString())
										+ I18n.getText("form.field.second_check"));
						jFields.add(itemI.getVal(Rc.LABEL_CODE).toString() + "_2ND", jLeaf);
						break;
					default:
					}
				}
				/*
				 * for (int i = 0; i < typetoGet.getItems().size(); i++) {
				 * 
				 * JsonObject jLeaf = new JsonObject(); switch
				 * (scenario.toString()) { case "1": if
				 * ("false".equalsIgnoreCase(typetoGet.getItems().get(i).getVal(
				 * Rc.ISNULL).toString()))
				 * listRequired.add(typetoGet.getItems().get(i).getVal(Rc.
				 * LABEL_CODE).toString()); jLeaf =
				 * addFieldTypeToJsonObject((DbDataObject)
				 * typetoGet.getItems().get(i), jLeaf, false); jLeaf =
				 * prepareFormJsonCodeList1(typetoGet.getItems().get(i), jLeaf,
				 * svr); jLeaf.addProperty(Rc.TITLE,
				 * I18n.getText(getLocaleId(svr),
				 * typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString())
				 * );
				 * jFields.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE)
				 * .toString(), jLeaf); break; case "2": jLeaf =
				 * addFieldTypeToJsonObject((DbDataObject)
				 * typetoGet.getItems().get(i), jLeaf, false); jLeaf =
				 * prepareFormJsonCodeList1(typetoGet.getItems().get(i), jLeaf,
				 * svr); jLeaf.addProperty(Rc.TITLE,
				 * I18n.getText(getLocaleId(svr),
				 * typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString())
				 * );
				 * jFields.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE)
				 * .toString(), jLeaf);
				 * 
				 * jLeaf = new JsonObject(); jLeaf =
				 * addFieldTypeToJsonObject((DbDataObject)
				 * typetoGet.getItems().get(i), jLeaf, false); jLeaf =
				 * prepareFormJsonCodeList1(typetoGet.getItems().get(i), jLeaf,
				 * svr); jLeaf.addProperty(Rc.TITLE,
				 * I18n.getText(getLocaleId(svr),
				 * typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString())
				 * + I18n.getText("form.field.first_check"));
				 * jFields.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE)
				 * .toString() + "_1ST", jLeaf); break; case "3": jLeaf =
				 * addFieldTypeToJsonObject((DbDataObject)
				 * typetoGet.getItems().get(i), jLeaf, false); jLeaf =
				 * prepareFormJsonCodeList1(typetoGet.getItems().get(i), jLeaf,
				 * svr); jLeaf.addProperty(Rc.TITLE,
				 * I18n.getText(getLocaleId(svr),
				 * typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString())
				 * );
				 * jFields.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE)
				 * .toString(), jLeaf);
				 * 
				 * jLeaf = new JsonObject(); jLeaf =
				 * addFieldTypeToJsonObject((DbDataObject)
				 * typetoGet.getItems().get(i), jLeaf, false); jLeaf =
				 * prepareFormJsonCodeList1(typetoGet.getItems().get(i), jLeaf,
				 * svr); jLeaf.addProperty(Rc.TITLE,
				 * I18n.getText(getLocaleId(svr),
				 * typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString())
				 * + I18n.getText("form.field.second_check"));
				 * jFields.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE)
				 * .toString() + "_2ND", jLeaf); break; case "4": jLeaf =
				 * addFieldTypeToJsonObject((DbDataObject)
				 * typetoGet.getItems().get(i), jLeaf, false); jLeaf =
				 * prepareFormJsonCodeList1(typetoGet.getItems().get(i), jLeaf,
				 * svr); jLeaf.addProperty(Rc.TITLE,
				 * I18n.getText(getLocaleId(svr),
				 * typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString())
				 * );
				 * jFields.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE)
				 * .toString(), jLeaf);
				 * 
				 * jLeaf = new JsonObject(); jLeaf =
				 * addFieldTypeToJsonObject((DbDataObject)
				 * typetoGet.getItems().get(i), jLeaf, false); jLeaf =
				 * prepareFormJsonCodeList1(typetoGet.getItems().get(i), jLeaf,
				 * svr); jLeaf.addProperty(Rc.TITLE,
				 * I18n.getText(getLocaleId(svr),
				 * typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString())
				 * + I18n.getText("form.field.first_check"));
				 * jFields.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE)
				 * .toString() + "_1ST", jLeaf); jLeaf = new JsonObject(); jLeaf
				 * = addFieldTypeToJsonObject((DbDataObject)
				 * typetoGet.getItems().get(i), jLeaf, false); jLeaf =
				 * prepareFormJsonCodeList1(typetoGet.getItems().get(i), jLeaf,
				 * svr); jLeaf.addProperty(Rc.TITLE,
				 * I18n.getText(getLocaleId(svr),
				 * typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString())
				 * + I18n.getText("form.field.second_check"));
				 * jFields.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE)
				 * .toString() + "_2ND", jLeaf); break; default: } }
				 */

				jData.add(Rc.PROPERTIES, jFields);
				if (!listRequired.isEmpty()) {
					JsonElement element = gson.toJsonTree(listRequired, new TypeToken<List<String>>() {
					}.getType());
					if (element.isJsonArray())
						jData.add(Rc.REQUIRED, element);
				}
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jData.toString()).build();
	}

	/**
	 * Web service to get the schema for react UI , metadata is saved in
	 * VSVAROG_FORM_FIELD_TYPE field GUI_METADATA sub_object "react" ,
	 * sub_object "uischema", it will just read the full object as it is and add
	 * it to return string with the same field name to be paired to the object
	 * returned by getFormJSONSchema
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param form_name
	 *            String/Long form code SVAROG_FORM_TYPE.LABEL_CODE or object id
	 *            SVAROG_FORM_TYPE.OBJECT_ID that describes the document best
	 * 
	 * @return Json string with all fields on form that have UISchema in
	 *         GUI_METAFATA filed
	 * 
	 */
	@Path("/getFormUISchema/{sessionId}/{form_name}/{scenario}")
	@GET
	@Produces("application/json")
	public Response getFormUISchema(@PathParam("sessionId") String sessionId, @PathParam("form_name") String formName,
			@PathParam("scenario") Long scenario, @Context HttpServletRequest httpRequest) {
		JsonObject jsonData = new JsonObject();
		SvReader svr = null;
		DbDataArray typetoGet = null;
		DbDataObject formObject = null;
		Gson gson = new Gson();
		try {
			svr = new SvReader(sessionId);
			formObject = findFormObject(formName, svr);
			if (formObject != null) {
				DbDataObject dbLink = SvCore.getLinkType(Rc.FORM_FIELD_LINK, svCONST.OBJECT_TYPE_FORM_TYPE,
						svCONST.OBJECT_TYPE_FORM_FIELD_TYPE);
				typetoGet = svr.getObjectsByLinkedId(formObject.getObject_id(), svCONST.OBJECT_TYPE_FORM_TYPE, dbLink,
						svCONST.OBJECT_TYPE_FORM_FIELD_TYPE, false, null, 0, 0);
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		if (typetoGet != null && !typetoGet.getItems().isEmpty())
			for (int i = 0; i < typetoGet.getItems().size(); i++) {
				JsonObject jsonreactGUI = null;
				JsonObject jsonObj = null;
				JsonObject jsonUISchema = null;
				JsonObject jsonUISchemaFalseProp = null;
				JsonObject jsonUISchemaTrueProp = null;

				if (typetoGet.getItems().get(i).getVal(Rc.GUI_METADATA) != null)
					try {
						jsonObj = gson.fromJson(typetoGet.getItems().get(i).getVal(Rc.GUI_METADATA).toString(),
								JsonObject.class);
					} catch (JsonSyntaxException e) {
						// bad json syntax
					}

				if (jsonObj != null)
					jsonreactGUI = (JsonObject) jsonObj.get(Rc.REACT);
				if (jsonreactGUI != null && jsonreactGUI.has(Rc.UISCHEMA))
					try {
						jsonUISchema = gson.fromJson(jsonreactGUI.get(Rc.UISCHEMA).toString(), JsonObject.class);
						jsonUISchemaTrueProp = gson.fromJson(jsonreactGUI.get(Rc.UISCHEMA).toString(),
								JsonObject.class);
						jsonUISchemaFalseProp = gson.fromJson(jsonreactGUI.get(Rc.UISCHEMA).toString(),
								JsonObject.class);
					} catch (JsonSyntaxException e) {
						// bad json syntax
					}
				if (jsonUISchema != null && jsonUISchemaTrueProp != null && jsonUISchemaFalseProp != null) {
					jsonUISchemaTrueProp.addProperty("ui:readonly", true);
					jsonUISchemaFalseProp.addProperty("ui:readonly", false);
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString(), jsonUISchema);
				} else {
					jsonUISchema = new JsonObject();
					jsonUISchemaTrueProp = new JsonObject();
					jsonUISchemaTrueProp.addProperty("ui:readonly", true);
					jsonUISchemaFalseProp = new JsonObject();
					jsonUISchemaFalseProp.addProperty("ui:readonly", false);
				}
				/* scenarios based on user_group and app status f.r */
				switch (scenario.toString()) {
				case "1":
					if (jsonUISchema != null)
						jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString(), jsonUISchema);
					break;
				case "2":
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString(), jsonUISchemaTrueProp);
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString() + "_1ST",
							jsonUISchemaFalseProp);
					break;
				case "3":
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString(), jsonUISchemaTrueProp);
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString() + "_1ST",
							jsonUISchemaTrueProp);
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString() + "_2ND",
							jsonUISchemaFalseProp);
					break;
				case "4":
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString(), jsonUISchemaTrueProp);
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString() + "_1ST",
							jsonUISchemaTrueProp);
					jsonData.add(typetoGet.getItems().get(i).getVal(Rc.LABEL_CODE).toString() + "_2ND",
							jsonUISchemaTrueProp);
					break;
				default:
				}
			}
		return Response.status(200).entity(jsonData.toString()).build();
	}

	/**
	 * Web service to get the a form and all its data fields , also return
	 * object id, pkid, parent_id and object_type, the idea is: if object_id >0
	 * then this is old object so we just return the form, but if this object
	 * does not exist we may have to fill some default values in it that can be
	 * dynamic and dependent on the form
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param form_name
	 *            String/Long form code SVAROG_FORM_TYPE.LABEL_CODE or object id
	 *            SVAROG_FORM_TYPE.OBJECT_ID that describes the document best
	 * @param object_id
	 *            object_id of the form from SVAROG_FORM table
	 * 
	 * @return Json string with all fields on form
	 * 
	 */
	@Path("/getFormFormData/{sessionId}/{object_id}/{form_name}")
	@GET
	@Produces("application/json")
	public Response getFormFormData(@PathParam("sessionId") String sessionId, @PathParam("object_id") Long objectId,
			@PathParam("form_name") String formName, @Context HttpServletRequest httpRequest) {
		JsonObject jsonData1 = new JsonObject();
		SvReader svr = null;
		try {
			svr = new SvReader(sessionId);
			// find the form and if is not invalidated try to get all its fields
			DbDataObject formObject = svr.getObjectById(objectId, svCONST.OBJECT_TYPE_FORM, null);
			if (formObject != null) {

				// TODO add scenario for admin control
				jsonData1.addProperty(Rc.OBJECT_ID, formObject.getObject_id());
				jsonData1.addProperty(Rc.OBJECT_TYPE, formObject.getObject_type());
				jsonData1.addProperty(Rc.PKID, formObject.getPkid());
				jsonData1.addProperty(Rc.PARENT_ID, formObject.getParent_id());

				// get the link between form and fields so we can get all fields
				// for the form, get all forms for the type, but filter them by
				// object_id so we return only one ( there rill be one since
				// formObject != null)
				DbDataObject dbLink = SvCore.getLinkType(Rc.FORM_FIELD_LINK, svCONST.OBJECT_TYPE_FORM_TYPE,
						svCONST.OBJECT_TYPE_FORM_FIELD_TYPE);
				DbDataArray typetoGet = svr.getObjectsByLinkedId((Long) formObject.getVal("FORM_TYPE_ID"),
						svCONST.OBJECT_TYPE_FORM_TYPE, dbLink, svCONST.OBJECT_TYPE_FORM_FIELD_TYPE, false, null, 0, 0);

				DbSearch byObjectId = new DbSearchCriterion(Rc.OBJECT_ID, DbCompareOperand.EQUAL,
						formObject.getObject_id());
				DbDataArray formData = svr.getFormsByParentId(formObject.getParent_id(),
						(Long) formObject.getVal("FORM_TYPE_ID"), byObjectId, null);
				DbDataObject formObjectWithData = formData.getItems().get(0);
				// loop all the fields, get the field name and field type, and
				// prepare the data for return
				if (typetoGet != null && !typetoGet.getItems().isEmpty())
					for (DbDataObject tempFieldType : typetoGet.getItems())
						jsonData1 = addValueToJsonObjectForm1(jsonData1, formObjectWithData, tempFieldType);
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jsonData1.toString()).build();
	}

	/**
	 * Web service to save an object that was entered in a form
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String type name of the object that we are saving
	 * @param parent_id
	 *            Long the new object usually has parent , set to 0 if there is
	 *            no parent
	 * @param JsonString
	 *            String if formVals fails, we can use this Json string for
	 *            object
	 * @param formVals
	 *            MultivaluedMap pairs of key:value that we need to save (for
	 *            now not in use)
	 * 
	 * @return Json, simple text that object is saved
	 */
	@Path("/createTableRecord/{session_id}/{table_name}/{parent_id}/{JsonString}")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response createTableRecord(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("parent_id") Long parentId,
			@PathParam("JsonString") String jsonString, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) {
		return createTableRecordWithLink(sessionId, tableName, parentId, jsonString, -5L, "", "", null, formVals,
				httpRequest);
	}

	/**
	 * Web service to save an object that was entered in a form
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String type name of the object that we are saving
	 * @param parent_id
	 *            Long the new object usually has parent , set to 0 if there is
	 *            no parent
	 * @param JsonString
	 *            String if formVals fails, we can use this Json string for
	 *            object
	 * @param formVals
	 *            MultivaluedMap pairs of key:value that we need to save (for
	 *            now not in use)
	 * 
	 * @return Json, simple text that object is saved
	 */
	@Path("/createTableRecordFormData/{session_id}/{table_name}/{parent_id}")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response createTableRecordFormData(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("parent_id") Long parentId,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		return createTableRecordWithLink(sessionId, tableName, parentId, "", -5L, "", "", null, formVals, httpRequest);

	}

	/**
	 * Web service to save an object that was entered in a form and then create
	 * link for that object to another existing object, link type will be
	 * automatic determined from table names of the 2 objects
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param table_name
	 *            String type name of the object that we are saving
	 * @param parent_id
	 *            Long the new object usually has parent , set to 0 if there is
	 *            no parent
	 * @param JsonString
	 *            String if retrieving key and name fails, we can use this Json
	 *            string for them
	 * @param object_id_to_link
	 *            Long object Id of the second object that we want to link the
	 *            new created object
	 * @param table_name_to_link
	 *            String name of the table that we want to link out new created
	 *            object
	 * @param link_name
	 *            String name of the link we want to use
	 * @param link_note
	 *            String note
	 * @param formVals
	 *            MultivaluedMap pairs of key:value that we need to save (for
	 *            now not in use)
	 * 
	 * @return Json, simple text that object is saved
	 */
	@Path("/createTableRecord/{session_id}/{table_name}/{parent_id}/{JsonString}/{object_id_to_link}/{table_name_to_link}/{link_name}/{link_note}")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response createTableRecordWithLink(@PathParam("session_id") String sessionId,
			@PathParam("table_name") String tableName, @PathParam("parent_id") Long parentId,
			@PathParam("JsonString") String jsonString, @PathParam("object_id_to_link") Long objectIdToLink,
			@PathParam("table_name_to_link") String tableNameToLink, @PathParam("link_name") String linkName,
			@PathParam("link_note") String linkNote, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) {
		String jsonObjString = jsonString;
		jsonObjString = jsonObjString.replace(",P!_1-", "/");
		jsonObjString = jsonObjString.replace(",P!_2-", "\\");
		if (formVals != null)
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					jsonObjString = key;
				}
			}
		SvReader svr = null;
		SvWriter svw = null;
		SvGeometry svg = null;
		SvLink svl = null;
		DbDataArray vData = null;
		DbDataObject vdataObject = null;
		DbDataObject linkObject = new DbDataObject();
		Boolean isReverse = false;
		Long vtableTypeID = 0L;
		Long vlinkToTableTypeID = 0L;
		JsonObject jsonData = null;
		Gson gson = new Gson();
		try {
			// connect, translate names to IDs and prepare empty objects
			svr = new SvReader(sessionId);
			svw = new SvWriter(svr);
			svg = new SvGeometry(svr);
			svl = new SvLink(svr);
			svw.dbSetAutoCommit(false);
			jsonData = gson.fromJson(jsonObjString, JsonObject.class);
			// if we are editing, try to load the old object
			// try if object (record) already exist, then only change the
			// svarog_data , if object does not exist, just get type
			if (jsonData != null && jsonData.has(Rc.OBJECT_TYPE) && jsonData.has(Rc.PKID)) {
				vtableTypeID = jsonData.get(Rc.OBJECT_TYPE).getAsLong();
				vdataObject = svr.getObjectById(jsonData.get(Rc.OBJECT_ID).getAsLong(), vtableTypeID, null);
				vdataObject.setPkid(jsonData.get(Rc.PKID).getAsLong());
			} else
				vtableTypeID = findTableType(tableName);
			if (vdataObject == null)
				vdataObject = new DbDataObject();
			if (jsonData != null && jsonData.has(Rc.STATUS))
				vdataObject.setStatus(jsonData.get(Rc.STATUS).getAsString());
			vdataObject.setObject_type(vtableTypeID);
			vdataObject.setParent_id(parentId);
			// if typeId is from GEOM type, set GEOM_TYPE flag to true
			if (SvCore.hasGeometries(vtableTypeID)) {
				vdataObject.setGeometryType(true);
			}
			// loop all fields in the table, get data for all of them from
			// formData
			vData = svr.getObjectsByParentId(vtableTypeID, svCONST.OBJECT_TYPE_FIELD, null, 0, 0, Rc.SORT_ORDER);

			if (vdataObject.getObject_type().equals(svCONST.OBJECT_TYPE_MESSAGE)) {
				SvMessage svm = new SvMessage();
				DbDataObject conversationObj = svr.getObjectById(parentId, svCONST.OBJECT_TYPE_CONVERSATION, null);
				if (conversationObj == null)
					throw (new SvException("message.user.not.found", svr.getInstanceUser()));
				vdataObject = svm.saveMessage(svr, conversationObj, null, jsonData);
			} else
				for (int j = 0; j < vData.getItems().size(); j++) {
					String tmpFieldname = vData.getItems().get(j).getVal(Rc.FIELD_NAME).toString();
					if (processField(tmpFieldname)
							&& !"".equalsIgnoreCase(vData.getItems().get(j).getVal(Rc.FIELD_TYPE).toString())) {

						vdataObject = addValueToDataObject(vdataObject, tmpFieldname, vData.getItems().get(j),
								jsonData);
					}
				}
			JsonArray multiCoord = (JsonArray) jsonData.get(Rc.MULTYPOLYARRAY);
			if (multiCoord != null && multiCoord.size() > 0)
				try {
					SvGeometry.setGeometry(vdataObject, createGeomFromJsonPolygons(multiCoord));
				} catch (Exception e9) {
					if (log4j.isDebugEnabled())
						log4j.debug(e9);
					if (log4j.isDebugEnabled())
						log4j.debug("error generating geometry from coordinates");
				}
			JsonElement jPoly = jsonData.get(Rc.MULTIPOLYGEOMETRY);
			if (jPoly != null) {
				try {
					GeometryFactory gf = SvUtil.sdiFactory;
					GeoJsonReader jtsGeoReader = new GeoJsonReader(gf);
					Geometry polyGeom = jtsGeoReader.read(jPoly.toString());
					String polyType = polyGeom.getGeometryType();

					if ("Polygon".equalsIgnoreCase(polyType))
						polyGeom = gf.createMultiPolygon(new Polygon[] { (Polygon) polyGeom });

					SvGeometry.setGeometry(vdataObject, polyGeom);
				} catch (Exception e9) {
					if (log4j.isDebugEnabled())
						log4j.debug(e9);
					if (log4j.isDebugEnabled())
						log4j.debug("error generating geometry from coordinates");
				}

			}
			// we have to save the object so we have Id for the link ( if there
			// is one)
			if (!vdataObject.isGeometryType()) {
				svw.saveObject(vdataObject);
			} else {
				Long vdataType = vdataObject.getObject_type();
				// Handle land cover
				DbDataObject cover = SvCore.getDbtByName("SDI_COVER");
				if (cover != null && vdataType.equals(cover.getObject_id())) {
					Geometry coverGeom = getCoverGeometry(vdataObject, svr);
					if (coverGeom.getNumPoints() < 3) {
						throw new SvException("perun.main.sdi.parent_no_contain_polygon", svg.getInstanceUser());
					} else {
						SvGeometry.setGeometry(vdataObject, coverGeom);
					}
				}
				// Set geom by gps coordinates
				String gpsN = (String) vdataObject.getVal("GPS_NORTH");
				String gpsE = (String) vdataObject.getVal("GPS_EAST");

				if (gpsN != null && gpsE != null) {
					if (gpsN.equals("00°00'00''") || gpsE.equals("00°00'00''")) {
						vdataObject.setVal("GPS_NORTH", null);
						vdataObject.setVal("GPS_EAST", null);
					} else {
						setPointFromLatLng(svr, vdataObject);
					}
				}
				// Handle tiles and save
				// THIS PIECE OF CODE WILL BE MOVED IN SVAROG. Method Name:
				// SvGeometry.invalidateGeoCache(DbDataArray dba)
				/**/
				List<Geometry> tileGeomList = null;
				Geometry vdataGeom = SvGeometry.getGeometry(vdataObject);
				if (vdataGeom != null) {
					Envelope env = vdataGeom.getEnvelopeInternal();
					tileGeomList = SvGeometry.getTileGeomtries(env);
					for (Geometry tgl : tileGeomList) {
						String tileID = (String) tgl.getUserData();
						SvSDITile tile = SvGeometry.getTile(vdataObject.getObject_type(), tileID, null);
						tile.setIsTileDirty(true);
					}
				} else {
					svg.setAllowNullGeometry(true);
				}
				DbDataArray importArr = new DbDataArray();
				importArr.addDataItem(vdataObject);
				svg.saveGeometry(importArr);
				// Handle parcel
				DbDataObject parc = SvCore.getDbtByName("PARCEL");
				if (parc != null && tileGeomList != null && vdataType.equals(parc.getObject_id()))
					this.setParcelLinks(vdataObject, tileGeomList, svw);
			}
			// check if we have link and object_id in parameters, we better link
			// new object to the one passed in parameters
			if (objectIdToLink != 0 && !"".equals(linkName) && !"".equals(tableNameToLink)) {
				vlinkToTableTypeID = SvCore.getTypeIdByName(tableNameToLink);
				linkObject = SvCore.getLinkType(linkName, vlinkToTableTypeID, vtableTypeID);
				if (linkObject == null) {
					isReverse = true;
					linkObject = SvCore.getLinkType(linkName, vtableTypeID, vlinkToTableTypeID);
					if (linkObject == null) {
						svw.dbRollback();
						if (svw != null)
							svw.release();
						if (svr != null)
							svr.release();
						return Response.status(401).entity("rolback,  link not found 336 396 ").build();
					}
				}
				if (isReverse) {
					DbSearchExpression dbSearch = new DbSearchExpression();
					DbSearchCriterion crit1 = new DbSearchCriterion("LINK_TYPE_ID", DbCompareOperand.EQUAL,
							linkObject.getObject_id());
					crit1.setNextCritOperand(DbLogicOperand.AND.toString());
					DbSearchCriterion crit2 = new DbSearchCriterion("LINK_OBJ_ID_1", DbCompareOperand.EQUAL,
							vdataObject.getObject_id());
					crit2.setNextCritOperand(DbLogicOperand.AND.toString());
					DbSearchCriterion crit3 = new DbSearchCriterion("LINK_OBJ_ID_2", DbCompareOperand.EQUAL,
							objectIdToLink);
					dbSearch.addDbSearchItem(crit1);
					dbSearch.addDbSearchItem(crit2);
					dbSearch.addDbSearchItem(crit3);
					DbDataArray linkExist = svr.getObjects(dbSearch, svCONST.OBJECT_TYPE_LINK, null, 0, 0);
					if (linkExist == null || linkExist.getItems().isEmpty())
						svl.linkObjects(vdataObject.getObject_id(), objectIdToLink, linkObject.getObject_id(),
								linkNote);
				} else {
					DbSearchExpression dbSearch = new DbSearchExpression();
					DbSearchCriterion crit1 = new DbSearchCriterion("LINK_TYPE_ID", DbCompareOperand.EQUAL,
							linkObject.getObject_id());
					crit1.setNextCritOperand(DbLogicOperand.AND.toString());
					DbSearchCriterion crit2 = new DbSearchCriterion("LINK_OBJ_ID_1", DbCompareOperand.EQUAL,
							objectIdToLink);
					crit2.setNextCritOperand(DbLogicOperand.AND.toString());
					DbSearchCriterion crit3 = new DbSearchCriterion("LINK_OBJ_ID_2", DbCompareOperand.EQUAL,
							vdataObject.getObject_id());
					dbSearch.addDbSearchItem(crit1);
					dbSearch.addDbSearchItem(crit2);
					dbSearch.addDbSearchItem(crit3);
					DbDataArray linkExist = svr.getObjects(dbSearch, svCONST.OBJECT_TYPE_LINK, null, 0, 0);
					if (linkExist == null || linkExist.getItems().isEmpty())
						svl.linkObjects(objectIdToLink, vdataObject.getObject_id(), linkObject.getObject_id(),
								linkNote);
				}
				svl.dbCommit();
				if (svl != null)
					svl.release();
			}
			if (!vdataObject.isGeometryType())
				svw.dbCommit();
			else
				// already commited, svg.saveGeometry always autoCommits
				svg.dbCommit();
		} catch (SvException e) {
			log4j.debug(e);
			return Response.status(401).entity(e.getJsonMessage()).build();
		} catch (SQLException e) {
			log4j.error(e);
			debugException(e);
			return Response.status(401).entity(e).build();
		} finally {
			releaseAll(svr);
			releaseAll(svw);
			releaseAll(svg);
			releaseAll(svl);
		}
		return Response.status(200).entity(vdataObject.toSimpleJson().toString()).build();
	}

	/**
	 * Web service to create new Document / form and save all the values
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param parent_id
	 *            Long the new object usually has parent , must have parent ID,
	 *            never 0
	 * @param form_type
	 *            Long Id type of the document/form
	 * @param form_validation
	 *            String set to 1 if there should be extra validations on the
	 *            form before it can become valid
	 * @param value
	 *            Long probably validation of the form
	 * @param JsonString
	 *            String if formVals fails, we can use this Json string for
	 *            object, for now this will hold all the values for the fields
	 *            pairs: field name=value
	 * @param formVals
	 *            MultivaluedMap pairs of key:value that we need to save (for
	 *            now not in use)
	 * 
	 * @return Json, simple text that object is saved
	 */
	public Response createFormWithFields(@PathParam("session_id") String sessionId,
			@PathParam("parent_id") Long parentId, @PathParam("form_type") Long formType,
			@PathParam("form_validation") String formValidation, @PathParam(Rc.VALUE_LC) Long value,
			@PathParam("JsonString") String jsonString, MultivaluedMap<String, String> formVals,
			@Context HttpServletRequest httpRequest) {
		Boolean boolFormValidation = true;
		if ("1".equals(formValidation) || "true".equalsIgnoreCase(formValidation)) {
			boolFormValidation = true;
		}
		if ("0".equals(formValidation) || "false".equalsIgnoreCase(formValidation)) {
			boolFormValidation = false;
		}
		String jsonObjString = jsonString;
		jsonObjString = jsonObjString.replace(",P!_1-", "/");
		jsonObjString = jsonObjString.replace(",P!_2-", "\\");
		if (formVals != null)
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					jsonObjString = key;
				}
			}
		SvReader svr = null;
		SvWriter svw = null;
		DbDataObject linkObject = new DbDataObject();
		DbDataArray vFieldsArray = null;
		JsonObject jsonData = new JsonObject();
		Gson gson = new Gson();
		try {
			svr = new SvReader(sessionId);
			svw = new SvWriter(svr);
			svw.dbSetAutoCommit(false);
			// create the form
			DbDataObject vformObject = null;
			// if this is changing already existing form/document, try to load
			// the existing object
			jsonData = gson.fromJson(jsonObjString, JsonObject.class);
			// we try to find if the type is single document MULTI_ENTRY - false
			// , so we find it without need of object_id
			DbDataArray formObjectArray = null;
			DbDataObject formTypeObj = svr.getObjectById(formType, svCONST.OBJECT_TYPE_FORM_TYPE, null);
			// single_instance
			if (!((Boolean) formTypeObj.getVal("MULTI_ENTRY"))) {
				formObjectArray = svr.getFormsByParentId(parentId, formTypeObj.getObject_id(), null, null);
				if (formObjectArray != null && !formObjectArray.getItems().isEmpty()) {
					vformObject = formObjectArray.getItems().get(0);
					vformObject.setPkid(jsonData.get(Rc.PKID).getAsLong());
				}
			}
			if (vformObject == null && jsonData != null && jsonData.has(Rc.OBJECT_ID) && jsonData.has(Rc.OBJECT_TYPE)
					&& jsonData.has(Rc.PKID)) {
				vformObject = svr.getObjectById(jsonData.get(Rc.OBJECT_ID).getAsLong(),
						jsonData.get(Rc.OBJECT_TYPE).getAsLong(), null);
				vformObject.setPkid(jsonData.get(Rc.PKID).getAsLong());
			}
			// document does not exist, it is first save
			if (vformObject == null)
				vformObject = new DbDataObject();
			vformObject.setParent_id(parentId);
			vformObject.setObject_type(svCONST.OBJECT_TYPE_FORM);
			vformObject.setVal("FORM_TYPE_ID", formType);
			vformObject.setVal("FORM_VALIDATION", boolFormValidation);
			vformObject.setVal(Rc.VALUE, value);
			// create the fields, parent them to the form we just created
			// get them by link
			linkObject = SvCore.getLinkType(Rc.FORM_FIELD_LINK, svCONST.OBJECT_TYPE_FORM_TYPE,
					svCONST.OBJECT_TYPE_FORM_FIELD_TYPE);
			vFieldsArray = svr.getObjectsByLinkedId(formType, svCONST.OBJECT_TYPE_FORM_TYPE, linkObject,
					svCONST.OBJECT_TYPE_FORM_FIELD_TYPE, false, null, 0, 0);
			for (DbDataObject dbo : vFieldsArray.getItems()) {
				if (dbo.getVal(Rc.LABEL_CODE) != null) {
					String tmpFieldname = dbo.getVal(Rc.LABEL_CODE).toString();

					if (jsonData != null && jsonData.has(tmpFieldname)) {
						vformObject.setVal(tmpFieldname, jsonData.get(tmpFieldname).getAsString());
					} else {
						vformObject.setVal(tmpFieldname, null);
					}

					if (jsonData.has(tmpFieldname + "_1ST")) {
						vformObject.setVal(tmpFieldname + "_1ST", jsonData.get(tmpFieldname + "_1ST").getAsString());
					} else {
						vformObject.setVal(tmpFieldname + "_1ST", null);
					}

					if (jsonData.has(tmpFieldname + "_2ND")) {
						vformObject.setVal(tmpFieldname + "_2ND", jsonData.get(tmpFieldname + "_2ND").getAsString());
					} else {
						vformObject.setVal(tmpFieldname + "_2ND", null);
					}
				}
			}
			svw.saveObject(vformObject);
			svw.dbCommit();
			jsonData.addProperty(Rc.OBJECT_ID, vformObject.getObject_id());
			jsonData.addProperty(Rc.OBJECT_TYPE, vformObject.getObject_type());
			jsonData.addProperty(Rc.PKID, vformObject.getPkid());
			jsonData.addProperty(Rc.PARENT_ID, vformObject.getParent_id());
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			try {
				if (svw != null)
					svw.dbRollback();
			} catch (SvException e1) {
				log4j.error(e1);
			}
			return Response.status(401).entity(e.getJsonMessage()).build();
		} finally {
			releaseAll(svr);
			releaseAll(svw);
		}
		return Response.status(200).entity(jsonData.toString()).build();
	}

	/**
	 * Web service to return documents by parent
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * 
	 * 
	 * @return JsonArray with all objects found
	 */
	@Path("/getDocumentsByParentId/{session_id}/{parentId}/{formName}/{recordNumber}")
	@GET
	@Produces("application/json")
	public Response getDocumentsByParentId(@PathParam("session_id") String sessionId,
			@PathParam("parentId") Long parentId, @PathParam("formName") String formName,
			@PathParam("recordNumber") Integer recordNumber, @Context HttpServletRequest httpRequest) {
		String tableName = "SVAROG_FORM";
		SvReader svr = null;
		String retString = "";
		String[] tablesUsedArray = new String[1];
		Boolean[] tableShowArray = new Boolean[1];
		int tablesusedCount = 1;
		try {
			svr = new SvReader(sessionId);
			DbDataObject formObject = findFormObject(formName, svr);
			Long tableID = findTableType(tableName);
			if (formObject != null && tableID > 0) {
				DbSearchExpression expr = new DbSearchExpression();
				DbSearchCriterion critU = new DbSearchCriterion(Rc.PARENT_ID, DbCompareOperand.EQUAL, parentId);
				expr.addDbSearchItem(critU);
				expr.setNextCritOperand(DbLogicOperand.AND.toString());
				DbSearchCriterion crit1 = new DbSearchCriterion("FORM_TYPE_ID", DbCompareOperand.EQUAL,
						formObject.getObject_id());
				expr.addDbSearchItem(crit1);
				DbDataArray vData = svr.getObjects(expr, tableID, null, recordNumber, 0);
				tablesUsedArray[0] = getTableNameById(tableID, svr);
				tableShowArray[0] = true;
				retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service for edit/view bundle of forms/documents of the same type,
	 * this will generate the data that can be viewed and changed
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param parent_id
	 *            Long application ID that all forms are children of
	 * @param form_id
	 *            Long id of the form/document that we want to view/edit
	 * 
	 * @return Json Array of data with transposed fields for every form/document
	 */
	@Path("/getTransposedFormByParent/{session_id}/{parent_id}/{form_id}")
	@GET
	@Produces("application/json")
	public Response getTransposedFormByParent(@PathParam("session_id") String sessionId,
			@PathParam("parent_id") Long parentId, @PathParam("form_id") Long formId,
			@Context HttpServletRequest httpRequest) {
		DbDataArray vData = null;
		StringBuilder joinCritFromPrev = new StringBuilder();
		SvReader svr = null;
		String retString = "";
		try {
			joinCritFromPrev.append(" [   ");
			svr = new SvReader(sessionId);
			vData = svr.getFormsByParentId(parentId, formId, null, null);
			for (int i = 0; i < vData.getItems().size(); i++) {
				DbDataObject form = vData.getItems().get(i);
				JsonObject jsono = form.toJson();
				JsonElement mainobj = jsono.get("com.prtech.svarog_common.DbDataObject");
				JsonElement vals = (mainobj.getAsJsonObject()).get("values");
				JsonArray arvals = vals.getAsJsonArray();
				StringBuilder strBuild = new StringBuilder();
				for (int y = 0; y < arvals.size(); y++) {
					JsonObject jelly = (JsonObject) arvals.get(y);
					Set<Entry<String, JsonElement>> jles = jelly.entrySet();
					Iterator<Entry<String, JsonElement>> it = jles.iterator();
					while (it.hasNext()) {
						Entry<String, JsonElement> el = it.next();
						String key = el.getKey();
						JsonElement val = el.getValue();
						jelly.remove(key);
						jelly.add(key.toUpperCase(), val);
						strBuild.append(",");
						strBuild.append(jelly.toString());
					}
				}
				String retval = strBuild.substring(1, strBuild.length());
				String ymp = retval.replaceAll("\\{|\\}|\\[|\\]", "");
				joinCritFromPrev.append(" {");
				joinCritFromPrev.append(prapareSvarogData(form, "SVAROG_FORM"));
				joinCritFromPrev.append(ymp);
				joinCritFromPrev.append(" }, ");
			}
			joinCritFromPrev.delete(joinCritFromPrev.length() - 2, joinCritFromPrev.length());
			joinCritFromPrev.append(" ] ");
			retString = joinCritFromPrev.toString();
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service for edit/view bundle of forms/documents of the same type,
	 * this will generate the field list (top row) with captions for every
	 * column
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param form_id
	 *            Long id of the form/document that we want to view/edit
	 * @param scenario
	 *            Integer what fields we want to be visible/editable: 0 only
	 *            view the Values, no inline edit, 1 - Normal document edit
	 *            change of values, 2 - Administrative control values are
	 *            visible, administrative control are editable, 3 - Field
	 *            control, Values are visible, field values are editable, 4 All
	 *            values are read only
	 * 
	 * @return Json Array of data with transposed fields for every form/document
	 */
	@Path("/getTransposedFormByParentFieldList/{session_id}/{form_id}/{scenario}")
	@GET
	@Produces("application/json")
	public Response getTransposedFormByParentFieldList(@PathParam("session_id") String sessionId,
			@PathParam("form_id") Long formId, @PathParam("scenario") int scenario,
			@Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		JsonArray jArray = new JsonArray();
		try {
			svr = new SvReader(sessionId);
			DbDataObject dbLink = null;
			DbSearchExpression expr = new DbSearchExpression();
			DbSearchCriterion critM = new DbSearchCriterion(Rc.LINK_TYPE, DbCompareOperand.EQUAL, "FORM_FIELD_LINK");
			expr.addDbSearchItem(critM);
			DbDataArray vData = svr.getObjects(expr, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
			if (vData == null || vData.getItems().isEmpty())
				return Response.status(200).entity("LINK NOT FOUND IN DATABASE").build();
			else
				dbLink = vData.getItems().get(0);
			DbDataArray typetoGet = svr.getObjectsByLinkedId(formId, svCONST.OBJECT_TYPE_FORM_TYPE, dbLink,
					svCONST.OBJECT_TYPE_FORM_FIELD_TYPE, false, null, 0, 0);
			JsonArray tmpJArray = prapareSvarogFields1("SVAROG_FORM", svr);
			for (int i = 0; i < tmpJArray.size(); i++)
				jArray.add((JsonObject) tmpJArray.get(i));
			for (DbDataObject fldDesc : typetoGet.getSortedItems("SORT_ORDER")) {
				if (fldDesc != null) {
					JsonObject tmpObj = null;
					switch (scenario) {
					case 0:
						tmpObj = prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE), fldDesc, false, svr);
						if (tmpObj != null)
							jArray.add(tmpObj);
						break;
					case 1:
						tmpObj = prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE), fldDesc, true, svr);
						if (tmpObj != null)
							jArray.add(tmpObj);
						break;
					case 2:
						jArray.add(prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE), fldDesc, false, svr));
						jArray.add(
								prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE) + "_1st", fldDesc, true, svr));
						break;
					case 3:
						jArray.add(prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE), fldDesc, false, svr));
						jArray.add(
								prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE) + "_2nd", fldDesc, true, svr));
						break;
					case 4:
						jArray.add(prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE), fldDesc, false, svr));
						jArray.add(prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE) + "_1st", fldDesc, false,
								svr));
						jArray.add(prapareFormField1((String) fldDesc.getVal(Rc.LABEL_CODE) + "_2nd", fldDesc, false,
								svr));
						break;
					default:
					}
				}
			}
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jArray.toString()).build();
	}

	/**
	 * Web service to return all documents that are of type Yes/No drop-down for
	 * given support type (merka) and Form category
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param application_id
	 *            Long application for which there are documents of type yes/no
	 * @param support_type
	 *            Long support type for which yes/no documents are assigned
	 * 
	 * @return Json
	 */
	@Path("/getYesNoDocumentByParent/{session_id}/{application_id}/{support_type}")
	@GET
	@Produces("application/json")
	public Response getYesNoDocumentByParent(@PathParam("session_id") String sessionId,
			@PathParam("application_id") Long applicationId, @PathParam("support_type") Long supportType,
			@Context HttpServletRequest httpRequest) {
		DbDataArray vData = null;
		SvReader svr = null;
		String retString = "[ ]";
		String[] tablesUsedArray = new String[2];
		Boolean[] tableShowArray = new Boolean[2];
		int tablesusedCount = 2;
		try {
			svr = new SvReader(sessionId);
			tablesUsedArray[0] = ("SVAROG_FORM_TYPE");
			tablesUsedArray[1] = ("SVAROG_FORM");
			Arrays.fill(tableShowArray, Boolean.TRUE);
			vData = getYesNoDocuments(applicationId, supportType, svr);
			retString = prapareTableQueryData(vData, tablesUsedArray, tableShowArray, tablesusedCount, true, svr);
		} catch (SvException e) {
			debugSvException(e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * Web service
	 * 
	 * 
	 * @return Json
	 */
	@Path("/getYesNoDocumentFieldList/{session_id}/{support_type}")
	@GET
	@Produces("application/json")
	public Response getYesNoDocumentFieldList(@PathParam("session_id") String sessionId,
			@PathParam("support_type") Long supportType, @Context HttpServletRequest httpRequest) {
		String[] tablesUsedArray = new String[2];
		Arrays.fill(tablesUsedArray, null);
		Boolean[] tableShowArray = new Boolean[2];
		Boolean[] svarogShowArray = new Boolean[2];
		String tmpField;
		SvReader svr = null;
		DbSearchExpression expr = new DbSearchExpression();
		JsonArray jarr = new JsonArray();
		JsonArray jArray = new JsonArray();
		try {
			svr = new SvReader(sessionId);
			tablesUsedArray[0] = ("SVAROG_FORM_TYPE");
			tablesUsedArray[1] = ("SVAROG_FORM");
			Arrays.fill(tableShowArray, Boolean.TRUE);
			Arrays.fill(svarogShowArray, Boolean.FALSE);
			DbDataObject tableObject = null;
			Long vFieldsType = SvCore.getTypeIdByName("SVAROG_FIELDS", null);
			DbDataArray typetoGet = null;
			// loop the 2 tables
			for (int k = 0; k < tablesUsedArray.length; k++)
				if (tableShowArray[k]) {
					tableObject = SvCore.getDbtByName(tablesUsedArray[k]);
					typetoGet = svr.getObjectsByParentId(tableObject.getObject_id(), vFieldsType, null, 0, 0);
					if (svarogShowArray[k]) {
						JsonArray tmpJArray = prapareSvarogFields1(tablesUsedArray[k], svr);
						for (int i = 0; i < tmpJArray.size(); i++)
							jArray.add((JsonObject) tmpJArray.get(i));
					}
					// loop the fields of every table
					for (int i = 0; i < typetoGet.getItems().size(); i++) {
						tmpField = typetoGet.getItems().get(i).getVal(Rc.FIELD_NAME).toString();
						if (Rc.VALUE.equalsIgnoreCase(tmpField)) {
							JsonObject tmpJObject = prapareObjectField1(tablesUsedArray[k], typetoGet.getItems().get(i),
									svr);
							Long vCodesType = SvCore.getTypeIdByName("CODES", null);
							DbDataObject codeObject = prepareCodeObject(vCodesType, svr);
							if (codeObject != null) {
								JsonObject tmpJsonList = prepareJsonCodeList1(codeObject, 2, true, svr);
								for (Entry<String, JsonElement> entry : tmpJsonList.entrySet())
									tmpJObject.add(entry.getKey(), entry.getValue());
							}
							tmpJObject.addProperty("editable", true);
							jArray.add(tmpJObject);
						}
						if (Rc.LABEL_CODE.equals(tmpField)) {
							DbSearchCriterion critU = new DbSearchCriterion(Rc.PARENT_ID, DbCompareOperand.EQUAL,
									supportType);
							expr.addDbSearchItem(critU);
							DbDataArray vFormsArray = svr.getObjects(expr, svCONST.OBJECT_TYPE_FORM_TYPE, null, 0, 0);
							JsonObject tmpJObject = prapareObjectField1(tablesUsedArray[k], typetoGet.getItems().get(i),
									svr);
							for (int j = 0; j < vFormsArray.getItems().size(); j++) {
								JsonObject jData = new JsonObject();
								String tmpStringa = vFormsArray.getItems().get(j).getVal(Rc.LABEL_CODE).toString();
								jData.addProperty(Rc.ID, tmpStringa);
								jData.addProperty(Rc.VALUE_LC, tmpStringa);
								tmpStringa = I18n.getText(getLocaleId(svr), tmpStringa);
								jData.addProperty(Rc.TITLE, tmpStringa);
								jData.addProperty(Rc.TEXT, tmpStringa);
								jarr.add(jData);
							}
							tmpJObject.addProperty("formatterType", "DropDownFormatter");
							tmpJObject.add("formatterOptions", jarr);
							jArray.add(tmpJObject);
						}
					}
				}
		} catch (SvException e) {
			debugSvException(e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jArray.toString()).build();
	}

	/**
	 * Web service
	 * 
	 * @return Json
	 */
	@Path("/getYesNoDocFormJsonSchema/{session_id}/{support_type}/{scenario}")
	@GET
	@Produces("application/json")
	public Response getYesNoDocFormJsonSchema(@PathParam("session_id") String sessionId,
			@PathParam("support_type") Long supportType, @PathParam("scenario") Long scenario,
			@Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		JsonObject jData = new JsonObject();
		try {
			svr = new SvReader(sessionId);
			DbDataArray listYesNDoc = listOfYNDocForSupportType(supportType, svr);
			DbDataObject suppType = svr.getObjectById(supportType, SvCore.getTypeIdByName("SUPPORT_TYPE"), null);
			if (listYesNDoc != null && !listYesNDoc.getItems().isEmpty())
				jData = jsonYesNoDocFormJsonSchema(listYesNDoc, suppType, svr, scenario);
		} catch (SvException e) {
			debugSvException(e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jData.toString()).build();
	}

	public JsonObject jsonYesNoDocFormJsonSchema(DbDataArray listYesNDoc, DbDataObject suppType, SvReader svr,
			Long scenario) throws SvException {
		JsonObject jData = new JsonObject();
		CodeList cl = null;

		if (svr != null)
			try {
				cl = new CodeList(svr);
				Long linkTypeId = SvCore.getTypeIdByName("SVAROG_CODES");
				DbSearchCriterion critM = new DbSearchCriterion("CODE_VALUE", DbCompareOperand.EQUAL,
						"NUMERIC_YES_NO_WITHOUT_CHOOSE");
				DbDataArray vData = svr.getObjects(critM, linkTypeId, null, 0, 0);
				if (vData != null && !vData.getItems().isEmpty()) {
					cl = new CodeList(svr);
					HashMap<String, String> listMap;
					listMap = cl.getCodeList(SvConf.getDefaultLocale(), vData.get(0).getObject_id(), true);
					Iterator<Entry<String, String>> it = listMap.entrySet().iterator();
					StringBuilder strBtmp = new StringBuilder("[");
					StringBuilder strBtmp1 = new StringBuilder("[");
					String prefix = "";
					while (it.hasNext()) {
						Entry<String, String> pair = it.next();
						strBtmp.append(prefix);
						strBtmp1.append(prefix);
						strBtmp.append(pair.getKey());
						strBtmp1.append(pair.getValue());
						prefix = ",";
						it.remove();
					}
					strBtmp.append(']');
					strBtmp1.append(']');
					Gson gson = new Gson();
					JsonArray enumValues = gson.fromJson(strBtmp.toString(), JsonArray.class);
					JsonArray enumNames = gson.fromJson(strBtmp1.toString(), JsonArray.class);
					if (suppType != null)
						jData.addProperty(Rc.TITLE,
								suppType.getVal("SUPPORT_CODE") + " - " + I18n.getText("perun.sidemenu.apendix"));
					else
						jData.addProperty(Rc.TITLE, I18n.getText("perun.sidemenu.completness.list"));
					jData.addProperty("type", "object");

					JsonObject propertiesObject = new JsonObject();
					for (DbDataObject tempObject : listYesNDoc.getItems())
						if (tempObject.getVal(Rc.LABEL_CODE) != null) {
							JsonObject itemObject = new JsonObject();
							itemObject.addProperty("type", "number");
							// some labels dont have long descriptions, so get
							// short one instead
							String finalLabel = "";
							String fCheckLabel = I18n.getText("form.field.first_check");
							String finalField = "";
							String tmpLabel = I18n.getLongText(tempObject.getVal(Rc.LABEL_CODE).toString());
							if (tmpLabel == null || "".equals(tmpLabel))
								tmpLabel = I18n.getText(tempObject.getVal(Rc.LABEL_CODE).toString());
							/*
							 * scenarios based on user_group and app status f.r
							 */
							switch (scenario.toString()) {
							case "1":
								finalLabel = tmpLabel;
								finalField = tempObject.getVal(Rc.LABEL_CODE).toString();
								itemObject.addProperty(Rc.TITLE, finalLabel);
								itemObject.add("enum", enumValues);
								itemObject.add("enumNames", enumNames);
								propertiesObject.add(finalField, itemObject);
								break;
							case "2":
							case "3":
								finalLabel = tmpLabel;
								finalField = tempObject.getVal(Rc.LABEL_CODE).toString();
								itemObject.addProperty(Rc.TITLE, finalLabel);
								itemObject.add("enum", enumValues);
								itemObject.add("enumNames", enumNames);
								propertiesObject.add(finalField, itemObject);

								itemObject = new JsonObject();
								itemObject.addProperty("type", "number");
								finalLabel = tmpLabel + fCheckLabel;
								finalField = tempObject.getVal(Rc.LABEL_CODE).toString() + "_1ST";
								itemObject.addProperty(Rc.TITLE, finalLabel);
								itemObject.add("enum", enumValues);
								itemObject.add("enumNames", enumNames);
								propertiesObject.add(finalField, itemObject);
								break;
							default:
							}
						}
					jData.add("properties", propertiesObject);
				}
			} finally {
				releaseAll(cl);
			}
		return jData;
	}

	/**
	 * Web service
	 * 
	 * @return Json
	 */
	@Path("/getYesNoDocFormUiSchema/{session_id}/{support_type}/{scenario}")
	@GET
	@Produces("application/json")
	public Response getYesNoDocFormUiSchema(@PathParam("session_id") String sessionId,
			@PathParam("support_type") Long supportType, @PathParam("scenario") Long scenario,
			@Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		JsonObject jData = new JsonObject();
		try {
			svr = new SvReader(sessionId);
			DbDataArray ynDocBySuppType = listOfYNDocForSupportType(supportType, svr);
			jData = jsonYesNoDocFormUiSchema(ynDocBySuppType, scenario);
		} catch (JsonSyntaxException e) {
			debugException(e);
			return Response.status(401).entity(e.getMessage()).build();
		} catch (SvException e) {
			debugSvException(e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jData.toString()).build();
	}

	public JsonObject jsonYesNoDocFormUiSchema(DbDataArray ynDoc, Long scenario) {
		JsonObject jData = new JsonObject();
		Gson gson = new Gson();
		if (ynDoc != null && !ynDoc.getItems().isEmpty()) {
			for (DbDataObject ynDocFormType : ynDoc.getItems()) {
				JsonObject jsonreactGUI = null;
				JsonObject jsonObj = null;
				JsonObject jsonUISchema = null;
				if (ynDocFormType.getVal(Rc.GUI_METADATA) != null)
					jsonObj = gson.fromJson(ynDocFormType.getVal(Rc.GUI_METADATA).toString(), JsonObject.class);
				if (jsonObj != null && jsonObj.has(Rc.REACT))
					jsonreactGUI = (JsonObject) jsonObj.get(Rc.REACT);
				if (jsonreactGUI != null && jsonreactGUI.has(Rc.UISCHEMA))
					jsonUISchema = (JsonObject) jsonreactGUI.get(Rc.UISCHEMA);
				if (jsonUISchema == null)
					jsonUISchema = new JsonObject();
				/// scenarios based on user_group and app status f.r
				switch (scenario.toString()) {
				case "1":
					jsonUISchema.addProperty("ui:readonly", false);
					jData.add(ynDocFormType.getVal(Rc.LABEL_CODE).toString(), jsonUISchema);
					break;
				case "2":
					jsonUISchema.addProperty("ui:readonly", true);
					jData.add(ynDocFormType.getVal(Rc.LABEL_CODE).toString(), jsonUISchema);
					break;
				case "3":
					jsonUISchema.addProperty("ui:readonly", true);
					jData.add(ynDocFormType.getVal(Rc.LABEL_CODE).toString(), jsonUISchema);
					jData.add(ynDocFormType.getVal(Rc.LABEL_CODE).toString() + "_1ST", jsonUISchema);
					break;
				default:
				}
			}
		}
		return jData;
	}

	/**
	 * Web service
	 * 
	 * @return Json
	 */
	@Path("/getYesNoDocFormData/{session_id}/{application_id}/{support_type}")
	@GET
	@Produces("application/json")
	public Response getYesNoDocFormData(@PathParam("session_id") String sessionId,
			@PathParam("application_id") Long applicationId, @PathParam("support_type") Long supportType,
			@Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		JsonObject jData = new JsonObject();
		try {
			svr = new SvReader(sessionId);
			DbDataArray listYesNDocForApp = listOfYNDocForApp(applicationId, svr);
			IDbFilter filter = new FilterBySupportCode(supportType);
			DbDataArray filteredListYesNDocForApp = listYesNDocForApp.applyFilter(filter);
			jData = jsonYesNoDocFormData(filteredListYesNDocForApp);
		} catch (SvException e) {
			debugSvException(e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(jData.toString()).build();
	}

	public JsonObject jsonYesNoDocFormData(DbDataArray filteredListYesNDocForApp) {
		JsonObject jData = new JsonObject();
		if (filteredListYesNDocForApp != null && !filteredListYesNDocForApp.getItems().isEmpty())
			for (DbDataObject tempObject : filteredListYesNDocForApp.getItems()) {
				if (tempObject.getVal("SF_VALUE") != null)
					jData.addProperty(tempObject.getVal("SFT_LABEL_CODE").toString(),
							(Long) tempObject.getVal("SF_VALUE"));
				if (tempObject.getVal("SF_FIRST_CHECK") != null)
					jData.addProperty(tempObject.getVal("SFT_LABEL_CODE").toString() + "_1ST",
							(Long) tempObject.getVal("SF_FIRST_CHECK"));
			}
		return jData;
	}

	/**
	 * Web service
	 * 
	 * 
	 * @return Json, simple text that object is saved
	 */
	@Path("/createYesNoDocFormSave/{session_id}/{application_id}/{measure_id}/{form_validation}/{JsonString}")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response createYesNoDocFormSave(@PathParam("session_id") String sessionId,
			@PathParam("application_id") Long applicationId, @PathParam("measure_id") Long measureId,
			@PathParam("form_validation") Boolean formValidation, @PathParam("JsonString") String jsonString,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		String jsonObjString = jsonString;
		jsonObjString = jsonObjString.replace(",P!_1-", "/");
		jsonObjString = jsonObjString.replace(",P!_2-", "\\");
		if (formVals != null)
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					jsonObjString = key;
				}
			}
		JsonObject jsonDocumentList = new JsonObject();
		SvReader svr = null;
		SvWriter svw = null;
		JsonObject vdataObject = new JsonObject();
		Gson gson = new Gson();
		try {
			svr = new SvReader(sessionId);
			svw = new SvWriter(svr);
			svw.dbSetAutoCommit(false);
			DbDataArray listToSave = new DbDataArray();
			if (jsonObjString != null)
				jsonDocumentList = gson.fromJson(jsonString, JsonObject.class);
			// get the list of all yes/no documents for the support type and all
			// created yes/no documents for the application, filter only
			// documents for the required support type
			DbDataArray yesNoBySupportType = listOfYNDocForSupportType(measureId, svr);
			DbDataArray listYesNDocForApp = listOfYNDocForApp(applicationId, svr);
			IDbFilter filter = new FilterBySupportCode(measureId);
			DbDataArray filteredListYesNDocForApp = listYesNDocForApp.applyFilter(filter);
			if (yesNoBySupportType != null && !yesNoBySupportType.getItems().isEmpty())
				for (DbDataObject ynDocFormType : yesNoBySupportType.getItems()) {
					// loop all document types for the support type, check if
					// there is document for it, get it, replace the values if
					// needed and save the document
					String labelCode = ynDocFormType.getVal(Rc.LABEL_CODE).toString();
					IDbFilter filterByLabelCode = new FilterByDocumentName(labelCode);
					DbDataArray lastFilter = filteredListYesNDocForApp.applyFilter(filterByLabelCode);
					DbDataObject tempDBo = new DbDataObject();
					if (lastFilter != null && !lastFilter.getItems().isEmpty()) {
						tempDBo = svr.getObjectById((Long) lastFilter.getItems().get(0).getVal("SF_OBJECT_ID"),
								(Long) lastFilter.getItems().get(0).getVal("SF_OBJECT_TYPE"), null);
					} else {
						tempDBo.setParent_id(applicationId);
						tempDBo.setObject_type(svCONST.OBJECT_TYPE_FORM);
						tempDBo.setVal("FORM_TYPE_ID", ynDocFormType.getObject_id());
						tempDBo.setVal("FORM_VALIDATION", formValidation);
					}
					// if there is value in the form data, replace it, if there
					// is not, set it null
					if (jsonDocumentList.has(labelCode))
						tempDBo.setVal(Rc.VALUE, jsonDocumentList.get(labelCode).getAsLong());
					else
						tempDBo.setVal(Rc.VALUE, null);
					if (jsonDocumentList.has(labelCode + "_1ST"))
						tempDBo.setVal("FIRST_CHECK", jsonDocumentList.get(labelCode + "_1ST").getAsLong());
					else
						tempDBo.setVal("FIRST_CHECK", null);
					listToSave.addDataItem(tempDBo);
				}
			if (listToSave != null && !listToSave.getItems().isEmpty()) {
				svw.saveObject(listToSave, false);
				svw.dbCommit();
			}
		} catch (SvException e) {
			log4j.debug(e);
			return Response.status(401).entity(e.getJsonMessage()).build();
		} finally {
			releaseAll(svr);
			releaseAll(svw);
		}
		return Response.status(200).entity(vdataObject.toString()).build();
	}

	@Path("/linkObjects/{session_id}/{objectId1}/{tableName1}/{objectId2}/{tableName2}/{linkName}")
	@GET
	@Produces("application/json")
	public Response linkObjects(@PathParam("session_id") String sessionId, @PathParam("objectId1") Long objectId1,
			@PathParam("tableName1") String tableName1, @PathParam("objectId2") Long objectId2,
			@PathParam("tableName2") String tableName2, @PathParam("linkName") String linkName,
			@Context HttpServletRequest httpRequest) {

		return linkObjects(sessionId, objectId1, tableName1, objectId2, tableName2, linkName, null, httpRequest);
	}

	/**
	 * method to create link between 2 objects, for now it will not check if
	 * link already exist
	 * 
	 * @param sessionId
	 *            String Session ID (SID) of the web communication between
	 *            browser and web server
	 * @param objectId1
	 *            Long , ObjectId of the object that we want to link to
	 * @param tableName1
	 *            Long , Object Type of the object we want to link to
	 * @param objectId2
	 *            Long , ObjectId of the object that we like to link to the
	 *            first object
	 * @param tableName2
	 *            Long , Object Type of the object that we are linking
	 * @param linkName
	 *            String name of the link
	 * @param linkNote
	 *            String note that we want to be set on the link between objects
	 * @param httpRequest
	 * @return
	 */
	@Path("/linkObjects/{session_id}/{objectId1}/{tableName1}/{objectId2}/{tableName2}/{linkName}/{linkNote}")
	@GET
	@Produces("application/json")
	public Response linkObjects(@PathParam("session_id") String sessionId, @PathParam("objectId1") Long objectId1,
			@PathParam("tableName1") String tableName1, @PathParam("objectId2") Long objectId2,
			@PathParam("tableName2") String tableName2, @PathParam("linkName") String linkName,
			@PathParam("linkNote") String linkNote, @Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		SvLink svl = null;
		String retString = "link not created";
		DbDataObject obj1 = null;
		DbDataObject obj2 = null;
		try {
			svr = new SvReader(sessionId);
			Long tableId1 = findTableType(tableName1);
			Long tableId2 = findTableType(tableName2);
			DbDataObject dbLink = null;
			if (tableId1.compareTo(0L) > 0 && tableId2.compareTo(0L) > 0 && objectId1.compareTo(0L) > 0
					&& objectId2.compareTo(0L) > 0) {
				obj1 = svr.getObjectById(objectId1, tableId1, null);
				obj2 = svr.getObjectById(objectId2, tableId2, null);
				if (obj1 != null && obj2 != null)
					dbLink = findLink(getTableNameById(tableId1, svr), linkName, getTableNameById(tableId2, svr), svr);
				else
					throw new SvException("object not found", null);
			}
			if (dbLink != null) {
				svl = new SvLink(svr);
				// check for saving reversed link implementations
				if (dbLink.getVal(Rc.LINK_OBJECT_TYPE1).equals(tableId2)) {
					svl.linkObjects(obj2, obj1, linkName, linkNote, false, true);
				} // if not reversed, try the standard one
				else {
					svl.linkObjects(obj1, obj2, linkName, linkNote, false, true);
				}
				retString = "link created";
			} else
				throw new SvException("link not found", null);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
			releaseAll(svl);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * method to delete (invalidate) link between 2 objects
	 * 
	 * @param sessionId
	 *            String Session ID (SID) of the web communication between
	 *            browser and web server
	 * @param objectId1
	 *            Long , ObjectId of the object that we want to link to
	 * @param tableName1
	 *            Long , Object Type of the object we want to link to
	 * @param objectId2
	 *            Long , ObjectId of the object that we like to link to the
	 *            first object
	 * @param tableName2
	 *            Long , Object Type of the object that we are linking
	 * @param linkName
	 *            String name of the link
	 * @param httpRequest
	 * @return
	 */
	@Path("/dropLink/{session_id}/{objectId1}/{tableName1}/{objectId2}/{tableName2}/{linkName}")
	@GET
	@Produces("application/json")
	public Response dropLink(@PathParam("session_id") String sessionId, @PathParam("objectId1") Long objectId1,
			@PathParam("tableName1") String tableName1, @PathParam("objectId2") Long objectId2,
			@PathParam("tableName2") String tableName2, @PathParam("linkName") String linkName,
			@Context HttpServletRequest httpRequest) {
		Boolean isReverse = false;
		SvReader svr = null;
		SvWriter svw = null;
		String retString = "";
		DbDataObject obj1 = null;
		DbDataObject obj2 = null;
		DbDataObject linkObject = null;
		try {
			svr = new SvReader(sessionId);
			Long tableId1 = findTableType(tableName1);
			Long tableId2 = findTableType(tableName2);
			DbDataObject dbLink = null;
			if (tableId1.compareTo(0L) > 0 && tableId2.compareTo(0L) > 0 && objectId1.compareTo(0L) > 0
					&& objectId2.compareTo(0L) > 0) {
				obj1 = svr.getObjectById(objectId1, tableId1, null);
				obj2 = svr.getObjectById(objectId2, tableId2, null);
				if (obj1 != null && obj2 != null)
					dbLink = findLink(getTableNameById(tableId1, svr), linkName, svr);
				else
					throw new SvException("object not found", null);
			}
			if (dbLink != null) {

				linkObject = SvCore.getLinkType(linkName, tableId1, tableId2);
				if (linkObject == null) {
					isReverse = true;
					linkObject = SvCore.getLinkType(linkName, tableId2, tableId1);
				}
				if (linkObject != null) {
					DbDataArray linkExist = null;
					DbSearchExpression dbSearch = new DbSearchExpression();
					DbSearchCriterion crit1 = new DbSearchCriterion("LINK_TYPE_ID", DbCompareOperand.EQUAL,
							linkObject.getObject_id());
					crit1.setNextCritOperand(DbLogicOperand.AND.toString());
					dbSearch.addDbSearchItem(crit1);
					if (isReverse) {
						DbSearchCriterion crit2 = new DbSearchCriterion("LINK_OBJ_ID_1", DbCompareOperand.EQUAL,
								objectId2);
						crit2.setNextCritOperand(DbLogicOperand.AND.toString());
						DbSearchCriterion crit3 = new DbSearchCriterion("LINK_OBJ_ID_2", DbCompareOperand.EQUAL,
								objectId1);
						dbSearch.addDbSearchItem(crit2);
						dbSearch.addDbSearchItem(crit3);
					} else {
						DbSearchCriterion crit2 = new DbSearchCriterion("LINK_OBJ_ID_1", DbCompareOperand.EQUAL,
								objectId1);
						crit2.setNextCritOperand(DbLogicOperand.AND.toString());
						DbSearchCriterion crit3 = new DbSearchCriterion("LINK_OBJ_ID_2", DbCompareOperand.EQUAL,
								objectId2);
						dbSearch.addDbSearchItem(crit2);
						dbSearch.addDbSearchItem(crit3);
					}
					linkExist = svr.getObjects(dbSearch, svCONST.OBJECT_TYPE_LINK, null, 0, 0);
					if (linkExist != null && !linkExist.getItems().isEmpty()) {
						svw = new SvWriter(svr);
						DbDataObject linkIt = linkExist.getItems().get(0);
						svw.deleteObject(linkIt);
						svw.dbCommit();
						retString = "link is now invalidated";
					}
				} else
					return Response.status(401).entity("rolback,  link not found 336 396 ").build();

			} else
				throw new SvException("link type not found", null);
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
			releaseAll(svw);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * method to get the configuration when displaying header for conversation
	 * object to be displayed in detail
	 * 
	 * @param sessionId
	 *            String Session ID (SID) of the web communication between
	 *            browser and web server
	 * @param objectId1
	 *            Long , ObjectId of the conversation
	 * @param httpRequest
	 * @return
	 */
	@Path("/getConversationHeader/{session_id}/{objectId1}")
	@GET
	@Produces("application/json")
	public Response getConversationHeader(@PathParam("session_id") String sessionId,
			@PathParam("objectId1") Long objectId1, @Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		String retString = "{}";
		try {
			DbDataObject conversationObject = null;
			svr = new SvReader(sessionId);
			if (!objectId1.equals(0L))
				conversationObject = svr.getObjectById(objectId1,
						SvCore.getDbtByName("SVAROG_CONVERSATION").getObject_id(), null);
			retString = prepareConversationHeader(conversationObject, svr).toString();
		} catch (SvException e) {
			log4j.error(e.getFormattedMessage(), e);
			return Response.status(401).entity(e.getFormattedMessage()).build();
		} finally {
			releaseAll(svr);
		}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * method to get the data for the header of the conversation
	 * 
	 * @param sessionId
	 *            String Session ID (SID) of the web communication between
	 *            browser and web server
	 * @param objectId1
	 *            Long , ObjectId of the conversation
	 * @param httpRequest
	 * @return
	 */
	@Path("/getConversationData/{session_id}/{objectId1}")
	@GET
	@Produces("application/json")
	public Response getConversationData(@PathParam("session_id") String sessionId,
			@PathParam("objectId1") Long objectId1, @Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		SvLink svl = null;
		String retString = "{}";
		if (objectId1 != null)
			try {
				svr = new SvReader(sessionId);
				DbDataObject conversationObject = svr.getObjectById(objectId1,
						SvCore.getDbtByName("SVAROG_CONVERSATION").getObject_id(), null);
				retString = prepareConversationData(svr, conversationObject).toString();
			} catch (SvException e) {
				log4j.error(e.getFormattedMessage(), e);
				return Response.status(401).entity(e.getFormattedMessage()).build();
			} finally {
				releaseAll(svr);
				releaseAll(svl);
			}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * method to get the data for the header of the conversation
	 * 
	 * @param sessionId
	 *            String Session ID (SID) of the web communication between
	 *            browser and web server
	 * @param convType
	 *            String what conversations we want to see: assigned to
	 *            user_name, created by user_name, or all those that have
	 *            user_name message in it: MY_CREATED, ASSIGNED_TO_ME,
	 *            WITH_MY_MESSAGE
	 * @param user_name
	 *            String search conversations for the user, only admin can
	 *            search other people conversation, users can read only their
	 *            own
	 * @param is_read
	 *            String show all or only unread conversations true: ALL, false:
	 *            UNREAD
	 * @param httpRequest
	 * @return
	 */
	@Path("/getConversationGridData/{session_id}/{convType}/{user_name}/{is_read}")
	@GET
	@Produces("application/json")
	public Response getConversationGridData(@PathParam("session_id") String sessionId,
			@PathParam("convType") String convType, @PathParam("user_name") String userName,
			@PathParam("is_read") String isUneadS, @Context HttpServletRequest httpRequest) {
		boolean isUnread = false;
		if (isUneadS != null && (isUneadS.equalsIgnoreCase("y") || isUneadS.equalsIgnoreCase("yes")
				|| isUneadS.equalsIgnoreCase("true") || isUneadS.equalsIgnoreCase("t")
				|| isUneadS.equalsIgnoreCase("1")))
			isUnread = true;
		SvReader svr = null;
		String retString = "[ ]";
		DbDataArray conversations = new DbDataArray();
		String[] tablesUsedArray = new String[2];
		Boolean[] tableShowArray = new Boolean[2];
		int tablesusedCount = 1;
		tableShowArray[0] = true;
		tableShowArray[1] = false;
		tablesUsedArray[0] = ("SVAROG_CONVERSATION");
		tablesUsedArray[1] = ("SVAROG_MESSAGE");
		if (convType != null)
			try {
				svr = new SvReader(sessionId);
				conversations = getConversationGridData(svr, convType, userName, isUnread);
				if (convType.toUpperCase().equalsIgnoreCase("WITH_MY_MESSAGE")) {
					tablesusedCount = 2;
				}
				retString = prapareTableQueryData(conversations, tablesUsedArray, tableShowArray, tablesusedCount, true,
						svr);
			} catch (SvException e) {
				log4j.error(e.getFormattedMessage(), e);
				return Response.status(401).entity(e.getFormattedMessage()).build();
			} finally {
				releaseAll(svr);
			}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * method to get the messages that are part of some conversation
	 * 
	 * @param sessionId
	 *            String Session ID (SID) of the web communication between
	 *            browser and web server
	 * @param objectId1
	 *            Long , ObjectId of the conversation
	 * @param httpRequest
	 * @return
	 */
	@Path("/getMessagesForConversation/{session_id}/{objectId1}")
	@GET
	@Produces("application/json")
	public Response getMessagesForConversation(@PathParam("session_id") String sessionId,
			@PathParam("objectId1") Long objectId1, @Context HttpServletRequest httpRequest) {
		SvReader svr = null;
		String retString = "{}";
		if (objectId1 != null)
			try {
				svr = new SvReader(sessionId);
				DbDataObject conversationObject = svr.getObjectById(objectId1,
						SvCore.getDbtByName("SVAROG_CONVERSATION").getObject_id(), null);
				retString = processListOfMessages(svr, conversationObject).toString();
			} catch (SvException e) {
				log4j.error(e.getFormattedMessage(), e);
				return Response.status(401).entity(e.getFormattedMessage()).build();
			} finally {
				releaseAll(svr);
			}
		return Response.status(200).entity(retString).build();
	}

	/**
	 * 
	 * @param svr
	 *            SvReader connected to database
	 * @param jsonData
	 *            JsonObject for the conversation that we want to create or
	 *            update
	 * @return
	 * @throws SvException
	 */
	private Boolean canIChangeConversation(SvReader svr, JsonObject jsonData) throws SvException {
		Boolean retVal = false;
		if (!jsonData.has(Rc.OBJECT_ID))
			retVal = true;
		else {

			DbDataObject objConv = svr.getObjectById(jsonData.get(Rc.OBJECT_ID).getAsLong(),
					SvCore.getDbtByName("SVAROG_CONVERSATION").getObject_id(), null);
			if (objConv.getVal("ASSIGNED_TO_USERNAME") != null) {

				String assignedTo = objConv.getVal("ASSIGNED_TO_USERNAME").toString();
				if (svr.isAdmin() || assignedTo.equalsIgnoreCase(svr.getInstanceUser().getVal("USER_NAME").toString()))
					retVal = true;
				else {
					DbDataArray vData = svr.getUserGroups();
					if (vData != null && !vData.getItems().isEmpty())
						for (DbDataObject groupObject : vData.getItems())
							if (assignedTo.equalsIgnoreCase(groupObject.getVal("GROUP_NAME").toString()))
								retVal = true;
				}
			}
		}
		return retVal;
	}

	/**
	 * Web service to create new conversation or tracker
	 * 
	 * @param sessionId
	 *            Session ID (SID) of the web communication between browser and
	 *            web server
	 * @param parent_id
	 *            Long the new object usually has parent , set to 0 if there is
	 *            no parent
	 * @param formVals
	 *            MultivaluedMap pairs of key:value that we need to save (for
	 *            now not in use)
	 * 
	 * @return Json, simple text that object is saved
	 */
	@Path("/createConversation/{session_id}/{parent_id}")
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces("application/json")
	public Response createConversation(@PathParam("session_id") String sessionId, @PathParam("parent_id") Long parentId,
			MultivaluedMap<String, String> formVals, @Context HttpServletRequest httpRequest) {
		String jsonObjString = "";
		SvReader svr = null;
		SvWriter svw = null;
		Response res = Response.status(401).entity("system.error.cannot_create").build();
		DbDataObject conversationObject = new DbDataObject();
		if (formVals != null)
			for (Entry<String, List<String>> entry : formVals.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					String key = entry.getKey();
					jsonObjString = key;
				}
			}
		if (!"".equals(jsonObjString))
			try {
				Gson gson = new Gson();
				JsonObject jOb = gson.fromJson(jsonObjString, JsonObject.class);
				if (jOb.has("ASSIGNED_TO_USERNAME") && jOb.get("ASSIGNED_TO_USERNAME") != null
						&& !"".equals(jOb.get("ASSIGNED_TO_USERNAME").getAsString())) {
					svr = new SvReader(sessionId);
					// make sure you can change conversation info only ig its
					// assigned to you, your group, or you are admin
					SvConversation conv = new SvConversation();
					if (canIChangeConversation(svr, jOb)) {
						conversationObject = conv.newConversation(svr, jOb);
						svw = new SvWriter(svr);
						svw.saveObject(conversationObject);
						svw.dbCommit();
						conversationObject = svr.getObjectById(conversationObject.getObject_id(),
								conversationObject.getObject_type(), null);
					} else {
						conversationObject = svr.getObjectById(jOb.get(Rc.OBJECT_ID).getAsLong(),
								svCONST.OBJECT_TYPE_CONVERSATION, null);
					}
					// check if there are links, attachments and if there is one
					// and not existing create it, we can still do this even if
					// we are not owner of the conversation
					if (jOb.has("attachments")) {
						JsonArray attachments = (JsonArray) jOb.get("attachments");
						conv.attachObjects(svr, conversationObject, attachments);
					}
					res = Response.status(200).entity(prepareConversationData(svr, conversationObject).toString())
							.build();
				}
			} catch (SvException | JsonSyntaxException e) {
				res = Response.status(401).entity(e.getMessage()).build();
			} finally {
				releaseAll(svr);
				releaseAll(svw);
			}
		return res;
	}

	public class FilterBySupportCode implements IDbFilter {
		private Long measureCode = 0L;

		public FilterBySupportCode(Long measureCode) {
			this.measureCode = measureCode;
		}

		public boolean filterObject(DbDataObject dbo) {
			Boolean retVal = false;
			if (dbo.getVal("SFT_PARENT_ID") != null
					&& ((Long) dbo.getVal("SFT_PARENT_ID")).compareTo(this.measureCode) == 0)
				retVal = true;
			return retVal;
		}
	}

	public class FilterByDocumentName implements IDbFilter {
		private String documentName = "";

		public FilterByDocumentName(String documentName) {
			this.documentName = documentName;
		}

		public boolean filterObject(DbDataObject dbo) {
			Boolean retVal = false;
			if (dbo.getVal("SFT_LABEL_CODE") != null && (dbo.getVal("SFT_LABEL_CODE").equals(this.documentName)))
				retVal = true;
			return retVal;
		}
	}

	private DbDataArray getObjectsWithPoa(String searchTableName, SvReader svr) throws SvException {
		DbDataArray results = new DbDataArray();
		DbDataArray linkedOrgUnitsPerUser = getLinkedOrgUnitsPerUser(svr);
		// log4j.info(linkedOrgUnitsPerUser.toSimpleJson());
		if (linkedOrgUnitsPerUser.size() > 0) {
			// DbDataArray searchedItemsLinkedPerTempOrgUnit = new
			// DbDataArray();
			for (DbDataObject tempOrgUnit : linkedOrgUnitsPerUser.getItems()) {
				// searchedItemsLinkedPerTempOrgUnit = new DbDataArray();
				DbDataArray searchedItemsLinkedPerTempOrgUnit = getLinkedSearchedItemsPerOrgUnit(searchTableName,
						tempOrgUnit, svr);
				if (searchedItemsLinkedPerTempOrgUnit != null
						&& !searchedItemsLinkedPerTempOrgUnit.getItems().isEmpty()) {
					for (DbDataObject tempHolding : searchedItemsLinkedPerTempOrgUnit.getItems()) {
						results.addDataItem(tempHolding);
					}
				}
			}
		}
		return results;
	}

	/** search POA link between org_unit and searched object */
	private DbDataObject getPoaLinkIfExistsBetweenSearchedObjectWithOrgUnit(Long tableObjectId, SvReader svr)
			throws SvException {
		DbDataObject dboLink = null;
		DbSearchExpression dbse = new DbSearchExpression();
		DbSearchExpression dbSubExpr = new DbSearchExpression();
		DbSearchCriterion cr0 = new DbSearchCriterion(Rc.LINK_TYPE, DbCompareOperand.EQUAL, "POA");
		dbse.addDbSearchItem(cr0);
		DbSearchCriterion cr1 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE1, DbCompareOperand.EQUAL, tableObjectId);
		cr1.setNextCritOperand("OR");
		DbSearchCriterion cr2 = new DbSearchCriterion(Rc.LINK_OBJECT_TYPE2, DbCompareOperand.EQUAL, tableObjectId);
		dbSubExpr.addDbSearchItem(cr1).addDbSearchItem(cr2);
		dbse.addDbSearchItem(dbSubExpr);
		DbDataArray results = svr.getObjects(dbse, svCONST.OBJECT_TYPE_LINK_TYPE, null, 0, 0);
		if (results.size() > 0) {
			dboLink = results.get(0);
			if (!dboLink.getVal(Rc.LINK_OBJECT_TYPE1).equals(svCONST.OBJECT_TYPE_ORG_UNITS)
					&& !dboLink.getVal(Rc.LINK_OBJECT_TYPE2).equals(svCONST.OBJECT_TYPE_ORG_UNITS)) {
				dboLink = null;
			}
		}
		return dboLink;
	}

	/** getLinkedOrgUnitsPerUser */
	private DbDataArray getLinkedOrgUnitsPerUser(SvReader svr) throws SvException {
		// DbDataArray linkedOrgUnitsPerUser = new DbDataArray();
		DbDataObject dbLink = SvReader.getLinkType("POA", svCONST.OBJECT_TYPE_USER, svCONST.OBJECT_TYPE_ORG_UNITS);
		DbDataObject dboUser = SvReader.getUserBySession(svr.getSessionId());
		// linkedOrgUnitsPerUser =
		// svr.getObjectsByLinkedId(dboUser.getObject_id(), dbLink, null, 0, 0);
		return svr.getObjectsByLinkedId(dboUser.getObject_id(), dbLink, null, 0, 0);
	}

	/** getLinkedSearchObjects per OrgUnit via POA link */
	private DbDataArray getLinkedSearchedItemsPerOrgUnit(String serachTableName, DbDataObject dboOrgUnit, SvReader svr)
			throws SvException {
		// DbDataArray linkedHoldingsPerOrgUnit = new DbDataArray();
		DbDataObject dbLink = SvReader.getLinkType("POA", svCONST.OBJECT_TYPE_ORG_UNITS,
				SvReader.getTypeIdByName(serachTableName));
		// linkedHoldingsPerOrgUnit =
		// svr.getObjectsByLinkedId(dboOrgUnit.getObject_id(), dbLink, null, 0,
		// 0);
		return svr.getObjectsByLinkedId(dboOrgUnit.getObject_id(), dbLink, null, 0, 0);
	}

}