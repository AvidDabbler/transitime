<%@ page import="org.transitime.db.webstructs.WebAgency" %>
<%@ page import="org.transitime.reports.ChartGenericJsonQuery" %>
<%@ page import="org.transitime.utils.Time" %>
<%@ page import="java.text.ParseException" %>
<%
	// Parameters from request
String agencyId = request.getParameter("a");
String beginDate = request.getParameter("beginDate");
String endDate = request.getParameter("endDate");
String beginTime = request.getParameter("beginTime");
String endTime = request.getParameter("endTime");
String routeId =  request.getParameter("r");
String source = request.getParameter("source");
String predictionType = request.getParameter("predictionType");

boolean showTooltips = true;
String showTooltipsStr = request.getParameter("tooltips");
if (showTooltipsStr != null && showTooltipsStr.toLowerCase().equals("false"))
    showTooltips = false;
    
if (agencyId == null || beginDate == null || endDate == null) {
		response.getWriter().write("For predAccuracyScatterData.jsp must "
	+ "specify parameters 'a' (agencyId), " 
	+ "'beginDate', and 'endDate'."); 
		return;
}

// Make sure not trying to get data for too long of a time span since
// that could bog down the database.
long timespan = Time.parseDate(endDate).getTime() - 
	Time.parseDate(beginDate).getTime() + 1*Time.MS_PER_DAY;
if (timespan > 31*Time.MS_PER_DAY) {
    throw new ParseException("Begin date to end date spans more than a month", 0);
}

    WebAgency agency = WebAgency.getCachedWebAgency(agencyId);
    String dbtype = agency.getDbType();
    String sql = null;
    if(dbtype.equals("mysql")){


// Determine the time portion of the SQL
        String timeSql = "";
        if ((beginTime != null && !beginTime.isEmpty())
                || (endTime != null && !endTime.isEmpty())) {
            // If only begin or only end time set then use default value
            if (beginTime == null || beginTime.isEmpty())
                beginTime = "00:00:00";
            if (endTime == null || endTime.isEmpty())
                endTime = "23:59:59";

            timeSql = " AND time(arrivalDepartureTime) BETWEEN '"
                    + beginTime + "' AND '" + endTime + "' ";
        }

// Determine route portion of SQL. Default is to provide info for
// all routes.
        String routeSql = "";
        if (routeId!=null && !routeId.isEmpty()) {
            routeSql = "  AND routeId='" + routeId + "' ";
        }

// Determine the source portion of the SQL. Default is to provide
// predictions for all sources
        String sourceSql = "";
        if (source != null && !source.isEmpty()) {
            if (source.equals("Transitime")) {
                // Only "Transitime" predictions
                sourceSql = " AND predictionSource='Transitime'";
            } else {
                // Anything but "Transitime"
                sourceSql = " AND predictionSource<>'Transitime'";
            }
        }

// Determine SQL for prediction type ()
        String predTypeSql = "";
        if (predictionType != null && !predictionType.isEmpty()) {
            if (source.equals("AffectedByWaitStop")) {
                // Only "AffectedByLayover" predictions
                predTypeSql = " AND affectedByWaitStop = true ";
            } else {
                // Only "NotAffectedByLayover" predictions
                predTypeSql = " AND affectedByWaitStop = false ";
            }
        }

        String tooltipsSql = "";
        if (showTooltips)
            tooltipsSql =
                    ",  concat(\n" +
                            "'predAccuracy=', predictedTime-predictionReadTime, '\\n',\n" +
                            "'stopId=', stopId, '\\n',\n" +
                            "'routeId=', routeId, '\\n',\n" +
                            "'tripId=', tripId, '\\n',\n" +
                            "'arrDepTime=', arrivalDepartureTime, '\\n',\n" +
                            "'predTime=', predictedTime, '\\n',\n" +
                            "'predReadTime=', predictionReadTime, '\\n',\n" +
                            "'vehicleId=', vehicleId, '\\n',\n" +
                            "'source=', predictionSource, '\\n',\n" +
                            "'affectedByLayover=', CASE WHEN affectedbywaitstop THEN 'True' ELSE 'False' END, '\\n'\n" +
                            ") as tooltip ";

        sql = "SELECT "
                + "     predictedTime-predictionReadTime as predLength, "
                + "     predictionAccuracyMsecs/1000 as predAccuracy "
                + tooltipsSql
                + " FROM PredictionAccuracy "
                + "WHERE date(arrivalDepartureTime) " +
                "BETWEEN STR_TO_DATE('" + beginDate + "', '%Y-%m-%d') and DATE_ADD(STR_TO_DATE('" + endDate + "', '%Y-%m-%d'),INTERVAL 1 DAY)"
                + timeSql
                + "  AND predictedTime-predictionReadTime < (15 * 60) "
                + routeSql
                + sourceSql
                + predTypeSql
                // Filter out MBTA_seconds source since it is isn't significantly different from MBTA_epoch.
                // TODO should clean this up by not having MBTA_seconds source at all
                // in the prediction accuracy module for MBTA.
                + "  AND predictionSource <> 'MBTA_seconds' ";
    }else{


// Determine the time portion of the SQL
        String timeSql = "";
        if ((beginTime != null && !beginTime.isEmpty())
                || (endTime != null && !endTime.isEmpty())) {
            // If only begin or only end time set then use default value
            if (beginTime == null || beginTime.isEmpty())
                beginTime = "00:00:00";
            if (endTime == null || endTime.isEmpty())
                endTime = "23:59:59";

            timeSql = " AND arrivalDepartureTime::time BETWEEN '"
                    + beginTime + "' AND '" + endTime + "' ";
        }

// Determine route portion of SQL. Default is to provide info for
// all routes.
        String routeSql = "";
        if (routeId!=null && !routeId.isEmpty()) {
            routeSql = "  AND routeId='" + routeId + "' ";
        }

// Determine the source portion of the SQL. Default is to provide
// predictions for all sources
        String sourceSql = "";
        if (source != null && !source.isEmpty()) {
            if (source.equals("Transitime")) {
                // Only "Transitime" predictions
                sourceSql = " AND predictionSource='Transitime'";
            } else {
                // Anything but "Transitime"
                sourceSql = " AND predictionSource<>'Transitime'";
            }
        }

// Determine SQL for prediction type ()
        String predTypeSql = "";
        if (predictionType != null && !predictionType.isEmpty()) {
            if (source.equals("AffectedByWaitStop")) {
                // Only "AffectedByLayover" predictions
                predTypeSql = " AND affectedByWaitStop = true ";
            } else {
                // Only "NotAffectedByLayover" predictions
                predTypeSql = " AND affectedByWaitStop = false ";
            }
        }

        String tooltipsSql = "";
        if (showTooltips)
            tooltipsSql =
                    ", format(E'predAccuracy= %s\\n"
                            +         "prediction=%s\\n"
                            +         "stopId=%s\\n"
                            +         "routeId=%s\\n"
                            +         "tripId=%s\\n"
                            +         "arrDepTime=%s\\n"
                            +         "predTime=%s\\n"
                            +         "predReadTime=%s\\n"
                            +         "vehicleId=%s\\n"
                            +         "source=%s\\n"
                            +         "affectedByLayover=%s', "
                            + "   CAST(predictionAccuracyMsecs || ' msec' AS INTERVAL), predictedTime-predictionReadTime,"
                            + "   stopId, routeId, tripId, "
                            + "   to_char(arrivalDepartureTime, 'HH24:MI:SS.MS MM/DD/YYYY'),"
                            + "   to_char(predictedTime, 'HH24:MI:SS.MS'),"
                            + "   to_char(predictionReadTime, 'HH24:MI:SS.MS'),"
                            + "   vehicleId,"
                            + "   predictionSource,"
                            + "   CASE WHEN affectedbywaitstop THEN 'True' ELSE 'False' END) AS tooltip ";

        sql = "SELECT "
                + "     to_char(predictedTime-predictionReadTime, 'SSSS')::integer as predLength, "
                + "     predictionAccuracyMsecs/1000 as predAccuracy "
                + tooltipsSql
                + " FROM PredictionAccuracy "
                + "WHERE arrivalDepartureTime BETWEEN '" + beginDate
                +     "' AND TIMESTAMP '" + endDate + "' + INTERVAL '1 day' "
                + timeSql
                + "  AND predictedTime-predictionReadTime < '00:15:00' "
                + routeSql
                + sourceSql
                + predTypeSql
                // Filter out MBTA_seconds source since it is isn't significantly different from MBTA_epoch.
                // TODO should clean this up by not having MBTA_seconds source at all
                // in the prediction accuracy module for MBTA.
                + "  AND predictionSource <> 'MBTA_seconds' ";
    }



    // Determine the json data by running the query
    String jsonString = ChartGenericJsonQuery.getJsonString(agencyId, sql);

// If no data then return error status with an error message
if (jsonString == null || jsonString.isEmpty()) {
    String message = "No data for beginDate=" + beginDate
	    + " endDate=" + endDate 
	    + " beginTime=" + beginTime
	    + " endTime=" + endTime 
	    + " routeId=" + routeId
	    + " source=" + source
	    + " predictionType=" + predictionType;
    response.sendError(416 /* Requested Range Not Satisfiable */, 
	    message);
	return;
}

// Return the JSON data
response.getWriter().write(jsonString);
%>