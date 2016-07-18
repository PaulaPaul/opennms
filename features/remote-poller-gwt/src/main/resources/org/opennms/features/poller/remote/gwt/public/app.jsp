<%--
/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2010-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

--%>

<%@page language="java" contentType="text/html; charset=UTF-8" %>
<%@page import="java.net.URLEncoder"%>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>

<%!
	public static String getUrl() {
		String url = System.getProperty("gwt.openlayers.url");
		if ("http://otile1.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.png".equals(url) || !isDefined(url)) {
			url = "https://tiles.opennms.org/${z}/${x}/${y}.png";
		}
	}

	public static String getAttribution() {
		String attribution = System.getProperty("gwt.openlayers.options.attribution");
		if (!isDefined(attribution)) {
			attribution = StringEscapeUtils.unescapeHtml("Map data &copy; &lt;a tabindex=\"-1\" target=\"_blank\" href=\"http://openstreetmap.org/copyright\"&gt;OpenStreetMap&lt;/a&gt; contributors under &lt;a tabindex=\"-1\" target=\"_blank\" href=\"http://opendatacommons.org/licenses/odbl/\"&gt;ODbL&lt;/a&gt;, &lt;a tabindex=\"-1\" target=\"_blank\" href=\"http://creativecommons.org/licenses/by-sa/2.0/\"&gt;CC BY-SA 2.0&lt;/a&gt;");
		}
		return attribution;
	}

	public static boolean isValidConfiguration() {
		return isUrlValid() && isAttributionValid();
	}

	public static boolean isUrlValid() {
		return isDefined(getUrl());
	}

	public static boolean isAttributionValid() {
		return isDefined(getAttribution());
	}

	public static boolean isDefined(String input) {
		return input != null && !"".equals(input);
	}
%>
<%

  String mapImplementation = System.getProperty("gwt.maptype", "");
  String openlayersUrl = getUrl();
  String openlayersAttribution = getAttribution();
  String apiKey = System.getProperty("gwt.apikey", "");

	if (openlayersAttribution != null) {
    	openlayersAttribution = StringEscapeUtils.escapeJavaScript(openlayersAttribution);
	}

	if (!apiKey.equals("")) {
		apiKey = "&key=" + apiKey;
	}
%>
<html>
	<head>
		<title>OpenNMS - Remote Monitor</title>
		<link rel="stylesheet" type="text/css" href="styles.css" />
		<script type="text/javascript">
			window.mapImplementation = "<%= mapImplementation %>";
			window.openlayersUrl = "<%= openlayersUrl %>";
			window.openlayersAttribution = "<%= openlayersAttribution %>";
		</script>
		<% if (mapImplementation.equalsIgnoreCase("googlemaps")) { %>
			<script src="<%= URLEncoder.encode("http://maps.google.com/maps?gwt=1&amp;file=api&amp;v=2.x" + apiKey, "UTF-8") %>"></script>
		<% } else if (mapImplementation.equalsIgnoreCase("mapquest")) { %>
			<script type="text/javascript" src="<%= URLEncoder.encode("http://btilelog.access.mapquest.com/tilelog/transaction?transaction=script&itk=true&v=5.3.s&ipkg=controls1" + apiKey, "UTF-8") %>"></script>
			<script type="text/javascript" src="mapquest/debug/mqutils.js"></script>
			<script type="text/javascript" src="mapquest/debug/mqobjects.js"></script>
		<% } else if (mapImplementation.equalsIgnoreCase("openlayers")) { %>
			<script type="text/javascript" src="opennms-openlayers.js"></script>
			<script type="text/javascript" src="openlayers/OpenLayers.js"></script>
		<% } %>
		<%
		if(isValidConfiguration()) { %>
			<script type="text/javascript" language="javascript" src="RemotePollerMap.nocache.js"></script>
		<%}%>
	</head>
	<body>
		<% if(isValidConfiguration()) { %>
		<div id="map"></div>

		<!-- OPTIONAL: include this if you want history support -->
		<iframe src="javascript:''" id="__gwt_historyFrame" tabIndex='-1' style="position:absolute;width:0;height:0;border:0"></iframe>
		<% } else {%>
			<div>
				<ul>
					<% if (!isUrlValid()) { %>
						<li>No <i>gwt.openlayers.url</i> property defined</li>
					<% } %>
					<% if (!isAttributionValid()) { %>
						<li>No <i>gwt.openlayers.options.attribution</i> property defined</li>
					<% }%>
				</ul>
				Please define the above properties in opennms.properties.<br/>
			</div>
		<% } %>
	</body>
</html>
