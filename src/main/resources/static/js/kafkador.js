const CONFIG = {
    baseUrl: window.location.origin || 'http://localhost:8080',
    apiEndpoints: {
        connection: '/api/connection',
        connect: '/api/connect',
        topics: '/api/topic',
        clusters: '/api/cluster',
        consumers: '/api/consumer'
    },
    defaultHeaders: {
        'Content-Type': 'application/json'
    }
};

function createTopic(){
    alert("Creating Topic");
}

function createConnection( connection ){
    $.ajax({
            url: CONFIG.baseUrl + CONFIG.apiEndpoints.connection,
            method: 'POST',
            dataType: 'json',
            contentType: CONFIG.defaultHeaders['Content-Type'],
            data: JSON.stringify(connection),
            headers: {
                'SESSION': Utils.getCookie("SESSION"),
                'Content-Type': CONFIG.defaultHeaders['Content-Type']
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
        url: CONFIG.baseUrl + CONFIG.apiEndpoints.connection,
        method: 'GET',
        dataType: 'json',
        headers: {
            'SESSION': Utils.getCookie("SESSION"),
            'Content-Type': CONFIG.defaultHeaders['Content-Type']
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


function connect(id,callback){
    $.ajax({
        url: CONFIG.baseUrl + CONFIG.apiEndpoints.connect,
        method: 'GET',
        dataType: 'json',
        data: {
            id : id
        },
        headers: {
            'SESSION': Utils.getCookie("SESSION"),
            'Content-Type': CONFIG.defaultHeaders['Content-Type']
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

function renderView( container, view, data ){
    if (Utils.isPlainObject(data)) {
        $(container).append($(view).html());
    } else if (Utils.isArrayOfPlainObjects(data)) {
        data.forEach(function(item, index) {
            $(container).append(updatePlaceHolder($(view).html(),item));
        });
    } else {
        console.log("Input is neither a plain object nor an array of plain objects.");
    }
}

function updatePlaceHolder( viewHtml, data ){
    processedView = viewHtml;
    Object.keys(data).forEach(key => {
          processedView = processedView.replaceAll('${'+key+'}', data[key]);
    });
    return processedView;
}

const Utils = {
    /**
     * Get cookie value by name
     * @param {string} name - Cookie name
     * @returns {string|null} Cookie value or null if not found
     */
    getCookie(name) {
        const cookies = document.cookie.split(';');
        for (let cookie of cookies) {
            cookie = cookie.trim();
            if (cookie.startsWith(name + '=')) {
                return decodeURIComponent(cookie.substring(name.length + 1));
            }
        }
        return null;
    },

    /**
     * Check if value is a plain object
     * @param {any} value - Value to check
     * @returns {boolean} True if plain object
     */
    isPlainObject(value) {
        return typeof value === 'object' && value !== null && value.constructor === Object;
    },

    /**
     * Check if value is an array of plain objects
     * @param {any} value - Value to check
     * @returns {boolean} True if array of plain objects
     */
    isArrayOfPlainObjects(value) {
        return Array.isArray(value) && value.every(Utils.isPlainObject);
    },

    /**
     * Show user-friendly error message
     * @param {string} message - Error message
     * @param {string} type - Message type (error, warning, success, info)
     */
    showMessage(message, type = 'error') {
        // You can integrate with your UI framework here
        console.error(`[${type.toUpperCase()}] ${message}`);
        
        // Simple alert fallback - replace with proper UI notification
        if (type === 'error') {
            alert(`Error: ${message}`);
        }
    },

    /**
     * Validate required parameters
     * @param {Object} params - Parameters to validate
     * @param {string[]} required - Required parameter names
     * @returns {boolean} True if all required parameters are present
     */
    validateParams(params, required) {
        for (let param of required) {
            if (!params.hasOwnProperty(param) || params[param] === undefined || params[param] === null) {
                Utils.showMessage(`Missing required parameter: ${param}`);
                return false;
            }
        }
        return true;
    }
};