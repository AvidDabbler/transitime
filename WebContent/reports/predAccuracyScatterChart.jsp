<%@ page import="org.transitime.utils.web.WebUtils" %>
<%
// Create title for chart
String agencyId = request.getParameter("a");
String routeId = request.getParameter("r");
String routeTitle = (routeId != null && !routeId.isEmpty()) ? 
	" for " + routeId : "";
String source = request.getParameter("source");
String sourceForTitle = (source != null && !source.isEmpty()) ? 
	" for " + source + " predictions" : ""; 
String beginDate = request.getParameter("beginDate");
String beginTime = request.getParameter("beginTime");
String endDate = request.getParameter("endDate");
String endTime = request.getParameter("endTime");

String chartTitle = "Prediction Accuracy for " + agencyId   
	+ routeTitle + sourceForTitle 
	+ " for " + beginDate + " " + beginTime + " to " + endDate + " " + endTime;
%>
<html>
  <head>
    <style>
      .google-visualization-tooltip {
        font-family: arial, sans-serif;
      }

      #loading {
        position: fixed;
		left: 0px;
		top: 0px;
		width: 100%;
		height: 100%;
		z-index: 9999;
		background: url('images/page-loader.gif') 50% 50% no-repeat rgb(249,249,249);
      }
      
      #errorMessage {
		  display: none;
          position: fixed;
	      top: 30px;
	      margin-left: 20%;
	      margin-right: 20%;
	      height: 100%;
	      text-align: center;
	      font-family: sans-serif;
	      font-size: large;
	      z-index: 9999;
		}
    </style>
  </head>

  <body>
    <div id="chart_div" style="width: 100%; height: 100%;"></div>
    <div id="loading"></div>
    <div id="errorMessage"></div>
  </body>

    <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
    <script type="text/javascript" src="https://www.google.com/jsapi"></script>
    
    <script type="text/javascript">

      // Updates chart when page is resized. But only does so at most
      // every 200 msec so that don't bog system down trying to repeatedly
      // update the chart.
      var globalTimer;
      window.onresize = function () {
                   clearTimeout(globalTimer);
                   globalTimer = setTimeout(drawChart, 100)
                 };

      var globalDataTable = null;

      function getDataTable() {
        var jsonTextData = $.ajax({
          url: "predAccuracyScatterData.jsp",
      	  // Pass in query string parameters to page being requested
          data: {<%= WebUtils.getAjaxDataString(request) %>},
      	  // Needed so that parameters passed properly to page being requested
          traditional: true,
          dataType:"json",
          async: false,
          success: function(jsonData) {
            globalDataTable = new google.visualization.DataTable(jsonData);
            },
          error: function(request, status, error) {
          	$("#errorMessage").html(request.responseText +
			  "<br/><br/>Hit back button to try other parameters.")
			$("#errorMessage").fadeIn("slow");
            },
          }).responseJSON;
      }

      /* Actualy draws the chart */
      function drawChart() {

        var chartOptions = {
          title: '<%= chartTitle %>',
          titleTextStyle: {fontSize: 28},
          // Could use html tooltips so can format them but for now using regular ones
          // FIXME tooltip: {isHtml: false},
          hAxis: {
        	  title: 'Prediction Time (secs)', 
        	  minValue: 0, 
        	  maxValue: 900, 
        	  ticks: [
        	          {v:60, f:'1'},
        	          {v:120, f:'2'},
        	          {v:180, f:'3'},
        	          {v:240, f:'4'},
        	          {v:300, f:'5'},
        	          {v:360, f:'6'},
        	          {v:420, f:'7'},
        	          {v:480, f:'8'},
        	          {v:540, f:'9'},
        	          {v:600, f:'10'},
        	          {v:660, f:'11'},
        	          {v:720, f:'12'},
        	          {v:780, f:'13'},
        	          {v:840, f:'14'},
        	          {v:900, f:'15'}]
               },
          vAxis: {title: 'Prediction Accuracy (secs) (postive means vehicle later than predicted)', 
          	  // Try to show accuracy on a consistent vertical axis and 
          	  // divide into minutes. This unfortunately won't work well
          	  // if values are greater than 360 because then chart will
          	  // autoscale but will still be using 11 gridlines
        	  minValue: -420, 
        	  maxValue: 420,
          	gridlines: {count: 15},
   	        // Nice to show a faint line for every 30 seconds as well
        	minorGridlines: {count: 1}
          },
          // Usually will first be displaying Transitime predictions and 
          // those will get the first color. If both Transitime and Tther
          // predictions shown then the Other ones will get the second color.
          // But want color for the Other predictions to be consistent 
          // whether only Other predictions or both Other and Transitime ones
          // are shown. Therefore do something fancy here for consistency.
          series: [{'color': '<%= (source==null || !source.equals("Other")) ? "blue" : "red" %>'},{'color': 'red'}],
          legend: 'none',
          // Use small points since have lots of them
          pointSize: 2,
          // Draw a trendline for data series 0
          //trendlines: { 
          //  0: {
          //    color: 'purple',
          //    lineWidth: 5,
          //    opacity: 0.5,
          //  } 
          //},
          // Need to not use 100% or else labels won't appear
          chartArea: {width:'90%', height:'80%', backgroundColor: '#f2f2f2'},
          // Allow zooming
          //explorer: { actions: ['dragToZoom', 'rightClickToReset'] }
        };

        var chart = new google.visualization.ScatterChart(document.getElementById('chart_div'));

        chart.draw(globalDataTable, chartOptions);
      }

      function getDataAndDrawChart() {
        getDataTable();
        if (globalDataTable != null)
      	    drawChart();

        // Now that chart has been drawn faceout the loading image
        $("#loading").fadeOut("slow");
      }

      // Start visualization after the body created so that the
      // page loading image will be displayed right away
      google.load("visualization", "1", {packages:["corechart"]});
      google.setOnLoadCallback(getDataAndDrawChart);
</script>
</html>

