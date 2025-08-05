const baseUrl = 'http://localhost:8080';

function createTopic(){
    alert("Creating Topic");
}

function createConnection( connection ){
    $.ajax({
            url: baseUrl + "/api/connection",
            method: 'POST',
            dataType: 'json',
            contentType: "application/json",
            data: JSON.stringify(connection),
            headers: {
                'SESSION': getCookie("SESSION"),
                'Content-Type': 'application/json'
            },
            success: function(response) {
                console.log('Success:', response);
            },
            error: function(xhr, status, error) {
                console.error('Error:', status, error);
                console.log('Response Text:', xhr.responseText);
            },
            complete: function() {
                console.log('Request complete.');
            }
    });
}

function getConnections(callback){
    $.ajax({
        url: baseUrl + "/api/connection",
        method: 'GET',
        dataType: 'json',
        headers: {
            'SESSION': getCookie("SESSION"),
            'Content-Type': 'application/json'
        },
        success: function(response) {
            callback(response);
            console.log('Success:', response);
        },
        error: function(xhr, status, error) {
            console.error('Error:', status, error);
            console.log('Response Text:', xhr.responseText);
        },
        complete: function() {
            console.log('Request complete.');
        }
    });
    return connections;
}

function callApiDummy(){
    $.ajax({
            url: 'https://api.example.com/data', // The API endpoint URL
            method: 'GET', // HTTP method (GET, POST, PUT, DELETE, etc.)
            dataType: 'json', // Expected data type of the response (e.g., 'json', 'xml', 'text')
            data: { // Data to send with the request (for POST, PUT, etc.)
                param1: 'value1',
                param2: 'value2'
            },
            headers: { // Custom headers (e.g., for authentication)
                'Authorization': 'Bearer YOUR_AUTH_TOKEN',
                'Content-Type': 'application/json'
            },
            success: function(response) {
                console.log('Success:', response);
            },
            error: function(xhr, status, error) {
                // Handle error response
                console.error('Error:', status, error);
                console.log('Response Text:', xhr.responseText);
            },
            complete: function() {
                // This function is called regardless of success or error
                console.log('Request complete.');
            }
        });
}

function getCookie(name) {
  const cookies = document.cookie.split(';');
  for (let i = 0; i < cookies.length; i++) {
    let cookie = cookies[i].trim();
    if (cookie.startsWith(name + '=')) {
      return cookie.substring(name.length + 1);
    }
  }
  return null;
}

function renderView( container, view, data ){
    if (isPlainObject(data)) {
        $(container).append($(view).html());
    } else if (isArrayOfPlainObjects(data)) {
        data.forEach(function(item, index) {
            $(container).append(updatePlaceHolder($(view).html(),item));
        });
    } else {
        console.log("Input is neither a plain object nor an array of plain objects.");
    }
}

function isPlainObject(value) {
  return typeof value === 'object' && value !== null && value.constructor === Object;
}

function isArrayOfPlainObjects(value) {
  return Array.isArray(value) && value.every(isPlainObject);
}

function updatePlaceHolder( viewHtml, data ){
    processedView = viewHtml;
    Object.keys(data).forEach(key => {
          processedView = processedView.replaceAll('${'+key+'}', data[key]);
    });
    return processedView;
}