package test;

import static org.junit.Assert.fail;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog.SvSecurity;
import react.triglav.plugin.WsReactElements;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestReact {

	static final Logger log4j = LogManager.getLogger(TestReact.class.getName());

	
	public static void releaseAll(SvCore svc) {
		if (svc != null)
			svc.release();
	}
	
	@Test
	public void searchConversationDetails() {
		SvReader svr = null;
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();


			// za  niats String token = svsec.logon("L.JANEV",SvUtil.getMD5("naits1234" ));
			String token = svsec.logon("ADMIN",SvUtil.getMD5("welcome" ));
			
			Long conversationd = 20565743L;
			//conversationd = 2178466L;   //naits
			//conversationd = 0L;   //naits
			svr = new SvReader(token);
			WsReactElements rea = new WsReactElements();

			JsonObject jsonObjectResponse = null;
			JsonArray jsonArrayResponse = null;
			Gson gson = new Gson();
			Object aaa = null;
			String baba = "";

			Response responseHtml = rea.getConversationHeader(token, conversationd, null);
			aaa = responseHtml.getEntity();
			baba = aaa.toString();
			jsonObjectResponse = gson.fromJson(baba, JsonObject.class);
			log4j.info(jsonObjectResponse.toString());

			responseHtml = rea.getConversationData(token, conversationd, null);
			aaa = responseHtml.getEntity();
			baba = aaa.toString();
			jsonObjectResponse = gson.fromJson(baba, JsonObject.class);
			log4j.info(jsonObjectResponse.toString());
			
			responseHtml = rea.getMessagesForConversation(token, conversationd, null);
			aaa = responseHtml.getEntity();
			baba = aaa.toString();
			jsonArrayResponse = gson.fromJson(baba, JsonArray.class);
			log4j.info(jsonArrayResponse.toString());
			
			responseHtml = rea.getConversationGridData(token, "ASSIGNED_TO_ME", "ADMIN", "true", null);
			aaa = responseHtml.getEntity();
			baba = aaa.toString();
			jsonArrayResponse = gson.fromJson(baba, JsonArray.class);
			log4j.info(jsonArrayResponse.toString());

		} catch (SvException ex) {
			log4j.error(ex.getFormattedMessage());
			fail(ex.getFormattedMessage());
		} finally {
			releaseAll(svr);
			releaseAll(svsec);
		}
	}
	
	
	@Test
	public void testLinkAttachemtToConversation () {
		SvReader svr = null;
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();
			String token = svsec.logon("L.JANEV",SvUtil.getMD5("naits1234" ));
			Long conversationd = 2178466L;   //naits
			Long userId = 220493L;  //l.janev
			svr = new SvReader(token);
			WsReactElements rea = new WsReactElements();
			Object aaa = null;
			String baba = "";
			Response responseHtml = rea.getConversationHeader(token, conversationd, null);
			 responseHtml = rea.linkObjects(token, conversationd, "SVAROG_CONVERSATION", userId, "SVAROG_USERS", "LINK_CONVERSATION_ATTACHMENT", "test fstak", null);
			aaa = responseHtml.getEntity();
			baba = aaa.toString();
			log4j.info(baba);
		} catch (SvException ex) {
			log4j.error(ex.getFormattedMessage());
			fail(ex.getFormattedMessage());
		} finally {
			releaseAll(svr);
			releaseAll(svsec);
		}
	}
	
	

	@Test
	public void testupdateConversation () {
		SvReader svr = null;
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();
			String token = svsec.logon("A.ADMIN3",SvUtil.getMD5("naits1234" ));
			MultivaluedMap<String, String> formVals = new MultivaluedHashMap<>();
			String jsonAsString ="{\"attachments\":[{\"OBJECT_ID\":220476,\"OBJECT_TYPE\":57,\"LINK_TYPE\":2251854},{\"OBJECT_ID\":1275788,\"OBJECT_TYPE\":62,\"LINK_TYPE\":2251855}],\"MODULE_NAME\":\"NAITS\",\"CATEGORY\":\"TASK\",\"TITLE\":\"TEST_ZOKAN\",\"PRIORITY\":\"IMMEDIATE\",\"ASSIGNED_TO_USERNAME\":\"E.REHBEN\",\"CONVERSATION_STATUS\":\"RESOLVED\",\"CONTACT_INFO\":\"777\"}";
			formVals.add(jsonAsString, "formVla");
			//Long conversationd = 2178466L;   //naits
			//Long userId = 220493L;  //l.janev
			svr = new SvReader(token);
			WsReactElements rea = new WsReactElements();
			Object aaa = null;
			String baba = "";
			Response responseHtml  = rea.createConversation(token, 0L, formVals, null);
			aaa = responseHtml.getEntity();
			baba = aaa.toString();
			log4j.info(baba);
		} catch (SvException ex) {
			log4j.error(ex.getFormattedMessage());
			fail(ex.getFormattedMessage());
		} finally {
			releaseAll(svr);
			releaseAll(svsec);
		}

	}
	
	
	@Test
	public void testGetTableWithLike () {
		SvReader svr = null;
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();
			String token = svsec.logon("L.JANEV",SvUtil.getMD5("naits1234" ));
			
			//Long userId = 220493L;  //l.janev
			svr = new SvReader(token);
			WsReactElements rea = new WsReactElements();
			Object aaa = null;
			String baba = "";
			//String fieldWithSpecialCharacter = "";
			String fieldValue = "MD-1317123000025%2F007";
			Response responseHtml = rea.getTableWithLike(token, "ANIMAL_MOVEMENT", "MOVEMENT_DOC_ID", fieldValue, 100, null);
			aaa = responseHtml.getEntity();
			baba = aaa.toString();
			log4j.info(baba);
		} catch (SvException ex) {
			log4j.error(ex.getFormattedMessage());
			fail(ex.getFormattedMessage());
		} finally {
			releaseAll(svr);
			releaseAll(svsec);
		}
	}
	
	
	@Test
	public void testDataError () {
		SvReader svr = null;
		SvSecurity svsec = null;
		try {
			svsec = new SvSecurity();
			String token = svsec.logon("ADMIN",SvUtil.getMD5("welcome" ));
			
			

			svr = new SvReader(token);
			WsReactElements rea = new WsReactElements();
			Object aaa = null;
			String baba = "";

			DbDataArray ret = new DbDataArray();
			// prepare the data to be displayed
			int tablesusedCount = 2;
			String[] tablesUsedArray = new String[tablesusedCount];
			Boolean[] tableShowArray = new Boolean[tablesusedCount];
			tablesUsedArray[0] = ("ORG_UNITS");
			tablesUsedArray[1] = ("FARMER");
			tableShowArray[0] = true;
			tableShowArray[1] = true;
			
			String jsonStr = WsReactElements.prapareTableQueryData(ret, tablesUsedArray, tableShowArray,
					tablesusedCount, true, svr);
			
			//aaa = responseHtml.getEntity();
			//baba = aaa.toString();
			log4j.info(baba);
			log4j.info(baba);
			log4j.info(jsonStr);
		} catch (SvException ex) {
			log4j.error(ex.getFormattedMessage());
			fail(ex.getFormattedMessage());
		} finally {
			releaseAll(svr);
			releaseAll(svsec);
		}
	}
	
	

}
